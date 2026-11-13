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
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.GUIConstants;
import org.jevis.commons.object.MeterQrPayload;
import org.jevis.commons.object.ValueFormat;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.jevis.rest.ResourceI18n;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meters, base data and equipment for the web app, view only (see docs/WEB-API.md, W4.3): tables over the native
 * attributes of the objects like the Control Center's "Meter Plugin" ({@code MeterController}, {@code MeterList},
 * {@code MeterTable}), "Base Data Plugin" and "Equipment Plugin".
 * <p>
 * Only objects the user may read are returned; objects in the recycle bin are left out. Names of parents, targets and
 * sites are only given if the user may read them. Class and attribute names are translated with the files of the
 * i18n directory (as served by {@link ResourceI18n}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebRegisters {

    static final String KIND_METERS = "meters";
    static final String KIND_BASEDATA = "basedata";
    static final String KIND_EQUIPMENT = "equipment";
    static final String CLASS_MEASUREMENT_DIRECTORY = "Measurement Directory";
    static final String CLASS_MEASUREMENT_INSTRUMENT = "Measurement Instrument";
    static final String CLASS_BASE_DATA = "Base Data";
    static final String CLASS_EQUIPMENT = "Building Equipment";
    static final String ATTRIBUTE_ONLINE_ID = "Online ID";
    static final String ATTRIBUTE_VERIFICATION_DATE = "Verification Date";
    static final String ATTRIBUTE_VALUE = "Value";
    static final String ATTRIBUTE_PERIOD = "Period";
    static final int DEFAULT_HISTORY = 12;
    static final int MAX_HISTORY = 100;
    static final String SVG = "image/svg+xml";

    /**
     * Columns shown by default in the meter table ({@code MeterTable.defaultList})
     */
    static final List<String> METER_DEFAULT_COLUMNS = Collections.unmodifiableList(Arrays.asList("Picture",
            "Measuring Point Name", "Serial Number", "Type", "Datasheet", "Accuracy", "Conversion Factor",
            "Installation Date", "Verified", "Verification Date", "Online ID", "Verification Documentation"));

    /**
     * Medium of a meter class ({@code MediumColumn}); subclasses inherit the medium of their parent class
     */
    static final Map<String, String> MEDIA;

    private static final Logger logger = LogManager.getLogger(ResourceWebRegisters.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TARGET = Pattern.compile("^\\s*(\\d+)\\s*(?::([^;]*))?");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2,3}$");
    private static final Pattern HOST = Pattern.compile("^(?:[A-Za-z0-9.\\-]+|\\[[0-9A-Fa-f:.]+\\])(?::(\\d{1,5}))?$");

    static {
        Map<String, String> media = new LinkedHashMap<>();
        media.put("Air Measurement Instrument", "AIR");
        media.put("Compressed-Air Measurement Instrument", "COMPRESSED_AIR");
        media.put("Electricity Measurement Instrument", "ELECTRICITY");
        media.put("Gas Measurement Instrument", "GAS");
        media.put("Heat Measurement Instrument", "HEAT");
        media.put("Nitrogen Measurement Instrument", "NITROGEN");
        media.put("Water Measurement Instrument", "WATER");
        MEDIA = Collections.unmodifiableMap(media);
    }

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("registers/{kind}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @PathParam("kind") String kind,
                         @QueryParam("lang") String lang) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            if (!Arrays.asList(KIND_METERS, KIND_BASEDATA, KIND_EQUIPMENT).contains(kind)) return notFound("Unknown kind");
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Classes classes = Classes.fromCache();
            Translations translations = new Translations(ResourceI18n.cached(), language(lang), classes);
            SqlData data = new SqlData(ds);
            LocalDate today = LocalDate.now();
            List<Map<String, Object>> groups;
            if (KIND_METERS.equals(kind)) {
                groups = meterGroups(lookup, classes, translations, data, today);
            } else if (KIND_BASEDATA.equals(kind)) {
                groups = baseDataGroups(lookup, classes, translations, data);
            } else {
                groups = equipmentGroups(lookup, classes, translations, data);
            }
            WebEdit.Rights rights = WebEdit.sqlRights(ds.getUserManager());
            addCanEnter(kind, groups, lookup, rights);
            ResourceWebEditRegisters.addEditInfo(kind, groups, lookup, classes, rights, data);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("kind", kind);
            result.put("today", today.toString());
            result.put("groups", groups);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the {}", kind, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("registers/objects/{id}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response history(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                            @QueryParam("limit") @DefaultValue("12") int limit) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Classes classes = Classes.fromCache();
            JsonObject object = registerObject(lookup, classes, id);
            if (object == null) return notFound("Object not found");
            Long targetId;
            String attribute;
            if (CLASS_BASE_DATA.equals(object.getJevisClass())) {
                targetId = object.getId();
                attribute = ATTRIBUTE_VALUE;
            } else if (classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT).contains(object.getJevisClass())) {
                JsonSample online = ds.getLastSample(id, ATTRIBUTE_ONLINE_ID);
                Long[] target = new Long[1];
                String[] targetAttribute = new String[1];
                if (!parseTarget(online != null ? online.getValue() : null, target, targetAttribute)) {
                    return json(historyJson(null, Collections.emptyList()));
                }
                if (lookup.readable(target[0]) == null) return notFound("Target not found");
                targetId = target[0];
                attribute = targetAttribute[0] != null ? targetAttribute[0] : MeterQrPayload.DEFAULT_TARGET_ATTRIBUTE;
            } else {
                return notFound("No data");
            }
            List<JsonSample> samples = ds.getLatestSamples(targetId, attribute, historyLimit(limit));
            return json(historyJson(unitLabel(ds.getAttribute(targetId, attribute)), samples, lookup.readable(targetId),
                    attribute, WebEdit.sqlRights(ds.getUserManager())));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the history of {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("registers/objects/{id}/files/{attribute}")
    public Response file(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                         @PathParam("attribute") String attributeName,
                         @QueryParam("inline") @DefaultValue("false") boolean inline) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Classes classes = Classes.fromCache();
            JsonObject object = registerObject(lookup, classes, id);
            JsonType type = object != null ? classes.type(object.getJevisClass(), attributeName) : null;
            if (type == null || type.getPrimitiveType() != JEVisConstants.PrimitiveType.FILE) {
                return notFound("File not found");
            }
            JEVisObject jevisObject = new LocalDataSource(ds).getObject(id);
            JEVisAttribute attribute = jevisObject != null ? jevisObject.getAttribute(type.getName()) : null;
            JEVisSample sample = attribute != null && attribute.hasSample() ? attribute.getLatestSample() : null;
            JEVisFile file = sample != null ? sample.getValueAsFile() : null;
            if (file == null || file.getBytes() == null) return notFound("File not found");
            String name = file.getFilename();
            if (name == null || name.trim().isEmpty()) name = sample.getValueAsString();
            if (name == null || name.trim().isEmpty()) name = type.getName();
            return fileResponse(file.getBytes(), name, inline);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the file {} of {}", attributeName, id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("registers/meters/{id}/qr.svg")
    @Produces(SVG)
    public Response qr(@Context HttpHeaders httpHeaders, @Context UriInfo uriInfo, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Classes classes = Classes.fromCache();
            JsonObject meter = registerObject(lookup, classes, id);
            if (meter == null || !classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT).contains(meter.getJevisClass())) {
                return notFound("Meter not found");
            }
            JsonSample online = ds.getLastSample(id, ATTRIBUTE_ONLINE_ID);
            String server = serverAddress(name -> first(httpHeaders.getRequestHeader(name)), uriInfo.getRequestUri());
            String payload = qrPayload(id, online != null ? online.getValue() : null, lookup, server);
            return Response.ok(svg(encode(payload))).type(SVG)
                    .header("Cache-Control", "private, max-age=300")
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not create the QR code of {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @param readable the objects the user may read
     * @return {@code meters}, {@code basedata}, {@code equipment}: number of readable objects (not in the recycle bin)
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable, Classes classes) {
        Set<String> meterClasses = classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT);
        Set<String> equipmentClasses = classes.withHeirs(CLASS_EQUIPMENT);
        int meters = 0;
        int baseData = 0;
        int equipment = 0;
        for (JsonObject object : readable) {
            if (ResourceWebIso.isDeleted(object)) continue;
            String jevisClass = object.getJevisClass();
            if (meterClasses.contains(jevisClass)) {
                meters++;
            } else if (CLASS_BASE_DATA.equals(jevisClass)) {
                baseData++;
            } else if (equipmentClasses.contains(jevisClass)) {
                equipment++;
            }
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put(KIND_METERS, meters);
        modules.put(KIND_BASEDATA, baseData);
        modules.put(KIND_EQUIPMENT, equipment);
        return modules;
    }

    // ---------------------------------------------------------------- groups

    /**
     * One group per "Measurement Directory" with all meters below it ({@code MeterList.load}: meters of all
     * subclasses, also in subfolders and below other meters).
     */
    static List<Map<String, Object>> meterGroups(ResourceWebModules.Lookup lookup, Classes classes,
                                                 Translations translations, Data data, LocalDate today) {
        Set<String> meterClasses = classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT);
        Map<JsonObject, List<JsonObject>> metersByDirectory = new LinkedHashMap<>();
        for (JsonObject directory : readableOfClasses(lookup, Collections.singleton(CLASS_MEASUREMENT_DIRECTORY))) {
            metersByDirectory.put(directory, below(directory, meterClasses, lookup));
        }

        /* the decimal places of a data object can also come from another meter pointing to it (ValueFormat) */
        Map<Long, Integer> decimalsByTarget = new HashMap<>();
        for (List<JsonObject> meters : metersByDirectory.values()) {
            for (JsonObject meter : meters) {
                Map<String, JsonAttribute> attributes = data.attributes(meter.getId());
                Integer decimals = ValueFormat.parse(text(attributes, ValueFormat.ATTRIBUTE_NAME));
                Long[] target = new Long[1];
                if (decimals != null && parseTarget(text(attributes, ATTRIBUTE_ONLINE_ID), target, new String[1])) {
                    decimalsByTarget.putIfAbsent(target[0], decimals);
                }
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<JsonObject, List<JsonObject>> entry : metersByDirectory.entrySet()) {
            groups.add(meterGroup(entry.getKey(), entry.getValue(), lookup, classes, translations, data,
                    decimalsByTarget, today));
        }
        sortGroups(groups);
        return groups;
    }

    /**
     * The group of one "Measurement Directory" (stage E5: also for the answer of a single changed meter; then the
     * decimal places of other meters pointing to the same data are only taken from the meters of this group).
     *
     * @param directory        the directory, null for meters outside of any "Measurement Directory" (group key "")
     * @param meters           the readable meters of the group
     * @param decimalsByTarget decimal places of meters by the data object their "Online ID" points to; null to take
     *                         them from the meters of the group
     */
    static Map<String, Object> meterGroup(JsonObject directory, List<JsonObject> meters,
                                          ResourceWebModules.Lookup lookup, Classes classes, Translations translations,
                                          Data data, Map<Long, Integer> decimalsByTarget, LocalDate today) {
        if (decimalsByTarget == null) {
            decimalsByTarget = new HashMap<>();
            for (JsonObject meter : meters) {
                Map<String, JsonAttribute> attributes = data.attributes(meter.getId());
                Integer decimals = ValueFormat.parse(text(attributes, ValueFormat.ATTRIBUTE_NAME));
                Long[] target = new Long[1];
                if (decimals != null && parseTarget(text(attributes, ATTRIBUTE_ONLINE_ID), target, new String[1])) {
                    decimalsByTarget.putIfAbsent(target[0], decimals);
                }
            }
        }
        List<String> meterClassNames = new ArrayList<>();
        List<Map<String, JsonAttribute>> attributes = new ArrayList<>();
        for (JsonObject meter : meters) {
            if (!meterClassNames.contains(meter.getJevisClass())) meterClassNames.add(meter.getJevisClass());
            attributes.add(data.attributes(meter.getId()));
        }
        List<Map<String, Object>> columns = columns(meterClassNames, classes, translations,
                METER_DEFAULT_COLUMNS::contains, attributes);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < meters.size(); i++) {
            rows.add(meterRow(meters.get(i), attributes.get(i), columns, lookup, classes, translations, data,
                    decimalsByTarget, today));
        }
        if (directory == null) return group("", "", null, columns, rows);
        JsonObject building = nearestOfClass(directory, ResourceWebNavigation.CLASS_BUILDING, lookup);
        return group(String.valueOf(directory.getId()),
                building != null ? building.getName() : directory.getName(), lookup.site(directory), columns, rows);
    }

    /**
     * One group per parent folder of the "Base Data" objects ({@code BaseDataPlugin.getAllBaseData}).
     */
    static List<Map<String, Object>> baseDataGroups(ResourceWebModules.Lookup lookup, Classes classes,
                                                    Translations translations, Data data) {
        Map<Long, List<JsonObject>> byParent = new LinkedHashMap<>();
        for (JsonObject object : readableOfClasses(lookup, Collections.singleton(CLASS_BASE_DATA))) {
            Long parent = lookup.parent(object.getId());
            byParent.computeIfAbsent(parent != null ? parent : 0L, k -> new ArrayList<>()).add(object);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<Long, List<JsonObject>> entry : byParent.entrySet()) {
            JsonObject folder = lookup.readable(entry.getKey());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonObject object : entry.getValue()) {
                rows.add(baseDataRow(object, data.attributes(object.getId()), lookup, translations));
            }
            JsonObject first = entry.getValue().get(0);
            groups.add(group(String.valueOf(entry.getKey()), folder != null ? folder.getName() : "",
                    lookup.site(folder != null ? folder : first), new ArrayList<>(), rows));
        }
        sortGroups(groups);
        return groups;
    }

    /**
     * One group per equipment class ("Building Equipment" and its subclasses, {@code EquipmentPlugin.getAllEquipment}),
     * with the types of the class as columns.
     */
    static List<Map<String, Object>> equipmentGroups(ResourceWebModules.Lookup lookup, Classes classes,
                                                     Translations translations, Data data) {
        Map<String, List<JsonObject>> byClass = new LinkedHashMap<>();
        for (JsonObject object : readableOfClasses(lookup, classes.withHeirs(CLASS_EQUIPMENT))) {
            byClass.computeIfAbsent(object.getJevisClass(), k -> new ArrayList<>()).add(object);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<String, List<JsonObject>> entry : byClass.entrySet()) {
            groups.add(equipmentGroup(entry.getKey(), entry.getValue(), lookup, classes, translations, data));
        }
        sortGroups(groups);
        return groups;
    }

    /**
     * The group of one equipment class with its readable objects.
     */
    static Map<String, Object> equipmentGroup(String className, List<JsonObject> objects,
                                              ResourceWebModules.Lookup lookup, Classes classes,
                                              Translations translations, Data data) {
        List<Map<String, JsonAttribute>> attributes = new ArrayList<>();
        for (JsonObject object : objects) attributes.add(data.attributes(object.getId()));
        List<Map<String, Object>> columns = columns(Collections.singletonList(className), classes,
                translations, name -> true, attributes);
        Set<String> typeNames = typeNames(classes, className);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            JsonObject object = objects.get(i);
            Map<String, Object> row = rowHeader(object, lookup, translations);
            row.put("values", values(columns, attributes.get(i), lookup, typeNames));
            rows.add(row);
        }
        return group(className, translations.className(className), null, columns, rows);
    }

    private static Map<String, Object> group(String key, String name, Map<String, Object> site,
                                             List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
        rows.sort(Comparator.comparing((Map<String, Object> r) -> lower(r.get("name")))
                .thenComparingLong(r -> (Long) r.get("id")));
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("key", key);
        group.put("name", name);
        group.put("site", site);
        group.put("columns", columns);
        group.put("rows", rows);
        return group;
    }

    private static void sortGroups(List<Map<String, Object>> groups) {
        groups.sort(Comparator.comparing((Map<String, Object> g) -> lower(g.get("name")))
                .thenComparing(g -> String.valueOf(g.get("key"))));
    }

    private static String lower(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * @return the readable objects of the classes that are not in the recycle bin
     */
    static List<JsonObject> readableOfClasses(ResourceWebModules.Lookup lookup, Collection<String> classNames) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject object : lookup.readable()) {
            if (classNames.contains(object.getJevisClass()) && !ResourceWebIso.isDeleted(object)) result.add(object);
        }
        return result;
    }

    /**
     * @return the readable objects of the classes below the object, at any depth (only through readable objects that
     * are not in the recycle bin)
     */
    static List<JsonObject> below(JsonObject root, Collection<String> classNames, ResourceWebModules.Lookup lookup) {
        List<JsonObject> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(root.getId());
        Deque<JsonObject> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            JsonObject current = stack.pop();
            List<JsonObject> children = new ArrayList<>(lookup.readableChildren(current.getId()));
            Collections.reverse(children);
            for (JsonObject child : children) {
                if (ResourceWebIso.isDeleted(child) || !visited.add(child.getId())) continue;
                if (classNames.contains(child.getJevisClass())) result.add(child);
                stack.push(child);
            }
        }
        return result;
    }

    /**
     * @return the nearest readable object of the class above the object, or null
     */
    static JsonObject nearestOfClass(JsonObject object, String className, ResourceWebModules.Lookup lookup) {
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null) return null;
            if (className.equals(parent.getJevisClass()) && lookup.readable(current) != null) return parent;
            current = lookup.parent(current);
        }
        return null;
    }

    /**
     * @return the names of the readable parents from the nearest organisation or building down to the parent
     * ({@code "Plant / Meters"}), up to the first parent the user may not read; null if there is none
     */
    static String path(JsonObject object, ResourceWebModules.Lookup lookup) {
        LinkedList<String> names = new LinkedList<>();
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.readable(current);
            if (parent == null) break;
            names.addFirst(parent.getName());
            if (ResourceWebNavigation.CLASS_ORGANIZATION.equals(parent.getJevisClass())
                    || ResourceWebNavigation.CLASS_BUILDING.equals(parent.getJevisClass())) {
                break;
            }
            current = lookup.parent(current);
        }
        return names.isEmpty() ? null : String.join(" / ", names);
    }

    // ---------------------------------------------------------------- rows

    private static Map<String, Object> rowHeader(JsonObject object, ResourceWebModules.Lookup lookup,
                                                 Translations translations) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", object.getId());
        row.put("name", object.getName());
        row.put("className", object.getJevisClass());
        row.put("classLabel", translations.className(object.getJevisClass()));
        row.put("path", path(object, lookup));
        return row;
    }

    /**
     * @param attributes       attributes of the meter by name
     * @param columns          columns of the group
     * @param decimalsByTarget decimal places of meters by the data object their "Online ID" points to
     */
    static Map<String, Object> meterRow(JsonObject meter, Map<String, JsonAttribute> attributes,
                                        List<Map<String, Object>> columns, ResourceWebModules.Lookup lookup,
                                        Classes classes, Translations translations, Data data,
                                        Map<Long, Integer> decimalsByTarget, LocalDate today) {
        Map<String, Object> row = rowHeader(meter, lookup, translations);
        row.put("medium", medium(meter.getJevisClass(), classes));
        row.put("values", values(columns, attributes, lookup, typeNames(classes, meter.getJevisClass())));

        Long[] targetId = new Long[1];
        String[] targetAttribute = new String[1];
        Map<String, Object> target = null;
        Map<String, Object> last = null;
        if (parseTarget(text(attributes, ATTRIBUTE_ONLINE_ID), targetId, targetAttribute)) {
            String attributeName = targetAttribute[0] != null ? targetAttribute[0] : MeterQrPayload.DEFAULT_TARGET_ATTRIBUTE;
            JsonObject targetObject = lookup.readable(targetId[0]);
            target = new LinkedHashMap<>();
            target.put("id", targetId[0]);
            target.put("attribute", attributeName);
            target.put("name", targetObject != null ? targetObject.getName() : null);
            target.put("readable", targetObject != null);
            if (targetObject != null) {
                JsonAttribute attribute = data.attributes(targetId[0]).get(attributeName);
                JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
                if (sample != null) {
                    Integer decimals = ValueFormat.parse(text(attributes, ValueFormat.ATTRIBUTE_NAME));
                    if (decimals == null) decimals = data.decimals(targetId[0], decimalsByTarget);
                    last = lastJson(sample, unitLabel(attribute));
                    last.put("decimals", decimals != null ? decimals : ValueFormat.DEFAULT_DECIMALS);
                }
            }
        }
        row.put("target", target);
        row.put("last", last);

        LocalDate due = parseDate(text(attributes, ATTRIBUTE_VERIFICATION_DATE));
        Map<String, Object> verification = new LinkedHashMap<>();
        verification.put("due", due != null ? due.toString() : null);
        verification.put("overdue", due != null && due.isBefore(today));
        row.put("verification", verification);
        return row;
    }

    /**
     * Base data: fixed columns; {@code last} is the latest sample of "Value" with display unit and the "Period".
     */
    static Map<String, Object> baseDataRow(JsonObject object, Map<String, JsonAttribute> attributes,
                                           ResourceWebModules.Lookup lookup, Translations translations) {
        Map<String, Object> row = rowHeader(object, lookup, translations);
        row.put("values", new LinkedHashMap<String, Object>());
        JsonAttribute value = attributes.get(ATTRIBUTE_VALUE);
        JsonSample sample = value != null ? value.getLatestValue() : null;
        Map<String, Object> last = null;
        if (sample != null) {
            last = lastJson(sample, unitLabel(value));
            String period = text(attributes, ATTRIBUTE_PERIOD);
            last.put("period", period != null && !period.trim().isEmpty() ? period.trim() : null);
        }
        row.put("last", last);
        return row;
    }

    private static Map<String, Object> lastJson(JsonSample sample, String unit) {
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("value", number(sample.getValue()));
        last.put("unit", unit);
        last.put("timestamp", isoTimestamp(sample.getTs()));
        return last;
    }

    /**
     * @return the medium of the meter class or of its nearest parent class with a medium, else null
     */
    static String medium(String className, Classes classes) {
        for (String name : classes.chain(className)) {
            String medium = MEDIA.get(name);
            if (medium != null) return medium;
        }
        return null;
    }

    // ---------------------------------------------------------------- columns and values

    /**
     * @param classNames the classes of the rows (their types are joined, first class wins)
     * @param visible    shown by default (by type name)
     * @param attributes attributes of the rows (for the unit of number columns)
     * @return {@code key, name, type, unit, position, visible}, sorted by GUI position; passwords are left out
     */
    static List<Map<String, Object>> columns(List<String> classNames, Classes classes, Translations translations,
                                             Predicate<String> visible, Collection<Map<String, JsonAttribute>> attributes) {
        Map<String, JsonType> types = new LinkedHashMap<>();
        Map<String, String> classOfType = new HashMap<>();
        for (String className : classNames) {
            for (JsonType type : classes.types(className)) {
                if (type.getName() == null || types.containsKey(type.getName()) || columnType(type) == null) continue;
                types.put(type.getName(), type);
                classOfType.put(type.getName(), className);
            }
        }
        List<JsonType> sorted = new ArrayList<>(types.values());
        sorted.sort(Comparator.comparingInt(JsonType::getGUIPosition));
        List<Map<String, Object>> columns = new ArrayList<>();
        for (JsonType type : sorted) {
            String columnType = columnType(type);
            String unit = null;
            if ("NUMBER".equals(columnType) || "INTEGER".equals(columnType)) {
                for (Map<String, JsonAttribute> rowAttributes : attributes) {
                    JsonAttribute attribute = rowAttributes.get(type.getName());
                    if (attribute != null && attribute.getLatestValue() != null) {
                        unit = unitLabel(attribute);
                        if (unit != null) break;
                    }
                }
            }
            String inputUnit = null;
            if ("NUMBER".equals(columnType) || "INTEGER".equals(columnType)) {
                for (Map<String, JsonAttribute> rowAttributes : attributes) {
                    inputUnit = inputUnitLabel(rowAttributes.get(type.getName()));
                    if (inputUnit != null) break;
                }
            }
            Map<String, Object> column = new LinkedHashMap<>();
            column.put("key", type.getName());
            column.put("name", translations.typeName(classOfType.get(type.getName()), type.getName()));
            column.put("type", columnType);
            column.put("unit", unit);
            column.put("position", type.getGUIPosition());
            column.put("visible", visible.test(type.getName()));
            /* stage E5 */
            column.put("primitiveType", primitiveTypeName(type.getPrimitiveType()));
            column.put("guiType", type.getGuiType());
            column.put("editable", ResourceWebEditRegisters.editable(type));
            column.put("inputUnit", inputUnit);
            column.put("required", false);
            columns.add(column);
        }
        return columns;
    }

    /**
     * @return the name of the primitive type ({@code STRING, DOUBLE, LONG, FILE, BOOLEAN, SELECTION, MULTI_SELECTION,
     * PASSWORD_PBKDF2}), {@code UNKNOWN} for other numbers
     */
    static String primitiveTypeName(int primitiveType) {
        switch (primitiveType) {
            case JEVisConstants.PrimitiveType.STRING:
                return "STRING";
            case JEVisConstants.PrimitiveType.DOUBLE:
                return "DOUBLE";
            case JEVisConstants.PrimitiveType.LONG:
                return "LONG";
            case JEVisConstants.PrimitiveType.FILE:
                return "FILE";
            case JEVisConstants.PrimitiveType.BOOLEAN:
                return "BOOLEAN";
            case JEVisConstants.PrimitiveType.SELECTION:
                return "SELECTION";
            case JEVisConstants.PrimitiveType.MULTI_SELECTION:
                return "MULTI_SELECTION";
            case JEVisConstants.PrimitiveType.PASSWORD_PBKDF2:
                return "PASSWORD_PBKDF2";
            default:
                return "UNKNOWN";
        }
    }

    /**
     * @return the names of the types of the class, null if the class has no types (unknown)
     */
    static Set<String> typeNames(Classes classes, String className) {
        List<JsonType> types = classes.types(className);
        if (types.isEmpty()) return null;
        Set<String> names = new HashSet<>();
        for (JsonType type : types) {
            if (type.getName() != null) names.add(type.getName());
        }
        return names;
    }

    /**
     * @return {@code TEXT, NUMBER, INTEGER, BOOLEAN, DATE, DATETIME, FILE, TARGET, TEXTAREA}, or null for passwords
     */
    static String columnType(JsonType type) {
        String gui = type.getGuiType() != null ? type.getGuiType() : "";
        if (type.getPrimitiveType() == JEVisConstants.PrimitiveType.PASSWORD_PBKDF2
                || "Password".equalsIgnoreCase(type.getName())
                || gui.equals(GUIConstants.BASIC_PASSWORD.getId()) || gui.equals(GUIConstants.PASSWORD_PBKDF2.getId())) {
            return null;
        }
        switch (type.getPrimitiveType()) {
            case JEVisConstants.PrimitiveType.FILE:
                return "FILE";
            case JEVisConstants.PrimitiveType.BOOLEAN:
                return "BOOLEAN";
            case JEVisConstants.PrimitiveType.DOUBLE:
                return "NUMBER";
            case JEVisConstants.PrimitiveType.LONG:
                return gui.equals(GUIConstants.BASIC_TARGET_LONG.getId()) ? "TARGET" : "INTEGER";
            default:
                if (gui.equals(GUIConstants.TARGET_OBJECT.getId()) || gui.equals(GUIConstants.TARGET_ATTRIBUTE.getId())
                        || ATTRIBUTE_ONLINE_ID.equals(type.getName())) {
                    return "TARGET";
                } else if (gui.equals(GUIConstants.DATE_TIME.getId())) {
                    return "DATETIME";
                } else if (gui.equals(GUIConstants.BASIC_TEXT_DATE_FULL.getId())) {
                    return "DATE";
                } else if (gui.equals(GUIConstants.BASIC_TEXT_MULTI.getId())) {
                    return "TEXTAREA";
                }
                return "TEXT";
        }
    }

    /**
     * @param typeNames the types of the row's class (stage E5: their missing or empty values are given as null so the
     *                  editor shows them); null to leave out all missing or empty values
     * @return latest value per column key, typed by the column type; missing or empty values of columns the row's
     * class does not have are left out
     */
    static Map<String, Object> values(List<Map<String, Object>> columns, Map<String, JsonAttribute> attributes,
                                      ResourceWebModules.Lookup lookup, Set<String> typeNames) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> column : columns) {
            String key = (String) column.get("key");
            JsonAttribute attribute = attributes.get(key);
            JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
            Object value = sample != null ? value((String) column.get("type"), sample.getValue(), lookup) : null;
            if (value != null || (typeNames != null && typeNames.contains(key))) values.put(key, value);
        }
        return values;
    }

    /**
     * @param type column type
     * @return the typed value: number, boolean, date {@code yyyy-MM-dd}, {@code {file, image}}, {@code {id, attribute,
     * name, readable}} or the text; values that cannot be converted are given as text; null if empty
     */
    static Object value(String type, String raw, ResourceWebModules.Lookup lookup) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String text = raw.trim();
        switch (type) {
            case "NUMBER":
                return number(text);
            case "INTEGER":
                try {
                    return Long.parseLong(text);
                } catch (NumberFormatException e) {
                    Object number = number(text);
                    return number instanceof Double && ((Double) number) == Math.rint((Double) number)
                            ? (Object) ((Double) number).longValue() : number;
                }
            case "BOOLEAN":
                if ("true".equalsIgnoreCase(text) || "1".equals(text)) return Boolean.TRUE;
                if ("false".equalsIgnoreCase(text) || "0".equals(text)) return Boolean.FALSE;
                return text;
            case "DATE":
            case "DATETIME":
                LocalDate date = parseDate(text);
                return date != null ? date.toString() : text;
            case "FILE":
                Map<String, Object> file = new LinkedHashMap<>();
                file.put("file", text);
                file.put("image", isImageName(text));
                return file;
            case "TARGET":
                Long[] id = new Long[1];
                String[] attribute = new String[1];
                if (!parseTarget(text, id, attribute)) return text;
                JsonObject object = lookup.readable(id[0]);
                Map<String, Object> target = new LinkedHashMap<>();
                target.put("id", id[0]);
                target.put("attribute", attribute[0]);
                target.put("name", object != null ? object.getName() : null);
                target.put("readable", object != null);
                return target;
            default:
                return raw;
        }
    }

    /**
     * @return the value as finite number, else as text (null for NaN/Infinity)
     */
    static Object number(String raw) {
        if (raw == null) return null;
        try {
            double value = Double.parseDouble(raw.trim());
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /**
     * Parses a target ({@code id}, {@code id:Attribute}; of a list separated by {@code ;} the first one).
     *
     * @return true if a target was found; {@code id[0]} and {@code attribute[0]} (null if not given) are set
     */
    static boolean parseTarget(String value, Long[] id, String[] attribute) {
        if (value == null) return false;
        Matcher matcher = TARGET.matcher(value);
        if (!matcher.find()) return false;
        try {
            id[0] = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return false;
        }
        String name = matcher.group(2);
        attribute[0] = name != null && !name.trim().isEmpty() ? name.trim() : null;
        return true;
    }

    /**
     * Dates are stored as ISO 8601 (with time and offset, {@code JEVisDates.DEFAULT_DATE_FORMAT}) or
     * {@code yyyy-MM-dd HH:mm:ss}; the date is taken in the stored offset.
     */
    static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        try {
            return ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(text).toLocalDate();
        } catch (Exception e) {
            // other formats below
        }
        for (String pattern : new String[]{"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd.MM.yyyy"}) {
            try {
                return DateTimeFormat.forPattern(pattern).parseLocalDate(text);
            } catch (Exception e) {
                // next pattern
            }
        }
        return null;
    }

    static boolean isImageName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : new String[]{".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp"}) {
            if (lower.endsWith(extension)) return true;
        }
        return false;
    }

    static String text(Map<String, JsonAttribute> attributes, String name) {
        JsonAttribute attribute = attributes.get(name);
        JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
        return sample != null ? sample.getValue() : null;
    }

    /**
     * @return the label of the display unit, null if none
     */
    static String unitLabel(JsonAttribute attribute) {
        JsonUnit unit = attribute != null ? attribute.getDisplayUnit() : null;
        String label = unit != null ? unit.getLabel() : null;
        // "one" is the label of the dimensionless unit JEVis stores for attributes without a unit
        return label != null && !label.trim().isEmpty() && !"one".equalsIgnoreCase(label.trim()) ? label.trim() : null;
    }

    /**
     * @return the label of the input unit, null if none
     */
    static String inputUnitLabel(JsonAttribute attribute) {
        JsonUnit unit = attribute != null ? attribute.getInputUnit() : null;
        String label = unit != null ? unit.getLabel() : null;
        return label != null && !label.trim().isEmpty() && !"one".equalsIgnoreCase(label.trim()) ? label.trim() : null;
    }

    static String isoTimestamp(String ts) {
        if (ts == null) return null;
        try {
            return ISODateTimeFormat.dateTime().print(JsonFactory.sampleDTF.withOffsetParsed().parseDateTime(ts));
        } catch (Exception e) {
            return ts;
        }
    }

    // ---------------------------------------------------------------- history

    static int historyLimit(int limit) {
        if (limit <= 0) return DEFAULT_HISTORY;
        return Math.min(limit, MAX_HISTORY);
    }

    /**
     * @param samples newest first
     * @return {@code unit, samples: [{timestamp, value, note}]}
     */
    static Map<String, Object> historyJson(String unit, List<JsonSample> samples) {
        return historyJson(unit, samples, null, null, WebEdit.Rights.NONE);
    }

    /**
     * @param row       the row the samples belong to (null if unknown)
     * @param attribute the attribute of the samples
     * @return {@code unit, rowId, samples: [{timestamp, value, note, manual, canDelete}]} (stage E1: {@code manual} if
     * the note starts with the manual entry text, {@code canDelete} if it may be deleted with
     * {@code DELETE /rows/{id}/values})
     */
    static Map<String, Object> historyJson(String unit, List<JsonSample> samples, JsonObject row, String attribute,
                                           WebEdit.Rights rights) {
        boolean rowDeletable = row != null && ATTRIBUTE_VALUE.equals(attribute)
                && WebEdit.MANUAL_ROW_CLASSES.contains(row.getJevisClass()) && !ResourceWebIso.isDeleted(row)
                && WebEdit.canDeleteSamples(rights, row);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonSample sample : samples) {
            boolean manual = ResourceWebEditValues.isManual(sample.getNote());
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("timestamp", isoTimestamp(sample.getTs()));
            json.put("value", number(sample.getValue()));
            json.put("note", sample.getNote() != null && !sample.getNote().trim().isEmpty() ? sample.getNote() : null);
            json.put("manual", manual);
            json.put("canDelete", manual && rowDeletable);
            list.add(json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", unit);
        result.put("rowId", row != null ? row.getId() : null);
        result.put("samples", list);
        return result;
    }

    /**
     * Stage E1: {@code canEnter} per row of meters (for the data row its "Online ID" points to, only if that target is
     * the attribute "Value") and base data, so the web app needs no {@code /entry} call to show the button.
     */
    @SuppressWarnings("unchecked")
    static void addCanEnter(String kind, List<Map<String, Object>> groups, ResourceWebModules.Lookup lookup,
                            WebEdit.Rights rights) {
        if (!KIND_METERS.equals(kind) && !KIND_BASEDATA.equals(kind)) return;
        for (Map<String, Object> group : groups) {
            for (Map<String, Object> row : (List<Map<String, Object>>) group.get("rows")) {
                Object id = row.get("id");
                if (KIND_METERS.equals(kind)) {
                    Map<String, Object> target = (Map<String, Object>) row.get("target");
                    boolean value = target != null && ATTRIBUTE_VALUE.equals(target.get("attribute"));
                    id = value ? target.get("id") : null;
                }
                row.put("canEnter", id instanceof Long && WebEdit.canEnter(lookup.readable((Long) id), rights));
            }
        }
    }

    // ---------------------------------------------------------------- files

    /**
     * @return the object if the user may read it, it is not in the recycle bin and it is a meter, base data or
     * equipment object; else null
     */
    static JsonObject registerObject(ResourceWebModules.Lookup lookup, Classes classes, long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        String jevisClass = object.getJevisClass();
        if (CLASS_BASE_DATA.equals(jevisClass) || classes.withHeirs(CLASS_MEASUREMENT_INSTRUMENT).contains(jevisClass)
                || classes.withHeirs(CLASS_EQUIPMENT).contains(jevisClass)) {
            return object;
        }
        return null;
    }

    /**
     * @return the image content type detected from the first bytes (PNG, JPEG, GIF, WebP, BMP), else null; SVG is
     * not treated as image since it can contain scripts
     */
    static String imageType(byte[] bytes) {
        if (bytes == null) return null;
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (startsWith(bytes, 0, 'G', 'I', 'F', '8', '7', 'a') || startsWith(bytes, 0, 'G', 'I', 'F', '8', '9', 'a')) {
            return "image/gif";
        }
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) return "image/webp";
        if (startsWith(bytes, 0, 'B', 'M') && bytes.length > 14) return "image/bmp";
        return null;
    }

    private static boolean startsWith(byte[] bytes, int offset, int... prefix) {
        if (bytes.length < offset + prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[offset + i] & 0xFF) != prefix[i]) return false;
        }
        return true;
    }

    /**
     * Images (detected from the bytes) are given inline if requested, everything else as attachment.
     */
    static Response fileResponse(byte[] bytes, String fileName, boolean inline) {
        String image = imageType(bytes);
        boolean showInline = inline && image != null;
        String type = image != null ? image : ResourceWebIso.contentType(ResourceWebIso.safeFileName(fileName));
        if (image == null && type.startsWith("image/")) {
            /* an attachment named like an image whose bytes are no image (e.g. SVG) */
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return Response.ok(bytes).type(type)
                .header("Content-Disposition", ResourceWebIso.contentDisposition(fileName, showInline))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .build();
    }

    // ---------------------------------------------------------------- QR code

    /**
     * @param onlineId value of the meter's "Online ID"; the target is only part of the code if the user may read it
     */
    static String qrPayload(long meterId, String onlineId, ResourceWebModules.Lookup lookup, String server) {
        Long[] targetId = new Long[1];
        String[] attribute = new String[1];
        if (parseTarget(onlineId, targetId, attribute) && lookup.readable(targetId[0]) != null) {
            return MeterQrPayload.payload(meterId, targetId[0],
                    attribute[0] != null ? attribute[0] : MeterQrPayload.DEFAULT_TARGET_ATTRIBUTE, server);
        }
        return MeterQrPayload.payload(meterId, null, null, server);
    }

    /**
     * The address of the web service as seen by the client, like the Control Center's host setting
     * ({@code https://example.org:443}): from {@code X-Forwarded-Proto}/{@code X-Forwarded-Host}
     * ({@code X-Forwarded-Port}) behind a proxy, else from the request URL.
     */
    static String serverAddress(Function<String, String> header, URI requestUri) {
        String proto = first(header.apply("X-Forwarded-Proto"));
        String host = first(header.apply("X-Forwarded-Host"));
        String port = first(header.apply("X-Forwarded-Port"));
        String scheme = requestUri != null && requestUri.getScheme() != null ? requestUri.getScheme().toLowerCase(Locale.ROOT) : "http";
        boolean forwardedScheme = false;
        if (proto != null && (proto.equalsIgnoreCase("http") || proto.equalsIgnoreCase("https"))) {
            forwardedScheme = !proto.equalsIgnoreCase(scheme);
            scheme = proto.toLowerCase(Locale.ROOT);
        }
        String defaultPort = "https".equals(scheme) ? "443" : "80";
        if (port == null || !port.matches("\\d{1,5}")) port = null;

        if (host != null) {
            Matcher matcher = HOST.matcher(host);
            if (matcher.matches()) {
                return scheme + "://" + (matcher.group(1) != null ? host : host + ":" + (port != null ? port : defaultPort));
            }
        }
        if (requestUri == null || requestUri.getHost() == null) return null;
        String requestHost = requestUri.getHost();
        if (requestHost.contains(":") && !requestHost.startsWith("[")) requestHost = "[" + requestHost + "]";
        String requestPort;
        if (port != null) {
            requestPort = port;
        } else if (forwardedScheme || requestUri.getPort() < 0) {
            requestPort = defaultPort;
        } else {
            requestPort = String.valueOf(requestUri.getPort());
        }
        return scheme + "://" + requestHost + ":" + requestPort;
    }

    /**
     * @return the first value of a header that may hold a comma separated list, trimmed; null if empty
     */
    static String first(String value) {
        if (value == null) return null;
        int comma = value.indexOf(',');
        String first = (comma >= 0 ? value.substring(0, comma) : value).trim();
        return first.isEmpty() ? null : first;
    }

    private static String first(List<String> values) {
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    static BitMatrix encode(String payload) throws WriterException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 4);
        hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        return new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 0, 0, hints);
    }

    /**
     * @return the matrix as SVG, one unit per module (dark modules as one path, runs of a row joined)
     */
    static String svg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < width && matrix.get(x, y)) x++;
                path.append('M').append(start).append(' ').append(y)
                        .append('h').append(x - start).append("v1h-").append(x - start).append('z');
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height + "\" width=\""
                + width * 8 + "\" height=\"" + height * 8 + "\" shape-rendering=\"crispEdges\">"
                + "<rect width=\"" + width + "\" height=\"" + height + "\" fill=\"#fff\"/>"
                + "<path fill=\"#000\" d=\"" + path + "\"/></svg>\n";
    }

    // ---------------------------------------------------------------- helpers

    /**
     * @return the language part of the parameter ({@code de-DE} → {@code de}), {@code en} if not set or invalid
     */
    static String language(String lang) {
        if (lang == null) return "en";
        String language = lang.trim().toLowerCase(Locale.ROOT);
        int separator = language.indexOf('-') >= 0 ? language.indexOf('-') : language.indexOf('_');
        if (separator >= 0) language = language.substring(0, separator);
        return LANGUAGE.matcher(language).matches() ? language : "en";
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    /**
     * Attributes of the objects of one request.
     */
    interface Data {
        /**
         * @return the attributes (with latest value and display unit) of a readable object by name
         */
        Map<String, JsonAttribute> attributes(long id);

        /**
         * @param meterDecimalsByTarget decimal places of meters by the data object their "Online ID" points to
         * @return the configured decimal places of a readable data object ({@link ValueFormat}), null if none
         */
        Integer decimals(long id, Map<Long, Integer> meterDecimalsByTarget);
    }

    static final class SqlData implements Data {
        private final SQLDataSource ds;
        private final Map<Long, Map<String, JsonAttribute>> cache = new HashMap<>();
        private LocalDataSource local;

        SqlData(SQLDataSource ds) {
            this.ds = ds;
        }

        @Override
        public Map<String, JsonAttribute> attributes(long id) {
            return cache.computeIfAbsent(id, key -> {
                Map<String, JsonAttribute> attributes = new HashMap<>();
                for (JsonAttribute attribute : ds.getAttributes(key)) {
                    if (attribute.getType() != null) attributes.put(attribute.getType(), attribute);
                }
                return attributes;
            });
        }

        @Override
        public Integer decimals(long id, Map<Long, Integer> meterDecimalsByTarget) {
            try {
                if (local == null) local = new LocalDataSource(ds);
                JEVisObject object = local.getObject(id);
                return object != null ? ValueFormat.configuredDecimals(object, meterDecimalsByTarget) : null;
            } catch (Exception e) {
                logger.debug("Could not determine the decimal places of {}", id, e);
                return null;
            }
        }
    }

    /**
     * Classes and their inheritance (from the class cache).
     */
    static final class Classes {
        private final Map<String, JsonJEVisClass> classes;

        Classes(Map<String, JsonJEVisClass> classes) {
            this.classes = classes;
        }

        /**
         * @return the classes of the class cache; none if it cannot be read
         */
        static Classes fromCache() {
            try {
                return new Classes(new HashMap<>(Config.getClassCache()));
            } catch (Exception e) {
                logger.debug("Could not read the class cache", e);
                return new Classes(Collections.emptyMap());
            }
        }

        /**
         * @return whether the class is known
         */
        boolean exists(String className) {
            return className != null && classes.containsKey(className);
        }

        /**
         * @return the classes with an OK_PARENT relationship from the class itself (not inherited) to them, found in
         * any class ({@code EquipmentDialog}: possible parents)
         */
        Set<String> okParents(String className) {
            Set<String> result = new TreeSet<>();
            for (JsonJEVisClass jevisClass : classes.values()) {
                if (jevisClass == null || jevisClass.getRelationships() == null) continue;
                for (JsonClassRelationship rel : jevisClass.getRelationships()) {
                    if (rel != null && rel.getType() == JEVisConstants.ClassRelationship.OK_PARENT
                            && className != null && className.equals(rel.getStart()) && rel.getEnd() != null) {
                        result.add(rel.getEnd());
                    }
                }
            }
            return result;
        }

        /**
         * Like {@code JEVisClass.getValidParents}: the OK_PARENT ends of the class and of its parent classes and all
         * heirs of these ends.
         */
        Set<String> validParents(String className) {
            Set<String> result = new HashSet<>();
            for (String name : chain(className)) {
                for (String end : okParents(name)) result.addAll(withHeirs(end));
            }
            return result;
        }

        /**
         * @return whether an object of the class may be created below an object of the parent class
         * ({@code JEVisClass.isAllowedUnder})
         */
        boolean isAllowedUnder(String className, String parentClassName) {
            return parentClassName != null && validParents(className).contains(parentClassName);
        }

        /**
         * @return the class this class inherits from, or null
         */
        String superClass(String className) {
            JsonJEVisClass jevisClass = className != null ? classes.get(className) : null;
            if (jevisClass == null || jevisClass.getRelationships() == null) return null;
            for (JsonClassRelationship rel : jevisClass.getRelationships()) {
                if (rel.getType() == JEVisConstants.ClassRelationship.INHERIT && className.equals(rel.getStart())
                        && !className.equals(rel.getEnd())) {
                    return rel.getEnd();
                }
            }
            return null;
        }

        /**
         * @return the class and its parent classes, nearest first
         */
        List<String> chain(String className) {
            List<String> chain = new ArrayList<>();
            String current = className;
            while (current != null && !chain.contains(current)) {
                chain.add(current);
                current = superClass(current);
            }
            return chain;
        }

        /**
         * @return the class and all classes inheriting from it
         */
        Set<String> withHeirs(String className) {
            Set<String> result = new HashSet<>();
            result.add(className);
            for (String name : classes.keySet()) {
                if (chain(name).contains(className)) result.add(name);
            }
            return result;
        }

        /**
         * @return the types of the class (including inherited ones)
         */
        List<JsonType> types(String className) {
            JsonJEVisClass jevisClass = classes.get(className);
            return jevisClass != null && jevisClass.getTypes() != null
                    ? new ArrayList<>(jevisClass.getTypes()) : new ArrayList<>();
        }

        /**
         * @return the type of the class by name (exact, else without case), or null
         */
        JsonType type(String className, String typeName) {
            if (typeName == null) return null;
            List<JsonType> types = types(className);
            for (JsonType type : types) {
                if (typeName.equals(type.getName())) return type;
            }
            for (JsonType type : types) {
                if (typeName.equalsIgnoreCase(type.getName())) return type;
            }
            return null;
        }
    }

    /**
     * Class and type names in one language ({@code I18nWS}): the language, else English, else the technical name;
     * type names are also looked up in the parent classes.
     */
    static final class Translations {
        private final Map<String, JsonI18nClass> byClass = new HashMap<>();
        private final String language;
        private final Classes classes;

        Translations(Collection<JsonI18nClass> translations, String language, Classes classes) {
            for (JsonI18nClass translation : translations) {
                if (translation != null && translation.getJevisclass() != null) {
                    byClass.putIfAbsent(translation.getJevisclass(), translation);
                }
            }
            this.language = language;
            this.classes = classes;
        }

        String className(String className) {
            JsonI18nClass translation = byClass.get(className);
            String name = translation != null ? pick(translation.getNames()) : null;
            return name != null ? name : className;
        }

        String typeName(String className, String typeName) {
            List<String> chain = classes.chain(className);
            for (String lang : new String[]{language, "en"}) {
                for (String name : chain) {
                    JsonI18nClass translation = byClass.get(name);
                    if (translation == null || translation.getTypes() == null) continue;
                    for (JsonI18nType type : translation.getTypes()) {
                        if (type != null && typeName.equalsIgnoreCase(type.getType()) && type.getNames() != null) {
                            String value = type.getNames().get(lang);
                            if (value != null && !value.trim().isEmpty()) return value;
                        }
                    }
                }
            }
            return typeName;
        }

        private String pick(Map<String, String> names) {
            if (names == null) return null;
            String name = names.get(language);
            if (name == null || name.trim().isEmpty()) name = names.get("en");
            return name != null && !name.trim().isEmpty() ? name : null;
        }
    }
}
