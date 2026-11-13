/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.jevis.rest.web.ReadingExpectation.Due;
import static org.jevis.rest.web.ReadingExpectation.Expected;
import static org.jevis.rest.web.ReadingExpectation.Reading;
import static org.junit.Assert.*;

/**
 * Expected value and due state of a meter reading (stage E8.1), the cases of the app's {@code ExpectedValueTest}.
 */
public class ReadingExpectationTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final WebEdit.WorkDay WORK_DAY = WebEdit.WorkDay.standard(ZONE);

    private static long day(int d) {
        return new DateTime(2026, 11, d, 0, 0, DateTimeZone.forOffsetHours(1)).getMillis();
    }

    private static Reading r(int d, double value) {
        return new Reading(day(d), value);
    }

    @Test
    public void meterReadingIsExtrapolatedAtTheAverageRate() {
        Expected e = ReadingExpectation.estimate(true, true, Arrays.asList(r(1, 100), r(2, 110), r(3, 120)), day(5));
        assertNotNull(e);
        assertEquals(140.0, e.value, 1e-6);
        assertTrue("±20 % of the consumption", e.contains(136.0));
        assertTrue(e.contains(144.0));
        assertFalse("looks like a missed reading", e.contains(130.0));
        assertFalse("typo", e.contains(200.0));
    }

    @Test
    public void rangeFollowsTheSpreadOfTheConsumption() {
        /* any order */
        Expected e = ReadingExpectation.estimate(true, true, Arrays.asList(r(3, 120), r(1, 100), r(2, 105)), day(4));
        assertNotNull(e);
        assertEquals(130.0, e.value, 1e-6);
        assertEquals(120.0, e.low, 1e-6);
        assertEquals(140.0, e.high, 1e-6);
    }

    @Test
    public void lowIsNotBelowTheLastReading() {
        /* rates 0 and 20 per day: average 10, spread 20 → low rate max(−10, 0) = 0 */
        Expected e = ReadingExpectation.estimate(true, true, Arrays.asList(r(1, 100), r(2, 100), r(3, 120)), day(4));
        assertNotNull(e);
        assertEquals(130.0, e.value, 1e-6);
        assertEquals(120.0, e.low, 1e-6);
        assertEquals(150.0, e.high, 1e-6);
    }

    @Test
    public void noEstimateWithoutEnoughReadingsOrForBackDatedMeterReadings() {
        assertNull(ReadingExpectation.estimate(true, true, Collections.singletonList(r(1, 1)), day(2)));
        assertNull(ReadingExpectation.estimate(true, true, Collections.emptyList(), day(2)));
        List<Reading> readings = Arrays.asList(r(1, 100), r(3, 120));
        assertNull("before the last reading", ReadingExpectation.estimate(true, true, readings, day(2)));
        assertNull("at the last reading", ReadingExpectation.estimate(true, true, readings, day(3)));
        assertNull("no period", ReadingExpectation.estimate(false, false, readings, day(5)));
        assertNull("only decreasing intervals",
                ReadingExpectation.estimate(true, true, Arrays.asList(r(1, 100), r(2, 90), r(3, 80)), day(5)));
        assertNull("no time between the readings",
                ReadingExpectation.estimate(true, true, Arrays.asList(new Reading(day(2), 100), new Reading(day(2), 110)), day(5)));
        assertNull("one value of a consumption row", ReadingExpectation.estimate(false, true, Collections.singletonList(r(1, 1)), day(5)));
    }

    @Test
    public void meterChangeDoesNotCountAsConsumption() {
        Expected e = ReadingExpectation.estimate(true, true,
                Arrays.asList(r(1, 1000), r(2, 1010), r(3, 5), r(4, 15)), day(5));
        assertNotNull(e);
        assertEquals(25.0, e.value, 1e-6);
    }

    @Test
    public void counterUsesTheLastElevenReadings() {
        List<Reading> readings = new ArrayList<>();
        readings.add(r(1, 0));
        readings.add(r(2, 500)); // rates 500 and 510 per day in the intervals 1 → 2 → 3, left out
        for (int d = 3; d <= 13; d++) readings.add(r(d, 1000 + (d - 2) * 10));
        Expected e = ReadingExpectation.estimate(true, true, readings, day(14));
        assertNotNull(e);
        assertEquals(1120.0, e.value, 1e-6);
        assertEquals(1118.0, e.low, 1e-6);
        assertEquals(1122.0, e.high, 1e-6);
    }

    @Test
    public void consumptionValuesAreComparedWithTheirAverage() {
        Expected e = ReadingExpectation.estimate(false, true, Arrays.asList(r(1, 10), r(2, 12), r(3, 8)), day(4));
        assertNotNull(e);
        assertEquals(10.0, e.value, 1e-6);
        /* margin max(20 % of 4, 20 % of 10) = 2 */
        assertEquals(6.0, e.low, 1e-6);
        assertEquals(14.0, e.high, 1e-6);
        assertTrue(e.contains(9.0));
        assertFalse(e.contains(50.0));
    }

    @Test
    public void averageUsesTheLastTenValuesWithTheSpreadAsMargin() {
        List<Reading> readings = new ArrayList<>();
        readings.add(r(1, 1000)); // left out
        for (int d = 2; d <= 11; d++) readings.add(r(d, d % 2 == 0 ? 0 : 20));
        Expected e = ReadingExpectation.estimate(false, true, readings, day(12));
        assertNotNull(e);
        assertEquals(10.0, e.value, 1e-6);
        /* margin max(20 % of 20, 20 % of 10) = 4 */
        assertEquals(-4.0, e.low, 1e-6);
        assertEquals(24.0, e.high, 1e-6);
    }

    @Test
    public void meterIsDueOnePeriodAfterTheLastReading() {
        DateTime now = new DateTime(2026, 11, 5, 10, 0, ZONE);
        Period p = Period.days(1);
        assertEquals(Due.OK, ReadingExpectation.due(new DateTime(day(5), ZONE), p, WORK_DAY, now));
        assertEquals(Due.DUE, ReadingExpectation.due(new DateTime(day(4), ZONE), p, WORK_DAY, now));
        assertEquals(Due.OVERDUE, ReadingExpectation.due(new DateTime(day(2), ZONE), p, WORK_DAY, now));
        assertEquals(Due.DUE, ReadingExpectation.due(null, p, WORK_DAY, now));
        assertEquals(Due.NONE, ReadingExpectation.due(new DateTime(day(1), ZONE), null, WORK_DAY, now));
        assertEquals(Due.NONE, ReadingExpectation.due(new DateTime(day(1), ZONE), new Period("P0D"), WORK_DAY, now));
        assertEquals(Due.NONE, ReadingExpectation.due(null, Period.ZERO, WORK_DAY, now));
    }

    @Test
    public void monthlyMeterIsDueInTheNextMonth() {
        DateTime last = new DateTime(2026, 10, 1, 0, 0, ZONE);
        Period p = Period.months(1);
        assertEquals(Due.OK, ReadingExpectation.due(last, p, WORK_DAY, new DateTime(2026, 10, 20, 0, 0, ZONE)));
        assertEquals(Due.DUE, ReadingExpectation.due(last, p, WORK_DAY, new DateTime(2026, 11, 5, 0, 0, ZONE)));
        assertEquals(Due.OVERDUE, ReadingExpectation.due(last, p, WORK_DAY, new DateTime(2026, 12, 2, 0, 0, ZONE)));
    }
}
