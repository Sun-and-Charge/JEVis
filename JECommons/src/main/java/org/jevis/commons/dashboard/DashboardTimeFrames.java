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
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.chart.TimeRange;
import org.jevis.commons.database.ObjectHandler;
import org.jevis.commons.datetime.CustomPeriodObject;
import org.jevis.commons.datetime.DateHelper;
import org.jevis.commons.datetime.WorkDays;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;

import java.time.LocalTime;
import java.util.*;

/**
 * The time frames of the dashboards, port of the Control Center's {@code TimeFrameFactory} and {@code LastPeriod}
 * (ids as stored in the dashboards):
 * <ul>
 *     <li>{@code P1D, P1W, P1M, P1Y, P3Y, P5Y, P10Y}: the day, week (Monday to Sunday), month, year or the last 3/5/10
 *     full years containing the reference date; except for the day with the workday of the dashboard's building
 *     ({@code applyWorkDay}),</li>
 *     <li>{@code PT24H, P7D, P30D, P365D}: rolling, ending at the reference date,</li>
 *     <li>{@code PALL}, {@code LASTVALUE}: zero length at the reference date (the rows are then evaluated from their
 *     first to their last sample, or at their last sample),</li>
 *     <li>{@code CUSTOM}: from/to of the request,</li>
 *     <li>custom periods: the id of a visible "Custom Period" object, resolved by {@link DateHelper}.</li>
 * </ul>
 * All dates are in the time zone of the user (the Control Center uses the time zone of the computer). Reference dates
 * in the future are replaced by now, like in the Control Center.
 * <p>
 * Previous/next: the Control Center's {@code nextPeriod/previousPeriod} do not work for all time frames (P3Y/P5Y/P10Y
 * skip or repeat periods, the rolling ones have none, P10Y covers 5 years); here the reference date is moved by the
 * length of the time frame (custom periods: by the length of their interval), and P10Y covers 10 years.
 */
public class DashboardTimeFrames {
    public static final String DAY = "P1D";
    public static final String WEEK = "P1W";
    public static final String MONTH = "P1M";
    public static final String YEAR = "P1Y";
    public static final String THREE_YEARS = "P3Y";
    public static final String FIVE_YEARS = "P5Y";
    public static final String TEN_YEARS = "P10Y";
    public static final String LAST_24_HOURS = "PT24H";
    public static final String LAST_7_DAYS = "P7D";
    public static final String LAST_30_DAYS = "P30D";
    public static final String LAST_365_DAYS = "P365D";
    public static final String ALL = "PALL";
    public static final String LAST_VALUE = "LASTVALUE";
    public static final String CUSTOM = "CUSTOM";
    public static final String CUSTOM_PERIOD_CLASS = "Custom Period";
    /**
     * The time frames a user can choose in the time frame bar, without the custom periods of the server.
     */
    public static final List<String> STANDARD = Collections.unmodifiableList(Arrays.asList(DAY, WEEK, MONTH, YEAR,
            THREE_YEARS, FIVE_YEARS, TEN_YEARS, LAST_24_HOURS, LAST_7_DAYS, LAST_30_DAYS, LAST_365_DAYS, ALL, CUSTOM));
    private static final Logger logger = LogManager.getLogger(DashboardTimeFrames.class);
    private final JEVisDataSource ds;
    private final DateTimeZone zone;
    private final WorkDays workDays;
    private final LocalTime workdayStart;
    private final LocalTime workdayEnd;
    private final DateTime now;
    private final Map<String, CustomPeriodObject> customPeriods = new HashMap<>();

    /**
     * @param ds       data source for the custom periods, may be {@code null}
     * @param zone     time zone of the user
     * @param workDays workday of the dashboard's building, may be {@code null} (00:00 - 23:59:59.999)
     * @param now      the current instant, {@code null} for the system clock
     */
    public DashboardTimeFrames(JEVisDataSource ds, DateTimeZone zone, WorkDays workDays, DateTime now) {
        this.ds = ds;
        this.zone = zone != null ? zone : DateTimeZone.getDefault();
        this.workDays = workDays;
        this.workdayStart = workDays != null ? workDays.getWorkdayStart() : null;
        this.workdayEnd = workDays != null ? workDays.getWorkdayEnd() : null;
        this.now = (now != null ? now : DateTime.now()).withZone(this.zone);
    }

    /**
     * Without data source (no custom periods), with an explicit workday (for tests).
     */
    public DashboardTimeFrames(DateTimeZone zone, LocalTime workdayStart, LocalTime workdayEnd, DateTime now) {
        this.ds = null;
        this.zone = zone != null ? zone : DateTimeZone.getDefault();
        this.workDays = null;
        this.workdayStart = workdayStart;
        this.workdayEnd = workdayEnd;
        this.now = (now != null ? now : DateTime.now()).withZone(this.zone);
    }

    /**
     * One entry of the time frame bar.
     */
    public static class Option {
        private final String id;
        private final String name;
        private final boolean custom;

        public Option(String id, String name, boolean custom) {
            this.id = id;
            this.name = name;
            this.custom = custom;
        }

        public String getId() {
            return id;
        }

        /**
         * @return the name of a custom period (object name), {@code null} for the standard time frames
         */
        public String getName() {
            return name;
        }

        /**
         * @return {@code true} for a custom period of the server
         */
        public boolean isCustom() {
            return custom;
        }
    }

    /**
     * @return the time frames of the time frame bar: {@link #STANDARD} and the visible custom periods (sorted by name)
     */
    public List<Option> options() {
        List<Option> options = new ArrayList<>();
        for (String id : STANDARD) options.add(new Option(id, null, false));
        List<Option> custom = new ArrayList<>();
        if (ds != null) {
            try {
                JEVisClass periodClass = ds.getJEVisClass(CUSTOM_PERIOD_CLASS);
                if (periodClass != null) {
                    for (JEVisObject object : ds.getObjects(periodClass, false)) {
                        CustomPeriodObject cpo = customPeriod(object);
                        if (cpo != null && Boolean.TRUE.equals(cpo.isVisible())) {
                            custom.add(new Option(String.valueOf(object.getID()), object.getName(), true));
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("Could not load the custom periods", e);
            }
        }
        custom.sort(Comparator.comparing(o -> o.getName() == null ? "" : o.getName().toLowerCase(Locale.ROOT)));
        options.addAll(custom);
        return options;
    }

    /**
     * @return {@code true} for {@link #STANDARD}, {@link #LAST_VALUE} and the ids of readable custom periods
     */
    public boolean isKnown(String id) {
        if (id == null) return false;
        if (STANDARD.contains(id) || LAST_VALUE.equals(id)) return true;
        return findCustomPeriod(id) != null;
    }

    /**
     * @return {@code true} for the time frames of zero length ({@link #ALL}, {@link #LAST_VALUE})
     */
    public static boolean isZeroLength(String id) {
        return ALL.equals(id) || LAST_VALUE.equals(id);
    }

    /**
     * The interval of a time frame for a reference date ({@code TimeFrame.getInterval(dateTime, fixed)}).
     *
     * @param id    the time frame id (not {@link #CUSTOM})
     * @param date  the reference date, {@code null} for now
     * @param fixed the "fixed time frame" switch of a widget: custom periods are resolved for now
     * @return the interval or {@code null} for unknown ids and {@link #CUSTOM}
     */
    public TimeRange interval(String id, DateTime date, boolean fixed) {
        if (id == null) return null;
        DateTime dateTime = date == null || date.isAfter(now) ? now : date.withZone(zone);
        switch (id) {
            case DAY:
                /* TimeFrameFactory.day() does not apply the workday */
                return new TimeRange(startOfDay(dateTime), endOfDay(dateTime));
            case WEEK:
                return applyWorkDay(startOfDay(dateTime.withDayOfWeek(1)), endOfDay(dateTime.withDayOfWeek(7)));
            case MONTH:
                return applyWorkDay(startOfDay(dateTime.withDayOfMonth(1)),
                        endOfDay(dateTime.withDayOfMonth(dateTime.dayOfMonth().getMaximumValue())));
            case YEAR:
                return applyWorkDay(startOfDay(dateTime.withDayOfYear(1)),
                        endOfDay(dateTime.withMonthOfYear(12).withDayOfMonth(31)));
            case THREE_YEARS:
                return lastYears(dateTime, 3);
            case FIVE_YEARS:
                return lastYears(dateTime, 5);
            case TEN_YEARS:
                /* the Control Center subtracts 5 years here (copy of fiveYears()) */
                return lastYears(dateTime, 10);
            case LAST_24_HOURS:
            case LAST_7_DAYS:
            case LAST_30_DAYS:
            case LAST_365_DAYS:
                return new TimeRange(dateTime.minus(new Period(id)), dateTime);
            case ALL:
            case LAST_VALUE:
                return new TimeRange(dateTime, dateTime);
            case CUSTOM:
                return null;
            default:
                return customPeriodInterval(id, dateTime, fixed);
        }
    }

    /**
     * @return the reference date of the period before the one of {@code date}, {@code null} if there is none
     * ({@link #ALL}, {@link #LAST_VALUE}, {@link #CUSTOM}, custom periods ending now)
     */
    public DateTime previous(String id, DateTime date) {
        DateTime dateTime = date == null || date.isAfter(now) ? now : date.withZone(zone);
        Period step = step(id);
        if (step != null) return dateTime.minus(step);
        CustomPeriodObject cpo = findCustomPeriod(id);
        if (cpo == null || "NOW".equals(cpo.getEndReferencePoint())) return null;
        TimeRange range = interval(id, dateTime, false);
        if (range == null) return null;
        return dateTime.minus(customLength(range));
    }

    /**
     * @return the reference date of the period after the one of {@code date}; {@code null} if the period of
     * {@code date} already contains now (or there is no next period, see {@link #previous})
     */
    public DateTime next(String id, DateTime date) {
        DateTime dateTime = date == null || date.isAfter(now) ? now : date.withZone(zone);
        TimeRange range = interval(id, dateTime, false);
        if (range == null || !range.getEnd().isBefore(now)) return null;
        DateTime next;
        Period step = step(id);
        if (step != null) {
            next = dateTime.plus(step);
        } else {
            CustomPeriodObject cpo = findCustomPeriod(id);
            if (cpo == null || "NOW".equals(cpo.getEndReferencePoint())) return null;
            next = dateTime.plus(customLength(range));
        }
        if (next.isAfter(now)) next = now;
        TimeRange nextRange = interval(id, next, false);
        if (nextRange == null || nextRange.getStart().isAfter(now)) return null;
        return next;
    }

    private static Period step(String id) {
        if (id == null) return null;
        switch (id) {
            case DAY:
            case WEEK:
            case MONTH:
            case YEAR:
            case THREE_YEARS:
            case FIVE_YEARS:
            case TEN_YEARS:
            case LAST_24_HOURS:
            case LAST_7_DAYS:
            case LAST_30_DAYS:
            case LAST_365_DAYS:
                return new Period(id);
            default:
                return null;
        }
    }

    /**
     * Length of a custom period like {@code nextPeriod/previousPeriod} of the Control Center: an end at second 59 counts
     * as the full minute.
     */
    private static long customLength(TimeRange range) {
        DateTime end = range.getEnd();
        long endMillis = end.getSecondOfMinute() == 59 ? end.plusSeconds(1).withMillisOfSecond(0).getMillis() : end.getMillis();
        return Math.max(0L, endMillis - range.getStart().getMillis());
    }

    private TimeRange lastYears(DateTime dateTime, int years) {
        DateTime start = startOfDay(dateTime.withDayOfYear(1)).minusYears(years);
        DateTime end = endOfDay(dateTime.withMonthOfYear(12).withDayOfMonth(31)).minusYears(1);
        return applyWorkDay(start, end);
    }

    private static DateTime startOfDay(DateTime dateTime) {
        return dateTime.withTimeAtStartOfDay();
    }

    private static DateTime endOfDay(DateTime dateTime) {
        return dateTime.withTimeAtStartOfDay().plusDays(1).minusMillis(1);
    }

    /**
     * Port of {@code TimeFrameFactory.applyWorkDay}: start/end at the workday times, a workday over midnight starts the
     * day before.
     */
    private TimeRange applyWorkDay(DateTime start, DateTime end) {
        if (workdayStart != null && workdayEnd != null) {
            start = withTime(start, workdayStart);
            end = withTime(end, workdayEnd);
            if (workdayEnd.isBefore(workdayStart)) {
                start = start.minusDays(1);
            }
        }
        return new TimeRange(start, end);
    }

    /**
     * Sets hour, minute and second (keeps the milliseconds like the Control Center); local times in a daylight saving
     * gap are moved forward.
     */
    private static DateTime withTime(DateTime dateTime, LocalTime time) {
        try {
            return dateTime.withTime(time.getHour(), time.getMinute(), time.getSecond(), dateTime.getMillisOfSecond());
        } catch (IllegalArgumentException e) {
            return dateTime.withTime(time.getHour(), 0, 0, 0).plusHours(1)
                    .withMinuteOfHour(time.getMinute()).withSecondOfMinute(time.getSecond());
        }
    }

    // ---------------------------------------------------------------- custom periods

    private CustomPeriodObject findCustomPeriod(String id) {
        if (ds == null || id == null) return null;
        if (customPeriods.containsKey(id)) return customPeriods.get(id);
        CustomPeriodObject cpo = null;
        try {
            long objectId = Long.parseLong(id.trim());
            JEVisObject object = ds.getObject(objectId);
            if (object != null && CUSTOM_PERIOD_CLASS.equals(object.getJEVisClassName())) {
                cpo = customPeriod(object);
            }
        } catch (NumberFormatException e) {
            logger.debug("Not a custom period: {}", id);
        } catch (Exception e) {
            logger.error("Could not get custom period {}", id, e);
        }
        customPeriods.put(id, cpo);
        return cpo;
    }

    private CustomPeriodObject customPeriod(JEVisObject object) {
        try {
            return new CustomPeriodObject(object, new ObjectHandler(ds));
        } catch (Exception e) {
            logger.error("Could not read custom period {}", object.getID(), e);
            return null;
        }
    }

    /**
     * {@code TimeFrameFactory.customPeriodObject(cpo).getInterval}: {@link DateHelper} computes in the time zone of the
     * server, so the reference date is passed with the local fields of the user and the result converted back.
     */
    private TimeRange customPeriodInterval(String id, DateTime dateTime, boolean fixed) {
        CustomPeriodObject cpo = findCustomPeriod(id);
        if (cpo == null) return null;
        try {
            DateTimeZone serverZone = DateTimeZone.getDefault();
            DateHelper dateHelper = new DateHelper();
            dateHelper.setCustomPeriodObject(cpo);
            dateHelper.setType(DateHelper.TransformType.CUSTOM_PERIOD);
            /* fixed: the Control Center does not set the date, DateHelper then uses now */
            DateTime reference = fixed ? now : dateTime;
            dateHelper.setCurrentDate(reference.withZoneRetainFields(serverZone));
            if (workDays != null) dateHelper.setWorkDays(workDays);
            DateTime start = dateHelper.getStartDate();
            DateTime end = dateHelper.getEndDate();
            if (start == null || end == null) return null;
            start = start.withZone(serverZone).withZoneRetainFields(zone);
            end = end.withZone(serverZone).withZoneRetainFields(zone);
            if (end.isBefore(start)) return null;
            return new TimeRange(start, end);
        } catch (Exception e) {
            logger.error("Could not resolve custom period {}", id, e);
            return null;
        }
    }

    public DateTimeZone getZone() {
        return zone;
    }

    public DateTime getNow() {
        return now;
    }
}
