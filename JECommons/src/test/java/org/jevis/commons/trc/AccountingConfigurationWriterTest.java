/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.trc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class AccountingConfigurationWriterTest {

    private static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String TEMPLATE = "{\"templateInputs\":["
            + "{\"objectClass\":\"Clean Data\",\"id\":\"a\",\"quantity\":true,\"attributeName\":\"Value\","
            + "\"variableName\":\"verbrauch\",\"variableType\":\"SUM\",\"filter\":\"Bezug\",\"group\":false,"
            + "\"dependency\":null,\"timeRestrictionEnabled\":false},"
            + "{\"objectClass\":\"Energy Supplier\",\"id\":\"b\",\"attributeName\":\"Energy Price\","
            + "\"variableName\":\"preis\",\"variableType\":\"NON_PERIODIC\",\"group\":true}],"
            + "\"templateOutputs\":[],\"templateFormulas\":[]}";

    private static final String STORED = "{\"selectedInputs\":["
            + "{\"objectClass\":\"Clean Data\",\"id\":\"a\",\"attributeName\":\"Value\",\"variableName\":\"verbrauch\","
            + "\"variableType\":\"SUM\",\"filter\":\"Bezug\",\"group\":false,\"objectID\":null,\"objectKey\":\"@a\"},"
            + "{\"objectClass\":\"Old\",\"id\":\"old\",\"variableType\":\"SUM\",\"objectID\":7}],"
            + "\"linkedOutputs\":[{\"id\":\"o\",\"link\":true,\"target\":\"5:Value\"}],\"templateSelection\":3,"
            + "\"type\":\"SimpleDataHandler\",\"contractNumber\":\"V-1\",\"contractStart\":\"2026-01-01T00:00:00.000+01:00\","
            + "\"extra\":{\"x\":1}}";

    private static RCTemplate template() {
        return TemplateHandler.parse(TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }

    private static ObjectNode stored() throws Exception {
        return (ObjectNode) MAPPER.readTree(STORED);
    }

    @Test
    public void datesAreMidnightInTheZoneLikeTheDatePickers() {
        assertEquals("2026-01-01T00:00:00.000+01:00", AccountingConfigurationWriter.dateTime(new LocalDate(2026, 1, 1), BERLIN));
        assertEquals("2026-07-01T00:00:00.000+02:00", AccountingConfigurationWriter.dateTime(new LocalDate(2026, 7, 1), BERLIN));
        assertEquals(new DateTime(2026, 1, 1, 0, 0, 0, BERLIN).toString(),
                AccountingConfigurationWriter.dateTime(new LocalDate(2026, 1, 1), BERLIN));
        assertNull(AccountingConfigurationWriter.dateTime(null, BERLIN));
    }

    @Test
    public void fileNameLikeTheAccountingPlugin() {
        DateTime time = new DateTime(2026, 11, 16, 9, 5, 42, BERLIN);
        assertEquals("StromWerkSüd2027_202611160905.json", AccountingConfigurationWriter.fileName("Strom / Werk Süd 2027", time));
        assertEquals("AccountingConfiguration_202611160905.json", AccountingConfigurationWriter.fileName(" - ", time));
    }

    @Test
    public void newConfigurationHasTheKeysOfToJsonNode() {
        Map<String, Long> objects = new HashMap<>();
        objects.put("b", 12L);
        ObjectNode tree = new AccountingConfigurationWriter().patch(null, 3, template(), objects,
                Collections.singletonMap("contractType", "GAS"));
        List<String> keys = new ArrayList<>();
        tree.fieldNames().forEachRemaining(keys::add);
        assertEquals(Arrays.asList("selectedInputs", "linkedOutputs", "templateSelection", "type", "contractNumber",
                "contractType", "marketLocationNumber", "contractDate", "firstRate", "periodOfNotice", "contractStart",
                "contractEnd"), keys);
        assertEquals(3, tree.get("templateSelection").asLong());
        assertEquals("GAS", tree.get("contractType").asText());
        assertTrue(tree.get("contractNumber").isNull());
        JsonNode inputs = tree.get("selectedInputs");
        assertEquals(2, inputs.size());
        assertEquals(-1, inputs.get(0).get("objectID").asLong());
        assertEquals(12, inputs.get(1).get("objectID").asLong());
        List<String> fields = new ArrayList<>();
        inputs.get(0).fieldNames().forEachRemaining(fields::add);
        assertEquals(Arrays.asList("objectClass", "id", "attributeName", "variableName", "variableType", "filter",
                "group", "objectID"), fields);
        /* the old Control Center reads it */
        AccountingTemplateHandler handler = new AccountingTemplateHandler();
        handler.jsonToModel(tree);
        assertEquals(Long.valueOf(12), handler.getSelectionTemplate().getSelectedInputs().get(1).getObjectID());
    }

    @Test
    public void patchKeepsWhatItDoesNotKnow() throws Exception {
        ObjectNode stored = stored();
        Map<String, Long> objects = new HashMap<>();
        objects.put("b", 12L);
        Map<String, String> contract = new HashMap<>();
        contract.put("contractNumber", "V-2");
        ObjectNode tree = new AccountingConfigurationWriter().patch(stored, 3, template(), objects, contract);
        assertEquals(stored(), stored);
        JsonNode inputs = tree.get("selectedInputs");
        /* the stored entries (unknown keys, objectID null untouched, entries of other inputs), then the new input */
        assertEquals(3, inputs.size());
        assertTrue(inputs.get(0).get("objectID").isNull());
        assertEquals("@a", inputs.get(0).get("objectKey").asText());
        assertEquals("old", inputs.get(1).get("id").asText());
        assertEquals("b", inputs.get(2).get("id").asText());
        assertEquals(12, inputs.get(2).get("objectID").asLong());
        assertEquals(stored.get("linkedOutputs"), tree.get("linkedOutputs"));
        assertEquals(stored.get("extra"), tree.get("extra"));
        assertEquals(stored.get("contractStart"), tree.get("contractStart"));
        assertEquals("V-2", tree.get("contractNumber").asText());
        assertFalse(tree.has("contractEnd"));
        assertEquals(Collections.singletonList("extra"), AccountingConfigurationWriter.unknownKeys(tree));
    }

    @Test
    public void clearingAnObjectWritesTheDefault() throws Exception {
        Map<String, Long> objects = new HashMap<>();
        objects.put("a", null);
        ObjectNode once = new AccountingConfigurationWriter().patch(stored(), 3, template(),
                Collections.singletonMap("a", 9L), null);
        assertEquals(9, once.get("selectedInputs").get(0).get("objectID").asLong());
        ObjectNode cleared = new AccountingConfigurationWriter().patch(once, 3, template(), objects, null);
        assertEquals(-1, cleared.get("selectedInputs").get(0).get("objectID").asLong());
    }

    @Test
    public void anotherTemplateReplacesTheInputs() throws Exception {
        ObjectNode tree = new AccountingConfigurationWriter().patch(stored(), 4, template(),
                Collections.singletonMap("a", 9L), null);
        JsonNode inputs = tree.get("selectedInputs");
        assertEquals(2, inputs.size());
        assertEquals("a", inputs.get(0).get("id").asText());
        assertFalse(inputs.get(0).has("objectKey"));
        assertEquals(9, inputs.get(0).get("objectID").asLong());
        assertEquals(4, tree.get("templateSelection").asLong());
        assertEquals(stored().get("linkedOutputs"), tree.get("linkedOutputs"));
    }

    @Test
    public void writeAndParse() throws Exception {
        AccountingConfigurationWriter writer = new AccountingConfigurationWriter();
        ObjectNode tree = stored();
        assertEquals(tree, writer.parse(writer.write(tree)));
        assertNull(writer.parse(null));
        assertNull(writer.parse("[1]".getBytes(StandardCharsets.UTF_8)));
        assertNull(writer.parse("{".getBytes(StandardCharsets.UTF_8)));
    }
}
