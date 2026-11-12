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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.iso.ActionItem;
import org.jevis.commons.iso.IsoJson;
import org.jevis.commons.iso.NonconformityItem;
import org.jevis.commons.iso.Obligation;
import org.jevis.commons.ws.json.JsonObject;
import org.joda.time.DateTime;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.jevis.rest.web.ResourceWebEditIso.Kind.*;
import static org.junit.Assert.*;

/**
 * Write endpoints for action plans, nonconformities and the legal register (stage E2).
 */
public class ResourceWebEditIsoTest {

    private static final DateTime NOW = new DateTime(2026, 11, 12, 10, 30, 15, 0, ZONE);
    private static final DateTime EARLIER = new DateTime(2026, 11, 1, 8, 0, ZONE);
    private static final String DATA = IsoJson.ATTRIBUTE_DATA;
    private static final String TODAY = "2026-11-12T00:00:00.000+01:00";

    private static final String ACTION_12 = "{\n  \"No\": 1,\n  \"Title\": \"First action\",\n"
            + "  \"Desciption\": \"Old text\",\n  \"Status Tags\": \"Retired status\",\n"
            + "  \"Field Tags\": \"Field A;Retired field\",\n  \"Medium Tags\": \"m1\",\n"
            + "  \"NPV\": {\n    \"Years\": 2,\n    \"Result\": NaN,\n    \"Year Data\": [1, 2]\n  },\n  \"Unknown Key\": \"keep me\",\n"
            + "  \"Check List\": {\n    \"Drawing\": true,\n    \"New EnPI\": \"EnPI A\",\n    \"Custom Check\": 1\n  },\n  \"Deleted\": false\n}";

    /* organisation 1
         action plan 10 > directory 11 > actions 12 (No 1), 13 (No 7, deleted JSON), 14 (No 9, not readable)
                                         action 12 > files 40, 42 (not deletable); directory 11 > file 41
         nonconformity plan 20 > directory 21 > nonconformity 22 (Nr 3)
         nonconformity plan 25 (no directory)
         legal plan 30 > directory 31 > obligation 32 (Nr 4)
         action plan 50 (not readable) */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(10, "Plan A", ActionItem.PLAN_CLASS_NAME, 1),
                obj(11, "Action Directory", "Action Directory", 10),
                obj(12, "AP 001 - First action", ActionItem.CLASS_NAME, 11),
                obj(13, "7", ActionItem.CLASS_NAME, 11),
                obj(14, "9", ActionItem.CLASS_NAME, 11),
                obj(40, "report.pdf", ResourceWebIso.CLASS_FILE, 12),
                obj(42, "photo.png", ResourceWebIso.CLASS_FILE, 12),
                obj(41, "other.pdf", ResourceWebIso.CLASS_FILE, 11),
                obj(20, "NC Plan", NonconformityItem.PLAN_CLASS_NAME, 1),
                obj(21, "Nonconformities Directory", "Nonconformities Directory", 20),
                obj(22, "NC3", NonconformityItem.CLASS_NAME, 21),
                obj(25, "NC Plan 2", NonconformityItem.PLAN_CLASS_NAME, 1),
                obj(30, "Legal", Obligation.PLAN_CLASS_NAME, 1),
                obj(31, "Obligation Directory", "Obligation Directory", 30),
                obj(32, "4", Obligation.CLASS_NAME, 31),
                obj(50, "Hidden plan", ActionItem.PLAN_CLASS_NAME, 1));
        store.hidden.add(14L);
        store.hidden.add(50L);
        store.type(ResourceWebIso.CLASS_FILE, null, ResourceWebIso.ATTRIBUTE_FILE, JEVisConstants.PrimitiveType.FILE);
        store.sample(10, ActionItem.PLAN_ATTRIBUTE_PREFIX, EARLIER, "AP", null)
                .sample(10, ActionItem.PLAN_ATTRIBUTE_STATUS, EARLIER, "Open;Check", null)
                .sample(10, ActionItem.PLAN_ATTRIBUTE_FIELDS, EARLIER, "Field A;Field B", null)
                .sample(10, ActionItem.PLAN_ATTRIBUTE_MEDIUM, EARLIER, "[{\"id\":\"m1\",\"name\":\"Medium A\",\"co2\":0.3},{\"id\":\"m2\",\"name\":\"Medium B\",\"co2\":0.2}]", null)
                .sample(10, ActionItem.PLAN_ATTRIBUTE_SEU, EARLIER, "SEU A;SEU B", null)
                .sample(20, NonconformityItem.PLAN_ATTRIBUTE_PREFIX, EARLIER, "NC", null)
                .sample(20, NonconformityItem.PLAN_ATTRIBUTE_MEDIUM, EARLIER, "Medium A;Medium B", null)
                .sample(20, NonconformityItem.PLAN_ATTRIBUTE_FIELDS, EARLIER, "Field A", null)
                .sample(20, NonconformityItem.PLAN_ATTRIBUTE_SEU, EARLIER, "SEU A", null)
                .sample(25, NonconformityItem.PLAN_ATTRIBUTE_PREFIX, EARLIER, "NCB-", null)
                .sample(30, Obligation.PLAN_ATTRIBUTE_CATEGORY, EARLIER, "Category A;Category B", null)
                .sample(30, Obligation.PLAN_ATTRIBUTE_SCOPE, EARLIER, "Scope A", null);
        store.file(12, DATA, EARLIER, "DataModel_v2_202611010800.json", ACTION_12)
                .file(13, DATA, EARLIER, "DataModel_v2_202611010800.json", "{\"No\": 7, \"Title\": \"Gone\", \"Deleted\": true}")
                .file(14, DATA, EARLIER, "DataModel_v2_202611010800.json", "{\"No\": 9, \"Title\": \"Secret\"}")
                .file(22, DATA, EARLIER, "DataModel_v2_202611010800.json", "{\"Nr\": 3, \"Title\": \"Leak\", "
                        + "\"Field Tags\": [\"Field A\"], \"Planned Date\": null, \"Done Date\": null, \"Action\": \"\"}")
                .file(32, DATA, EARLIER, "DataModel_v2_202611010800.json", "{\"uuid\": \"u-1\", \"Nr\": 4, "
                        + "\"title\": \"Rule\", \"category\": \"Category A\", \"issue date\": null}")
                .file(40, ResourceWebIso.ATTRIBUTE_FILE, EARLIER, "report.pdf", "%PDF")
                .file(42, ResourceWebIso.ATTRIBUTE_FILE, EARLIER, "photo.png", "PNG");
        return store;
    }

    private static String version(FakeEditStore store, long id) {
        return WebEdit.version(store.latest(id, DATA));
    }

    private static ObjectNode stored(FakeEditStore store, long id) {
        return IsoJsonTree.parse(store.latestFile(id, DATA));
    }

    private static WebEdit.Reply update(FakeEditStore store, ResourceWebEditIso.Kind kind, long id, String fields)
            throws Exception {
        String body = "{\"version\":\"" + version(store, id) + "\",\"fields\":" + fields + "}";
        return WebEdit.call(store, s -> ResourceWebEditIso.update(s, kind, id, body, "en", NOW));
    }

    // ---------------------------------------------------------------- create

    @Test
    public void createActionCountsDeletedAndUnreadableItemsForTheNumber() throws Exception {
        FakeEditStore store = store();
        store.create.add(11L);
        store.write.add(11L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10,
                "{\"Title\":\"New action\",\"Field Tags\":[\"Field B\"],\"SEU Tags\":[\"SEU A\",\"SEU B\"]}", "en", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(10, json.get("no").asInt());
        assertEquals("AP 010", json.get("number").asText());
        assertEquals("Demo User", json.get("fromUser").asText());
        assertTrue(json.get("canWrite").asBoolean());
        assertFalse(json.get("canDelete").asBoolean());
        assertNotNull(json.get("version").asText(null));
        /* nothing of the unreadable item is returned */
        assertFalse(reply.body.toString().contains("Secret"));

        long id = json.get("id").asLong();
        JsonObject created = store.object(id);
        assertEquals(11L, (long) created.getParent());
        assertEquals(ActionItem.CLASS_NAME, created.getJevisClass());
        assertTrue(store.renames.contains(id + " AP 010 - New action"));
        ObjectNode tree = stored(store, id);
        assertEquals(10, tree.get("No").asInt());
        assertEquals(TODAY, tree.get("Create Date").asText());
        assertEquals("Demo User", tree.get("From User").asText());
        assertEquals("Open", tree.get("Status Tags").asText());
        assertEquals("Field B", tree.get("Field Tags").asText());
        assertEquals("SEU A;SEU B", tree.get("SEU Tags").asText());
        assertFalse(tree.get("Deleted").asBoolean());
        /* actions omit empty dates */
        assertFalse(tree.has("Planned Date"));
        assertFalse(tree.has("Done Date"));
        assertTrue(store.writes.get(0).startsWith(id + "/Data 3 DataModel_v2_202611121030.json"));
        assertTrue(store.logs.contains("CREATE_OBJECT " + id + ":10|web:action-create"));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + id + ":Data|1|web:action-create"));
        assertTrue(store.logs.contains("UPDATE_OBJECT " + id + ":AP 010 - New action|web:action-create"));
    }

    @Test
    public void createNeedsCreateRightAndAReadablePlan() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10, "{}", "en", NOW));
        assertEquals(403, reply.status);
        /* create right on the plan is not enough if the directory exists */
        store.create.add(10L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10, "{}", "en", NOW)).status);
        store.create.add(50L);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 50, "{}", "en", NOW)).status);
        /* plan of another kind */
        store.create.add(20L);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 20, "{}", "en", NOW)).status);
        assertTrue(store.writes.isEmpty());
        assertTrue(store.logs.isEmpty());
    }

    @Test
    public void createValidatesBeforeCreatingAnything() throws Exception {
        FakeEditStore store = store();
        store.create.add(11L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10,
                "{\"Status Tags\":\"Unknown status\"}", "en", NOW));
        assertEquals(400, reply.status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10,
                "{\"No\":3}", "en", NOW)).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditIso.create(s, ACTIONS, 10, "[]", "en", NOW)).status);
        assertEquals(17, store.objects.size());
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void createNonconformityCreatesTheDirectory() throws Exception {
        FakeEditStore store = store();
        store.create.add(25L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, NONCONFORMITIES, 25,
                "{\"Title\":\"Leak\",\"Planned Date\":\"2026-12-01\"}", "en", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(1, json.get("nr").asInt());
        long id = json.get("id").asLong();
        JsonObject item = store.object(id);
        JsonObject directory = store.object(item.getParent());
        assertEquals("Nonconformities Directory", directory.getJevisClass());
        assertEquals("Nonconformities Directory", directory.getName());
        assertEquals(25L, (long) directory.getParent());
        assertTrue(store.renames.contains(id + " NCB-1"));
        ObjectNode tree = stored(store, id);
        assertEquals("Demo User", tree.get("Creator").asText());
        assertEquals("2026-12-01T00:00:00.000+01:00", tree.get("Planned Date").asText());
        /* nonconformities keep null for empty dates */
        assertTrue(tree.get("Done Date").isNull());
        assertTrue(tree.get("Field Tags").isArray());
        assertTrue(store.logs.contains("CREATE_OBJECT " + directory.getId() + ":Nonconformities Directory|web:nonconformity-create"));
    }

    @Test
    public void createWithTheAccountNameIfTheUserHasNoName() throws Exception {
        FakeEditStore store = store();
        store.create.add(21L);
        store.userName = FakeEditStore.ACCOUNT;
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, NONCONFORMITIES, 20, "{\"Title\":\"Leak\"}", "en", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(4, json.get("nr").asInt());
        assertEquals(FakeEditStore.ACCOUNT, json.get("creator").asText());
    }

    @Test
    public void failedCreateRemovesTheNewObjects() throws Exception {
        FakeEditStore store = store();
        store.create.add(25L);
        store.failFileWrites = true;
        try {
            WebEdit.call(store, s -> ResourceWebEditIso.create(s, NONCONFORMITIES, 25, "{\"Title\":\"Leak\"}", "en", NOW));
            fail("the failure is passed on");
        } catch (java.io.IOException expected) {
            /* the request answers 500 */
        }
        assertEquals(2, store.deletedObjects.size());
        assertEquals(17, store.objects.size());
        assertTrue(store.logs.get(store.logs.size() - 1).startsWith("DELETE_OBJECT "));
    }

    @Test
    public void createObligationWithUuid() throws Exception {
        FakeEditStore store = store();
        store.create.add(31L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, OBLIGATIONS, 30,
                "{\"title\":\"Rule B\",\"category\":\"Category B\",\"Relevance 50001\":true}", "en", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals(5, json.get("nr").asInt());
        assertTrue(json.get("relevant").asBoolean());
        long id = json.get("id").asLong();
        /* created with the number as name: no rename needed */
        assertTrue(store.renames.isEmpty());
        assertEquals("5", store.object(id).getName());
        ObjectNode tree = stored(store, id);
        assertEquals(36, tree.get("uuid").asText().length());
        assertTrue(tree.get("issue date").isNull());
        assertFalse(tree.has("Create Date"));
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditIso.create(s, OBLIGATIONS, 30,
                "{\"scope\":\"Scope B\"}", "en", NOW)).status);
    }

    // ---------------------------------------------------------------- update

    @Test
    public void updateChangesOnlyTheSentKeys() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        WebEdit.Reply reply = update(store, ACTIONS, 12, "{\"Title\":\"Changed\",\"Description\":\"New text\","
                + "\"Note\":null,\"Check List\":{\"Target Reached\":true}}");
        assertEquals(200, reply.status);
        String raw = store.latestFile(12, DATA);
        /* NaN stays unquoted, unknown keys, legacy keys and unchanged lists stay */
        assertTrue(raw, raw.contains("NaN") && !raw.contains("\"NaN\""));
        ObjectNode tree = stored(store, 12);
        assertEquals("keep me", tree.get("Unknown Key").asText());
        assertEquals("New text", tree.get("Desciption").asText());
        assertFalse(tree.has("Description"));
        assertEquals("", tree.get("Note").asText());
        assertEquals("Retired status", tree.get("Status Tags").asText());
        assertEquals("Field A;Retired field", tree.get("Field Tags").asText());
        assertEquals(2, tree.get("NPV").get("Year Data").size());
        assertTrue(Double.isNaN(tree.get("NPV").get("Result").doubleValue()));
        assertEquals(1, tree.get("No").asInt());
        assertFalse(tree.has("Done Date"));
        JsonNode checks = tree.get("Check List");
        assertTrue(checks.get("Drawing").asBoolean());
        assertTrue(checks.get("Target Reached").asBoolean());
        assertEquals(1, checks.get("Custom Check").asInt());
        assertEquals("EnPI A", checks.get("New EnPI").asText());
        assertTrue(store.renames.contains("12 AP 001 - Changed"));
        assertTrue(store.logs.contains("CREATE_SAMPLE 12:Data|1|web:action-update"));

        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Changed", json.get("title").asText());
        assertEquals("New text", json.get("description").asText());
        assertEquals(version(store, 12), json.get("version").asText());
    }

    @Test
    public void listValuesMustBeInThePlanUnlessUnchanged() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Status Tags\":\"Other\"}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Field Tags\":[\"Field C\"]}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Field Tags\":\"Field A\"}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Medium Tags\":\"Medium A\"}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Check List\":{\"Unknown\":true}}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Check List\":{\"Drawing\":\"yes\"}}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"No\":4}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Planned Date\":\"tomorrow\"}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Title\":5}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Planned Date\":\"2026-11-20T00:00:00.000+01:00\"}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Check List\":{\"New EnPI\":\"x\"}}").status);
        assertEquals(400, update(store, ACTIONS, 12, "{\"Title\":\" \"}").status);
        WebEdit.Reply several = update(store, ACTIONS, 12, "{\"Status Tags\":\"Other\",\"Title\":\"\",\"No\":4}");
        assertEquals(400, several.status);
        assertEquals(3, FakeEditStore.json(several).get("messages").size());
        assertTrue(store.writes.isEmpty());
        /* the stored old values are accepted when sent unchanged */
        assertEquals(200, update(store, ACTIONS, 12, "{\"Status Tags\":\"Retired status\",\"Field Tags\":[\"Retired field\",\"Field B\"],"
                + "\"Medium Tags\":\"m2\"}").status);
        ObjectNode tree = stored(store, 12);
        assertEquals("Retired field;Field B", tree.get("Field Tags").asText());
        assertEquals("m2", tree.get("Medium Tags").asText());
    }

    @Test
    public void doneStatusSetsTheDoneDate() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        assertEquals(200, update(store, ACTIONS, 12, "{\"Status Tags\":\"Erledigt\"}").status);
        assertEquals(TODAY, stored(store, 12).get("Done Date").asText());

        /* a given done date is kept, an empty one is removed (actions omit empty keys) */
        FakeEditStore other = store();
        other.write.add(12L);
        assertEquals(200, update(other, ACTIONS, 12, "{\"Status Tags\":\"Done\",\"Done Date\":\"2026-11-02\"}").status);
        assertEquals("2026-11-02T00:00:00.000+01:00", stored(other, 12).get("Done Date").asText());
        other.waits.clear();
        assertEquals(200, update(other, ACTIONS, 12, "{\"Done Date\":null}").status);
        assertFalse(stored(other, 12).has("Done Date"));
        /* status not done: no done date */
        assertEquals(200, update(other, ACTIONS, 12, "{\"Status Tags\":\"Check\"}").status);
        assertFalse(stored(other, 12).has("Done Date"));
    }

    @Test
    public void versionIsRequiredAndChecked() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        WebEdit.Reply missing = WebEdit.call(store, s -> ResourceWebEditIso.update(s, ACTIONS, 12,
                "{\"fields\":{\"Title\":\"x\"}}", "en", NOW));
        assertEquals(400, missing.status);
        assertEquals("version missing", missing.body);
        WebEdit.Reply invalid = WebEdit.call(store, s -> ResourceWebEditIso.update(s, ACTIONS, 12,
                "{\"version\":\"yesterday\",\"fields\":{\"Title\":\"x\"}}", "en", NOW));
        assertEquals(400, invalid.status);
        WebEdit.Reply stale = WebEdit.call(store, s -> ResourceWebEditIso.update(s, ACTIONS, 12,
                "{\"version\":\"2026-10-01T00:00:00.000+02:00\",\"fields\":{\"Title\":\"x\"}}", "en", NOW));
        assertEquals(409, stale.status);
        assertEquals("First action", FakeEditStore.json(stale).get("title").asText());
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void updateRightsAndVisibility() throws Exception {
        FakeEditStore store = store();
        assertEquals(403, update(store, ACTIONS, 12, "{\"Title\":\"x\"}").status);
        store.write.addAll(Arrays.asList(13L, 14L, 22L));
        /* deleted JSON, not readable, other kind */
        assertEquals(404, update(store, ACTIONS, 13, "{\"Title\":\"x\"}").status);
        assertEquals(404, update(store, ACTIONS, 14, "{\"Title\":\"x\"}").status);
        assertEquals(404, update(store, ACTIONS, 22, "{\"Title\":\"x\"}").status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void nonconformityRequirementsAreChecked() throws Exception {
        FakeEditStore store = store();
        store.write.add(22L);
        WebEdit.Reply reply = update(store, NONCONFORMITIES, 22, "{\"Done Date\":\"2026-11-10\"}");
        assertEquals(400, reply.status);
        assertTrue(FakeEditStore.json(reply).get("messages").get(0).asText().startsWith("Action cant be empty"));
        WebEdit.Reply both = update(store, NONCONFORMITIES, 22, "{\"Check List\":{\"Immediate Action Required\":true},"
                + "\"Done Date\":\"2026-11-20\",\"Action\":\"\"}");
        assertEquals(3, FakeEditStore.json(both).get("messages").size());
        assertTrue(store.writes.isEmpty());

        WebEdit.Reply ok = update(store, NONCONFORMITIES, 22, "{\"Done Date\":\"2026-11-10\",\"Action\":\"Repaired\","
                + "\"Medium\":\"Medium B\",\"Field Tags\":[],\"Planned Date\":null}");
        assertEquals(200, ok.status);
        ObjectNode tree = stored(store, 22);
        assertEquals("2026-11-10T00:00:00.000+01:00", tree.get("Done Date").asText());
        assertTrue(tree.get("Planned Date").isNull());
        assertEquals(0, tree.get("Field Tags").size());
        assertTrue(store.renames.contains("22 NC3") || "NC3".equals(store.object(22).getName()));
        assertEquals(400, update(store, NONCONFORMITIES, 22, "{\"SEU\":\"SEU X\"}").status);
    }

    @Test
    public void obligationIsRenamedToItsNumber() throws Exception {
        FakeEditStore store = store();
        store.write.add(32L);
        store.object(32).setName("old name");
        assertEquals(200, update(store, OBLIGATIONS, 32, "{\"link\":\"https://example.org\",\"Relevance 50001\":true}").status);
        assertTrue(store.renames.contains("32 4"));
        ObjectNode tree = stored(store, 32);
        assertEquals("u-1", tree.get("uuid").asText());
        assertTrue(tree.get("Relevance 50001").asBoolean());
        assertEquals(400, update(store, OBLIGATIONS, 32, "{\"Relevance 50001\":\"yes\"}").status);
    }

    @Test
    public void saveInTheSameSecondWaitsForTheNextSecond() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        assertEquals(200, update(store, ACTIONS, 12, "{\"Title\":\"One\"}").status);
        assertTrue(store.waits.isEmpty());
        assertEquals(200, update(store, ACTIONS, 12, "{\"Title\":\"Two\"}").status);
        assertEquals(Collections.singletonList(NOW.plusSeconds(1)), store.waits);
        assertEquals(3, store.count(12, DATA));
        assertEquals("Two", stored(store, 12).get("Title").asText());
    }

    // ---------------------------------------------------------------- delete

    @Test
    public void deleteMarksTheItemDeleted() throws Exception {
        FakeEditStore store = store();
        store.write.add(12L);
        String version = version(store, 12);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditIso.delete(s, ACTIONS, 12, version, NOW)).status);
        store.delete.add(12L);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditIso.delete(s, ACTIONS, 12, null, NOW)).status);
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditIso.delete(s, ACTIONS, 12,
                "2026-10-01T00:00:00.000+02:00", NOW)).status);
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditIso.delete(s, ACTIONS, 12, version, NOW)).status);
        ObjectNode tree = stored(store, 12);
        assertTrue(tree.get("Deleted").asBoolean());
        assertEquals("keep me", tree.get("Unknown Key").asText());
        assertTrue(store.logs.contains("CREATE_SAMPLE 12:Data|1|web:action-delete"));
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.delete(s, ACTIONS, 12,
                version(store, 12), NOW)).status);
    }

    // ---------------------------------------------------------------- attachments

    private static WebEdit.Reply upload(FakeEditStore store, long id, String name, byte[] content, int max) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditIso.attach(s, id, name, new ByteArrayInputStream(content), max, NOW));
    }

    @Test
    public void uploadCreatesAFileChild() throws Exception {
        FakeEditStore store = store();
        store.create.add(12L);
        WebEdit.Reply reply = upload(store, 12, "C:\\Users\\someone\\offer 2026.pdf", "%PDF-1".getBytes(StandardCharsets.UTF_8), 100);
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("offer 2026.pdf", json.get("name").asText());
        assertEquals(6, json.get("size").asLong());
        /* the new file gets the owners of the item */
        assertFalse(json.get("canDelete").asBoolean());
        JsonObject file = store.object(json.get("id").asLong());
        assertEquals(ResourceWebIso.CLASS_FILE, file.getJevisClass());
        assertEquals("offer 2026.pdf", file.getName());
        assertEquals(12L, (long) file.getParent());
        assertEquals("offer 2026.pdf", store.latest(file.getId(), ResourceWebIso.ATTRIBUTE_FILE).getValue());
        assertEquals("%PDF-1", store.latestFile(file.getId(), ResourceWebIso.ATTRIBUTE_FILE));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + file.getId() + ":File|1|web:attachment-add"));
    }

    @Test
    public void uploadChecksSizeRightsAndParent() throws Exception {
        FakeEditStore store = store();
        byte[] content = new byte[11];
        assertEquals(403, upload(store, 12, "a.txt", content, 100).status);
        store.create.addAll(Arrays.asList(12L, 13L, 11L, 22L));
        assertEquals(413, upload(store, 12, "a.txt", content, 10).status);
        /* deleted JSON, not an item */
        assertEquals(404, upload(store, 13, "a.txt", content, 100).status);
        assertEquals(404, upload(store, 11, "a.txt", content, 100).status);
        assertEquals(17, store.objects.size());
        assertEquals(201, upload(store, 22, "a.txt", content, 100).status);
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditIso.attach(s, 12, "a.txt", null, 100, NOW)).status);
    }

    @Test
    public void headerFileNamesAreReadAsUtf8() {
        String utf8AsLatin1 = new String("Prüfbericht.txt".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertEquals("Prüfbericht.txt", ResourceWebEditIso.headerFileName(utf8AsLatin1));
        assertEquals("Prüfbericht.txt", ResourceWebEditIso.headerFileName("Prüfbericht.txt"));
        assertEquals("plain.pdf", ResourceWebEditIso.headerFileName("plain.pdf"));
        assertEquals("€.txt", ResourceWebEditIso.headerFileName("€.txt"));
        assertEquals(null, ResourceWebEditIso.headerFileName(null));
    }

    @Test
    public void fileNamesAreSanitised() {
        assertEquals("evil.sh", ResourceWebEditIso.uploadName("../../evil.sh"));
        assertEquals("a_b_.txt", ResourceWebEditIso.uploadName("a\u0000b\u202e.txt"));
        assertEquals("x__y.pdf", ResourceWebEditIso.uploadName("x<>y.pdf"));
        assertEquals("hidden", ResourceWebEditIso.uploadName("..hidden"));
        assertEquals("file", ResourceWebEditIso.uploadName(null));
        assertEquals("file", ResourceWebEditIso.uploadName("C:\\temp\\"));
        String longName = ResourceWebEditIso.uploadName(String.join("", Collections.nCopies(300, "a")) + ".pdf");
        assertEquals(ResourceWebEditIso.MAX_FILE_NAME_LENGTH, longName.length());
        assertTrue(longName.endsWith(".pdf"));
    }

    @Test
    public void deleteAttachmentMovesItToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        store.delete.addAll(Arrays.asList(40L, 41L));
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditIso.detach(s, 40)).status);
        assertEquals(Collections.singletonList(40L), store.recycled);
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT 40:report.pdf|web:attachment-delete"));
        /* parent is not an item, no delete right, already in the bin, not a file */
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.detach(s, 41)).status);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditIso.detach(s, 42)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.detach(s, 40)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditIso.detach(s, 12)).status);
        assertEquals(1, store.recycled.size());
    }

    // ---------------------------------------------------------------- read answers and names

    @Test
    public void readAnswersCarryTheEditInfo() {
        FakeEditStore store = store();
        store.write.add(12L);
        store.delete.add(12L);
        store.create.addAll(Arrays.asList(11L, 12L, 25L));
        ResourceWebModules.Lookup lookup = store.lookup();
        Map<String, Object> plan = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 12L);
        item.put("title", "First action");
        items.add(item);
        plan.put("actions", items);
        ResourceWebEditIso.addEditInfo(ACTIONS, plan, lookup.readable(10L), lookup, store.rights(), id -> store.latest(id, DATA));
        assertEquals(true, plan.get("canCreate"));
        assertEquals("First action", item.get("title"));
        assertEquals(WebEdit.version(store.latest(12, DATA)), item.get("version"));
        assertEquals(true, item.get("canWrite"));
        assertEquals(true, item.get("canDelete"));
        assertEquals(true, item.get("canAttach"));

        /* no directory: create right on the plan; nonconformity list key "items" */
        Map<String, Object> ncPlan = new LinkedHashMap<>();
        ncPlan.put("items", new ArrayList<>());
        ResourceWebEditIso.addEditInfo(NONCONFORMITIES, ncPlan, lookup.readable(25L), lookup, store.rights(), id -> null);
        assertEquals(true, ncPlan.get("canCreate"));
        Map<String, Object> ncPlan2 = new LinkedHashMap<>();
        ResourceWebEditIso.addEditInfo(NONCONFORMITIES, ncPlan2, lookup.readable(20L), lookup, store.rights(), id -> null);
        assertEquals(false, ncPlan2.get("canCreate"));
    }

    @Test
    public void createAcceptsFieldsObjectAndNeedsATitle() throws Exception {
        FakeEditStore store = store();
        store.create.add(31L);
        WebEdit.Reply missing = WebEdit.call(store, s -> ResourceWebEditIso.create(s, OBLIGATIONS, 30,
                "{\"fields\":{\"Designation\":\"A\"}}", "en", NOW));
        assertEquals(400, missing.status);
        assertEquals("Missing title", FakeEditStore.json(missing).get("messages").get(0).asText());
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditIso.create(s, OBLIGATIONS, 30,
                "{\"fields\":{\"title\":\"Rule C\",\"issue date\":\"2026-01-15\"}}", "en", NOW));
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Rule C", json.get("title").asText());
        assertEquals("2026-01-15T00:00:00.000+01:00", json.get("issueDate").asText());
    }

    @Test
    public void actionPlansAlwaysOfferDoneAndOpen() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("statuses", new ArrayList<>(Arrays.asList("Check")));
        ResourceWebEditIso.addDefaultStatuses("en", plan);
        assertEquals(Arrays.asList("Check", "Done", "Open"), plan.get("statuses"));

        /* a translation in any language counts; missing ones in the language, German without one */
        Map<String, Object> german = new LinkedHashMap<>();
        german.put("statuses", new ArrayList<>(Arrays.asList("offen", "Check")));
        ResourceWebEditIso.addDefaultStatuses(ResourceWebEditIso.statusLanguage(null, null), german);
        assertEquals(Arrays.asList("offen", "Check", "Erledigt"), german.get("statuses"));
        assertEquals("en", ResourceWebEditIso.statusLanguage("en", null));

        Map<String, Object> complete = new LinkedHashMap<>();
        complete.put("statuses", new ArrayList<>(Arrays.asList("Erledigt", "Open")));
        ResourceWebEditIso.addDefaultStatuses("en", complete);
        assertEquals(Arrays.asList("Erledigt", "Open"), complete.get("statuses"));
    }

    @Test
    public void objectNamesAreThoseOfTheControlCenter() {
        ResourceWebEditIso.Plan plan = new ResourceWebEditIso.Plan(obj(10, "Plan", ActionItem.PLAN_CLASS_NAME, 1), ZONE);
        ObjectNode tree = IsoJsonTree.object();
        tree.put("Nr", 7);
        tree.put("Title", String.join("", Collections.nCopies(60, "x")));
        assertEquals(" 007 - " + String.join("", Collections.nCopies(50, "x")) + "..",
                ResourceWebEditIso.objectName(ACTIONS, plan, tree));
        plan.attributes.put(ActionItem.PLAN_ATTRIBUTE_PREFIX, "AP");
        tree.put("No", 12);
        tree.put("Title", "Short");
        assertEquals("AP 012 - Short", ResourceWebEditIso.objectName(ACTIONS, plan, tree));
        plan.attributes.put(NonconformityItem.PLAN_ATTRIBUTE_PREFIX, "NC");
        assertEquals("NC7", ResourceWebEditIso.objectName(NONCONFORMITIES, plan, tree));
        assertEquals("7", ResourceWebEditIso.objectName(OBLIGATIONS, plan, tree));
    }
}
