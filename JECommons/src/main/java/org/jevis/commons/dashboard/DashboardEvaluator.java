/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dashboard;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.chart.*;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.datetime.PeriodComparator;
import org.jevis.commons.datetime.WorkDays;
import org.jevis.commons.json.JsonAlarm;
import org.jevis.commons.json.JsonTools;
import org.jevis.commons.unit.ChartUnits.QuantityUnits;
import org.jevis.commons.unit.UnitManager;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes the values of the widgets of a dashboard for a time frame, port of the Control Center's
 * {@code DataModelDataHandler.setInterval/update} and of what the widgets take from it:
 * <ul>
 *     <li>the interval of a widget: its own time frame ({@code forcedInterval}) for the end of the dashboard interval,
 *     a fixed time frame of zero length ({@code LASTVALUE}, {@code PALL}) keeps the dashboard interval and only the
 *     newest sample of each row,</li>
 *     <li>the aggregation of a row: none if the automatic aggregation of the interval is finer than the period of the
 *     row; for Chart widgets rows without own aggregation get the automatic one (under 6 days none, under 27 days
 *     hourly, under 364 days daily, otherwise weekly; not for heat maps and rows with a rate unit such as kW); otherwise
 *     the stored aggregation of the row,</li>
 *     <li>rows aggregated daily or coarser are read from 00:00:00 to 23:59:59 (a workday over midnight starts a day
 *     later), rows of a {@code LASTVALUE} widget at their last sample, rows of a {@code PALL} widget from their first
 *     to their last sample,</li>
 *     <li>Chart widgets get samples and statistics like an analysis; the other data widgets are evaluated in absolute
 *     mode (one value per row: {@code setAutoAggregation(true)}), String Value widgets give the newest sample as text,
 *     the input widgets (Value Editor, Toggle Switch, Slider, PlusMinus) the latest sample of the attribute
 *     ({@code forceLastValue}), Alarm Count widgets the number of logged alarms in the dashboard interval
 *     ({@code AlarmCountWidget.countAlarms}).</li>
 * </ul>
 * A dashboard time frame of zero length ({@code PALL}) is applied to the rows like a fixed one (the Control Center
 * would load nothing for it).
 */
public class DashboardEvaluator {
    private static final Logger logger = LogManager.getLogger(DashboardEvaluator.class);
    private final JEVisDataSource ds;
    private final RowDataLoader rowDataLoader;

    public DashboardEvaluator(JEVisDataSource ds) {
        this.ds = ds;
        this.rowDataLoader = new RowDataLoader(ds);
    }

    /**
     * Evaluates all widgets of a dashboard.
     *
     * @param dashboardObject the dashboard (its building defines the workday), may be {@code null}
     * @param definition      the dashboard read by {@link DashboardReader}
     * @param request         time frame, reference date, time zone
     */
    public DashboardResult evaluate(JEVisObject dashboardObject, DashboardDefinition definition, DashboardRequest request) {
        WorkDays workDays = dashboardObject != null ? new WorkDays(dashboardObject) : null;
        DashboardTimeFrames timeFrames = new DashboardTimeFrames(ds, request.getZone(), workDays, request.getNow());
        return evaluate(definition, request, timeFrames);
    }

    /**
     * Evaluates all widgets with the given time frames.
     *
     * @throws IllegalArgumentException for {@link DashboardTimeFrames#CUSTOM} without valid from/to
     */
    public DashboardResult evaluate(DashboardDefinition definition, DashboardRequest request, DashboardTimeFrames timeFrames) {
        DashboardResult result = new DashboardResult();
        String id = request.getTimeFrame();
        TimeRange range;
        if (DashboardTimeFrames.CUSTOM.equals(id)) {
            if (request.getFrom() == null || request.getTo() == null || request.getTo().isBefore(request.getFrom())) {
                throw new IllegalArgumentException("CUSTOM needs valid from and to");
            }
            range = new TimeRange(request.getFrom().withZone(timeFrames.getZone()), request.getTo().withZone(timeFrames.getZone()));
        } else {
            if (!timeFrames.isKnown(id)) id = definition.getTimeFrame();
            if (!timeFrames.isKnown(id) || DashboardTimeFrames.CUSTOM.equals(id)) id = DashboardTimeFrames.DAY;
            range = timeFrames.interval(id, request.getDate(), false);
            result.setPrevious(timeFrames.previous(id, request.getDate()));
            result.setNext(timeFrames.next(id, request.getDate()));
        }
        result.setTimeFrame(id);
        result.setFrom(range.getStart());
        result.setTo(range.getEnd());

        EvaluationSettings base = new EvaluationSettings().setZone(timeFrames.getZone())
                .setCustomWorkDay(request.isCustomWorkDay()).setNow(timeFrames.getNow());
        for (WidgetDefinition widget : definition.getWidgets()) {
            try {
                DashboardResult.WidgetResult widgetResult = evaluateWidget(widget, id, range, base, timeFrames);
                if (widgetResult != null) result.getWidgets().put(String.valueOf(widget.getUuid()), widgetResult);
            } catch (Exception e) {
                logger.error("Could not evaluate widget {} ({})", widget.getUuid(), widget.getType(), e);
            }
        }
        return result;
    }

    /**
     * @return the data of the widget or {@code null} for widgets without data
     */
    DashboardResult.WidgetResult evaluateWidget(WidgetDefinition widget, String dashboardTimeFrame, TimeRange range,
                                                EvaluationSettings base, DashboardTimeFrames timeFrames) {
        String type = widget.getType();
        if (WidgetDefinition.TYPE_ALARM_COUNT.equals(type)) {
            DashboardResult.WidgetResult result = new DashboardResult.WidgetResult(DashboardResult.WidgetResult.Kind.ALARMS);
            result.setFrom(range.getStart());
            result.setTo(range.getEnd());
            Object objectId = widget.getConfig().get("objectId");
            result.setAlarmCount(countAlarms(range, objectId instanceof Long ? (Long) objectId : null));
            return result;
        }

        List<RowDefinition> rows = widget.getRows();
        if (rows.isEmpty()) return null;
        DashboardResult.WidgetResult.Kind kind;
        if (WidgetDefinition.TYPE_CHART.equals(type)) {
            kind = DashboardResult.WidgetResult.Kind.SERIES;
        } else if (WidgetDefinition.isValueType(type)) {
            kind = DashboardResult.WidgetResult.Kind.VALUE;
        } else if (WidgetDefinition.TYPE_STRING_VALUE.equals(type)) {
            kind = DashboardResult.WidgetResult.Kind.TEXT;
        } else if (WidgetDefinition.isInputType(type)) {
            kind = DashboardResult.WidgetResult.Kind.LATEST;
        } else {
            return null;
        }
        DashboardResult.WidgetResult result = new DashboardResult.WidgetResult(kind);

        if (kind == DashboardResult.WidgetResult.Kind.LATEST) {
            result.setFrom(range.getStart());
            result.setTo(range.getEnd());
            for (RowDefinition row : rows) result.getRows().add(latest(row));
            return result;
        }

        /* DataModelDataHandler.setInterval */
        String forced = widget.getTimeFrame();
        TimeRange interval = range;
        boolean zeroLength = false;
        String special = null;
        if (forced != null) {
            TimeRange forcedRange = timeFrames.interval(forced, range.getEnd(), widget.isFixedTimeFrame());
            if (forcedRange != null) {
                if (forcedRange.getEnd().getMillis() == forcedRange.getStart().getMillis()) {
                    zeroLength = true;
                } else {
                    interval = forcedRange;
                }
                if (DashboardTimeFrames.isZeroLength(forced)) special = forced;
            }
        } else if (DashboardTimeFrames.isZeroLength(dashboardTimeFrame)) {
            special = dashboardTimeFrame;
        }
        result.setFrom(interval.getStart());
        result.setTo(interval.getEnd());

        AnalysisChartType chartType = widget.getChartType();
        boolean chartWidget = kind == DashboardResult.WidgetResult.Kind.SERIES;
        AggregationPeriod auto = autoAggregation(interval);
        WorkDays workDays = null;

        /* DataModelDataHandler.update */
        for (RowDefinition row : rows) {
            JEVisAttribute attribute = rowDataLoader.resolveAttribute(row);
            AggregationPeriod initial = row.getAggregation() != null ? row.getAggregation() : AggregationPeriod.NONE;
            ManipulationMode manipulation = row.getManipulation() != null ? row.getManipulation() : ManipulationMode.NONE;
            AggregationPeriod aggregation = initial;
            if (attribute != null) {
                aggregation = chooseAggregation(initial, auto, objectPeriod(attribute), chartWidget, row.getChartType(),
                        chartType, hasSumUnit(row));
            }

            DateTime start = interval.getStart();
            DateTime end = interval.getEnd();
            if (isDailyOrCoarser(aggregation)) {
                if (workDays == null && attribute != null) workDays = new WorkDays(attribute.getObject());
                TimeRange snapped = snapToDays(start, end, base.isCustomWorkDay(),
                        workDays != null ? workDays.getWorkdayStart() : null, workDays != null ? workDays.getWorkdayEnd() : null);
                start = snapped.getStart();
                end = snapped.getEnd();
            }

            RowResult rowResult = null;
            if (special != null && attribute != null) {
                DateTime first = attribute.getTimestampOfFirstSample();
                DateTime last = attribute.getTimestampOfLastSample();
                if (last == null) {
                    rowResult = new RowResult(row.getKey(), RowStatus.NO_DATA, null);
                    rowResult.setUnit(row.getUnitDisplay());
                } else if (DashboardTimeFrames.LAST_VALUE.equals(special)) {
                    start = last.withZone(base.getZone());
                    end = start;
                } else {
                    start = (first != null ? first : last).withZone(base.getZone());
                    end = last.withZone(base.getZone());
                }
            }

            if (rowResult == null) {
                EvaluationSettings settings = base.copy().setAggregation(aggregation).setManipulation(manipulation)
                        .setAbsolute(!chartWidget).setLastSampleOnly(zeroLength);
                rowResult = rowDataLoader.load(row, chartWidget ? chartType : null, new TimeRange(start, end), null, settings);
            }

            if (kind == DashboardResult.WidgetResult.Kind.VALUE && rowResult.getStatus() == RowStatus.OK
                    && rowResult.getSamples() != null && !rowResult.getSamples().isEmpty()) {
                /* ValueWidget & co.: results.get(0) of the absolute row */
                rowResult.setValue(rowResult.getSamples().get(0).getValue());
            } else if (kind == DashboardResult.WidgetResult.Kind.TEXT && rowResult.getStatus() == RowStatus.OK
                    && rowResult.getSamples() != null && !rowResult.getSamples().isEmpty()) {
                /* StringValueWidget: the last sample as string */
                Object value = rowResult.getSamples().get(rowResult.getSamples().size() - 1).getValue();
                rowResult.setText(value != null ? String.valueOf(value) : null);
            }
            if (WidgetDefinition.TYPE_TIME_FRAME.equals(type) && attribute != null) {
                /* TimeFrameWidget.getMaxTimeStamps: the latest sample of the attribute */
                try {
                    DateTime last = attribute.getTimestampOfLastSample();
                    rowResult.setLastTimestamp(last != null ? last.withZone(base.getZone()) : null);
                } catch (Exception e) {
                    logger.debug("Could not get the last timestamp of row {}", row.getKey(), e);
                }
            }
            result.getRows().add(rowResult);
        }
        return result;
    }

    /**
     * The input widgets show the latest sample of the attribute, independent of the time frame.
     */
    private RowResult latest(RowDefinition row) {
        RowResult result = new RowResult();
        result.setKey(row.getKey());
        try {
            if (ds.getObject(row.getObjectId()) == null) {
                return new RowResult(row.getKey(), RowStatus.NO_PERMISSION, null);
            }
            JEVisAttribute attribute = rowDataLoader.resolveAttribute(row);
            if (attribute == null) {
                result.setStatus(RowStatus.NO_DATA);
                result.setMessage("Object has no data attribute");
                return result;
            }
            try {
                result.setUnit(UnitManager.getInstance().format(attribute.getDisplayUnit()));
            } catch (Exception e) {
                result.setUnit(row.getUnitDisplay());
            }
            JEVisSample sample = attribute.getLatestSample();
            if (sample == null) {
                result.setStatus(RowStatus.NO_DATA);
                return result;
            }
            result.setStatus(RowStatus.OK);
            result.setLastTimestamp(sample.getTimestamp());
            Double value = null;
            try {
                value = sample.getValueAsDouble();
            } catch (Exception ignored) {
            }
            if (value != null) {
                result.setValue(value);
            } else {
                result.setText(sample.getValueAsString());
            }
        } catch (Exception e) {
            logger.error("Could not get the latest sample of row {}", row.getKey(), e);
            result.setStatus(RowStatus.ERROR);
            result.setMessage(e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : ""));
        }
        return result;
    }

    /**
     * Port of {@code AlarmCountWidget.countAlarms}: the logged alarms (JSON files of the attribute "Log File") of all
     * enabled alarm configurations, or of those at or below {@code filterId}, in the interval.
     */
    int countAlarms(TimeRange range, Long filterId) {
        int count = 0;
        try {
            JEVisClass alarmConfigClass = ds.getJEVisClass(AlarmConfiguration.CLASS_NAME);
            if (alarmConfigClass == null) return 0;
            for (JEVisObject object : ds.getObjects(alarmConfigClass, true)) {
                try {
                    if (filterId != null && !isSelfOrBelow(object, filterId, new HashSet<>())) continue;
                    AlarmConfiguration alarmConfiguration = new AlarmConfiguration(ds, object);
                    if (!Boolean.TRUE.equals(alarmConfiguration.isEnabled())) continue;
                    JEVisAttribute fileLog = alarmConfiguration.getFileLogAttribute();
                    if (fileLog == null || !fileLog.hasSample()) continue;
                    for (JEVisSample sample : fileLog.getSamples(range.getStart(), range.getEnd())) {
                        try {
                            JsonAlarm[] alarms = JsonTools.objectMapper().readValue(sample.getValueAsFile().getBytes(), JsonAlarm[].class);
                            count += alarms.length;
                        } catch (Exception e) {
                            logger.warn("Could not read alarm log of {}", object.getID(), e);
                        }
                    }
                } catch (Exception e) {
                    logger.warn("Could not count the alarms of {}", object.getID(), e);
                }
            }
        } catch (Exception e) {
            logger.error("Could not count alarms", e);
        }
        return count;
    }

    private static boolean isSelfOrBelow(JEVisObject object, Long parentId, Set<Long> visited) throws JEVisException {
        if (object == null || !visited.add(object.getID())) return false;
        if (object.getID().equals(parentId)) return true;
        List<JEVisObject> parents = object.getParents();
        if (parents == null) return false;
        for (JEVisObject parent : parents) {
            if (isSelfOrBelow(parent, parentId, visited)) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- aggregation (DataModelDataHandler)

    /**
     * {@code DataModelDataHandler.getAggregationPeriod}: under 6 days none, under 27 days hourly, under 364 days daily,
     * otherwise weekly.
     */
    static AggregationPeriod autoAggregation(TimeRange interval) {
        long days = new org.joda.time.Duration(interval.getStart(), interval.getEnd()).getStandardDays();
        if (days < 6) return AggregationPeriod.NONE;
        if (days < 27) return AggregationPeriod.HOURLY;
        if (days < 364) return AggregationPeriod.DAILY;
        return AggregationPeriod.WEEKLY;
    }

    /**
     * The aggregation of a row in {@code DataModelDataHandler.setInterval}.
     *
     * @param initial      the stored aggregation of the row
     * @param auto         the automatic aggregation of the interval ({@link #autoAggregation})
     * @param objectPeriod the period of the data row, {@code null} if unknown (then the stored aggregation is used)
     * @param chartWidget  {@code true} for Chart widgets
     * @param rowType      chart type of the row
     * @param chartType    chart type of the chart model
     * @param sumUnit      {@code true} if the unit of the row has a sum unit (rate such as kW)
     */
    static AggregationPeriod chooseAggregation(AggregationPeriod initial, AggregationPeriod auto, Period objectPeriod,
                                               boolean chartWidget, AnalysisChartType rowType, AnalysisChartType chartType,
                                               boolean sumUnit) {
        if (objectPeriod == null) return initial;
        if (new PeriodComparator().compare(period(auto), objectPeriod) < 0) {
            /* the data row period is bigger than the requested one */
            return AggregationPeriod.NONE;
        }
        if (initial == AggregationPeriod.NONE && chartWidget && rowType != AnalysisChartType.HEAT_MAP
                && chartType != AnalysisChartType.HEAT_MAP && !sumUnit) {
            return auto;
        }
        return initial;
    }

    static Period period(AggregationPeriod aggregation) {
        switch (aggregation) {
            case MINUTELY:
                return Period.minutes(1);
            case QUARTER_HOURLY:
                return Period.minutes(15);
            case HOURLY:
                return Period.hours(1);
            case DAILY:
                return Period.days(1);
            case WEEKLY:
                return Period.weeks(1);
            case MONTHLY:
                return Period.months(1);
            case QUARTERLY:
                return Period.months(3);
            case YEARLY:
                return Period.years(1);
            case THREEYEARS:
                return Period.years(3);
            case FIVEYEARS:
                return Period.years(5);
            case TENYEARS:
                return Period.years(10);
            case NONE:
            default:
                return Period.ZERO;
        }
    }

    static boolean isDailyOrCoarser(AggregationPeriod aggregation) {
        return aggregation != AggregationPeriod.NONE && aggregation != AggregationPeriod.MINUTELY
                && aggregation != AggregationPeriod.QUARTER_HOURLY && aggregation != AggregationPeriod.HOURLY;
    }

    /**
     * {@code DataModelDataHandler.update}: daily or coarser rows are read from 00:00:00 to 23:59:59 (milliseconds kept);
     * with the custom workday and a workday over midnight the start moves one day later.
     */
    static TimeRange snapToDays(DateTime start, DateTime end, boolean customWorkDay, LocalTime workdayStart, LocalTime workdayEnd) {
        DateTime s = start.withTimeAtStartOfDay().withMillisOfSecond(start.getMillisOfSecond());
        DateTime e = end.withTimeAtStartOfDay().plusDays(1).minusSeconds(1).withMillisOfSecond(end.getMillisOfSecond());
        if (customWorkDay && workdayStart != null && workdayEnd != null && workdayEnd.isBefore(workdayStart)) {
            s = s.plusDays(1);
        }
        return new TimeRange(s, e);
    }

    /**
     * @return the period of the data row (latest sample of "Period" of the object of the attribute), {@code null} if
     * it cannot be read (the Control Center then keeps the stored aggregation)
     */
    private static Period objectPeriod(JEVisAttribute attribute) {
        try {
            JEVisAttribute periodAttribute = attribute.getObject().getAttribute("Period");
            JEVisSample sample = periodAttribute != null ? periodAttribute.getLatestSample() : null;
            return sample != null ? new Period(sample.getValueAsString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code DataModelDataHandler.hasSumUnit}: rows with a rate unit (e.g. kW) are not aggregated automatically.
     */
    private static boolean hasSumUnit(RowDefinition row) {
        try {
            JEVisUnit unit = row.createUnit();
            JEVisUnit sumUnit = new QuantityUnits().getSumUnit(unit);
            return sumUnit != null && !sumUnit.equals(unit);
        } catch (Exception e) {
            return false;
        }
    }
}
