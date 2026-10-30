/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm;

import org.jevis.commons.datetime.Period;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AlarmProcessTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 10, 30, 14, 37, 12, ZONE);

    @Test
    public void firstStartIsTheLastCompletePeriod() {
        assertEquals(new DateTime(2026, 10, 30, 14, 15, ZONE), AlarmProcess.firstStart(Period.QUARTER_HOURLY, ZONE, NOW, null));
        assertEquals(new DateTime(2026, 10, 30, 13, 0, ZONE), AlarmProcess.firstStart(Period.HOURLY, ZONE, NOW, null));
        assertEquals(new DateTime(2026, 10, 29, 0, 0, ZONE), AlarmProcess.firstStart(Period.DAILY, ZONE, NOW, null));
        assertEquals(new DateTime(2026, 10, 19, 0, 0, ZONE), AlarmProcess.firstStart(Period.WEEKLY, ZONE, NOW, null));
        assertEquals(new DateTime(2026, 9, 1, 0, 0, ZONE), AlarmProcess.firstStart(Period.MONTHLY, ZONE, NOW, null));
        assertEquals(new DateTime(2026, 7, 1, 0, 0, ZONE), AlarmProcess.firstStart(Period.QUARTERLY, ZONE, NOW, null));
        assertEquals(new DateTime(2025, 1, 1, 0, 0, ZONE), AlarmProcess.firstStart(Period.YEARLY, ZONE, NOW, null));
    }

    @Test
    public void withoutPeriodTheLastDayIsChecked() {
        assertEquals(NOW.minusDays(1), AlarmProcess.firstStart(Period.NONE, ZONE, NOW, null));
    }
}
