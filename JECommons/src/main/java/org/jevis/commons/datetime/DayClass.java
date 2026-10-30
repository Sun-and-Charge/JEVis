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

import java.time.LocalDate;
import java.util.function.Predicate;

/**
 * Day class matching for "bind to weekday" in gap filling and forecast. Holidays form their own day
 * class: a holiday is matched with other holidays, a normal weekday only with the same weekday that is
 * not a holiday. Without holiday information ({@code holidays == null}) only the weekday is compared.
 */
public final class DayClass {

    private DayClass() {
    }

    /**
     * @param reference timestamp of a reference sample
     * @param target    timestamp that is filled/forecast
     * @param holidays  holiday check, may be {@code null}
     * @return true if the reference belongs to the same day class as the target
     */
    public static boolean sameWeekdayClass(DateTime reference, DateTime target, Predicate<LocalDate> holidays) {
        if (holidays == null) {
            return reference.getDayOfWeek() == target.getDayOfWeek();
        }
        boolean targetHoliday = isHoliday(target, holidays);
        boolean referenceHoliday = isHoliday(reference, holidays);
        if (targetHoliday) {
            return referenceHoliday;
        }
        return !referenceHoliday && reference.getDayOfWeek() == target.getDayOfWeek();
    }

    public static boolean isHoliday(DateTime dateTime, Predicate<LocalDate> holidays) {
        return holidays != null && dateTime != null && holidays.test(HolidayCalendar.toLocalDate(dateTime));
    }
}
