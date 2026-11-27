/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dataprocessing.processor;

import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * Days, weeks, months and years are stamped at their start (user 2026-11-27): the clean value on 1 January is the
 * consumption of January, for counter readings the reading of 1 February minus the reading of 1 January.
 */
public class CleanPeriodStampTest {

    private static final DateTimeZone Z = CleanPipelineHarness.ZONE;
    private DateTimeZone previousDefault;

    @Before
    public void zone() {
        previousDefault = DateTimeZone.getDefault();
        DateTimeZone.setDefault(Z);
    }

    @After
    public void restore() {
        DateTimeZone.setDefault(previousDefault);
    }

    /** consumption of month i of 2025 (0 = January) */
    private static double month(int i) {
        return 100 + 10 * i;
    }

    private static DateTime monthStart(int i) {
        return new DateTime(2025, 1, 1, 0, 0, Z).plusMonths(i);
    }

    @Test
    public void monthlyCounterReadingsAreStampedAtTheMonthStart() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double reading = 5000;
        for (int i = 0; i <= 12; i++) {
            raw.add(new VirtualSample(monthStart(i).withZone(DateTimeZone.UTC), reading));
            if (i < 12) reading += month(i);
        }
        TreeMap<DateTime, Double> clean = new CleanPipelineHarness(raw, Period.months(1), Period.months(1), true, true, 50000).run();
        for (int i = 0; i < 12; i++) {
            assertEquals("value at " + monthStart(i), month(i), clean.get(monthStart(i).withZone(DateTimeZone.UTC)), 1e-9);
        }
        assertEquals(null, clean.get(monthStart(12).withZone(DateTimeZone.UTC)));
    }

    @Test
    public void monthlyConsumptionValuesKeepTheirMonth() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        for (int i = 0; i < 12; i++) raw.add(new VirtualSample(monthStart(i).withZone(DateTimeZone.UTC), month(i)));
        TreeMap<DateTime, Double> clean = new CleanPipelineHarness(raw, Period.months(1), Period.months(1), false, true, 50000).run();
        for (int i = 0; i < 12; i++) {
            assertEquals("value at " + monthStart(i), month(i), clean.get(monthStart(i).withZone(DateTimeZone.UTC)), 1e-9);
        }
    }

    @Test
    public void dailyCounterReadingsAreStampedAtTheDayStart() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double reading = 5000;
        DateTime start = new DateTime(2025, 3, 25, 0, 0, Z);
        for (int i = 0; i <= 10; i++) {
            raw.add(new VirtualSample(start.plusDays(i).withZone(DateTimeZone.UTC), reading));
            reading += 10 + i;
        }
        TreeMap<DateTime, Double> clean = new CleanPipelineHarness(raw, Period.days(1), Period.days(1), true, true, 50000).run();
        for (int i = 0; i < 10; i++) {
            assertEquals("value at " + start.plusDays(i), 10 + i, clean.get(start.plusDays(i).withZone(DateTimeZone.UTC)), 1e-9);
        }
    }

    @Test
    public void monthlyCounterReadingsIntoYearsAreStampedAtTheYearStart() throws Exception {
        List<JEVisSample> raw = new ArrayList<>();
        double reading = 0;
        DateTime start = new DateTime(2023, 1, 1, 0, 0, Z);
        for (int i = 0; i <= 36; i++) {
            raw.add(new VirtualSample(start.plusMonths(i).withZone(DateTimeZone.UTC), reading));
            reading += i < 12 ? 1 : i < 24 ? 2 : 3;
        }
        TreeMap<DateTime, Double> clean = new CleanPipelineHarness(raw, Period.months(1), Period.years(1), true, true, 50000).run();
        assertEquals(12, clean.get(new DateTime(2023, 1, 1, 0, 0, Z).withZone(DateTimeZone.UTC)), 1e-9);
        assertEquals(24, clean.get(new DateTime(2024, 1, 1, 0, 0, Z).withZone(DateTimeZone.UTC)), 1e-9);
        assertEquals(36, clean.get(new DateTime(2025, 1, 1, 0, 0, Z).withZone(DateTimeZone.UTC)), 1e-9);
    }
}
