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
package org.jevis.commons.iso;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class IsoJsonTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final LocalDate TODAY = new LocalDate(2026, 11, 6);

    /* as written by the Control Center with Gson: legacy keys, NaN, ISO dates with offset, unknown keys */
    private static final String ACTION_JSON = "{\n"
            + "  \"NPV\": {\"Interest Rate\": 10.0, \"Investment\": 1000.0, \"Saving\": 300.0, \"Running Cost\": 0.0,"
            + " \"Inflation\": 3.1, \"Years\": 2, \"Duration\": 1, \"Result\": 123.4, \"Profitability Index\": NaN,"
            + " \"Result over X\": Infinity, \"Sum Net\": -Infinity},\n"
            + "  \"From User\": \"User A\",\n"
            + "  \"Nr\": 3,\n"
            + "  \"Desciption\": \"Text A\",\n"
            + "  \"Create Date\": \"2026-01-10T08:00:00.000+01:00\",\n"
            + "  \"Done Date\": \"2026-07-09T12:00:00.000+02:00\",\n"
            + "  \"Title\": \"Action A\",\n"
            + "  \"Status Tags\": \"Status A\",\n"
            + "  \"Medium Tags\": \"1700000000000\",\n"
            + "  \"EnpI\": {\"Before\": 5.0, \"After\": 4.0, \"Diff\": 1.0, \"Object\": 0, \"Data Source\": \"-2\"},\n"
            + "  \"Consumption\": {\"Before\": 100.0, \"After\": 80.0, \"Diff\": 20.0, \"Object\": 0, \"Data Source\": \"42\","
            + " \"Before From Date\": \"2025-01-01T00:00:00.000+01:00\"},\n"
            + "  \"Check List\": {\"Process Document\": true, \"New EnPI\": \"EnPI A\"},\n"
            + "  \"Unknown Key\": [1, 2],\n"
            + "  \"Deleted\": false\n"
            + "}";

    @Test
    public void actionIsReadWithLegacyKeysAndNonNumericNumbers() {
        ActionItem action = ActionItem.parse(ACTION_JSON);
        assertNotNull(action);
        assertEquals(3, action.getNo());
        assertFalse(action.isDeleted());

        List<Medium> media = Collections.singletonList(new Medium("1700000000000", "Medium A", 0.5));
        Map<String, Object> json = action.toJson("AP", media, TODAY, ZONE, id -> id == 42L ? "kWh" : null);
        assertEquals("AP 003", json.get("number"));
        assertEquals("Text A", json.get("description"));
        assertEquals("", json.get("note"));
        assertEquals("Medium A", json.get("mediumName"));
        assertEquals("2026-07-09T12:00:00.000+02:00", json.get("doneDate"));
        assertNull(json.get("plannedDate"));

        @SuppressWarnings("unchecked") Map<String, Object> npv = (Map<String, Object>) json.get("npv");
        /* results are computed from the inputs like NPVData.updateResults, the stored (partly non-numeric) results are
           not used: years 309.30 and 318.89 deposit, discounted 281.18 and 263.55 */
        assertEquals(-455.27, (Double) npv.get("result"), 1e-9);
        assertEquals(0.5447273, (Double) npv.get("profitabilityIndex"), 1e-6);
        assertEquals(-718.82, (Double) npv.get("resultOverX"), 1e-9);
        assertEquals(0.2811818, (Double) npv.get("profitabilityIndexOverX"), 1e-6);
        assertEquals(628.19, (Double) npv.get("sumDeposits"), 1e-9);
        assertEquals(1000.0, (Double) npv.get("sumPayouts"), 1e-9);
        assertEquals(-371.81, (Double) npv.get("sumNet"), 1e-9);

        @SuppressWarnings("unchecked") Map<String, Object> consumption = (Map<String, Object>) json.get("consumption");
        assertEquals(20.0, consumption.get("diff"));
        assertEquals("kWh", consumption.get("unit"));
        assertEquals(42L, consumption.get("objectId"));
        assertEquals("2025-01-01T00:00:00.000+01:00", consumption.get("beforeFrom"));
        assertNull(consumption.get("afterUntil"));

        @SuppressWarnings("unchecked") Map<String, Object> enpi = (Map<String, Object>) json.get("enpi");
        assertNull("placeholder ids are no object", enpi.get("objectId"));
        assertNull(enpi.get("unit"));

        @SuppressWarnings("unchecked") Map<String, Object> checkList = (Map<String, Object>) json.get("checkList");
        assertEquals(true, checkList.get("processDocument"));
        assertEquals(false, checkList.get("drawing"));
        assertEquals("EnPI A", checkList.get("newEnPI"));

        /* 2026-07-09 to 2026-11-06 */
        assertEquals(120, json.get("daysSinceDone"));
        assertEquals(120 * 20.0 / 365, (Double) json.get("savingsSinceDone"), 1e-9);
    }

    @Test
    public void currentKeysWinAndMissingPartsAreNull() {
        ActionItem action = ActionItem.parse("{\"No\": 7, \"Description\": \"Text B\", \"Deleted\": true}");
        assertNotNull(action);
        assertTrue(action.isDeleted());
        Map<String, Object> json = action.toJson("", Collections.emptyList(), TODAY, ZONE, null);
        assertEquals("007", json.get("number"));
        assertEquals("Text B", json.get("description"));
        assertNull(json.get("npv"));
        assertNull(json.get("consumption"));
        assertNull(json.get("daysSinceDone"));
        assertNull(json.get("savingsSinceDone"));
        assertEquals("", json.get("mediumName"));
    }

    @Test
    public void invalidJsonIsNull() {
        assertNull(ActionItem.parse("{no json"));
        assertNull(ActionItem.parse(""));
        assertNull(NonconformityItem.parse(null));
    }

    @Test
    public void npvYearRowsLikeTheControlCenter() {
        NpvData npv = ActionItem.parse(ACTION_JSON).getNpv();
        List<NpvData.YearRow> rows = npv.yearRows();
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getYear());
        assertEquals(309.3, rows.get(0).getDeposit(), 1e-9);
        assertEquals(0.0, rows.get(0).getCost(), 1e-9);
        assertEquals(309.3, rows.get(0).getNet(), 1e-9);
        assertEquals(281.18, rows.get(0).getDiscounted(), 1e-9);
        assertEquals(318.89, rows.get(1).getDeposit(), 1e-9);
        assertEquals(263.55, rows.get(1).getDiscounted(), 1e-9);
    }

    @Test
    public void npvRunningCostIsSubtracted() {
        NpvData npv = IsoJson.read("{\"Saving\": 100, \"Running Cost\": 50, \"Inflation\": 0, \"Interest Rate\": 0,"
                + " \"Years\": 1}", NpvData.class);
        NpvData.YearRow row = npv.yearRows().get(0);
        assertEquals(50.0, row.getCost(), 1e-9);
        assertEquals(50.0, row.getNet(), 1e-9);
        assertEquals(50.0, row.getDiscounted(), 1e-9);
        assertTrue(IsoJson.read("{\"Years\": -3}", NpvData.class).yearRows().isEmpty());
        assertEquals(NpvData.MAX_YEARS, IsoJson.read("{\"Years\": 100000}", NpvData.class).yearRows().size());
    }

    @Test
    public void mediaFromJsonAndLegacyList() {
        List<Medium> media = Medium.parseList("[{\"id\":\"1\",\"name\":\"Medium A\",\"co2\":0.2},{\"id\":\"2\",\"name\":\"Medium B\"}]");
        assertEquals(2, media.size());
        assertEquals("Medium A", media.get(0).getName());
        assertEquals(0.2, media.get(0).getCo2(), 1e-9);
        assertEquals(0.366, media.get(1).getCo2(), 1e-9);

        List<Medium> legacy = Medium.parseList(";Medium A;Medium B");
        assertEquals(2, legacy.size());
        assertEquals("Medium A", legacy.get(0).getId());
        assertEquals("Medium A", legacy.get(0).getName());
        assertEquals(Medium.LEGACY_CO2, legacy.get(0).getCo2(), 1e-9);

        assertTrue(Medium.parseList(null).isEmpty());
        assertTrue(Medium.parseList("[broken").isEmpty());
        assertEquals("2", Medium.byId(media, "2").getId());
        assertNull(Medium.byId(media, "3"));
    }

    @Test
    public void unknownMediumKeepsTheStoredValue() {
        ActionItem action = ActionItem.parse("{\"Medium Tags\": \"Medium C\"}");
        assertEquals("Medium C", action.mediumName(Medium.parseList(";Medium A")));
    }

    @Test
    public void listsAndNumbers() {
        assertEquals(Arrays.asList("A", "B"), IsoJson.splitList(" A;;B; A ;"));
        assertTrue(IsoJson.splitList(null).isEmpty());
        assertEquals("NC 012", IsoJson.number(" NC ", 12));
        assertEquals("1234", IsoJson.number(null, 1234));
        assertNull(IsoJson.finite(Double.NaN));
        assertEquals(1.5, IsoJson.finite(1.5), 1e-9);
    }

    @Test
    public void onlyHttpLinks() {
        assertEquals("https://example.org/a", IsoJson.httpUrl(" https://example.org/a "));
        assertEquals("HTTP://example.org", IsoJson.httpUrl("HTTP://example.org"));
        assertNull(IsoJson.httpUrl("javascript:alert(1)"));
        assertNull(IsoJson.httpUrl("file:///etc/passwd"));
        assertNull(IsoJson.httpUrl("https://"));
        assertNull(IsoJson.httpUrl(""));
        assertNull(IsoJson.httpUrl(null));
    }

    private static final String NONCONFORMITY_JSON = "{\"Title\": \"Finding A\", \"Creator\": \"User A\", \"Nr\": 1,"
            + " \"Description\": \"Text A\", \"Immediate Measures\": \"\", \"Responsible Person\": \"User B\","
            + " \"Check List\": {\"Immediate Action Required\": true, \"Design\": false},"
            + " \"Create Date\": \"2026-10-01T10:00:00.000+02:00\", \"Planned Date\": \"2026-11-05T10:00:00.000+01:00\","
            + " \"Done Date\": null, \"Deleted\": false, \"Medium\": \"Medium A\", \"Field Tags\": [\"Field A\", \"Field B\"],"
            + " \"Action\": \"\", \"SEU\": \"SEU A\"}";

    @Test
    public void nonconformityIsMapped() {
        NonconformityItem item = NonconformityItem.parse(NONCONFORMITY_JSON);
        assertNotNull(item);
        DateTime now = new DateTime(2026, 11, 6, 10, 0, ZONE);
        Map<String, Object> json = item.toJson("NC", now, TODAY, ZONE);
        assertEquals("NC 001", json.get("number"));
        assertEquals("User B", json.get("responsible"));
        assertEquals(Arrays.asList("Field A", "Field B"), json.get("fields"));
        assertNull(json.get("doneDate"));
        assertEquals(true, json.get("open"));
        assertEquals(true, json.get("overdue"));
        assertEquals(Collections.singletonList(NonconformityItem.WARNING_IMMEDIATE_MEASURES_MISSING), json.get("warnings"));
        @SuppressWarnings("unchecked") Map<String, Object> checkList = (Map<String, Object>) json.get("checkList");
        assertEquals(true, checkList.get("immediateActionRequired"));
        assertEquals(false, checkList.get("metrics"));
        assertEquals(12, checkList.size());
    }

    @Test
    public void nonconformityWarningsForDoneDates() {
        DateTime now = new DateTime(2026, 11, 6, 10, 0, ZONE);
        NonconformityItem future = NonconformityItem.parse("{\"Done Date\": \"2026-12-01T10:00:00.000+01:00\","
                + " \"Planned Date\": \"2026-01-01T10:00:00.000+01:00\", \"Action\": \"\"}");
        assertFalse(future.isOpen());
        assertFalse(future.isOverdue(TODAY, ZONE));
        assertEquals(Arrays.asList(NonconformityItem.WARNING_DONE_WITHOUT_ACTION, NonconformityItem.WARNING_DONE_IN_FUTURE),
                future.warnings(now));

        NonconformityItem ok = NonconformityItem.parse("{\"Done Date\": \"2026-11-01T10:00:00.000+01:00\","
                + " \"Action\": \"Action A\", \"Check List\": {\"Immediate Action Required\": true},"
                + " \"Immediate Measures\": \"Measure A\"}");
        assertTrue(ok.warnings(now).isEmpty());

        NonconformityItem planned = NonconformityItem.parse("{\"Planned Date\": \"2026-11-06T23:00:00.000+01:00\"}");
        assertFalse("planned today is not overdue", planned.isOverdue(TODAY, ZONE));
    }

    @Test
    public void fieldTagsAsText() {
        assertEquals(Arrays.asList("Field A", "Field B"),
                NonconformityItem.parse("{\"Field Tags\": \"[Field A, Field B]\"}").getFields());
        assertEquals(Arrays.asList("Field A", "Field B"),
                NonconformityItem.parse("{\"Field Tags\": \"Field A;Field B\"}").getFields());
        assertTrue(NonconformityItem.parse("{\"Field Tags\": null}").getFields().isEmpty());
        assertTrue(NonconformityItem.parse("{}").getFields().isEmpty());
    }

    @Test
    public void obligationIsMapped() {
        Obligation obligation = Obligation.parse("{\"uuid\": \"00000000-0000-0000-0000-000000000001\", \"Nr\": 72,"
                + " \"title\": \"Regulation A\", \"Designation\": \"Designation A\", \"Relevance 50001\": true,"
                + " \"link\": \"javascript:alert(1)\", \"category\": \"Category A\", \"scope\": \"Scope A\","
                + " \"issue date\": \"2020-01-01T00:00:00.000+01:00\", \"importance for the company\": \"High\"}");
        Map<String, Object> json = obligation.toJson();
        assertEquals(72, json.get("nr"));
        assertEquals("Regulation A", json.get("title"));
        assertEquals("High", json.get("importance"));
        assertNull(json.get("link"));
        assertEquals(true, json.get("relevant"));
        assertEquals("2020-01-01T00:00:00.000+01:00", json.get("issueDate"));
        assertNull(json.get("examinationDate"));
        assertFalse(obligation.isDeleted());
    }
}
