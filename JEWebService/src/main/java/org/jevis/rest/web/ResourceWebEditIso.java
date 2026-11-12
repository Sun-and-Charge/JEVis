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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.commons.iso.*;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Write endpoints of the web app for action plans, nonconformities and the legal register (stage E2, see
 * docs/WEB-API.md): create, change and (soft-)delete items, add and remove attachments.
 * <p>
 * The data is written like the Control Center's {@code ActionData}, {@code NonconformityData} and
 * {@code ObligationData}: each save is a new file sample of "Data" with the item JSON, the object is renamed after the
 * number (and title). The stored JSON is changed as a tree ({@link IsoJsonTree}): only the keys sent are touched.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditIso {

    /**
     * Largest attachment
     */
    static final int MAX_FILE_SIZE = 25 * 1024 * 1024;
    static final int MAX_TEXT_LENGTH = 50000;
    static final int MAX_FILE_NAME_LENGTH = 200;
    static final String ACTION_ATTACHMENT_ADD = "attachment-add";
    static final String ACTION_ATTACHMENT_DELETE = "attachment-delete";
    static final String KEY_STATUS_DONE = "plugin.action.status.done";
    static final String KEY_STATUS_OPEN = "plugin.action.status.open";
    static final String KEY_DELETED = "Deleted";
    private static final Pattern DATE_ONLY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    /**
     * One create at a time, so two items do not get the same number
     */
    private static final Object CREATE_LOCK = new Object();

    /**
     * The three item kinds ({@code {kind}} of the paths).
     */
    enum Kind {
        ACTIONS("actions", "action", ActionItem.CLASS_NAME, ActionItem.PLAN_CLASS_NAME, "Action Directory", "actions", "No"),
        NONCONFORMITIES("nonconformities", "nonconformity", NonconformityItem.CLASS_NAME,
                NonconformityItem.PLAN_CLASS_NAME, "Nonconformities Directory", "items", "Nr"),
        OBLIGATIONS("obligations", "obligation", Obligation.CLASS_NAME, Obligation.PLAN_CLASS_NAME,
                "Obligation Directory", "obligations", "Nr");

        final String path;
        final String action;
        final String itemClass;
        final String planClass;
        final String directoryClass;
        /**
         * Key of the item list in the plan JSON of the read answer
         */
        final String listKey;
        /**
         * Key of the number in the stored JSON
         */
        final String numberKey;

        Kind(String path, String action, String itemClass, String planClass, String directoryClass, String listKey,
             String numberKey) {
            this.path = path;
            this.action = action;
            this.itemClass = itemClass;
            this.planClass = planClass;
            this.directoryClass = directoryClass;
            this.listKey = listKey;
            this.numberKey = numberKey;
        }

        static Kind of(String path) {
            for (Kind kind : values()) {
                if (kind.path.equals(path)) return kind;
            }
            return null;
        }
    }

    /**
     * Type of an editable field
     */
    enum Type {
        TEXT, DATE, BOOLEAN, STATUS, ACTION_MEDIUM, NC_MEDIUM, SEU, CATEGORY, SCOPE, FIELDS_JOINED, SEUS_JOINED,
        FIELDS_ARRAY, CHECK_LIST
    }

    static final Map<Kind, Map<String, Type>> FIELDS = new EnumMap<>(Kind.class);

    /**
     * Stored check list keys that may be changed (booleans, {@code CheckLists}); "New EnPI" of actions is kept
     */
    static final List<String> ACTION_CHECKS = Collections.unmodifiableList(Arrays.asList("Process Document",
            "isNeedWorkInstruction", "Test Instruction", "Drawing", "Other", "Additional Action", "Affect Others",
            "Consumption Documented", "Target Reached", "Document Correction Text", "Document Correction",
            "Additional Meter"));
    static final List<String> NONCONFORMITY_CHECKS = Collections.unmodifiableList(Arrays.asList(
            "Immediate Action Required", "Effect on ongoing Processes", "Routinely Affected", "Employee Trained",
            "Documents Changes Needed", "Process Instructions", "Work Instructions", "Test Instructions", "Design",
            "Model", "Miscellaneous", "Metrics"));
    /**
     * Stored legacy spelling of a key (written to if only it exists, so the Control Center keeps reading one value)
     */
    static final Map<String, String> LEGACY_KEYS = Collections.singletonMap("Description", "Desciption");

    static {
        Map<String, Type> action = new LinkedHashMap<>();
        for (String key : Arrays.asList("Title", "Description", "Note", "Responsible", "Distributor")) action.put(key, Type.TEXT);
        action.put("Planned Date", Type.DATE);
        action.put("Done Date", Type.DATE);
        action.put("Status Tags", Type.STATUS);
        action.put("Field Tags", Type.FIELDS_JOINED);
        action.put("Medium Tags", Type.ACTION_MEDIUM);
        action.put("SEU Tags", Type.SEUS_JOINED);
        for (String key : Arrays.asList("Correction", "Follow Action", "Alternative Measures", "Affected Process Note",
                "Energy Flow Note", "Evaluation Note")) {
            action.put(key, Type.TEXT);
        }
        action.put("Check List", Type.CHECK_LIST);
        FIELDS.put(Kind.ACTIONS, Collections.unmodifiableMap(action));

        Map<String, Type> nonconformity = new LinkedHashMap<>();
        for (String key : Arrays.asList("Title", "Responsible Person", "Description", "Cause", "Immediate Measures",
                "Corrective Actions", "Action")) {
            nonconformity.put(key, Type.TEXT);
        }
        nonconformity.put("Medium", Type.NC_MEDIUM);
        nonconformity.put("SEU", Type.SEU);
        nonconformity.put("Field Tags", Type.FIELDS_ARRAY);
        nonconformity.put("Planned Date", Type.DATE);
        nonconformity.put("Done Date", Type.DATE);
        nonconformity.put("Check List", Type.CHECK_LIST);
        FIELDS.put(Kind.NONCONFORMITIES, Collections.unmodifiableMap(nonconformity));

        Map<String, Type> obligation = new LinkedHashMap<>();
        for (String key : Arrays.asList("title", "Designation", "Description", "importance for the company", "link")) {
            obligation.put(key, Type.TEXT);
        }
        obligation.put("category", Type.CATEGORY);
        obligation.put("scope", Type.SCOPE);
        obligation.put("Relevance 50001", Type.BOOLEAN);
        obligation.put("issue date", Type.DATE);
        obligation.put("current version date", Type.DATE);
        obligation.put("Date of Examination", Type.DATE);
        FIELDS.put(Kind.OBLIGATIONS, Collections.unmodifiableMap(obligation));
    }

    // ---------------------------------------------------------------- endpoints

    @POST
    @Path("{kind: actions|nonconformities|obligations}/plans/{planId}/items")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @PathParam("kind") String kind,
                           @PathParam("planId") long planId, @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "create an item in " + planId,
                store -> create(store, Kind.of(kind), planId, body, language, DateTime.now()));
    }

    @PUT
    @Path("{kind: actions|nonconformities|obligations}/items/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("kind") String kind, @PathParam("id") long id,
                           @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "change the item " + id,
                store -> update(store, Kind.of(kind), id, body, language, DateTime.now()));
    }

    @DELETE
    @Path("{kind: actions|nonconformities|obligations}/items/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("kind") String kind, @PathParam("id") long id,
                           @QueryParam("version") String version) {
        return WebEdit.run(httpHeaders, "delete the item " + id,
                store -> delete(store, Kind.of(kind), id, version, DateTime.now()));
    }

    @POST
    @Path("items/{id}/attachments")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response attach(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "add an attachment to " + id, store -> {
            String name = file != null && file.getContentDisposition() != null
                    ? headerFileName(file.getContentDisposition().getFileName()) : null;
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return attach(store, id, name, in, MAX_FILE_SIZE, DateTime.now());
            }
        });
    }

    @DELETE
    @Path("attachments/{fileId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response detach(@Context HttpHeaders httpHeaders, @PathParam("fileId") long fileId) {
        return WebEdit.run(httpHeaders, "delete the attachment " + fileId, store -> detach(store, fileId));
    }

    // ---------------------------------------------------------------- create, change, delete

    /**
     * {@code POST /{kind}/plans/{planId}/items} with the editable fields
     */
    static WebEdit.Reply create(WebEdit.Store store, Kind kind, long planId, String body, String language, DateTime now)
            throws Exception {
        if (kind == null) return WebEdit.Reply.notFound("Not found");
        JsonNode fields = WebEdit.body(body);
        /* the fields flat or as {"fields": {...}} like PUT */
        if (fields.size() == 1 && fields.has("fields")) {
            fields = fields.get("fields");
            if (!fields.isObject()) return WebEdit.Reply.badRequest("Invalid fields");
        }
        ResourceWebModules.Lookup lookup = store.lookup();
        JsonObject planObject = WebEdit.readable(store, planId);
        if (planObject == null || !kind.planClass.equals(planObject.getJevisClass())) return WebEdit.Reply.notFound("Plan not found");
        JsonObject directory = directory(kind, planObject, lookup);
        if (!canCreate(kind, planObject, directory, store.rights())) {
            return WebEdit.Reply.forbidden("Not allowed to create items in this plan");
        }
        Plan plan = plan(store, kind, planObject);
        LocalDate today = now.withZone(plan.zone).toLocalDate();
        ObjectNode tree = skeleton(kind, store, language, plan.zone, today);
        apply(kind, plan, tree.deepCopy(), tree, fields, today, true);
        checkRequirements(kind, tree, language, now);

        synchronized (CREATE_LOCK) {
            int number = nextNumber(store, kind, planObject);
            tree.put(kind.numberKey, number);
            String action = kind.action + "-create";
            JsonObject createdDirectory = null;
            JsonObject item = null;
            try {
                if (directory == null) {
                    createdDirectory = store.createObject(kind.directoryClass, kind.directoryClass, planObject.getId());
                    store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(createdDirectory.getId(), createdDirectory.getName(), action));
                    lookup.addReadable(createdDirectory, planObject.getId());
                    directory = createdDirectory;
                }
                item = store.createObject(String.valueOf(number), kind.itemClass, directory.getId());
                store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(item.getId(), item.getName(), action));
                lookup.addReadable(item, directory.getId());
                save(store, kind, plan, item, tree, now, action);
            } catch (Exception e) {
                rollback(store, item, action);
                rollback(store, createdDirectory, action);
                throw e;
            }
            return WebEdit.Reply.created(itemJson(store, kind, plan, item, now));
        }
    }

    /**
     * {@code PUT /{kind}/items/{id}} with {@code {version, fields}}: only the fields sent are changed
     */
    static WebEdit.Reply update(WebEdit.Store store, Kind kind, long id, String body, String language, DateTime now)
            throws Exception {
        if (kind == null) return WebEdit.Reply.notFound("Not found");
        JsonNode json = WebEdit.body(body);
        Item item = item(store, kind, id);
        if (item == null) return WebEdit.Reply.notFound("Item not found");
        if (!store.rights().canWrite(item.object)) return WebEdit.Reply.forbidden("Not allowed to change this item");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        JsonNode fields = json.get("fields");
        if (fields == null || !fields.isObject()) return WebEdit.Reply.badRequest("Missing fields");
        LocalDate today = now.withZone(item.plan.zone).toLocalDate();
        ObjectNode tree = item.tree.deepCopy();
        apply(kind, item.plan, item.tree, tree, fields, today, false);
        if (!WebEdit.sameVersion(version, item.data)) return WebEdit.Reply.conflict(itemJson(store, kind, item.plan, item.object, now));
        checkRequirements(kind, tree, language, now);
        save(store, kind, item.plan, item.object, tree, now, kind.action + "-update");
        return WebEdit.Reply.ok(itemJson(store, kind, item.plan, item.object, now));
    }

    /**
     * {@code DELETE /{kind}/items/{id}?version=…}: soft delete like the Control Center ({@code "Deleted": true})
     */
    static WebEdit.Reply delete(WebEdit.Store store, Kind kind, long id, String version, DateTime now) throws Exception {
        if (kind == null) return WebEdit.Reply.notFound("Not found");
        Item item = item(store, kind, id);
        if (item == null) return WebEdit.Reply.notFound("Item not found");
        if (!canDelete(store.rights(), item.object)) return WebEdit.Reply.forbidden("Not allowed to delete this item");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!WebEdit.sameVersion(version, item.data)) return WebEdit.Reply.conflict(itemJson(store, kind, item.plan, item.object, now));
        ObjectNode tree = item.tree.deepCopy();
        tree.put(KEY_DELETED, true);
        save(store, kind, item.plan, item.object, tree, now, kind.action + "-delete");
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- attachments

    /**
     * {@code POST /items/{id}/attachments}: a "File" child named like the file with the file as sample of "File"
     */
    static WebEdit.Reply attach(WebEdit.Store store, long itemId, String fileName, InputStream in, int max, DateTime now)
            throws Exception {
        JsonObject item = attachmentParent(store, itemId);
        if (item == null) return WebEdit.Reply.notFound("Item not found");
        if (!store.rights().canCreate(item, ResourceWebIso.CLASS_FILE)) {
            return WebEdit.Reply.forbidden("Not allowed to add attachments to this item");
        }
        if (in == null) return WebEdit.Reply.badRequest("Missing file");
        byte[] bytes = read(in, max);
        if (bytes == null) return new WebEdit.Reply(413, "The file is larger than " + (max / (1024 * 1024)) + " MB");
        String name = uploadName(fileName);
        JsonObject file = store.createObject(ResourceWebEdit.objectName(name), ResourceWebIso.CLASS_FILE, item.getId());
        store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(file.getId(), file.getName(), ACTION_ATTACHMENT_ADD));
        try {
            int count = store.writeFile(file.getId(), ResourceWebIso.ATTRIBUTE_FILE, now.withMillisOfSecond(0), name, bytes);
            store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(file.getId(), ResourceWebIso.ATTRIBUTE_FILE, count, ACTION_ATTACHMENT_ADD));
        } catch (Exception e) {
            rollback(store, file, ACTION_ATTACHMENT_ADD);
            throw e;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", file.getId());
        json.put("name", name);
        json.put("size", (long) bytes.length);
        json.put("canDelete", store.rights().canDelete(file));
        return WebEdit.Reply.created(json);
    }

    /**
     * {@code DELETE /attachments/{fileId}}: moves the "File" object of an item to the recycle bin
     */
    static WebEdit.Reply detach(WebEdit.Store store, long fileId) throws Exception {
        JsonObject file = WebEdit.readable(store, fileId);
        if (file == null || !fileClasses(store).contains(file.getJevisClass())) return WebEdit.Reply.notFound("Attachment not found");
        Long parent = store.lookup().parent(fileId);
        JsonObject item = parent != null ? WebEdit.readable(store, parent) : null;
        if (item == null || !ResourceWebIso.ITEM_CLASSES.contains(item.getJevisClass())) {
            return WebEdit.Reply.notFound("Attachment not found");
        }
        if (!store.rights().canDelete(file)) return WebEdit.Reply.forbidden("Not allowed to delete this attachment");
        store.recycle(file);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(file.getId(), file.getName(), ACTION_ATTACHMENT_DELETE));
        return WebEdit.Reply.noContent();
    }

    /**
     * @return the readable item outside the recycle bin whose JSON is not marked deleted, else null
     */
    static JsonObject attachmentParent(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        if (object == null) return null;
        for (Kind kind : Kind.values()) {
            if (kind.itemClass.equals(object.getJevisClass())) return item(store, kind, id) != null ? object : null;
        }
        return null;
    }

    /**
     * @return the content, null if it has more than {@code max} bytes
     */
    static byte[] read(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
            if (out.size() > max) return null;
        }
        return out.toByteArray();
    }

    /**
     * Browsers send the multipart file name as raw UTF-8 bytes, which the multipart parser reads as ISO-8859-1.
     * Re-decodes the name as UTF-8 when all characters are single bytes and they form valid UTF-8; otherwise the
     * name is returned unchanged.
     */
    static String headerFileName(String raw) {
        if (raw == null) return null;
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) > 0xFF) return raw;
        }
        byte[] bytes = raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        try {
            return java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return raw;
        }
    }

    /**
     * @return the file name without path, control characters and characters not allowed in file names, without
     * leading dots, at most {@link #MAX_FILE_NAME_LENGTH} characters (the extension is kept); {@code file} if
     * nothing is left
     */
    static String uploadName(String fileName) {
        String name = fileName != null ? fileName : "";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isISOControl(c) || Character.getType(c) == Character.FORMAT || "\"<>:*?|;".indexOf(c) >= 0) {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String safe = sb.toString().trim();
        while (safe.startsWith(".")) safe = safe.substring(1).trim();
        while (safe.contains("..")) safe = safe.replace("..", ".");
        if (safe.length() > MAX_FILE_NAME_LENGTH) {
            int dot = safe.lastIndexOf('.');
            String extension = dot > 0 && safe.length() - dot <= 16 ? safe.substring(dot) : "";
            safe = safe.substring(0, MAX_FILE_NAME_LENGTH - extension.length()).trim() + extension;
        }
        return safe.isEmpty() ? "file" : safe;
    }

    // ---------------------------------------------------------------- read answers

    /**
     * Adds what the user may do to a plan of a read answer ({@code GET /actionplans}, {@code /nonconformities},
     * {@code /legal}): {@code canCreate} per plan; {@code version}, {@code canWrite}, {@code canDelete} and
     * {@code canAttach} per item.
     *
     * @param latestData latest sample of "Data" of an item (null if none)
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> addEditInfo(Kind kind, Map<String, Object> planJson, JsonObject plan,
                                           ResourceWebModules.Lookup lookup, WebEdit.Rights rights,
                                           Function<Long, JsonSample> latestData) {
        planJson.put("canCreate", canCreate(kind, plan, directory(kind, plan, lookup), rights));
        Object items = planJson.get(kind.listKey);
        if (items instanceof List) {
            for (Map<String, Object> item : (List<Map<String, Object>>) items) {
                JsonObject object = lookup.readable((Long) item.get("id"));
                if (object != null) itemEditInfo(item, object, latestData.apply(object.getId()), rights);
            }
        }
        return planJson;
    }

    /**
     * Like the Control Center's plan ({@code ActionPlanData}), the statuses of an action plan always offer "done" and
     * "open": if none of the translations of {@code plugin.action.status.done} (or {@code .open}) is in the plan's
     * {@code statuses}, it is appended in the language.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> addDefaultStatuses(String language, Map<String, Object> planJson) {
        Object value = planJson.get("statuses");
        if (!(value instanceof List)) return planJson;
        List<String> statuses = (List<String>) value;
        for (String key : Arrays.asList(KEY_STATUS_DONE, KEY_STATUS_OPEN)) {
            List<String> translations = ResourceWebNotes.NoteTags.allTranslations(key);
            boolean present = false;
            for (String status : statuses) {
                for (String translation : translations) {
                    if (translation.equalsIgnoreCase(status.trim())) present = true;
                }
            }
            String text = ResourceWebNotes.NoteTags.translate(key, language);
            if (!present && text != null) statuses.add(text);
        }
        return planJson;
    }

    /**
     * @return the language for the default statuses: {@code lang}, else the first {@code Accept-Language}, else German
     * (the Control Center's default plan statuses)
     */
    static String statusLanguage(String lang, HttpHeaders headers) {
        boolean given = lang != null && !lang.trim().isEmpty();
        if (!given) {
            try {
                List<Locale> locales = headers != null ? headers.getAcceptableLanguages() : null;
                given = locales != null && !locales.isEmpty() && !"*".equals(locales.get(0).getLanguage());
            } catch (Exception e) {
                given = false;
            }
        }
        return given ? WebEdit.language(lang, headers) : "de";
    }

    static Map<String, Object> itemEditInfo(Map<String, Object> json, JsonObject object, JsonSample data, WebEdit.Rights rights) {
        json.put("version", WebEdit.version(data));
        json.put("canWrite", rights.canWrite(object));
        json.put("canDelete", canDelete(rights, object));
        json.put("canAttach", rights.canCreate(object, ResourceWebIso.CLASS_FILE));
        return json;
    }

    /**
     * Create right for the item class below the item directory of the plan, or below the plan if there is none
     * (the directory is created then)
     */
    static boolean canCreate(Kind kind, JsonObject plan, JsonObject directory, WebEdit.Rights rights) {
        return rights.canCreate(directory != null ? directory : plan, kind.itemClass);
    }

    /**
     * Soft delete writes a sample (write right) and is a delete (delete right)
     */
    static boolean canDelete(WebEdit.Rights rights, JsonObject item) {
        return rights.canWrite(item) && rights.canDelete(item);
    }

    // ---------------------------------------------------------------- plans and items

    /**
     * The settings of a plan used for checks, names and the answers.
     */
    static final class Plan {
        final JsonObject object;
        final Map<String, String> attributes = new HashMap<>();
        final DateTimeZone zone;

        Plan(JsonObject object, DateTimeZone zone) {
            this.object = object;
            this.zone = zone;
        }

        String attribute(String name) {
            return attributes.get(name);
        }

        List<String> list(String name) {
            return IsoJson.splitList(attribute(name));
        }
    }

    static Plan plan(WebEdit.Store store, Kind kind, JsonObject object) throws Exception {
        Plan plan = new Plan(object, store.workDay(object).zone);
        List<String> names;
        switch (kind) {
            case ACTIONS:
                names = Arrays.asList(ActionItem.PLAN_ATTRIBUTE_PREFIX, ActionItem.PLAN_ATTRIBUTE_STATUS,
                        ActionItem.PLAN_ATTRIBUTE_FIELDS, ActionItem.PLAN_ATTRIBUTE_MEDIUM, ActionItem.PLAN_ATTRIBUTE_SEU);
                break;
            case NONCONFORMITIES:
                names = Arrays.asList(NonconformityItem.PLAN_ATTRIBUTE_PREFIX, NonconformityItem.PLAN_ATTRIBUTE_MEDIUM,
                        NonconformityItem.PLAN_ATTRIBUTE_FIELDS, NonconformityItem.PLAN_ATTRIBUTE_SEU);
                break;
            default:
                names = Arrays.asList(Obligation.PLAN_ATTRIBUTE_CATEGORY, Obligation.PLAN_ATTRIBUTE_SCOPE);
        }
        for (String name : names) {
            JsonSample sample = store.latest(object.getId(), name);
            if (sample != null) plan.attributes.put(name, sample.getValue());
        }
        return plan;
    }

    /**
     * The item directory of the plan (the Control Center uses the first child of the directory class): the readable
     * one outside the recycle bin with the lowest id, null if there is none
     */
    static JsonObject directory(Kind kind, JsonObject plan, ResourceWebModules.Lookup lookup) {
        JsonObject directory = null;
        for (JsonObject child : lookup.readableChildren(plan.getId(), Collections.singleton(kind.directoryClass))) {
            if (!ResourceWebIso.isDeleted(child) && (directory == null || child.getId() < directory.getId())) directory = child;
        }
        return directory;
    }

    /**
     * An item with its plan and stored JSON.
     */
    static final class Item {
        final JsonObject object;
        final Plan plan;
        final JsonSample data;
        final ObjectNode tree;

        Item(JsonObject object, Plan plan, JsonSample data, ObjectNode tree) {
            this.object = object;
            this.plan = plan;
            this.data = data;
            this.tree = tree;
        }
    }

    /**
     * @return the readable item of the kind outside the recycle bin, below a readable plan, with valid JSON that is not
     * marked deleted; null otherwise
     */
    static Item item(WebEdit.Store store, Kind kind, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        if (object == null || !kind.itemClass.equals(object.getJevisClass())) return null;
        ResourceWebModules.Lookup lookup = store.lookup();
        Long planId = ResourceWebIso.planOf(object, lookup);
        JsonObject planObject = planId != null ? WebEdit.readable(store, planId) : null;
        if (planObject == null || !kind.planClass.equals(planObject.getJevisClass())) return null;
        JsonSample data = store.latest(id, IsoJson.ATTRIBUTE_DATA);
        ObjectNode tree = IsoJsonTree.parse(text(store.file(id, IsoJson.ATTRIBUTE_DATA, data)));
        if (tree == null || tree.path(KEY_DELETED).asBoolean(false)) return null;
        return new Item(object, plan(store, kind, planObject), data, tree);
    }

    /**
     * Highest number of all items of the plan + 1: counts items marked deleted and items the user cannot read (read
     * with the server's rights for this count only).
     */
    static int nextNumber(WebEdit.Store store, Kind kind, JsonObject plan) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        int max = 0;
        for (JsonObject object : lookup.allOfClass(kind.itemClass)) {
            if (!Long.valueOf(plan.getId()).equals(ResourceWebIso.planOf(object, lookup))) continue;
            max = Math.max(max, storedNumber(store, kind, object));
        }
        return max + 1;
    }

    private static int storedNumber(WebEdit.Store store, Kind kind, JsonObject object) {
        try {
            JsonSample data = store.latest(object.getId(), IsoJson.ATTRIBUTE_DATA);
            String json = text(store.file(object.getId(), IsoJson.ATTRIBUTE_DATA, data));
            if (json != null) {
                switch (kind) {
                    case ACTIONS:
                        ActionItem action = ActionItem.parse(json);
                        if (action != null) return action.getNo();
                        break;
                    case NONCONFORMITIES:
                        NonconformityItem nonconformity = NonconformityItem.parse(json);
                        if (nonconformity != null) return nonconformity.getNr();
                        break;
                    default:
                        Obligation obligation = Obligation.parse(json);
                        if (obligation != null) return obligation.getNr();
                }
            }
        } catch (Exception e) {
            /* no number: the object name (the Control Center names new items by their number) */
        }
        try {
            return Integer.parseInt(object.getName().trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String text(byte[] bytes) {
        return bytes != null ? new String(bytes, StandardCharsets.UTF_8) : null;
    }

    /**
     * The JSON of a new item as the Control Center's data classes write it (without the number)
     */
    static ObjectNode skeleton(Kind kind, WebEdit.Store store, String language, DateTimeZone zone, LocalDate today) {
        ObjectNode tree = IsoJsonTree.object();
        String created = IsoJsonTree.date(today, zone);
        switch (kind) {
            case ACTIONS:
                tree.put("From User", store.userName());
                tree.put("Description", "");
                tree.put("Note", "");
                tree.put("Create Date", created);
                tree.put("Attachment", "");
                tree.put("Title", "");
                String open = ResourceWebNotes.NoteTags.translate(KEY_STATUS_OPEN, language);
                tree.put("Status Tags", open != null ? open : "");
                for (String key : Arrays.asList("Field Tags", "Medium Tags", "Correction", "Follow Action",
                        "Alternative Measures", "Responsible", "Affected Process Note", "Energy Flow Note",
                        "Evaluation Note", "Distributor", "SEU Tags")) {
                    tree.put(key, "");
                }
                break;
            case NONCONFORMITIES:
                tree.put("Title", "");
                tree.put("Creator", store.userName());
                for (String key : Arrays.asList("Description", "Cause", "Immediate Measures", "Corrective Actions",
                        "Responsible Person")) {
                    tree.put(key, "");
                }
                tree.put("Create Date", created);
                tree.putNull("Planned Date");
                tree.putNull("Done Date");
                tree.put("Attachment", "");
                tree.put("Medium", "");
                tree.putArray("Field Tags");
                tree.put("Action", "");
                tree.put("SEU", "");
                break;
            default:
                tree.put("uuid", UUID.randomUUID().toString());
                tree.put("title", "");
                tree.put("Designation", "");
                tree.put("Description", "");
                tree.putNull("issue date");
                tree.putNull("current version date");
                tree.put("Relevance 50001", false);
                tree.putNull("Date of Examination");
                tree.put("importance for the company", "");
                tree.put("link", "");
                tree.put("category", "");
                tree.put("scope", "");
                tree.put("Attachment", "");
        }
        tree.put(KEY_DELETED, false);
        return tree;
    }

    // ---------------------------------------------------------------- fields

    /**
     * Sets the fields sent in the target tree.
     *
     * @param stored the stored JSON (old values outside the plan's lists are allowed if unchanged)
     * @param create a new item: the title is required even if it is not sent
     * @throws WebEdit.Stop {@code 400} {@code {messages}} for unknown or invalid fields, values outside the plan's
     *                      lists and an empty title
     */
    static void apply(Kind kind, Plan plan, ObjectNode stored, ObjectNode target, JsonNode fields, LocalDate today,
                      boolean create) throws WebEdit.Stop {
        Map<String, Type> types = FIELDS.get(kind);
        List<String> messages = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> field = it.next();
            try {
                applyField(kind, plan, stored, target, field.getKey(), types.get(field.getKey()), field.getValue());
            } catch (Invalid invalid) {
                messages.add(invalid.getMessage());
            }
        }
        String titleKey = kind == Kind.OBLIGATIONS ? "title" : "Title";
        if ((create || fields.has(titleKey)) && target.path(titleKey).asText("").trim().isEmpty()) {
            messages.add("Missing " + titleKey);
        }
        if (!messages.isEmpty()) throw invalid(messages);
        if (kind == Kind.ACTIONS && fields.has("Status Tags")) {
            String status = target.path("Status Tags").asText("").trim();
            String before = stored.path("Status Tags").asText("").trim();
            JsonNode done = target.get("Done Date");
            boolean doneEmpty = done == null || done.isNull() || done.asText("").trim().isEmpty();
            if (!status.equals(before) && isDone(status) && doneEmpty) {
                target.put("Done Date", IsoJsonTree.date(today, plan.zone));
            }
        }
    }

    private static void applyField(Kind kind, Plan plan, ObjectNode stored, ObjectNode target, String key, Type type,
                                   JsonNode value) throws Invalid {
        if (type == null) throw new Invalid("Unknown or read-only field: " + key);
        {
            switch (type) {
                case TEXT:
                    putText(target, key, text(value, key));
                    break;
                case DATE:
                    LocalDate date = date(value, key);
                    if (date != null) {
                        target.put(key, IsoJsonTree.date(date, plan.zone));
                    } else if (kind == Kind.ACTIONS) {
                        target.remove(key);
                    } else {
                        target.putNull(key);
                    }
                    break;
                case BOOLEAN:
                    if (value != null && !value.isNull() && !value.isBoolean()) throw new Invalid("Invalid " + key);
                    target.put(key, value != null && value.asBoolean(false));
                    break;
                case STATUS:
                    List<String> statuses = plan.list(ActionItem.PLAN_ATTRIBUTE_STATUS);
                    statuses.addAll(ResourceWebNotes.NoteTags.allTranslations(KEY_STATUS_DONE));
                    target.put(key, oneOf(text(value, key), statuses, stored, key));
                    break;
                case ACTION_MEDIUM:
                    List<String> ids = new ArrayList<>();
                    for (Medium medium : Medium.parseList(plan.attribute(ActionItem.PLAN_ATTRIBUTE_MEDIUM))) ids.add(medium.getId());
                    target.put(key, oneOf(text(value, key), ids, stored, key));
                    break;
                case NC_MEDIUM:
                    target.put(key, oneOf(text(value, key), plan.list(NonconformityItem.PLAN_ATTRIBUTE_MEDIUM), stored, key));
                    break;
                case SEU:
                    target.put(key, oneOf(text(value, key), plan.list(NonconformityItem.PLAN_ATTRIBUTE_SEU), stored, key));
                    break;
                case CATEGORY:
                    target.put(key, oneOf(text(value, key), plan.list(Obligation.PLAN_ATTRIBUTE_CATEGORY), stored, key));
                    break;
                case SCOPE:
                    target.put(key, oneOf(text(value, key), plan.list(Obligation.PLAN_ATTRIBUTE_SCOPE), stored, key));
                    break;
                case FIELDS_JOINED:
                    target.put(key, String.join(";", allOf(value, plan.list(ActionItem.PLAN_ATTRIBUTE_FIELDS),
                            IsoJson.splitList(stored.path(key).asText("")), key)));
                    break;
                case SEUS_JOINED:
                    target.put(key, String.join(";", allOf(value, plan.list(ActionItem.PLAN_ATTRIBUTE_SEU),
                            IsoJson.splitList(stored.path(key).asText("")), key)));
                    break;
                case FIELDS_ARRAY:
                    List<String> storedFields = new ArrayList<>();
                    NonconformityItem storedItem = NonconformityItem.parse(stored.toString());
                    if (storedItem != null) storedFields.addAll(storedItem.getFields());
                    ArrayNode array = target.putArray(key);
                    for (String part : allOf(value, plan.list(NonconformityItem.PLAN_ATTRIBUTE_FIELDS), storedFields, key)) {
                        array.add(part);
                    }
                    break;
                case CHECK_LIST:
                    checkList(kind, target, key, value);
                    break;
                default:
                    throw new Invalid("Unknown field: " + key);
            }
        }
    }

    /**
     * A validation message of a field
     */
    static final class Invalid extends Exception {
        Invalid(String message) {
            super(message);
        }
    }

    /**
     * @return {@code 400} with {@code {"messages": [...]}}
     */
    static WebEdit.Stop invalid(List<String> messages) {
        return new WebEdit.Stop(new WebEdit.Reply(400, Collections.singletonMap("messages", messages)));
    }

    /**
     * @return whether the status is the Control Center's "done" status in any language
     */
    static boolean isDone(String status) {
        for (String done : ResourceWebNotes.NoteTags.allTranslations(KEY_STATUS_DONE)) {
            if (done.equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }

    /**
     * Writes a text to its key, or to the stored legacy spelling if only that exists
     */
    private static void putText(ObjectNode target, String key, String text) {
        String legacy = LEGACY_KEYS.get(key);
        if (legacy != null && !target.has(key) && target.has(legacy)) {
            target.put(legacy, text);
        } else {
            target.put(key, text);
        }
    }

    /**
     * @return the text ("" for null)
     * @throws Invalid if not a text or too long
     */
    private static String text(JsonNode value, String key) throws Invalid {
        if (value == null || value.isNull()) return "";
        if (!value.isTextual()) throw new Invalid("Invalid " + key);
        String text = value.asText();
        if (text.length() > MAX_TEXT_LENGTH) throw new Invalid(key + " longer than " + MAX_TEXT_LENGTH + " characters");
        return text;
    }

    /**
     * @param value {@code yyyy-MM-dd}; null (or empty) for no date
     */
    static LocalDate date(JsonNode value, String key) throws Invalid {
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new Invalid("Invalid " + key);
        String text = value.asText().trim();
        if (text.isEmpty()) return null;
        if (!DATE_ONLY.matcher(text).matches()) throw new Invalid("Invalid " + key);
        try {
            return LocalDate.parse(text);
        } catch (IllegalArgumentException e) {
            throw new Invalid("Invalid " + key);
        }
    }

    /**
     * @return the value if it is empty, in the list or the stored value
     * @throws Invalid otherwise
     */
    private static String oneOf(String value, List<String> allowed, ObjectNode stored, String key) throws Invalid {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || allowed.contains(trimmed) || trimmed.equals(stored.path(key).asText("").trim())) return trimmed;
        throw new Invalid("Invalid " + key + ": " + trimmed);
    }

    /**
     * @return the texts of the list (empty parts and repeats left out), each in the allowed or stored values
     * @throws Invalid if not a list of texts or a value is not allowed
     */
    private static List<String> allOf(JsonNode value, List<String> allowed, List<String> stored, String key)
            throws Invalid {
        List<String> result = new ArrayList<>();
        if (value == null || value.isNull()) return result;
        if (!value.isArray()) throw new Invalid("Invalid " + key);
        for (JsonNode part : value) {
            if (!part.isTextual()) throw new Invalid("Invalid " + key);
            String text = part.asText().trim();
            if (text.isEmpty() || result.contains(text)) continue;
            if (text.contains(";") || !(allowed.contains(text) || stored.contains(text))) {
                throw new Invalid("Invalid " + key + ": " + text);
            }
            result.add(text);
        }
        return result;
    }

    /**
     * Merges the sent booleans into the stored check list (other stored keys, e.g. "New EnPI", stay; "New EnPI" is
     * not editable on the web)
     */
    private static void checkList(Kind kind, ObjectNode target, String key, JsonNode value) throws Invalid {
        if (value == null || !value.isObject()) throw new Invalid("Invalid " + key);
        JsonNode current = target.get(key);
        ObjectNode checks = current != null && current.isObject() ? (ObjectNode) current : target.putObject(key);
        List<String> booleans = kind == Kind.ACTIONS ? ACTION_CHECKS : NONCONFORMITY_CHECKS;
        Iterator<Map.Entry<String, JsonNode>> it = value.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> check = it.next();
            if (booleans.contains(check.getKey())) {
                if (!check.getValue().isBoolean()) throw new Invalid("Invalid " + key + ": " + check.getKey());
                checks.put(check.getKey(), check.getValue().asBoolean());
            } else {
                throw new Invalid("Unknown " + key + " entry: " + check.getKey());
            }
        }
    }

    /**
     * The requirement check of the Control Center ({@code NonconformityData.checkForRequirements}) for
     * nonconformities
     *
     * @throws WebEdit.Stop {@code 400} {@code {messages}} if violated
     */
    static void checkRequirements(Kind kind, ObjectNode tree, String language, DateTime now) throws Exception {
        if (kind != Kind.NONCONFORMITIES) return;
        NonconformityItem item = NonconformityItem.parse(IsoJsonTree.write(tree));
        if (item == null) return;
        List<String> messages = new ArrayList<>();
        for (String warning : item.warnings(now)) {
            String key;
            switch (warning) {
                case NonconformityItem.WARNING_IMMEDIATE_MEASURES_MISSING:
                    key = "plugin.nonconformities.error.immediatemeasures";
                    break;
                case NonconformityItem.WARNING_DONE_WITHOUT_ACTION:
                    key = "plugin.nonconformities.error.donedate";
                    break;
                default:
                    key = "plugin.nonconformities.error.donedateafter";
            }
            String message = ResourceWebNotes.NoteTags.translate(key, language);
            messages.add(message != null ? message : warning);
        }
        if (!messages.isEmpty()) throw invalid(messages);
    }

    // ---------------------------------------------------------------- writing

    /**
     * Like the {@code commit} of the Control Center's data classes: the JSON as new file sample of "Data"
     * ({@code DataModel_v2_<yyyyMMddHHmm>.json}), then the object renamed if the name changed.
     */
    static void save(WebEdit.Store store, Kind kind, Plan plan, JsonObject item, ObjectNode tree, DateTime now,
                     String action) throws Exception {
        DateTime time = saveTime(store, item.getId(), now);
        String fileName = "DataModel_v2_" + time.toString("yyyyMMddHHmm") + ".json";
        int count = store.writeFile(item.getId(), IsoJson.ATTRIBUTE_DATA, time, fileName,
                IsoJsonTree.write(tree).getBytes(StandardCharsets.UTF_8));
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(item.getId(), IsoJson.ATTRIBUTE_DATA, count, action));
        String name = objectName(kind, plan, tree);
        if (!name.equals(item.getName())) {
            store.rename(item, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(item.getId(), name, action));
            item.setName(name);
        }
    }

    /**
     * @return the time of the next sample of "Data": now to the second, after the latest sample (waits for the next
     * second if there already is a sample in this second; the stored file name has only seconds)
     */
    static DateTime saveTime(WebEdit.Store store, long id, DateTime now) throws Exception {
        DateTime time = now.withMillisOfSecond(0);
        JsonSample latest = store.latest(id, IsoJson.ATTRIBUTE_DATA);
        Long last = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
        if (last != null && last >= time.getMillis()) {
            time = new DateTime(last, now.getZone()).withMillisOfSecond(0).plusSeconds(1);
        }
        if (time.isAfter(now)) store.waitUntil(time);
        return time;
    }

    /**
     * The object name the Control Center gives: actions {@code "<prefix> %03d - <title cut at 50>"}
     * ({@code ActionData.commit}), nonconformities {@code <prefix><nr>}, obligations {@code <nr>}
     */
    static String objectName(Kind kind, Plan plan, ObjectNode tree) {
        switch (kind) {
            case ACTIONS:
                int no = tree.has("No") ? tree.path("No").asInt(0) : tree.path("Nr").asInt(0);
                String prefix = plan.attribute(ActionItem.PLAN_ATTRIBUTE_PREFIX);
                String title = tree.path("Title").asText("");
                String shortTitle = title.length() < 50 ? title : title.substring(0, 50) + "..";
                return String.format(Locale.ROOT, "%s %03d", prefix != null ? prefix : "", no) + " - " + shortTitle;
            case NONCONFORMITIES:
                String ncPrefix = plan.attribute(NonconformityItem.PLAN_ATTRIBUTE_PREFIX);
                return (ncPrefix != null ? ncPrefix : "") + tree.path("Nr").asInt(0);
            default:
                return String.valueOf(tree.path("Nr").asInt(0));
        }
    }

    private static void rollback(WebEdit.Store store, JsonObject object, String action) {
        if (object == null) return;
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), action + "-rollback"));
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(ResourceWebEditIso.class)
                    .error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    // ---------------------------------------------------------------- answers

    /**
     * @return the item in the format of the read answer (list entry with edit info)
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> itemJson(WebEdit.Store store, Kind kind, Plan plan, JsonObject object, DateTime now)
            throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        JsonSample data = store.latest(object.getId(), IsoJson.ATTRIBUTE_DATA);
        String json = text(store.file(object.getId(), IsoJson.ATTRIBUTE_DATA, data));
        List<JsonObject> items = Collections.singletonList(object);
        Function<Long, String> dataOf = id -> id == object.getId() ? json : null;
        Set<String> fileClasses = fileClasses(store);
        java.util.function.ToIntFunction<Long> attachments = id -> ResourceWebIso.attachmentsOf(id, lookup, fileClasses).size();
        DateTimeZone zone = DateTimeZone.getDefault();
        DateTime serverNow = now.withZone(zone);
        Map<String, Object> planJson;
        switch (kind) {
            case ACTIONS:
                planJson = ResourceWebIso.actionPlanJson(plan.object, plan::attribute, items, dataOf,
                        id -> unit(store, id), attachments, lookup, serverNow.toLocalDate(), zone);
                break;
            case NONCONFORMITIES:
                planJson = ResourceWebIso.nonconformityPlanJson(plan.object, plan::attribute, items, dataOf, attachments,
                        lookup, serverNow);
                break;
            default:
                planJson = ResourceWebIso.legalPlanJson(plan.object, plan::attribute, items, dataOf, attachments, lookup);
        }
        List<Map<String, Object>> list = (List<Map<String, Object>>) planJson.get(kind.listKey);
        Map<String, Object> item = list != null && !list.isEmpty() ? list.get(0) : new LinkedHashMap<>();
        if (list == null || list.isEmpty()) item.put("id", object.getId());
        return itemEditInfo(item, object, data, store.rights());
    }

    /**
     * Unit of a data object like the read answer: attribute "Unit", otherwise the display unit of "Value"; null if
     * not readable
     */
    private static String unit(WebEdit.Store store, long id) {
        try {
            if (store.lookup().readable(id) == null) return null;
            JsonSample unit = store.latest(id, "Unit");
            if (unit != null && unit.getValue() != null && !unit.getValue().trim().isEmpty()) return unit.getValue().trim();
            return store.unit(id, "Value");
        } catch (Exception e) {
            return null;
        }
    }

    static Set<String> fileClasses(WebEdit.Store store) {
        return store.classes().withHeirs(ResourceWebIso.CLASS_FILE);
    }
}
