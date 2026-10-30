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
package org.jevis.commons.unit.ChartUnits;

import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests the conversion factors of {@link ChartUnits#scaleValue(Period, String, Period, String)}.
 * The factor converts a value in the input unit into the output unit by multiplication.
 */
public class ChartUnitsTest {

    private static final double DELTA = 1e-9;
    private static final Period QUARTER_HOUR = Period.minutes(15);
    private static final Period HOUR = Period.hours(1);
    private static final Period DAY = Period.days(1);

    private final ChartUnits chartUnits = new ChartUnits();

    private double factor(String inputUnit, String outputUnit) {
        return chartUnits.scaleValue(QUARTER_HOUR, inputUnit, QUARTER_HOUR, outputUnit);
    }

    @Test
    public void volumeFlowToCubicMetrePerHour() {
        assertEquals(3600d, factor("m³/s", "m³/h"), DELTA);
        assertEquals(60d, factor("m³/min", "m³/h"), DELTA);
        assertEquals(3.6d, factor("l/s", "m³/h"), DELTA);
        assertEquals(0.06d, factor("l/min", "m³/h"), DELTA);
        assertEquals(0.001d, factor("l/h", "m³/h"), DELTA);
    }

    @Test
    public void volumeFlowFromCubicMetrePerHour() {
        assertEquals(1d / 3600d, factor("m³/h", "m³/s"), DELTA);
        assertEquals(1d / 60d, factor("m³/h", "m³/min"), DELTA);
        assertEquals(1000d / 3600d, factor("m³/h", "l/s"), DELTA);
        assertEquals(1000d / 60d, factor("m³/h", "l/min"), DELTA);
        assertEquals(1000d, factor("m³/h", "l/h"), DELTA);
    }

    @Test
    public void litreFlows() {
        assertEquals(1000d, factor("m³/s", "l/s"), DELTA);
        assertEquals(1d / 60d, factor("l/min", "l/s"), DELTA);
        assertEquals(60d, factor("l/s", "l/min"), DELTA);
        assertEquals(3600d, factor("l/s", "l/h"), DELTA);
        assertEquals(60000d, factor("m³/min", "l/h"), DELTA);
    }

    @Test
    public void massFlows() {
        assertEquals(3.6d, factor("kg/s", "t/h"), DELTA);
        assertEquals(60d, factor("t/min", "t/h"), DELTA);
        assertEquals(1000d, factor("t/h", "kg/h"), DELTA);
        assertEquals(1d / 60d, factor("kg/min", "kg/s"), DELTA);
    }

    @Test
    public void pressure() {
        assertEquals(1.01325d, factor("atm", "bar"), DELTA);
        assertEquals(1d / 1.01325d, factor("bar", "atm"), DELTA);
    }

    @Test
    public void volumePerSampleToVolumeFlow() {
        /* 1 m³ within 15 minutes is a flow of 4 m³/h */
        assertEquals(4d, factor("m³", "m³/h"), DELTA);
        assertEquals(1d, chartUnits.scaleValue(HOUR, "m³", HOUR, "m³/h"), DELTA);
        assertEquals(1d / 24d, chartUnits.scaleValue(HOUR, "m³", DAY, "m³/h"), DELTA);
        /* 1000 l within 15 minutes */
        assertEquals(0.004d, factor("L", "m³/h"), DELTA);
        assertEquals(4000d, factor("m³", "l/h"), DELTA);
    }

    @Test
    public void volumeFlowToVolumePerSample() {
        /* 4 m³/h over 15 minutes are 1 m³ */
        assertEquals(0.25d, factor("m³/h", "m³"), DELTA);
        assertEquals(24d, chartUnits.scaleValue(HOUR, "m³/h", DAY, "m³"), DELTA);
    }

    @Test
    public void volumeFlowWithoutFixedPeriodIsUnchanged() {
        assertEquals(1d, chartUnits.scaleValue(Period.months(1), "m³", Period.months(1), "m³/h"), DELTA);
    }

    @Test
    public void unrelatedUnitsAreUnchanged() {
        assertEquals(1d / 1000d, factor("L", "m³"), DELTA);
        assertEquals(1000d, factor("m³", "L"), DELTA);
        assertEquals(4d, factor("kWh", "kW"), DELTA);
    }
}
