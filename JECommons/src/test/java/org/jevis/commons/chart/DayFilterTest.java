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

import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class DayFilterTest {
    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");

    private static List<ChartSample> days(DateTime first, int count) {
        List<ChartSample> samples = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            samples.add(new ChartSample(first.plusDays(i), (double) i, null));
        }
        return samples;
    }

    @Test
    public void excludesWeekdaysDatesAndHolidays() {
        /* Monday 2026-03-23 .. Sunday 2026-03-29 */
        List<ChartSample> week = days(new DateTime(2026, 3, 23, 12, 0, BERLIN), 7);
        DayFilter filter = DayFilter.of("6, 7", true, "2026-03-25");
        List<ChartSample> result = filter.filter(week, BERLIN, date -> date.equals(LocalDate.of(2026, 3, 26)));
        assertEquals(Arrays.asList(0d, 1d, 4d), values(result));
        /* input is not modified */
        assertEquals(7, week.size());
    }

    @Test
    public void usesTheGivenTimeZone() {
        /* Saturday 23:30 UTC is already Sunday in Berlin */
        List<ChartSample> samples = new ArrayList<>();
        samples.add(new ChartSample(new DateTime(2026, 3, 28, 23, 30, DateTimeZone.UTC), 1d, null));
        DayFilter sundays = DayFilter.of("7", false, "");
        assertTrue(sundays.filter(samples, BERLIN, null).isEmpty());
        assertEquals(1, sundays.filter(samples, DateTimeZone.UTC, null).size());
    }

    @Test
    public void inactiveFilterKeepsEverything() {
        DayFilter filter = DayFilter.of("", false, "  ");
        assertFalse(filter.isActive());
        List<ChartSample> samples = days(new DateTime(2026, 3, 23, 0, 0, BERLIN), 3);
        assertSame(samples, filter.filter(samples, BERLIN, null));
        /* invalid parts are ignored */
        assertEquals(1, DayFilter.parseWeekdays("8,x,3").size());
        assertEquals(1, DayFilter.parseDates("2026-01-01;nope").size());
    }

    @Test
    public void applicableUpToOneDay() {
        assertTrue(DayFilter.isApplicable(AggregationPeriod.NONE, null));
        assertTrue(DayFilter.isApplicable(AggregationPeriod.NONE, Period.minutes(15)));
        assertTrue(DayFilter.isApplicable(AggregationPeriod.NONE, Period.days(1)));
        assertFalse(DayFilter.isApplicable(AggregationPeriod.NONE, Period.months(1)));
        assertTrue(DayFilter.isApplicable(AggregationPeriod.HOURLY, null));
        assertTrue(DayFilter.isApplicable(AggregationPeriod.DAILY, null));
        assertFalse(DayFilter.isApplicable(AggregationPeriod.WEEKLY, null));
        assertFalse(DayFilter.isApplicable(AggregationPeriod.MONTHLY, null));
    }

    private static List<Double> values(List<ChartSample> samples) {
        List<Double> values = new ArrayList<>();
        for (ChartSample sample : samples) values.add((Double) sample.getValue());
        return values;
    }
}
