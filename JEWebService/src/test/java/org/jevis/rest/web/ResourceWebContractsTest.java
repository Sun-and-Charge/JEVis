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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebContractsTest {

    private static final String SUPPLIER = ResourceWebContracts.CLASS_SUPPLIER;
    private static final String ELECTRICITY_SUPPLIER = "Electricity Supply Contractor";
    private static final String METERING = ResourceWebContracts.CLASS_METERING_OPERATOR;
    private static final String GRID = ResourceWebContracts.CLASS_GRID_OPERATOR;
    private static final String ELECTRICITY_GRID = "Electricity Grid Operator";
    private static final String CONTRACTOR = ResourceWebContracts.CLASS_CONTRACTOR;
    private static final String SUPPLY_CONTRACTOR = "Energy Supply Contractor";
    private static final String DUES = ResourceWebContracts.CLASS_DUES;
    private static final String CONFIGURATION = ResourceWebContracts.CLASS_CONFIGURATION;
    private static final String TS_OLD = "2025-01-01T00:00:00.000+01:00";
    private static final String TS_NEW = "2026-01-01T00:00:00.000+01:00";

    // ---------------------------------------------------------------- classes and translations

    private static JsonType type(String cls, String name, int primitive, String gui, int position) {
        JsonType type = new JsonType();
        type.setJevisclass(cls);
        type.setName(name);
        type.setPrimitiveType(primitive);
        type.setGuiType(gui);
        type.setGUIPosition(position);
        return type;
    }

    private static JsonJEVisClass jclass(String name, String inherits, List<JsonType> types) {
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
        jclass.setTypes(new ArrayList<>(types));
        return jclass;
    }

    private static List<JsonType> plus(List<JsonType> types, JsonType... more) {
        List<JsonType> result = new ArrayList<>(Arrays.asList(more));
        result.addAll(types);
        return result;
    }

    private static final List<JsonType> SUPPLIER_TYPES = Arrays.asList(
            type(SUPPLIER, "Contractor", 0, "Object Target", 0),
            type(SUPPLIER, "Additional Relative Cost", 1, "Number with Unit", 21),
            type(SUPPLIER, "Additional Fix Cost", 1, "Number with Unit", 22),
            type(SUPPLIER, "Contract File", 3, "File Selector", 23));
    /* own types first, as the class cache adds the inherited ones after them */
    private static final List<JsonType> ELECTRICITY_SUPPLIER_TYPES = plus(SUPPLIER_TYPES,
            type(ELECTRICITY_SUPPLIER, "Energy Price HT", 1, "Number with Unit", 24),
            type(ELECTRICITY_SUPPLIER, "Energy Price NT", 1, "Number with Unit", 25),
            type(ELECTRICITY_SUPPLIER, "Demand Charge", 1, "Number with Unit", 26));
    private static final List<JsonType> GRID_TYPES = Arrays.asList(
            type(GRID, "Contractor", 0, "Object Target", 0),
            type(GRID, "Energy Grid Operator Code Number", 0, "Text", 11),
            type(GRID, "Net Cost", 1, "Number with Unit", 13),
            type(GRID, "Demand Charge", 0, "Ranging Value", 15));
    private static final List<JsonType> CONTRACTOR_TYPES = Arrays.asList(
            type(CONTRACTOR, "Customer Number", 0, "Text", 3),
            type(CONTRACTOR, "Company", 0, "Text", 4),
            type(CONTRACTOR, "Password", 0, "Password", 5));
    private static final List<JsonType> DUES_TYPES = Arrays.asList(
            type(DUES, "VAT Electricity", 1, "Number with Unit", 2),
            type(DUES, "Allocation 01", 1, "Number with Unit", 6));

    static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        for (JsonJEVisClass c : Arrays.asList(
                jclass(ResourceWebNavigation.CLASS_BUILDING, null, Collections.emptyList()),
                jclass(SUPPLIER, null, SUPPLIER_TYPES),
                jclass(ELECTRICITY_SUPPLIER, SUPPLIER, ELECTRICITY_SUPPLIER_TYPES),
                jclass(METERING, null, Collections.emptyList()),
                jclass(GRID, null, GRID_TYPES),
                jclass(ELECTRICITY_GRID, GRID, GRID_TYPES),
                jclass(CONTRACTOR, null, CONTRACTOR_TYPES),
                jclass(SUPPLY_CONTRACTOR, CONTRACTOR, CONTRACTOR_TYPES),
                jclass(DUES, null, DUES_TYPES),
                jclass(CONFIGURATION, null, Collections.singletonList(
                        type(CONFIGURATION, "Template File", 3, "File Selector", 0))),
                jclass(ResourceWebInvoices.CLASS_INVOICE, null, Collections.emptyList()))) {
            map.put(c.getName(), c);
        }
        return new ResourceWebRegisters.Classes(map);
    }

    private static Map<String, String> names(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static JsonI18nClass i18n(String cls, Map<String, String> names, JsonI18nType... types) {
        JsonI18nClass i18n = new JsonI18nClass();
        i18n.setJevisclass(cls);
        i18n.setNames(names);
        i18n.setTypes(new ArrayList<>(Arrays.asList(types)));
        return i18n;
    }

    private static JsonI18nType i18nType(String type, Map<String, String> names) {
        JsonI18nType i18n = new JsonI18nType();
        i18n.setType(type);
        i18n.setNames(names);
        return i18n;
    }

    private static ResourceWebRegisters.Translations translations(String lang) {
        return new ResourceWebRegisters.Translations(Arrays.asList(
                i18n(SUPPLIER, names("en", "Energy Supplier", "de", "Energielieferant"),
                        i18nType("Additional Fix Cost", names("en", "Additional fix cost", "de", "Zusätzliche Fixkosten"))),
                i18n(ELECTRICITY_SUPPLIER, names("en", "Electricity Supply Contractor", "de", "Stromlieferant"),
                        i18nType("Energy Price HT", names("en", "Energy price HT", "de", "Arbeitspreis HT"))),
                i18n(GRID, names("en", "Energy Grid Operator"),
                        i18nType("Demand Charge", names("en", "Demand charge", "de", "Leistungspreis")))),
                lang, classes());
    }

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 "Musterwerk" > 3 "Energieverträge" > 4 "Energielieferung" > suppliers 10, 11, hidden 12,
       recycle bin 13; 3 > 5 "Vertragspartner" > contractors 20, hidden 21, recycle bin 22; 3 > grid operator 30;
       3 > dues 40; 3 > 6 "Abrechnung" > configurations 50 (complete), 51 (broken JSON), 52 (no template),
       53 (unknown energy type), hidden 54; supplier 14 below the organisation (no site); invoice 60 */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject CONTRACTS = obj(3, "Energieverträge", "Energy Contracting Directory", 2);
    private static final JsonObject SUPPLY = obj(4, "Energielieferung", "Energy Supply Directory", 3);
    private static final JsonObject PARTNERS = obj(5, "Vertragspartner", "Energy Contractor Directory", 3);
    private static final JsonObject ACCOUNTING = obj(6, "Abrechnung", "Accounting Configuration Directory", 3);
    static final JsonObject POWER = obj(10, "Strom - Energieversorger Nord", ELECTRICITY_SUPPLIER, 4);
    private static final JsonObject GENERAL = obj(11, "Allgemein", SUPPLIER, 4);
    private static final JsonObject SUPPLIER_HIDDEN = obj(12, "Verborgen", ELECTRICITY_SUPPLIER, 4);
    private static final JsonObject SUPPLIER_BIN = obj(13, "Alt", ELECTRICITY_SUPPLIER, 4);
    private static final JsonObject SUPPLIER_LONE = obj(14, "Ohne Standort", SUPPLIER, 1);
    private static final JsonObject NORD = obj(20, "Energieversorger Nord", SUPPLY_CONTRACTOR, 5);
    private static final JsonObject CONTRACTOR_HIDDEN = obj(21, "Verborgen GmbH", SUPPLY_CONTRACTOR, 5);
    private static final JsonObject CONTRACTOR_BIN = obj(22, "Alt GmbH", SUPPLY_CONTRACTOR, 5);
    private static final JsonObject NET = obj(30, "Netz Nord", ELECTRICITY_GRID, 3);
    private static final JsonObject TAXES = obj(40, "Abgaben 2026", DUES, 3);
    private static final JsonObject CONFIG = obj(50, "Strom Musterwerk", CONFIGURATION, 6);
    private static final JsonObject CONFIG_BROKEN = obj(51, "Kaputt", CONFIGURATION, 6);
    private static final JsonObject CONFIG_EMPTY = obj(52, "Leer", CONFIGURATION, 6);
    private static final JsonObject CONFIG_OTHER = obj(53, "Sonstiges", CONFIGURATION, 6);
    private static final JsonObject CONFIG_HIDDEN = obj(54, "Verborgen", CONFIGURATION, 6);
    private static final JsonObject INVOICE = obj(60, "Rechnung", ResourceWebInvoices.CLASS_INVOICE, 3);

    static {
        SUPPLIER_BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
        CONTRACTOR_BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, CONTRACTS, SUPPLY, PARTNERS, ACCOUNTING, POWER,
            GENERAL, SUPPLIER_HIDDEN, SUPPLIER_BIN, SUPPLIER_LONE, NORD, CONTRACTOR_HIDDEN, CONTRACTOR_BIN, NET, TAXES,
            CONFIG, CONFIG_BROKEN, CONFIG_EMPTY, CONFIG_OTHER, CONFIG_HIDDEN, INVOICE);
    private static final List<JsonObject> HIDDEN = Arrays.asList(SUPPLIER_HIDDEN, CONTRACTOR_HIDDEN, CONFIG_HIDDEN);

    private static List<JsonObject> readable() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.removeAll(HIDDEN);
        return readable;
    }

    private static ResourceWebModules.Lookup lookup() {
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : ALL) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(ALL, readable(), parents);
    }

    // ---------------------------------------------------------------- store

    private static JsonSample sample(String ts, String value) {
        JsonSample sample = new JsonSample();
        sample.setTs(ts);
        sample.setValue(value);
        return sample;
    }

    static class TestStore implements ResourceWebContracts.Store {
        final Map<Long, Map<String, JsonAttribute>> attributes = new HashMap<>();
        final Map<String, List<JsonSample>> samples = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        /**
         * @param values oldest first
         */
        TestStore add(long id, String name, String unit, String... values) {
            List<JsonSample> list = samples.computeIfAbsent(id + "/" + name, k -> new ArrayList<>());
            for (int i = 0; i < values.length; i++) {
                list.add(sample(String.format("%d-01-01T00:00:00.000+01:00", 2026 - values.length + 1 + i), values[i]));
            }
            JsonAttribute attribute = new JsonAttribute();
            attribute.setType(name);
            attribute.setSampleCount(list.size());
            if (!list.isEmpty()) attribute.setLatestValue(list.get(list.size() - 1));
            JsonUnit jsonUnit = new JsonUnit();
            jsonUnit.setLabel(unit != null ? unit : "");
            attribute.setDisplayUnit(jsonUnit);
            attributes.computeIfAbsent(id, k -> new HashMap<>()).put(name, attribute);
            return this;
        }

        TestStore file(long id, String name, String fileName, String content) {
            add(id, name, null, fileName);
            files.put(id + "/" + name, content.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        @Override
        public Map<String, JsonAttribute> attributes(long id) {
            calls.add("attributes " + id);
            return attributes.getOrDefault(id, Collections.emptyMap());
        }

        @Override
        public List<JsonSample> latest(long id, String attribute, int limit) {
            calls.add("latest " + id + "/" + attribute + "/" + limit);
            List<JsonSample> list = new ArrayList<>(samples.getOrDefault(id + "/" + attribute, Collections.emptyList()));
            Collections.reverse(list);
            return list.subList(0, Math.min(limit, list.size()));
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) {
            calls.add("file " + id + "/" + attribute);
            return files.get(id + "/" + attribute);
        }
    }

    private static final String TEMPLATE = "{\"selectedInputs\":[{\"objectClass\":\"Data\",\"id\":\"x\",\"objectID\":4711}],"
            + "\"linkedOutputs\":[{\"id\":\"y\",\"link\":true,\"target\":\"4712:Value\"}],\"templateSelection\":99,"
            + "\"type\":\"SimpleDataHandler\",\"contractNumber\":\"V-1001\",\"contractType\":\"ELECTRICITY\","
            + "\"marketLocationNumber\":\"DE0000000000000000000000000000001\","
            + "\"contractDate\":\"2025-12-01T00:00:00.000+01:00\",\"firstRate\":\"2026-01-01T00:00:00.000+01:00\","
            + "\"periodOfNotice\":\"2027-09-30T00:00:00.000+02:00\",\"contractStart\":\"2026-01-01T00:00:00.000+01:00\","
            + "\"contractEnd\":\"2027-12-31T00:00:00.000+01:00\"}";

    static TestStore store() {
        TestStore store = new TestStore();
        store.add(POWER.getId(), "Contractor", null, "20");
        store.add(POWER.getId(), "Energy Price HT", "€/kWh", "0.25", "0.27", "0.28");
        store.add(POWER.getId(), "Energy Price NT", "€/kWh", "0.21");
        store.add(POWER.getId(), "Additional Fix Cost", "€", "12.5");
        store.file(POWER.getId(), "Contract File", "vertrag.pdf", "%PDF-contract");
        store.add(GENERAL.getId(), "Contractor", null, "21:Value");
        store.add(SUPPLIER_LONE.getId(), "Contractor", null, "22");
        store.add(NET.getId(), "Contractor", null, "keine Zahl");
        store.add(NET.getId(), "Energy Grid Operator Code Number", null, "NB-4711");
        store.add(NET.getId(), "Net Cost", "€", "abc");
        store.add(NET.getId(), "Demand Charge", "€/kW",
                "{\"rangingValues\":[{\"from\":0,\"to\":100,\"value\":12.0}]}",
                "{\"rangingValues\":[{\"from\":0,\"to\":100,\"value\":12.5},{\"from\":100,\"to\":1000,\"value\":10.0}]}");
        store.add(NORD.getId(), "Customer Number", null, "K-1", "K-2");
        store.add(NORD.getId(), "Password", null, "geheim");
        store.add(TAXES.getId(), "VAT Electricity", "%", "19");
        store.file(CONFIG.getId(), "Template File", "template.json", TEMPLATE);
        store.file(CONFIG_BROKEN.getId(), "Template File", "template.json", "{\"contractNumber\": ");
        store.file(CONFIG_OTHER.getId(), "Template File", "template.json",
                "{\"contractNumber\":\"\",\"contractType\":\"WATER\",\"periodOfNotice\":\"3 Monate\",\"marketLocationNumber\":null,"
                        + "\"contractStart\":{\"a\":1}}");
        return store;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("groups");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> answer, long id) {
        for (Map<String, Object> group : groups(answer)) {
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

    @SuppressWarnings("unchecked")
    private static List<String> attributeNames(Map<String, Object> object) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> a : (List<Map<String, Object>>) object.get("attributes")) names.add((String) a.get("name"));
        return names;
    }

    private static Map<String, Object> answer(String lang) {
        return ResourceWebContracts.contractsJson(lookup(), classes(), translations(lang), store());
    }

    // ---------------------------------------------------------------- groups

    @Test
    @SuppressWarnings("unchecked")
    public void groupsInOrderOnlyReadableNotDeleted() {
        Map<String, Object> answer = answer("de");
        List<String> kinds = new ArrayList<>();
        for (Map<String, Object> group : groups(answer)) kinds.add((String) group.get("kind"));
        /* no metering point operator: the group is left out */
        assertEquals(Arrays.asList("suppliers", "gridOperators", "contractors", "dues"), kinds);

        List<Object> suppliers = new ArrayList<>();
        for (Map<String, Object> o : (List<Map<String, Object>>) groups(answer).get(0).get("objects")) suppliers.add(o.get("id"));
        /* site "Musterwerk", same path, by name; without site last; subclasses included */
        assertEquals(Arrays.asList(11L, 10L, 14L), suppliers);
        for (JsonObject o : Arrays.asList(SUPPLIER_HIDDEN, SUPPLIER_BIN, CONTRACTOR_HIDDEN, CONTRACTOR_BIN, SITE)) {
            assertNull(o.getName(), object(answer, o.getId()));
        }
        assertNotNull(object(answer, NORD.getId()));
        assertNotNull(object(answer, NET.getId()));
        assertNotNull(object(answer, TAXES.getId()));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void supplierObject() {
        Map<String, Object> power = object(answer("de"), POWER.getId());
        assertEquals("Strom - Energieversorger Nord", power.get("name"));
        assertEquals(ELECTRICITY_SUPPLIER, power.get("className"));
        assertEquals("Stromlieferant", power.get("classLabel"));
        assertEquals("Energieverträge / Energielieferung", power.get("path"));
        Map<String, Object> site = (Map<String, Object>) power.get("site");
        assertEquals(2L, site.get("id"));
        assertEquals("Musterwerk", site.get("name"));
        Map<String, Object> contractor = (Map<String, Object>) power.get("contractor");
        assertEquals(20L, contractor.get("id"));
        assertEquals("Energieversorger Nord", contractor.get("name"));
        assertEquals(true, contractor.get("readable"));

        /* by GUI position, without "Contractor" and file attributes; inherited ones included */
        assertEquals(Arrays.asList("Additional Relative Cost", "Additional Fix Cost", "Energy Price HT", "Energy Price NT",
                "Demand Charge"), attributeNames(power));

        Map<String, Object> ht = attribute(power, "Energy Price HT");
        assertEquals("Arbeitspreis HT", ht.get("label"));
        assertEquals("number", ht.get("type"));
        assertEquals(0.28, ht.get("value"));
        assertEquals("€/kWh", ht.get("unit"));
        assertEquals(TS_NEW, ht.get("timestamp"));
        assertEquals(true, ht.get("history"));

        Map<String, Object> nt = attribute(power, "Energy Price NT");
        assertEquals(false, nt.get("history"));
        assertEquals(0.21, nt.get("value"));

        /* inherited translation of the parent class */
        assertEquals("Zusätzliche Fixkosten", attribute(power, "Additional Fix Cost").get("label"));

        /* no sample */
        Map<String, Object> relative = attribute(power, "Additional Relative Cost");
        assertEquals("Additional Relative Cost", relative.get("label"));
        assertNull(relative.get("value"));
        assertNull(relative.get("timestamp"));
        assertNull(relative.get("unit"));
        assertEquals(false, relative.get("history"));
    }

    @Test
    public void labelsInEnglishAndFallbacks() {
        Map<String, Object> answer = answer("en");
        Map<String, Object> power = object(answer, POWER.getId());
        assertEquals("Electricity Supply Contractor", power.get("classLabel"));
        assertEquals("Energy price HT", attribute(power, "Energy Price HT").get("label"));
        /* unknown language: English */
        assertEquals("Energy price HT", attribute(object(answer("fr"), POWER.getId()), "Energy Price HT").get("label"));
        /* no German name: English; no translation at all: the class name */
        Map<String, Object> net = object(answer("de"), NET.getId());
        assertEquals(ELECTRICITY_GRID, net.get("classLabel"));
        assertEquals("Leistungspreis", attribute(net, "Demand Charge").get("label"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void contractorTargets() {
        Map<String, Object> answer = answer("de");
        /* "id:attribute" of a hidden object: id only */
        Map<String, Object> hidden = (Map<String, Object>) object(answer, GENERAL.getId()).get("contractor");
        assertEquals(21L, hidden.get("id"));
        assertNull(hidden.get("name"));
        assertEquals(false, hidden.get("readable"));
        /* contractor in the recycle bin */
        Map<String, Object> deleted = (Map<String, Object>) object(answer, SUPPLIER_LONE.getId()).get("contractor");
        assertEquals(22L, deleted.get("id"));
        assertEquals(false, deleted.get("readable"));
        /* no target */
        assertNull(object(answer, NET.getId()).get("contractor"));
        /* class without "Contractor" */
        assertNull(object(answer, NORD.getId()).get("contractor"));
        assertTrue(object(answer, NORD.getId()).containsKey("contractor"));

        ResourceWebModules.Lookup lookup = lookup();
        assertNull(ResourceWebContracts.contractorJson(null, lookup));
        assertNull(ResourceWebContracts.contractorJson("", lookup));
        assertNull(ResourceWebContracts.contractorJson("abc", lookup));
        assertEquals(20L, ResourceWebContracts.contractorJson(" 20 ", lookup).get("id"));
        assertEquals(20L, ResourceWebContracts.contractorJson("20:Value;21", lookup).get("id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void rangingAndTextAttributes() {
        Map<String, Object> answer = answer("de");
        Map<String, Object> net = object(answer, NET.getId());
        Map<String, Object> demand = attribute(net, "Demand Charge");
        assertEquals("ranging", demand.get("type"));
        List<Map<String, Object>> ranges = (List<Map<String, Object>>) demand.get("value");
        assertEquals(2, ranges.size());
        assertEquals(0.0, ranges.get(0).get("from"));
        assertEquals(100.0, ranges.get(0).get("to"));
        assertEquals(12.5, ranges.get(0).get("value"));
        assertEquals(10.0, ranges.get(1).get("value"));
        assertEquals("€/kW", demand.get("unit"));
        assertEquals(true, demand.get("history"));

        Map<String, Object> code = attribute(net, "Energy Grid Operator Code Number");
        assertEquals("text", code.get("type"));
        assertEquals("NB-4711", code.get("value"));
        assertNull(code.get("unit"));

        /* a number attribute holding text: the text */
        assertEquals("abc", attribute(net, "Net Cost").get("value"));

        Map<String, Object> nord = object(answer, NORD.getId());
        assertEquals("K-2", attribute(nord, "Customer Number").get("value"));
        assertEquals(true, attribute(nord, "Customer Number").get("history"));
        /* no passwords */
        assertNull(attribute(nord, "Password"));

        assertEquals(19.0, attribute(object(answer, TAXES.getId()), "VAT Electricity").get("value"));

        assertTrue(ResourceWebContracts.ranging("{\"rangingValues\": [").isEmpty());
        assertTrue(ResourceWebContracts.ranging("[1,2]").isEmpty());
        assertTrue(ResourceWebContracts.ranging("").isEmpty());
        assertTrue(ResourceWebContracts.ranging(null).isEmpty());
        assertTrue(ResourceWebContracts.ranging("{\"rangingValues\": []}").isEmpty());
        assertEquals(1, ResourceWebContracts.ranging("{\"rangingValues\":[{\"from\":0,\"to\":1,\"value\":2}],\"x\":1}").size());
    }

    // ---------------------------------------------------------------- configurations

    @SuppressWarnings("unchecked")
    private static Map<String, Object> configuration(Map<String, Object> answer, long id) {
        for (Map<String, Object> c : (List<Map<String, Object>>) answer.get("configurations")) {
            if (((Long) c.get("id")) == id) return c;
        }
        return null;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void configurationFields() throws Exception {
        Map<String, Object> answer = answer("de");
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> c : (List<Map<String, Object>>) answer.get("configurations")) ids.add(c.get("id"));
        /* sorted by name, the hidden one left out */
        assertEquals(Arrays.asList(51L, 52L, 53L, 50L), ids);

        Map<String, Object> config = configuration(answer, CONFIG.getId());
        assertEquals("Strom Musterwerk", config.get("name"));
        assertEquals("Energieverträge / Abrechnung", config.get("path"));
        assertEquals("Musterwerk", ((Map<String, Object>) config.get("site")).get("name"));
        assertEquals("V-1001", config.get("contractNumber"));
        assertEquals("ELECTRICITY", config.get("contractType"));
        assertEquals("DE0000000000000000000000000000001", config.get("marketLocation"));
        assertEquals("2025-12-01", config.get("contractDate"));
        assertEquals("2026-01-01", config.get("firstRate"));
        assertEquals("2027-09-30", config.get("periodOfNotice"));
        assertEquals("2026-01-01", config.get("start"));
        assertEquals("2027-12-31", config.get("end"));
        assertEquals("ELECTRICITY", config.get("energyType"));

        /* the calculation inputs/outputs are never given */
        String text = new ObjectMapper().writeValueAsString(answer);
        assertFalse(text.contains("selectedInputs"));
        assertFalse(text.contains("linkedOutputs"));
        assertFalse(text.contains("4712"));
        assertFalse(text.contains("templateSelection"));
        /* nor files of contract objects */
        assertFalse(text.contains("vertrag.pdf"));
    }

    @Test
    public void configurationsWithBrokenOrMissingTemplate() {
        Map<String, Object> answer = answer("de");
        for (JsonObject o : Arrays.asList(CONFIG_BROKEN, CONFIG_EMPTY)) {
            Map<String, Object> config = configuration(answer, o.getId());
            for (String key : new String[]{"contractNumber", "contractType", "marketLocation", "contractDate", "firstRate",
                    "periodOfNotice", "start", "end", "energyType"}) {
                assertTrue(key, config.containsKey(key));
                assertNull(o.getName() + " " + key, config.get(key));
            }
        }
        Map<String, Object> other = configuration(answer, CONFIG_OTHER.getId());
        assertNull(other.get("contractNumber"));
        assertEquals("WATER", other.get("contractType"));
        /* unknown energy type */
        assertNull(other.get("energyType"));
        /* no date: as stored */
        assertEquals("3 Monate", other.get("periodOfNotice"));
        assertNull(other.get("marketLocation"));
        assertNull(other.get("start"));
        assertNull(configuration(answer, CONFIG_HIDDEN.getId()));
    }

    @Test
    public void templateNotAnObjectOrUnreadable() {
        TestStore store = new TestStore();
        store.file(CONFIG.getId(), "Template File", "template.json", "[1, 2]");
        assertNull(ResourceWebContracts.template(CONFIG.getId(), store));
        TestStore throwing = new TestStore() {
            @Override
            public byte[] file(long id, String attribute, JsonSample sample) {
                throw new IllegalStateException("disk");
            }
        };
        throwing.add(CONFIG.getId(), "Template File", null, "template.json");
        assertNull(ResourceWebContracts.template(CONFIG.getId(), throwing));
        /* no sample: no file is read */
        TestStore empty = new TestStore();
        assertNull(ResourceWebContracts.template(CONFIG.getId(), empty));
        assertFalse(empty.calls.toString().contains("file"));
    }

    // ---------------------------------------------------------------- history

    @Test
    @SuppressWarnings("unchecked")
    public void historyNumberNewestFirst() throws Exception {
        TestStore store = store();
        Map<String, Object> history = ResourceWebContracts.historyJson(lookup(), classes(), store, POWER.getId(),
                "Energy Price HT", 100);
        assertEquals("€/kWh", history.get("unit"));
        assertEquals("number", history.get("type"));
        List<Map<String, Object>> samples = (List<Map<String, Object>>) history.get("samples");
        assertEquals(3, samples.size());
        assertEquals(0.28, samples.get(0).get("value"));
        assertEquals(TS_NEW, samples.get(0).get("timestamp"));
        assertEquals(0.25, samples.get(2).get("value"));
        assertEquals(Collections.singletonList("latest 10/Energy Price HT/100"), filter(store.calls, "latest"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void historyRanging() throws Exception {
        Map<String, Object> history = ResourceWebContracts.historyJson(lookup(), classes(), store(), NET.getId(),
                "Demand Charge", 100);
        assertEquals("ranging", history.get("type"));
        List<Map<String, Object>> samples = (List<Map<String, Object>>) history.get("samples");
        assertEquals(2, samples.size());
        assertEquals(2, ((List<Object>) samples.get(0).get("value")).size());
        assertEquals(TS_OLD, samples.get(1).get("timestamp"));
    }

    private static List<String> filter(List<String> calls, String prefix) {
        List<String> result = new ArrayList<>();
        for (String call : calls) if (call.startsWith(prefix)) result.add(call);
        return result;
    }

    @Test
    public void historyLimitBounds() throws Exception {
        assertEquals(100, ResourceWebContracts.historyLimit(0));
        assertEquals(100, ResourceWebContracts.historyLimit(-5));
        assertEquals(1, ResourceWebContracts.historyLimit(1));
        assertEquals(500, ResourceWebContracts.historyLimit(500));
        assertEquals(500, ResourceWebContracts.historyLimit(100000));
        TestStore store = store();
        ResourceWebContracts.historyJson(lookup(), classes(), store, POWER.getId(), "Energy Price HT", 9999);
        ResourceWebContracts.historyJson(lookup(), classes(), store, POWER.getId(), "Energy Price HT", 2);
        assertEquals(Arrays.asList("latest 10/Energy Price HT/500", "latest 10/Energy Price HT/2"), filter(store.calls, "latest"));
    }

    @Test
    public void historyNotFound() throws Exception {
        TestStore store = store();
        /* not readable, recycle bin, configuration, folder, unknown */
        for (long id : new long[]{SUPPLIER_HIDDEN.getId(), SUPPLIER_BIN.getId(), CONFIG.getId(), CONTRACTS.getId(), 4711L}) {
            assertNull(ResourceWebContracts.historyJson(lookup(), classes(), store, id, "Energy Price HT", 10));
        }
        /* unknown, text, contractor, file, missing attribute */
        for (String attribute : new String[]{"Unknown", "Contractor", "Contract File", null, "energy price ht"}) {
            assertNull(String.valueOf(attribute), ResourceWebContracts.historyJson(lookup(), classes(), store,
                    POWER.getId(), attribute, 10));
        }
        assertNull(ResourceWebContracts.historyJson(lookup(), classes(), store, NET.getId(),
                "Energy Grid Operator Code Number", 10));
        assertNull(ResourceWebContracts.historyJson(lookup(), classes(), store, NORD.getId(), "Password", 10));
        assertTrue(filter(store.calls, "latest").isEmpty());
        assertTrue(filter(store.calls, "file").isEmpty());
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void moduleCounts() {
        /* suppliers 10, 11, 14; contractors 20; grid 30; dues 40; configurations 50, 51, 52, 53 */
        Map<String, Object> counts = ResourceWebContracts.moduleCounts(readable(), classes());
        assertEquals(10, counts.get("contracts"));
        assertEquals(1, counts.get("invoices"));
        /* without class cache only the base classes count */
        Map<String, Object> plain = ResourceWebContracts.moduleCounts(readable(),
                new ResourceWebRegisters.Classes(Collections.emptyMap()));
        assertEquals(7, plain.get("contracts"));
        Map<String, Object> modules = ResourceWebModules.moduleCounts(Arrays.asList(SITE, GENERAL, SUPPLIER_BIN, INVOICE));
        assertEquals(1, modules.get("contracts"));
        assertEquals(1, modules.get("invoices"));
    }

    @Test
    public void answerIsSerializable() throws Exception {
        String text = new ObjectMapper().writeValueAsString(answer("de"));
        assertTrue(text.contains("\"kind\":\"suppliers\""));
        assertTrue(text.contains("\"label\":\"Arbeitspreis HT\""));
    }
}
