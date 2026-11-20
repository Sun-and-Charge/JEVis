/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.context;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportInfoTest {
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");

    @Test
    void periodTextFollowsTheSchedule() {
        DateTime oct = new DateTime(2026, 10, 1, 0, 0, ZONE);
        assertEquals("Oktober 2026", ReportInfo.text(oct, oct.plusMonths(1).minusMillis(1), "MONTHLY"));
        assertEquals("2026", ReportInfo.text(new DateTime(2026, 1, 1, 0, 0, ZONE), new DateTime(2026, 12, 31, 23, 59, ZONE), "YEARLY"));
        assertEquals("4. Quartal 2026", ReportInfo.text(oct, oct.plusMonths(3), "QUARTERLY"));
        assertEquals("KW 47 2026", ReportInfo.text(new DateTime(2026, 11, 16, 0, 0, ZONE), new DateTime(2026, 11, 22, 0, 0, ZONE), "WEEKLY"));
        assertEquals("01.10.2026 – 15.10.2026", ReportInfo.text(oct, new DateTime(2026, 10, 15, 0, 0, ZONE), "CUSTOM"));
        /* a year from November and a month from the 15th are not calendar units */
        assertEquals("November 2025 – Oktober 2026", ReportInfo.text(new DateTime(2025, 11, 1, 0, 0, ZONE), new DateTime(2026, 10, 31, 23, 59, ZONE), "YEARLY"));
        assertEquals("15.10.2026 – 14.11.2026", ReportInfo.text(new DateTime(2026, 10, 15, 0, 0, ZONE), new DateTime(2026, 11, 14, 0, 0, ZONE), "MONTHLY"));
    }

    @Test
    void periodEndIsTheLastDayForExclusiveAndInclusiveEnds() {
        DateTime oct = new DateTime(2026, 10, 1, 0, 0, ZONE);
        Map<String, Object> exclusive = ReportInfo.period(oct, oct.plusMonths(1), "MONTHLY", ZONE);
        assertEquals("01.10.2026", exclusive.get("periodStart"));
        assertEquals("31.10.2026", exclusive.get("periodEnd"));
        Map<String, Object> inclusive = ReportInfo.period(oct, oct.plusMonths(1).minusMillis(1), "MONTHLY", ZONE);
        assertEquals("31.10.2026", inclusive.get("periodEnd"));
    }
}
