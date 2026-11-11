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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.iso.*;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisClassHelper;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Action plans, nonconformities and the legal register (ISO 50001 modules of the Control Center) for the web app,
 * view only, with the attachments of their items (see docs/WEB-API.md).
 * <p>
 * A plan object holds the plan settings in attributes; its items keep their content as JSON in the file attribute
 * "Data" (read by {@code org.jevis.commons.iso}). Items belong to the nearest plan above them. Only plans and items
 * the user may read are returned; objects in the recycle bin are left out. Attachments are the "File" children of an
 * item; they are only delivered if the user may read the file object and the item.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebIso {

    static final String CLASS_FILE = "File";
    static final String ATTRIBUTE_FILE = "File";
    static final List<String> ITEM_CLASSES = Collections.unmodifiableList(Arrays.asList(
            ActionItem.CLASS_NAME, NonconformityItem.CLASS_NAME, Obligation.CLASS_NAME));
    static final List<String> PLAN_CLASSES = Collections.unmodifiableList(Arrays.asList(
            ActionItem.PLAN_CLASS_NAME, NonconformityItem.PLAN_CLASS_NAME, Obligation.PLAN_CLASS_NAME));

    private static final Logger logger = LogManager.getLogger(ResourceWebIso.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> CONTENT_TYPES = new HashMap<>();

    static {
        String[][] types = {
                {"pdf", "application/pdf"}, {"txt", "text/plain"}, {"csv", "text/csv"}, {"json", "application/json"},
                {"xml", "application/xml"}, {"png", "image/png"}, {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"},
                {"gif", "image/gif"}, {"bmp", "image/bmp"}, {"webp", "image/webp"}, {"svg", "image/svg+xml"},
                {"tif", "image/tiff"}, {"tiff", "image/tiff"}, {"zip", "application/zip"},
                {"doc", "application/msword"}, {"xls", "application/vnd.ms-excel"},
                {"ppt", "application/vnd.ms-powerpoint"},
                {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
                {"odt", "application/vnd.oasis.opendocument.text"},
                {"ods", "application/vnd.oasis.opendocument.spreadsheet"},
                {"msg", "application/vnd.ms-outlook"}, {"eml", "message/rfc822"},
        };
        for (String[] type : types) CONTENT_TYPES.put(type[0], type[1]);
    }

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("actionplans")
    @Produces(MediaType.APPLICATION_JSON)
    public Response actionPlans(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Source source = new Source(new LocalDataSource(ds), lookup);
            LocalDate today = LocalDate.now();
            DateTimeZone zone = DateTimeZone.getDefault();
            List<Map<String, Object>> plans = new ArrayList<>();
            for (JsonObject plan : plans(lookup, ActionItem.PLAN_CLASS_NAME)) {
                plans.add(actionPlanJson(plan, name -> source.text(plan.getId(), name),
                        itemsOf(plan, ActionItem.CLASS_NAME, lookup), source::data, source::unit,
                        source::attachmentCount, lookup, today, zone));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("today", today.toString());
            result.put("plans", plans);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the action plans", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("nonconformities")
    @Produces(MediaType.APPLICATION_JSON)
    public Response nonconformities(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Source source = new Source(new LocalDataSource(ds), lookup);
            DateTime now = DateTime.now();
            List<Map<String, Object>> plans = new ArrayList<>();
            for (JsonObject plan : plans(lookup, NonconformityItem.PLAN_CLASS_NAME)) {
                plans.add(nonconformityPlanJson(plan, name -> source.text(plan.getId(), name),
                        itemsOf(plan, NonconformityItem.CLASS_NAME, lookup), source::data, source::attachmentCount,
                        lookup, now));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plans", plans);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the nonconformities", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("legal")
    @Produces(MediaType.APPLICATION_JSON)
    public Response legal(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Source source = new Source(new LocalDataSource(ds), lookup);
            List<Map<String, Object>> plans = new ArrayList<>();
            for (JsonObject plan : plans(lookup, Obligation.PLAN_CLASS_NAME)) {
                plans.add(legalPlanJson(plan, name -> source.text(plan.getId(), name),
                        itemsOf(plan, Obligation.CLASS_NAME, lookup), source::data, source::attachmentCount, lookup));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("plans", plans);
            return json(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the legal register", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("objects/{id}/attachments")
    @Produces(MediaType.APPLICATION_JSON)
    public Response attachments(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            JsonObject item = item(lookup, id);
            if (item == null) return notFound("Object not found");
            List<Map<String, Object>> files = new ArrayList<>();
            for (JsonObject file : attachmentsOf(item.getId(), lookup, fileClasses())) {
                JsonSample sample = null;
                try {
                    sample = ds.getLastSample(file.getId(), ATTRIBUTE_FILE);
                } catch (Exception e) {
                    logger.warn("Could not read the file sample of {}", file.getId(), e);
                }
                files.add(attachmentJson(file, sample, fileSize(file.getId(), sample)));
            }
            files.sort(Comparator.comparing(f -> String.valueOf(f.get("name")).toLowerCase(Locale.ROOT)));
            return json(files);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not list the attachments of {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("files/{id}")
    public Response file(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            if (!isDownloadable(id, lookup, fileClasses())) return notFound("File not found");
            JEVisObject object = new LocalDataSource(ds).getObject(id);
            JEVisAttribute attribute = object != null ? object.getAttribute(ATTRIBUTE_FILE) : null;
            JEVisSample sample = attribute != null && attribute.hasSample() ? attribute.getLatestSample() : null;
            JEVisFile file = sample != null ? sample.getValueAsFile() : null;
            if (file == null || file.getBytes() == null) return notFound("File not found");
            String name = file.getFilename() != null && !file.getFilename().trim().isEmpty()
                    ? file.getFilename() : object.getName();
            return download(file.getBytes(), name);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read file {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- plans and items

    /**
     * @param readable the objects the user may read
     * @return {@code actionPlans}, {@code nonconformities}, {@code legal}: number of readable plans
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable) {
        int actionPlans = 0;
        int nonconformities = 0;
        int legal = 0;
        for (JsonObject object : readable) {
            if (isDeleted(object)) continue;
            if (ActionItem.PLAN_CLASS_NAME.equals(object.getJevisClass())) {
                actionPlans++;
            } else if (NonconformityItem.PLAN_CLASS_NAME.equals(object.getJevisClass())) {
                nonconformities++;
            } else if (Obligation.PLAN_CLASS_NAME.equals(object.getJevisClass())) {
                legal++;
            }
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("actionPlans", actionPlans);
        modules.put("nonconformities", nonconformities);
        modules.put("legal", legal);
        return modules;
    }

    static boolean isDeleted(JsonObject object) {
        return object.getDeleteTS() != null && !object.getDeleteTS().trim().isEmpty();
    }

    /**
     * @return the readable plans of the class (not in the recycle bin), by name
     */
    static List<JsonObject> plans(ResourceWebModules.Lookup lookup, String planClass) {
        List<JsonObject> plans = new ArrayList<>();
        for (JsonObject plan : lookup.readableOfClass(planClass)) {
            if (!isDeleted(plan)) plans.add(plan);
        }
        plans.sort(Comparator.comparing((JsonObject o) -> o.getName() == null ? "" : o.getName().toLowerCase(Locale.ROOT))
                .thenComparingLong(JsonObject::getId));
        return plans;
    }

    /**
     * @return the readable items of the class (not in the recycle bin) whose nearest plan above is the given plan
     */
    static List<JsonObject> itemsOf(JsonObject plan, String itemClass, ResourceWebModules.Lookup lookup) {
        List<JsonObject> items = new ArrayList<>();
        for (JsonObject item : lookup.readableOfClass(itemClass)) {
            if (!isDeleted(item) && Long.valueOf(plan.getId()).equals(planOf(item, lookup))) items.add(item);
        }
        return items;
    }

    /**
     * @return the id of the nearest plan (of any plan class) above the object, or null
     */
    static Long planOf(JsonObject object, ResourceWebModules.Lookup lookup) {
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null) return null;
            if (PLAN_CLASSES.contains(parent.getJevisClass())) return current;
            current = lookup.parent(current);
        }
        return null;
    }

    private static Map<String, Object> planHeader(JsonObject plan, ResourceWebModules.Lookup lookup) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", plan.getId());
        json.put("name", plan.getName());
        json.put("site", lookup.site(plan));
        return json;
    }

    /**
     * @param planAttribute   latest value of an attribute of the plan (by name)
     * @param items           the readable actions of the plan
     * @param dataOf          JSON of an item (attribute "Data")
     * @param unitOf          unit of a data object (null if not readable)
     * @param attachmentCount number of readable attachments of an item
     */
    static Map<String, Object> actionPlanJson(JsonObject plan, Function<String, String> planAttribute,
                                              List<JsonObject> items, Function<Long, String> dataOf,
                                              Function<Long, String> unitOf, ToIntFunction<Long> attachmentCount,
                                              ResourceWebModules.Lookup lookup, LocalDate today, DateTimeZone zone) {
        String prefix = text(planAttribute.apply(ActionItem.PLAN_ATTRIBUTE_PREFIX));
        List<Medium> media = Medium.parseList(planAttribute.apply(ActionItem.PLAN_ATTRIBUTE_MEDIUM));
        List<String> statuses = IsoJson.splitList(planAttribute.apply(ActionItem.PLAN_ATTRIBUTE_STATUS));

        List<ActionItem> parsed = new ArrayList<>();
        List<Map<String, Object>> actions = new ArrayList<>();
        for (JsonObject object : items) {
            ActionItem action = ActionItem.parse(dataOf.apply(object.getId()));
            if (action == null || action.isDeleted()) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.putAll(action.toJson(prefix, media, today, zone, unitOf));
            json.put("attachments", attachmentCount.applyAsInt(object.getId()));
            actions.add(json);
            parsed.add(action);
        }
        sortByNumber(actions, "no");
        /* statuses used by actions but missing in the plan (e.g. the Control Center's "done" in its language) */
        for (ActionItem action : parsed) {
            String status = action.getStatus().trim();
            if (!status.isEmpty() && !statuses.contains(status)) statuses.add(status);
        }

        List<Map<String, Object>> mediaJson = new ArrayList<>();
        for (Medium medium : media) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", medium.getId());
            m.put("name", medium.getName());
            m.put("co2", medium.getCo2());
            mediaJson.add(m);
        }

        Map<String, Object> json = planHeader(plan, lookup);
        json.put("prefix", prefix);
        json.put("statuses", statuses);
        json.put("fields", IsoJson.splitList(planAttribute.apply(ActionItem.PLAN_ATTRIBUTE_FIELDS)));
        json.put("seus", IsoJson.splitList(planAttribute.apply(ActionItem.PLAN_ATTRIBUTE_SEU)));
        json.put("media", mediaJson);
        json.put("actions", actions);
        return json;
    }

    /**
     * @param planAttribute   latest value of an attribute of the plan (by name)
     * @param items           the readable nonconformities of the plan
     * @param dataOf          JSON of an item (attribute "Data")
     * @param attachmentCount number of readable attachments of an item
     * @param now             for the warnings and {@code overdue} (its date and time zone)
     */
    static Map<String, Object> nonconformityPlanJson(JsonObject plan, Function<String, String> planAttribute,
                                                     List<JsonObject> items, Function<Long, String> dataOf,
                                                     ToIntFunction<Long> attachmentCount,
                                                     ResourceWebModules.Lookup lookup, DateTime now) {
        String prefix = text(planAttribute.apply(NonconformityItem.PLAN_ATTRIBUTE_PREFIX));
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject object : items) {
            NonconformityItem item = NonconformityItem.parse(dataOf.apply(object.getId()));
            if (item == null || item.isDeleted()) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.putAll(item.toJson(prefix, now, now.toLocalDate(), now.getZone()));
            json.put("attachments", attachmentCount.applyAsInt(object.getId()));
            list.add(json);
        }
        sortByNumber(list, "nr");

        Map<String, Object> json = planHeader(plan, lookup);
        json.put("prefix", prefix);
        json.put("media", IsoJson.splitList(planAttribute.apply(NonconformityItem.PLAN_ATTRIBUTE_MEDIUM)));
        json.put("fields", IsoJson.splitList(planAttribute.apply(NonconformityItem.PLAN_ATTRIBUTE_FIELDS)));
        json.put("seus", IsoJson.splitList(planAttribute.apply(NonconformityItem.PLAN_ATTRIBUTE_SEU)));
        json.put("items", list);
        return json;
    }

    /**
     * @param planAttribute   latest value of an attribute of the plan (by name, matched without case)
     * @param items           the readable obligations of the plan
     * @param dataOf          JSON of an item (attribute "Data")
     * @param attachmentCount number of readable attachments of an item
     */
    static Map<String, Object> legalPlanJson(JsonObject plan, Function<String, String> planAttribute,
                                             List<JsonObject> items, Function<Long, String> dataOf,
                                             ToIntFunction<Long> attachmentCount, ResourceWebModules.Lookup lookup) {
        List<Map<String, Object>> obligations = new ArrayList<>();
        for (JsonObject object : items) {
            Obligation obligation = Obligation.parse(dataOf.apply(object.getId()));
            if (obligation == null || obligation.isDeleted()) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.putAll(obligation.toJson());
            json.put("attachments", attachmentCount.applyAsInt(object.getId()));
            obligations.add(json);
        }
        sortByNumber(obligations, "nr");

        Map<String, Object> json = planHeader(plan, lookup);
        json.put("categories", IsoJson.splitList(planAttribute.apply(Obligation.PLAN_ATTRIBUTE_CATEGORY)));
        json.put("scopes", IsoJson.splitList(planAttribute.apply(Obligation.PLAN_ATTRIBUTE_SCOPE)));
        json.put("obligations", obligations);
        return json;
    }

    /**
     * Sorts by the number (like the Control Center), then by id.
     */
    static void sortByNumber(List<Map<String, Object>> items, String field) {
        items.sort(Comparator.comparingInt((Map<String, Object> e) -> (Integer) e.get(field))
                .thenComparingLong(e -> (Long) e.get("id")));
    }

    private static String text(String value) {
        return value != null ? value.trim() : "";
    }

    // ---------------------------------------------------------------- attachments

    /**
     * The File class and its heirs (from the class cache; "File" only if it cannot be read).
     */
    static Set<String> fileClasses() {
        List<String> classes = new ArrayList<>();
        classes.add(CLASS_FILE);
        try {
            JEVisClassHelper.findHeir(CLASS_FILE, classes);
        } catch (Exception e) {
            logger.debug("Could not read the heirs of {}", CLASS_FILE, e);
        }
        return new HashSet<>(classes);
    }

    /**
     * @return the item (Action, Nonconformity, Obligation) if the user may read it and it is not deleted, else null
     */
    static JsonObject item(ResourceWebModules.Lookup lookup, long id) {
        JsonObject item = lookup.readable(id);
        if (item == null || isDeleted(item) || !ITEM_CLASSES.contains(item.getJevisClass())) return null;
        return item;
    }

    /**
     * @return the readable file children of the item that are not deleted
     */
    static List<JsonObject> attachmentsOf(long itemId, ResourceWebModules.Lookup lookup, Collection<String> fileClasses) {
        List<JsonObject> files = new ArrayList<>();
        for (JsonObject file : lookup.readableChildren(itemId, fileClasses)) {
            if (!isDeleted(file)) files.add(file);
        }
        return files;
    }

    /**
     * A file may be downloaded if the user may read it, it is a file object (not deleted) and its parent is an item
     * the user may read.
     */
    static boolean isDownloadable(long fileId, ResourceWebModules.Lookup lookup, Collection<String> fileClasses) {
        JsonObject file = lookup.readable(fileId);
        if (file == null || isDeleted(file) || !fileClasses.contains(file.getJevisClass())) return false;
        Long parent = lookup.parent(fileId);
        return parent != null && item(lookup, parent) != null;
    }

    /**
     * @param sample latest sample of the attribute "File" (its value is the file name), may be null
     * @param size   size in bytes or null
     * @return {@code id, name, size, date}
     */
    static Map<String, Object> attachmentJson(JsonObject file, JsonSample sample, Long size) {
        Map<String, Object> json = new LinkedHashMap<>();
        String name = sample != null && sample.getValue() != null && !sample.getValue().trim().isEmpty()
                ? sample.getValue() : file.getName();
        json.put("id", file.getId());
        json.put("name", name);
        json.put("size", size);
        json.put("date", sample != null ? isoDate(sample.getTs()) : null);
        return json;
    }

    private static String isoDate(String ts) {
        if (ts == null) return null;
        try {
            return ISODateTimeFormat.dateTime().print(JsonFactory.sampleDTF.withOffsetParsed().parseDateTime(ts));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Size of the stored file of a file sample (stored as {@code <file dir>/<id>/File/<UTC yyyyMMddHHmmss>_<name>}).
     */
    private static Long fileSize(long id, JsonSample sample) {
        try {
            if (sample == null || sample.getValue() == null || sample.getTs() == null) return null;
            String name = sample.getValue();
            if (name.contains("..") || name.contains("/") || name.contains("\\")) return null;
            DateTime ts = JsonFactory.sampleDTF.parseDateTime(sample.getTs());
            File file = new File(new File(new File(Config.getFileDir(), String.valueOf(id)), ATTRIBUTE_FILE),
                    DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(ts) + "_" + name);
            return file.isFile() ? file.length() : null;
        } catch (Exception e) {
            logger.debug("Could not determine the size of file {}", id, e);
            return null;
        }
    }

    /**
     * @return the content type of the file name's extension, {@code application/octet-stream} if unknown
     */
    static String contentType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return MediaType.APPLICATION_OCTET_STREAM;
        String type = CONTENT_TYPES.get(fileName.substring(dot + 1).trim().toLowerCase(Locale.ROOT));
        return type != null ? type : MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * @return the file name without path, control characters, quotes and other characters that are unsafe in a
     * header, {@code download} if nothing is left
     */
    static String safeFileName(String fileName) {
        String name = fileName != null ? fileName : "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || c == '"' || c == '\\' || c == '/' || c == ';' || c == ':'
                    || c == '*' || c == '?' || c == '<' || c == '>' || c == '|' || c == ' ' || c == ' ') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String safe = sb.toString().trim();
        while (safe.startsWith(".")) safe = safe.substring(1);
        return safe.isEmpty() ? "download" : safe;
    }

    /**
     * @return {@code attachment; filename="<ASCII>"; filename*=UTF-8''<percent encoded>} (RFC 6266)
     */
    static String contentDisposition(String fileName) {
        return contentDisposition(fileName, false);
    }

    /**
     * @param inline {@code inline} instead of {@code attachment}
     */
    static String contentDisposition(String fileName, boolean inline) {
        String safe = safeFileName(fileName);
        StringBuilder ascii = new StringBuilder();
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            ascii.append(c >= 0x20 && c < 0x7f ? c : '_');
        }
        String encoded;
        try {
            encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            encoded = ascii.toString();
        }
        return (inline ? "inline" : "attachment") + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    static Response download(byte[] bytes, String fileName) {
        return Response.ok(bytes).type(contentType(safeFileName(fileName)))
                .header("Content-Disposition", contentDisposition(fileName))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .build();
    }

    // ---------------------------------------------------------------- helpers

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN).build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    /**
     * Reads attributes with the rights of the user ({@link LocalDataSource}, attribute names without case).
     */
    private static final class Source {
        private final LocalDataSource local;
        private final ResourceWebModules.Lookup lookup;
        private Set<String> fileClasses;

        Source(LocalDataSource local, ResourceWebModules.Lookup lookup) {
            this.local = local;
            this.lookup = lookup;
        }

        private JEVisSample latest(long id, String attributeName) {
            JEVisObject object = local.getObject(id);
            if (object == null) return null;
            try {
                JEVisAttribute attribute = object.getAttribute(attributeName);
                return attribute != null && attribute.hasSample() ? attribute.getLatestSample() : null;
            } catch (Exception e) {
                logger.debug("Could not read {} of {}", attributeName, id, e);
                return null;
            }
        }

        /**
         * @return the latest value of the attribute as text, or null
         */
        String text(long id, String attributeName) {
            try {
                JEVisSample sample = latest(id, attributeName);
                return sample != null ? sample.getValueAsString() : null;
            } catch (Exception e) {
                logger.debug("Could not read {} of {}", attributeName, id, e);
                return null;
            }
        }

        /**
         * @return the content of the latest file of the attribute "Data" (UTF-8), or null
         */
        String data(long id) {
            try {
                JEVisSample sample = latest(id, IsoJson.ATTRIBUTE_DATA);
                JEVisFile file = sample != null ? sample.getValueAsFile() : null;
                return file != null && file.getBytes() != null ? new String(file.getBytes(), StandardCharsets.UTF_8) : null;
            } catch (Exception e) {
                logger.warn("Could not read the data of {}", id, e);
                return null;
            }
        }

        /**
         * @return the unit of a data object: its attribute "Unit", otherwise the display unit of "Value"; null if the
         * object cannot be read
         */
        String unit(long id) {
            JEVisObject object = local.getObject(id);
            if (object == null) return null;
            String unit = text(id, "Unit");
            if (unit != null && !unit.trim().isEmpty()) return unit.trim();
            try {
                JEVisAttribute value = object.getAttribute("Value");
                JEVisUnit displayUnit = value != null ? value.getDisplayUnit() : null;
                String label = displayUnit != null ? displayUnit.getLabel() : null;
                return label != null && !label.trim().isEmpty() ? label.trim() : null;
            } catch (Exception e) {
                return null;
            }
        }

        int attachmentCount(long itemId) {
            if (fileClasses == null) fileClasses = fileClasses();
            return attachmentsOf(itemId, lookup, fileClasses).size();
        }
    }
}
