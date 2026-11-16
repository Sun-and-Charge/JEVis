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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

public class ResourceWebEditContractsTest {

    private static final DateTimeZone ZONE = FakeEditStore.ZONE;
    private static final DateTime NOW = new DateTime(2026, 11, 16, 10, 0, 0, ZONE);
    private static final DateTime T_CONTRACTOR = new DateTime(2026, 3, 1, 12, 0, 0, ZONE);
    private static final DateTime T_PRICE_OLD = new DateTime(2025, 1, 1, 0, 0, 0, ZONE);
    private static final DateTime T_PRICE = new DateTime(2026, 1, 1, 0, 0, 0, ZONE);
    private static final DateTime T_RANGING = new DateTime(2026, 2, 1, 0, 0, 0, ZONE);
    private static final DateTime T_DUES = new DateTime(2026, 1, 1, 0, 0, 0, ZONE);
    private static final DateTime T_TEMPLATE = new DateTime(2026, 4, 1, 0, 0, 0, ZONE);

    private static final String SUPPLIER = ResourceWebContracts.CLASS_SUPPLIER;
    private static final String ELECTRICITY_SUPPLIER = "Electricity Supply Contractor";
    private static final String GAS_SUPPLIER = "Gas Supply Contractor";
    private static final String GRID = ResourceWebContracts.CLASS_GRID_OPERATOR;
    private static final String ELECTRICITY_GRID = "Electricity Grid Operator";
    private static final String CONTRACTOR = ResourceWebContracts.CLASS_CONTRACTOR;
    private static final String SUPPLY_CONTRACTOR = "Energy Supply Contractor";
    private static final String GRID_CONTRACTOR = "Energy Grid Operation Contractor";
    private static final String DUES = ResourceWebContracts.CLASS_DUES;
    private static final String CONFIGURATION = ResourceWebContracts.CLASS_CONFIGURATION;
    private static final String PRICE = "Energy Price HT";
    private static final String STATE_NUMBER = "State Number";
    private static final String CODE = "Energy Grid Operator Code Number";
    private static final String DEMAND = "Demand Charge";
    private static final String VAT = "VAT Electricity";

    // ---------------------------------------------------------------- setup

    private static JsonType type(String cls, String name, int primitive, String gui, int position) {
        JsonType type = new JsonType();
        type.setJevisclass(cls);
        type.setName(name);
        type.setPrimitiveType(primitive);
        type.setGuiType(gui);
        type.setGUIPosition(position);
        return type;
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

    private static final int STRING = JEVisConstants.PrimitiveType.STRING;
    private static final int DOUBLE = JEVisConstants.PrimitiveType.DOUBLE;
    private static final int LONG = JEVisConstants.PrimitiveType.LONG;

    private static JsonType[] supplierTypes(String cls, JsonType... own) {
        List<JsonType> types = new ArrayList<>(Arrays.asList(own));
        types.add(type(cls, "Contractor", STRING, "Object Target", 0));
        types.add(type(cls, "Additional Fix Cost", DOUBLE, "Number with Unit", 22));
        return types.toArray(new JsonType[0]);
    }

    private static JsonType[] gridTypes(String cls) {
        return new JsonType[]{type(cls, "Contractor", STRING, "Object Target", 0),
                type(cls, CODE, STRING, "Text", 11),
                type(cls, "Net Cost", DOUBLE, "Number with Unit", 13),
                type(cls, DEMAND, STRING, "Ranging Value", 15)};
    }

    private static JsonType[] contractorTypes(String cls) {
        return new JsonType[]{type(cls, "Customer Number", STRING, "Text", 3),
                type(cls, "Company", STRING, "Text", 4),
                type(cls, "Password", STRING, "Password", 5)};
    }

    /* Organisation 1 > site 2 > 3 "Energieverträge" > supply directory 4 (suppliers 10, 11), contractor directory 5
       (supply contractor 20, grid contractor 21, hidden 22), grid directory 6 (grid operator 30), dues directory 7
       (dues 40), accounting directory 8 (configuration 50 using 10, hidden configuration 51 using 40) */
    private static FakeEditStore store() {
        return store(true);
    }

    private static FakeEditStore store(boolean withDues) {
        FakeEditStore store = new FakeEditStore();
        jclass(store, ResourceWebNavigation.CLASS_BUILDING, null);
        jclass(store, ResourceWebNavigation.CLASS_ORGANIZATION, null);
        for (String directory : ResourceWebEditContracts.DIRECTORIES.values()) {
            if (!directory.contains("Metering")) jclass(store, directory, null);
        }
        jclass(store, SUPPLIER, null, supplierTypes(SUPPLIER));
        jclass(store, ELECTRICITY_SUPPLIER, SUPPLIER, supplierTypes(ELECTRICITY_SUPPLIER,
                type(ELECTRICITY_SUPPLIER, PRICE, DOUBLE, "Number with Unit", 24),
                type(ELECTRICITY_SUPPLIER, STATE_NUMBER, LONG, "Number with Unit", 25)));
        jclass(store, GAS_SUPPLIER, SUPPLIER, supplierTypes(GAS_SUPPLIER));
        jclass(store, GRID, null, gridTypes(GRID));
        jclass(store, ELECTRICITY_GRID, GRID, gridTypes(ELECTRICITY_GRID));
        jclass(store, CONTRACTOR, null, contractorTypes(CONTRACTOR));
        jclass(store, SUPPLY_CONTRACTOR, CONTRACTOR, contractorTypes(SUPPLY_CONTRACTOR));
        jclass(store, GRID_CONTRACTOR, CONTRACTOR, contractorTypes(GRID_CONTRACTOR));
        jclass(store, DUES, null, type(DUES, VAT, DOUBLE, "Number with Unit", 2));
        jclass(store, CONFIGURATION, null, type(CONFIGURATION, "Template File", JEVisConstants.PrimitiveType.FILE,
                "File Selector", 0));

        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Energieverträge", "Energy Contracting Directory", 2),
                obj(4, "Energielieferung", "Energy Supply Directory", 3),
                obj(5, "Vertragspartner", "Energy Contractor Directory", 3),
                obj(6, "Netz", "Energy Grid Operation Directory", 3),
                obj(7, "Abgaben", "Energy Governmental Dues Directory", 3),
                obj(8, "Abrechnung", "Accounting Configuration Directory", 3),
                obj(10, "Strom Nord", ELECTRICITY_SUPPLIER, 4),
                obj(11, "Strom Süd", ELECTRICITY_SUPPLIER, 4),
                obj(20, "Versorger Nord", SUPPLY_CONTRACTOR, 5),
                obj(21, "Netzbetreiber Nord", GRID_CONTRACTOR, 5),
                obj(22, "Verborgen GmbH", SUPPLY_CONTRACTOR, 5),
                obj(30, "Netz Nord", ELECTRICITY_GRID, 6),
                obj(50, "Strom Musterwerk", CONFIGURATION, 8),
                obj(51, "Verborgen", CONFIGURATION, 8));
        if (withDues) store.add(obj(40, "Abgaben 2026", DUES, 7));
        store.hidden.addAll(Arrays.asList(22L, 51L));
        store.write.addAll(Arrays.asList(10L, 20L, 30L, 40L));
        store.delete.addAll(Arrays.asList(10L, 20L, 40L));
        store.create.addAll(Arrays.asList(4L, 5L, 7L));

        store.sample(10, "Contractor", T_CONTRACTOR, "20", null);
        store.sample(10, PRICE, T_PRICE_OLD, "0.25", null);
        store.sample(10, PRICE, T_PRICE, "0.26", null);
        store.inputUnits.put("10/" + PRICE, "€/kWh");
        store.sample(30, DEMAND, T_RANGING, "{\"type\":\"RangingValuesHandler\",\"rangingValues\":[{\"from\":0.0,\"to\":100.0,\"value\":12.5}]}", null);
        if (withDues) store.sample(40, VAT, T_DUES, "19.0", null);
        store.file(50, "Template File", T_TEMPLATE, "template.json",
                "{\"selectedInputs\":[{\"objectClass\":\"Electricity Supply Contractor\",\"objectID\":10}],"
                        + "\"linkedOutputs\":[],\"contractNumber\":\"V-1\"}");
        store.file(51, "Template File", T_TEMPLATE, "template.json",
                "{\"selectedInputs\":[{\"objectClass\":\"Governmental Dues\",\"objectID\":40}]}");
        return store;
    }

    private static ResourceWebRegisters.Translations translations(FakeEditStore store) {
        return new ResourceWebRegisters.Translations(Collections.emptyList(), "de", store.classes());
    }

    private static Map<String, Object> answer(FakeEditStore store, WebEdit.Rights rights) {
        return ResourceWebContracts.contractsJson(store.lookup(), store.classes(), translations(store),
                ResourceWebEditInvoices.contractsStore(store), rights);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> group(Map<String, Object> answer, String kind) {
        for (Map<String, Object> group : (List<Map<String, Object>>) answer.get("groups")) {
            if (kind.equals(group.get("kind"))) return group;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> answer, long id) {
        for (Map<String, Object> group : (List<Map<String, Object>>) answer.get("groups")) {
            for (Map<String, Object> o : (List<Map<String, Object>>) group.get("objects")) {
                if (((Long) o.get("id")) == id) return o;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attribute(Map<String, Object> object, String name) {
        for (Map<String, Object> a : (List<Map<String, Object>>) object.get("attributes")) {
            if (name.equals(a.get("name"))) return a;
        }
        return null;
    }

    private static String version(FakeEditStore store, long id) {
        return ResourceWebEditContracts.currentVersion(store, store.object(id));
    }

    private static WebEdit.Reply call(FakeEditStore store, WebEdit.Action action) throws Exception {
        return WebEdit.call(store, action);
    }

    private static String messages(WebEdit.Reply reply) throws Exception {
        return FakeEditStore.json(reply).get("messages").toString();
    }

    // ---------------------------------------------------------------- reading

    @Test
    @SuppressWarnings("unchecked")
    public void readingAdditions() {
        FakeEditStore store = store();
        Map<String, Object> answer = answer(store, store.rights());

        Map<String, Object> suppliers = group(answer, "suppliers");
        assertEquals(true, suppliers.get("canCreate"));
        List<Map<String, Object>> targets = (List<Map<String, Object>>) suppliers.get("createTargets");
        assertEquals(1, targets.size());
        assertEquals(4L, targets.get(0).get("id"));
        assertEquals("Energieverträge", targets.get(0).get("path"));
        assertEquals("Musterwerk", ((Map<String, Object>) targets.get(0).get("site")).get("name"));
        List<Object> classes = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) suppliers.get("classes")) classes.add(c.get("class"));
        /* "Community Heating Supply Contractor" does not exist */
        assertEquals(Arrays.asList(ELECTRICITY_SUPPLIER, GAS_SUPPLIER), classes);
        assertEquals(ELECTRICITY_SUPPLIER, ((List<Map<String, Object>>) suppliers.get("classes")).get(0).get("label"));

        /* no create right in the grid directory */
        Map<String, Object> grid = group(answer, "gridOperators");
        assertEquals(false, grid.get("canCreate"));
        assertTrue(((List<Object>) grid.get("createTargets")).isEmpty());
        assertTrue(((List<Object>) grid.get("classes")).isEmpty());
        /* no directory class, no objects: left out */
        assertNull(group(answer, "meteringOperators"));

        Map<String, Object> power = object(answer, 10);
        assertEquals(true, power.get("canWrite"));
        assertEquals(true, power.get("canDelete"));
        assertEquals(WebEdit.iso(T_CONTRACTOR.getMillis(), DateTimeZone.getDefault()), power.get("version"));
        assertEquals(version(store, 10), power.get("version"));
        assertEquals(SUPPLY_CONTRACTOR, power.get("contractorClass"));
        Map<String, Object> price = attribute(power, PRICE);
        assertEquals(true, price.get("editable"));
        assertEquals("€/kWh", price.get("inputUnit"));
        assertEquals("DOUBLE", price.get("primitive"));
        assertEquals("Number with Unit", price.get("guiType"));
        assertEquals(true, price.get("history"));
        assertEquals("LONG", attribute(power, STATE_NUMBER).get("primitive"));

        Map<String, Object> net = object(answer, 30);
        assertEquals(GRID_CONTRACTOR, net.get("contractorClass"));
        assertEquals(true, net.get("canWrite"));
        assertEquals(false, net.get("canDelete"));
        Map<String, Object> demand = attribute(net, DEMAND);
        assertEquals("ranging", demand.get("type"));
        assertEquals(true, demand.get("editable"));
        assertNull(demand.get("inputUnit"));
        assertEquals("STRING", demand.get("primitive"));
        assertEquals("Ranging Value", demand.get("guiType"));
        assertEquals(true, attribute(net, CODE).get("editable"));

        assertNull(object(answer, 20).get("contractorClass"));
        assertNull(object(answer, 40).get("contractorClass"));
        assertNull(attribute(object(answer, 20), "Password"));
        Map<String, Object> south = object(answer, 11);
        assertEquals(false, south.get("canWrite"));
        assertEquals(ResourceWebEditContracts.VERSION_NONE, south.get("version"));
    }

    @Test
    public void emptyGroupsListedWhenCreatable() {
        FakeEditStore store = store(false);
        Map<String, Object> dues = group(answer(store, store.rights()), "dues");
        assertNotNull(dues);
        assertEquals(true, dues.get("canCreate"));
        assertTrue(((List<?>) dues.get("objects")).isEmpty());
        /* without rights (the old read) the empty group is left out */
        assertNull(group(answer(store, WebEdit.Rights.NONE), "dues"));
        Map<String, Object> grid = group(answer(store, WebEdit.Rights.NONE), "gridOperators");
        assertEquals(false, grid.get("canCreate"));
        assertEquals(false, object(answer(store, WebEdit.Rights.NONE), 30).get("canWrite"));
    }

    @Test
    public void editableTypes() {
        assertFalse(ResourceWebEditContracts.editable(type("X", "File", JEVisConstants.PrimitiveType.FILE, "File Selector", 0)));
        assertFalse(ResourceWebEditContracts.editable(type("X", "Password", STRING, "Password", 0)));
        assertFalse(ResourceWebEditContracts.editable(type("X", "Flag", JEVisConstants.PrimitiveType.BOOLEAN, "", 0)));
        assertFalse(ResourceWebEditContracts.editable(type("X", "Day", STRING, "Date", 0)));
        assertFalse(ResourceWebEditContracts.editable(type("X", "Contractor", STRING, "Object Target", 0)));
        assertTrue(ResourceWebEditContracts.editable(type("X", "Note", STRING, "Text", 0)));
        assertTrue(ResourceWebEditContracts.editable(type("X", "Count", LONG, "", 0)));
    }

    // ---------------------------------------------------------------- history

    @Test
    @SuppressWarnings("unchecked")
    public void historyCanDelete() throws Exception {
        FakeEditStore store = store();
        Map<String, Object> history = ResourceWebContracts.historyJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), 10, PRICE, 100, store.rights());
        List<Map<String, Object>> samples = (List<Map<String, Object>>) history.get("samples");
        assertEquals(2, samples.size());
        assertEquals(0.26, samples.get(0).get("value"));
        for (Map<String, Object> sample : samples) assertEquals(true, sample.get("canDelete"));
        Map<String, Object> ranging = ResourceWebContracts.historyJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), 30, DEMAND, 100, store.rights());
        assertEquals(false, ((List<Map<String, Object>>) ranging.get("samples")).get(0).get("canDelete"));
        /* the old signature: no rights */
        Map<String, Object> none = ResourceWebContracts.historyJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), 10, PRICE, 100);
        assertEquals(false, ((List<Map<String, Object>>) none.get("samples")).get(0).get("canDelete"));
    }

    // ---------------------------------------------------------------- contractors

    @Test
    public void contractorsList() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = ResourceWebEditContracts.contractors(store, SUPPLY_CONTRACTOR);
        assertEquals(200, reply.status);
        JsonNode list = FakeEditStore.json(reply);
        assertEquals(1, list.size());
        assertEquals(20, list.get(0).get("id").asLong());
        assertEquals("Versorger Nord", list.get(0).get("name").asText());
        assertEquals("Energieverträge / Vertragspartner", list.get(0).get("path").asText());
        /* base class: all contractors (the hidden one left out) */
        assertEquals(2, FakeEditStore.json(ResourceWebEditContracts.contractors(store, CONTRACTOR)).size());
        store.recycle(store.object(20));
        assertEquals(0, FakeEditStore.json(ResourceWebEditContracts.contractors(store, SUPPLY_CONTRACTOR)).size());
        for (String cls : new String[]{null, "", SUPPLIER, "Unknown", "Energy Metering Point Operation Contractor"}) {
            assertEquals(String.valueOf(cls), 404, ResourceWebEditContracts.contractors(store, cls).status);
        }
    }

    // ---------------------------------------------------------------- create

    private static WebEdit.Reply create(FakeEditStore store, String body) throws Exception {
        return call(store, s -> ResourceWebEditContracts.create(s, translations(store), body, NOW));
    }

    @Test
    public void createObject() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = create(store, "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"  Gas Nord \"}");
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        long id = json.get("id").asLong();
        JsonObject created = store.object(id);
        assertEquals("Gas Nord", created.getName());
        assertEquals(GAS_SUPPLIER, created.getJevisClass());
        assertEquals(4L, created.getParent());
        assertEquals("Gas Nord", json.get("name").asText());
        assertEquals(SUPPLY_CONTRACTOR, json.get("contractorClass").asText());
        assertEquals(ResourceWebEditContracts.VERSION_NONE, json.get("version").asText());
        assertTrue(store.logs.toString().contains(id + ":Gas Nord|web:contract-object-create"));

        assertEquals(201, create(store, "{\"parentId\":7,\"class\":\"Governmental Dues\",\"name\":\"Abgaben 2027\"}").status);
        assertEquals(201, create(store, "{\"parentId\":5,\"class\":\"Energy Grid Operation Contractor\",\"name\":\"Netz Süd\"}").status);
    }

    @Test
    public void createRightsAndValidation() throws Exception {
        FakeEditStore store = store();
        int before = store.objects.size();
        /* no create right in the grid directory */
        assertEquals(403, create(store, "{\"parentId\":6,\"class\":\"Electricity Grid Operator\",\"name\":\"Netz Süd\"}").status);
        /* not readable / unknown directory */
        store.hidden.add(4L);
        store.add();
        assertEquals(404, create(store, "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"X\"}").status);
        store.hidden.remove(4L);
        store.add();
        assertEquals(404, create(store, "{\"parentId\":4711,\"class\":\"Gas Supply Contractor\",\"name\":\"X\"}").status);

        String[] invalid = {
                "{\"class\":\"Gas Supply Contractor\",\"name\":\"X\"}",
                "{\"parentId\":\"4\",\"class\":\"Gas Supply Contractor\",\"name\":\"X\"}",
                "{\"parentId\":4,\"name\":\"X\"}",
                /* class of another group, the base class, a class that does not exist */
                "{\"parentId\":4,\"class\":\"Governmental Dues\",\"name\":\"X\"}",
                "{\"parentId\":4,\"class\":\"Energy Supplier\",\"name\":\"X\"}",
                "{\"parentId\":4,\"class\":\"Community Heating Supply Contractor\",\"name\":\"X\"}",
                /* not a directory of the group */
                "{\"parentId\":5,\"class\":\"Gas Supply Contractor\",\"name\":\"X\"}",
                "{\"parentId\":4,\"class\":\"Gas Supply Contractor\"}",
                "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"   \"}",
                "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"" + String.join("", Collections.nCopies(101, "a")) + "\"}",
                "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"A\\u0007B\"}",
                /* a sibling has the name (ignoring case) */
                "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"strom nord\"}",
        };
        for (String body : invalid) {
            WebEdit.Reply reply = create(store, body);
            assertEquals(body, 400, reply.status);
            assertTrue(body, FakeEditStore.json(reply).has("messages"));
        }
        assertTrue(messages(create(store, invalid[invalid.length - 1])).contains("already exists"));
        assertEquals(400, create(store, "[]").status);
        assertEquals(before, store.objects.size());
        /* a name of an object in the recycle bin is free again */
        store.recycle(store.object(10));
        assertEquals(201, create(store, "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\"Strom Nord\"}").status);
        /* 100 characters are allowed */
        assertEquals(201, create(store, "{\"parentId\":4,\"class\":\"Gas Supply Contractor\",\"name\":\""
                + String.join("", Collections.nCopies(100, "a")) + "\"}").status);
    }

    // ---------------------------------------------------------------- PUT

    private static WebEdit.Reply update(FakeEditStore store, long id, String body) throws Exception {
        return call(store, s -> ResourceWebEditContracts.update(s, translations(store), id, body, NOW));
    }

    private static String body(FakeEditStore store, long id, String rest) {
        return "{\"version\":\"" + version(store, id) + "\"," + rest + "}";
    }

    @Test
    public void updateTextAndRename() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = update(store, 30, body(store, 30, "\"name\":\"Netz Nord neu\",\"fields\":{\"" + CODE + "\":\"NB-1\"}"));
        assertEquals(200, reply.status);
        assertEquals("NB-1", store.latest(30, CODE).getValue());
        assertEquals(NOW.getMillis(), (long) ResourceWebNotes.millis(store.latest(30, CODE).getTs()));
        assertEquals(Collections.singletonList("30 Netz Nord neu"), store.renames);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Netz Nord neu", json.get("name").asText());
        assertEquals(WebEdit.iso(NOW.getMillis(), DateTimeZone.getDefault()), json.get("version").asText());
        assertTrue(store.logs.toString().contains("30:" + CODE + "|1|web:contract-object-save"));
        assertTrue(store.logs.toString().contains("30:Netz Nord neu|web:contract-object-save"));

        /* empty text clears */
        assertEquals(200, update(store, 30, body(store, 30, "\"fields\":{\"" + CODE + "\":\"\"}")).status);
        assertEquals("", store.latest(30, CODE).getValue());
        assertEquals(200, update(store, 20, body(store, 20, "\"fields\":{\"Company\":null}")).status);
        assertEquals("", store.latest(20, "Company").getValue());

        /* rename to the name of a sibling */
        assertTrue(messages(update(store, 10, body(store, 10, "\"name\":\"STROM SÜD\""))).contains("already exists"));
        /* the own name (other case) */
        assertEquals(200, update(store, 10, body(store, 10, "\"name\":\"strom nord\"")).status);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void updateRanging() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = update(store, 30, body(store, 30, "\"fields\":{\"" + DEMAND
                + "\":[{\"from\":0,\"to\":100,\"value\":12.5},{\"from\":100,\"to\":1e9,\"value\":10}]}"));
        assertEquals(200, reply.status);
        String stored = store.latest(30, DEMAND).getValue();
        JsonNode node = FakeEditStore.MAPPER.readTree(stored);
        assertEquals("RangingValuesHandler", node.get("type").asText());
        assertEquals(2, node.get("rangingValues").size());
        assertEquals(1e9, node.get("rangingValues").get(1).get("to").asDouble(), 0);
        assertTrue(stored.startsWith("{\"type\":\"RangingValuesHandler\",\"rangingValues\":[{\"from\":0.0,\"to\":100.0,\"value\":12.5}"));
        /* read back as the W4.7 read does */
        assertEquals(2, ResourceWebContracts.ranging(stored).size());
        Map<String, Object> demand = attribute((Map<String, Object>) reply.body, DEMAND);
        assertEquals(2, ((List<Object>) demand.get("value")).size());

        int writes = store.writes.size();
        String[] invalid = {
                "[]",
                "{\"from\":0,\"to\":1,\"value\":1}",
                "[{\"from\":0,\"to\":100}]",
                "[{\"from\":\"0\",\"to\":100,\"value\":1}]",
                /* from >= to */
                "[{\"from\":100,\"to\":100,\"value\":1}]",
                "[{\"from\":100,\"to\":50,\"value\":1}]",
                /* not sorted, overlapping */
                "[{\"from\":100,\"to\":200,\"value\":1},{\"from\":0,\"to\":100,\"value\":2}]",
                "[{\"from\":0,\"to\":150,\"value\":1},{\"from\":100,\"to\":200,\"value\":2}]",
        };
        for (String ranges : invalid) {
            WebEdit.Reply bad = update(store, 30, body(store, 30, "\"fields\":{\"" + DEMAND + "\":" + ranges + "}"));
            assertEquals(ranges, 400, bad.status);
        }
        StringBuilder many = new StringBuilder("[");
        for (int i = 0; i < 51; i++) many.append(i > 0 ? "," : "").append("{\"from\":").append(i).append(",\"to\":").append(i + 1).append(",\"value\":1}");
        many.append("]");
        assertTrue(messages(update(store, 30, body(store, 30, "\"fields\":{\"" + DEMAND + "\":" + many + "}"))).contains("at most 50"));
        String fifty = many.toString().replaceFirst(",\\{\"from\":50,\"to\":51,\"value\":1}", "");
        assertEquals(writes, store.writes.size());
        assertEquals(200, update(store, 30, body(store, 30, "\"fields\":{\"" + DEMAND + "\":" + fifty + "}")).status);
    }

    @Test
    public void updateContractor() throws Exception {
        FakeEditStore store = store();
        store.write.add(11L);
        assertEquals(200, update(store, 11, body(store, 11, "\"contractor\":20")).status);
        assertEquals("20", store.latest(11, "Contractor").getValue());
        assertEquals(200, update(store, 11, body(store, 11, "\"contractor\":null")).status);
        assertEquals("", store.latest(11, "Contractor").getValue());

        /* wrong class, not readable, in the recycle bin, no id */
        for (String contractor : new String[]{"21", "22", "4711", "\"20\"", "20.5"}) {
            assertEquals(contractor, 400, update(store, 11, body(store, 11, "\"contractor\":" + contractor)).status);
        }
        /* contractors have no contractor */
        assertEquals(400, update(store, 20, body(store, 20, "\"contractor\":21")).status);
        store.recycle(store.object(20));
        assertEquals(400, update(store, 11, body(store, 11, "\"contractor\":20")).status);
        /* grid operator: grid operation contractors */
        assertEquals(200, update(store, 30, body(store, 30, "\"contractor\":21")).status);
        /* dues have no contractor */
        assertTrue(messages(update(store, 40, body(store, 40, "\"contractor\":21"))).contains("no contractor"));
        /* not as a field */
        assertEquals(400, update(store, 30, body(store, 30, "\"fields\":{\"Contractor\":\"21\"}")).status);
    }

    @Test
    public void updateRejectsNumbersAndUnknownFields() throws Exception {
        FakeEditStore store = store();
        int writes = store.writes.size();
        assertTrue(messages(update(store, 10, body(store, 10, "\"fields\":{\"" + PRICE + "\":\"0.3\"}"))).contains("/values"));
        assertEquals(400, update(store, 10, body(store, 10, "\"fields\":{\"Unknown\":\"x\"}")).status);
        assertEquals(400, update(store, 20, body(store, 20, "\"fields\":{\"Password\":\"x\"}")).status);
        assertEquals(400, update(store, 30, body(store, 30, "\"fields\":{\"" + CODE + "\":5}")).status);
        assertEquals(400, update(store, 30, body(store, 30, "\"fields\":[]")).status);
        /* one invalid field: nothing written */
        assertEquals(400, update(store, 30, body(store, 30, "\"name\":\"Neu\",\"fields\":{\"" + CODE + "\":\"x\",\"Unknown\":1}")).status);
        assertEquals(writes, store.writes.size());
        assertTrue(store.renames.isEmpty());
    }

    @Test
    public void updateConflictAndErrors() throws Exception {
        FakeEditStore store = store();
        String stale = "{\"version\":\"2020-01-01T00:00:00.000+01:00\",\"fields\":{\"" + CODE + "\":\"x\"}}";
        WebEdit.Reply conflict = update(store, 30, stale);
        assertEquals(409, conflict.status);
        assertEquals(30, FakeEditStore.json(conflict).get("id").asLong());
        assertEquals(version(store, 30), FakeEditStore.json(conflict).get("version").asText());
        assertNull(store.latest(30, CODE));
        /* an object without samples: "none" */
        store.write.add(11L);
        assertEquals(409, update(store, 11, "{\"version\":\"" + version(store, 10) + "\",\"name\":\"X\"}").status);
        assertEquals(200, update(store, 11, "{\"version\":\"none\",\"name\":\"Strom West\"}").status);

        assertEquals(400, update(store, 30, "{\"fields\":{\"" + CODE + "\":\"x\"}}").status);
        assertEquals(400, update(store, 30, "{\"version\":\"kein Datum\",\"fields\":{\"" + CODE + "\":\"x\"}}").status);
        assertEquals(400, update(store, 30, body(store, 30, "\"x\":1")).status);
        store.write.remove(11L);
        assertEquals(403, update(store, 11, "{\"version\":\"none\",\"name\":\"Y\"}").status);
        /* hidden, unknown, configuration, directory */
        for (long id : new long[]{22, 4711, 50, 4}) {
            assertEquals(String.valueOf(id), 404, update(store, id, "{\"version\":\"none\",\"name\":\"Y\"}").status);
        }
    }

    // ---------------------------------------------------------------- values

    private static WebEdit.Reply value(FakeEditStore store, long id, String body) throws Exception {
        return call(store, s -> ResourceWebEditContracts.addValue(s, id, body, "de", NOW));
    }

    @Test
    public void addValueWithValidity() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = value(store, 10, "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-01-01\",\"value\":0.28}");
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("2027-01-01T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals(0.28, json.get("value").asDouble(), 0);
        DateTime validFrom = new DateTime(2027, 1, 1, 0, 0, 0, ZONE);
        JsonSample sample = store.at(10, PRICE, validFrom);
        assertEquals("0.28", sample.getValue());
        assertTrue(sample.getNote(), ResourceWebEditValues.isManual(sample.getNote()));
        assertTrue(store.logs.toString().contains("10:" + PRICE + "|1|web:contract-value"));

        /* the site's time zone */
        store.workDay = WebEdit.WorkDay.standard(DateTimeZone.forID("America/New_York"));
        reply = value(store, 40, "{\"attribute\":\"" + VAT + "\",\"validFrom\":\"2027-07-01\",\"value\":7}");
        assertEquals("2027-07-01T00:00:00.000-04:00", FakeEditStore.json(reply).get("timestamp").asText());
        assertEquals("7.0", store.at(40, VAT, new DateTime(2027, 7, 1, 0, 0, 0, DateTimeZone.forID("America/New_York"))).getValue());
    }

    @Test
    public void addValueOverwriteNeedsConfirm() throws Exception {
        FakeEditStore store = store();
        String body = "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2026-01-01\",\"value\":0.27";
        WebEdit.Reply conflict = value(store, 10, body + "}");
        assertEquals(409, conflict.status);
        JsonNode json = FakeEditStore.json(conflict);
        assertEquals("Für 01.01.2026 gibt es schon einen Wert: 0,26 – überschreiben?", json.get("warnings").get(0).asText());
        assertEquals("2026-01-01T00:00:00.000+01:00", json.get("timestamp").asText());
        assertEquals("0.26", store.at(10, PRICE, T_PRICE).getValue());
        assertEquals(409, value(store, 10, body + ",\"confirm\":false}").status);
        assertEquals(201, value(store, 10, body + ",\"confirm\":true}").status);
        assertEquals("0.27", store.at(10, PRICE, T_PRICE).getValue());
        assertEquals("There already is a value for 2026-01-01: 0.26 – overwrite?",
                ResourceWebEditContracts.existsWarning(T_PRICE.toLocalDate(), "0.26", "en"));
        assertEquals("Für 01.01.2026 gibt es schon einen Wert: abc – überschreiben?",
                ResourceWebEditContracts.existsWarning(T_PRICE.toLocalDate(), "abc", "de"));
    }

    @Test
    public void addValueValidation() throws Exception {
        FakeEditStore store = store();
        int writes = store.writes.size();
        String[] invalid = {
                "{\"validFrom\":\"2027-01-01\",\"value\":1}",
                "{\"attribute\":\"Unknown\",\"validFrom\":\"2027-01-01\",\"value\":1}",
                /* ranging, text, contractor: not with a validity date */
                "{\"attribute\":\"Contractor\",\"validFrom\":\"2027-01-01\",\"value\":1}",
                "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"01.01.2027\",\"value\":1}",
                "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-02-30\",\"value\":1}",
                "{\"attribute\":\"" + PRICE + "\",\"value\":1}",
                "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-01-01\",\"value\":\"0,28\"}",
                "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-01-01\"}",
                /* LONG: integers only */
                "{\"attribute\":\"" + STATE_NUMBER + "\",\"validFrom\":\"2027-01-01\",\"value\":3.5}",
                "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-01-01\",\"value\":1,\"confirm\":\"yes\"}",
        };
        for (String body : invalid) assertEquals(body, 400, value(store, 10, body).status);
        assertEquals(400, value(store, 30, "{\"attribute\":\"" + DEMAND + "\",\"validFrom\":\"2027-01-01\",\"value\":1}").status);
        assertEquals(400, value(store, 30, "{\"attribute\":\"" + CODE + "\",\"validFrom\":\"2027-01-01\",\"value\":1}").status);
        assertEquals(writes, store.writes.size());

        WebEdit.Reply reply = value(store, 10, "{\"attribute\":\"" + STATE_NUMBER + "\",\"validFrom\":\"2027-01-01\",\"value\":3}");
        assertEquals(201, reply.status);
        assertEquals(3, FakeEditStore.json(reply).get("value").asLong());
        assertEquals("3", store.latest(10, STATE_NUMBER).getValue());

        /* no write right, not readable, not a contract object */
        String ok = "{\"attribute\":\"" + PRICE + "\",\"validFrom\":\"2027-01-01\",\"value\":1}";
        assertEquals(403, value(store, 11, ok).status);
        assertEquals(404, value(store, 22, ok).status);
        assertEquals(404, value(store, 50, ok).status);
        assertEquals(404, value(store, 4711, ok).status);
    }

    // ---------------------------------------------------------------- value delete

    private static WebEdit.Reply deleteValue(FakeEditStore store, long id, String attribute, String timestamp)
            throws Exception {
        return call(store, s -> ResourceWebEditContracts.deleteValue(s, id, attribute, timestamp));
    }

    @Test
    public void deleteValue() throws Exception {
        FakeEditStore store = store();
        String ts = ResourceWebContracts.isoTimestamp(store.at(10, PRICE, T_PRICE).getTs());
        assertEquals(204, deleteValue(store, 10, PRICE, ts).status);
        assertNull(store.at(10, PRICE, T_PRICE));
        assertNotNull(store.at(10, PRICE, T_PRICE_OLD));
        assertTrue(store.logs.toString().contains("web:contract-value-delete"));
        assertEquals(404, deleteValue(store, 10, PRICE, ts).status);

        /* no delete right on the grid operator */
        assertEquals(403, deleteValue(store, 30, DEMAND, WebEdit.iso(T_RANGING.getMillis(), ZONE)).status);
        store.delete.add(30L);
        assertEquals(204, deleteValue(store, 30, DEMAND, WebEdit.iso(T_RANGING.getMillis(), ZONE)).status);
        /* text, contractor, unknown attribute; unknown object */
        assertEquals(404, deleteValue(store, 30, CODE, ts).status);
        assertEquals(404, deleteValue(store, 10, "Contractor", ts).status);
        assertEquals(404, deleteValue(store, 10, null, ts).status);
        assertEquals(404, deleteValue(store, 4711, PRICE, ts).status);
        assertEquals(400, deleteValue(store, 10, PRICE, "gestern").status);
        assertEquals(400, deleteValue(store, 10, PRICE, null).status);
    }

    // ---------------------------------------------------------------- object delete

    private static WebEdit.Reply delete(FakeEditStore store, long id, String version, boolean confirm) throws Exception {
        return call(store, s -> ResourceWebEditContracts.delete(s, translations(store), id, version, confirm));
    }

    @Test
    public void deleteUsedObjectNeedsConfirm() throws Exception {
        FakeEditStore store = store();
        /* the supplier is an input of a configuration */
        WebEdit.Reply used = delete(store, 10, version(store, 10), false);
        assertEquals(409, used.status);
        JsonNode usedBy = FakeEditStore.json(used).get("usedBy");
        assertEquals(1, usedBy.size());
        assertEquals(50, usedBy.get(0).get("id").asLong());
        assertEquals("Strom Musterwerk", usedBy.get(0).get("name").asText());
        assertEquals("configuration", usedBy.get(0).get("kind").asText());
        assertTrue(store.recycled.isEmpty());

        /* the contractor is the contractor of the supplier */
        JsonNode contractor = FakeEditStore.json(delete(store, 20, version(store, 20), false)).get("usedBy");
        assertEquals(1, contractor.size());
        assertEquals(10, contractor.get(0).get("id").asLong());
        assertEquals("object", contractor.get(0).get("kind").asText());

        assertEquals(204, delete(store, 10, version(store, 10), true).status);
        assertEquals(Collections.singletonList(10L), store.recycled);
        assertTrue(store.logs.toString().contains("10:Strom Nord|web:contract-object-delete"));
        /* the supplier is in the recycle bin: the contractor is free */
        assertEquals(204, delete(store, 20, version(store, 20), false).status);
        /* only a hidden configuration uses the dues */
        assertEquals(204, delete(store, 40, version(store, 40), false).status);
    }

    @Test
    public void deleteVersionRightsAndErrors() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply conflict = delete(store, 40, "2020-01-01T00:00:00.000+01:00", true);
        assertEquals(409, conflict.status);
        assertEquals(40, FakeEditStore.json(conflict).get("id").asLong());
        assertEquals(400, delete(store, 40, null, true).status);
        assertEquals(400, delete(store, 40, " ", true).status);
        assertEquals(400, delete(store, 40, "kein Datum", true).status);
        assertEquals(403, delete(store, 30, version(store, 30), true).status);
        assertEquals(404, delete(store, 22, "none", true).status);
        assertEquals(404, delete(store, 50, "none", true).status);
        assertEquals(404, delete(store, 4711, "none", true).status);
        assertTrue(store.recycled.isEmpty());
        assertEquals(204, delete(store, 40, version(store, 40), true).status);
        assertEquals(404, delete(store, 40, version(store, 40), true).status);
    }

    @Test
    public void templateInputIds() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList(10L, 30L)), ResourceWebEditContracts.templateInputs(FakeEditStore.MAPPER.readTree(
                "{\"selectedInputs\":[{\"objectID\":10},{\"objectID\":\"30\"},{\"objectID\":null},{\"objectID\":\"x\"},{}]}")));
        assertTrue(ResourceWebEditContracts.templateInputs(null).isEmpty());
        assertTrue(ResourceWebEditContracts.templateInputs(FakeEditStore.MAPPER.readTree("{\"selectedInputs\":{}}")).isEmpty());
    }
}
