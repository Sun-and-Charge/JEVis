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

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.time.LocalTime;

import static org.junit.Assert.assertEquals;

public class TimeFrameResolverTest {
    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    private static final LocalTime DAY_START = LocalTime.of(0, 0, 0, 0);
    private static final LocalTime DAY_END = LocalTime.of(23, 59, 59, 999999999);
    /* Tuesday */
    private static final DateTime NOW = new DateTime(2026, 3, 31, 10, 15, 30, 123, BERLIN);

    private static TimeRange resolve(TimeFrame timeFrame, DateTime now) {
        return TimeFrameResolver.resolve(timeFrame, null, null, DAY_START, DAY_END, BERLIN, now);
    }

    private static DateTime berlin(int y, int m, int d, int h, int min, int s) {
        return new DateTime(y, m, d, h, min, s, 0, BERLIN);
    }

    private static void assertRange(TimeRange range, DateTime start, DateTime end) {
        assertEquals("start", start, range.getStart());
        assertEquals("end", end, range.getEnd());
    }

    @Test
    public void standardTimeFrames() {
        assertRange(resolve(TimeFrame.TODAY, NOW), berlin(2026, 3, 31, 0, 0, 0), NOW);
        /* end with second precision like DateHelper: 23:59:59.000 */
        assertRange(resolve(TimeFrame.YESTERDAY, NOW), berlin(2026, 3, 30, 0, 0, 0), berlin(2026, 3, 30, 23, 59, 59));
        assertRange(resolve(TimeFrame.LAST_7_DAYS, NOW), berlin(2026, 3, 24, 0, 0, 0), NOW);
        assertRange(resolve(TimeFrame.THIS_WEEK, NOW), berlin(2026, 3, 30, 0, 0, 0), NOW);
        assertRange(resolve(TimeFrame.LAST_WEEK, NOW), berlin(2026, 3, 23, 0, 0, 0), berlin(2026, 3, 29, 23, 59, 59));
        assertRange(resolve(TimeFrame.LAST_30_DAYS, NOW), berlin(2026, 3, 1, 0, 0, 0), NOW);
        assertRange(resolve(TimeFrame.THIS_MONTH, NOW), berlin(2026, 3, 1, 0, 0, 0), NOW);
        assertRange(resolve(TimeFrame.THIS_YEAR, NOW), berlin(2026, 1, 1, 0, 0, 0), NOW);
        assertRange(resolve(TimeFrame.LAST_YEAR, NOW), berlin(2025, 1, 1, 0, 0, 0), berlin(2025, 12, 31, 23, 59, 59));
        assertRange(resolve(TimeFrame.THE_YEAR_BEFORE_LAST, NOW), berlin(2024, 1, 1, 0, 0, 0), berlin(2024, 12, 31, 23, 59, 59));
    }

    @Test
    public void lastMonthAtEndOfMonth() {
        /* DateHelper would start on January 29 (February 28 minus 30 days) */
        assertRange(resolve(TimeFrame.LAST_MONTH, NOW), berlin(2026, 2, 1, 0, 0, 0), berlin(2026, 2, 28, 23, 59, 59));
        DateTime midMonth = berlin(2026, 5, 15, 8, 0, 0);
        assertRange(resolve(TimeFrame.LAST_MONTH, midMonth), berlin(2026, 4, 1, 0, 0, 0), berlin(2026, 4, 30, 23, 59, 59));
    }

    @Test
    public void workdayStartAfterEndStartsTheDayBefore() {
        LocalTime start = LocalTime.of(6, 0);
        LocalTime end = LocalTime.of(5, 59, 59);
        TimeRange today = TimeFrameResolver.resolve(TimeFrame.TODAY, null, null, start, end, BERLIN, NOW);
        assertRange(today, berlin(2026, 3, 30, 6, 0, 0), NOW);
        TimeRange yesterday = TimeFrameResolver.resolve(TimeFrame.YESTERDAY, null, null, start, end, BERLIN, NOW);
        assertRange(yesterday, berlin(2026, 3, 29, 6, 0, 0), berlin(2026, 3, 30, 5, 59, 59));
    }

    @Test
    public void workdayWithinOneDay() {
        TimeRange range = TimeFrameResolver.resolve(TimeFrame.YESTERDAY, null, null, LocalTime.of(7, 30), LocalTime.of(18, 0),
                BERLIN, NOW);
        assertRange(range, berlin(2026, 3, 30, 7, 30, 0), berlin(2026, 3, 30, 18, 0, 0));
    }

    @Test
    public void daylightSavingTime() {
        /* spring forward on 2026-03-29 02:00 -> 03:00 */
        DateTime springDay = berlin(2026, 3, 29, 12, 0, 0);
        TimeRange today = resolve(TimeFrame.TODAY, springDay);
        assertEquals(3600000, BERLIN.getOffset(today.getStart()));
        assertEquals(berlin(2026, 3, 29, 0, 0, 0), today.getStart());
        /* 7 days back keeps the local start of the day */
        TimeRange week = resolve(TimeFrame.LAST_7_DAYS, berlin(2026, 4, 2, 9, 0, 0));
        assertEquals(berlin(2026, 3, 26, 0, 0, 0), week.getStart());

        /* a workday start inside the gap does not fail, it moves behind the gap */
        TimeRange gap = TimeFrameResolver.resolve(TimeFrame.TODAY, null, null, LocalTime.of(2, 30), LocalTime.of(23, 59, 59),
                BERLIN, springDay);
        assertEquals(new DateTime(2026, 3, 29, 1, 30, 0, 0, DateTimeZone.UTC).getMillis(), gap.getStart().getMillis());

        /* fall back on 2026-10-25 03:00 -> 02:00: the day has 25 hours */
        TimeRange yesterday = resolve(TimeFrame.YESTERDAY, berlin(2026, 10, 26, 9, 0, 0));
        assertEquals(25L * 3600 * 1000 - 1000, yesterday.getEnd().getMillis() - yesterday.getStart().getMillis());
    }

    @Test
    public void explicitTimeZone() {
        DateTimeZone newYork = DateTimeZone.forID("America/New_York");
        /* 2026-03-31 02:00 in Berlin is still March 30 in New York */
        DateTime instant = new DateTime(2026, 3, 31, 2, 0, 0, 0, BERLIN);
        TimeRange range = TimeFrameResolver.resolve(TimeFrame.TODAY, null, null, DAY_START, DAY_END, newYork, instant);
        assertEquals(new DateTime(2026, 3, 30, 0, 0, 0, 0, newYork), range.getStart());
        assertEquals(newYork, range.getStart().getZone());
    }

    @Test
    public void custom() {
        DateTime from = new DateTime(2026, 1, 5, 0, 0, 0, 0, DateTimeZone.UTC);
        DateTime to = new DateTime(2026, 1, 6, 0, 0, 0, 0, DateTimeZone.UTC);
        TimeRange range = TimeFrameResolver.resolve(TimeFrame.CUSTOM, from, to, DAY_START, DAY_END, BERLIN, NOW);
        assertEquals(from.getMillis(), range.getStart().getMillis());
        assertEquals(to.getMillis(), range.getEnd().getMillis());
        assertEquals(BERLIN, range.getStart().getZone());
    }
}
