/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with JECommons. If not, see
 * <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dataprocessing.processor.preparation;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PrepareForecastTimeZoneTest {

    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");

    @Test
    public void hourlyIntervalsAreNotCorrectedOnTheDayOfTheChange() {
        DateTime start = new DateTime(2026, 3, 29, 0, 0, DateTimeZone.UTC); // 01:00 CET
        int offset = BERLIN.getOffset(start);
        DateTime next = start.plus(Period.hours(1));                          // 03:00 CEST
        assertEquals(next, PrepareForecast.fixTimeZoneOffset(BERLIN, next, offset, Period.hours(1)));
        assertEquals(next, PrepareForecast.fixTimeZoneOffset(BERLIN, next, offset, Period.minutes(15)));
    }

    @Test
    public void dailyIntervalsKeepTheLocalTimeOfDay() {
        DateTime start = new DateTime(2026, 3, 28, 23, 0, DateTimeZone.UTC); // 2026-03-29 00:00 CET
        int offset = BERLIN.getOffset(start);
        DateTime next = start.plus(Period.days(1));                          // 24 h later = 01:00 CEST
        DateTime fixed = PrepareForecast.fixTimeZoneOffset(BERLIN, next, offset, Period.days(1));
        assertEquals(new DateTime(2026, 3, 30, 0, 0, BERLIN).getMillis(), fixed.getMillis());
    }
}
