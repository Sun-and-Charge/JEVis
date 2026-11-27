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
package org.jevis.commons.calculation;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A periodic and a non-periodic input (bug report 2026-11-27: the value widget used the first value of the
 * non-periodic row, from 2023, for a time frame in 2025).
 */
public class CalcNonPeriodicTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");

    private static CalcInputObject input(String id, CalcInputType type, List<JEVisSample> samples) {
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        JEVisObject object = mock(JEVisObject.class);
        when(attribute.getObject()).thenReturn(object);
        when(object.getName()).thenReturn(id);
        CalcInputObject input = new CalcInputObject(id, type, attribute);
        input.setSamples(samples);
        input.setQuantity(type != CalcInputType.NON_PERIODIC);
        return input;
    }

    private static List<JEVisSample> factor() {
        return new ArrayList<>(Arrays.asList(
                new VirtualSample(new DateTime(2023, 1, 1, 0, 0, Z), 2.0),
                new VirtualSample(new DateTime(2025, 7, 1, 0, 0, Z), 3.0)));
    }

    private static List<JEVisSample> months2025() {
        List<JEVisSample> list = new ArrayList<>();
        for (int m = 1; m <= 12; m++) list.add(new VirtualSample(new DateTime(2025, m, 1, 0, 0, Z), 100.0));
        return list;
    }

    private static double sum(List<JEVisSample> samples) throws Exception {
        double sum = 0;
        for (JEVisSample s : samples) sum += s.getValueAsDouble();
        return sum;
    }

    @Test
    public void eachPeriodicValueUsesTheNonPeriodicValueValidAtItsTime() throws Exception {
        CalcJob job = new CalcJob(Arrays.asList(input("x", CalcInputType.PERIODIC, months2025()),
                input("f", CalcInputType.NON_PERIODIC, factor())), "x*f", Collections.emptyList(), 1L);
        job.setStaticValue(0.0);
        List<JEVisSample> results = job.getResults();
        assertEquals(12, results.size());
        /* January to June with 2 (valid since 2023), July to December with 3 */
        assertEquals(6 * 200 + 6 * 300, sum(results), 1e-9);
    }

    @Test
    public void totalOfTheTimeFrameIsTheSumOfThePerPeriodResults() throws Exception {
        /* the absolute total reduces the periodic input to one value (1200 at the start) and uses the factor valid
           at the start (2): 2400; per period 3000. Value widgets keep the absolute total, because ratios such as
           kWh per bed need total / total (a sum of per-period ratios is wrong) */
        CalcJob absolute = new CalcJob(Arrays.asList(
                input("x", CalcInputType.PERIODIC, Collections.singletonList(new VirtualSample(new DateTime(2025, 1, 1, 0, 0, Z), 1200.0))),
                input("f", CalcInputType.NON_PERIODIC, factor())), "x*f", Collections.emptyList(), 1L);
        absolute.setStaticValue(0.0);
        assertEquals(2400, sum(absolute.getResults()), 1e-9);

        CalcJob perPeriod = new CalcJob(Arrays.asList(input("x", CalcInputType.PERIODIC, months2025()),
                input("f", CalcInputType.NON_PERIODIC, factor())), "x*f", Collections.emptyList(), 1L);
        perPeriod.setStaticValue(0.0);
        assertEquals(3000, sum(perPeriod.getResults()), 1e-9);
    }

    @Test
    public void sampleValidAtShowsTheValueOfTheTimeFrame() throws Exception {
        CalcInputObject f = input("f", CalcInputType.NON_PERIODIC, factor());
        /* not the first value of the row (2023) */
        assertEquals(3.0, f.sampleValidAt(new DateTime(2025, 8, 1, 0, 0, Z)).getValueAsDouble(), 1e-9);
        assertEquals(2.0, f.sampleValidAt(new DateTime(2025, 1, 1, 0, 0, Z)).getValueAsDouble(), 1e-9);
        assertEquals(3.0, f.sampleValidAt(new DateTime(2025, 7, 1, 0, 0, Z)).getValueAsDouble(), 1e-9);
        /* before all values: the first one */
        assertEquals(2.0, f.sampleValidAt(new DateTime(2020, 1, 1, 0, 0, Z)).getValueAsDouble(), 1e-9);
        CalcInputObject x = input("x", CalcInputType.PERIODIC, months2025());
        assertEquals(new DateTime(2025, 1, 1, 0, 0, Z), x.sampleValidAt(new DateTime(2025, 8, 1, 0, 0, Z)).getTimestamp());
        assertNull(input("e", CalcInputType.NON_PERIODIC, new ArrayList<>()).sampleValidAt(new DateTime()));
    }

    @Test
    public void nonPeriodicAmountsAreAddedUpPerPeriod() throws Exception {
        /* "Value is a Quantity" on the non-periodic row (e.g. consumption entered by hand): its values are added up
           for the period they fall into, not repeated until the next value (bug report 2026-11-27) */
        List<JEVisSample> entered = new ArrayList<>(Arrays.asList(
                new VirtualSample(new DateTime(2024, 12, 15, 0, 0, Z), 999.0),
                new VirtualSample(new DateTime(2025, 1, 20, 0, 0, Z), 50.0),
                new VirtualSample(new DateTime(2025, 3, 5, 0, 0, Z), 70.0)));
        CalcInputObject y = input("y", CalcInputType.NON_PERIODIC, entered);
        y.setQuantity(true);
        CalcJob job = new CalcJob(Arrays.asList(input("x", CalcInputType.PERIODIC, months2025()), y), "x-y", Collections.emptyList(), 1L);
        job.setStaticValue(0.0);
        job.setNonPeriodicQuantitiesPerPeriod(true);
        List<JEVisSample> results = job.getResults();
        assertEquals(12, results.size());
        assertEquals(1200 - 50 - 70, sum(results), 1e-9);
        assertEquals(50, results.get(0).getValueAsDouble(), 1e-9);
        assertEquals(100, results.get(1).getValueAsDouble(), 1e-9);
        assertEquals(30, results.get(2).getValueAsDouble(), 1e-9);

        /* JECalc keeps the step function */
        CalcInputObject stepY = input("y", CalcInputType.NON_PERIODIC, entered);
        stepY.setQuantity(true);
        CalcJob step = new CalcJob(Arrays.asList(input("x", CalcInputType.PERIODIC, months2025()), stepY), "x-y", Collections.emptyList(), 1L);
        step.setStaticValue(0.0);
        assertEquals(-899, step.getResults().get(0).getValueAsDouble(), 1e-9);
    }
}
