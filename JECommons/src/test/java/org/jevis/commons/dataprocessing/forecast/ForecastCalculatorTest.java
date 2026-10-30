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
package org.jevis.commons.dataprocessing.forecast;

import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.GapFillingBoundToSpecific;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.regression.LinearTrendLine;
import org.joda.time.DateTime;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * Tests for {@link ForecastCalculator}: day class selection with holidays and the regression method.
 */
public class ForecastCalculatorTest {
    private static final double DELTA = 1e-6;

    private static DateTime day(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0);
    }

    private static JEVisSample sample(DateTime ts, double value) {
        return new VirtualSample(ts, value);
    }

    /**
     * Fridays 11.12. and 18.12. (normal, 100), holiday Friday 25.12. (10) and holiday Thursday 14.05. (20).
     */
    private static List<JEVisSample> referenceData() {
        return new ArrayList<>(Arrays.asList(
                sample(day(2026, 5, 14), 20),
                sample(day(2026, 12, 11), 100),
                sample(day(2026, 12, 18), 100),
                sample(day(2026, 12, 25), 10)));
    }

    private static final Set<LocalDate> HOLIDAYS = new HashSet<>(Arrays.asList(
            LocalDate.of(2026, 12, 25), LocalDate.of(2026, 5, 14), LocalDate.of(2027, 1, 1)));
    private static final Predicate<LocalDate> IS_HOLIDAY = HOLIDAYS::contains;

    @Test
    public void normalWeekdayIgnoresHolidays() {
        DateTime target = day(2027, 1, 8); // normal Friday
        Double withHolidays = ForecastCalculator.forecastValue(referenceData(), target, GapFillingType.AVERAGE,
                GapFillingBoundToSpecific.WEEKDAY, IS_HOLIDAY);
        assertEquals(100d, withHolidays, DELTA);

        // unchanged behaviour without holiday information: holiday Friday is included
        Double withoutHolidays = ForecastCalculator.forecastValue(referenceData(), target, GapFillingType.AVERAGE,
                GapFillingBoundToSpecific.WEEKDAY, null);
        assertEquals(70d, withoutHolidays, DELTA);
    }

    @Test
    public void holidayUsesOtherHolidays() {
        DateTime target = day(2027, 1, 1); // holiday Friday
        Double value = ForecastCalculator.forecastValue(referenceData(), target, GapFillingType.AVERAGE,
                GapFillingBoundToSpecific.WEEKDAY, IS_HOLIDAY);
        assertEquals(15d, value, DELTA);
    }

    @Test
    public void holidayWithoutHolidayReferencesFallsBackToWeekday() {
        List<JEVisSample> data = Arrays.asList(sample(day(2026, 12, 11), 100), sample(day(2026, 12, 18), 80));
        Double value = ForecastCalculator.forecastValue(data, day(2027, 1, 1), GapFillingType.AVERAGE,
                GapFillingBoundToSpecific.WEEKDAY, IS_HOLIDAY);
        assertEquals(90d, value, DELTA);
    }

    @Test
    public void holidaysOnlyAffectWeekdayBinding() {
        DateTime target = day(2027, 1, 8);
        Double value = ForecastCalculator.forecastValue(referenceData(), target, GapFillingType.AVERAGE,
                GapFillingBoundToSpecific.NONE, IS_HOLIDAY);
        assertEquals(57.5d, value, DELTA);
    }

    @Test
    public void timeOfDayMustMatch() {
        List<JEVisSample> data = Arrays.asList(
                sample(new DateTime(2026, 12, 18, 0, 0), 100),
                sample(new DateTime(2026, 12, 18, 0, 15), 50));
        List<JEVisSample> refs = ForecastCalculator.selectReferenceSamples(data, new DateTime(2027, 1, 8, 0, 15),
                GapFillingBoundToSpecific.WEEKDAY, null);
        assertEquals(1, refs.size());
        assertEquals(50d, ForecastCalculator.calcValue(refs, GapFillingType.AVERAGE, null), DELTA);
    }

    @Test
    public void aggregations() {
        List<JEVisSample> data = Arrays.asList(sample(day(2026, 1, 1), 4), sample(day(2026, 1, 2), 1),
                sample(day(2026, 1, 3), 7));
        assertEquals(1d, ForecastCalculator.calcValue(data, GapFillingType.MINIMUM, null), DELTA);
        assertEquals(7d, ForecastCalculator.calcValue(data, GapFillingType.MAXIMUM, null), DELTA);
        assertEquals(4d, ForecastCalculator.calcValue(data, GapFillingType.MEDIAN, null), DELTA);
        assertEquals(4d, ForecastCalculator.calcValue(data, GapFillingType.AVERAGE, null), DELTA);
        assertNull(ForecastCalculator.calcValue(new ArrayList<>(), GapFillingType.AVERAGE, null));
        assertEquals(1d, ForecastCalculator.calcValue(data.subList(1, 2), GapFillingType.MEDIAN, null), DELTA);
    }

    @Test
    public void regressionExtrapolatesLinearTrend() {
        List<JEVisSample> data = new ArrayList<>();
        DateTime start = day(2026, 1, 1);
        for (int i = 0; i < 10; i++) {
            data.add(sample(start.plusDays(i), 10 + 2 * i));
        }
        Double value = ForecastCalculator.forecastValue(data, start.plusDays(10), GapFillingType.REGRESSION,
                GapFillingBoundToSpecific.NONE, null);
        assertEquals(30d, value, 1e-6);

        Double value2 = ForecastCalculator.forecastValue(data, start.plusDays(15), GapFillingType.REGRESSION,
                GapFillingBoundToSpecific.NONE, null);
        assertEquals(40d, value2, 1e-6);
    }

    @Test
    public void regressionPerWeekday() {
        // Mondays rise by 7 per week, other days are constant
        List<JEVisSample> data = new ArrayList<>();
        DateTime monday = day(2026, 1, 5);
        for (int week = 0; week < 4; week++) {
            for (int d = 0; d < 7; d++) {
                DateTime ts = monday.plusWeeks(week).plusDays(d);
                data.add(sample(ts, d == 0 ? 100 + 7 * week : 50));
            }
        }
        Double nextMonday = ForecastCalculator.forecastValue(data, monday.plusWeeks(4), GapFillingType.REGRESSION,
                GapFillingBoundToSpecific.WEEKDAY, null);
        assertEquals(128d, nextMonday, 1e-6);
        Double nextTuesday = ForecastCalculator.forecastValue(data, monday.plusWeeks(4).plusDays(1), GapFillingType.REGRESSION,
                GapFillingBoundToSpecific.WEEKDAY, null);
        assertEquals(50d, nextTuesday, 1e-6);
    }

    @Test
    public void regressionFallsBackToAverageWithFewPoints() {
        List<JEVisSample> data = Arrays.asList(sample(day(2026, 1, 1), 10), sample(day(2026, 1, 2), 20));
        assertEquals(15d, ForecastCalculator.calcValue(data, GapFillingType.REGRESSION, day(2026, 1, 3)), DELTA);
    }

    @Test
    public void parseRegressionType() {
        assertEquals(GapFillingType.REGRESSION, GapFillingType.parse("REGRESSION"));
        assertEquals(GapFillingType.REGRESSION, GapFillingType.parse("regression"));
    }

    @Test
    public void linearTrendLine() {
        LinearTrendLine line = new LinearTrendLine();
        line.setValues(new double[]{1, 3, 5, 7}, new double[]{0, 1, 2, 3});
        assertEquals(9d, line.predict(4), 1e-9);
        assertEquals(-1d, line.predict(-1), 1e-9);
    }
}
