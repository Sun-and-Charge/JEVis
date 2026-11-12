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
import org.jevis.commons.trc.RCTemplate;
import org.jevis.commons.trc.TemplateInput;
import org.jevis.commons.trc.TemplateOutput;
import org.jevis.commons.trc.TrcResult;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebCostsTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 11, 11, 10, 0, ZONE);
    private static final String CONFIGURATION = ResourceWebCosts.CLASS_CONFIGURATION;
    private static final String TEMPLATE = ResourceWebCosts.CLASS_TEMPLATE;
    private static final String SUPPLIER = "Energy Supplier";

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 > 3 "Abrechnung" > configurations 50 (template 60), 51 (hidden template 61),
       52 (missing template 99), hidden 53, 54 (bound to the hidden clean data 74); 2 > 4 "Vorlagen" > templates 60,
       hidden 61, recycle bin 62; 2 > 5 "Daten" > data 70 > clean 71, data 72 > clean 73, data 75 > hidden clean 74;
       2 > supplier 80 */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Werk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject ACCOUNTING = obj(3, "Abrechnung", "Accounting Configuration Directory", 2);
    private static final JsonObject TEMPLATES = obj(4, "Vorlagen", "Result Calculation Template Directory", 2);
    private static final JsonObject DATA_DIR = obj(5, "Daten", "Data Directory", 2);
    private static final JsonObject CONFIG = obj(50, "Strom", CONFIGURATION, 3);
    private static final JsonObject CONFIG_HIDDEN_TEMPLATE = obj(51, "Ohne Vorlage", CONFIGURATION, 3);
    private static final JsonObject CONFIG_MISSING_TEMPLATE = obj(52, "Fehlende Vorlage", CONFIGURATION, 3);
    private static final JsonObject CONFIG_HIDDEN = obj(53, "Verborgen", CONFIGURATION, 3);
    private static final JsonObject CONFIG_HIDDEN_INPUT = obj(54, "Eingabe verborgen", CONFIGURATION, 3);
    private static final JsonObject COSTS = obj(60, "Kosten", TEMPLATE, 4);
    private static final JsonObject TEMPLATE_HIDDEN = obj(61, "Geheim", TEMPLATE, 4);
    private static final JsonObject TEMPLATE_BIN = obj(62, "Alt", TEMPLATE, 4);
    private static final JsonObject DATA_A = obj(70, "Zähler A", "Data", 5);
    private static final JsonObject CLEAN_A = obj(71, "Bereinigt", ResourceWebCosts.CLASS_CLEAN_DATA, 70);
    private static final JsonObject DATA_B = obj(72, "Zähler B", "Data", 5);
    private static final JsonObject CLEAN_B = obj(73, "Bereinigt", ResourceWebCosts.CLASS_CLEAN_DATA, 72);
    private static final JsonObject DATA_C = obj(75, "Zähler C", "Data", 5);
    private static final JsonObject CLEAN_HIDDEN = obj(74, "Bereinigt", ResourceWebCosts.CLASS_CLEAN_DATA, 75);
    private static final JsonObject SUPPLY = obj(80, "Lieferant", SUPPLIER, 2);

    static {
        TEMPLATE_BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, ACCOUNTING, TEMPLATES, DATA_DIR, CONFIG,
            CONFIG_HIDDEN_TEMPLATE, CONFIG_MISSING_TEMPLATE, CONFIG_HIDDEN, CONFIG_HIDDEN_INPUT, COSTS, TEMPLATE_HIDDEN,
            TEMPLATE_BIN, DATA_A, CLEAN_A, DATA_B, CLEAN_B, DATA_C, CLEAN_HIDDEN, SUPPLY);
    private static final List<JsonObject> HIDDEN = Arrays.asList(CONFIG_HIDDEN, TEMPLATE_HIDDEN, CLEAN_HIDDEN);

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

    private static ResourceWebRegisters.Classes classes() {
        Map<String, JsonJEVisClass> map = new HashMap<>();
        for (String name : Arrays.asList(ResourceWebNavigation.CLASS_BUILDING, CONFIGURATION, TEMPLATE, "Data",
                ResourceWebCosts.CLASS_CLEAN_DATA, SUPPLIER)) {
            JsonJEVisClass jclass = new JsonJEVisClass();
            jclass.setName(name);
            jclass.setRelationships(new ArrayList<>());
            jclass.setTypes(new ArrayList<>());
            map.put(name, jclass);
        }
        return new ResourceWebRegisters.Classes(map);
    }

    // ---------------------------------------------------------------- template files

    private static final String TEMPLATE_JSON = "{\"templateInputs\":["
            + "{\"objectClass\":\"Clean Data\",\"id\":\"in-1\",\"quantity\":true,\"attributeName\":\"Value\","
            + "\"variableName\":\"consumption\",\"variableType\":\"SUM\",\"filter\":null,\"group\":false},"
            + "{\"objectClass\":\"Energy Supplier\",\"id\":\"in-2\",\"attributeName\":\"Energy Price\","
            + "\"variableName\":\"price\",\"variableType\":\"NON_PERIODIC\",\"group\":null},"
            + "{\"objectClass\":\"Energy Supplier\",\"id\":\"in-3\",\"attributeName\":\"Tax\","
            + "\"variableName\":\"tax\",\"variableType\":\"NON_PERIODIC\",\"group\":true}],"
            + "\"templateOutputs\":["
            + "{\"id\":\"out-1\",\"name\":\"Energiekosten\",\"nameBold\":true,\"unit\":\"€\",\"showAnalysisLink\":true,"
            + "\"tooltip\":\"Hinweis\",\"showTooltip\":true,\"resultBold\":true,\"column\":0,\"row\":0,\"colSpan\":2,\"rowSpan\":1},"
            + "{\"id\":\"out-2\",\"column\":0,\"row\":1,\"colSpan\":3,\"rowSpan\":1,\"separator\":true},"
            + "{\"id\":\"out-3\",\"name\":\"Hinweistext\",\"column\":0,\"row\":2,\"colSpan\":1,\"rowSpan\":1},"
            + "{\"id\":\"out-4\",\"name\":\"Tarif\",\"column\":1,\"row\":2,\"colSpan\":1,\"rowSpan\":1},"
            + "{\"id\":\"out-5\",\"column\":2,\"row\":2,\"colSpan\":1,\"rowSpan\":2,\"separator\":true}],"
            + "\"templateFormulas\":[{\"id\":\"f-1\",\"name\":\"Kosten\",\"formula\":\"consumption * price\","
            + "\"output\":\"out-1\",\"inputIds\":[\"in-1\",\"in-2\"]}],"
            + "\"intervalSelectorConfiguration\":{\"P1D\":false,\"P1M\":true,\"P1Y\":true},\"type\":\"SimpleDataHandler\"}";

    private static String configuration(long template, long consumption) {
        return "{\"selectedInputs\":["
                + "{\"objectClass\":\"Clean Data\",\"id\":\"in-1\",\"variableName\":\"consumption\",\"variableType\":\"SUM\","
                + "\"group\":false,\"objectID\":" + consumption + "},"
                + "{\"objectClass\":\"Energy Supplier\",\"id\":\"in-2\",\"variableName\":\"price\",\"variableType\":\"NON_PERIODIC\","
                + "\"objectID\":80},"
                + "{\"objectClass\":\"Energy Supplier\",\"id\":\"in-3\",\"variableName\":\"tax\",\"variableType\":\"NON_PERIODIC\","
                + "\"group\":true,\"objectID\":-1}],"
                + "\"linkedOutputs\":[],\"templateSelection\":" + template + ",\"type\":\"SimpleDataHandler\","
                + "\"contractNumber\":\"V-1\",\"contractType\":\"ELECTRICITY\",\"marketLocationNumber\":\"ML-1\","
                + "\"contractStart\":\"2026-01-01T00:00:00.000+01:00\",\"contractEnd\":\"2027-12-31T00:00:00.000+01:00\"}";
    }

    private static ResourceWebContractsTest.TestStore store() {
        ResourceWebContractsTest.TestStore store = new ResourceWebContractsTest.TestStore();
        store.file(COSTS.getId(), "Template File", "template.json", TEMPLATE_JSON);
        store.file(TEMPLATE_HIDDEN.getId(), "Template File", "template.json", TEMPLATE_JSON);
        store.file(CONFIG.getId(), "Template File", "configuration.json", configuration(60, 71));
        store.file(CONFIG_HIDDEN_TEMPLATE.getId(), "Template File", "configuration.json", configuration(61, 71));
        store.file(CONFIG_MISSING_TEMPLATE.getId(), "Template File", "configuration.json", configuration(99, 71));
        store.file(CONFIG_HIDDEN_INPUT.getId(), "Template File", "configuration.json", configuration(60, 74));
        return store;
    }

    // ---------------------------------------------------------------- calculation fake

    /**
     * Answers with one cell per output (value 12.5 for formulas) and remembers the request.
     */
    private static final class FakeCalculator implements ResourceWebCosts.Calculator {
        RCTemplate template;
        DateTime from;
        DateTime to;

        @Override
        public TrcResult calculate(RCTemplate template, DateTime from, DateTime to, DateTimeZone zone) {
            this.template = template;
            this.from = from;
            this.to = to;
            TrcResult result = new TrcResult(from, to);
            for (TemplateOutput output : template.getTemplateOutputs()) {
                TrcResult.Kind kind = Boolean.TRUE.equals(output.getSeparator()) ? TrcResult.Kind.SEPARATOR
                        : "out-1".equals(output.getId()) ? TrcResult.Kind.VALUE
                        : "out-4".equals(output.getId()) ? TrcResult.Kind.TEXT : TrcResult.Kind.LABEL;
                TrcResult.Cell cell = new TrcResult.Cell(output, kind);
                if (kind == TrcResult.Kind.VALUE) {
                    cell.setValue(12.5);
                    Long object = template.getTemplateInputs().get(0).getObjectID();
                    cell.setAnalysis(object, "Value", from, to);
                }
                if (kind == TrcResult.Kind.TEXT) cell.setText("Tarif A");
                result.getCells().add(cell);
            }
            result.addWarning(new TrcResult.Warning(TrcResult.WarningType.INPUT_NO_VALUES, "Kosten", "price"));
            return result;
        }

        Long object(String inputId) {
            for (TemplateInput input : template.getTemplateInputs()) {
                if (inputId.equals(input.getId())) return input.getObjectID();
            }
            return null;
        }
    }

    private static Map<String, Object> result(long id, String body, FakeCalculator calculator) throws Exception {
        return ResourceWebCosts.resultJson(lookup(), classes(), store(), id, body, "de", NOW, calculator);
    }

    private static int status(long id, String body) {
        try {
            result(id, body, new FakeCalculator());
            return 200;
        } catch (ResourceWebCosts.WebException e) {
            return e.status;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object map, String key) {
        return (T) ((Map<String, Object>) map).get(key);
    }

    // ---------------------------------------------------------------- list

    @Test
    public void listsReadableConfigurationsAndTemplates() {
        Map<String, Object> answer = ResourceWebCosts.listJson(lookup(), classes(), store());
        List<Map<String, Object>> configurations = get(answer, "configurations");
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> c : configurations) ids.add(c.get("id"));
        /* sorted by site, path, name */
        assertEquals(Arrays.asList(54L, 52L, 51L, 50L), ids);
        Map<String, Object> config = configurations.get(3);
        assertEquals("Strom", config.get("name"));
        assertEquals("Abrechnung", config.get("path"));
        assertEquals("Werk", get(config.get("site"), "name"));
        assertEquals((Object) 60L, (Object) get(config.get("template"), "id"));
        assertEquals("Kosten", get(config.get("template"), "name"));
        assertEquals(true, get(config.get("template"), "readable"));
        assertEquals(false, get(configurations.get(2).get("template"), "readable"));
        assertNull(get(configurations.get(2).get("template"), "name"));
        assertEquals(false, get(configurations.get(1).get("template"), "readable"));

        List<Map<String, Object>> templates = get(answer, "templates");
        assertEquals(1, templates.size());
        assertEquals(60L, templates.get(0).get("id"));
        assertEquals("Vorlagen", templates.get(0).get("path"));
    }

    // ---------------------------------------------------------------- definition

    @Test
    public void definitionOfAConfiguration() throws Exception {
        Map<String, Object> answer = ResourceWebCosts.definitionJson(lookup(), classes(), store(), 50, "de");
        assertEquals("configuration", answer.get("kind"));
        assertEquals("Strom", answer.get("name"));
        assertEquals("Kosten", answer.get("templateName"));
        List<Map<String, Object>> timeFrames = get(answer, "timeFrames");
        assertEquals(2, timeFrames.size());
        assertEquals("P1M", timeFrames.get(0).get("id"));
        assertEquals("Monat", timeFrames.get(0).get("name"));
        assertEquals("P1Y", timeFrames.get(1).get("id"));
        assertEquals("P1M", answer.get("defaultTimeFrame"));
        Map<String, Object> contract = get(answer, "contract");
        assertEquals("V-1", contract.get("contractNumber"));
        assertEquals("ML-1", contract.get("marketLocation"));
        assertEquals("ELECTRICITY", contract.get("energyType"));
        assertEquals("2026-01-01", contract.get("start"));
        assertFalse(contract.containsKey("id"));

        List<Map<String, Object>> inputs = get(answer, "inputs");
        assertEquals(2, inputs.size());
        Map<String, Object> consumption = inputs.get(0);
        assertEquals("in-1", consumption.get("id"));
        assertEquals("consumption", consumption.get("label"));
        assertEquals("Clean Data", consumption.get("objectClass"));
        assertEquals(false, consumption.get("selectable"));
        assertEquals((Object) 71L, (Object) get(consumption.get("selected"), "id"));
        assertEquals("Zähler A \\ Bereinigt", get(consumption.get("selected"), "name"));
        assertEquals(true, get(consumption.get("selected"), "readable"));
        assertTrue(((List<?>) consumption.get("options")).isEmpty());
        /* price and tax share the selector of their class */
        Map<String, Object> supplier = inputs.get(1);
        assertEquals("in-2", supplier.get("id"));
        assertEquals("price, tax", supplier.get("label"));
        assertEquals((Object) 80L, (Object) get(supplier.get("selected"), "id"));

        assertEquals((Object) 4, (Object) get(answer.get("grid"), "rows"));
        assertEquals((Object) 3, (Object) get(answer.get("grid"), "columns"));
        new ObjectMapper().writeValueAsString(answer);
    }

    @Test
    public void unreadableBoundObjectIsShownWithoutName() throws Exception {
        Map<String, Object> answer = ResourceWebCosts.definitionJson(lookup(), classes(), store(), 54, "en");
        Map<String, Object> consumption = ((List<Map<String, Object>>) get(answer, "inputs")).get(0);
        Map<String, Object> selected = get(consumption, "selected");
        assertEquals(74L, selected.get("id"));
        assertNull(selected.get("name"));
        assertEquals(false, selected.get("readable"));
    }

    @Test
    public void definitionOfATemplateHasReadableOptions() throws Exception {
        Map<String, Object> answer = ResourceWebCosts.definitionJson(lookup(), classes(), store(), 60, "en");
        assertEquals("template", answer.get("kind"));
        assertEquals("Kosten", answer.get("templateName"));
        assertNull(answer.get("contract"));
        assertEquals("Month", ((List<Map<String, Object>>) get(answer, "timeFrames")).get(0).get("name"));
        Map<String, Object> consumption = ((List<Map<String, Object>>) get(answer, "inputs")).get(0);
        assertEquals(true, consumption.get("selectable"));
        List<Map<String, Object>> options = get(consumption, "options");
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> option : options) ids.add(option.get("id"));
        assertEquals("only readable clean data", Arrays.asList(71L, 73L), ids);
        assertEquals("Zähler B \\ Bereinigt", options.get(1).get("name"));
        assertEquals("first option as in the Control Center", (Object) 71L, (Object) get(consumption.get("selected"), "id"));
    }

    @Test
    public void optionsAreFiltered() {
        /* case-insensitive, on the name with the data row */
        List<JsonObject> options = ResourceWebCosts.options(lookup(), ResourceWebCosts.CLASS_CLEAN_DATA, "A");
        assertEquals(1, options.size());
        assertEquals(71L, options.get(0).getId());
        /* every word of the filter must be contained */
        assertEquals(2, ResourceWebCosts.options(lookup(), ResourceWebCosts.CLASS_CLEAN_DATA, "bereinigt zähler").size());
        assertTrue(ResourceWebCosts.options(lookup(), ResourceWebCosts.CLASS_CLEAN_DATA, "bereinigt x").isEmpty());
        assertTrue(ResourceWebCosts.options(lookup(), ResourceWebCosts.CLASS_CLEAN_DATA, "zähler x").isEmpty());
    }

    @Test
    public void notFoundAndConflict() throws Exception {
        for (long id : new long[]{53, 61, 62, 999, 80}) {
            try {
                ResourceWebCosts.definitionJson(lookup(), classes(), store(), id, "de");
                fail(String.valueOf(id));
            } catch (ResourceWebCosts.WebException e) {
                assertEquals(String.valueOf(id), 404, e.status);
            }
        }
        for (long id : new long[]{51, 52}) {
            try {
                ResourceWebCosts.definitionJson(lookup(), classes(), store(), id, "de");
                fail(String.valueOf(id));
            } catch (ResourceWebCosts.WebException e) {
                assertEquals(409, e.status);
            }
        }
        assertEquals(409, status(51, "{\"timeFrame\":\"P1M\"}"));
        assertEquals(404, status(53, "{\"timeFrame\":\"P1M\"}"));
    }

    // ---------------------------------------------------------------- result

    @Test
    public void resultOfAConfiguration() throws Exception {
        FakeCalculator calculator = new FakeCalculator();
        Map<String, Object> answer = result(50, "{\"timeFrame\":\"P1M\",\"timeZone\":\"Europe/Berlin\"}", calculator);
        assertEquals("2026-11-01T00:00:00.000+01:00", answer.get("from"));
        assertEquals("2026-11-30T23:59:59.999+01:00", answer.get("to"));
        /* bound as configured, the grouped input without object gets the object of its group */
        assertEquals(Long.valueOf(71), calculator.object("in-1"));
        assertEquals(Long.valueOf(80), calculator.object("in-2"));
        assertEquals(Long.valueOf(80), calculator.object("in-3"));

        assertEquals(4, answer.get("rows"));
        assertEquals(3, answer.get("columns"));
        List<Map<String, Object>> cells = get(answer, "cells");
        assertEquals(5, cells.size());
        Map<String, Object> value = cells.get(0);
        assertEquals("value", value.get("kind"));
        assertEquals(2, value.get("columnSpan"));
        assertEquals("Energiekosten", value.get("name"));
        assertEquals(true, value.get("nameBold"));
        assertEquals(12.5, value.get("value"));
        assertNull(value.get("text"));
        assertEquals("€", value.get("unit"));
        assertEquals(true, value.get("resultBold"));
        assertEquals("Hinweis", value.get("tooltip"));
        assertEquals((Object) 71L, (Object) get(value.get("analysis"), "objectId"));
        assertEquals("Value", get(value.get("analysis"), "attribute"));
        assertEquals("2026-11-01T00:00:00.000+01:00", get(value.get("analysis"), "from"));

        Map<String, Object> separator = cells.get(1);
        assertEquals("separator", separator.get("kind"));
        assertEquals("horizontal", separator.get("orientation"));
        assertFalse(separator.containsKey("name"));
        assertEquals("label", cells.get(2).get("kind"));
        assertEquals("Hinweistext", cells.get(2).get("name"));
        assertNull(cells.get(2).get("value"));
        assertEquals("text", cells.get(3).get("kind"));
        assertEquals("Tarif A", cells.get(3).get("text"));
        assertEquals("vertical", cells.get(4).get("orientation"));

        assertEquals(Collections.singletonList("Formel \"Kosten\": Eingabe \"price\" hat keine Werte im Zeitraum"),
                answer.get("warnings"));
        new ObjectMapper().writeValueAsString(answer);
    }

    @Test
    public void configurationsAcceptMissingOrEmptySelections() {
        assertEquals(200, status(50, "{\"timeFrame\":\"P1Y\"}"));
        assertEquals(200, status(50, "{\"timeFrame\":\"P1Y\",\"selections\":null}"));
        assertEquals(200, status(50, "{\"timeFrame\":\"P1Y\",\"selections\":{}}"));
    }

    @Test
    public void analysisLinkOnlyToReadableObjects() throws Exception {
        Map<String, Object> answer = result(54, "{\"timeFrame\":\"P1M\"}", new FakeCalculator());
        assertNull(((List<Map<String, Object>>) get(answer, "cells")).get(0).get("analysis"));
    }

    @Test
    public void resultOfATemplateWithSelection() throws Exception {
        FakeCalculator calculator = new FakeCalculator();
        Map<String, Object> answer = result(60, "{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-01-01T00:00:00\","
                + "\"to\":\"2026-03-31T23:59:59\",\"timeZone\":\"Europe/Berlin\",\"selections\":{\"in-1\":73}}", calculator);
        assertEquals("2026-01-01T00:00:00.000+01:00", answer.get("from"));
        assertEquals(Long.valueOf(73), calculator.object("in-1"));
        assertEquals("missing selections use the default", Long.valueOf(80), calculator.object("in-2"));
        assertEquals(Long.valueOf(80), calculator.object("in-3"));
        assertEquals(new DateTime(2026, 3, 31, 23, 59, 59, ZONE).getMillis(), calculator.to.getMillis());
    }

    @Test
    public void badRequests() {
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"selections\":{\"in-1\":74}}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"selections\":{\"in-1\":80}}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"selections\":{\"in-3\":80}}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"selections\":{\"in-1\":\"x\"}}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"selections\":[]}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1D\"}"));
        assertEquals(400, status(60, "{}"));
        assertEquals(400, status(60, "not json"));
        assertEquals(400, status(60, "{\"timeFrame\":\"P1M\",\"timeZone\":\"Mars/Base\"}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-01-01T00:00:00\"}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"CUSTOM\",\"from\":\"2026-02-01T00:00:00\",\"to\":\"2026-01-01T00:00:00\"}"));
        assertEquals(400, status(60, "{\"timeFrame\":\"CUSTOM\",\"from\":\"2010-01-01T00:00:00\",\"to\":\"2026-01-01T00:00:00\"}"));
        assertEquals(200, status(60, "{\"timeFrame\":\"P1M\",\"selections\":{\"in-1\":73,\"in-2\":80}}"));
    }

    @Test
    public void warningsInEnglish() {
        assertEquals("Formula \"Kosten\" could not be calculated", ResourceWebCosts.warningText(
                new TrcResult.Warning(TrcResult.WarningType.FORMULA_FAILED, "Kosten", null), "en"));
        assertEquals("Formula \"Kosten\": input \"price\" is not readable", ResourceWebCosts.warningText(
                new TrcResult.Warning(TrcResult.WarningType.INPUT_NOT_READABLE, "Kosten", "price"), "fr"));
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void moduleCounts() {
        /* configurations 50, 51, 52, 54 and template 60; not the hidden ones nor the recycle bin */
        assertEquals(5, ResourceWebCosts.moduleCounts(readable(), classes()).get("costs"));
        List<JsonObject> withBin = new ArrayList<>(readable());
        withBin.add(TEMPLATE_BIN);
        assertEquals(5, ResourceWebCosts.moduleCounts(withBin, classes()).get("costs"));
        assertTrue(ResourceWebModules.moduleCounts(Arrays.asList(SITE, CONFIG, COSTS)).containsKey("costs"));
        assertEquals(2, ResourceWebModules.moduleCounts(Arrays.asList(SITE, CONFIG, COSTS)).get("costs"));
    }
}
