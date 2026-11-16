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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jevis.commons.trc.AccountingConfigurationWriter;
import org.jevis.commons.trc.AccountingTemplateHandler;
import org.jevis.commons.trc.RCTemplate;
import org.jevis.commons.trc.ResultCalculator;
import org.jevis.commons.trc.SelectionTemplate;
import org.jevis.commons.trc.TemplateInput;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.text.Collator;
import java.util.*;

/**
 * Editing of the contracts (accounting configurations, stage E6.3, see docs/WEB-API.md), as the tab "Configuration"
 * of the Control Center's "Accounting Plugin" ({@code createContractsTab}, the input selectors of {@code OutputView},
 * {@code SaveUnderDialog}): the contract fields, the "Result Calculation Template" and the objects of its inputs,
 * written as the JSON of "Template File" ({@link AccountingConfigurationWriter}, a patch of the stored JSON).
 * <p>
 * All writes use the rights of the logged-in user ({@link WebEdit.Store}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditConfigurations {

    static final String ACTION_CREATE = "contract-create";
    static final String ACTION_SAVE = "contract-save";
    static final String ACTION_DELETE = "contract-delete";
    static final String CLASS_CONFIGURATION = ResourceWebContracts.CLASS_CONFIGURATION;
    static final String CLASS_DIRECTORY = "Accounting Configuration Directory";
    static final String CLASS_TEMPLATE = ResourceWebCosts.CLASS_TEMPLATE;
    static final String ATTRIBUTE_FILE = ResourceWebContracts.ATTRIBUTE_TEMPLATE_FILE;
    static final String VERSION_NONE = ResourceWebEditAnalysis.VERSION_NONE;
    static final int MAX_TEXT_LENGTH = 200;
    static final int MAX_OPTIONS = ResourceWebCosts.MAX_OPTIONS;
    /**
     * Contract fields of the web (as {@code GET /contracts}) and their keys in the JSON
     */
    static final Map<String, String> CONTRACT_KEYS;
    static final Set<String> TEXT_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "contractNumber", "marketLocation")));
    static final String FIELD_TYPE = "contractType";

    static {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("contractNumber", "contractNumber");
        keys.put("contractType", "contractType");
        keys.put("marketLocation", "marketLocationNumber");
        keys.put("contractDate", "contractDate");
        keys.put("firstRate", "firstRate");
        keys.put("periodOfNotice", "periodOfNotice");
        keys.put("start", "contractStart");
        keys.put("end", "contractEnd");
        CONTRACT_KEYS = Collections.unmodifiableMap(keys);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object CREATE_LOCK = new Object();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("contracts/configurations/{id}/edit")
    @Produces(MediaType.APPLICATION_JSON)
    public Response edit(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "load the contract " + id, store -> edit(store, id, language));
    }

    @GET
    @Path("contracts/templates")
    @Produces(MediaType.APPLICATION_JSON)
    public Response templates(@Context HttpHeaders httpHeaders) {
        return WebEdit.run(httpHeaders, "load the calculation templates", store -> templates(store));
    }

    @GET
    @Path("contracts/templates/{templateId}/inputs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response inputs(@Context HttpHeaders httpHeaders, @PathParam("templateId") long templateId,
                           @QueryParam("configuration") Long configuration) {
        return WebEdit.run(httpHeaders, "load the inputs of the template " + templateId,
                store -> inputs(store, templateId, configuration));
    }

    @POST
    @Path("contracts/configurations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "create a contract", store -> create(store, body, language, DateTime.now()));
    }

    @PUT
    @Path("contracts/configurations/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "save the contract " + id,
                store -> update(store, id, body, language, DateTime.now()));
    }

    @DELETE
    @Path("contracts/configurations/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "delete the contract " + id, store -> delete(store, id, version, language));
    }

    // ---------------------------------------------------------------- GET /contracts (additive)

    /**
     * Adds {@code canWrite}, {@code canDelete}, {@code version} and {@code template} to a configuration of
     * {@code GET /contracts}.
     *
     * @param tree the parsed "Template File", null if none
     */
    static void addConfigurationInfo(Map<String, Object> json, JsonObject object, JsonNode tree,
                                     ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                     ResourceWebContracts.Store store, WebEdit.Rights rights) {
        JsonAttribute file = ResourceWebContracts.attributes(object.getId(), store).get(ATTRIBUTE_FILE);
        json.put("canWrite", rights.canWrite(object));
        json.put("canDelete", rights.canDelete(object));
        json.put("version", version(file != null ? file.getLatestValue() : null));
        json.put("template", templateRef(templateId(tree), lookup, classes));
    }

    /**
     * Adds {@code configurationTargets} (readable "Accounting Configuration Directory" objects where the user may
     * create an "Accounting Configuration") and {@code canCreateConfiguration} to {@code GET /contracts}.
     */
    static void addCreateInfo(Map<String, Object> result, ResourceWebModules.Lookup lookup,
                              ResourceWebRegisters.Classes classes, WebEdit.Rights rights, Set<String> buildingClasses) {
        List<Map<String, Object>> targets = new ArrayList<>();
        if (classes.exists(CLASS_CONFIGURATION)) {
            for (JsonObject directory : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_DIRECTORY))) {
                if (ResourceWebIso.isDeleted(directory) || !rights.canCreate(directory, CLASS_CONFIGURATION)) continue;
                targets.add(header(directory, lookup, buildingClasses));
            }
        }
        targets.sort(ResourceWebReports.order());
        result.put("configurationTargets", targets);
        result.put("canCreateConfiguration", !targets.isEmpty());
    }

    // ---------------------------------------------------------------- reading

    /**
     * @return the version of a configuration: the time stamp of its latest "Template File" sample,
     * {@value #VERSION_NONE} without
     */
    static String version(JsonSample latest) {
        return ResourceWebEditAnalysis.version(latest);
    }

    /**
     * @return {@code templateSelection} of the stored JSON, null if missing or no number
     */
    static Long templateId(JsonNode tree) {
        JsonNode node = tree != null ? tree.get(AccountingConfigurationWriter.TEMPLATE_SELECTION) : null;
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber() && node.canConvertToLong()) return node.asLong();
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * @return {@code {id, name, readable}} of a template (name only if readable), null without id
     */
    static Map<String, Object> templateRef(Long id, ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes) {
        if (id == null) return null;
        JsonObject template = ResourceWebCosts.templateObject(lookup, classes, id);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("name", template != null ? template.getName() : null);
        json.put("readable", template != null);
        return json;
    }

    private static Map<String, Object> header(JsonObject object, ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
        return json;
    }

    /**
     * @return the readable configuration outside the recycle bin, else null
     */
    static JsonObject configurationObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && store.classes().withHeirs(CLASS_CONFIGURATION).contains(object.getJevisClass())
                ? object : null;
    }

    /**
     * The stored "Template File" of a configuration.
     */
    static final class Stored {
        JsonSample sample;
        /**
         * the JSON, null if there is none or it cannot be parsed
         */
        ObjectNode tree;

        String version() {
            return ResourceWebEditConfigurations.version(sample);
        }

        /**
         * @return the configuration as the Control Center reads it, null without JSON
         */
        SelectionTemplate selection() {
            if (tree == null) return null;
            try {
                return AccountingTemplateHandler.parse(MAPPER.writeValueAsBytes(tree));
            } catch (Exception e) {
                return null;
            }
        }
    }

    static Stored stored(WebEdit.Store store, long id) throws Exception {
        Stored stored = new Stored();
        stored.sample = store.latest(id, ATTRIBUTE_FILE);
        byte[] bytes = stored.sample != null ? store.file(id, ATTRIBUTE_FILE, stored.sample) : null;
        stored.tree = new AccountingConfigurationWriter().parse(bytes);
        return stored;
    }

    /**
     * @return the parsed template ({@code TemplateHandler}), an empty one if it has no or no readable file
     */
    static RCTemplate template(WebEdit.Store store, long templateId) {
        RCTemplate template = ResourceWebCosts.template(templateId, ResourceWebEditInvoices.contractsStore(store));
        return template != null ? template : new RCTemplate();
    }

    /**
     * @return the contract fields as {@code GET /contracts} ({@code yyyy-MM-dd} for dates, {@code contractType} only
     * if it is one of the energy types)
     */
    static Map<String, Object> contractJson(JsonNode tree) {
        Map<String, Object> json = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : CONTRACT_KEYS.entrySet()) {
            String value = ResourceWebContracts.text(tree, field.getValue());
            if (AccountingConfigurationWriter.DATE_FIELDS.contains(field.getValue())) {
                value = ResourceWebContracts.day(value);
            } else if (FIELD_TYPE.equals(field.getKey()) && !ResourceWebContracts.ENERGY_TYPES.contains(value)) {
                value = null;
            }
            json.put(field.getKey(), value);
        }
        return json;
    }

    // ---------------------------------------------------------------- selectors

    /**
     * The input selectors of a template with the objects of a configuration.
     */
    static final class Inputs {
        List<ResourceWebCosts.Selector> selectors = new ArrayList<>();
        Map<String, ResourceWebCosts.Selector> byKey = new LinkedHashMap<>();
    }

    /**
     * @param selection the configuration if it uses this template (its objects become {@code bound}), else null
     */
    static Inputs selectors(RCTemplate template, SelectionTemplate selection) {
        if (selection != null) ResultCalculator.link(template, selection, null);
        Inputs inputs = new Inputs();
        inputs.selectors = ResourceWebCosts.selectors(template, selection != null);
        for (ResourceWebCosts.Selector selector : inputs.selectors) inputs.byKey.putIfAbsent(selector.key(), selector);
        return inputs;
    }

    /**
     * @return the selectors as {@code [{key, label, variableNames, objectClass, filter, options, selected}]}
     */
    static List<Map<String, Object>> inputsJson(Inputs inputs, ResourceWebModules.Lookup lookup,
                                                Set<String> buildingClasses) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ResourceWebCosts.Selector selector : inputs.selectors) {
            Map<String, Object> json = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            for (TemplateInput input : selector.inputs) names.add(input.getVariableName());
            json.put("key", selector.key());
            json.put("label", selector.label);
            json.put("variableNames", names);
            json.put("objectClass", selector.first().getObjectClass());
            json.put("filter", selector.first().getFilter());
            json.put("options", optionsJson(selector, lookup, buildingClasses));
            json.put("selected", selector.bound);
            list.add(json);
        }
        return list;
    }

    /**
     * @return the readable objects of exactly the class, filtered like the Control Center, sorted by path and name, at
     * most {@value #MAX_OPTIONS}; the chosen object is always contained (not readable: {@code {id, name: null,
     * path: null, readable: false}} first)
     */
    static List<Map<String, Object>> optionsJson(ResourceWebCosts.Selector selector, ResourceWebModules.Lookup lookup,
                                                 Set<String> buildingClasses) {
        List<JsonObject> objects = ResourceWebCosts.matching(lookup, selector.first().getObjectClass(),
                selector.first().getFilter());
        List<Map<String, Object>> options = new ArrayList<>();
        for (JsonObject object : objects) options.add(option(object, lookup, buildingClasses));
        options.sort(byPathAndName());
        if (options.size() > MAX_OPTIONS) options = new ArrayList<>(options.subList(0, MAX_OPTIONS));
        Long bound = selector.bound;
        if (bound == null) return options;
        for (Map<String, Object> option : options) {
            if (bound.equals(option.get("id"))) return options;
        }
        JsonObject object = lookup.readable(bound);
        if (object != null && !ResourceWebIso.isDeleted(object)) {
            options.add(option(object, lookup, buildingClasses));
            options.sort(byPathAndName());
        } else {
            Map<String, Object> unreadable = new LinkedHashMap<>();
            unreadable.put("id", bound);
            unreadable.put("name", null);
            unreadable.put("path", null);
            unreadable.put("readable", false);
            options.add(0, unreadable);
        }
        return options;
    }

    private static Map<String, Object> option(JsonObject object, ResourceWebModules.Lookup lookup,
                                              Set<String> buildingClasses) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", ResourceWebCosts.realName(object, lookup));
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("readable", true);
        return json;
    }

    private static Comparator<Map<String, Object>> byPathAndName() {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        collator.setStrength(Collator.SECONDARY);
        Comparator<String> text = Comparator.nullsFirst(collator::compare);
        return Comparator.comparing((Map<String, Object> m) -> (String) m.get("path"), text)
                .thenComparing(m -> (String) m.get("name"), text)
                .thenComparingLong(m -> (Long) m.get("id"));
    }

    /**
     * @return a warning per selector without object (the Control Center allows empty inputs)
     */
    static List<String> warnings(Inputs inputs, String language) {
        List<String> warnings = new ArrayList<>();
        for (ResourceWebCosts.Selector selector : inputs.selectors) {
            if (selector.bound != null) continue;
            warnings.add("de".equals(language)
                    ? "Eingang \"" + selector.label + "\": kein Objekt zugeordnet"
                    : "Input \"" + selector.label + "\" has no object");
        }
        return warnings;
    }

    // ---------------------------------------------------------------- GET endpoints

    /**
     * {@code GET /contracts/configurations/{id}/edit}
     */
    static WebEdit.Reply edit(WebEdit.Store store, long id, String language) throws Exception {
        JsonObject object = configurationObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Contract not found");
        return WebEdit.Reply.ok(editJson(store, object, language));
    }

    /**
     * @return the editor model of the configuration, read freshly
     */
    static Map<String, Object> editJson(WebEdit.Store store, JsonObject object, String language) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        Stored stored = stored(store, object.getId());
        Long templateId = templateId(stored.tree);
        Map<String, Object> templateRef = templateRef(templateId, lookup, classes);
        Inputs inputs = new Inputs();
        if (templateRef != null && Boolean.TRUE.equals(templateRef.get("readable"))) {
            SelectionTemplate selection = stored.selection();
            inputs = selectors(template(store, templateId), selection != null ? selection : new SelectionTemplate());
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("version", stored.version());
        json.put("parentId", lookup.parent(object.getId()));
        json.put("canWrite", store.rights().canWrite(object));
        json.put("canDelete", store.rights().canDelete(object));
        json.put("contract", contractJson(stored.tree));
        json.put("template", templateRef);
        json.put("inputs", inputsJson(inputs, lookup, classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING)));
        json.put("unknownKeys", AccountingConfigurationWriter.unknownKeys(stored.tree));
        json.put("warnings", warnings(inputs, language));
        return json;
    }

    /**
     * {@code GET /contracts/templates}: readable "Result Calculation Template" objects outside the recycle bin
     */
    static WebEdit.Reply templates(WebEdit.Store store) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_TEMPLATE))) {
            if (!ResourceWebIso.isDeleted(object)) list.add(header(object, lookup, buildingClasses));
        }
        list.sort(ResourceWebReports.order());
        return WebEdit.Reply.ok(list);
    }

    /**
     * {@code GET /contracts/templates/{templateId}/inputs?configuration=…}: the selectors of the template, with the
     * objects of the configuration if it uses this template
     */
    static WebEdit.Reply inputs(WebEdit.Store store, long templateId, Long configurationId) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        if (ResourceWebCosts.templateObject(lookup, classes, templateId) == null) {
            return WebEdit.Reply.notFound("Template not found");
        }
        SelectionTemplate selection = null;
        if (configurationId != null) {
            JsonObject configuration = configurationObject(store, configurationId);
            if (configuration == null) return WebEdit.Reply.notFound("Contract not found");
            Stored stored = stored(store, configurationId);
            if (Long.valueOf(templateId).equals(templateId(stored.tree))) selection = stored.selection();
        }
        Inputs inputs = selectors(template(store, templateId), selection);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("templateId", templateId);
        json.put("inputs", inputsJson(inputs, lookup, classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING)));
        return WebEdit.Reply.ok(json);
    }

    // ---------------------------------------------------------------- writing

    /**
     * What a POST/PUT writes.
     */
    static final class Change {
        long templateId;
        RCTemplate template;
        /**
         * JSON key &rarr; value of the changed contract fields
         */
        final Map<String, String> contract = new LinkedHashMap<>();
        /**
         * template input id &rarr; object of the changed selectors
         */
        final Map<String, Long> objects = new LinkedHashMap<>();
    }

    /**
     * Reads {@code contract}, {@code templateId} and {@code selections}; all problems are added to the messages.
     *
     * @param stored the current file (null on create)
     * @param zone   time zone of the dates (the site's)
     */
    static Change change(WebEdit.Store store, JsonNode json, Stored stored, DateTimeZone zone, List<String> messages)
            throws Exception {
        Change change = new Change();
        ObjectNode tree = stored != null ? stored.tree : null;
        contract(json.get("contract"), contractJson(tree), zone, change, messages);

        JsonNode templateNode = json.get("templateId");
        if (templateNode == null || templateNode.isNull()) {
            messages.add("Missing templateId");
            return change;
        }
        if (!templateNode.isIntegralNumber() || !templateNode.canConvertToLong()) {
            messages.add("Invalid templateId");
            return change;
        }
        change.templateId = templateNode.asLong();
        if (ResourceWebCosts.templateObject(store.lookup(), store.classes(), change.templateId) == null) {
            messages.add("templateId: a readable " + CLASS_TEMPLATE + " expected");
            return change;
        }
        change.template = template(store, change.templateId);
        boolean sameTemplate = stored != null && Long.valueOf(change.templateId).equals(templateId(tree));
        SelectionTemplate selection = sameTemplate ? stored.selection() : null;
        /* the selectors are built on a copy: the template written must stay as stored */
        Inputs inputs = selectors(template(store, change.templateId), sameTemplate
                ? (selection != null ? selection : new SelectionTemplate()) : null);
        selections(store, json.get("selections"), inputs, change, messages);
        return change;
    }

    /**
     * The contract fields sent; a field equal to the current value (as the editor model shows it) keeps the stored
     * value, missing fields too.
     */
    static void contract(JsonNode node, Map<String, Object> current, DateTimeZone zone, Change change,
                         List<String> messages) {
        if (node == null || node.isNull()) return;
        if (!node.isObject()) {
            messages.add("Invalid contract");
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        Map<String, LocalDate> dates = new HashMap<>();
        boolean periodChanged = false;
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            String key = CONTRACT_KEYS.get(name);
            if (key == null) {
                messages.add("Unknown contract field: " + name);
                continue;
            }
            JsonNode value = field.getValue();
            if (value != null && !value.isNull() && !value.isTextual()) {
                messages.add(name + ": text expected");
                continue;
            }
            String text = value == null || value.isNull() || value.asText().isEmpty() ? null : value.asText();
            if (Objects.equals(text, current.get(name))) continue;
            if (TEXT_FIELDS.contains(name)) {
                if (text != null && text.length() > MAX_TEXT_LENGTH) {
                    messages.add(name + " longer than " + MAX_TEXT_LENGTH + " characters");
                    continue;
                }
                change.contract.put(key, text);
            } else if (FIELD_TYPE.equals(name)) {
                if (text != null && !ResourceWebContracts.ENERGY_TYPES.contains(text)) {
                    messages.add(name + ": one of ELECTRICITY, GAS, COMMUNITY_HEATING expected");
                    continue;
                }
                change.contract.put(key, text);
            } else {
                LocalDate date = text != null ? ResourceWebEditRegisters.parseDate(text.trim()) : null;
                if (text != null && date == null) {
                    messages.add(name + ": yyyy-MM-dd expected");
                    continue;
                }
                change.contract.put(key, AccountingConfigurationWriter.dateTime(date, zone));
                dates.put(name, date);
                if ("start".equals(name) || "end".equals(name)) periodChanged = true;
            }
        }
        if (periodChanged) {
            LocalDate start = dates.containsKey("start") ? dates.get("start") : date(current.get("start"));
            LocalDate end = dates.containsKey("end") ? dates.get("end") : date(current.get("end"));
            if (start != null && end != null && end.isBefore(start)) messages.add("end is before start");
        }
    }

    private static LocalDate date(Object value) {
        return value instanceof String ? ResourceWebEditRegisters.parseDate((String) value) : null;
    }

    /**
     * The objects sent per selector key; a selector not sent, or sent with its current object, keeps the stored
     * objects.
     */
    static void selections(WebEdit.Store store, JsonNode node, Inputs inputs, Change change, List<String> messages)
            throws Exception {
        if (node == null || node.isNull()) return;
        if (!node.isObject()) {
            messages.add("Invalid selections");
            return;
        }
        ResourceWebModules.Lookup lookup = store.lookup();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            ResourceWebCosts.Selector selector = inputs.byKey.get(field.getKey());
            if (selector == null) {
                messages.add("Unknown input: " + field.getKey());
                continue;
            }
            JsonNode value = field.getValue();
            Long object = null;
            if (value != null && !value.isNull()) {
                if (!value.isIntegralNumber() || !value.canConvertToLong()) {
                    messages.add(field.getKey() + ": object id expected");
                    continue;
                }
                object = value.asLong();
            }
            if (Objects.equals(object, selector.bound)) continue;
            if (object != null) {
                JsonObject chosen = lookup.readable(object);
                String objectClass = selector.first().getObjectClass();
                if (chosen == null || ResourceWebIso.isDeleted(chosen) || !Objects.equals(objectClass, chosen.getJevisClass())) {
                    messages.add(field.getKey() + ": a readable " + objectClass + " expected");
                    continue;
                }
            }
            for (TemplateInput input : selector.inputs) change.objects.put(input.getId(), object);
        }
    }

    /**
     * Writes the JSON as new file sample of "Template File" ({@code AccountingPlugin} save).
     */
    static void save(WebEdit.Store store, JsonObject configuration, ObjectNode tree, DateTime now, String action)
            throws Exception {
        AccountingConfigurationWriter writer = new AccountingConfigurationWriter();
        DateTime time = ResourceWebEditAnalysis.saveTime(store, configuration.getId(), ATTRIBUTE_FILE, now);
        String fileName = AccountingConfigurationWriter.fileName(configuration.getName(), time);
        int count = store.writeFile(configuration.getId(), ATTRIBUTE_FILE, time, fileName, writer.write(tree));
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(configuration.getId(), ATTRIBUTE_FILE, count, action));
    }

    /**
     * {@code POST /contracts/configurations} with {@code {parentId, name, contract, templateId, selections}}
     */
    static WebEdit.Reply create(WebEdit.Store store, String body, String language, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        ResourceWebModules.Lookup lookup = store.lookup();
        List<String> messages = new ArrayList<>();
        JsonNode parentNode = json.get("parentId");
        JsonObject parent = null;
        if (parentNode == null || parentNode.isNull()) {
            messages.add("Missing parentId");
        } else if (!parentNode.isIntegralNumber() || !parentNode.canConvertToLong()) {
            messages.add("Invalid parentId");
        } else {
            parent = WebEdit.readable(store, parentNode.asLong());
            if (parent == null) return WebEdit.Reply.notFound("Directory not found");
            if (!store.classes().withHeirs(CLASS_DIRECTORY).contains(parent.getJevisClass())) {
                messages.add("parentId: an " + CLASS_DIRECTORY + " expected");
            }
        }
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        if (parent != null && name != null && messages.isEmpty()) {
            ResourceWebEditContracts.checkUnique(lookup, parent.getId(), name, null, messages);
        }
        DateTimeZone zone = parent != null ? store.workDay(parent).zone : DateTimeZone.getDefault();
        Change change = change(store, json, null, zone, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!store.rights().canCreate(parent, CLASS_CONFIGURATION)) {
            return WebEdit.Reply.forbidden("Not allowed to create a contract here");
        }
        ObjectNode tree = new AccountingConfigurationWriter().patch(null, change.templateId, change.template,
                change.objects, change.contract);
        JsonObject configuration;
        synchronized (CREATE_LOCK) {
            /* again inside the lock: two requests with the same name */
            ResourceWebEditContracts.checkUnique(lookup, parent.getId(), name, null, messages);
            if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
            configuration = store.createObject(name, CLASS_CONFIGURATION, parent.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(configuration.getId(), configuration.getName(), ACTION_CREATE));
            lookup.addReadable(configuration, parent.getId());
            try {
                save(store, configuration, tree, now, ACTION_CREATE);
            } catch (Exception e) {
                rollback(store, configuration);
                throw e;
            }
        }
        return WebEdit.Reply.created(editJson(store, configuration, language));
    }

    private static void rollback(WebEdit.Store store, JsonObject object) {
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE + "-rollback"));
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(ResourceWebEditConfigurations.class)
                    .error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    /**
     * {@code PUT /contracts/configurations/{id}} with {@code {version, name?, contract, templateId, selections}}
     */
    static WebEdit.Reply update(WebEdit.Store store, long id, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = configurationObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Contract not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this contract");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");

        List<String> messages = new ArrayList<>();
        String name = json.has("name") ? ResourceWebEditAnalysis.name(json, messages, true) : null;
        if (name != null && messages.isEmpty() && !name.equals(object.getName())) {
            Long parent = store.lookup().parent(id);
            if (parent != null) ResourceWebEditContracts.checkUnique(store.lookup(), parent, name, id, messages);
        }
        Stored stored = stored(store, id);
        Change change = change(store, json, stored, store.workDay(object).zone, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!ResourceWebEditAnalysis.sameVersion(version, stored.sample)) {
            return WebEdit.Reply.conflict(editJson(store, object, language));
        }
        if (name != null && !name.equals(object.getName())) {
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        ObjectNode tree = new AccountingConfigurationWriter().patch(stored.tree, change.templateId, change.template,
                change.objects, change.contract);
        save(store, object, tree, now, ACTION_SAVE);
        return WebEdit.Reply.ok(editJson(store, object, language));
    }

    /**
     * {@code DELETE /contracts/configurations/{id}?version=…}: to the recycle bin
     */
    static WebEdit.Reply delete(WebEdit.Store store, long id, String version, String language) throws Exception {
        JsonObject object = configurationObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Contract not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this contract");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!ResourceWebEditAnalysis.sameVersion(version, store.latest(id, ATTRIBUTE_FILE))) {
            return WebEdit.Reply.conflict(editJson(store, object, language));
        }
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }
}
