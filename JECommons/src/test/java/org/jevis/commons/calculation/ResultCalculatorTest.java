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
package org.jevis.commons.calculation;

import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.joda.time.DateTime;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ResultCalculatorTest {

    private static Map<DateTime, List<Sample>> singleEntry(DateTime ts, Sample... samples) {
        Map<DateTime, List<Sample>> map = new TreeMap<>();
        map.put(ts, new ArrayList<>(Arrays.asList(samples)));
        return map;
    }

    @Test
    public void testBasicEvaluation() {
        DateTime ts = new DateTime(2020, 1, 1, 0, 0);
        VirtualSample vs = new VirtualSample(ts, 5.0);
        Sample s = new Sample(vs, "x", CalcInputType.PERIODIC);

        CalcTemplate template = new CalcTemplate(1L, "x * 2");
        ResultCalculator rc = new ResultCalculator(singleEntry(ts, s), template);
        List<JEVisSample> results = rc.calculateResult(0.0, null);

        assertEquals(1, results.size());
        try {
            assertEquals(10.0, results.get(0).getValueAsDouble(), 0.001);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testAllZeroReplacementFires() {
        DateTime ts = new DateTime(2020, 1, 1, 0, 0);
        VirtualSample vs = new VirtualSample(ts, 0.0);
        Sample s = new Sample(vs, "x", CalcInputType.PERIODIC);

        CalcTemplate template = new CalcTemplate(1L, "x");
        ResultCalculator rc = new ResultCalculator(singleEntry(ts, s), template);
        List<JEVisSample> results = rc.calculateResult(0.0, 99.0);

        assertEquals(1, results.size());
        try {
            assertEquals(99.0, results.get(0).getValueAsDouble(), 0.001);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testAllZeroReplacementSkippedWhenNull() {
        DateTime ts = new DateTime(2020, 1, 1, 0, 0);
        VirtualSample vs = new VirtualSample(ts, 0.0);
        Sample s = new Sample(vs, "x", CalcInputType.PERIODIC);

        CalcTemplate template = new CalcTemplate(1L, "x + 1");
        ResultCalculator rc = new ResultCalculator(singleEntry(ts, s), template);
        List<JEVisSample> results = rc.calculateResult(0.0, null);

        assertEquals(1, results.size());
        try {
            assertEquals(1.0, results.get(0).getValueAsDouble(), 0.001);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testArrayAllZeroIndexCorrectnessForDuplicateValues() {
        // Both inputs have value 0.0 — old indexOf bug would map both to index 0
        DateTime ts = new DateTime(2020, 1, 1, 0, 0);
        VirtualSample vs1 = new VirtualSample(ts, 0.0);
        VirtualSample vs2 = new VirtualSample(ts, 0.0);
        Sample s1 = new Sample(vs1, "x", CalcInputType.PERIODIC);
        Sample s2 = new Sample(vs2, "y", CalcInputType.PERIODIC);

        List<Sample> samples = new ArrayList<>(Arrays.asList(s1, s2));
        Map<DateTime, List<Sample>> map = new TreeMap<>();
        map.put(ts, samples);

        CalcTemplate template = new CalcTemplate(1L, "x + y");
        ResultCalculator rc = new ResultCalculator(map, template);
        // allZeroReplacementValue=5.0 -> both are zero -> replacement fires
        List<JEVisSample> results = rc.calculateResult(0.0, 5.0);
        assertEquals(1, results.size());
        try {
            assertEquals(5.0, results.get(0).getValueAsDouble(), 0.001);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}
