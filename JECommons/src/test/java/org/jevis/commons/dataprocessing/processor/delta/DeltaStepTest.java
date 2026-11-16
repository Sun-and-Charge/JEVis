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
package org.jevis.commons.dataprocessing.processor.delta;

import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.dataprocessing.processor.limits.LimitBreak;
import org.jevis.commons.dataprocessing.processor.workflow.CleanInterval;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The delta check ("Änderungsschwellen" of a clean data row): sudden jumps are compared with the last normal value,
 * so the return to normal is not taken as a jump (bug reported 2026-11-16).
 */
public class DeltaStepTest {

    private static final DateTime T0 = new DateTime(2026, 11, 16, 0, 0, DateTimeZone.UTC);
    private static final long QUARTER = 15 * 60 * 1000L;
    private static final DeltaStep.History NO_HISTORY = new DeltaStep.History(null, null);

    private static List<CleanInterval> values(double... values) {
        List<CleanInterval> list = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            DateTime ts = T0.plusMinutes(15 * i);
            CleanInterval ci = new CleanInterval(new Interval(ts.minusMinutes(15), ts), ts);
            ci.getResult().setTimeStamp(ts);
            ci.getResult().setValue(values[i]);
            ci.setOutputPeriod(Period.minutes(15));
            list.add(ci);
        }
        return list;
    }

    /** the indices of the values in the breaks */
    private static List<Integer> indices(List<CleanInterval> all, List<LimitBreak> breaks) {
        List<Integer> result = new ArrayList<>();
        for (LimitBreak b : breaks) for (CleanInterval ci : b.getIntervals()) result.add(all.indexOf(ci));
        return result;
    }

    @Test
    public void singleSpikeEndsWithTheFirstNormalValue() {
        List<CleanInterval> data = values(10, 10, 100, 10, 10);
        DeltaStep.Detection d = DeltaStep.detect(data, 50, NO_HISTORY, 0);
        /* before: the 10 after the spike was part of the break (jump 100 → 10) */
        assertEquals(Arrays.asList(2), indices(data, d.spikes));
        assertTrue(d.levelChanges.isEmpty());
        LimitBreak spike = d.spikes.get(0);
        assertEquals(10, spike.getFirstValue(), 0);
        assertEquals(10, spike.getLastValue(), 0);
    }

    @Test
    public void longerSpikeKeepsAllHighValuesAndNotTheReturn() {
        List<CleanInterval> data = values(10, 100, 100, 10, 10);
        DeltaStep.Detection d = DeltaStep.detect(data, 50, NO_HISTORY, 2 * QUARTER);
        /* before: only the first 100 was found and the return to 10 was taken as a new jump */
        assertEquals(Arrays.asList(1, 2), indices(data, d.spikes));
        assertTrue(d.levelChanges.isEmpty());
    }

    @Test
    public void fallingSpikeIsFoundToo() {
        List<CleanInterval> data = values(100, 100, 10, 100, 100);
        DeltaStep.Detection d = DeltaStep.detect(data, 50, NO_HISTORY, 0);
        assertEquals(Arrays.asList(2), indices(data, d.spikes));
    }

    @Test
    public void aLastingNewLevelIsOnlyMarkedAndBecomesTheReference() {
        List<CleanInterval> data = values(10, 10, 100, 100, 100, 100, 10);
        /* spikes may last at most one quarter hour: from the second high value on it is a new level */
        DeltaStep.Detection d = DeltaStep.detect(data, 50, NO_HISTORY, QUARTER);
        assertEquals(Arrays.asList(2, 3, 4), indices(data, d.levelChanges));
        /* the reference followed the new level: 100 → 100 is normal, the fall to 10 is a (still open) jump */
        assertEquals(Arrays.asList(6), indices(data, d.spikes));
    }

    @Test
    public void smallChangesAreNoBreaks() {
        List<CleanInterval> data = values(10, 11, 12, 11, 10);
        DeltaStep.Detection d = DeltaStep.detect(data, 50, NO_HISTORY, 0);
        assertTrue(d.spikes.isEmpty());
        assertTrue(d.levelChanges.isEmpty());
    }

    @Test
    public void theReferenceComesFromTheLastNormalStoredValue() throws Exception {
        /* stored: 10 (normal), then 100 with a delta note (break still going on at the end of the previous run) */
        VirtualSample normal = new VirtualSample(T0.minusMinutes(30), 10.0);
        VirtualSample spike = new VirtualSample(T0.minusMinutes(15), 100.0);
        spike.setNote("rawsample,delta(Step1),(0.9,0.5)");
        List<JEVisSample> stored = Arrays.asList(normal, spike);
        DeltaStep.History history = DeltaStep.history(stored);
        assertEquals(10, history.reference, 0);
        assertEquals(T0.minusMinutes(15), history.openBreakStart);

        /* the next run starts with the return to 10: not a jump (before: 100 → 10 was taken as one) */
        List<CleanInterval> data = values(10, 10);
        DeltaStep.Detection d = DeltaStep.detect(data, 50, history, 0);
        assertTrue(d.spikes.isEmpty());

        /* a deviation that went on since the previous run (start 23:45) counts its time from there: 00:00 is still
           within the quarter hour, at 00:15 it is a new level (both values only marked) */
        data = values(100, 100);
        d = DeltaStep.detect(data, 50, history, QUARTER);
        assertEquals(Arrays.asList(0, 1), indices(data, d.levelChanges));
        assertTrue(d.spikes.isEmpty());
        /* without the history the same two values would be one spike of 15 minutes */
        d = DeltaStep.detect(data, 50, new DeltaStep.History(10.0, null), QUARTER);
        assertEquals(Arrays.asList(0, 1), indices(data, d.spikes));
    }

    @Test
    public void deltaBreaksDoNotClampTheSubstitutedValues() {
        DeltaStep.Detection d = DeltaStep.detect(values(10, 100, 10), 50, NO_HISTORY, 0);
        LimitBreak spike = d.spikes.get(0);
        /* before: min = relative change (0.9) and max = threshold (0.5) clamped the substitute to 0.5 */
        assertNull(spike.getMin());
        assertNull(spike.getMax());
        assertEquals(0.9, ((DeltaStep.DeltaBreak) spike).getChange(), 1e-9);
        assertEquals(0.5, ((DeltaStep.DeltaBreak) spike).getThreshold(), 1e-9);
    }

    @Test
    public void changeIsRelativeToTheNewValueAsBefore() {
        assertEquals(0.9, DeltaStep.change(100, 10), 1e-9);
        assertEquals(9.0, DeltaStep.change(10, 100), 1e-9);
        assertEquals(1.0, DeltaStep.change(0, 10), 1e-9);
        assertEquals(0.0, DeltaStep.change(5, 5), 1e-9);
    }

    @Test
    public void thresholdZeroStillFindsFrozenValues() {
        List<CleanInterval> data = values(10, 12, 12, 12, 13);
        DeltaStep.Detection d = DeltaStep.detect(data, 0, NO_HISTORY, 0);
        assertEquals(Arrays.asList(2, 3), indices(data, d.spikes));
    }
}
