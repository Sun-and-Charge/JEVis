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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.*;

import static org.junit.Assert.*;

/**
 * DataProcessor test matrix (user 2026-11-27): raw → clean period for all usual combinations, as consumption values,
 * as counter readings (conversion to differential) and as average values (no quantity), across both daylight saving
 * changes and with small batches so that batch boundaries fall inside raw periods.
 * <p>
 * Checks: the clean values add up to the raw consumption (counter: last − first reading); splitting into shorter
 * periods fills every clean period; no clean value lies before the first raw value (minus one clean period); average
 * values of a constant raw row stay constant.
 */
@RunWith(Parameterized.class)
public class CleanPeriodMatrixTest {

    enum Mode {CONSUMPTION, COUNTER, AVERAGE}

    private static final DateTimeZone Z = CleanPipelineHarness.ZONE;

    @Parameterized.Parameters(name = "{0} -> {1} {2} batch {3}")
    public static Collection<Object[]> cases() {
        String[][] pairs = {
                /* shorter clean period (split) */
                {"PT5M", "PT1M"}, {"PT15M", "PT1M"}, {"PT15M", "PT5M"}, {"PT1H", "PT15M"},
                {"P1D", "PT1H"}, {"P1D", "PT15M"}, {"P1M", "P1D"}, {"P1M", "PT15M"},
                /* same period */
                {"PT15M", "PT15M"}, {"P1D", "P1D"},
                /* longer clean period (sum / mean) */
                {"PT1M", "PT5M"}, {"PT1M", "PT15M"}, {"PT5M", "PT15M"}, {"PT15M", "PT1H"},
                {"PT15M", "P1D"}, {"PT1H", "P1D"}, {"P1D", "P1M"}, {"PT15M", "P1M"}, {"P1M", "P1Y"},
        };
        List<Object[]> list = new ArrayList<>();
        for (String[] p : pairs) {
            for (Mode m : Mode.values()) {
                /* the service's batch size and a small one, so that batch boundaries fall inside raw periods */
                list.add(new Object[]{p[0], p[1], m, 50000});
                /* a batch must hold the clean periods of one raw period (a month has 2976 quarter hours), else a
                   month value cannot be passed; the service uses 50000 */
                if (!(p[0].equals("P1M") && p[1].startsWith("PT"))) list.add(new Object[]{p[0], p[1], m, 997});
            }
        }
        return list;
    }

    private final Period rawPeriod;
    private final Period cleanPeriod;
    private final Mode mode;
    private DateTimeZone previousDefault;

    private final int batchSize;

    public CleanPeriodMatrixTest(String raw, String clean, Mode mode, int batchSize) {
        this.rawPeriod = Period.parse(raw);
        this.cleanPeriod = Period.parse(clean);
        this.mode = mode;
        this.batchSize = batchSize;
    }

    @Before
    public void zone() {
        previousDefault = DateTimeZone.getDefault();
        DateTimeZone.setDefault(Z);
    }

    @After
    public void restore() {
        DateTimeZone.setDefault(previousDefault);
    }

    private static long millis(Period p) {
        if (p.getYears() > 0) return 365L * 86400000L;
        if (p.getMonths() > 0) return 30L * 86400000L;
        return p.toStandardDuration().getMillis();
    }

    /** range of the raw data: whole clean periods, both DST changes; years need several years */
    private DateTime[] range() {
        if (cleanPeriod.getYears() > 0) return new DateTime[]{new DateTime(2023, 1, 1, 0, 0, Z), new DateTime(2026, 1, 1, 0, 0, Z)};
        long smallest = Math.min(millis(rawPeriod), millis(cleanPeriod));
        if (smallest < 15 * 60000L) {
            /* minute values: a few days around the DST change in March, about 9000 samples */
            return new DateTime[]{new DateTime(2025, 3, 28, 0, 0, Z), new DateTime(2025, 4, 3, 0, 0, Z)};
        }
        if (smallest < 3600000L) return new DateTime[]{new DateTime(2025, 3, 1, 0, 0, Z), new DateTime(2025, 11, 1, 0, 0, Z)};
        return new DateTime[]{new DateTime(2025, 1, 1, 0, 0, Z), new DateTime(2026, 1, 1, 0, 0, Z)};
    }

    private static DateTime next(DateTime t, Period p) {
        return t.plus(p);
    }

    @Test
    public void cleanValuesAddUp() throws Exception {
        /* debugging one case: HARNESS_ONLY="PT15M P1M COUNTER 50000" (environment) (also sets harness.debug) */
        String only = System.getenv("HARNESS_ONLY");
        if (only != null && !only.isEmpty()) {
            org.junit.Assume.assumeTrue((rawPeriod + " " + cleanPeriod + " " + mode + " " + batchSize).equals(only));
            System.setProperty("harness.debug", "true");
        }
        DateTime[] r = range();
        List<JEVisSample> raw = new ArrayList<>();
        double total = 0;
        double reading = 1000;
        int i = 0;
        int rawIntervals = 0;
        for (DateTime t = r[0]; t.isBefore(r[1]); t = next(t, rawPeriod), i++) {
            double v = 1 + (i % 5);
            switch (mode) {
                case CONSUMPTION:
                    raw.add(new VirtualSample(t.withZone(DateTimeZone.UTC), v));
                    total += v;
                    break;
                case COUNTER:
                    raw.add(new VirtualSample(t.withZone(DateTimeZone.UTC), reading));
                    reading += v;
                    break;
                case AVERAGE:
                    raw.add(new VirtualSample(t.withZone(DateTimeZone.UTC), 20.0));
                    break;
            }
            rawIntervals++;
        }
        if (mode == Mode.COUNTER) {
            /* the closing reading at the end of the range */
            raw.add(new VirtualSample(r[1].withZone(DateTimeZone.UTC), reading));
            total = reading - 1000;
        }

        CleanPipelineHarness harness = new CleanPipelineHarness(raw, rawPeriod, cleanPeriod, mode == Mode.COUNTER, mode != Mode.AVERAGE, batchSize);
        TreeMap<DateTime, Double> clean = harness.run();
        StringBuilder ends = new StringBuilder();
        int k = 0;
        for (Map.Entry<DateTime, Double> e : clean.entrySet()) {
            if (k < 3 || k >= clean.size() - 3) ends.append(" ").append(e.getKey().withZone(Z).toString("MM-dd HH:mm")).append("=").append(Math.round(e.getValue() * 1000) / 1000.0);
            if (k == 3 && clean.size() > 6) ends.append(" …");
            k++;
        }
        System.out.println("CASE " + rawPeriod + " -> " + cleanPeriod + " " + mode + " batch " + batchSize + ":" + ends);
        String label = rawPeriod + " -> " + cleanPeriod + " " + mode + " batch " + batchSize + " (" + rawIntervals + " raw, " + harness.batches + " batches, "
                + clean.size() + " clean";
        assertFalse(label + "): no clean values", clean.isEmpty());

        /* values before the data: zeros are padding (reported), other values are an error */
        DateTime earliestAllowed = r[0].minus(cleanPeriod).minusMillis(1);
        int zerosBefore = 0;
        for (Map.Entry<DateTime, Double> e : clean.headMap(earliestAllowed, false).entrySet()) {
            if (e.getValue() == 0d) zerosBefore++;
            else fail(label + "): value " + e.getValue() + " at " + e.getKey().withZone(Z) + " before the first raw value " + r[0]);
        }
        if (zerosBefore > 0) System.out.println("NOTE " + label + "): " + zerosBefore + " zero values before the first raw value");

        if (mode == Mode.AVERAGE) {
            for (Map.Entry<DateTime, Double> e : clean.entrySet()) {
                assertEquals(label + "): average at " + e.getKey().withZone(Z), 20.0, e.getValue(), 1e-6);
            }
            return;
        }

        double sum = 0;
        for (double v : clean.values()) sum += v;
        assertEquals(label + ", first " + clean.firstKey().withZone(Z) + ", last " + clean.lastKey().withZone(Z) + "): sum", total, sum, Math.max(1e-6, total * 1e-9));

        if (millis(cleanPeriod) < millis(rawPeriod)) {
            /* split: every clean period of the range has a value */
            int periods = 0;
            for (DateTime t = r[0]; t.isBefore(r[1]); t = next(t, cleanPeriod)) periods++;
            int inRange = 0;
            for (DateTime t : clean.keySet()) {
                if (!t.isBefore(r[0].minus(cleanPeriod)) && !t.isAfter(r[1])) inRange++;
            }
            assertTrue(label + "): " + inRange + " of " + periods + " clean periods filled", inRange >= periods);
        }
    }
}
