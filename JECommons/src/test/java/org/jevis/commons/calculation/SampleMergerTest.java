/**
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
package org.jevis.commons.calculation;

import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Combination rules for periodic and async calculation inputs (upstream issue #2310).
 */
public class SampleMergerTest {

    private static final DateTime T0 = new DateTime(2026, 1, 5, 10, 0);

    private static DateTime at(int minutes) {
        return T0.plusMinutes(minutes);
    }

    private static List<JEVisSample> samples(Object... timeAndValue) {
        List<JEVisSample> list = new ArrayList<>();
        for (int i = 0; i < timeAndValue.length; i += 2) {
            list.add(new VirtualSample((DateTime) timeAndValue[i], ((Number) timeAndValue[i + 1]).doubleValue()));
        }
        return list;
    }

    /* periodic 15 min grid 10:00, 10:15, 10:30 with value 10 */
    private static List<JEVisSample> periodic() {
        return samples(at(0), 10, at(15), 10, at(30), 10);
    }

    private static double value(Map<DateTime, List<Sample>> merged, DateTime ts, String variable) {
        for (Sample sample : merged.get(ts)) {
            if (sample.getVariable().equals(variable)) {
                return sample.getValue().doubleValue();
            }
        }
        fail("no value for " + variable + " at " + ts);
        return Double.NaN;
    }

    @Test
    public void asyncQuantityIsSummedPerPeriod() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(7), 2, at(12), 1, at(15), 4), "a", CalcInputType.ASYNC, true);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(3, merged.size());
        assertEquals(3.0, value(merged, at(0), "a"), 0.0001);
        assertEquals(4.0, value(merged, at(15), "a"), 0.0001);
        assertEquals(0.0, value(merged, at(30), "a"), 0.0001);
        assertFalse("async timestamps must not create own rows", merged.containsKey(at(7)));
    }

    @Test
    public void asyncQuantityInLastPeriodIsIncluded() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(44), 5), "a", CalcInputType.ASYNC, true);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(5.0, value(merged, at(30), "a"), 0.0001);
    }

    @Test
    public void asyncNonQuantityUsesLastValueAtPeriodStart() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(-60), 0.20, at(7), 0.30, at(15), 0.40), "price", CalcInputType.ASYNC, false);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(3, merged.size());
        assertEquals(0.20, value(merged, at(0), "price"), 0.0001);
        assertEquals(0.40, value(merged, at(15), "price"), 0.0001);
        assertEquals(0.40, value(merged, at(30), "price"), 0.0001);
    }

    @Test
    public void periodsBeforeFirstNonQuantityValueAreSkipped() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(7), 50), "kw", CalcInputType.ASYNC, false);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(2, merged.size());
        assertFalse(merged.containsKey(at(0)));
        assertEquals(50.0, value(merged, at(15), "kw"), 0.0001);
    }

    @Test
    public void withoutPeriodTheNextPeriodicTimestampEndsThePeriod() {
        SampleMerger merger = new SampleMerger();
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(7), 2, at(20), 3, at(44), 1), "a", CalcInputType.ASYNC, true);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(2.0, value(merged, at(0), "a"), 0.0001);
        assertEquals(3.0, value(merged, at(15), "a"), 0.0001);
        assertEquals(1.0, value(merged, at(30), "a"), 0.0001);
    }

    @Test
    public void missingPeriodicValueRemovesTimestamp() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(0), 1, at(30), 1), "q", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(16), 2), "a", CalcInputType.ASYNC, true);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(2, merged.size());
        assertFalse(merged.containsKey(at(15)));
        assertEquals(0.0, value(merged, at(30), "a"), 0.0001);
    }

    @Test
    public void staticInputIsAddedWithAsyncInputs() {
        SampleMerger merger = new SampleMerger();
        merger.setPeriod(Period.minutes(15));
        merger.addSamples(periodic(), "p", CalcInputType.PERIODIC);
        merger.addSamples(samples(at(3), 2), "a", CalcInputType.ASYNC, true);
        merger.addSamples(samples(at(-1000), 7), "c", CalcInputType.STATIC);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertEquals(3, merged.size());
        assertEquals(3, merged.get(at(0)).size());
        assertEquals(7.0, value(merged, at(15), "c"), 0.0001);
    }

    @Test
    public void onlyAsyncInputsKeepAllTimestamps() {
        SampleMerger merger = new SampleMerger();
        merger.addSamples(samples(at(1), 1, at(5), 2, at(9), 3), "a", CalcInputType.ASYNC, true);
        merger.addSamples(samples(at(2), 1, at(9), 4), "b", CalcInputType.ASYNC, false);

        Map<DateTime, List<Sample>> merged = merger.merge();

        assertTrue(merged.containsKey(at(1)));
        assertTrue(merged.containsKey(at(2)));
        assertTrue(merged.containsKey(at(5)));
        assertEquals(0.0, value(merged, at(1), "b"), 0.0001);
    }
}
