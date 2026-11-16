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
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonType;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

public class ResourceWebEditConfigurationsTest {

    private static final DateTimeZone ZONE = FakeEditStore.ZONE;
    private static final DateTime NOW = new DateTime(2026, 11, 16, 10, 0, 0, ZONE);
    private static final DateTime T_FILE = new DateTime(2026, 4, 1, 9, 30, 0, ZONE);
    private static final String FILE = "Template File";
    private static final String CONFIGURATION = ResourceWebContracts.CLASS_CONFIGURATION;
    private static final String TEMPLATE = ResourceWebCosts.CLASS_TEMPLATE;
    private static final String DIRECTORY = ResourceWebEditConfigurations.CLASS_DIRECTORY;
    private static final String SUPPLIER = "Electricity Supply Contractor";
    private static final String GRID = "Electricity Grid Operator";
    private static final String DUES = "Governmental Dues";
    private static final String CLEAN = ResourceWebCosts.CLASS_CLEAN_DATA;

    private static final String KEY_PEAK = "5b0e7c1a-1d2f-4c3b-9a41-0d6e2f7a1b03";
    private static final String KEY_CONSUMPTION = "5b0e7c1a-1d2f-4c3b-9a41-0d6e2f7a1b02";
    private static final String KEY_GRID = "class:" + GRID;
    private static final String KEY_SUPPLIER = "class:" + SUPPLIER;
    private static final String KEY_DUES = "class:" + DUES;
    private static final String GAS_INPUT = "9c2d4e6f-0a1b-4c3d-8e5f-6a7b8c9d0e01";

    private static final String OTHER_TEMPLATE = "{\"templateInputs\":[{\"objectClass\":\"Clean Data\",\"id\":\""
            + GAS_INPUT + "\",\"quantity\":true,\"attributeName\":\"Value\",\"variableName\":\"Verbrauch\","
            + "\"variableType\":\"SUM\",\"filter\":null,\"group\":false}],\"templateOutputs\":[],\"templateFormulas\":[],"
            + "\"intervalSelectorConfiguration\":{\"P1M\":true},\"type\":\"SimpleDataHandler\"}";

    // ---------------------------------------------------------------- setup

    static String resource(String name) throws Exception {
        try (InputStream in = ResourceWebEditConfigurationsTest.class.getResourceAsStream(name)) {
            assertNotNull(name, in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void jclass(FakeEditStore store, String name, String inherits, JsonType... types) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(name);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) {
            JsonClassRelationship rel = new JsonClassRelationship();
            rel.setStart(name);
            rel.setEnd(inherits);
            rel.setType(JEVisConstants.ClassRelationship.INHERIT);
            relationships.add(rel);
        }
        jclass.setRelationships(relationships);
        jclass.setTypes(new ArrayList<>(Arrays.asList(types)));
        store.classMap.put(name, jclass);
    }

    private static JsonType fileType(String cls) {
        JsonType type = new JsonType();
        type.setJevisclass(cls);
        type.setName(FILE);
        type.setPrimitiveType(JEVisConstants.PrimitiveType.FILE);
        type.setGuiType("File Selector");
        return type;
    }

    /* Organisation 1 > site 2 "Werk Nord" > 3 "Energieverträge" > accounting directory 4 (configurations 50 with the
       fixture, hidden 51, 52 without file), template directory 5 (templates 60 "Stromkosten", 61 "Gaskosten", hidden
       62, recycle bin 63), supply directory 6 (suppliers 10, 11, hidden 12), grid directory 7 (20), dues directory 8
       (30); 2 > 9 "Daten" > data 40 "Zähler Bezug" > clean 41, data 42 "Zähler Einspeisung" > clean 43, data 44
       "Zähler Bezug Halle" > hidden clean 45 (bound to "Spitze_Viertelstunde"); second accounting directory 13 without
       create right */
    private static FakeEditStore store() throws Exception {
        FakeEditStore store = new FakeEditStore();
        jclass(store, ResourceWebNavigation.CLASS_BUILDING, null);
        jclass(store, ResourceWebNavigation.CLASS_ORGANIZATION, null);
        jclass(store, DIRECTORY, null);
        jclass(store, CONFIGURATION, null, fileType(CONFIGURATION));
        jclass(store, TEMPLATE, null, fileType(TEMPLATE));
        jclass(store, ResourceWebContracts.CLASS_SUPPLIER, null);
        jclass(store, SUPPLIER, ResourceWebContracts.CLASS_SUPPLIER);
        jclass(store, ResourceWebContracts.CLASS_GRID_OPERATOR, null);
        jclass(store, GRID, ResourceWebContracts.CLASS_GRID_OPERATOR);
        jclass(store, DUES, null);
        jclass(store, "Data", null);
        jclass(store, CLEAN, null);

        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Werk Nord", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Energieverträge", "Energy Contracting Directory", 2),
                obj(4, "Abrechnung", DIRECTORY, 3),
                obj(5, "Vorlagen", "Result Calculation Template Directory", 3),
                obj(6, "Lieferung", "Energy Supply Directory", 3),
                obj(7, "Netz", "Energy Grid Operation Directory", 3),
                obj(8, "Abgaben", "Energy Governmental Dues Directory", 3),
                obj(9, "Daten", "Data Directory", 2),
                obj(10, "Strom Nord", SUPPLIER, 6),
                obj(11, "Strom Süd", SUPPLIER, 6),
                obj(12, "Strom Verborgen", SUPPLIER, 6),
                obj(13, "Abrechnung Archiv", DIRECTORY, 3),
                obj(20, "Netz Nord", GRID, 7),
                obj(30, "Abgaben 2026", DUES, 8),
                obj(40, "Zähler Bezug", "Data", 9),
                obj(41, "Bereinigt", CLEAN, 40),
                obj(42, "Zähler Einspeisung", "Data", 9),
                obj(43, "Bereinigt", CLEAN, 42),
                obj(44, "Zähler Bezug Halle", "Data", 9),
                obj(45, "Bereinigt", CLEAN, 44),
                obj(50, "Strom Werk Nord", CONFIGURATION, 4),
                obj(51, "Verborgen", CONFIGURATION, 4),
                obj(52, "Neu ohne Datei", CONFIGURATION, 4),
                obj(60, "Stromkosten", TEMPLATE, 5),
                obj(61, "Gaskosten", TEMPLATE, 5),
                obj(62, "Geheim", TEMPLATE, 5),
                obj(63, "Alt", TEMPLATE, 5));
        store.object(63).setDeleteTS("2026-11-01T00:00:00.000+01:00");
        store.hidden.addAll(Arrays.asList(12L, 45L, 51L, 62L));
        store.write.addAll(Arrays.asList(50L, 52L));
        store.delete.addAll(Arrays.asList(50L));
        store.create.addAll(Arrays.asList(4L));

        store.file(50, FILE, T_FILE, "StromWerkNord_202604010930.json", resource("e63-configuration.json"));
        store.file(51, FILE, T_FILE, "Verborgen.json", resource("e63-configuration.json"));
        store.file(60, FILE, T_FILE, "Stromkosten.json", resource("e63-template.json"));
        store.file(61, FILE, T_FILE, "Gaskosten.json", OTHER_TEMPLATE);
        store.file(62, FILE, T_FILE, "Geheim.json", OTHER_TEMPLATE);
        return store;
    }

    private static JsonNode call(FakeEditStore store, WebEdit.Action action) throws Exception {
        return FakeEditStore.json(WebEdit.call(store, action));
    }

    private static JsonNode edit(FakeEditStore store, long id) throws Exception {
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditConfigurations.edit(s, id, "de"));
        assertEquals(200, reply.status);
        return FakeEditStore.json(reply);
    }

    private static JsonNode input(JsonNode inputs, String key) {
        for (JsonNode input : inputs) {
            if (key.equals(input.get("key").asText())) return input;
        }
        return null;
    }

    private static List<Long> optionIds(JsonNode input) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode option : input.get("options")) ids.add(option.get("id").asLong());
        return ids;
    }

    private static ObjectNode stored(FakeEditStore store, long id) throws Exception {
        return (ObjectNode) FakeEditStore.MAPPER.readTree(store.latestFile(id, FILE));
    }

    private static JsonNode fixture() throws Exception {
        return FakeEditStore.MAPPER.readTree(resource("e63-configuration.json"));
    }

    /**
     * @return a PUT body that sends back what the editor model shows
     */
    private static ObjectNode unchangedBody(JsonNode model) {
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", model.get("version").asText());
        body.set("contract", model.get("contract").deepCopy());
        body.put("templateId", model.get("template").get("id").asLong());
        ObjectNode selections = body.putObject("selections");
        for (JsonNode input : model.get("inputs")) selections.set(input.get("key").asText(), input.get("selected"));
        return body;
    }

    private static WebEdit.Reply put(FakeEditStore store, long id, JsonNode body) throws Exception {
        String text = FakeEditStore.MAPPER.writeValueAsString(body);
        return WebEdit.call(store, s -> ResourceWebEditConfigurations.update(s, id, text, "de", NOW));
    }

    private static WebEdit.Reply post(FakeEditStore store, String body) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditConfigurations.create(s, body, "de", NOW));
    }

    @SuppressWarnings("unchecked")
    private static List<String> messages(WebEdit.Reply reply) {
        assertEquals(400, reply.status);
        return (List<String>) ((Map<String, Object>) reply.body).get("messages");
    }

    // ---------------------------------------------------------------- GET /contracts additions

    @SuppressWarnings("unchecked")
    @Test
    public void contractsListsRightsVersionTemplateAndTargets() throws Exception {
        FakeEditStore store = store();
        Map<String, Object> answer = ResourceWebContracts.contractsJson(store.lookup(), store.classes(),
                new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes()),
                ResourceWebEditInvoices.contractsStore(store), store.rights());
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) answer.get("configurations")) byId.put((Long) c.get("id"), c);
        assertFalse(byId.containsKey(51L));
        Map<String, Object> config = byId.get(50L);
        assertEquals(true, config.get("canWrite"));
        assertEquals(true, config.get("canDelete"));
        assertEquals(WebEdit.iso(T_FILE.getMillis(), DateTimeZone.getDefault()), config.get("version"));
        Map<String, Object> template = (Map<String, Object>) config.get("template");
        assertEquals(60L, template.get("id"));
        assertEquals("Stromkosten", template.get("name"));
        assertEquals(true, template.get("readable"));
        assertEquals("V-2026-0001", config.get("contractNumber"));
        Map<String, Object> empty = byId.get(52L);
        assertEquals("none", empty.get("version"));
        assertNull(empty.get("template"));
        assertEquals(true, empty.get("canWrite"));
        assertEquals(false, empty.get("canDelete"));

        List<Map<String, Object>> targets = (List<Map<String, Object>>) answer.get("configurationTargets");
        assertEquals(1, targets.size());
        assertEquals(4L, targets.get(0).get("id"));
        assertEquals("Abrechnung", targets.get(0).get("name"));
        assertEquals(true, answer.get("canCreateConfiguration"));

        Map<String, Object> none = ResourceWebContracts.contractsJson(store.lookup(), store.classes(),
                new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes()),
                ResourceWebEditInvoices.contractsStore(store));
        assertEquals(false, none.get("canCreateConfiguration"));
        assertTrue(((List<?>) none.get("configurationTargets")).isEmpty());
        Map<String, Object> first = ((List<Map<String, Object>>) none.get("configurations")).get(0);
        assertEquals(false, first.get("canWrite"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void unreadableTemplateIsListedAsNotReadable() throws Exception {
        FakeEditStore store = store();
        store.hidden.add(60L);
        Map<String, Object> answer = ResourceWebContracts.contractsJson(store.lookup(), store.classes(),
                new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes()),
                ResourceWebEditInvoices.contractsStore(store), store.rights());
        for (Map<String, Object> c : (List<Map<String, Object>>) answer.get("configurations")) {
            if (((Long) c.get("id")) != 50L) continue;
            Map<String, Object> template = (Map<String, Object>) c.get("template");
            assertEquals(60L, template.get("id"));
            assertNull(template.get("name"));
            assertEquals(false, template.get("readable"));
        }
    }

    // ---------------------------------------------------------------- editor model

    @Test
    public void editModelReadsTheConfiguration() throws Exception {
        JsonNode model = edit(store(), 50);
        assertEquals(50, model.get("id").asLong());
        assertEquals("Strom Werk Nord", model.get("name").asText());
        assertEquals(WebEdit.iso(T_FILE.getMillis(), DateTimeZone.getDefault()), model.get("version").asText());
        assertEquals(4, model.get("parentId").asLong());
        assertTrue(model.get("canWrite").asBoolean());
        assertTrue(model.get("canDelete").asBoolean());
        JsonNode contract = model.get("contract");
        assertEquals("V-2026-0001", contract.get("contractNumber").asText());
        assertEquals("ELECTRICITY", contract.get("contractType").asText());
        assertEquals("10000000001", contract.get("marketLocation").asText());
        assertEquals("2025-10-15", contract.get("contractDate").asText());
        assertEquals("2026-02-01", contract.get("firstRate").asText());
        assertEquals("2027-09-30", contract.get("periodOfNotice").asText());
        assertEquals("2026-01-01", contract.get("start").asText());
        assertEquals("2027-12-31", contract.get("end").asText());
        assertEquals(60, model.get("template").get("id").asLong());
        assertEquals("Stromkosten", model.get("template").get("name").asText());
        assertEquals(1, model.get("unknownKeys").size());
        assertEquals("templateKey", model.get("unknownKeys").get(0).asText());
        assertEquals(0, model.get("warnings").size());

        /* as OutputView: the ungrouped inputs by variable name, then one selector per class */
        List<String> keys = new ArrayList<>();
        for (JsonNode input : model.get("inputs")) keys.add(input.get("key").asText());
        assertEquals(Arrays.asList(KEY_PEAK, KEY_CONSUMPTION, KEY_GRID, KEY_SUPPLIER, KEY_DUES), keys);

        JsonNode supplier = input(model.get("inputs"), KEY_SUPPLIER);
        assertEquals("Name_Lieferant, ENERGY_Preis", supplier.get("label").asText());
        assertEquals(2, supplier.get("variableNames").size());
        assertEquals(SUPPLIER, supplier.get("objectClass").asText());
        assertTrue(supplier.get("filter").isNull());
        assertEquals(10, supplier.get("selected").asLong());
        assertEquals(Arrays.asList(10L, 11L), optionIds(supplier));
        assertEquals("Strom Nord", supplier.get("options").get(0).get("name").asText());
        assertEquals("Energieverträge / Lieferung", supplier.get("options").get(0).get("path").asText());
        assertTrue(supplier.get("options").get(0).get("readable").asBoolean());

        JsonNode consumption = input(model.get("inputs"), KEY_CONSUMPTION);
        assertEquals("Bezug", consumption.get("filter").asText());
        assertEquals(41, consumption.get("selected").asLong());
        /* the filter keeps "Zähler Bezug \ Bereinigt" only */
        assertEquals(Collections.singletonList(41L), optionIds(consumption));
        assertEquals("Zähler Bezug \\ Bereinigt", consumption.get("options").get(0).get("name").asText());
    }

    @Test
    public void unreadableSelectedObjectIsKeptInTheOptions() throws Exception {
        JsonNode peak = input(edit(store(), 50).get("inputs"), KEY_PEAK);
        assertEquals(45, peak.get("selected").asLong());
        JsonNode first = peak.get("options").get(0);
        assertEquals(45, first.get("id").asLong());
        assertTrue(first.get("name").isNull());
        assertFalse(first.get("readable").asBoolean());
        assertEquals(Arrays.asList(45L, 41L), optionIds(peak));
    }

    @Test
    public void editModelWithoutFileAndRights() throws Exception {
        FakeEditStore store = store();
        JsonNode model = edit(store, 52);
        assertEquals("none", model.get("version").asText());
        assertTrue(model.get("template").isNull());
        assertEquals(0, model.get("inputs").size());
        assertTrue(model.get("contract").get("contractNumber").isNull());
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.edit(s, 51, "de")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.edit(s, 60, "de")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.edit(s, 999, "de")).status);
    }

    // ---------------------------------------------------------------- templates and inputs

    @Test
    public void templatesListsReadableTemplates() throws Exception {
        JsonNode list = call(store(), ResourceWebEditConfigurations::templates);
        List<Long> ids = new ArrayList<>();
        for (JsonNode t : list) ids.add(t.get("id").asLong());
        assertEquals(Arrays.asList(61L, 60L), ids);
        assertEquals("Gaskosten", list.get(0).get("name").asText());
        assertEquals("Energieverträge / Vorlagen", list.get(0).get("path").asText());
        assertEquals(2, list.get(0).get("site").get("id").asLong());
    }

    @Test
    public void inputsOfATemplate() throws Exception {
        FakeEditStore store = store();
        JsonNode withConfiguration = call(store, s -> ResourceWebEditConfigurations.inputs(s, 60, 50L));
        assertEquals(60, withConfiguration.get("templateId").asLong());
        assertEquals(10, input(withConfiguration.get("inputs"), KEY_SUPPLIER).get("selected").asLong());
        assertEquals(45, input(withConfiguration.get("inputs"), KEY_PEAK).get("selected").asLong());
        assertEquals(30, input(withConfiguration.get("inputs"), KEY_DUES).get("selected").asLong());

        JsonNode plain = call(store, s -> ResourceWebEditConfigurations.inputs(s, 60, null));
        assertEquals(5, plain.get("inputs").size());
        for (JsonNode input : plain.get("inputs")) assertTrue(input.get("selected").isNull());
        assertEquals(Collections.singletonList(41L), optionIds(input(plain.get("inputs"), KEY_PEAK)));

        /* another template: nothing selected from the configuration */
        JsonNode other = call(store, s -> ResourceWebEditConfigurations.inputs(s, 61, 50L));
        assertEquals(1, other.get("inputs").size());
        JsonNode gas = other.get("inputs").get(0);
        assertEquals(GAS_INPUT, gas.get("key").asText());
        assertTrue(gas.get("selected").isNull());
        assertEquals(Arrays.asList(41L, 43L), optionIds(gas));

        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.inputs(s, 62, null)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.inputs(s, 63, null)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.inputs(s, 50, null)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.inputs(s, 60, 51L)).status);
    }

    // ---------------------------------------------------------------- PUT

    @Test
    public void saveUnchangedKeepsTheControlCenterJson() throws Exception {
        FakeEditStore store = store();
        JsonNode model = edit(store, 50);
        WebEdit.Reply reply = put(store, 50, unchangedBody(model));
        assertEquals(200, reply.status);
        assertEquals(fixture(), stored(store, 50));
        assertEquals(2, store.count(50, FILE));
        assertEquals("StromWerkNord_202611161000.json", store.latest(50, FILE).getValue());
        assertEquals(NOW.getMillis(), (long) ResourceWebNotes.millis(store.latest(50, FILE).getTs()));
        assertTrue(store.logs.contains("CREATE_SAMPLE 50:Template File|1|web:contract-save"));
        JsonNode answer = FakeEditStore.json(reply);
        assertEquals(WebEdit.iso(NOW.getMillis(), DateTimeZone.getDefault()), answer.get("version").asText());
        assertEquals(0, answer.get("warnings").size());
        /* pretty printed like the Control Center */
        assertTrue(store.latestFile(50, FILE).contains("\"selectedInputs\" : [ {"));
    }

    @Test
    public void saveWritesDatesAndFieldsLikeTheControlCenter() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        ObjectNode contract = (ObjectNode) body.get("contract");
        contract.put("contractNumber", "V-2026-0002");
        contract.put("contractType", "GAS");
        contract.put("start", "2026-03-01");
        contract.put("end", "2028-06-30");
        contract.putNull("firstRate");
        assertEquals(200, put(store, 50, body).status);
        ObjectNode tree = stored(store, 50);
        assertEquals("V-2026-0002", tree.get("contractNumber").asText());
        assertEquals("GAS", tree.get("contractType").asText());
        assertEquals("2026-03-01T00:00:00.000+01:00", tree.get("contractStart").asText());
        assertEquals("2028-06-30T00:00:00.000+02:00", tree.get("contractEnd").asText());
        assertTrue(tree.get("firstRate").isNull());
        /* unchanged fields, unknown keys, linkedOutputs and the keys of the inputs stay */
        JsonNode fixture = fixture();
        assertEquals(fixture.get("contractDate"), tree.get("contractDate"));
        assertEquals(fixture.get("templateKey"), tree.get("templateKey"));
        assertEquals(fixture.get("linkedOutputs"), tree.get("linkedOutputs"));
        assertEquals(fixture.get("selectedInputs"), tree.get("selectedInputs"));
        assertEquals("@W.dues", tree.get("selectedInputs").get(5).get("objectKey").asText());
    }

    @Test
    public void saveChangesTheObjectsOfASelector() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        ObjectNode selections = (ObjectNode) body.get("selections");
        selections.put(KEY_SUPPLIER, 11L);
        selections.putNull(KEY_CONSUMPTION);
        selections.put(KEY_DUES, 30L);
        WebEdit.Reply reply = put(store, 50, body);
        assertEquals(200, reply.status);
        JsonNode inputs = stored(store, 50).get("selectedInputs");
        assertEquals(11, inputs.get(0).get("objectID").asLong());
        assertEquals(-1, inputs.get(1).get("objectID").asLong());
        assertEquals(45, inputs.get(2).get("objectID").asLong());
        assertEquals(11, inputs.get(3).get("objectID").asLong());
        assertEquals("@W.dues", inputs.get(5).get("objectKey").asText());
        JsonNode answer = FakeEditStore.json(reply);
        assertEquals(1, answer.get("warnings").size());
        assertEquals("Eingang \"Verbrauch_Zaehler\": kein Objekt zugeordnet", answer.get("warnings").get(0).asText());
        assertTrue(input(answer.get("inputs"), KEY_CONSUMPTION).get("selected").isNull());
    }

    @Test
    public void saveSwitchesTheTemplate() throws Exception {
        FakeEditStore store = store();
        JsonNode model = edit(store, 50);
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", model.get("version").asText());
        body.put("templateId", 61);
        body.putObject("selections").put(GAS_INPUT, 43);
        WebEdit.Reply reply = put(store, 50, body);
        assertEquals(200, reply.status);
        ObjectNode tree = stored(store, 50);
        assertEquals(61, tree.get("templateSelection").asLong());
        JsonNode inputs = tree.get("selectedInputs");
        assertEquals(1, inputs.size());
        JsonNode gas = inputs.get(0);
        List<String> fields = new ArrayList<>();
        gas.fieldNames().forEachRemaining(fields::add);
        assertEquals(Arrays.asList("objectClass", "id", "attributeName", "variableName", "variableType", "filter",
                "group", "objectID"), fields);
        assertEquals(43, gas.get("objectID").asLong());
        assertFalse(gas.get("group").asBoolean());
        /* the contract and what the web does not know stay */
        JsonNode fixture = fixture();
        assertEquals(fixture.get("linkedOutputs"), tree.get("linkedOutputs"));
        assertEquals(fixture.get("contractStart"), tree.get("contractStart"));
        assertEquals(fixture.get("templateKey"), tree.get("templateKey"));
        assertEquals(61, FakeEditStore.json(reply).get("template").get("id").asLong());
    }

    @Test
    public void switchWithoutSelectionWarns() throws Exception {
        FakeEditStore store = store();
        JsonNode model = edit(store, 50);
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", model.get("version").asText());
        body.put("templateId", 61);
        WebEdit.Reply reply = put(store, 50, body);
        assertEquals(200, reply.status);
        assertEquals(-1, stored(store, 50).get("selectedInputs").get(0).get("objectID").asLong());
        assertEquals("Eingang \"Verbrauch\": kein Objekt zugeordnet", FakeEditStore.json(reply).get("warnings").get(0).asText());
    }

    @Test
    public void saveRenames() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        body.put("name", "Strom Werk Nord 2027");
        WebEdit.Reply reply = put(store, 50, body);
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList("50 Strom Werk Nord 2027"), store.renames);
        assertEquals("StromWerkNord2027_202611161000.json", store.latest(50, FILE).getValue());
        assertTrue(store.logs.contains("UPDATE_OBJECT 50:Strom Werk Nord 2027|web:contract-save"));
    }

    @Test
    public void putValidationCollectsAllMessages() throws Exception {
        FakeEditStore store = store();
        JsonNode model = edit(store, 50);
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", model.get("version").asText());
        body.put("name", "Neu ohne Datei");
        ObjectNode contract = body.putObject("contract");
        contract.put("contractType", "WATER");
        contract.put("contractDate", "15.10.2025");
        contract.put("start", "2027-01-01");
        contract.put("end", "2026-12-31");
        contract.put("contractNumber", String.join("", Collections.nCopies(201, "x")));
        contract.put("marketLocation", 12);
        contract.put("tariff", "A");
        body.put("templateId", 60);
        ObjectNode selections = body.putObject("selections");
        selections.put(KEY_SUPPLIER, 20);
        selections.put(KEY_GRID, "x");
        selections.put("nope", 10);
        selections.put(KEY_DUES, 12);
        assertEquals(Arrays.asList(
                "An object named \"Neu ohne Datei\" already exists here",
                "contractType: one of ELECTRICITY, GAS, COMMUNITY_HEATING expected",
                "contractDate: yyyy-MM-dd expected",
                "contractNumber longer than 200 characters",
                "marketLocation: text expected",
                "Unknown contract field: tariff",
                "end is before start",
                KEY_SUPPLIER + ": a readable " + SUPPLIER + " expected",
                KEY_GRID + ": object id expected",
                "Unknown input: nope",
                KEY_DUES + ": a readable " + DUES + " expected"), messages(put(store, 50, body)));
        assertEquals(1, store.count(50, FILE));

        ObjectNode template = FakeEditStore.MAPPER.createObjectNode();
        template.put("version", model.get("version").asText());
        assertEquals(Collections.singletonList("Missing templateId"), messages(put(store, 50, template)));
        template.put("templateId", "60");
        assertEquals(Collections.singletonList("Invalid templateId"), messages(put(store, 50, template)));
        template.put("templateId", 62);
        assertEquals(Collections.singletonList("templateId: a readable " + TEMPLATE + " expected"), messages(put(store, 50, template)));
        template.put("templateId", 60);
        template.put("contract", "x");
        template.put("selections", 5);
        template.put("name", " ");
        assertEquals(Arrays.asList("Missing name", "Invalid contract", "Invalid selections"), messages(put(store, 50, template)));

        ObjectNode noVersion = unchangedBody(model);
        noVersion.remove("version");
        WebEdit.Reply reply = put(store, 50, noVersion);
        assertEquals(400, reply.status);
        assertEquals("version missing", reply.body);
    }

    @Test
    public void unchangedUnreadableObjectMayStay() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        assertEquals(45, body.get("selections").get(KEY_PEAK).asLong());
        assertEquals(200, put(store, 50, body).status);
        assertEquals(45, stored(store, 50).get("selectedInputs").get(2).get("objectID").asLong());
        /* but cannot be chosen anew */
        ObjectNode other = unchangedBody(edit(store, 50));
        ((ObjectNode) other.get("selections")).put(KEY_CONSUMPTION, 45);
        assertEquals(Collections.singletonList(KEY_CONSUMPTION + ": a readable " + CLEAN + " expected"),
                messages(put(store, 50, other)));
    }

    @Test
    public void putVersionConflict() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        body.put("version", "2026-03-01T00:00:00.000+01:00");
        WebEdit.Reply reply = put(store, 50, body);
        assertEquals(409, reply.status);
        JsonNode current = FakeEditStore.json(reply);
        assertEquals(50, current.get("id").asLong());
        assertEquals(WebEdit.iso(T_FILE.getMillis(), DateTimeZone.getDefault()), current.get("version").asText());
        assertEquals(1, store.count(50, FILE));
    }

    @Test
    public void putRights() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = unchangedBody(edit(store, 50));
        store.write.remove(50L);
        assertEquals(403, put(store, 50, body).status);
        assertEquals(404, put(store, 51, body).status);
        assertEquals(404, put(store, 60, body).status);
        store.object(50).setDeleteTS("2026-11-12T10:30:15.000+01:00");
        assertEquals(404, put(store, 50, body).status);
    }

    @Test
    public void firstSaveOfAConfigurationWithoutFile() throws Exception {
        FakeEditStore store = store();
        ObjectNode body = FakeEditStore.MAPPER.createObjectNode();
        body.put("version", "none");
        body.put("templateId", 61);
        body.putObject("selections").put(GAS_INPUT, 41);
        assertEquals(200, put(store, 52, body).status);
        ObjectNode tree = stored(store, 52);
        assertEquals(41, tree.get("selectedInputs").get(0).get("objectID").asLong());
        assertTrue(tree.get("contractNumber").isNull());
        assertEquals("NeuohneDatei_202611161000.json", store.latest(52, FILE).getValue());
    }

    // ---------------------------------------------------------------- POST

    @Test
    public void createWritesTheControlCenterJson() throws Exception {
        FakeEditStore store = store();
        String body = "{\"parentId\":4,\"name\":\"Strom Halle\",\"contract\":{\"contractNumber\":\"V-7\","
                + "\"contractType\":\"ELECTRICITY\",\"marketLocation\":null,\"contractDate\":null,\"firstRate\":null,"
                + "\"periodOfNotice\":\"2027-09-30\",\"start\":\"2027-01-01\",\"end\":null},\"templateId\":60,"
                + "\"selections\":{\"" + KEY_SUPPLIER + "\":10,\"" + KEY_CONSUMPTION + "\":41,\"" + KEY_GRID + "\":null}}";
        WebEdit.Reply reply = post(store, body);
        assertEquals(201, reply.status);
        JsonNode answer = FakeEditStore.json(reply);
        long id = answer.get("id").asLong();
        assertEquals(4L, (long) store.object(id).getParent());
        assertEquals(CONFIGURATION, store.object(id).getJevisClass());
        assertEquals("Strom Halle", answer.get("name").asText());

        ObjectNode tree = stored(store, id);
        List<String> keys = new ArrayList<>();
        tree.fieldNames().forEachRemaining(keys::add);
        assertEquals(Arrays.asList("selectedInputs", "linkedOutputs", "templateSelection", "type", "contractNumber",
                "contractType", "marketLocationNumber", "contractDate", "firstRate", "periodOfNotice", "contractStart",
                "contractEnd"), keys);
        assertEquals(60, tree.get("templateSelection").asLong());
        assertEquals("SimpleDataHandler", tree.get("type").asText());
        assertEquals(0, tree.get("linkedOutputs").size());
        assertEquals("V-7", tree.get("contractNumber").asText());
        assertTrue(tree.get("marketLocationNumber").isNull());
        assertEquals("2027-09-30T00:00:00.000+02:00", tree.get("periodOfNotice").asText());
        assertEquals("2027-01-01T00:00:00.000+01:00", tree.get("contractStart").asText());
        assertTrue(tree.get("contractEnd").isNull());
        /* every input of the template but formulas, in the template's order, with the fields of toJsonNode */
        JsonNode inputs = tree.get("selectedInputs");
        assertEquals(6, inputs.size());
        JsonNode fixtureInputs = fixture().get("selectedInputs");
        long[] objects = {10, 41, -1, 10, -1, -1};
        for (int i = 0; i < 6; i++) {
            ObjectNode expected = ((ObjectNode) fixtureInputs.get(i)).deepCopy();
            expected.remove("objectKey");
            expected.put("objectID", (int) objects[i]);
            assertEquals(expected, inputs.get(i));
        }
        assertEquals(Arrays.asList(
                        "CREATE_OBJECT " + id + ":Strom Halle|web:contract-create",
                        "CREATE_SAMPLE " + id + ":Template File|1|web:contract-create"),
                store.logs);
        assertEquals("StromHalle_202611161000.json", store.latest(id, FILE).getValue());
        assertEquals(3, answer.get("warnings").size());
    }

    @Test
    public void createValidation() throws Exception {
        FakeEditStore store = store();
        assertEquals(Arrays.asList("Missing parentId", "Missing name", "Missing templateId"), messages(post(store, "{}")));
        assertEquals(Arrays.asList("Invalid parentId", "Invalid name", "Missing templateId"),
                messages(post(store, "{\"parentId\":\"4\",\"name\":5}")));
        assertEquals(Collections.singletonList("parentId: an " + DIRECTORY + " expected"),
                messages(post(store, "{\"parentId\":5,\"name\":\"X\",\"templateId\":60}")));
        assertEquals(Collections.singletonList("An object named \"strom werk nord\" already exists here"),
                messages(post(store, "{\"parentId\":4,\"name\":\"strom werk nord\",\"templateId\":60}")));
        assertEquals(Collections.singletonList("Name longer than 100 characters"),
                messages(post(store, "{\"parentId\":4,\"name\":\"" + String.join("", Collections.nCopies(101, "n"))
                        + "\",\"templateId\":60}")));
        assertEquals(404, post(store, "{\"parentId\":51,\"name\":\"X\",\"templateId\":60}").status);
        assertEquals(403, post(store, "{\"parentId\":13,\"name\":\"X\",\"templateId\":60}").status);
        assertEquals(400, post(store, "").status);
        assertTrue(store.logs.isEmpty());
        assertEquals(0, store.writes.size());
    }

    @Test
    public void createRollsBackWhenTheFileCannotBeWritten() throws Exception {
        FakeEditStore store = store();
        store.failFileWrites = true;
        try {
            post(store, "{\"parentId\":4,\"name\":\"Strom Halle\",\"templateId\":61}");
            fail("expected the write error");
        } catch (java.io.IOException expected) {
            assertEquals(1, store.deletedObjects.size());
        }
    }

    // ---------------------------------------------------------------- DELETE

    @Test
    public void deleteMovesToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        String version = WebEdit.iso(T_FILE.getMillis(), DateTimeZone.getDefault());
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 50, null, "de")).status);
        WebEdit.Reply conflict = WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 50, "none", "de"));
        assertEquals(409, conflict.status);
        assertEquals(50, FakeEditStore.json(conflict).get("id").asLong());
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 52, "none", "de")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 51, version, "de")).status);
        assertTrue(store.recycled.isEmpty());
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 50, version, "de")).status);
        assertEquals(Collections.singletonList(50L), store.recycled);
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT 50:Strom Werk Nord|web:contract-delete"));
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditConfigurations.delete(s, 50, version, "de")).status);
    }
}
