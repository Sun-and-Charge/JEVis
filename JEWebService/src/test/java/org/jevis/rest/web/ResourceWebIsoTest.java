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

import jakarta.ws.rs.core.Response;
import org.jevis.commons.iso.ActionItem;
import org.jevis.commons.iso.NonconformityItem;
import org.jevis.commons.iso.Obligation;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebIsoTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final LocalDate TODAY = new LocalDate(2026, 11, 6);
    private static final Set<String> FILE_CLASSES = Collections.singleton(ResourceWebIso.CLASS_FILE);

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Site 1 > plan 10 > directory 11 > actions 12, 13 (deleted in JSON), 14 (unreadable), 15 (no JSON);
       files 16, 17 (unreadable), 18 (recycle bin) below action 12; plan 20 without site; other plan 30 */
    private static final JsonObject SITE = obj(1, "Site A", ResourceWebNavigation.CLASS_BUILDING, 0);
    private static final JsonObject PLAN = obj(10, "Plan B", ActionItem.PLAN_CLASS_NAME, 1);
    private static final JsonObject DIR = obj(11, "Actions", "Action Directory", 10);
    private static final JsonObject ACTION_1 = obj(12, "AP 002", ActionItem.CLASS_NAME, 11);
    private static final JsonObject ACTION_DELETED = obj(13, "AP 003", ActionItem.CLASS_NAME, 11);
    private static final JsonObject ACTION_HIDDEN = obj(14, "AP 004", ActionItem.CLASS_NAME, 11);
    private static final JsonObject ACTION_BROKEN = obj(15, "AP 005", ActionItem.CLASS_NAME, 11);
    private static final JsonObject FILE_1 = obj(16, "file", ResourceWebIso.CLASS_FILE, 12);
    private static final JsonObject FILE_HIDDEN = obj(17, "file 2", ResourceWebIso.CLASS_FILE, 12);
    private static final JsonObject FILE_BIN = obj(18, "file 3", ResourceWebIso.CLASS_FILE, 12);
    private static final JsonObject PLAN_2 = obj(20, "Plan A", ActionItem.PLAN_CLASS_NAME, 0);
    private static final JsonObject ACTION_2 = obj(21, "AP 001", ActionItem.CLASS_NAME, 20);
    private static final JsonObject FILE_OTHER = obj(22, "file 4", ResourceWebIso.CLASS_FILE, 1);
    private static final JsonObject NC_PLAN = obj(30, "Nonconformities", NonconformityItem.PLAN_CLASS_NAME, 1);
    private static final JsonObject NC = obj(31, "NC1", NonconformityItem.CLASS_NAME, 30);
    private static final JsonObject LEGAL_PLAN = obj(40, "Legal", Obligation.PLAN_CLASS_NAME, 1);
    private static final JsonObject OBLIGATION = obj(41, "Obligation", Obligation.CLASS_NAME, 40);

    static {
        FILE_BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(SITE, PLAN, DIR, ACTION_1, ACTION_DELETED, ACTION_HIDDEN,
            ACTION_BROKEN, FILE_1, FILE_HIDDEN, FILE_BIN, PLAN_2, ACTION_2, FILE_OTHER, NC_PLAN, NC, LEGAL_PLAN, OBLIGATION);

    private static ResourceWebModules.Lookup lookup(JsonObject... unreadable) {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(Arrays.asList(unreadable));
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable, parents);
    }

    private static final Map<Long, String> DATA = new HashMap<>();

    static {
        DATA.put(12L, "{\"No\": 2, \"Title\": \"Action B\", \"Status Tags\": \"Status C\", \"Medium Tags\": \"m1\","
                + " \"Done Date\": \"2026-11-01T10:00:00.000+01:00\","
                + " \"Consumption\": {\"Diff\": 36.5, \"Data Source\": \"1\"}}");
        DATA.put(13L, "{\"No\": 3, \"Title\": \"Action C\", \"Deleted\": true}");
        DATA.put(14L, "{\"No\": 4, \"Title\": \"Action D\"}");
        DATA.put(15L, "not json");
        DATA.put(21L, "{\"Nr\": 1, \"Title\": \"Action A\"}");
        DATA.put(31L, "{\"Nr\": 1, \"Title\": \"Finding A\", \"Field Tags\": [\"Field A\"], \"Deleted\": false}");
        DATA.put(41L, "{\"Nr\": 72, \"title\": \"Regulation A\", \"link\": \"https://example.org\"}");
    }

    private static Map<String, String> planAttributes() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put(ActionItem.PLAN_ATTRIBUTE_PREFIX, "AP");
        attributes.put(ActionItem.PLAN_ATTRIBUTE_STATUS, "Status A;Status B");
        attributes.put(ActionItem.PLAN_ATTRIBUTE_FIELDS, "Field A;Field B");
        attributes.put(ActionItem.PLAN_ATTRIBUTE_SEU, "SEU A");
        attributes.put(ActionItem.PLAN_ATTRIBUTE_MEDIUM, "[{\"id\":\"m1\",\"name\":\"Medium A\",\"co2\":0.3}]");
        return attributes;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> json, String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    @Test
    public void plansAreSortedAndItemsBelongToTheirNearestPlan() {
        ResourceWebModules.Lookup lookup = lookup(ACTION_HIDDEN);
        assertEquals(Arrays.asList(PLAN_2, PLAN), ResourceWebIso.plans(lookup, ActionItem.PLAN_CLASS_NAME));
        assertEquals(Arrays.asList(ACTION_1, ACTION_DELETED, ACTION_BROKEN),
                ResourceWebIso.itemsOf(PLAN, ActionItem.CLASS_NAME, lookup));
        assertEquals(Collections.singletonList(ACTION_2), ResourceWebIso.itemsOf(PLAN_2, ActionItem.CLASS_NAME, lookup));
        assertNull(ResourceWebIso.planOf(PLAN, lookup));
        assertTrue(ResourceWebIso.plans(lookup(PLAN, PLAN_2), ActionItem.PLAN_CLASS_NAME).isEmpty());
    }

    @Test
    public void actionPlanIsMapped() {
        ResourceWebModules.Lookup lookup = lookup(ACTION_HIDDEN, FILE_HIDDEN);
        Map<String, String> attributes = planAttributes();
        Map<String, Object> plan = ResourceWebIso.actionPlanJson(PLAN, attributes::get,
                ResourceWebIso.itemsOf(PLAN, ActionItem.CLASS_NAME, lookup), DATA::get, id -> id == 1L ? "kWh" : null,
                id -> ResourceWebIso.attachmentsOf(id, lookup, FILE_CLASSES).size(), lookup, TODAY, ZONE);

        assertEquals(10L, plan.get("id"));
        assertEquals("Plan B", plan.get("name"));
        @SuppressWarnings("unchecked") Map<String, Object> site = (Map<String, Object>) plan.get("site");
        assertEquals(1L, site.get("id"));
        assertEquals("AP", plan.get("prefix"));
        assertEquals(Arrays.asList("Status A", "Status B", "Status C"), plan.get("statuses"));
        assertEquals(Arrays.asList("Field A", "Field B"), plan.get("fields"));
        assertEquals(Collections.singletonList("SEU A"), plan.get("seus"));
        List<Map<String, Object>> media = list(plan, "media");
        assertEquals("m1", media.get(0).get("id"));
        assertEquals(0.3, media.get(0).get("co2"));

        /* deleted (JSON) and unreadable JSON are left out, the unreadable object is not passed */
        List<Map<String, Object>> actions = list(plan, "actions");
        assertEquals(1, actions.size());
        Map<String, Object> action = actions.get(0);
        assertEquals(12L, action.get("id"));
        assertEquals("AP 002", action.get("number"));
        assertEquals("Medium A", action.get("mediumName"));
        assertEquals(5, action.get("daysSinceDone"));
        assertEquals(0.5, (Double) action.get("savingsSinceDone"), 1e-9);
        @SuppressWarnings("unchecked") Map<String, Object> consumption = (Map<String, Object>) action.get("consumption");
        assertEquals("kWh", consumption.get("unit"));
        /* file 17 is not readable, file 18 is in the recycle bin */
        assertEquals(1, action.get("attachments"));
    }

    @Test
    public void actionsAreSortedByNumber() {
        ResourceWebModules.Lookup lookup = lookup();
        Map<Long, String> data = new HashMap<>();
        data.put(12L, "{\"No\": 9}");
        data.put(13L, "{\"No\": 1}");
        data.put(14L, "{\"No\": 5}");
        Map<String, Object> plan = ResourceWebIso.actionPlanJson(PLAN, name -> null,
                ResourceWebIso.itemsOf(PLAN, ActionItem.CLASS_NAME, lookup), data::get, id -> null, id -> 0,
                lookup, TODAY, ZONE);
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> a : list(plan, "actions")) ids.add((Long) a.get("id"));
        assertEquals(Arrays.asList(13L, 14L, 12L), ids);
        assertEquals("", plan.get("prefix"));
        assertTrue(list(plan, "media").isEmpty());
        assertEquals("001", list(plan, "actions").get(0).get("number"));
    }

    @Test
    public void nonconformityPlanIsMapped() {
        ResourceWebModules.Lookup lookup = lookup();
        Map<String, String> attributes = new HashMap<>();
        attributes.put(NonconformityItem.PLAN_ATTRIBUTE_PREFIX, "NC");
        attributes.put(NonconformityItem.PLAN_ATTRIBUTE_MEDIUM, "Medium A;Medium B");
        Map<String, Object> plan = ResourceWebIso.nonconformityPlanJson(NC_PLAN, attributes::get,
                ResourceWebIso.itemsOf(NC_PLAN, NonconformityItem.CLASS_NAME, lookup), DATA::get, id -> 0, lookup,
                new DateTime(2026, 11, 6, 10, 0, ZONE));
        assertEquals("NC", plan.get("prefix"));
        assertEquals(Arrays.asList("Medium A", "Medium B"), plan.get("media"));
        assertTrue(((List<?>) plan.get("fields")).isEmpty());
        Map<String, Object> item = list(plan, "items").get(0);
        assertEquals(31L, item.get("id"));
        assertEquals("NC 001", item.get("number"));
        assertEquals(Collections.singletonList("Field A"), item.get("fields"));
        assertEquals(true, item.get("open"));
        assertEquals(0, item.get("attachments"));
    }

    @Test
    public void legalPlanIsMapped() {
        ResourceWebModules.Lookup lookup = lookup();
        Map<String, String> attributes = new HashMap<>();
        attributes.put(Obligation.PLAN_ATTRIBUTE_CATEGORY, "Category A;Category B");
        attributes.put(Obligation.PLAN_ATTRIBUTE_SCOPE, "Scope A");
        Map<String, Object> plan = ResourceWebIso.legalPlanJson(LEGAL_PLAN, attributes::get,
                ResourceWebIso.itemsOf(LEGAL_PLAN, Obligation.CLASS_NAME, lookup), DATA::get, id -> 2, lookup);
        assertEquals(Arrays.asList("Category A", "Category B"), plan.get("categories"));
        assertEquals(Collections.singletonList("Scope A"), plan.get("scopes"));
        Map<String, Object> obligation = list(plan, "obligations").get(0);
        assertEquals(72, obligation.get("nr"));
        assertEquals("https://example.org", obligation.get("link"));
        assertEquals(2, obligation.get("attachments"));
        assertFalse(plan.containsKey("prefix"));
    }

    @Test
    public void attachmentsAndDownloadRights() {
        ResourceWebModules.Lookup lookup = lookup(FILE_HIDDEN);
        assertEquals(ACTION_1, ResourceWebIso.item(lookup, 12));
        assertNull("no item", ResourceWebIso.item(lookup, 11));
        assertNull("not readable", ResourceWebIso.item(lookup(ACTION_1), 12));
        assertEquals(Collections.singletonList(FILE_1), ResourceWebIso.attachmentsOf(12, lookup, FILE_CLASSES));

        assertTrue(ResourceWebIso.isDownloadable(16, lookup, FILE_CLASSES));
        assertFalse("file not readable", ResourceWebIso.isDownloadable(17, lookup, FILE_CLASSES));
        assertFalse("recycle bin", ResourceWebIso.isDownloadable(18, lookup, FILE_CLASSES));
        assertFalse("item not readable", ResourceWebIso.isDownloadable(16, lookup(ACTION_1), FILE_CLASSES));
        assertFalse("parent is no item", ResourceWebIso.isDownloadable(22, lookup, FILE_CLASSES));
        assertFalse("no file", ResourceWebIso.isDownloadable(12, lookup, FILE_CLASSES));
        assertFalse("unknown", ResourceWebIso.isDownloadable(999, lookup, FILE_CLASSES));
    }

    @Test
    public void attachmentJson() {
        JsonSample sample = new JsonSample();
        sample.setTs("2026-11-01T10:00:00.000+01:00");
        sample.setValue("offer.pdf");
        Map<String, Object> json = ResourceWebIso.attachmentJson(FILE_1, sample, 12345L);
        assertEquals(16L, json.get("id"));
        assertEquals("offer.pdf", json.get("name"));
        assertEquals(12345L, json.get("size"));
        assertEquals("2026-11-01T10:00:00.000+01:00", json.get("date"));

        Map<String, Object> empty = ResourceWebIso.attachmentJson(FILE_1, null, null);
        assertEquals("file", empty.get("name"));
        assertNull(empty.get("date"));
    }

    @Test
    public void contentTypesAndFileNames() {
        assertEquals("application/pdf", ResourceWebIso.contentType("Offer.PDF"));
        assertEquals("image/jpeg", ResourceWebIso.contentType("photo.jpeg"));
        assertEquals("application/octet-stream", ResourceWebIso.contentType("script.html"));
        assertEquals("application/octet-stream", ResourceWebIso.contentType("noextension"));
        assertEquals("application/octet-stream", ResourceWebIso.contentType("dot."));
        assertEquals("application/octet-stream", ResourceWebIso.contentType(null));

        assertEquals("passwd", ResourceWebIso.safeFileName("../../etc/passwd"));
        assertEquals("a_b_.txt", ResourceWebIso.safeFileName("a\"b\r.txt"));
        assertEquals("download", ResourceWebIso.safeFileName(" .. "));
        assertEquals("download", ResourceWebIso.safeFileName(null));

        assertEquals("attachment; filename=\"Pr_fung 1.pdf\"; filename*=UTF-8''Pr%C3%BCfung%201.pdf",
                ResourceWebIso.contentDisposition("Prüfung 1.pdf"));
    }

    @Test
    public void downloadHeaders() {
        Response response = ResourceWebIso.download(new byte[]{1, 2}, "list.csv");
        assertEquals(200, response.getStatus());
        assertEquals("text/csv", response.getMediaType().toString());
        assertEquals("nosniff", response.getHeaderString("X-Content-Type-Options"));
        assertEquals("private, no-store", response.getHeaderString("Cache-Control"));
        assertTrue(response.getHeaderString("Content-Disposition").startsWith("attachment; filename=\"list.csv\""));
    }

    @Test
    public void moduleCountsCountReadablePlans() {
        Map<String, Object> counts = ResourceWebModules.moduleCounts(Arrays.asList(SITE, PLAN, PLAN_2, NC_PLAN, ACTION_1));
        assertEquals(2, counts.get("actionPlans"));
        assertEquals(1, counts.get("nonconformities"));
        assertEquals(0, counts.get("legal"));
        assertEquals(0, counts.get("appointments"));
    }
}
