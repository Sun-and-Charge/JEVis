/**
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
package org.jevis.commons.chart;

import org.jevis.api.JEVisObject;
import org.jevis.commons.datetime.WorkDays;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.chrono.ISOChronology;

import java.time.LocalTime;

/**
 * Resolves a {@link TimeFrame} into start and end like the Control Center's {@code AnalysisTimeFrame.updateDates()}
 * with {@code org.jevis.commons.datetime.DateHelper}, including the workday start/end of the building
 * ({@link WorkDays}), but with an explicit time zone and "now" instead of the JVM defaults.
 * <p>
 * Like the Control Center: start and end are built from the workday start/end with second precision (milliseconds 0),
 * so e.g. YESTERDAY ends at 23:59:59.000; if the workday start is after the workday end (e.g. 06:00 - 05:59:59)
 * the start is moved one day back; time frames up to "now" end at now.
 */
public final class TimeFrameResolver {

    private TimeFrameResolver() {
    }

    /**
     * Resolves a time frame with the workday of the building of the given analysis (or of the first visible
     * building, see {@link WorkDays}); {@code analysis} may be {@code null} for 00:00:00 - 23:59:59.
     *
     * @param timeFrame the time frame
     * @param from      start for {@link TimeFrame#CUSTOM}, ignored otherwise
     * @param to        end for {@link TimeFrame#CUSTOM}, ignored otherwise
     * @param analysis  the analysis object whose building defines the workday, or {@code null}
     * @param zone      the time zone of the user
     */
    public static TimeRange resolve(TimeFrame timeFrame, DateTime from, DateTime to, JEVisObject analysis, DateTimeZone zone) {
        WorkDays workDays = new WorkDays(analysis);
        return resolve(timeFrame, from, to, workDays.getWorkdayStart(), workDays.getWorkdayEnd(), zone, DateTime.now(zone));
    }

    /**
     * Resolves a time frame with explicit workday start/end and "now" (for tests).
     *
     * @param timeFrame    the time frame
     * @param from         start for {@link TimeFrame#CUSTOM}, ignored otherwise
     * @param to           end for {@link TimeFrame#CUSTOM}, ignored otherwise
     * @param workdayStart start of the workday (Control Center: {@code WorkDays.getWorkdayStart()})
     * @param workdayEnd   end of the workday (Control Center: {@code WorkDays.getWorkdayEnd()})
     * @param zone         the time zone of the user
     * @param now          the current instant
     */
    public static TimeRange resolve(TimeFrame timeFrame, DateTime from, DateTime to, LocalTime workdayStart, LocalTime workdayEnd,
                                    DateTimeZone zone, DateTime now) {
        if (timeFrame == null) throw new IllegalArgumentException("No time frame");
        if (zone == null) zone = DateTimeZone.getDefault();
        if (workdayStart == null) workdayStart = LocalTime.of(0, 0, 0, 0);
        if (workdayEnd == null) workdayEnd = LocalTime.of(23, 59, 59, 999999999);
        now = now.withZone(zone);

        boolean shift = workdayStart.isAfter(workdayEnd);
        int year = now.getYear();
        int month = now.getMonthOfYear();
        int day = now.getDayOfMonth();
        DateTime todayStart = at(zone, year, month, day, workdayStart);
        DateTime todayEnd = at(zone, year, month, day, workdayEnd);

        DateTime start;
        DateTime end;
        switch (timeFrame) {
            case CUSTOM:
                if (from == null || to == null) throw new IllegalArgumentException("CUSTOM needs from and to");
                return new TimeRange(from.withZone(zone), to.withZone(zone));
            case TODAY:
                start = todayStart;
                end = now;
                break;
            case YESTERDAY:
                start = todayStart.minusDays(1);
                end = todayEnd.minusDays(1);
                break;
            case LAST_7_DAYS:
                start = todayStart.minusDays(7);
                end = now;
                break;
            case LAST_30_DAYS:
                start = todayStart.minusDays(30);
                end = now;
                break;
            case THIS_WEEK:
                start = todayStart.minusDays(now.getDayOfWeek() - 1);
                end = now;
                break;
            case LAST_WEEK:
                start = todayStart.minusDays(now.getDayOfWeek() - 1).minusWeeks(1);
                end = todayEnd.minusDays(now.getDayOfWeek() - 1).minusWeeks(1).plusDays(6);
                break;
            case THIS_MONTH:
                start = todayStart.minusDays(day - 1);
                end = now;
                break;
            case LAST_MONTH:
                /* DateHelper: minusMonths(1).minusDays(day - 1), which goes back too far at the end of a month
                 * (e.g. on March 31: February 28 minus 30 days = January 29); first day of this month minus one month */
                start = todayStart.minusDays(day - 1).minusMonths(1);
                end = todayEnd.minusDays(day - 1).minusDays(1);
                break;
            case THIS_YEAR:
                start = at(zone, year, 1, 1, workdayStart);
                end = now;
                break;
            case LAST_YEAR:
                start = at(zone, year, 1, 1, workdayStart).minusYears(1);
                end = at(zone, year, 1, 1, workdayEnd).minusDays(1);
                break;
            case THE_YEAR_BEFORE_LAST:
                start = at(zone, year, 1, 1, workdayStart).minusYears(2);
                end = at(zone, year, 1, 1, workdayEnd).minusYears(1).minusDays(1);
                break;
            default:
                throw new IllegalArgumentException("Unsupported time frame " + timeFrame);
        }
        if (shift) start = start.minusDays(1);
        return new TimeRange(start, end);
    }

    /**
     * Like {@code new DateTime(y, m, d, h, min, s)} of the Control Center, but in the given zone and without failing
     * for a local time in a daylight saving gap (moved forward by the length of the gap instead).
     */
    static DateTime at(DateTimeZone zone, int year, int month, int day, LocalTime time) {
        ISOChronology utc = ISOChronology.getInstanceUTC();
        long local = utc.getDateTimeMillis(year, month, day, time.getHour(), time.getMinute(), time.getSecond(), 0);
        return new DateTime(zone.convertLocalToUTC(local, false), zone);
    }
}
