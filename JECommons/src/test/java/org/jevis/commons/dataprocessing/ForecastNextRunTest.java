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
package org.jevis.commons.dataprocessing;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ForecastNextRunTest {

    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    private static final long HOUR = 3600000L;
    private static final long DAY = 24 * HOUR;

    @Test
    public void hourlyCycleAdvancesOnTheDayOfTheSpringChange() {
        // 2026-03-29 01:00 CET = 00:00 UTC; one hour later is 03:00 CEST = 01:00 UTC
        DateTime lastRun = new DateTime(2026, 3, 29, 0, 0, DateTimeZone.UTC);
        DateTime next = ForecastDataObject.nextRun(lastRun, HOUR, BERLIN);
        assertEquals(new DateTime(2026, 3, 29, 1, 0, DateTimeZone.UTC).getMillis(), next.getMillis());
        assertTrue(next.isAfter(lastRun));
    }

    @Test
    public void hourlyCycleAdvancesOnTheDayOfTheAutumnChange() {
        DateTime lastRun = new DateTime(2026, 10, 25, 0, 0, DateTimeZone.UTC); // 02:00 CEST
        DateTime next = ForecastDataObject.nextRun(lastRun, HOUR, BERLIN);
        assertEquals(new DateTime(2026, 10, 25, 1, 0, DateTimeZone.UTC).getMillis(), next.getMillis());
    }

    @Test
    public void dailyCycleKeepsMidnightAcrossTheChange() {
        DateTime lastRun = new DateTime(2026, 3, 29, 0, 0, BERLIN);
        DateTime next = ForecastDataObject.nextRun(lastRun, DAY, BERLIN);
        assertEquals(new DateTime(2026, 3, 30, 0, 0, BERLIN).getMillis(), next.getMillis());

        lastRun = new DateTime(2026, 10, 25, 0, 0, BERLIN);
        next = ForecastDataObject.nextRun(lastRun, DAY, BERLIN);
        assertEquals(new DateTime(2026, 10, 26, 0, 0, BERLIN).getMillis(), next.getMillis());
    }

    @Test
    public void everyHourOfAYearAdvances() {
        DateTime run = new DateTime(2026, 1, 1, 0, 0, DateTimeZone.UTC);
        for (int i = 0; i < 365 * 24; i++) {
            DateTime next = ForecastDataObject.nextRun(run, HOUR, BERLIN);
            assertEquals(run.getMillis() + HOUR, next.getMillis());
            run = next;
        }
    }
}
