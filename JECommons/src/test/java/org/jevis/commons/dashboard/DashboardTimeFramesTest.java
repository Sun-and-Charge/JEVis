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
package org.jevis.commons.dashboard;

import org.jevis.commons.chart.TimeRange;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.time.LocalTime;

import static org.junit.Assert.*;

public class DashboardTimeFramesTest {
    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    /* a Friday */
    private static final DateTime NOW = new DateTime(2026, 11, 6, 12, 0, BERLIN);
    private final DashboardTimeFrames frames = new DashboardTimeFrames(BERLIN, null, null, NOW);

    private static void assertRange(TimeRange range, DateTime start, DateTime end) {
        assertNotNull(range);
        assertEquals(start.getMillis(), range.getStart().getMillis());
        assertEquals(end.getMillis(), range.getEnd().getMillis());
        assertEquals(BERLIN, range.getStart().getZone());
    }

    private static DateTime endOfDay(int year, int month, int day) {
        return new DateTime(year, month, day, 23, 59, 59, 999, BERLIN);
    }

    private static DateTime startOfDay(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0, BERLIN);
    }

    @Test
    public void calendarTimeFrames() {
        assertRange(frames.interval("P1D", null, false), startOfDay(2026, 11, 6), endOfDay(2026, 11, 6));
        assertRange(frames.interval("P1W", null, false), startOfDay(2026, 11, 2), endOfDay(2026, 11, 8));
        assertRange(frames.interval("P1M", null, false), startOfDay(2026, 11, 1), endOfDay(2026, 11, 30));
        assertRange(frames.interval("P1Y", null, false), startOfDay(2026, 1, 1), endOfDay(2026, 12, 31));
        assertRange(frames.interval("P3Y", null, false), startOfDay(2023, 1, 1), endOfDay(2025, 12, 31));
        assertRange(frames.interval("P5Y", null, false), startOfDay(2021, 1, 1), endOfDay(2025, 12, 31));
        assertRange(frames.interval("P10Y", null, false), startOfDay(2016, 1, 1), endOfDay(2025, 12, 31));
        /* a reference date in another month, over the change from summer time */
        assertRange(frames.interval("P1M", new DateTime(2026, 10, 20, 8, 0, BERLIN), false),
                startOfDay(2026, 10, 1), endOfDay(2026, 10, 31));
    }

    @Test
    public void rollingAndZeroLengthTimeFrames() {
        assertRange(frames.interval("PT24H", null, false), NOW.minusHours(24), NOW);
        assertRange(frames.interval("P7D", null, false), NOW.minusDays(7), NOW);
        assertRange(frames.interval("P30D", null, false), NOW.minusDays(30), NOW);
        assertRange(frames.interval("P365D", null, false), NOW.minusDays(365), NOW);
        assertRange(frames.interval("PALL", null, false), NOW, NOW);
        assertRange(frames.interval("LASTVALUE", null, false), NOW, NOW);
        assertTrue(DashboardTimeFrames.isZeroLength("PALL"));
        assertFalse(DashboardTimeFrames.isZeroLength("P1D"));
    }

    @Test
    public void futureDatesBecomeNow() {
        assertRange(frames.interval("P7D", NOW.plusDays(3), false), NOW.minusDays(7), NOW);
        assertRange(frames.interval("P1D", NOW.plusDays(3), false), startOfDay(2026, 11, 6), endOfDay(2026, 11, 6));
    }

    @Test
    public void workdayOverMidnightStartsTheDayBefore() {
        DashboardTimeFrames shifted = new DashboardTimeFrames(BERLIN, LocalTime.of(6, 0), LocalTime.of(5, 59, 59), NOW);
        assertRange(shifted.interval("P1W", null, false), new DateTime(2026, 11, 1, 6, 0, BERLIN),
                new DateTime(2026, 11, 8, 5, 59, 59, 999, BERLIN));
        /* the day has no workday in the Control Center */
        assertRange(shifted.interval("P1D", null, false), startOfDay(2026, 11, 6), endOfDay(2026, 11, 6));
    }

    @Test
    public void unknownAndCustomHaveNoInterval() {
        assertNull(frames.interval("CUSTOM", null, false));
        assertNull(frames.interval("P2D", null, false));
        assertNull(frames.interval(null, null, false));
        assertFalse(frames.isKnown("P2D"));
        assertFalse(frames.isKnown("4711"));
        assertTrue(frames.isKnown("CUSTOM"));
        assertTrue(frames.isKnown("LASTVALUE"));
        assertNull(frames.previous("CUSTOM", null));
        assertNull(frames.next("CUSTOM", null));
        assertNull(frames.previous("PALL", null));
    }

    @Test
    public void previousAndNext() {
        assertEquals(NOW.minusWeeks(1).getMillis(), frames.previous("P1W", null).getMillis());
        assertNull(frames.next("P1W", null));
        assertEquals(NOW.getMillis(), frames.next("P1W", NOW.minusWeeks(1)).getMillis());
        /* the next month of October is the current one: moved to now */
        assertEquals(NOW.getMillis(), frames.next("P1M", new DateTime(2026, 10, 15, 9, 0, BERLIN)).getMillis());
        assertEquals(new DateTime(2026, 8, 15, 9, 0, BERLIN).getMillis(),
                frames.next("P1M", new DateTime(2026, 7, 15, 9, 0, BERLIN)).getMillis());
        assertEquals(NOW.minusYears(3).getMillis(), frames.previous("P3Y", null).getMillis());
        assertEquals(NOW.minusHours(24).getMillis(), frames.previous("PT24H", null).getMillis());
        assertNull(frames.next("PT24H", null));
        assertEquals(NOW.getMillis(), frames.next("P7D", NOW.minusDays(1)).getMillis());
    }

    @Test
    public void optionsWithoutDataSourceAreTheStandardTimeFrames() {
        assertEquals(DashboardTimeFrames.STANDARD.size(), frames.options().size());
        assertEquals("P1D", frames.options().get(0).getId());
        assertFalse(frames.options().get(0).isCustom());
    }
}
