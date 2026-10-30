/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.datetime;

import org.joda.time.DateTime;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * Tests for the holiday aware weekday matching in {@link DayClass} and {@link HolidayCalendar}.
 */
public class DayClassTest {

    // 2026-12-25 (Friday) and 2026-05-14 (Thursday) are holidays
    private static final Set<LocalDate> HOLIDAYS = new HashSet<>(Arrays.asList(
            LocalDate.of(2026, 12, 25), LocalDate.of(2026, 5, 14)));
    private static final Predicate<LocalDate> IS_HOLIDAY = HOLIDAYS::contains;

    private static DateTime day(int year, int month, int day) {
        return new DateTime(year, month, day, 8, 0);
    }

    @Test
    public void withoutHolidayInfoOnlyTheWeekdayCounts() {
        DateTime target = day(2026, 12, 25); // Friday
        assertTrue(DayClass.sameWeekdayClass(day(2026, 12, 18), target, null));
        assertFalse(DayClass.sameWeekdayClass(day(2026, 5, 14), target, null));
        assertTrue(DayClass.sameWeekdayClass(day(2026, 12, 25), day(2027, 1, 1), null));
    }

    @Test
    public void normalWeekdayExcludesHolidays() {
        DateTime target = day(2027, 1, 8); // Friday, no holiday
        assertTrue(DayClass.sameWeekdayClass(day(2026, 12, 18), target, IS_HOLIDAY));
        assertFalse("holiday Friday must not be a reference for a normal Friday",
                DayClass.sameWeekdayClass(day(2026, 12, 25), target, IS_HOLIDAY));
        assertFalse(DayClass.sameWeekdayClass(day(2026, 12, 17), target, IS_HOLIDAY));
    }

    @Test
    public void holidayMatchesOtherHolidaysOnly() {
        DateTime target = day(2026, 12, 25); // holiday Friday
        assertTrue("holiday Thursday is a reference for a holiday",
                DayClass.sameWeekdayClass(day(2026, 5, 14), target, IS_HOLIDAY));
        assertFalse("normal Friday is no reference for a holiday",
                DayClass.sameWeekdayClass(day(2026, 12, 18), target, IS_HOLIDAY));
    }

    @Test
    public void isHolidayHandlesMissingInfo() {
        assertFalse(DayClass.isHoliday(day(2026, 12, 25), null));
        assertTrue(DayClass.isHoliday(day(2026, 12, 25), IS_HOLIDAY));
        assertFalse(DayClass.isHoliday(day(2026, 12, 24), IS_HOLIDAY));
    }

    @Test
    public void parseCountryAndState() {
        assertArrayEquals(new String[]{"DE", "nw"}, HolidayCalendar.parseCountryAndState("DE,nw"));
        assertArrayEquals(new String[]{"DE", ""}, HolidayCalendar.parseCountryAndState("DE"));
        assertNull(HolidayCalendar.parseCountryAndState(""));
        assertNull(HolidayCalendar.parseCountryAndState(null));
    }

    @Test
    public void jollydayCountryAndState() {
        HolidayCalendar nrw = new HolidayCalendar(HolidayCalendar.createCountryManager("de"), "nw", null);
        HolidayCalendar berlin = new HolidayCalendar(HolidayCalendar.createCountryManager("de"), "be", null);

        assertTrue(nrw.isHoliday(LocalDate.of(2026, 12, 25)));
        assertFalse(nrw.isHoliday(LocalDate.of(2026, 12, 18)));
        // Corpus Christi is a holiday in North Rhine-Westphalia but not in Berlin
        assertTrue(nrw.isHoliday(LocalDate.of(2026, 6, 4)));
        assertFalse(berlin.isHoliday(LocalDate.of(2026, 6, 4)));
        assertTrue(nrw.isHoliday(new DateTime(2026, 10, 3, 12, 0)));
    }
}
