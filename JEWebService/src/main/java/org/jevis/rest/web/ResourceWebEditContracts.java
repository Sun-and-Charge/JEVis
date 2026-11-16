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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.ResourceI18n;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Editing of contract partners, prices and governmental dues (stage E6.2, see docs/WEB-API.md), as the tab "Enter
 * data" of the Control Center's "Accounting Plugin": create objects in the directories of the groups ("New" dialog,
 * {@code dir.buildObject(name, class)}), rename them, set the contractor and the text and ranging attributes (written
 * at now), enter prices with a validity date ({@code EnterDataDialog}) and delete objects to the recycle bin.
 * <p>
 * All writes use the rights of the logged-in user ({@link WebEdit.Store}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditContracts {

    static final String ACTION_CREATE = "contract-object-create";
    static final String ACTION_SAVE = "contract-object-save";
    static final String ACTION_DELETE = "contract-object-delete";
    static final String ACTION_VALUE = "contract-value";
    static final String ACTION_VALUE_DELETE = "contract-value-delete";
    static final String VERSION_NONE = ResourceWebEditRegisters.VERSION_NONE;
    static final String RANGING_TYPE = "RangingValuesHandler";
    static final int MAX_RANGES = 50;
    static final int MAX_TEXT_LENGTH = ResourceWebEditRegisters.MAX_TEXT_LENGTH;
    static final String USED_BY_CONFIGURATION = "configuration";
    static final String USED_BY_OBJECT = "object";

    /**
     * Directory class of each group ({@code AccountingDirectories})
     */
    static final Map<String, String> DIRECTORIES;
    /**
     * Classes of the Control Center's "New" dialog per group
     */
    static final Map<String, List<String>> CREATE_CLASSES;
    /**
     * Class whose objects may be chosen as "Contractor", per group (the Control Center's contractor combo box)
     */
    static final Map<String, String> CONTRACTOR_CLASSES;

    static {
        Map<String, String> directories = new LinkedHashMap<>();
        directories.put("suppliers", "Energy Supply Directory");
        directories.put("meteringOperators", "Energy Metering Point Operation Directory");
        directories.put("gridOperators", "Energy Grid Operation Directory");
        directories.put("contractors", "Energy Contractor Directory");
        directories.put("dues", "Energy Governmental Dues Directory");
        DIRECTORIES = Collections.unmodifiableMap(directories);

        Map<String, List<String>> create = new LinkedHashMap<>();
        create.put("suppliers", Arrays.asList("Electricity Supply Contractor", "Gas Supply Contractor",
                "Community Heating Supply Contractor"));
        create.put("meteringOperators", Arrays.asList("Electricity Metering Point Operator",
                "Gas Metering Point Operator", "Community Heating Metering Point Operator"));
        create.put("gridOperators", Arrays.asList("Electricity Grid Operator", "Gas Grid Operator",
                "Community Heating Grid Operator"));
        create.put("contractors", Arrays.asList("Energy Supply Contractor", "Energy Metering Point Operation Contractor",
                "Energy Grid Operation Contractor"));
        create.put("dues", Collections.singletonList(ResourceWebContracts.CLASS_DUES));
        CREATE_CLASSES = Collections.unmodifiableMap(create);

        Map<String, String> contractors = new LinkedHashMap<>();
        contractors.put("suppliers", "Energy Supply Contractor");
        contractors.put("meteringOperators", "Energy Metering Point Operation Contractor");
        contractors.put("gridOperators", "Energy Grid Operation Contractor");
        CONTRACTOR_CLASSES = Collections.unmodifiableMap(contractors);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object CREATE_LOCK = new Object();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("contracts/contractors")
    @Produces(MediaType.APPLICATION_JSON)
    public Response contractors(@Context HttpHeaders httpHeaders, @QueryParam("class") String className) {
        return WebEdit.run(httpHeaders, "load the contractors", store -> contractors(store, className));
    }

    @POST
    @Path("contracts/objects")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang, String body) {
        return WebEdit.run(httpHeaders, "create a contract object",
                store -> create(store, translations(store, lang), body, DateTime.now()));
    }

    @PUT
    @Path("contracts/objects/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        return WebEdit.run(httpHeaders, "change the contract object " + id,
                store -> update(store, translations(store, lang), id, body, DateTime.now()));
    }

    @POST
    @Path("contracts/objects/{id}/values")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response addValue(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                             @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "enter a value of " + id,
                store -> addValue(store, id, body, language, DateTime.now()));
    }

    @DELETE
    @Path("contracts/objects/{id}/values")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteValue(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                                @QueryParam("attribute") String attribute, @QueryParam("timestamp") String timestamp) {
        return WebEdit.run(httpHeaders, "delete a value of " + id,
                store -> deleteValue(store, id, attribute, timestamp));
    }

    @DELETE
    @Path("contracts/objects/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version, @QueryParam("confirm") String confirm,
                           @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "delete the contract object " + id,
                store -> delete(store, translations(store, lang), id, version, "true".equalsIgnoreCase(confirm)));
    }

    private static ResourceWebRegisters.Translations translations(WebEdit.Store store, String lang) {
        return new ResourceWebRegisters.Translations(ResourceI18n.cached(), ResourceWebRegisters.language(lang),
                store.classes());
    }

    // ---------------------------------------------------------------- groups, classes, rights

    /**
     * @return the group kind of the class (the first group whose base class it is or inherits from), null if none
     */
    static String kindOf(ResourceWebRegisters.Classes classes, String className) {
        for (Map.Entry<String, String> group : ResourceWebContracts.GROUPS.entrySet()) {
            if (classes.withHeirs(group.getValue()).contains(className)) return group.getKey();
        }
        return null;
    }

    /**
     * @return the classes of the group's "New" dialog that exist
     */
    static List<String> createClasses(String kind, ResourceWebRegisters.Classes classes) {
        List<String> result = new ArrayList<>();
        for (String className : CREATE_CLASSES.getOrDefault(kind, Collections.emptyList())) {
            if (classes.exists(className)) result.add(className);
        }
        return result;
    }

    /**
     * @return the readable directories of the group's directory class outside the recycle bin where the user may
     * create at least one of the group's classes
     */
    static List<JsonObject> createTargets(String kind, ResourceWebModules.Lookup lookup,
                                          ResourceWebRegisters.Classes classes, WebEdit.Rights rights) {
        List<JsonObject> result = new ArrayList<>();
        String directoryClass = DIRECTORIES.get(kind);
        List<String> creatable = createClasses(kind, classes);
        if (directoryClass == null || creatable.isEmpty()) return result;
        for (JsonObject directory : lookup.readableOfClass(directoryClass)) {
            if (ResourceWebIso.isDeleted(directory)) continue;
            for (String className : creatable) {
                if (rights.canCreate(directory, className)) {
                    result.add(directory);
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Adds {@code canCreate}, {@code createTargets} and {@code classes} (the classes the user may create in at least
     * one target) to a group of {@code GET /contracts}.
     *
     * @return whether the user may create objects of the group
     */
    static boolean addCreateInfo(Map<String, Object> group, String kind, ResourceWebModules.Lookup lookup,
                                 ResourceWebRegisters.Classes classes, ResourceWebRegisters.Translations translations,
                                 WebEdit.Rights rights, Set<String> buildingClasses) {
        List<JsonObject> targets = createTargets(kind, lookup, classes, rights);
        List<Map<String, Object>> targetList = new ArrayList<>();
        for (JsonObject directory : targets) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", directory.getId());
            json.put("name", directory.getName());
            json.put("path", ResourceWebNotes.path(directory, lookup, buildingClasses));
            json.put("site", ResourceWebNotes.siteJson(directory, lookup, buildingClasses));
            targetList.add(json);
        }
        targetList.sort(ResourceWebReports.order());
        List<Map<String, Object>> classList = new ArrayList<>();
        for (String className : createClasses(kind, classes)) {
            boolean allowed = false;
            for (JsonObject directory : targets) allowed |= rights.canCreate(directory, className);
            if (!allowed) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("class", className);
            json.put("label", translations.className(className));
            classList.add(json);
        }
        group.put("canCreate", !targetList.isEmpty());
        group.put("createTargets", targetList);
        group.put("classes", classList);
        return !targetList.isEmpty();
    }

    /**
     * @return whether the web edits attributes of the type: numbers (prices, by {@code POST …/values}), ranging values
     * and texts; not files, passwords, dates, booleans, selections and targets
     */
    static boolean editable(JsonType type) {
        String kind = ResourceWebContracts.attributeType(type);
        if (kind == null) return false;
        switch (kind) {
            case ResourceWebContracts.TYPE_NUMBER:
                return !"TARGET".equals(ResourceWebRegisters.columnType(type));
            case ResourceWebContracts.TYPE_RANGING:
                return true;
            default:
                String column = ResourceWebRegisters.columnType(type);
                return type.getPrimitiveType() == JEVisConstants.PrimitiveType.STRING
                        && ("TEXT".equals(column) || "TEXTAREA".equals(column));
        }
    }

    /**
     * Adds the edit fields of one attribute of {@code GET /contracts}: {@code editable}, {@code inputUnit} (numbers
     * only), {@code primitive}, {@code guiType}.
     */
    static void addAttributeInfo(Map<String, Object> entry, JsonType type, JsonAttribute attribute) {
        String kind = ResourceWebContracts.attributeType(type);
        entry.put("editable", editable(type));
        entry.put("inputUnit", ResourceWebContracts.TYPE_NUMBER.equals(kind)
                ? ResourceWebRegisters.inputUnitLabel(attribute) : null);
        entry.put("primitive", ResourceWebRegisters.primitiveTypeName(type.getPrimitiveType()));
        entry.put("guiType", type.getGuiType());
    }

    /**
     * @return the version of an object: the latest sample of all its attributes but passwords (as the registers of
     * E5), ISO in the server time zone; {@value #VERSION_NONE} without samples
     */
    static String version(Map<String, JsonAttribute> attributes, ResourceWebRegisters.Classes classes, String className) {
        long max = Long.MIN_VALUE;
        for (String name : ResourceWebEditRegisters.versionTypes(classes, className)) {
            JsonAttribute attribute = attributes.get(name);
            JsonSample latest = attribute != null ? attribute.getLatestValue() : null;
            Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (millis != null) max = Math.max(max, millis);
        }
        return max != Long.MIN_VALUE ? WebEdit.iso(max, DateTimeZone.getDefault()) : VERSION_NONE;
    }

    /**
     * Adds {@code contractorClass}, {@code canWrite}, {@code canDelete} and {@code version} to an object of
     * {@code GET /contracts}.
     */
    static void addObjectInfo(Map<String, Object> json, JsonObject object, String kind,
                              Map<String, JsonAttribute> attributes, ResourceWebRegisters.Classes classes,
                              WebEdit.Rights rights) {
        json.put("contractorClass", contractorClass(kind, object, classes));
        json.put("canWrite", rights.canWrite(object));
        json.put("canDelete", rights.canDelete(object));
        json.put("version", version(attributes, classes, object.getJevisClass()));
    }

    /**
     * @return the class of the selectable contractors of the object, null for contractors, dues and classes without
     * "Contractor"
     */
    static String contractorClass(String kind, JsonObject object, ResourceWebRegisters.Classes classes) {
        String contractorClass = kind != null ? CONTRACTOR_CLASSES.get(kind) : null;
        if (contractorClass == null) return null;
        return classes.type(object.getJevisClass(), ResourceWebContracts.ATTRIBUTE_CONTRACTOR) != null
                ? contractorClass : null;
    }

    /**
     * @return the object in the read format of {@code GET /contracts} with the edit fields, read freshly
     */
    static Map<String, Object> objectJson(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                          JsonObject object) throws Exception {
        ResourceWebRegisters.Classes classes = store.classes();
        return ResourceWebContracts.objectJson(object, store.lookup(), classes, translations,
                ResourceWebEditInvoices.contractsStore(store), classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING),
                kindOf(classes, object.getJevisClass()), store.rights());
    }

    // ---------------------------------------------------------------- contractors

    /**
     * {@code GET /contracts/contractors?class=…}: readable objects of the class and its subclasses outside the
     * recycle bin
     */
    static WebEdit.Reply contractors(WebEdit.Store store, String className) throws Exception {
        ResourceWebRegisters.Classes classes = store.classes();
        if (className == null || !classes.exists(className)
                || !classes.withHeirs(ResourceWebContracts.CLASS_CONTRACTOR).contains(className)) {
            return WebEdit.Reply.notFound("Unknown class");
        }
        ResourceWebModules.Lookup lookup = store.lookup();
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(className))) {
            if (ResourceWebIso.isDeleted(object)) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.put("name", object.getName());
            json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
            json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
            list.add(json);
        }
        list.sort(ResourceWebReports.order());
        return WebEdit.Reply.ok(list);
    }

    // ---------------------------------------------------------------- create

    /**
     * Adds a message if another object below the parent (readable or not, outside the recycle bin) has the name
     * (ignoring case).
     */
    static void checkUnique(ResourceWebModules.Lookup lookup, long parentId, String name, Long self,
                            List<String> messages) {
        if (name == null) return;
        for (JsonObject other : lookup.all()) {
            if (ResourceWebIso.isDeleted(other) || (self != null && other.getId() == self)) continue;
            Long parent = lookup.parent(other.getId());
            if (parent != null && parent == parentId && name.equalsIgnoreCase(String.valueOf(other.getName()).trim())) {
                messages.add("An object named \"" + name + "\" already exists here");
                return;
            }
        }
    }

    /**
     * {@code POST /contracts/objects} with {@code {parentId, class, name}}
     */
    static WebEdit.Reply create(WebEdit.Store store, ResourceWebRegisters.Translations translations, String body,
                                DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
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
        }

        JsonNode classNode = json.get("class");
        String className = classNode != null && classNode.isTextual() && !classNode.asText().trim().isEmpty()
                ? classNode.asText().trim() : null;
        String kind = null;
        if (className == null) {
            messages.add("Missing class");
        } else {
            for (Map.Entry<String, List<String>> entry : CREATE_CLASSES.entrySet()) {
                if (entry.getValue().contains(className)) kind = entry.getKey();
            }
            if (kind == null || !classes.exists(className)) {
                messages.add("Unknown class: " + className);
                kind = null;
            }
        }
        if (parent != null && kind != null && !DIRECTORIES.get(kind).equals(parent.getJevisClass())) {
            messages.add(className + " cannot be created in " + parent.getJevisClass());
        }
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        if (parent != null && messages.isEmpty()) checkUnique(lookup, parent.getId(), name, null, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!store.rights().canCreate(parent, className)) {
            return WebEdit.Reply.forbidden("Not allowed to create this object here");
        }

        JsonObject object;
        synchronized (CREATE_LOCK) {
            /* again inside the lock: two requests with the same name */
            checkUnique(lookup, parent.getId(), name, null, messages);
            if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
            object = store.createObject(name, className, parent.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE));
            lookup.addReadable(object, parent.getId());
        }
        return WebEdit.Reply.created(objectJson(store, translations, object));
    }

    // ---------------------------------------------------------------- PUT

    /**
     * @return the readable object of one of the groups outside the recycle bin, else null
     */
    static JsonObject contractObject(WebEdit.Store store, long id) throws Exception {
        return ResourceWebContracts.contractObject(store.lookup(), store.classes(), id);
    }

    /**
     * @return the current version of the object
     */
    static String currentVersion(WebEdit.Store store, JsonObject object) {
        return version(ResourceWebContracts.attributes(object.getId(), ResourceWebEditInvoices.contractsStore(store)),
                store.classes(), object.getJevisClass());
    }

    /**
     * @return the given type of the class (as listed by {@code GET /contracts}), null if unknown
     */
    static JsonType listedType(ResourceWebRegisters.Classes classes, String className, String name) {
        if (name == null) return null;
        for (JsonType type : ResourceWebContracts.attributeTypes(className, classes)) {
            if (name.equals(type.getName())) return type;
        }
        return null;
    }

    /**
     * One attribute value to write.
     */
    static final class Change {
        final String attribute;
        final int primitiveType;
        final String value;

        Change(String attribute, int primitiveType, String value) {
            this.attribute = attribute;
            this.primitiveType = primitiveType;
            this.value = value;
        }
    }

    /**
     * {@code PUT /contracts/objects/{id}} with {@code {version, name?, contractor?, fields?}}
     */
    static WebEdit.Reply update(WebEdit.Store store, ResourceWebRegisters.Translations translations, long id,
                                String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = contractObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this object");
        JsonNode fields = json.get("fields");
        boolean hasFields = fields != null && !fields.isNull();
        if (!hasFields && !json.has("name") && !json.has("contractor")) return WebEdit.Reply.badRequest("Nothing to change");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");

        ResourceWebRegisters.Classes classes = store.classes();
        List<String> messages = new ArrayList<>();
        String name = json.has("name") ? ResourceWebEditAnalysis.name(json, messages, true) : null;
        if (name != null && messages.isEmpty() && !name.equals(object.getName())) {
            Long parent = store.lookup().parent(id);
            if (parent != null) checkUnique(store.lookup(), parent, name, id, messages);
        }
        List<Change> changes = new ArrayList<>();
        if (json.has("contractor")) {
            Change contractor = contractor(store, object, json.get("contractor"), messages);
            if (contractor != null) changes.add(contractor);
        }
        if (hasFields) {
            if (!fields.isObject()) {
                messages.add("Invalid fields");
            } else {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    Change change = fieldChange(classes, object.getJevisClass(), entry.getKey(), entry.getValue(), messages);
                    if (change != null) changes.add(change);
                }
            }
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);

        if (!ResourceWebEditInvoices.sameVersion(version, currentVersion(store, object))) {
            return WebEdit.Reply.conflict(objectJson(store, translations, object));
        }
        if (!changes.isEmpty()) {
            List<String> attributes = new ArrayList<>();
            for (Change change : changes) attributes.add(change.attribute);
            DateTime time = ResourceWebEditRegisters.saveTime(store, id, attributes, now);
            for (Change change : changes) {
                WebEdit.writeSample(store, id, change.attribute, change.primitiveType,
                        WebEdit.sample(time, change.value, null), ACTION_SAVE);
            }
        }
        if (name != null && !name.equals(object.getName())) {
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        return WebEdit.Reply.ok(objectJson(store, translations, object));
    }

    /**
     * The contractor: a readable object of the contractor class (or a subclass) outside the recycle bin, stored as
     * {@code "<id>"} like the Control Center; null clears it.
     */
    static Change contractor(WebEdit.Store store, JsonObject object, JsonNode value, List<String> messages)
            throws Exception {
        ResourceWebRegisters.Classes classes = store.classes();
        String contractorClass = contractorClass(kindOf(classes, object.getJevisClass()), object, classes);
        JsonType type = classes.type(object.getJevisClass(), ResourceWebContracts.ATTRIBUTE_CONTRACTOR);
        if (contractorClass == null || type == null) {
            messages.add("This object has no contractor");
            return null;
        }
        if (value == null || value.isNull()) return new Change(type.getName(), type.getPrimitiveType(), "");
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            messages.add("contractor: id expected");
            return null;
        }
        JsonObject contractor = WebEdit.readable(store, value.asLong());
        if (contractor == null || !classes.withHeirs(contractorClass).contains(contractor.getJevisClass())) {
            messages.add("contractor: a readable " + contractorClass + " expected");
            return null;
        }
        return new Change(type.getName(), type.getPrimitiveType(), String.valueOf(contractor.getId()));
    }

    /**
     * @return the change of a text or ranging attribute, null if invalid (the problem is added to the messages)
     */
    static Change fieldChange(ResourceWebRegisters.Classes classes, String className, String key, JsonNode value,
                              List<String> messages) {
        if (ResourceWebContracts.ATTRIBUTE_CONTRACTOR.equals(key)) {
            messages.add(key + ": set with \"contractor\"");
            return null;
        }
        JsonType type = listedType(classes, className, key);
        if (type == null) {
            messages.add("Unknown attribute: " + key);
            return null;
        }
        if (!editable(type)) {
            messages.add(key + " cannot be changed");
            return null;
        }
        String kind = ResourceWebContracts.attributeType(type);
        if (ResourceWebContracts.TYPE_NUMBER.equals(kind)) {
            messages.add(key + ": values are entered with POST /contracts/objects/{id}/values");
            return null;
        }
        if (ResourceWebContracts.TYPE_RANGING.equals(kind)) {
            String stored = ranging(key, value, messages);
            return stored != null ? new Change(type.getName(), type.getPrimitiveType(), stored) : null;
        }
        if (value == null || value.isNull()) return new Change(type.getName(), type.getPrimitiveType(), "");
        if (!value.isTextual()) {
            messages.add(key + ": text expected");
            return null;
        }
        if (value.asText().length() > MAX_TEXT_LENGTH) {
            messages.add(key + " longer than " + MAX_TEXT_LENGTH + " characters");
            return null;
        }
        return new Change(type.getName(), type.getPrimitiveType(), value.asText());
    }

    /**
     * Checks ranges {@code [{from, to, value}]} (numbers, {@code from < to}, sorted by {@code from}, not
     * overlapping, 1 to {@value #MAX_RANGES}).
     *
     * @return the JSON of the Control Center's {@code RangingValueEditor}
     * ({@code {"type":"RangingValuesHandler","rangingValues":[…]}}), null if invalid
     */
    static String ranging(String key, JsonNode value, List<String> messages) {
        if (value == null || !value.isArray()) {
            messages.add(key + ": list of {from, to, value} expected");
            return null;
        }
        if (value.size() == 0) {
            messages.add(key + ": at least one range expected");
            return null;
        }
        if (value.size() > MAX_RANGES) {
            messages.add(key + ": at most " + MAX_RANGES + " ranges");
            return null;
        }
        ArrayNode ranges = JsonNodeFactory.instance.arrayNode();
        Double previousTo = null;
        int row = 0;
        for (JsonNode range : value) {
            row++;
            Double from = finite(range, "from");
            Double to = finite(range, "to");
            Double amount = finite(range, "value");
            if (from == null || to == null || amount == null) {
                messages.add(key + ", range " + row + ": numbers from, to and value expected");
                return null;
            }
            if (from >= to) {
                messages.add(key + ", range " + row + ": from must be less than to");
                return null;
            }
            if (previousTo != null && from < previousTo) {
                messages.add(key + ", range " + row + ": ranges must be sorted and must not overlap");
                return null;
            }
            previousTo = to;
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("from", from);
            node.put("to", to);
            node.put("value", amount);
            ranges.add(node);
        }
        ObjectNode handler = JsonNodeFactory.instance.objectNode();
        handler.put("type", RANGING_TYPE);
        handler.set("rangingValues", ranges);
        try {
            return MAPPER.writeValueAsString(handler);
        } catch (Exception e) {
            messages.add(key + ": invalid ranges");
            return null;
        }
    }

    private static Double finite(JsonNode range, String field) {
        JsonNode node = range != null && range.isObject() ? range.get(field) : null;
        if (node == null || !node.isNumber()) return null;
        double value = node.asDouble();
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    // ---------------------------------------------------------------- values with validity date

    /**
     * {@code POST /contracts/objects/{id}/values} with {@code {attribute, validFrom, value, confirm}}: a sample of a
     * number attribute at {@code validFrom} 00:00 in the site's time zone, with the manual entry note of
     * {@code EnterDataDialog}
     */
    static WebEdit.Reply addValue(WebEdit.Store store, long id, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = contractObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this object");

        List<String> messages = new ArrayList<>();
        JsonNode attributeNode = json.get("attribute");
        JsonType type = null;
        if (attributeNode == null || !attributeNode.isTextual() || attributeNode.asText().trim().isEmpty()) {
            messages.add("Missing attribute");
        } else {
            type = listedType(store.classes(), object.getJevisClass(), attributeNode.asText());
            if (type == null || !ResourceWebContracts.TYPE_NUMBER.equals(ResourceWebContracts.attributeType(type))
                    || !editable(type)) {
                messages.add("Unknown number attribute: " + attributeNode.asText());
                type = null;
            }
        }
        JsonNode validFromNode = json.get("validFrom");
        LocalDate validFrom = validFromNode != null && validFromNode.isTextual()
                ? ResourceWebEditRegisters.parseDate(validFromNode.asText().trim()) : null;
        if (validFrom == null) messages.add("validFrom yyyy-MM-dd expected");
        JsonNode valueNode = json.get("value");
        String stored = null;
        Object value = null;
        if (valueNode == null || valueNode.isNull()) {
            messages.add("Missing value");
        } else if (!valueNode.isNumber() || Double.isNaN(valueNode.asDouble()) || Double.isInfinite(valueNode.asDouble())) {
            messages.add("value: number expected");
        } else if (type != null && type.getPrimitiveType() == JEVisConstants.PrimitiveType.LONG) {
            try {
                long number = valueNode.decimalValue().longValueExact();
                stored = Long.toString(number);
                value = number;
            } catch (ArithmeticException e) {
                messages.add("value: integer expected");
            }
        } else {
            /* Double.toString like the Control Center (JEVisSample.setValue(Double)) */
            stored = Double.toString(valueNode.asDouble());
            value = valueNode.asDouble();
        }
        boolean confirm = WebEdit.optionalBool(json, "confirm");
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);

        DateTimeZone zone = store.workDay(object).zone;
        DateTime timestamp = validFrom.toDateTimeAtStartOfDay(zone);
        List<JsonSample> existing = store.samples(id, type.getName(), timestamp, timestamp, 1);
        if (existing != null && !existing.isEmpty() && !confirm) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
            conflict.put("value", value);
            conflict.put("warnings", Collections.singletonList(
                    existsWarning(validFrom, existing.get(0).getValue(), language)));
            return WebEdit.Reply.conflict(conflict);
        }
        WebEdit.writeSample(store, id, type.getName(), type.getPrimitiveType(),
                WebEdit.sample(timestamp, stored, ResourceWebEditValues.manualText(language) + " " + now), ACTION_VALUE);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
        result.put("value", value);
        return WebEdit.Reply.created(result);
    }

    /**
     * @return "Für 01.01.2027 gibt es schon einen Wert: 0,26 – überschreiben?" (English for other languages)
     */
    static String existsWarning(LocalDate day, String existing, String language) {
        boolean german = "de".equals(language);
        String shown = existing != null ? existing.trim() : "";
        Double number = ResourceWebEditValues.parseDouble(shown);
        if (number != null) {
            DecimalFormat format = new DecimalFormat("0.######",
                    DecimalFormatSymbols.getInstance(german ? Locale.GERMAN : Locale.ROOT));
            shown = format.format(BigDecimal.valueOf(number));
        }
        return german
                ? "Für " + day.toString("dd.MM.yyyy") + " gibt es schon einen Wert: " + shown + " – überschreiben?"
                : "There already is a value for " + day + ": " + shown + " – overwrite?";
    }

    /**
     * {@code DELETE /contracts/objects/{id}/values?attribute=…&timestamp=…}: one sample of a number or ranging
     * attribute (the generic sample delete rule)
     */
    static WebEdit.Reply deleteValue(WebEdit.Store store, long id, String attribute, String timestampText)
            throws Exception {
        JsonObject object = contractObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        JsonType type = listedType(store.classes(), object.getJevisClass(), attribute);
        String kind = ResourceWebContracts.attributeType(type);
        if (!ResourceWebContracts.TYPE_NUMBER.equals(kind) && !ResourceWebContracts.TYPE_RANGING.equals(kind)) {
            return WebEdit.Reply.notFound("Attribute not found");
        }
        if (!WebEdit.canDeleteSamples(store.rights(), object)) {
            return WebEdit.Reply.forbidden("Not allowed to delete values of this object");
        }
        DateTime timestamp = WebEdit.timestamp(timestampText, store.workDay(object).zone, "timestamp");
        List<JsonSample> samples = store.samples(id, type.getName(), timestamp, timestamp, 1);
        if (samples == null || samples.isEmpty()) return WebEdit.Reply.notFound("No value at this time");
        WebEdit.deleteSamples(store, id, type.getName(), timestamp, timestamp, ACTION_VALUE_DELETE);
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- delete

    /**
     * @return the configurations and objects that still use the object: readable "Accounting Configuration" objects
     * whose "Template File" names it as an input object ({@code selectedInputs[].objectID}) and readable objects of
     * the groups whose "Contractor" is the object; {@code [{id, name, kind}]}
     */
    static List<Map<String, Object>> usedBy(WebEdit.Store store, JsonObject object) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        ResourceWebContracts.Store read = ResourceWebEditInvoices.contractsStore(store);
        long id = object.getId();
        List<Map<String, Object>> configurations = new ArrayList<>();
        for (JsonObject configuration : ResourceWebRegisters.readableOfClasses(lookup,
                classes.withHeirs(ResourceWebContracts.CLASS_CONFIGURATION))) {
            if (ResourceWebIso.isDeleted(configuration)) continue;
            if (templateInputs(ResourceWebContracts.template(configuration.getId(), read)).contains(id)) {
                configurations.add(usedByJson(configuration, USED_BY_CONFIGURATION));
            }
        }
        List<Map<String, Object>> objects = new ArrayList<>();
        Set<String> groupClasses = ResourceWebContracts.groupClasses(classes);
        Long[] target = new Long[1];
        for (JsonObject other : ResourceWebRegisters.readableOfClasses(lookup, groupClasses)) {
            if (other.getId() == id || ResourceWebIso.isDeleted(other)) continue;
            if (classes.type(other.getJevisClass(), ResourceWebContracts.ATTRIBUTE_CONTRACTOR) == null) continue;
            String value = ResourceWebContracts.latestValue(ResourceWebContracts.attributes(other.getId(), read)
                    .get(ResourceWebContracts.ATTRIBUTE_CONTRACTOR));
            if (ResourceWebRegisters.parseTarget(value, target, new String[1]) && target[0] == id) {
                objects.add(usedByJson(other, USED_BY_OBJECT));
            }
        }
        Comparator<Map<String, Object>> byName = Comparator.comparing(
                (Map<String, Object> m) -> String.valueOf(m.get("name")).toLowerCase(Locale.ROOT))
                .thenComparingLong(m -> (Long) m.get("id"));
        configurations.sort(byName);
        objects.sort(byName);
        List<Map<String, Object>> result = new ArrayList<>(configurations);
        result.addAll(objects);
        return result;
    }

    /**
     * @return the object ids of the template inputs ({@code SelectionTemplate.selectedInputs[].objectID}, numbers or
     * texts)
     */
    static Set<Long> templateInputs(JsonNode template) {
        Set<Long> ids = new HashSet<>();
        JsonNode inputs = template != null ? template.get("selectedInputs") : null;
        if (inputs == null || !inputs.isArray()) return ids;
        for (JsonNode input : inputs) {
            JsonNode objectId = input != null ? input.get("objectID") : null;
            if (objectId == null || objectId.isNull()) continue;
            if (objectId.isIntegralNumber() && objectId.canConvertToLong()) {
                ids.add(objectId.asLong());
            } else if (objectId.isTextual()) {
                try {
                    ids.add(Long.parseLong(objectId.asText().trim()));
                } catch (NumberFormatException e) {
                    // not an id
                }
            }
        }
        return ids;
    }

    private static Map<String, Object> usedByJson(JsonObject object, String kind) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("kind", kind);
        return json;
    }

    /**
     * {@code DELETE /contracts/objects/{id}?version=…&confirm=…}: to the recycle bin as the Control Center
     * ({@code deleteObject(id, false)})
     */
    static WebEdit.Reply delete(WebEdit.Store store, ResourceWebRegisters.Translations translations, long id,
                                String version, boolean confirm) throws Exception {
        JsonObject object = contractObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this object");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!ResourceWebEditInvoices.sameVersion(version, currentVersion(store, object))) {
            return WebEdit.Reply.conflict(objectJson(store, translations, object));
        }
        if (!confirm) {
            List<Map<String, Object>> usedBy = usedBy(store, object);
            if (!usedBy.isEmpty()) return WebEdit.Reply.conflict(Collections.singletonMap("usedBy", usedBy));
        }
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }
}
