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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.constants.GUIConstants;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.ResourceI18n;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Editing of meters and equipment (stage E5, see docs/WEB-API.md): change, create and delete objects, upload files,
 * record a meter exchange; as the Control Center's Meter Plugin ({@code NewMeterDialog},
 * {@code MeterFormAttributeTab}, {@code MeterFormReadingsTab}, {@code MetersToolbar}) and Equipment Plugin
 * ({@code EquipmentDialog}, {@code TablePlugin}, {@code AttributeValueChange}).
 * <p>
 * Values are written as new samples at the time of saving (the previous values stay as history), with the rights of
 * the logged-in user ({@link WebEdit.Store}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditRegisters {

    static final String KIND_METERS = ResourceWebRegisters.KIND_METERS;
    static final String KIND_EQUIPMENT = ResourceWebRegisters.KIND_EQUIPMENT;
    static final String CLASS_MEASUREMENT_DIRECTORY = ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY;
    static final String CLASS_MEASUREMENT_INSTRUMENT = ResourceWebRegisters.CLASS_MEASUREMENT_INSTRUMENT;
    static final String CLASS_EQUIPMENT = ResourceWebRegisters.CLASS_EQUIPMENT;
    static final String ATTRIBUTE_ONLINE_ID = ResourceWebRegisters.ATTRIBUTE_ONLINE_ID;
    static final String ATTRIBUTE_VALUE = ResourceWebRegisters.ATTRIBUTE_VALUE;
    /**
     * Note of both samples of a meter exchange ({@code MeterFormReadingsTab})
     */
    static final String NOTE_EXCHANGE = "Meter Tausch";
    static final String VERSION_NONE = "none";
    static final int MAX_TEXT_LENGTH = 50000;
    /**
     * How far back the last reading before a meter exchange is looked for (plausibility of the old reading)
     */
    static final int EXCHANGE_LOOKBACK_DAYS = 31;
    static final int MAX_LOOKBACK_SAMPLES = 100000;
    static final String ACTION_SAVE = "register-save";
    static final String ACTION_CREATE = "register-create";
    static final String ACTION_DELETE = "register-delete";
    static final String ACTION_FILE = "register-file";
    static final String ACTION_EXCHANGE = "meter-exchange";

    private static final Logger logger = LogManager.getLogger(ResourceWebEditRegisters.class);
    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Object CREATE_LOCK = new Object();

    // ---------------------------------------------------------------- endpoints

    @PUT
    @Path("registers/objects/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        return WebEdit.run(httpHeaders, "change the register object " + id,
                store -> update(store, translations(store, lang), id, body, DateTime.now()));
    }

    @POST
    @Path("registers/{kind}/objects")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @PathParam("kind") String kind,
                           @QueryParam("lang") String lang, String body) {
        return WebEdit.run(httpHeaders, "create a register object",
                store -> create(store, translations(store, lang), kind, body, DateTime.now()));
    }

    @GET
    @Path("registers/meters/create-options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response meterOptions(@Context HttpHeaders httpHeaders, @QueryParam("groupKey") String groupKey,
                                 @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "load the meter create options",
                store -> meterOptions(store, translations(store, lang), groupKey));
    }

    @GET
    @Path("registers/equipment/create-options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response equipmentOptions(@Context HttpHeaders httpHeaders, @QueryParam("class") String className,
                                     @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "load the equipment create options",
                store -> equipmentOptions(store, className));
    }

    @DELETE
    @Path("registers/objects/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version, @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "delete the register object " + id,
                store -> delete(store, translations(store, lang), id, version, DateTime.now()));
    }

    @POST
    @Path("registers/objects/{id}/files/{attribute}")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @PathParam("attribute") String attribute, @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "upload the file " + attribute + " of " + id, store -> {
            String name = file != null && file.getContentDisposition() != null
                    ? ResourceWebEditIso.headerFileName(file.getContentDisposition().getFileName()) : null;
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return upload(store, id, attribute, name, in, ResourceWebEditIso.MAX_FILE_SIZE, DateTime.now());
            }
        });
    }

    @POST
    @Path("registers/meters/{id}/exchange")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exchange(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                             @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "record a meter exchange of " + id,
                store -> exchange(store, id, body, language, DateTime.now()));
    }

    private static ResourceWebRegisters.Translations translations(WebEdit.Store store, String lang) {
        return new ResourceWebRegisters.Translations(ResourceI18n.cached(), ResourceWebRegisters.language(lang),
                store.classes());
    }

    // ---------------------------------------------------------------- objects and types

    static boolean isMeter(ResourceWebRegisters.Classes classes, JsonObject object) {
        return object != null && classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT).contains(object.getJevisClass());
    }

    static boolean isEquipment(ResourceWebRegisters.Classes classes, JsonObject object) {
        return object != null && classes.withHeirs(CLASS_EQUIPMENT).contains(object.getJevisClass());
    }

    /**
     * @return the readable meter or equipment object outside the recycle bin, else null (base data: null)
     */
    static JsonObject editableObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        ResourceWebRegisters.Classes classes = store.classes();
        return isMeter(classes, object) || isEquipment(classes, object) ? object : null;
    }

    /**
     * @return whether the web edits attributes of the type: text, numbers, booleans, dates, data row targets and
     * files (by upload); not passwords, GPS, selections, "Target Selector" (object ids), unknown primitive types and
     * the "Reading Photo" of meters (stage E8.1)
     */
    static boolean editable(JsonType type) {
        if (type == null || ResourceWebRegisters.columnType(type) == null) return false;
        /* stage E8.1: the photos of the readings are written with the readings (POST /readings/{id}/photo) */
        if (ResourceWebReadings.ATTRIBUTE_READING_PHOTO.equals(type.getName())) return false;
        String gui = type.getGuiType() != null ? type.getGuiType() : "";
        if (gui.equals(GUIConstants.GPS.getId())) return false;
        switch (type.getPrimitiveType()) {
            case JEVisConstants.PrimitiveType.STRING:
            case JEVisConstants.PrimitiveType.DOUBLE:
            case JEVisConstants.PrimitiveType.BOOLEAN:
            case JEVisConstants.PrimitiveType.FILE:
                return true;
            case JEVisConstants.PrimitiveType.LONG:
                return !gui.equals(GUIConstants.BASIC_TARGET_LONG.getId());
            default:
                return false;
        }
    }

    /**
     * @return the names of the types of the class that are columns (all but passwords): the attributes of a row's
     * {@code versions}
     */
    static List<String> versionTypes(ResourceWebRegisters.Classes classes, String className) {
        List<String> names = new ArrayList<>();
        for (JsonType type : classes.types(className)) {
            if (type.getName() != null && ResourceWebRegisters.columnType(type) != null) names.add(type.getName());
        }
        return names;
    }

    // ---------------------------------------------------------------- read answers

    /**
     * Adds what the user may do to the groups of {@code GET /registers/{kind}} (meters and equipment): per group
     * {@code canCreate}, {@code createTarget}; per row {@code canWrite}, {@code canDelete}, {@code canUpload},
     * {@code versions}, {@code version} and {@code inputUnits}.
     */
    @SuppressWarnings("unchecked")
    static void addEditInfo(String kind, List<Map<String, Object>> groups, ResourceWebModules.Lookup lookup,
                            ResourceWebRegisters.Classes classes, WebEdit.Rights rights,
                            ResourceWebRegisters.Data data) {
        if (!KIND_METERS.equals(kind) && !KIND_EQUIPMENT.equals(kind)) return;
        for (Map<String, Object> group : groups) {
            String key = (String) group.get("key");
            if (KIND_METERS.equals(kind)) {
                JsonObject directory = groupDirectory(lookup, key);
                group.put("canCreate", directory != null && !creatableMeterClasses(directory, lookup, classes, rights).isEmpty());
                group.put("createTarget", directory != null ? directory.getId() : null);
            } else {
                group.put("canCreate", !equipmentParents(key, lookup, classes, rights).isEmpty());
                group.put("createTarget", null);
            }
            Map<String, String> columnInputUnits = new HashMap<>();
            for (Map<String, Object> column : (List<Map<String, Object>>) group.get("columns")) {
                columnInputUnits.put((String) column.get("key"), (String) column.get("inputUnit"));
            }
            for (Map<String, Object> row : (List<Map<String, Object>>) group.get("rows")) {
                long id = (Long) row.get("id");
                JsonObject object = lookup.readable(id);
                boolean canWrite = object != null && rights.canWrite(object);
                row.put("canWrite", canWrite);
                row.put("canDelete", object != null && rights.canDelete(object));
                row.put("canUpload", canWrite);
                Map<String, JsonAttribute> attributes = data.attributes(id);
                Map<String, Object> versions = new LinkedHashMap<>();
                Map<String, Object> inputUnits = new LinkedHashMap<>();
                long max = Long.MIN_VALUE;
                for (String name : versionTypes(classes, (String) row.get("className"))) {
                    JsonAttribute attribute = attributes.get(name);
                    JsonSample latest = attribute != null ? attribute.getLatestValue() : null;
                    Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
                    versions.put(name, millis != null ? WebEdit.iso(millis, DateTimeZone.getDefault()) : null);
                    if (millis != null) max = Math.max(max, millis);
                    String inputUnit = ResourceWebRegisters.inputUnitLabel(attribute);
                    if (inputUnit != null && !inputUnit.equals(columnInputUnits.get(name))) inputUnits.put(name, inputUnit);
                }
                row.put("versions", versions);
                row.put("version", max != Long.MIN_VALUE ? WebEdit.iso(max, DateTimeZone.getDefault()) : VERSION_NONE);
                row.put("inputUnits", inputUnits);
            }
        }
    }

    /**
     * @return the readable "Measurement Directory" of the group key, else null
     */
    static JsonObject groupDirectory(ResourceWebModules.Lookup lookup, String key) {
        if (key == null || key.isEmpty()) return null;
        try {
            JsonObject directory = lookup.readable(Long.parseLong(key.trim()));
            return directory != null && !ResourceWebIso.isDeleted(directory)
                    && CLASS_MEASUREMENT_DIRECTORY.equals(directory.getJevisClass()) ? directory : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The row of an object in the read format of {@code GET /registers/{kind}} (with the columns of its group).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> rowJson(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                       JsonObject object, LocalDate today) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        ResourceWebRegisters.Data data = store.registerData();
        String kind;
        Map<String, Object> group;
        if (isMeter(classes, object)) {
            kind = KIND_METERS;
            JsonObject directory = ResourceWebRegisters.nearestOfClass(object, CLASS_MEASUREMENT_DIRECTORY, lookup);
            List<JsonObject> meters = directory != null
                    ? ResourceWebRegisters.below(directory, classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT), lookup)
                    : null;
            if (meters == null || !meters.contains(object)) {
                directory = null;
                meters = Collections.singletonList(object);
            }
            group = ResourceWebRegisters.meterGroup(directory, meters, lookup, classes, translations, data, null, today);
        } else {
            kind = KIND_EQUIPMENT;
            group = ResourceWebRegisters.equipmentGroup(object.getJevisClass(),
                    ResourceWebRegisters.readableOfClasses(lookup, Collections.singleton(object.getJevisClass())),
                    lookup, classes, translations, data);
        }
        List<Map<String, Object>> groups = new ArrayList<>(Collections.singletonList(group));
        ResourceWebRegisters.addCanEnter(kind, groups, lookup, store.rights());
        addEditInfo(kind, groups, lookup, classes, store.rights(), data);
        for (Map<String, Object> row : (List<Map<String, Object>>) group.get("rows")) {
            if (Long.valueOf(object.getId()).equals(row.get("id"))) return row;
        }
        throw new IllegalStateException("Row " + object.getId() + " not found in its group");
    }

    // ---------------------------------------------------------------- field values

    /**
     * One attribute value to write.
     */
    static final class Change {
        final JsonType type;
        final String value;
        final JsonNode version;

        Change(JsonType type, String value, JsonNode version) {
            this.type = type;
            this.value = value;
            this.version = version;
        }
    }

    /**
     * Converts the fields of a request to the values to store.
     *
     * @param fields       {@code {"<attribute>": {"version", "value"}}} (PUT) or {@code {"<attribute>": value}} /
     *                     {@code {"<attribute>": {"value": …}}} (create)
     * @param withVersions PUT: every field needs its {@code version}
     * @param meter        dates as the meter form (UTC midnight), else as the equipment table (midnight in the zone)
     * @throws WebEdit.Stop {@code 400 version missing}
     */
    static List<Change> changes(WebEdit.Store store, String className, JsonNode fields, boolean withVersions,
                                boolean meter, DateTimeZone zone, List<String> messages) throws Exception {
        List<Change> changes = new ArrayList<>();
        if (fields == null || fields.isNull()) return changes;
        if (!fields.isObject()) {
            messages.add("Invalid fields");
            return changes;
        }
        if (withVersions) {
            Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
            while (it.hasNext()) {
                JsonNode field = it.next().getValue();
                if (field != null && field.isObject() && !field.has("version")) throw WebEdit.Stop.badRequest("version missing");
            }
        }
        ResourceWebRegisters.Classes classes = store.classes();
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String key = entry.getKey();
            JsonNode field = entry.getValue();
            JsonType type = typeOf(classes, className, key);
            if (type == null) {
                messages.add("Unknown attribute: " + key);
                continue;
            }
            if (!editable(type)) {
                messages.add(key + " cannot be changed");
                continue;
            }
            if (type.getPrimitiveType() == JEVisConstants.PrimitiveType.FILE) {
                messages.add(key + ": files are uploaded with POST /registers/objects/{id}/files/{attribute}");
                continue;
            }
            JsonNode value;
            JsonNode version = null;
            if (withVersions) {
                if (field == null || !field.isObject()) {
                    messages.add(key + ": {version, value} expected");
                    continue;
                }
                if (!field.has("value")) {
                    messages.add(key + ": value missing");
                    continue;
                }
                value = field.get("value");
                version = field.get("version");
            } else {
                value = field != null && field.isObject() && field.has("value") ? field.get("value") : field;
            }
            String stored = storedValue(store, type, value, meter, zone, messages);
            if (stored != null) changes.add(new Change(type, stored, version));
        }
        return changes;
    }

    /**
     * @return the type of the class by its exact name, null if unknown
     */
    static JsonType typeOf(ResourceWebRegisters.Classes classes, String className, String name) {
        for (JsonType type : classes.types(className)) {
            if (name.equals(type.getName())) return type;
        }
        return null;
    }

    /**
     * @return the value to store (the format of the Control Center), null if invalid (the problem is added to the
     * messages); null or an empty text clear the value (empty sample, as the Control Center writes for an emptied
     * field)
     */
    static String storedValue(WebEdit.Store store, JsonType type, JsonNode value, boolean meter, DateTimeZone zone,
                              List<String> messages) throws Exception {
        String key = type.getName();
        if (value == null || value.isNull()) return "";
        String columnType = ResourceWebRegisters.columnType(type);
        switch (columnType != null ? columnType : "") {
            case "TEXT":
            case "TEXTAREA":
                if (!value.isTextual()) break;
                if (value.asText().length() > MAX_TEXT_LENGTH) {
                    messages.add(key + " longer than " + MAX_TEXT_LENGTH + " characters");
                    return null;
                }
                return value.asText();
            case "NUMBER":
                if (!value.isNumber() || Double.isNaN(value.asDouble()) || Double.isInfinite(value.asDouble())) break;
                /* Double.toString like the Control Center (JEVisSample.setValue(Double)) */
                return Double.toString(value.asDouble());
            case "INTEGER":
                if (!value.isNumber()) break;
                BigDecimal number = value.decimalValue();
                try {
                    return Long.toString(number.longValueExact());
                } catch (ArithmeticException e) {
                    break;
                }
            case "BOOLEAN":
                if (!value.isBoolean()) break;
                return String.valueOf(value.asBoolean());
            case "DATE":
            case "DATETIME":
                if (!value.isTextual()) break;
                String text = value.asText().trim();
                if (text.isEmpty()) return "";
                LocalDate date = parseDate(text);
                if (date == null) break;
                return storedDate(date, meter, zone);
            case "TARGET":
                return target(store, key, value, messages);
            default:
                messages.add(key + " cannot be changed");
                return null;
        }
        messages.add(key + ": " + expected(columnType));
        return null;
    }

    private static String expected(String columnType) {
        switch (columnType) {
            case "NUMBER":
                return "number expected";
            case "INTEGER":
                return "integer expected";
            case "BOOLEAN":
                return "true or false expected";
            case "DATE":
            case "DATETIME":
                return "date yyyy-MM-dd expected";
            default:
                return "text expected";
        }
    }

    static LocalDate parseDate(String text) {
        if (!DATE_ONLY.matcher(text).matches()) return null;
        try {
            return LocalDate.parse(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Meters ({@code MeterFormAttributeTab.toDateTime}): midnight UTC, {@code 2026-11-13T00:00:00.000Z}; equipment
     * ({@code TablePlugin}, {@code AttributeValueChange}): midnight in the time zone, ISO with offset,
     * {@code 2026-11-13T00:00:00.000+01:00} (the Control Center takes the zone of the computer, the web the site's).
     */
    static String storedDate(LocalDate date, boolean meter, DateTimeZone zone) {
        DateTimeZone dateZone = meter ? DateTimeZone.UTC : zone;
        return ISODateTimeFormat.dateTime().print(date.toDateTimeAtStartOfDay(dateZone));
    }

    /**
     * A data row target {@code {"id": 123, "attribute": "Value"}} → {@code 123:Value} (the attribute defaults to
     * "Value" as the Control Center's target selection); the row must be readable, outside the recycle bin and of a
     * class the row picker offers.
     */
    static String target(WebEdit.Store store, String key, JsonNode value, List<String> messages) throws Exception {
        JsonNode idNode = value.isObject() ? value.get("id") : null;
        if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
            messages.add(key + ": {id, attribute} expected");
            return null;
        }
        JsonObject row = WebEdit.readable(store, idNode.asLong());
        if (row == null || !ResourceWebEditAnalysis.ROW_CLASSES.contains(row.getJevisClass())) {
            messages.add(key + ": the target must be a readable data row");
            return null;
        }
        JsonNode attributeNode = value.get("attribute");
        String attribute;
        if (attributeNode == null || attributeNode.isNull()) {
            attribute = ATTRIBUTE_VALUE;
        } else if (attributeNode.isTextual() && !attributeNode.asText().trim().isEmpty()) {
            attribute = attributeNode.asText().trim();
        } else {
            messages.add(key + ": invalid attribute");
            return null;
        }
        JsonType type = typeOf(store.classes(), row.getJevisClass(), attribute);
        if (type == null) {
            messages.add(key + ": unknown attribute " + attribute + " of the target");
            return null;
        }
        return row.getId() + ":" + type.getName();
    }

    /**
     * @param version the version sent: null (JSON null) = there was no sample
     * @return whether it is the version of the latest sample
     * @throws WebEdit.Stop {@code 400} for an invalid version
     */
    static boolean sameVersion(JsonNode version, JsonSample latest) throws WebEdit.Stop {
        if (version == null || version.isNull()) return latest == null;
        if (!version.isTextual()) throw WebEdit.Stop.badRequest("Invalid version");
        return WebEdit.sameVersion(version.asText(), latest);
    }

    /**
     * @return the time of the samples of a save: now to the second, after the latest samples of the attributes (waits
     * for the next second if there already is a sample in this second)
     */
    static DateTime saveTime(WebEdit.Store store, long id, Collection<String> attributes, DateTime now) throws Exception {
        DateTime time = now.withMillisOfSecond(0);
        for (String attribute : attributes) {
            JsonSample latest = store.latest(id, attribute);
            Long last = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (last != null && last >= time.getMillis()) {
                time = new DateTime(last, now.getZone()).withMillisOfSecond(0).plusSeconds(1);
            }
        }
        if (time.isAfter(now)) store.waitUntil(time);
        return time;
    }

    private static void write(WebEdit.Store store, long id, List<Change> changes, DateTime time, String action)
            throws Exception {
        for (Change change : changes) {
            WebEdit.writeSample(store, id, change.type.getName(), change.type.getPrimitiveType(),
                    WebEdit.sample(time, change.value, null), action);
        }
    }

    // ---------------------------------------------------------------- PUT

    /**
     * {@code PUT /registers/objects/{id}} with {@code {name?, fields: {"<attribute>": {version, value}}}}
     */
    static WebEdit.Reply update(WebEdit.Store store, ResourceWebRegisters.Translations translations, long id,
                                String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = editableObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this object");
        JsonNode fields = json.get("fields");
        if ((fields == null || fields.isNull()) && !json.has("name")) return WebEdit.Reply.badRequest("Missing fields");

        List<String> messages = new ArrayList<>();
        String name = json.has("name") ? ResourceWebEditAnalysis.name(json, messages, true) : null;
        boolean meter = isMeter(store.classes(), object);
        List<Change> changes = changes(store, object.getJevisClass(), fields, true, meter, store.workDay(object).zone,
                messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);

        LocalDate today = now.toLocalDate();
        for (Change change : changes) {
            if (!sameVersion(change.version, store.latest(id, change.type.getName()))) {
                return WebEdit.Reply.conflict(rowJson(store, translations, object, today));
            }
        }
        if (!changes.isEmpty()) {
            List<String> attributes = new ArrayList<>();
            for (Change change : changes) attributes.add(change.type.getName());
            write(store, id, changes, saveTime(store, id, attributes, now), ACTION_SAVE);
        }
        if (name != null && !name.equals(object.getName())) {
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        return WebEdit.Reply.ok(rowJson(store, translations, object, today));
    }

    // ---------------------------------------------------------------- create

    /**
     * @return the class of the medium directory of a meter class ({@code NewMeterDialog.getDirectoryClass})
     */
    static String directoryClass(String meterClass) {
        return meterClass.replace(" Measurement Instrument", " Measurement Directory");
    }

    /**
     * @return the first (lowest id) readable directory of the class directly below the directory, outside the recycle
     * bin; null if there is none ({@code NewMeterDialog.getDirectory})
     */
    static JsonObject mediumDirectory(JsonObject directory, String directoryClass, ResourceWebModules.Lookup lookup) {
        JsonObject first = null;
        for (JsonObject child : lookup.readableChildren(directory.getId(), Collections.singleton(directoryClass))) {
            if (ResourceWebIso.isDeleted(child)) continue;
            if (first == null || child.getId() < first.getId()) first = child;
        }
        return first;
    }

    /**
     * @return the seven medium classes of {@code NewMeterDialog} the user may create below the group's directory
     * (into the existing medium directory, or by creating it)
     */
    static List<String> creatableMeterClasses(JsonObject directory, ResourceWebModules.Lookup lookup,
                                              ResourceWebRegisters.Classes classes, WebEdit.Rights rights) {
        List<String> result = new ArrayList<>();
        for (String meterClass : ResourceWebRegisters.MEDIA.keySet()) {
            if (!classes.exists(meterClass)) continue;
            String directoryClass = directoryClass(meterClass);
            JsonObject medium = mediumDirectory(directory, directoryClass, lookup);
            boolean allowed = medium != null ? rights.canCreate(medium, meterClass)
                    : classes.exists(directoryClass) && rights.canCreate(directory, directoryClass);
            if (allowed) result.add(meterClass);
        }
        return result;
    }

    /**
     * @return the classes objects of the equipment class may be created below ({@code EquipmentDialog}: OK_PARENT
     * relationships of the class itself; here with the heirs of each parent class)
     */
    static Set<String> equipmentParentClasses(String className, ResourceWebRegisters.Classes classes) {
        Set<String> result = new HashSet<>();
        for (String end : classes.okParents(className)) result.addAll(classes.withHeirs(end));
        result.remove(className);
        return result;
    }

    /**
     * @return the readable objects outside the recycle bin the equipment class may be created below, with create right
     */
    static List<JsonObject> equipmentParents(String className, ResourceWebModules.Lookup lookup,
                                             ResourceWebRegisters.Classes classes, WebEdit.Rights rights) {
        List<JsonObject> result = new ArrayList<>();
        if (className == null) return result;
        Set<String> parentClasses = equipmentParentClasses(className, classes);
        if (parentClasses.isEmpty()) return result;
        for (JsonObject object : lookup.readable()) {
            if (parentClasses.contains(object.getJevisClass()) && !ResourceWebIso.isDeleted(object)
                    && rights.canCreate(object, className)) {
                result.add(object);
            }
        }
        return result;
    }

    /**
     * {@code GET /registers/meters/create-options?groupKey=…}
     */
    static WebEdit.Reply meterOptions(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                      String groupKey) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        JsonObject directory = groupDirectory(lookup, groupKey);
        if (directory == null) return WebEdit.Reply.notFound("Group not found");
        List<Map<String, Object>> classList = new ArrayList<>();
        for (String meterClass : creatableMeterClasses(directory, lookup, classes, store.rights())) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("class", meterClass);
            json.put("label", translations.className(meterClass));
            json.put("medium", ResourceWebRegisters.MEDIA.get(meterClass));
            classList.add(json);
        }
        List<Map<String, Object>> parents = new ArrayList<>();
        for (JsonObject meter : ResourceWebRegisters.below(directory, classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT), lookup)) {
            if (!store.rights().canCreate(meter, meter.getJevisClass())) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", meter.getId());
            json.put("name", meter.getName());
            json.put("class", meter.getJevisClass());
            json.put("path", ResourceWebRegisters.path(meter, lookup));
            parents.add(json);
        }
        parents.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("name")).toLowerCase(Locale.ROOT))
                .thenComparingLong(m -> (Long) m.get("id")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("classes", classList);
        result.put("parents", parents);
        return WebEdit.Reply.ok(result);
    }

    /**
     * {@code GET /registers/equipment/create-options?class=…}
     */
    static WebEdit.Reply equipmentOptions(WebEdit.Store store, String className) throws Exception {
        ResourceWebRegisters.Classes classes = store.classes();
        if (className == null || !classes.exists(className) || !classes.withHeirs(CLASS_EQUIPMENT).contains(className)) {
            return WebEdit.Reply.notFound("Unknown class");
        }
        ResourceWebModules.Lookup lookup = store.lookup();
        List<Map<String, Object>> parents = new ArrayList<>();
        for (JsonObject parent : equipmentParents(className, lookup, classes, store.rights())) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", parent.getId());
            json.put("name", parent.getName());
            json.put("path", ResourceWebRegisters.path(parent, lookup));
            parents.add(json);
        }
        parents.sort(Comparator.comparing((Map<String, Object> m) -> (String.valueOf(m.get("path")) + " / "
                + m.get("name")).toLowerCase(Locale.ROOT)).thenComparingLong(m -> (Long) m.get("id")));
        return WebEdit.Reply.ok(Collections.singletonMap("parents", parents));
    }

    /**
     * {@code POST /registers/{kind}/objects}
     */
    static WebEdit.Reply create(WebEdit.Store store, ResourceWebRegisters.Translations translations, String kind,
                                String body, DateTime now) throws Exception {
        if (!KIND_METERS.equals(kind) && !KIND_EQUIPMENT.equals(kind)) return WebEdit.Reply.notFound("Unknown kind");
        JsonNode json = WebEdit.body(body);
        return KIND_METERS.equals(kind) ? createMeter(store, translations, json, now)
                : createEquipment(store, translations, json, now);
    }

    private static Long longField(JsonNode json, String field, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber() && node.canConvertToLong()) return node.asLong();
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException e) {
                // below
            }
        }
        messages.add("Invalid " + field);
        return null;
    }

    private static String classField(JsonNode json, List<String> messages) {
        JsonNode node = json.get("class");
        if (node == null || node.isNull() || !node.isTextual() || node.asText().trim().isEmpty()) {
            messages.add("Missing class");
            return null;
        }
        return node.asText().trim();
    }

    static WebEdit.Reply createMeter(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                     JsonNode json, DateTime now) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        JsonNode groupNode = json.get("groupKey");
        String groupKey = groupNode != null && !groupNode.isNull() ? groupNode.asText() : null;
        if (groupKey == null || groupKey.trim().isEmpty()) return WebEdit.Reply.badRequest("Missing groupKey");
        JsonObject directory = groupDirectory(lookup, groupKey);
        if (directory == null) return WebEdit.Reply.notFound("Group not found");

        List<String> messages = new ArrayList<>();
        String meterClass = classField(json, messages);
        if (meterClass != null && (!ResourceWebRegisters.MEDIA.containsKey(meterClass) || !classes.exists(meterClass))) {
            messages.add("Unknown class: " + meterClass);
            meterClass = null;
        }
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        Long parentId = longField(json, "parentId", messages);
        JsonObject parent = null;
        if (parentId != null) {
            parent = WebEdit.readable(store, parentId);
            List<JsonObject> meters = ResourceWebRegisters.below(directory, classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT), lookup);
            if (parent == null || !meters.contains(parent)) {
                messages.add("The parent must be a meter of the group");
                parent = null;
            } else if (meterClass != null && !classes.isAllowedUnder(meterClass, parent.getJevisClass())) {
                messages.add(meterClass + " cannot be created below " + parent.getJevisClass());
            }
        }
        String directoryClass = meterClass != null ? directoryClass(meterClass) : null;
        if (meterClass != null && parent == null && parentId == null && !classes.exists(directoryClass)) {
            messages.add("Unknown class: " + directoryClass);
        }
        List<Change> changes = meterClass != null
                ? changes(store, meterClass, json.get("fields"), false, true, store.workDay(directory).zone, messages)
                : Collections.emptyList();
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);

        WebEdit.Rights rights = store.rights();
        JsonObject medium = parent == null ? mediumDirectory(directory, directoryClass, lookup) : null;
        if (parent != null) {
            if (!rights.canCreate(parent, meterClass)) return WebEdit.Reply.forbidden("Not allowed to create meters below this meter");
        } else if (medium != null) {
            if (!rights.canCreate(medium, meterClass)) return WebEdit.Reply.forbidden("Not allowed to create meters in this group");
            if (!classes.isAllowedUnder(meterClass, medium.getJevisClass())) {
                throw ResourceWebEditIso.invalid(Collections.singletonList(meterClass + " cannot be created below " + medium.getJevisClass()));
            }
        } else if (!rights.canCreate(directory, directoryClass)) {
            return WebEdit.Reply.forbidden("Not allowed to create meters in this group");
        }

        synchronized (CREATE_LOCK) {
            JsonObject createdDirectory = null;
            JsonObject meter = null;
            try {
                JsonObject target = parent;
                if (target == null) {
                    target = mediumDirectory(directory, directoryClass, lookup);
                    if (target == null) {
                        createdDirectory = store.createObject(directoryClass, directoryClass, directory.getId());
                        store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT,
                                WebEdit.objectLog(createdDirectory.getId(), createdDirectory.getName(), ACTION_CREATE));
                        lookup.addReadable(createdDirectory, directory.getId());
                        target = createdDirectory;
                        if (!rights.canCreate(target, meterClass)) {
                            throw new WebEdit.Stop(WebEdit.Reply.forbidden("Not allowed to create meters in this group"));
                        }
                    }
                }
                meter = store.createObject(name, meterClass, target.getId());
                store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(meter.getId(), meter.getName(), ACTION_CREATE));
                lookup.addReadable(meter, target.getId());
                write(store, meter.getId(), changes, now.withMillisOfSecond(0), ACTION_CREATE);
            } catch (Exception e) {
                rollback(store, meter);
                rollback(store, createdDirectory);
                throw e;
            }
            return WebEdit.Reply.created(rowJson(store, translations, meter, now.toLocalDate()));
        }
    }

    static WebEdit.Reply createEquipment(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                         JsonNode json, DateTime now) throws Exception {
        ResourceWebRegisters.Classes classes = store.classes();
        List<String> messages = new ArrayList<>();
        String className = classField(json, messages);
        if (className != null && (!classes.exists(className) || !classes.withHeirs(CLASS_EQUIPMENT).contains(className))) {
            messages.add("Unknown class: " + className);
            className = null;
        }
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        Long parentId = longField(json, "parentId", messages);
        JsonObject parent = null;
        if (parentId == null) {
            if (json.get("parentId") == null || json.get("parentId").isNull()) messages.add("Missing parentId");
        } else {
            parent = WebEdit.readable(store, parentId);
            if (parent == null) return WebEdit.Reply.notFound("Parent not found");
            if (className != null && !equipmentParentClasses(className, classes).contains(parent.getJevisClass())) {
                messages.add(className + " cannot be created below " + parent.getJevisClass());
            }
        }
        DateTimeZone zone = parent != null ? store.workDay(parent).zone : DateTimeZone.getDefault();
        List<Change> changes = className != null
                ? changes(store, className, json.get("fields"), false, false, zone, messages)
                : Collections.emptyList();
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!store.rights().canCreate(parent, className)) return WebEdit.Reply.forbidden("Not allowed to create this object here");

        JsonObject object = null;
        try {
            object = store.createObject(name, className, parent.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE));
            store.lookup().addReadable(object, parent.getId());
            write(store, object.getId(), changes, now.withMillisOfSecond(0), ACTION_CREATE);
        } catch (Exception e) {
            rollback(store, object);
            throw e;
        }
        return WebEdit.Reply.created(rowJson(store, translations, object, now.toLocalDate()));
    }

    private static void rollback(WebEdit.Store store, JsonObject object) {
        if (object == null) return;
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE + "-rollback"));
        } catch (Exception e) {
            logger.error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    // ---------------------------------------------------------------- delete

    /**
     * @return the version of the object: the latest sample of all its attributes ({@link #versionTypes}), ISO;
     * {@value #VERSION_NONE} without samples
     */
    static String objectVersion(WebEdit.Store store, JsonObject object) throws Exception {
        long max = Long.MIN_VALUE;
        for (String name : versionTypes(store.classes(), object.getJevisClass())) {
            JsonSample latest = store.latest(object.getId(), name);
            Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (millis != null) max = Math.max(max, millis);
        }
        return max != Long.MIN_VALUE ? WebEdit.iso(max, DateTimeZone.getDefault()) : VERSION_NONE;
    }

    /**
     * {@code DELETE /registers/objects/{id}?version=…}: to the recycle bin as {@code JEVisObject.delete()}
     */
    static WebEdit.Reply delete(WebEdit.Store store, ResourceWebRegisters.Translations translations, long id,
                                String version, DateTime now) throws Exception {
        JsonObject object = editableObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Object not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this object");
        String current = objectVersion(store, object);
        /* an object without any sample (e.g. a new meter) has no version: empty or "none" */
        boolean empty = version == null || version.trim().isEmpty();
        if (empty && !VERSION_NONE.equals(current)) return WebEdit.Reply.badRequest("version missing");
        boolean same;
        if (empty) {
            same = true;
        } else if (VERSION_NONE.equals(version.trim()) || VERSION_NONE.equals(current)) {
            same = version.trim().equals(current);
        } else {
            JsonSample latest = new JsonSample();
            latest.setTs(WebEdit.sampleTs(new DateTime(current)));
            same = WebEdit.sameVersion(version, latest);
        }
        if (!same) return WebEdit.Reply.conflict(rowJson(store, translations, object, now.toLocalDate()));
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- files

    /**
     * {@code POST /registers/objects/{id}/files/{attribute}}: a new sample of the FILE attribute at now
     */
    static WebEdit.Reply upload(WebEdit.Store store, long id, String attributeName, String fileName, InputStream in,
                                int max, DateTime now) throws Exception {
        JsonObject object = editableObject(store, id);
        JsonType type = object != null && attributeName != null
                ? store.classes().type(object.getJevisClass(), attributeName) : null;
        if (type == null || type.getPrimitiveType() != JEVisConstants.PrimitiveType.FILE) {
            return WebEdit.Reply.notFound("File attribute not found");
        }
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this object");
        if (!editable(type)) return WebEdit.Reply.badRequest(type.getName() + " cannot be changed");
        if (in == null) return WebEdit.Reply.badRequest("Missing file");
        byte[] bytes = ResourceWebEditIso.read(in, max);
        if (bytes == null) return new WebEdit.Reply(413, "The file is larger than " + (max / (1024 * 1024)) + " MB");
        String name = ResourceWebEditIso.uploadName(fileName);
        DateTime time = saveTime(store, id, Collections.singletonList(type.getName()), now);
        int count = store.writeFile(id, type.getName(), time, name, bytes);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(id, type.getName(), count, ACTION_FILE));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("attribute", type.getName());
        json.put("file", name);
        json.put("image", ResourceWebRegisters.isImageName(name));
        json.put("version", WebEdit.iso(time.getMillis(), DateTimeZone.getDefault()));
        return WebEdit.Reply.created(json);
    }

    // ---------------------------------------------------------------- meter exchange

    /**
     * {@code POST /registers/meters/{id}/exchange} with {@code {timestamp, oldReading, newReading, confirm}}: the old
     * meter's last reading at {@code timestamp − 1 s} and the new meter's first reading at {@code timestamp}, both
     * with the note {@value #NOTE_EXCHANGE}, into "Value" of the meter's data row ({@code MeterFormReadingsTab}).
     */
    static WebEdit.Reply exchange(WebEdit.Store store, long id, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject meter = WebEdit.readable(store, id);
        if (!isMeter(store.classes(), meter)) return WebEdit.Reply.notFound("Meter not found");
        JsonSample online = store.latest(id, ATTRIBUTE_ONLINE_ID);
        Long[] targetId = new Long[1];
        if (!ResourceWebRegisters.parseTarget(online != null ? online.getValue() : null, targetId, new String[1])) {
            return WebEdit.Reply.badRequest("The meter has no data row (Online ID)");
        }
        JsonObject row = WebEdit.readable(store, targetId[0]);
        if (row == null) return WebEdit.Reply.notFound("Data row not found");
        if (!WebEdit.MANUAL_ROW_CLASSES.contains(row.getJevisClass())) {
            return WebEdit.Reply.badRequest("The data row of the meter takes no manual values");
        }
        if (!store.rights().canWrite(meter) || !store.rights().canWrite(row)) {
            return WebEdit.Reply.forbidden("Not allowed to record a meter exchange");
        }

        ResourceWebEditValues.Info info = ResourceWebEditValues.info(store, row);
        DateTimeZone zone = info.workDay.zone;
        List<String> messages = new ArrayList<>();
        DateTime timestamp = null;
        try {
            timestamp = WebEdit.timestamp(WebEdit.text(json, "timestamp"), zone, "timestamp").withMillisOfSecond(0);
            if (timestamp.isAfter(now)) messages.add("The exchange cannot be in the future");
        } catch (WebEdit.Stop stop) {
            messages.add(String.valueOf(stop.reply.body));
        }
        Double oldReading = reading(json, "oldReading", messages);
        Double newReading = reading(json, "newReading", messages);
        boolean confirm = WebEdit.optionalBool(json, "confirm");
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);

        DateTime before = timestamp.minusSeconds(1);
        List<String> warnings = exchangeWarnings(store, info, before, timestamp, oldReading, language);
        if (!warnings.isEmpty() && !confirm) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
            conflict.put("oldReading", oldReading);
            conflict.put("newReading", newReading);
            conflict.put("warnings", warnings);
            return WebEdit.Reply.conflict(conflict);
        }

        int primitiveType = WebEdit.primitiveType(store, row, ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE);
        WebEdit.writeSample(store, row.getId(), ATTRIBUTE_VALUE, primitiveType,
                WebEdit.sample(before, plain(oldReading), NOTE_EXCHANGE), ACTION_EXCHANGE);
        WebEdit.writeSample(store, row.getId(), ATTRIBUTE_VALUE, primitiveType,
                WebEdit.sample(timestamp, plain(newReading), NOTE_EXCHANGE), ACTION_EXCHANGE);
        ResourceWebEditValues.resetDependents(store, row, before);

        List<Map<String, Object>> samples = new ArrayList<>();
        samples.add(sampleJson(before, oldReading, zone));
        samples.add(sampleJson(timestamp, newReading, zone));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowId", row.getId());
        result.put("samples", samples);
        result.put("warnings", warnings);
        return WebEdit.Reply.created(result);
    }

    private static Double reading(JsonNode json, String field, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            messages.add("Missing " + field);
            return null;
        }
        if (!node.isNumber() || Double.isNaN(node.asDouble()) || Double.isInfinite(node.asDouble())) {
            messages.add("Invalid " + field);
            return null;
        }
        if (node.asDouble() < 0) {
            messages.add(field + " must not be negative");
            return null;
        }
        return node.asDouble();
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static Map<String, Object> sampleJson(DateTime time, double value, DateTimeZone zone) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("timestamp", WebEdit.iso(time.getMillis(), zone));
        json.put("value", value);
        json.put("note", NOTE_EXCHANGE);
        return json;
    }

    /**
     * Plausibility of an exchange (E1 texts): for counters an old reading lower than the last reading within
     * {@value #EXCHANGE_LOOKBACK_DAYS} days before; existing samples at both time stamps (would be overwritten).
     */
    static List<String> exchangeWarnings(WebEdit.Store store, ResourceWebEditValues.Info info, DateTime before,
                                         DateTime timestamp, double oldReading, String language) throws Exception {
        List<String> warnings = new ArrayList<>();
        long id = info.row.getId();
        if (info.counter) {
            List<JsonSample> previous = store.samples(id, ATTRIBUTE_VALUE, before.minusDays(EXCHANGE_LOOKBACK_DAYS),
                    before.minusMillis(1), MAX_LOOKBACK_SAMPLES);
            if (previous != null && !previous.isEmpty()) {
                Double last = ResourceWebEditValues.parseDouble(previous.get(previous.size() - 1).getValue());
                if (last != null && last > oldReading) {
                    warnings.add(ResourceWebEditValues.text(ResourceWebEditValues.KEY_COUNTER_LOWER, language)
                            + " " + ResourceWebEditValues.format(oldReading) + " < " + ResourceWebEditValues.format(last));
                }
            }
        }
        for (DateTime time : Arrays.asList(before, timestamp)) {
            List<JsonSample> existing = store.samples(id, ATTRIBUTE_VALUE, time, time, 1);
            if (existing != null && !existing.isEmpty()) {
                warnings.add(ResourceWebEditValues.text(ResourceWebEditValues.KEY_EXISTS, language) + " "
                        + existing.get(0).getValue());
            }
        }
        return warnings;
    }
}
