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
package org.jevis.report3.data.reportlink;

import org.jevis.commons.iso.ActionItem;
import org.jevis.commons.iso.NonconformityItem;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnergyManagementFunctionTest {
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    /* report period: the year 2026 */
    private static final DateTime START = new DateTime(2026, 1, 1, 0, 0, ZONE);
    private static final DateTime END = new DateTime(2027, 1, 1, 0, 0, ZONE);

    private static ActionItem action(int no, String created, String done, double diff, double saving, double investment) {
        return ActionItem.parse("{\"No\":" + no + ",\"Title\":\"A" + no + "\",\"Status Tags\":\"x\",\"Create Date\":\"" + created + "\""
                + (done != null ? ",\"Done Date\":\"" + done + "\"" : "")
                + ",\"Consumption\":{\"Diff\":" + diff + "}"
                + ",\"NPV\":{\"Saving\":" + saving + ",\"Investment\":" + investment + "}}");
    }

    private static NonconformityItem nonconformity(int nr, String created, String planned, String done) {
        return NonconformityItem.parse("{\"Nr\":" + nr + ",\"Title\":\"N" + nr + "\",\"Create Date\":\"" + created + "\""
                + (planned != null ? ",\"Planned Date\":\"" + planned + "\"" : "")
                + (done != null ? ",\"Done Date\":\"" + done + "\"" : "") + "}");
    }

    @SuppressWarnings("unchecked")
    @Test
    void actionsAreSplitByTheReportPeriod() {
        List<ActionItem> items = Arrays.asList(
                action(1, "2025-03-01T00:00:00.000+01:00", "2025-11-10T00:00:00.000+01:00", 1000, 200, 5000),   // done before
                action(2, "2025-06-01T00:00:00.000+02:00", "2026-04-15T00:00:00.000+02:00", 3000, 600, 8000),   // done in period
                action(3, "2026-02-01T00:00:00.000+01:00", null, 500, 100, 1500),                               // open
                action(4, "2027-02-01T00:00:00.000+01:00", null, 9999, 9999, 9999));                            // created after the period
        Map<String, Object> map = EnergyManagementFunction.build(
                Collections.singletonList(new EnergyManagementFunction.Plan<>("Aktionsplan", "MW", Collections.emptyList(), items)),
                Collections.emptyList(), START, END, ZONE);

        assertEquals("01.01.2026", map.get("periodStart"));
        assertEquals("31.12.2026", map.get("periodEnd"));
        assertEquals(3, ((List<?>) map.get("actions")).size());
        Map<String, Object> summary = (Map<String, Object>) map.get("actionSummary");
        assertEquals(3, summary.get("total"));
        assertEquals(1, summary.get("open"));
        assertEquals(2, summary.get("done"));
        assertEquals(1, summary.get("doneInPeriod"));
        assertEquals(500d, summary.get("plannedSavingKWh"));
        assertEquals(4000d, summary.get("achievedSavingKWh"));
        assertEquals(800d, summary.get("savingEuroPerYear"));
        assertEquals(14500d, summary.get("investment"));
        Map<String, Object> doneRow = ((List<Map<String, Object>>) map.get("actionsDoneInPeriod")).get(0);
        assertEquals("MW 002", doneRow.get("number"));
        assertEquals("15.04.2026", doneRow.get("doneDateText"));
        assertEquals("Aktionsplan", doneRow.get("plan"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void nonconformitiesCountOpenOverdueAndClosed() {
        List<NonconformityItem> items = Arrays.asList(
                nonconformity(1, "2025-05-01T00:00:00.000+02:00", "2025-06-01T00:00:00.000+02:00", "2026-03-01T00:00:00.000+01:00"), // closed in period
                nonconformity(2, "2026-05-01T00:00:00.000+02:00", "2026-06-01T00:00:00.000+02:00", null),                            // open, overdue
                nonconformity(3, "2026-11-01T00:00:00.000+01:00", "2027-03-01T00:00:00.000+01:00", null),                            // open
                nonconformity(4, "2024-01-01T00:00:00.000+01:00", null, "2024-02-01T00:00:00.000+01:00"));                           // closed before
        Map<String, Object> map = EnergyManagementFunction.build(Collections.emptyList(),
                Collections.singletonList(new EnergyManagementFunction.Plan<>("Abweichungen", "AB", Collections.emptyList(), items)),
                START, END, ZONE);
        Map<String, Object> summary = (Map<String, Object>) map.get("nonconformitySummary");
        assertEquals(4, summary.get("total"));
        assertEquals(2, summary.get("open"));
        assertEquals(1, summary.get("overdue"));
        assertEquals(2, summary.get("newInPeriod"));
        assertEquals(1, summary.get("closedInPeriod"));
        assertEquals(2, ((List<?>) map.get("nonconformitiesOpen")).size());
    }
}
