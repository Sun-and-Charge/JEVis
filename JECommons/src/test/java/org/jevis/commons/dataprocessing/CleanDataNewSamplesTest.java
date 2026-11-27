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
package org.jevis.commons.dataprocessing;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** {@link CleanDataObject#hasNewSamples}: is there anything to clean before a run starts */
public class CleanDataNewSamplesTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime DEC_1 = new DateTime(2025, 12, 1, 0, 0, Z);

    @Test
    public void monthValueToQuarterHoursContinuesInsideTheLastMonth() {
        /* the run stopped on 4 Dec 12:30, the last raw value (1 Dec) covers the month up to 31 Dec 23:45 */
        assertTrue(CleanDataObject.hasNewSamples(new DateTime(2025, 12, 4, 12, 30, Z), DEC_1, Period.months(1), Period.minutes(15), false, Z));
        assertTrue(CleanDataObject.hasNewSamples(new DateTime(2025, 12, 31, 23, 30, Z), DEC_1, Period.months(1), Period.minutes(15), false, Z));
        assertFalse(CleanDataObject.hasNewSamples(new DateTime(2025, 12, 31, 23, 45, Z), DEC_1, Period.months(1), Period.minutes(15), false, Z));
    }

    @Test
    public void samePeriodOrShorterRawPeriodNeedsANewerRawValue() {
        DateTime raw = new DateTime(2025, 12, 1, 12, 0, Z);
        assertTrue(CleanDataObject.hasNewSamples(raw, raw, Period.minutes(15), Period.minutes(15), false, Z));
        assertTrue(CleanDataObject.hasNewSamples(raw.minusMinutes(15), raw, Period.minutes(15), Period.minutes(15), false, Z));
        assertFalse(CleanDataObject.hasNewSamples(raw.plusMinutes(15), raw, Period.minutes(15), Period.minutes(15), false, Z));
        assertFalse(CleanDataObject.hasNewSamples(raw.plusDays(1), raw, Period.minutes(15), Period.days(1), false, Z));
    }
}
