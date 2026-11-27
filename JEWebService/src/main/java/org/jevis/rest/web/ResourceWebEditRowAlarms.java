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
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.constants.AlarmConstants;
import org.jevis.commons.constants.GapFillingType;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;

import java.util.*;

/**
 * The alarm settings of a clean data row (stage E7.2, see docs/WEB-API.md): the limit steps ("Limits Enabled",
 * "Limits Configuration", Control Center {@code LimitEditor}) and the alarm rules ("Alarm Enabled", "Alarm Config",
 * {@code AlarmEditor}) with their silent and standby times ({@code ScheduleEditor}), in the formats of
 * {@link AlarmFormats}. Keys the web does not know are kept; values sent back unchanged keep their stored text.
 * <p>
 * All writes use the rights of the logged-in user ({@link WebEdit.Store}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditRowAlarms {

    static final String ACTION_SAVE = "alarm-row-save";
    /** copy of the alarm rules to other rows (R16) */
    static final String ACTION_COPY = "alarm-row-copy";
    static final int MAX_COPY_TARGETS = 200;
    static final String ATTRIBUTE_LIMITS_ENABLED = ResourceWebEditValues.ATTRIBUTE_LIMITS_ENABLED;
    static final String ATTRIBUTE_LIMITS = ResourceWebEditValues.ATTRIBUTE_LIMITS;
    static final String ATTRIBUTE_ALARM_ENABLED = "Alarm Enabled";
    static final String ATTRIBUTE_ALARM_CONFIG = "Alarm Config";
    static final String ATTRIBUTE_VALUE = "Value";
    static final List<String> VERSION_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(ATTRIBUTE_LIMITS_ENABLED,
            ATTRIBUTE_LIMITS, ATTRIBUTE_ALARM_ENABLED, ATTRIBUTE_ALARM_CONFIG));
    static final String KIND_STATIC = "STATIC";
    static final String KIND_DYNAMIC = "DYNAMIC";
    /** data outage rule (docs/ALARMS-OUTAGE.md): minutes without a new value */
    static final String KIND_NO_DATA = "NO_DATA";
    static final int MAX_STEPS = 2;
    static final int MAX_RULES = 10;
    static final int MAX_NAME_LENGTH = 200;

    /* JSON keys of JsonLimitsConfig and JsonAlarmConfig as Jackson writes them */
    static final String STEP_NAME = "name";
    static final String STEP_MIN = "min";
    static final String STEP_MAX = "max";
    static final String STEP_SUBSTITUTE = "typeOfSubstituteValue";
    static final String STEP_DEFAULT_MIN = "defaultMinValue";
    static final String STEP_DEFAULT_MAX = "defaultMaxValue";
    static final String RULE_ID = "id";
    static final String RULE_NAME = "name";
    static final String RULE_LIMIT = "limit";
    static final String RULE_LIMIT_DATA = "limitData";
    static final String RULE_OPERATOR = "operator";
    static final String RULE_TOLERANCE = "tolerance";
    static final String RULE_SILENT = "silentTime";
    static final String RULE_STANDBY = "standbyTime";
    /* new keys (docs/ALARMS-OUTAGE.md): removed when empty, never written as null, so that older Control Centers,
       which reject unknown keys, can still read rows that do not use them */
    static final String RULE_NO_DATA = "noData";
    static final String RULE_VALID = "validTime";
    static final String RULE_AVERAGE = "average";
    /**
     * Keys of a step the web handles itself (web names and stored names); all other keys are passed through
     */
    private static final Set<String> STEP_KEYS = new HashSet<>(Arrays.asList("name", "min", "max", "substitute",
            "defaultMin", "defaultMax", STEP_SUBSTITUTE, STEP_DEFAULT_MIN, STEP_DEFAULT_MAX));

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("rows/{rowId}/alarm-settings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response settings(@Context HttpHeaders httpHeaders, @PathParam("rowId") long rowId, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "load the alarm settings of " + rowId, store -> settings(store, rowId, language));
    }

    @PUT
    @Path("rows/{rowId}/alarm-settings")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("rowId") long rowId,
                           @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "save the alarm settings of " + rowId,
                store -> update(store, rowId, body, language, DateTime.now()));
    }

    @POST
    @Path("rows/{rowId}/alarm-settings/copy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response copy(@Context HttpHeaders httpHeaders, @PathParam("rowId") long rowId, String body) {
        return WebEdit.run(httpHeaders, "copy the alarm rules of " + rowId, store -> copy(store, rowId, body, DateTime.now()));
    }

    // ---------------------------------------------------------------- row

    /**
     * @return the readable clean data row outside the recycle bin: the row itself, or the first clean data child of a
     * raw data row; null if there is none
     */
    static JsonObject cleanRow(WebEdit.Store store, long id) throws Exception {
        JsonObject row = WebEdit.readable(store, id);
        if (row == null) return null;
        ResourceWebRegisters.Classes classes = store.classes();
        if (classes.withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA).contains(row.getJevisClass())) return row;
        if (classes.withHeirs(ResourceWebNotes.CLASS_DATA).contains(row.getJevisClass())) {
            return ResourceWebEditValues.cleanChild(store, row);
        }
        return null;
    }

    static Map<String, Object> rowJson(WebEdit.Store store, JsonObject clean) {
        ResourceWebModules.Lookup lookup;
        try {
            lookup = store.lookup();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Long parentId = lookup.parent(clean.getId());
        JsonObject raw = parentId != null ? lookup.readable(parentId) : null;
        if (raw != null && (ResourceWebIso.isDeleted(raw)
                || !store.classes().withHeirs(ResourceWebNotes.CLASS_DATA).contains(raw.getJevisClass()))) {
            raw = null;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", clean.getId());
        json.put("name", clean.getName());
        json.put("unit", store.unit(clean.getId(), ATTRIBUTE_VALUE));
        json.put("rawId", raw != null ? raw.getId() : null);
        json.put("rawName", raw != null ? raw.getName() : null);
        /* newest raw value (arrival of data, as the data outage rule checks it; docs/ALARMS-OUTAGE.md) */
        String lastValue = null;
        try {
            JsonSample last = store.latest(raw != null ? raw.getId() : clean.getId(), ATTRIBUTE_VALUE);
            lastValue = WebEdit.version(last);
        } catch (Exception e) {
            lastValue = null;
        }
        json.put("lastValue", lastValue);
        return json;
    }

    // ---------------------------------------------------------------- stored settings

    /**
     * The stored settings of a row.
     */
    static final class Stored {
        JsonSample limitsEnabled;
        JsonSample limits;
        JsonSample alarmEnabled;
        JsonSample alarmConfig;
        /**
         * the steps, null if the stored value cannot be read
         */
        List<ObjectNode> steps;
        /**
         * the rules, null if the stored value cannot be read
         */
        List<ObjectNode> rules;

        JsonSample latest() {
            JsonSample latest = null;
            for (JsonSample sample : Arrays.asList(limitsEnabled, limits, alarmEnabled, alarmConfig)) {
                Long millis = sample != null ? ResourceWebNotes.millis(sample.getTs()) : null;
                Long current = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
                if (millis != null && (current == null || millis > current)) latest = sample;
            }
            return latest;
        }

        String version() {
            return ResourceWebEditAnalysis.version(latest());
        }
    }

    static Stored stored(WebEdit.Store store, long id) throws Exception {
        Stored stored = new Stored();
        stored.limitsEnabled = store.latest(id, ATTRIBUTE_LIMITS_ENABLED);
        stored.limits = store.latest(id, ATTRIBUTE_LIMITS);
        stored.alarmEnabled = store.latest(id, ATTRIBUTE_ALARM_ENABLED);
        stored.alarmConfig = store.latest(id, ATTRIBUTE_ALARM_CONFIG);
        stored.steps = AlarmFormats.parseList(stored.limits != null ? stored.limits.getValue() : null);
        stored.rules = AlarmFormats.parseList(stored.alarmConfig != null ? stored.alarmConfig.getValue() : null);
        return stored;
    }

    // ---------------------------------------------------------------- reading

    /**
     * @return the web form of a step: {@code name, min, max, substitute, defaultMin, defaultMax} and all other keys as
     * stored
     */
    static Map<String, Object> stepJson(ObjectNode step) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", AlarmFormats.text(step, STEP_NAME));
        json.put("min", AlarmFormats.number(AlarmFormats.text(step, STEP_MIN)));
        json.put("max", AlarmFormats.number(AlarmFormats.text(step, STEP_MAX)));
        json.put("substitute", GapFillingType.parse(AlarmFormats.text(step, STEP_SUBSTITUTE)).name());
        json.put("defaultMin", AlarmFormats.number(AlarmFormats.text(step, STEP_DEFAULT_MIN)));
        json.put("defaultMax", AlarmFormats.number(AlarmFormats.text(step, STEP_DEFAULT_MAX)));
        Iterator<Map.Entry<String, JsonNode>> fields = step.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!STEP_KEYS.contains(field.getKey()) && !json.containsKey(field.getKey())) {
                json.put(field.getKey(), field.getValue());
            }
        }
        return json;
    }

    /**
     * @return the kind of a rule as JEAlarm reads it ({@code CleanDataAlarm}: a row to compare with makes it
     * dynamic, a limit static); a rule with neither is shown as the Control Center shows it (id 1: dynamic)
     */
    static String kind(ObjectNode rule) {
        if (AlarmFormats.number(AlarmFormats.text(rule, RULE_NO_DATA)) != null) return KIND_NO_DATA;
        String limitData = AlarmFormats.text(rule, RULE_LIMIT_DATA);
        if (limitData != null && !limitData.trim().isEmpty()) return KIND_DYNAMIC;
        if (AlarmFormats.text(rule, RULE_LIMIT) != null) return KIND_STATIC;
        return "1".equals(AlarmFormats.text(rule, RULE_ID)) ? KIND_DYNAMIC : KIND_STATIC;
    }

    /**
     * @return whether {@code CleanDataAlarm} accepts the rule (limit or row, operator, tolerance a number if set)
     */
    static boolean complete(ObjectNode rule) {
        String kind = kind(rule);
        if (KIND_NO_DATA.equals(kind)) {
            Double minutes = AlarmFormats.number(AlarmFormats.text(rule, RULE_NO_DATA));
            return minutes != null && minutes >= 1;
        }
        boolean target = KIND_DYNAMIC.equals(kind)
                ? !ResourceWebEditAlarms.targets(AlarmFormats.text(rule, RULE_LIMIT_DATA)).isEmpty()
                : AlarmFormats.number(AlarmFormats.text(rule, RULE_LIMIT)) != null;
        String operator = AlarmFormats.text(rule, RULE_OPERATOR);
        String tolerance = AlarmFormats.text(rule, RULE_TOLERANCE);
        return target && operator != null && AlarmConstants.Operator.parse(operator) != null
                && (tolerance == null || AlarmFormats.number(tolerance) != null);
    }

    static Map<String, Object> limitDataJson(String stored, ResourceWebModules.Lookup lookup) {
        List<ResourceWebEditAlarms.Target> targets = ResourceWebEditAlarms.targets(stored);
        if (targets.isEmpty()) return null;
        ResourceWebEditAlarms.Target target = targets.get(0);
        JsonObject object = lookup.readable(target.id);
        if (object != null && ResourceWebIso.isDeleted(object)) object = null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", target.id);
        json.put("attribute", target.attribute != null ? target.attribute : ResourceWebEditAlarms.DEFAULT_ATTRIBUTE);
        json.put("name", object != null ? object.getName() : null);
        json.put("readable", object != null);
        return json;
    }

    static Map<String, Object> ruleJson(ObjectNode rule, ResourceWebModules.Lookup lookup) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        String operator = AlarmFormats.text(rule, RULE_OPERATOR);
        AlarmConstants.Operator parsed = operator != null ? AlarmConstants.Operator.parse(operator) : null;
        json.put("id", AlarmFormats.text(rule, RULE_ID));
        json.put("name", AlarmFormats.text(rule, RULE_NAME));
        json.put("kind", kind(rule));
        json.put("limit", AlarmFormats.number(AlarmFormats.text(rule, RULE_LIMIT)));
        json.put("limitData", limitDataJson(AlarmFormats.text(rule, RULE_LIMIT_DATA), lookup));
        json.put("operator", parsed != null ? parsed.name() : null);
        json.put("tolerance", AlarmFormats.number(AlarmFormats.text(rule, RULE_TOLERANCE)));
        json.put("silent", AlarmFormats.scheduleJson(rule.get(RULE_SILENT)));
        json.put("standby", AlarmFormats.scheduleJson(rule.get(RULE_STANDBY)));
        json.put("valid", AlarmFormats.scheduleJson(rule.get(RULE_VALID)));
        json.put("noData", AlarmFormats.number(AlarmFormats.text(rule, RULE_NO_DATA)));
        json.put("average", AlarmFormats.number(AlarmFormats.text(rule, RULE_AVERAGE)));
        json.put("complete", complete(rule));
        return json;
    }

    static Map<String, Object> settingsJson(WebEdit.Store store, JsonObject clean) throws Exception {
        Stored stored = stored(store, clean.getId());
        ResourceWebModules.Lookup lookup = store.lookup();
        Map<String, Object> limits = new LinkedHashMap<>();
        limits.put("enabled", ResourceWebAlarms.isTrue(stored.limitsEnabled));
        List<Map<String, Object>> steps = new ArrayList<>();
        if (stored.steps != null) {
            for (ObjectNode step : stored.steps) steps.add(stepJson(step));
        }
        limits.put("steps", steps);
        limits.put("readable", stored.steps != null);
        Map<String, Object> alarms = new LinkedHashMap<>();
        alarms.put("enabled", ResourceWebAlarms.isTrue(stored.alarmEnabled));
        List<Map<String, Object>> rules = new ArrayList<>();
        if (stored.rules != null) {
            for (ObjectNode rule : stored.rules) rules.add(ruleJson(rule, lookup));
        }
        alarms.put("rules", rules);
        alarms.put("readable", stored.rules != null);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("row", rowJson(store, clean));
        json.put("canWrite", store.rights().canWrite(clean));
        json.put("version", stored.version());
        json.put("limits", limits);
        json.put("alarms", alarms);
        json.put("configurations", ResourceWebEditAlarms.configurationsOfRow(store, clean));
        return json;
    }

    /**
     * {@code GET /rows/{rowId}/alarm-settings}
     */
    static WebEdit.Reply settings(WebEdit.Store store, long rowId, String language) throws Exception {
        JsonObject clean = cleanRow(store, rowId);
        if (clean == null) return WebEdit.Reply.notFound("Clean data row not found");
        return WebEdit.Reply.ok(settingsJson(store, clean));
    }

    // ---------------------------------------------------------------- input helpers

    /**
     * A number field: absent (not sent), null or a number.
     */
    private static final class Num {
        final boolean sent;
        final Double value;

        Num(boolean sent, Double value) {
            this.sent = sent;
            this.value = value;
        }
    }

    private static Num num(JsonNode json, String field, String label, List<String> messages) {
        if (!json.has(field)) return new Num(false, null);
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return new Num(true, null);
        if (!node.isNumber() || Double.isNaN(node.asDouble()) || Double.isInfinite(node.asDouble())) {
            messages.add(label + ": number expected");
            return new Num(false, null);
        }
        return new Num(true, node.asDouble());
    }

    /**
     * Sets a number as text; a value equal to the stored number keeps the stored text.
     */
    private static void setNumber(ObjectNode node, String key, Double value, boolean asDouble) {
        String stored = AlarmFormats.text(node, key);
        if (Objects.equals(AlarmFormats.number(stored), value) && (value != null || stored == null)) return;
        if (value == null) {
            node.set(key, NullNode.getInstance());
        } else {
            node.put(key, asDouble ? AlarmFormats.doubleText(value) : AlarmFormats.plainText(value));
        }
    }

    private static String name(JsonNode json, String label, List<String> messages) {
        JsonNode node = json.get("name");
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            messages.add(label + ": invalid name");
            return null;
        }
        String name = node.asText().trim();
        if (name.length() > MAX_NAME_LENGTH) {
            messages.add(label + ": name longer than " + MAX_NAME_LENGTH + " characters");
            return null;
        }
        return name.isEmpty() ? null : name;
    }

    // ---------------------------------------------------------------- limits

    /**
     * @return the default configuration of the Control Center ({@code LimitEditor.createDefaultConfig}: two steps
     * with names only)
     */
    static List<ObjectNode> defaultSteps(String language) {
        List<ObjectNode> steps = new ArrayList<>();
        for (int i = 0; i < MAX_STEPS; i++) {
            ObjectNode step = AlarmFormats.newLimitStep();
            step.put(STEP_NAME, defaultStepName(i, language));
            steps.add(step);
        }
        return steps;
    }

    static String defaultStepName(int index, String language) {
        String name = ResourceWebNotes.NoteTags.translate("newobject.title" + (index + 1), language);
        return name != null ? name : ("de".equals(language) ? "Stufe " : "Level ") + (index + 1);
    }

    /**
     * @return the steps to store: the sent steps merged into the stored ones (same index); problems are added to the
     * messages
     */
    static List<ObjectNode> steps(JsonNode node, List<ObjectNode> stored, String language, List<String> messages) {
        if (!node.isArray()) {
            messages.add("limits.steps: array expected");
            return null;
        }
        if (node.size() < 1 || node.size() > MAX_STEPS) {
            messages.add("limits.steps: 1 to " + MAX_STEPS + " steps expected");
            return null;
        }
        List<ObjectNode> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode sent = node.get(i);
            String label = "limits.steps[" + i + "]";
            if (!sent.isObject()) {
                messages.add(label + ": object expected");
                continue;
            }
            ObjectNode original = stored != null && i < stored.size() ? stored.get(i) : null;
            ObjectNode step = original != null ? original.deepCopy() : AlarmFormats.newLimitStep();
            int before = messages.size();
            String name = name(sent, label, messages);
            if (name != null && !name.equals(AlarmFormats.text(step, STEP_NAME))) step.put(STEP_NAME, name);
            if (AlarmFormats.text(step, STEP_NAME) == null) step.put(STEP_NAME, defaultStepName(i, language));
            Num min = num(sent, "min", label + ".min", messages);
            Num max = num(sent, "max", label + ".max", messages);
            Num defaultMin = num(sent, "defaultMin", label + ".defaultMin", messages);
            Num defaultMax = num(sent, "defaultMax", label + ".defaultMax", messages);
            if (min.sent) setNumber(step, STEP_MIN, min.value, true);
            if (max.sent) setNumber(step, STEP_MAX, max.value, true);
            if (defaultMin.sent) setNumber(step, STEP_DEFAULT_MIN, defaultMin.value, true);
            if (defaultMax.sent) setNumber(step, STEP_DEFAULT_MAX, defaultMax.value, true);
            if (sent.has("substitute")) {
                JsonNode substitute = sent.get("substitute");
                GapFillingType type = null;
                if (substitute != null && substitute.isTextual()) {
                    for (GapFillingType value : GapFillingType.values()) {
                        if (value.name().equals(substitute.asText())) type = value;
                    }
                }
                if (type == null) {
                    messages.add(label + ".substitute: one of " + Arrays.toString(GapFillingType.values()) + " expected");
                } else if (GapFillingType.parse(AlarmFormats.text(step, STEP_SUBSTITUTE)) != type
                        || (AlarmFormats.text(step, STEP_SUBSTITUTE) == null && type != GapFillingType.NONE)) {
                    step.put(STEP_SUBSTITUTE, type.name());
                }
            }
            Iterator<Map.Entry<String, JsonNode>> fields = sent.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (STEP_KEYS.contains(field.getKey())) continue;
                JsonNode value = field.getValue();
                if (value == null || value.isNull()) {
                    if (step.has(field.getKey()) && !step.get(field.getKey()).isNull()) step.set(field.getKey(), NullNode.getInstance());
                } else if (value.isValueNode()) {
                    if (!value.equals(step.get(field.getKey()))) {
                        step.set(field.getKey(), value.isTextual() ? value : TextNode.valueOf(value.asText()));
                    }
                } else {
                    messages.add(label + "." + field.getKey() + ": value expected");
                }
            }
            if (messages.size() == before && !step.equals(original)) {
                Double effectiveMin = AlarmFormats.number(AlarmFormats.text(step, STEP_MIN));
                Double effectiveMax = AlarmFormats.number(AlarmFormats.text(step, STEP_MAX));
                if (effectiveMin != null && effectiveMax != null && effectiveMin >= effectiveMax) {
                    messages.add(label + ": min must be below max");
                }
                if (GapFillingType.parse(AlarmFormats.text(step, STEP_SUBSTITUTE)) == GapFillingType.DEFAULT_VALUE
                        && (AlarmFormats.number(AlarmFormats.text(step, STEP_DEFAULT_MIN)) == null
                        || AlarmFormats.number(AlarmFormats.text(step, STEP_DEFAULT_MAX)) == null)) {
                    messages.add(label + ": defaultMin and defaultMax are needed for DEFAULT_VALUE");
                }
            }
            result.add(step);
        }
        return result;
    }

    // ---------------------------------------------------------------- alarm rules

    private static String idText(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isTextual() && !node.asText().trim().isEmpty()) return node.asText().trim();
        if (node.isIntegralNumber()) return node.asText();
        return "";
    }

    /**
     * @return a new id: {@code 0} for a static and {@code 1} for a dynamic rule if free (the Control Center's
     * {@code AlarmEditor} shows the limit for id 0 and the row for id 1), else the highest number + 1
     */
    static String newId(String kind, Set<String> used) {
        String preferred = KIND_DYNAMIC.equals(kind) ? "1" : KIND_NO_DATA.equals(kind) ? "2" : "0";
        if (!used.contains(preferred)) return preferred;
        int max = 1;
        for (String id : used) {
            try {
                max = Math.max(max, Integer.parseInt(id));
            } catch (NumberFormatException e) {
                // not a number
            }
        }
        return String.valueOf(max + 1);
    }

    static String defaultRuleName(String kind, String language) {
        if (KIND_NO_DATA.equals(kind)) return "de".equals(language) ? "Datenausfall" : "Data outage";
        String key = KIND_DYNAMIC.equals(kind) ? "newobject.alarmconfig2" : "newobject.alarmconfig1";
        String name = ResourceWebNotes.NoteTags.translate(key, language);
        return name != null ? name : KIND_DYNAMIC.equals(kind) ? "Dynamic Alarm" : "Static Alarm";
    }

    /**
     * @return the rules to store: each sent rule merged into the stored rule with its id; problems are added to the
     * messages
     */
    static List<ObjectNode> rules(WebEdit.Store store, JsonNode node, List<ObjectNode> stored, String zone,
                                  String language, List<String> messages) throws Exception {
        if (!node.isArray()) {
            messages.add("alarms.rules: array expected");
            return null;
        }
        if (node.size() > MAX_RULES) {
            messages.add("alarms.rules: at most " + MAX_RULES + " rules");
            return null;
        }
        Map<String, ObjectNode> byId = new LinkedHashMap<>();
        if (stored != null) {
            for (ObjectNode rule : stored) {
                String id = AlarmFormats.text(rule, RULE_ID);
                if (id != null) byId.putIfAbsent(id, rule);
            }
        }
        Set<String> used = new HashSet<>();
        for (JsonNode sent : node) {
            String id = sent.isObject() ? idText(sent.get("id")) : null;
            if (id != null && !id.isEmpty() && !used.add(id)) messages.add("alarms.rules: id " + id + " is listed twice");
        }
        Set<String> rowClasses = ResourceWebEditAlarms.rowClasses(store.classes());
        List<ObjectNode> result = new ArrayList<>();
        for (int i = 0; i < node.size(); i++) {
            JsonNode sent = node.get(i);
            String label = "alarms.rules[" + i + "]";
            if (!sent.isObject()) {
                messages.add(label + ": object expected");
                continue;
            }
            String id = idText(sent.get("id"));
            if ("".equals(id)) {
                messages.add(label + ": invalid id");
                continue;
            }
            ObjectNode original = id != null ? byId.get(id) : null;
            ObjectNode rule = original != null ? original.deepCopy() : AlarmFormats.newAlarmRule();
            int before = messages.size();

            String kind = original != null ? kind(original) : null;
            JsonNode kindNode = sent.get("kind");
            if (kindNode != null && !kindNode.isNull()) {
                if (!kindNode.isTextual() || !(KIND_STATIC.equals(kindNode.asText()) || KIND_DYNAMIC.equals(kindNode.asText())
                        || KIND_NO_DATA.equals(kindNode.asText()))) {
                    messages.add(label + ".kind: STATIC, DYNAMIC or NO_DATA expected");
                    continue;
                }
                kind = kindNode.asText();
            }
            if (kind == null) {
                messages.add(label + ".kind: STATIC, DYNAMIC or NO_DATA expected");
                continue;
            }
            if (id == null) {
                id = newId(kind, used);
                used.add(id);
            }
            if (!id.equals(AlarmFormats.text(rule, RULE_ID))) rule.put(RULE_ID, id);

            String name = name(sent, label, messages);
            if (name != null && !name.equals(AlarmFormats.text(rule, RULE_NAME))) rule.put(RULE_NAME, name);
            if (AlarmFormats.text(rule, RULE_NAME) == null) rule.put(RULE_NAME, defaultRuleName(kind, language));

            if (KIND_NO_DATA.equals(kind)) {
                Num minutes = num(sent, "noData", label + ".noData", messages);
                if (minutes.sent) {
                    if (minutes.value == null || minutes.value < 1 || minutes.value > 525600) {
                        messages.add(label + ".noData: minutes from 1 to 525600 expected");
                    } else {
                        rule.put(RULE_NO_DATA, String.valueOf(Math.round(minutes.value)));
                    }
                }
                /* an outage rule has no limit, row, operator, mean or validity time */
                for (String key : new String[]{RULE_LIMIT, RULE_LIMIT_DATA, RULE_OPERATOR}) {
                    if (AlarmFormats.text(rule, key) != null) rule.set(key, NullNode.getInstance());
                }
                rule.remove(RULE_AVERAGE);
                rule.remove(RULE_VALID);
            } else if (KIND_STATIC.equals(kind)) {
                rule.remove(RULE_NO_DATA);
                Num limit = num(sent, "limit", label + ".limit", messages);
                if (limit.sent) setNumber(rule, RULE_LIMIT, limit.value, false);
                if (AlarmFormats.text(rule, RULE_LIMIT_DATA) != null) rule.set(RULE_LIMIT_DATA, NullNode.getInstance());
            } else {
                rule.remove(RULE_NO_DATA);
                if (sent.has("limitData")) limitData(store, sent.get("limitData"), rule, rowClasses, label, messages);
                if (AlarmFormats.text(rule, RULE_LIMIT) != null) rule.set(RULE_LIMIT, NullNode.getInstance());
            }

            if (sent.has("operator")) {
                JsonNode operator = sent.get("operator");
                AlarmConstants.Operator parsed = operator != null && operator.isTextual()
                        ? AlarmConstants.Operator.parse(operator.asText()) : null;
                if (parsed == null) {
                    messages.add(label + ".operator: one of " + Arrays.toString(AlarmConstants.Operator.values()) + " expected");
                } else if (!parsed.name().equals(AlarmFormats.text(rule, RULE_OPERATOR))) {
                    rule.put(RULE_OPERATOR, parsed.name());
                }
            }
            Num tolerance = num(sent, "tolerance", label + ".tolerance", messages);
            if (tolerance.sent) {
                if (tolerance.value != null && tolerance.value < 0) {
                    messages.add(label + ".tolerance: 0 or more expected");
                } else {
                    setNumber(rule, RULE_TOLERANCE, tolerance.value, false);
                }
            }
            if (sent.has("silent")) {
                setSchedule(rule, RULE_SILENT, AlarmFormats.schedule(sent.get("silent"), rule.get(RULE_SILENT), zone,
                        label + ".silent", messages));
            }
            if (sent.has("standby")) {
                setSchedule(rule, RULE_STANDBY, AlarmFormats.schedule(sent.get("standby"), rule.get(RULE_STANDBY), zone,
                        label + ".standby", messages));
            }
            if (!KIND_NO_DATA.equals(kind)) {
                if (sent.has("valid")) {
                    JsonNode valid = AlarmFormats.schedule(sent.get("valid"), rule.get(RULE_VALID), zone, label + ".valid", messages);
                    if (valid == null) rule.remove(RULE_VALID);
                    else rule.set(RULE_VALID, valid);
                }
                Num average = num(sent, "average", label + ".average", messages);
                if (average.sent) {
                    if (average.value == null) {
                        rule.remove(RULE_AVERAGE);
                    } else if (average.value < 1 || average.value > 10080) {
                        messages.add(label + ".average: minutes from 1 to 10080 expected");
                    } else {
                        rule.put(RULE_AVERAGE, String.valueOf(Math.round(average.value)));
                    }
                }
            }

            if (messages.size() == before && !rule.equals(original) && KIND_NO_DATA.equals(kind)) {
                if (!complete(rule)) messages.add(label + ".noData: minutes expected");
            } else if (messages.size() == before && !rule.equals(original)) {
                /* only changed or new rules must be complete (the Control Center's default rules are not) */
                if (KIND_STATIC.equals(kind) && AlarmFormats.number(AlarmFormats.text(rule, RULE_LIMIT)) == null) {
                    messages.add(label + ".limit: number expected");
                }
                if (KIND_DYNAMIC.equals(kind) && ResourceWebEditAlarms.targets(AlarmFormats.text(rule, RULE_LIMIT_DATA)).isEmpty()) {
                    messages.add(label + ".limitData: a readable data row expected");
                }
                String operator = AlarmFormats.text(rule, RULE_OPERATOR);
                if (operator == null || AlarmConstants.Operator.parse(operator) == null) {
                    messages.add(label + ".operator: one of " + Arrays.toString(AlarmConstants.Operator.values()) + " expected");
                }
                String toleranceText = AlarmFormats.text(rule, RULE_TOLERANCE);
                if (toleranceText != null && AlarmFormats.number(toleranceText) == null) {
                    messages.add(label + ".tolerance: number expected");
                }
            }
            result.add(rule);
        }
        return result;
    }

    private static void setSchedule(ObjectNode rule, String key, JsonNode schedule) {
        JsonNode current = rule.get(key);
        if (schedule == current) return;
        if (schedule == null) {
            if (current != null && !current.isNull()) rule.set(key, NullNode.getInstance());
        } else {
            rule.set(key, schedule);
        }
    }

    /**
     * Sets "limitData" from {@code {id, attribute}} (stored as {@code id:Attribute}, as the {@code AlarmEditor}).
     */
    private static void limitData(WebEdit.Store store, JsonNode sent, ObjectNode rule, Set<String> rowClasses,
                                  String label, List<String> messages) throws Exception {
        if (sent == null || sent.isNull()) {
            if (AlarmFormats.text(rule, RULE_LIMIT_DATA) != null) rule.set(RULE_LIMIT_DATA, NullNode.getInstance());
            return;
        }
        JsonNode idNode = sent.isObject() ? sent.get("id") : sent;
        if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
            messages.add(label + ".limitData: id expected");
            return;
        }
        long id = idNode.asLong();
        String attribute = ResourceWebEditAlarms.DEFAULT_ATTRIBUTE;
        JsonNode attributeNode = sent.isObject() ? sent.get("attribute") : null;
        if (attributeNode != null && !attributeNode.isNull()) {
            if (!attributeNode.isTextual() || attributeNode.asText().trim().isEmpty()
                    || attributeNode.asText().contains(";") || attributeNode.asText().contains(":")) {
                messages.add(label + ".limitData: invalid attribute");
                return;
            }
            attribute = attributeNode.asText().trim();
        }
        List<ResourceWebEditAlarms.Target> current = ResourceWebEditAlarms.targets(AlarmFormats.text(rule, RULE_LIMIT_DATA));
        if (!current.isEmpty() && current.get(0).id == id && Objects.equals(
                current.get(0).attribute != null ? current.get(0).attribute : ResourceWebEditAlarms.DEFAULT_ATTRIBUTE, attribute)) {
            return;
        }
        JsonObject object = WebEdit.readable(store, id);
        if (object == null || !rowClasses.contains(object.getJevisClass())) {
            messages.add(label + ".limitData: a readable data row expected");
            return;
        }
        rule.put(RULE_LIMIT_DATA, id + ":" + attribute);
    }

    // ---------------------------------------------------------------- PUT

    /**
     * {@code PUT /rows/{rowId}/alarm-settings} with {@code {version, limits?: {enabled, steps}, alarms?: {enabled,
     * rules}}}
     */
    static WebEdit.Reply update(WebEdit.Store store, long rowId, String body, String language, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject clean = cleanRow(store, rowId);
        if (clean == null) return WebEdit.Reply.notFound("Clean data row not found");
        if (!store.rights().canWrite(clean)) return WebEdit.Reply.forbidden("Not allowed to change the settings of this row");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");

        Stored stored = stored(store, clean.getId());
        String zone = store.workDay(clean).zone.getID();
        List<String> messages = new ArrayList<>();
        Boolean limitsEnabled = null;
        List<ObjectNode> steps = null;
        Boolean alarmEnabled = null;
        List<ObjectNode> rules = null;

        JsonNode limits = json.get("limits");
        if (limits != null && !limits.isNull()) {
            if (!limits.isObject()) {
                messages.add("Invalid limits");
            } else {
                limitsEnabled = enabled(limits, "limits.enabled", messages);
                JsonNode stepsNode = limits.get("steps");
                boolean noSteps = stepsNode == null || stepsNode.isNull()
                        || (stepsNode.isArray() && stepsNode.size() == 0 && (stored.steps == null || stored.steps.isEmpty()));
                if (!noSteps) {
                    steps = steps(limits.get("steps"), stored.steps, language, messages);
                } else if (Boolean.TRUE.equals(limitsEnabled) && (stored.limits == null
                        || stored.limits.getValue() == null || stored.limits.getValue().trim().isEmpty())) {
                    /* switched on without configuration: the default of the Control Center's CleanDataExtension */
                    steps = defaultSteps(language);
                }
            }
        }
        JsonNode alarms = json.get("alarms");
        if (alarms != null && !alarms.isNull()) {
            if (!alarms.isObject()) {
                messages.add("Invalid alarms");
            } else {
                alarmEnabled = enabled(alarms, "alarms.enabled", messages);
                if (alarms.has("rules") && !alarms.get("rules").isNull()) {
                    rules = rules(store, alarms.get("rules"), stored.rules, zone, language, messages);
                }
            }
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!ResourceWebEditAnalysis.sameVersion(version, stored.latest())) {
            return WebEdit.Reply.conflict(settingsJson(store, clean));
        }

        boolean limitsChanged = false;
        /* a missing flag counts as off (as the processor and JEAlarm read it): only a real change is written */
        if (limitsEnabled != null && ResourceWebAlarms.isTrue(stored.limitsEnabled) != limitsEnabled) {
            write(store, clean, ATTRIBUTE_LIMITS_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN, String.valueOf(limitsEnabled), now);
            limitsChanged = true;
        }
        if (steps != null && !steps.equals(stored.steps)) {
            write(store, clean, ATTRIBUTE_LIMITS, JEVisConstants.PrimitiveType.STRING, AlarmFormats.listText(steps), now);
            limitsChanged = true;
        }
        if (alarmEnabled != null && ResourceWebAlarms.isTrue(stored.alarmEnabled) != alarmEnabled) {
            write(store, clean, ATTRIBUTE_ALARM_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN, String.valueOf(alarmEnabled), now);
        }
        if (rules != null && !rules.equals(stored.rules)) {
            write(store, clean, ATTRIBUTE_ALARM_CONFIG, JEVisConstants.PrimitiveType.STRING, AlarmFormats.listText(rules), now);
        }
        Map<String, Object> answer = settingsJson(store, clean);
        answer.put("reprocessHint", limitsChanged);
        return WebEdit.Reply.ok(answer);
    }

    /**
     * {@code POST /rows/{rowId}/alarm-settings/copy} with {@code {targets: [row ids], replace?: false}}: the stored
     * alarm rules of the row go to each target row (raw rows resolve to their clean data row) the user may write.
     * Added after the target's own rules, or instead of them with {@code replace}; ids are kept where free, else the
     * next free number. "Alarm Enabled" is switched on; limits (Grenzwertprüfung) are not copied.
     *
     * @return {@code {changed: [row], skipped: [{row, reason}]}}; reasons: source, notFound, noWrite, unreadable,
     * tooMany
     */
    static WebEdit.Reply copy(WebEdit.Store store, long rowId, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject source = cleanRow(store, rowId);
        if (source == null) return WebEdit.Reply.notFound("Clean data row not found");
        List<ObjectNode> rules = stored(store, source.getId()).rules;
        if (rules == null || rules.isEmpty()) return WebEdit.Reply.badRequest("The row has no alarm rules to copy");
        JsonNode targets = json.get("targets");
        if (targets == null || !targets.isArray() || targets.size() == 0) return WebEdit.Reply.badRequest("targets: row ids expected");
        if (targets.size() > MAX_COPY_TARGETS) return WebEdit.Reply.badRequest("targets: at most " + MAX_COPY_TARGETS + " rows");
        for (JsonNode target : targets) {
            if (!target.canConvertToLong()) return WebEdit.Reply.badRequest("targets: row ids expected");
        }
        JsonNode replaceNode = json.get("replace");
        if (replaceNode != null && !replaceNode.isNull() && !replaceNode.isBoolean()) return WebEdit.Reply.badRequest("replace: boolean expected");
        boolean replace = replaceNode != null && replaceNode.asBoolean(false);

        List<Map<String, Object>> changed = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        Set<Long> done = new HashSet<>();
        for (JsonNode target : targets) {
            long id = target.asLong();
            JsonObject clean = cleanRow(store, id);
            if (clean == null) {
                skipped.add(skip(id, null, "notFound"));
                continue;
            }
            if (!done.add(clean.getId())) continue;
            if (clean.getId() == source.getId()) {
                skipped.add(skip(id, rowJson(store, clean), "source"));
                continue;
            }
            if (!store.rights().canWrite(clean)) {
                skipped.add(skip(id, rowJson(store, clean), "noWrite"));
                continue;
            }
            Stored stored = stored(store, clean.getId());
            List<ObjectNode> result = new ArrayList<>();
            if (!replace) {
                if (stored.rules == null) {
                    skipped.add(skip(id, rowJson(store, clean), "unreadable"));
                    continue;
                }
                result.addAll(stored.rules);
            }
            if (result.size() + rules.size() > MAX_RULES) {
                skipped.add(skip(id, rowJson(store, clean), "tooMany"));
                continue;
            }
            Set<String> used = new HashSet<>();
            for (ObjectNode rule : result) {
                if (rule.hasNonNull(RULE_ID)) used.add(rule.get(RULE_ID).asText());
            }
            for (ObjectNode rule : rules) {
                ObjectNode copy = rule.deepCopy();
                String ruleId = copy.hasNonNull(RULE_ID) ? copy.get(RULE_ID).asText() : null;
                if (ruleId == null || used.contains(ruleId)) {
                    int next = 0;
                    while (used.contains(String.valueOf(next))) next++;
                    ruleId = String.valueOf(next);
                    copy.put(RULE_ID, ruleId);
                }
                used.add(ruleId);
                result.add(copy);
            }
            if (!result.equals(stored.rules)) {
                WebEdit.writeSample(store, clean.getId(), ATTRIBUTE_ALARM_CONFIG,
                        WebEdit.primitiveType(store, clean, ATTRIBUTE_ALARM_CONFIG, JEVisConstants.PrimitiveType.STRING),
                        WebEdit.sample(now, AlarmFormats.listText(result), null), ACTION_COPY);
            }
            if (!ResourceWebAlarms.isTrue(stored.alarmEnabled)) {
                WebEdit.writeSample(store, clean.getId(), ATTRIBUTE_ALARM_ENABLED,
                        WebEdit.primitiveType(store, clean, ATTRIBUTE_ALARM_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN),
                        WebEdit.sample(now, "true", null), ACTION_COPY);
            }
            changed.add(rowJson(store, clean));
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("changed", changed);
        answer.put("skipped", skipped);
        return WebEdit.Reply.ok(answer);
    }

    private static Map<String, Object> skip(long id, Map<String, Object> row, String reason) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("row", row);
        json.put("reason", reason);
        return json;
    }

    private static Boolean enabled(JsonNode json, String label, List<String> messages) {
        JsonNode node = json.get("enabled");
        if (node == null || node.isNull()) return null;
        if (!node.isBoolean()) {
            messages.add(label + ": boolean expected");
            return null;
        }
        return node.asBoolean();
    }

    private static void write(WebEdit.Store store, JsonObject clean, String attribute, int fallback, String value,
                              DateTime now) throws Exception {
        WebEdit.writeSample(store, clean.getId(), attribute, WebEdit.primitiveType(store, clean, attribute, fallback),
                WebEdit.sample(now, value, null), ACTION_SAVE);
    }
}
