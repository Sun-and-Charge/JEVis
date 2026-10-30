/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.charts;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.GapFillingBoundToSpecific;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.forecast.ForecastCalculator;
import org.jevis.commons.datetime.HolidayCalendar;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Forecast that is calculated on the fly from the loaded samples of one data row of an analysis (#2242).
 * Uses the forecast core of JECommons ({@link ForecastCalculator}); the result is not stored.
 */
public class OnTheFlyForecast {
    public static final GapFillingType[] METHODS = {GapFillingType.AVERAGE, GapFillingType.MEDIAN,
            GapFillingType.REGRESSION, GapFillingType.MINIMUM, GapFillingType.MAXIMUM};
    private static final Logger logger = LogManager.getLogger(OnTheFlyForecast.class);
    private static final int MAX_FORECAST_SAMPLES = 100000;

    private final String rowKey;
    private final Long analysisId;
    private final GapFillingType method;
    private final int horizonDays;

    public OnTheFlyForecast(ChartDataRow row, JEVisObject analysis, GapFillingType method, int horizonDays) {
        this.rowKey = key(row);
        this.analysisId = analysis != null ? analysis.getID() : null;
        this.method = method;
        this.horizonDays = horizonDays;
    }

    public static String key(ChartDataRow row) {
        return row.getId() + "|" + row.getAttributeString() + "|" + row.getName();
    }

    public static String getMethodName(GapFillingType type) {
        switch (type) {
            case MINIMUM:
                return I18n.getInstance().getString("graph.dialog.note.text.forecast.min");
            case MAXIMUM:
                return I18n.getInstance().getString("graph.dialog.note.text.forecast.max");
            case MEDIAN:
                return I18n.getInstance().getString("graph.dialog.note.text.forecast.median");
            case REGRESSION:
                return I18n.getInstance().getString("graph.dialog.note.text.forecast.regression");
            case AVERAGE:
            default:
                return I18n.getInstance().getString("graph.dialog.note.text.forecast.average");
        }
    }

    public boolean appliesTo(ChartDataRow row, JEVisObject currentAnalysis) {
        Long currentId = currentAnalysis != null ? currentAnalysis.getID() : null;
        if (analysisId == null ? currentId != null : !analysisId.equals(currentId)) {
            return false;
        }
        return rowKey.equals(key(row));
    }

    public GapFillingType getMethod() {
        return method;
    }

    public int getHorizonDays() {
        return horizonDays;
    }

    /**
     * Calculates the forecast for the next {@link #getHorizonDays()} days after the last loaded sample of the row.
     */
    public List<JEVisSample> compute(ChartDataRow row) {
        List<JEVisSample> result = new ArrayList<>();
        try {
            List<JEVisSample> samples = row.getSamples();
            if (samples == null || samples.size() < 2) {
                return result;
            }

            DateTime last = samples.get(samples.size() - 1).getTimestamp();
            Period step = getStep(row, samples);
            if (step == null) {
                return result;
            }

            Duration stepDuration = new Duration(last, last.plus(step));
            GapFillingBoundToSpecific bind = stepDuration.isShorterThan(Duration.standardDays(7))
                    ? GapFillingBoundToSpecific.WEEKDAY : GapFillingBoundToSpecific.NONE;
            HolidayCalendar holidays = bind == GapFillingBoundToSpecific.WEEKDAY ? HolidayCalendar.forObject(row.getObject()) : null;

            // group the references by time of day, the calculator only uses samples of the same slot
            Map<Integer, List<JEVisSample>> slots = new HashMap<>();
            for (JEVisSample sample : samples) {
                DateTime ts = sample.getTimestamp();
                slots.computeIfAbsent(ts.getMinuteOfDay(), k -> new ArrayList<>()).add(sample);
            }

            DateTime end = last.plusDays(horizonDays);
            DateTime ts = last.plus(step);
            while (!ts.isAfter(end) && result.size() < MAX_FORECAST_SAMPLES) {
                List<JEVisSample> slot = slots.get(ts.getMinuteOfDay());
                Double value = slot == null ? null : ForecastCalculator.forecastValue(slot, ts, method, bind, holidays);
                if (value != null) {
                    result.add(new VirtualSample(ts, value));
                }
                ts = ts.plus(step);
            }
        } catch (Exception e) {
            logger.error("Could not calculate forecast for {}", row.getName(), e);
        }
        return result;
    }

    private Period getStep(ChartDataRow row, List<JEVisSample> samples) throws Exception {
        AggregationPeriod aggregationPeriod = row.getAggregationPeriod();
        if (aggregationPeriod != null && aggregationPeriod != AggregationPeriod.NONE) {
            Period period = AggregationPeriod.getJodaPeriod(aggregationPeriod);
            if (period != null && !period.equals(Period.ZERO)) {
                return period;
            }
        }
        Period period = row.getPeriod();
        if (period != null && !period.equals(Period.ZERO)) {
            return period;
        }
        DateTime last = samples.get(samples.size() - 1).getTimestamp();
        DateTime beforeLast = samples.get(samples.size() - 2).getTimestamp();
        long millis = last.getMillis() - beforeLast.getMillis();
        return millis > 0 ? new Period(millis) : null;
    }
}
