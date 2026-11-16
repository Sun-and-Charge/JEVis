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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Writes the JSON of an "Accounting Configuration" ("Template File") like the Control Center's
 * {@code AccountingPlugin} ({@link AccountingTemplateHandler#toJsonNode()}, {@code OutputView} input selectors), as a
 * patch of the stored JSON tree: keys this class does not know, {@code linkedOutputs} and the keys of the stored
 * {@code selectedInputs} entries survive, so a configuration saved without changes stays the same JSON.
 */
public class AccountingConfigurationWriter {

    public static final String SELECTED_INPUTS = "selectedInputs";
    public static final String LINKED_OUTPUTS = "linkedOutputs";
    public static final String TEMPLATE_SELECTION = "templateSelection";
    public static final String TYPE = "type";
    public static final String OBJECT_ID = "objectID";
    /**
     * The contract fields in the order of {@link AccountingTemplateHandler#toJsonNode()}
     */
    public static final List<String> CONTRACT_FIELDS = Collections.unmodifiableList(Arrays.asList(
            "contractNumber", "contractType", "marketLocationNumber", "contractDate", "firstRate", "periodOfNotice",
            "contractStart", "contractEnd"));
    /**
     * The contract fields that the Control Center edits with a date picker
     */
    public static final Set<String> DATE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "contractDate", "firstRate", "periodOfNotice", "contractStart", "contractEnd")));
    /**
     * All top level keys the Control Center writes
     */
    public static final Set<String> KNOWN_KEYS;
    /**
     * Value of {@code objectID} for an input without object ({@link TemplateSelected})
     */
    public static final long NO_OBJECT = -1L;

    static {
        Set<String> known = new LinkedHashSet<>(Arrays.asList(SELECTED_INPUTS, LINKED_OUTPUTS, TEMPLATE_SELECTION, TYPE));
        known.addAll(CONTRACT_FIELDS);
        KNOWN_KEYS = Collections.unmodifiableSet(known);
    }

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * @return whether the input gets an entry in {@code selectedInputs} ({@code toJsonNode}: a variable type other than
     * {@code FORMULA})
     */
    public static boolean isSelectedInput(TemplateInput input) {
        return input != null && input.getVariableType() != null
                && !InputVariableType.FORMULA.toString().equals(input.getVariableType());
    }

    /**
     * @param stored     the stored JSON (not changed), {@code null} for a new configuration
     * @param templateId the chosen "Result Calculation Template"
     * @param template   its parsed JSON
     * @param objects    template input id &rarr; object id ({@code null}: no object) for the inputs that have a selector;
     *                   inputs not in the map keep the stored object
     * @param contract   contract field &rarr; value to store ({@link #CONTRACT_FIELDS}, {@code null} = JSON null);
     *                   fields not in the map keep the stored value
     * @return the new JSON
     */
    public ObjectNode patch(ObjectNode stored, long templateId, RCTemplate template, Map<String, Long> objects,
                            Map<String, String> contract) {
        ObjectNode tree = stored != null ? stored.deepCopy() : newTree();
        JsonNode storedTemplate = tree.get(TEMPLATE_SELECTION);
        boolean sameTemplate = storedTemplate != null && storedTemplate.canConvertToLong()
                && storedTemplate.asLong() == templateId;

        Map<String, TemplateInput> templateInputs = new LinkedHashMap<>();
        for (TemplateInput input : template.getTemplateInputs()) {
            if (isSelectedInput(input) && input.getId() != null) templateInputs.putIfAbsent(input.getId(), input);
        }
        ArrayNode inputs = NODES.arrayNode();
        Set<String> done = new HashSet<>();
        JsonNode storedInputs = tree.get(SELECTED_INPUTS);
        if (storedInputs != null && storedInputs.isArray()) {
            for (JsonNode entry : storedInputs) {
                String id = entry != null && entry.isObject() && entry.hasNonNull("id") ? entry.get("id").asText() : null;
                if (id != null && sameTemplate && templateInputs.containsKey(id) && done.add(id)) {
                    ObjectNode copy = ((ObjectNode) entry).deepCopy();
                    if (objects != null && objects.containsKey(id)) setObject(copy, objects.get(id));
                    inputs.add(copy);
                } else if (sameTemplate) {
                    /* the Control Center keeps the entries it does not know (ResultCalculator.link) */
                    inputs.add(entry);
                }
            }
        }
        for (TemplateInput input : templateInputs.values()) {
            if (done.contains(input.getId())) continue;
            Long object = objects != null ? objects.get(input.getId()) : null;
            inputs.add(inputNode(input, object));
        }
        tree.set(SELECTED_INPUTS, inputs);
        JsonNode linked = tree.get(LINKED_OUTPUTS);
        if (linked == null || !linked.isArray()) tree.set(LINKED_OUTPUTS, NODES.arrayNode());
        tree.set(TEMPLATE_SELECTION, NODES.numberNode(templateId));
        tree.set(TYPE, NODES.textNode(AccountingTemplateHandler.TYPE));
        if (contract != null) {
            for (String field : CONTRACT_FIELDS) {
                if (!contract.containsKey(field)) continue;
                String value = contract.get(field);
                tree.set(field, value != null ? NODES.textNode(value) : NODES.nullNode());
            }
        }
        return tree;
    }

    /**
     * @return an empty configuration with the keys in the order of {@code toJsonNode}
     */
    private static ObjectNode newTree() {
        ObjectNode tree = NODES.objectNode();
        tree.set(SELECTED_INPUTS, NODES.arrayNode());
        tree.set(LINKED_OUTPUTS, NODES.arrayNode());
        tree.set(TEMPLATE_SELECTION, NODES.nullNode());
        tree.set(TYPE, NODES.textNode(AccountingTemplateHandler.TYPE));
        for (String field : CONTRACT_FIELDS) tree.set(field, NODES.nullNode());
        return tree;
    }

    /**
     * Sets the object of a stored entry; without object a stored entry that has no object keeps its value (e.g.
     * {@code null}), one that had an object gets {@value #NO_OBJECT}.
     */
    private static void setObject(ObjectNode entry, Long object) {
        if (object != null && object > 0) {
            entry.set(OBJECT_ID, NODES.numberNode(object));
            return;
        }
        JsonNode current = entry.get(OBJECT_ID);
        boolean hasObject = current != null && current.canConvertToLong() && current.asLong() > 0;
        if (hasObject || current == null) entry.set(OBJECT_ID, NODES.numberNode(NO_OBJECT));
    }

    /**
     * @return the entry of {@code toJsonNode} for a template input
     */
    static ObjectNode inputNode(TemplateInput input, Long object) {
        ObjectNode node = NODES.objectNode();
        node.put("objectClass", input.getObjectClass());
        node.put("id", input.getId());
        node.put("attributeName", input.getAttributeName());
        node.put("variableName", input.getVariableName());
        node.put("variableType", input.getVariableType());
        node.put("filter", input.getFilter());
        node.put("group", input.getGroup());
        node.put(OBJECT_ID, object != null && object > 0 ? object : NO_OBJECT);
        return node;
    }

    /**
     * @return the date as the Control Center's date pickers store it ({@code localDateToDateTimeString}): 00:00 of the
     * day in the time zone, ISO with offset ({@code DateTime.toString()}, e.g. {@code 2026-01-01T00:00:00.000+01:00})
     */
    public static String dateTime(LocalDate date, DateTimeZone zone) {
        if (date == null) return null;
        DateTime start = date.toDateTimeAtStartOfDay(zone);
        return ISODateTimeFormat.dateTime().withZone(zone).print(start);
    }

    /**
     * @return the file name of {@code AccountingPlugin} ({@code <name>_<yyyyMMddHHmm>.json}) with the name reduced to
     * letters and digits (it becomes a stored file name)
     */
    public static String fileName(String name, DateTime time) {
        StringBuilder sb = new StringBuilder();
        String text = name != null ? name : "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) sb.append(c);
        }
        String part = sb.length() > 0 ? sb.toString() : "AccountingConfiguration";
        return part + "_" + time.toString("yyyyMMddHHmm") + ".json";
    }

    /**
     * @return the JSON pretty printed like {@code AccountingPlugin} ({@code writerWithDefaultPrettyPrinter})
     */
    public byte[] write(JsonNode tree) throws IOException {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * @param json the stored file
     * @return the JSON object, {@code null} if it is missing or no JSON object
     */
    public ObjectNode parse(byte[] json) {
        if (json == null || json.length == 0) return null;
        try {
            JsonNode node = mapper.readTree(new String(json, StandardCharsets.UTF_8));
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * @return the top level keys of the stored JSON that the Control Center does not write, in their order
     */
    public static List<String> unknownKeys(JsonNode tree) {
        List<String> keys = new ArrayList<>();
        if (tree == null) return keys;
        Iterator<String> names = tree.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!KNOWN_KEYS.contains(name)) keys.add(name);
        }
        return keys;
    }
}
