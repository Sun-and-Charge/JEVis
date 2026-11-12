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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisObject;
import org.jevis.commons.chart.RowDefinition;
import org.jevis.commons.dashboard.*;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Editing of dashboards for the web app (stage E4, see docs/WEB-API.md): the source of a dashboard (its JSON as the
 * Control Center stores it), preview, create, change, copy and delete, directories, background and image uploads and
 * the values of the input widgets.
 * <p>
 * A dashboard is stored like the Control Center's {@code ConfigManager.saveDashboard}: a new file sample of "Data
 * Model File" with the checked source ({@link DashboardSource}). Rights are checked with the rights of the logged-in
 * user like the Control Center's {@code SaveUnderDialog} and toolbar. The view endpoints (W3) are in
 * {@link ResourceWebDashboard}.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditDashboard {

    static final String CLASS_DASHBOARD = DashboardReader.DASHBOARD_CLASS;
    static final String CLASS_DIRECTORY = "Dashboard Directory";
    static final String ATTRIBUTE_FILE = DashboardReader.DATA_MODEL_FILE_ATTRIBUTE;
    static final String ATTRIBUTE_BACKGROUND = DashboardReader.BACKGROUND_ATTRIBUTE;
    static final String ATTRIBUTE_VALUE = ResourceWebEditAnalysis.ATTRIBUTE_VALUE;
    static final String VERSION_NONE = ResourceWebEditAnalysis.VERSION_NONE;
    static final int MAX_IMAGE_SIZE = 10 * 1024 * 1024;
    static final String ACTION_CREATE = "dashboard-create";
    static final String ACTION_SAVE = "dashboard-save";
    static final String ACTION_COPY = "dashboard-copy";
    static final String ACTION_DELETE = "dashboard-delete";
    static final String ACTION_BACKGROUND = "dashboard-background";
    static final String ACTION_IMAGE = "dashboard-image";
    static final String ACTION_VALUE = "dashboard-value";
    /**
     * {@code IncrementPojo}: increment of a PlusMinus widget without setting
     */
    static final double DEFAULT_INCREMENT = 0.1d;
    /**
     * The PlusMinus tile (TilesFX {@code PlusMinusTileSkin}) keeps its value within the tile's default range and its
     * increment within 0–10
     */
    static final double PLUS_MINUS_MIN = 0d;
    static final double PLUS_MINUS_MAX = 100d;
    static final double PLUS_MINUS_MAX_INCREMENT = 10d;
    /**
     * One create at a time, so two dashboards do not get the same name
     */
    private static final Object CREATE_LOCK = new Object();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger(ResourceWebEditDashboard.class);

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("dashboards/directories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response directories(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "read the dashboard directories", store -> directories(store, language));
    }

    @GET
    @Path("dashboards/{id}/source")
    @Produces(MediaType.APPLICATION_JSON)
    public Response source(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        return WebEdit.runSql(httpHeaders, "read the source of dashboard " + id,
                (sql, store) -> source(store, sqlEngine(sql), id));
    }

    @POST
    @Path("dashboards/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response preview(@Context HttpHeaders httpHeaders, String body) {
        return WebEdit.runSql(httpHeaders, "preview a dashboard", (sql, store) -> preview(store, sqlEngine(sql), body));
    }

    @POST
    @Path("dashboards")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang, String body) {
        return WebEdit.runSql(httpHeaders, "create a dashboard",
                (sql, store) -> create(store, sqlEngine(sql), body, DateTime.now()));
    }

    @PUT
    @Path("dashboards/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        return WebEdit.runSql(httpHeaders, "change dashboard " + id,
                (sql, store) -> update(store, sqlEngine(sql), id, body, DateTime.now()));
    }

    @POST
    @Path("dashboards/{id}/copy")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response copy(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                         String body) {
        return WebEdit.runSql(httpHeaders, "copy dashboard " + id,
                (sql, store) -> copy(store, sqlEngine(sql), id, body, DateTime.now()));
    }

    @DELETE
    @Path("dashboards/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version) {
        return WebEdit.runSql(httpHeaders, "delete dashboard " + id,
                (sql, store) -> delete(store, sqlEngine(sql), id, version));
    }

    @PUT
    @Path("dashboards/{id}/background")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response background(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                               @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "set the background of dashboard " + id, store -> {
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return background(store, id, fileName(file), in, MAX_IMAGE_SIZE, DateTime.now());
            }
        });
    }

    @POST
    @Path("dashboards/{id}/images")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response image(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                          @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "add an image to dashboard " + id, store -> {
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return image(store, id, fileName(file), in, MAX_IMAGE_SIZE, DateTime.now());
            }
        });
    }

    @POST
    @Path("dashboards/{id}/widgets/{uuid}/value")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response value(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @PathParam("uuid") int uuid,
                          @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.runSql(httpHeaders, "enter a value in dashboard " + id,
                (sql, store) -> value(store, sqlEngine(sql), id, uuid, body, language, DateTime.now()));
    }

    private static String fileName(FormDataBodyPart file) {
        return file != null && file.getContentDisposition() != null
                ? ResourceWebEditIso.headerFileName(file.getContentDisposition().getFileName()) : null;
    }

    // ---------------------------------------------------------------- access beyond the store

    /**
     * What the dashboard endpoints need from the JEVis API of the request (production: the read-only
     * {@link LocalDataSource} with the rights of the user).
     */
    interface Engine {
        /**
         * @return the dashboard of the source as W3 reads it ({@link DashboardReader#readDashboardFile}, rows and links
         * resolved)
         */
        DashboardDefinition read(JsonNode source);

        /**
         * {@link DashboardReader#convertLegacyDataHandler}
         */
        ObjectNode convertLegacy(JsonNode handler);

        /**
         * @return the time frames of the time frame bar ({@link DashboardTimeFrames#options()})
         */
        List<DashboardTimeFrames.Option> timeFrames();

        /**
         * {@link DashboardTimeFrames#isKnown}
         */
        boolean knownTimeFrame(String id);

        /**
         * @return the calculation whose output is the row, -1 if none
         */
        long calculationId(long rowId);

        /**
         * @param contextId the dashboard or directory whose site defines the working day, may be null
         */
        DashboardResult evaluate(DashboardDefinition definition, Long contextId, DashboardRequest request);
    }

    static Engine sqlEngine(SQLDataSource sql) {
        return new Engine() {
            private LocalDataSource local;
            private DashboardTimeFrames timeFrames;
            private ResourceWebEditAnalysis.Engine analysis;

            private LocalDataSource local() {
                if (local == null) local = new LocalDataSource(sql);
                return local;
            }

            private DashboardTimeFrames timeFrameList() {
                if (timeFrames == null) timeFrames = new DashboardTimeFrames(local(), DateTimeZone.getDefault(), null, null);
                return timeFrames;
            }

            @Override
            public DashboardDefinition read(JsonNode source) {
                DashboardReader reader = new DashboardReader();
                DashboardDefinition definition = reader.readDashboardFile(source, local());
                reader.resolve(local(), definition);
                return definition;
            }

            @Override
            public ObjectNode convertLegacy(JsonNode handler) {
                return new DashboardReader().convertLegacyDataHandler(handler, local());
            }

            @Override
            public List<DashboardTimeFrames.Option> timeFrames() {
                return timeFrameList().options();
            }

            @Override
            public boolean knownTimeFrame(String id) {
                return timeFrameList().isKnown(id);
            }

            @Override
            public long calculationId(long rowId) {
                if (analysis == null) analysis = ResourceWebEditAnalysis.sqlEngine(sql);
                return analysis.calculationId(rowId);
            }

            @Override
            public DashboardResult evaluate(DashboardDefinition definition, Long contextId, DashboardRequest request) {
                JEVisObject context = contextId != null ? local().getObject(contextId) : null;
                return new DashboardEvaluator(local()).evaluate(context, definition, request);
            }
        };
    }

    // ---------------------------------------------------------------- reading

    /**
     * @return the readable dashboard outside the recycle bin, else null
     */
    static JsonObject dashboardObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && CLASS_DASHBOARD.equals(object.getJevisClass()) ? object : null;
    }

    /**
     * The stored state of a dashboard: the latest file sample (null if never saved) and its normalised source
     */
    static final class Stored {
        final JsonObject object;
        final JsonSample version;
        final ObjectNode source;

        Stored(JsonObject object, JsonSample version, ObjectNode source) {
            this.object = object;
            this.version = version;
            this.source = source;
        }
    }

    static Stored stored(WebEdit.Store store, Engine engine, JsonObject object) throws Exception {
        JsonSample latest = store.latest(object.getId(), ATTRIBUTE_FILE);
        ObjectNode source = latest != null ? parse(store.file(object.getId(), ATTRIBUTE_FILE, latest)) : null;
        if (source == null) source = DashboardSource.empty();
        return new Stored(object, latest, DashboardSource.normalize(source, engine));
    }

    private static ObjectNode parse(byte[] bytes) {
        if (bytes == null) return null;
        try {
            JsonNode node = MAPPER.readTree(new String(bytes, StandardCharsets.UTF_8));
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (IOException e) {
            logger.warn("Could not read a dashboard file", e);
            return null;
        }
    }

    static String version(JsonSample sample) {
        return ResourceWebEditAnalysis.version(sample);
    }

    /**
     * {@code GET /web/dashboards/{id}}: the W3 configuration with what the editor needs
     */
    static WebEdit.Reply config(WebEdit.Store store, Engine engine, long id) throws Exception {
        JsonObject object = dashboardObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Dashboard not found");
        Stored stored = stored(store, engine, object);
        return WebEdit.Reply.ok(answer(store, engine, object, stored.source, stored.version));
    }

    /**
     * @return the GET answer for the source of the dashboard: W3 configuration, version, rights and directory
     */
    static Map<String, Object> answer(WebEdit.Store store, Engine engine, JsonObject object, ObjectNode source,
                                      JsonSample version) throws Exception {
        DashboardDefinition definition = definition(store, engine, source, object.getId());
        Map<String, Object> json = ResourceWebDashboard.configJson(object.getId(), object.getName(),
                ResourceWebEditAnalysis.names(object), definition, engine.timeFrames());
        json.put("version", version(version));
        WebEdit.Rights rights = store.rights();
        json.put("canWrite", rights.canWrite(object));
        json.put("canDelete", rights.canDelete(object));
        Long parent = store.lookup().parent(object.getId());
        JsonObject directory = parent != null ? WebEdit.readable(store, parent) : null;
        json.put("directoryId", directory != null && CLASS_DIRECTORY.equals(directory.getJevisClass()) ? directory.getId() : null);
        addCanEnter(store, json, definition);
        return json;
    }

    /**
     * @param id the dashboard (background image), null for a new one
     */
    private static DashboardDefinition definition(WebEdit.Store store, Engine engine, ObjectNode source, Long id) throws Exception {
        DashboardDefinition definition = engine.read(source);
        definition.setId(id);
        definition.setBackgroundImage(id != null && store.latest(id, ATTRIBUTE_BACKGROUND) != null);
        return definition;
    }

    /**
     * Adds {@code canEnter} to each widget: whether the user may use the input widget
     */
    @SuppressWarnings("unchecked")
    private static void addCanEnter(WebEdit.Store store, Map<String, Object> json, DashboardDefinition definition) throws Exception {
        List<Map<String, Object>> widgets = (List<Map<String, Object>>) json.get("widgets");
        for (int i = 0; widgets != null && i < widgets.size() && i < definition.getWidgets().size(); i++) {
            widgets.get(i).put("canEnter", canEnter(store, definition.getWidgets().get(i)));
        }
    }

    /**
     * @return whether the user may use the input widget: Value Editor like the manual values of E1 (raw row with
     * write right), the other input widgets with write right on the object of the row
     */
    static boolean canEnter(WebEdit.Store store, WidgetDefinition widget) throws Exception {
        if (!WidgetDefinition.isInputType(widget.getType()) || widget.getRows().isEmpty()) return false;
        JsonObject row = WebEdit.readable(store, widget.getRows().get(0).getObjectId());
        if (row == null) return false;
        if (WidgetDefinition.TYPE_VALUE_EDITOR.equals(widget.getType())) return WebEdit.canEnter(row, store.rights());
        return store.rights().canWrite(row) && store.classes().type(row.getJevisClass(), ATTRIBUTE_VALUE) != null;
    }

    /**
     * {@code GET /web/dashboards/{id}/source}
     */
    static WebEdit.Reply source(WebEdit.Store store, Engine engine, long id) throws Exception {
        JsonObject object = dashboardObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Dashboard not found");
        Stored stored = stored(store, engine, object);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("version", version(stored.version));
        json.put("source", stored.source);
        return WebEdit.Reply.ok(json);
    }

    // ---------------------------------------------------------------- writing

    /**
     * {@code POST /web/dashboards} with {@code {directoryId, name, source?}}
     */
    static WebEdit.Reply create(WebEdit.Store store, Engine engine, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        Long directoryId = longValue(json, "directoryId");
        if (directoryId == null) return WebEdit.Reply.badRequest("Missing directoryId");
        JsonObject directory = directory(store, directoryId);
        if (directory == null) return WebEdit.Reply.notFound("Directory not found");
        if (!store.rights().canCreate(directory, CLASS_DASHBOARD)) {
            return WebEdit.Reply.forbidden("Not allowed to create dashboards in this directory");
        }
        List<String> messages = new ArrayList<>();
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        JsonNode sent = json.get("source");
        ObjectNode source = sent == null || sent.isNull() ? DashboardSource.empty()
                : new DashboardSource(store, engine, null, messages).check(sent);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        return createDashboard(store, engine, directory, name, source, null, now, ACTION_CREATE);
    }

    /**
     * {@code POST /web/dashboards/{id}/copy} with {@code {directoryId, name, source?}} ("Speichern unter")
     */
    static WebEdit.Reply copy(WebEdit.Store store, Engine engine, long id, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject original = dashboardObject(store, id);
        if (original == null) return WebEdit.Reply.notFound("Dashboard not found");
        Long directoryId = longValue(json, "directoryId");
        if (directoryId == null) return WebEdit.Reply.badRequest("Missing directoryId");
        JsonObject directory = directory(store, directoryId);
        if (directory == null) return WebEdit.Reply.notFound("Directory not found");
        if (!store.rights().canCreate(directory, CLASS_DASHBOARD)) {
            return WebEdit.Reply.forbidden("Not allowed to create dashboards in this directory");
        }
        List<String> messages = new ArrayList<>();
        String name = ResourceWebEditAnalysis.name(json, messages, true);
        Stored stored = stored(store, engine, original);
        JsonNode sent = json.get("source");
        ObjectNode source = sent == null || sent.isNull() ? stored.source.deepCopy()
                : new DashboardSource(store, engine, stored.source, messages).check(sent);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        return createDashboard(store, engine, directory, name, source, original, now, ACTION_COPY);
    }

    /**
     * @param backgroundOf the dashboard whose latest background is copied (as {@code ConfigManager.openSaveUnder}),
     *                     null for none
     */
    private static WebEdit.Reply createDashboard(WebEdit.Store store, Engine engine, JsonObject directory, String name,
                                                 ObjectNode source, JsonObject backgroundOf, DateTime now, String action)
            throws Exception {
        synchronized (CREATE_LOCK) {
            JsonObject existing = ResourceWebEditAnalysis.nameTaken(store, directory.getId(), name, null);
            if (existing != null) return nameTakenReply(existing);
            JsonObject dashboard = store.createObject(name, CLASS_DASHBOARD, directory.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(dashboard.getId(), dashboard.getName(), action));
            store.lookup().addReadable(dashboard, directory.getId());
            try {
                if (backgroundOf != null) copyBackground(store, backgroundOf, dashboard, now, action);
                save(store, dashboard, source, now, action);
            } catch (Exception e) {
                rollback(store, dashboard, action);
                throw e;
            }
            return WebEdit.Reply.created(answer(store, engine, dashboard, source, store.latest(dashboard.getId(), ATTRIBUTE_FILE)));
        }
    }

    private static void copyBackground(WebEdit.Store store, JsonObject from, JsonObject to, DateTime now, String action)
            throws Exception {
        JsonSample latest = store.latest(from.getId(), ATTRIBUTE_BACKGROUND);
        byte[] bytes = latest != null ? store.file(from.getId(), ATTRIBUTE_BACKGROUND, latest) : null;
        if (bytes == null) return;
        String fileName = ResourceWebEditIso.uploadName(latest.getValue());
        int count = store.writeFile(to.getId(), ATTRIBUTE_BACKGROUND, now.withMillisOfSecond(0), fileName, bytes);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(to.getId(), ATTRIBUTE_BACKGROUND, count, action));
    }

    /**
     * {@code PUT /web/dashboards/{id}} with {@code {version, source?, name?}}
     */
    static WebEdit.Reply update(WebEdit.Store store, Engine engine, long id, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = dashboardObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Dashboard not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this dashboard");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        List<String> messages = new ArrayList<>();
        String name = json.has("name") && !json.get("name").isNull() ? ResourceWebEditAnalysis.name(json, messages, true) : null;
        Stored stored = stored(store, engine, object);
        JsonNode sent = json.get("source");
        ObjectNode source = null;
        if (sent != null && !sent.isNull()) {
            source = new DashboardSource(store, engine, stored.source, messages).check(sent);
        } else if (name == null && messages.isEmpty()) {
            messages.add("Missing source");
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!ResourceWebEditAnalysis.sameVersion(version, stored.version)) {
            return WebEdit.Reply.conflict(answer(store, engine, object, stored.source, stored.version));
        }
        if (name != null && !name.equals(object.getName())) {
            Long parent = store.lookup().parent(id);
            JsonObject existing = parent != null ? ResourceWebEditAnalysis.nameTaken(store, parent, name, id) : null;
            if (existing != null) return nameTakenReply(existing);
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        if (source == null) return WebEdit.Reply.ok(answer(store, engine, object, stored.source, stored.version));
        save(store, object, source, now, ACTION_SAVE);
        return WebEdit.Reply.ok(answer(store, engine, object, source, store.latest(id, ATTRIBUTE_FILE)));
    }

    /**
     * {@code DELETE /web/dashboards/{id}?version=…}: moves the dashboard to the recycle bin
     */
    static WebEdit.Reply delete(WebEdit.Store store, Engine engine, long id, String version) throws Exception {
        JsonObject object = dashboardObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Dashboard not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this dashboard");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        JsonSample current = store.latest(id, ATTRIBUTE_FILE);
        if (!ResourceWebEditAnalysis.sameVersion(version, current)) {
            Stored stored = stored(store, engine, object);
            return WebEdit.Reply.conflict(answer(store, engine, object, stored.source, stored.version));
        }
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }

    /**
     * Writes the source as new file sample of "Data Model File" ({@code ConfigManager.saveDashboard}).
     */
    static void save(WebEdit.Store store, JsonObject dashboard, ObjectNode source, DateTime now, String action)
            throws Exception {
        DateTime time = ResourceWebEditAnalysis.saveTime(store, dashboard.getId(), ATTRIBUTE_FILE, now);
        byte[] json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(source).getBytes(StandardCharsets.UTF_8);
        int count = store.writeFile(dashboard.getId(), ATTRIBUTE_FILE, time, fileName(dashboard.getName(), time), json);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(dashboard.getId(), ATTRIBUTE_FILE, count, action));
    }

    /**
     * @return the file name of {@code ConfigManager.saveDashboard}: the name with only A–Z, a–z, 0–9, then
     * {@code _yyyyMMddHHmm.json}
     */
    static String fileName(String name, DateTime time) {
        return (name != null ? name : "").replaceAll("[^A-Za-z0-9]", "") + "_" + time.toString("yyyyMMddHHmm") + ".json";
    }

    private static void rollback(WebEdit.Store store, JsonObject object, String action) {
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), action + "-rollback"));
        } catch (Exception e) {
            logger.error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    /**
     * @return the readable "Dashboard Directory" outside the recycle bin, else null
     */
    static JsonObject directory(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && CLASS_DIRECTORY.equals(object.getJevisClass()) ? object : null;
    }

    private static WebEdit.Reply nameTakenReply(JsonObject existing) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("nameTaken", true);
        json.put("id", existing.getId());
        return WebEdit.Reply.conflict(json);
    }

    private static Long longValue(JsonNode json, String field) throws WebEdit.Stop {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.canConvertToLong() || !node.isIntegralNumber()) throw WebEdit.Stop.badRequest("Invalid " + field);
        return node.asLong();
    }

    // ---------------------------------------------------------------- helpers of the editor

    /**
     * {@code GET /web/dashboards/directories}
     */
    static WebEdit.Reply directories(WebEdit.Store store, String language) throws Exception {
        return ResourceWebEditAnalysis.directories(store, language, CLASS_DIRECTORY, CLASS_DASHBOARD);
    }

    /**
     * {@code POST /web/dashboards/preview} with {@code {source, id?, directoryId?, …W3 data request…}}
     */
    static WebEdit.Reply preview(WebEdit.Store store, Engine engine, String body) throws Exception {
        JsonNode json = WebEdit.body(body);
        DashboardRequest request;
        try {
            request = ResourceWebDashboard.parseRequest(body);
        } catch (IllegalArgumentException e) {
            return WebEdit.Reply.badRequest(e.getMessage());
        }
        Long dashboardId = longValue(json, "id");
        Long directoryId = longValue(json, "directoryId");
        JsonObject dashboard = dashboardId != null ? dashboardObject(store, dashboardId) : null;
        ObjectNode storedSource = dashboard != null ? stored(store, engine, dashboard).source : null;
        List<String> messages = new ArrayList<>();
        JsonNode sent = json.get("source");
        if (sent == null || sent.isNull()) {
            messages.add("Missing source");
            throw ResourceWebEditIso.invalid(messages);
        }
        ObjectNode source = new DashboardSource(store, engine, storedSource, messages).check(sent);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        Long id = dashboard != null ? Long.valueOf(dashboard.getId()) : null;
        DashboardDefinition definition = definition(store, engine, source, id);
        Map<String, Object> config = ResourceWebDashboard.configJson(id != null ? id : 0L,
                dashboard != null ? dashboard.getName() : null,
                dashboard != null ? ResourceWebEditAnalysis.names(dashboard) : null, definition, engine.timeFrames());
        if (id == null) config.put("id", null);
        addCanEnter(store, config, definition);
        Long context = id != null ? id
                : directoryId != null && directory(store, directoryId) != null ? directoryId : null;
        DashboardResult result;
        try {
            result = engine.evaluate(definition, context, request);
        } catch (IllegalArgumentException e) {
            return WebEdit.Reply.badRequest(e.getMessage());
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("dashboard", config);
        answer.put("data", ResourceWebDashboard.dataJson(result));
        return WebEdit.Reply.ok(answer);
    }

    // ---------------------------------------------------------------- uploads

    /**
     * {@code PUT /web/dashboards/{id}/background}: a new sample of "Background"
     */
    static WebEdit.Reply background(WebEdit.Store store, long id, String fileName, InputStream in, int max, DateTime now)
            throws Exception {
        JsonObject dashboard = dashboardObject(store, id);
        if (dashboard == null) return WebEdit.Reply.notFound("Dashboard not found");
        if (!store.rights().canWrite(dashboard)) return WebEdit.Reply.forbidden("Not allowed to change this dashboard");
        byte[] bytes = upload(in, max);
        String name = ResourceWebEditIso.uploadName(fileName);
        DateTime time = ResourceWebEditAnalysis.saveTime(store, id, ATTRIBUTE_BACKGROUND, now);
        int count = store.writeFile(id, ATTRIBUTE_BACKGROUND, time, name, bytes);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(id, ATTRIBUTE_BACKGROUND, count, ACTION_BACKGROUND));
        return WebEdit.Reply.noContent();
    }

    /**
     * {@code POST /web/dashboards/{id}/images}: a "File" child of the dashboard for an Image widget, as
     * {@code ImageConfig} creates it (named like the file without extension, the image as sample of "File")
     */
    static WebEdit.Reply image(WebEdit.Store store, long id, String fileName, InputStream in, int max, DateTime now)
            throws Exception {
        JsonObject dashboard = dashboardObject(store, id);
        if (dashboard == null) return WebEdit.Reply.notFound("Dashboard not found");
        if (!store.rights().canCreate(dashboard, ResourceWebIso.CLASS_FILE)) {
            return WebEdit.Reply.forbidden("Not allowed to add images to this dashboard");
        }
        byte[] bytes = upload(in, max);
        String name = ResourceWebEditIso.uploadName(fileName);
        int dot = name.lastIndexOf('.');
        JsonObject file = store.createObject(ResourceWebEdit.objectName(dot > 0 ? name.substring(0, dot) : name),
                ResourceWebIso.CLASS_FILE, dashboard.getId());
        store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(file.getId(), file.getName(), ACTION_IMAGE));
        try {
            int count = store.writeFile(file.getId(), ResourceWebIso.ATTRIBUTE_FILE, now.withMillisOfSecond(0), name, bytes);
            store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(file.getId(), ResourceWebIso.ATTRIBUTE_FILE, count, ACTION_IMAGE));
        } catch (Exception e) {
            rollback(store, file, ACTION_IMAGE);
            throw e;
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("objectId", file.getId());
        json.put("name", file.getName());
        return WebEdit.Reply.created(json);
    }

    /**
     * @return the uploaded image
     * @throws WebEdit.Stop {@code 400} without file or if it is no image, {@code 413} if larger than {@code max}
     */
    private static byte[] upload(InputStream in, int max) throws Exception {
        if (in == null) throw WebEdit.Stop.badRequest("Missing file");
        byte[] bytes = ResourceWebEditIso.read(in, max);
        if (bytes == null) {
            throw new WebEdit.Stop(new WebEdit.Reply(413, "The file is larger than " + (max / (1024 * 1024)) + " MB"));
        }
        if (ResourceWebDashboard.contentType(bytes) == null) {
            throw WebEdit.Stop.badRequest("The file is no image (PNG, JPEG, GIF, BMP, WebP, SVG)");
        }
        return bytes;
    }

    // ---------------------------------------------------------------- input widgets

    /**
     * {@code POST /web/dashboards/{id}/widgets/{uuid}/value} with {@code {value, rowKey?}} (Value Editor additionally
     * {@code timestamp?}, {@code confirm?}): writes the value as the Control Center's input widget does
     *
     * @return the data of the widget (as in {@code POST /web/dashboards/{id}/data})
     */
    static WebEdit.Reply value(WebEdit.Store store, Engine engine, long id, int uuid, String body, String language,
                               DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject dashboard = dashboardObject(store, id);
        if (dashboard == null) return WebEdit.Reply.notFound("Dashboard not found");
        DashboardDefinition definition = definition(store, engine, stored(store, engine, dashboard).source, id);
        WidgetDefinition widget = definition.findWidget(uuid);
        if (widget == null) return WebEdit.Reply.notFound("Widget not found");
        if (!WidgetDefinition.isInputType(widget.getType())) return WebEdit.Reply.badRequest("The widget is no input widget");
        if (widget.getRows().isEmpty()) return WebEdit.Reply.badRequest("The widget has no data row");
        /* the input widgets use their first row (sampleHandler.getChartDataRows().get(0)) */
        RowDefinition row = widget.getRows().get(0);
        String rowKey = WebEdit.text(json, "rowKey");
        if (rowKey != null && !rowKey.equals(row.getKey())) return WebEdit.Reply.badRequest("Invalid rowKey");
        JsonNode valueNode = json.get("value");
        if (valueNode == null || valueNode.isNull()) return WebEdit.Reply.badRequest("Missing value");
        DateTimeZone zone = WebEdit.zone(WebEdit.text(json, "timeZone"), DateTimeZone.getDefault());

        if (WidgetDefinition.TYPE_VALUE_EDITOR.equals(widget.getType())) {
            /* ValueEditWidget: the manual entry dialog of the Control Center (EnterDataDialog) */
            ObjectNode entry = MAPPER.createObjectNode();
            entry.set("value", valueNode);
            JsonNode timestamp = json.get("timestamp");
            entry.set("timestamp", timestamp != null && !timestamp.isNull() ? timestamp
                    : MAPPER.getNodeFactory().textNode(now.withZone(zone).toString()));
            if (json.has("confirm")) entry.set("confirm", json.get("confirm"));
            WebEdit.Reply reply = ResourceWebEditValues.enter(store, row.getObjectId(), MAPPER.writeValueAsString(entry),
                    language, now);
            if (reply.status != 201) return reply;
            return WebEdit.Reply.ok(widgetData(engine, definition, widget, id, zone, now));
        }

        JsonObject object = WebEdit.readable(store, row.getObjectId());
        if (object == null) return WebEdit.Reply.notFound("Row not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to enter values for this row");
        if (store.classes().type(object.getJevisClass(), ATTRIBUTE_VALUE) == null) {
            return WebEdit.Reply.badRequest("The row has no attribute " + ATTRIBUTE_VALUE);
        }
        double value;
        switch (widget.getType()) {
            case WidgetDefinition.TYPE_TOGGLE_SWITCH:
                /* ToogleSwitchWidget: 1.0 for on, 0.0 for off */
                if (!valueNode.isBoolean()) return WebEdit.Reply.badRequest("Invalid value (true or false)");
                value = valueNode.asBoolean() ? 1d : 0d;
                break;
            case WidgetDefinition.TYPE_SLIDER: {
                if (!isFinite(valueNode)) return WebEdit.Reply.badRequest("Invalid value (number)");
                /* SliderWidget: rounded to the widget's decimals, within the slider's minimum and maximum (minMax) */
                value = round(valueNode.asDouble(), widget.getDecimals());
                double min = configNumber(widget, "minimum", 0d);
                double max = configNumber(widget, "maximum", 0d);
                if (value < Math.min(min, max) || value > Math.max(min, max)) {
                    return WebEdit.Reply.badRequest("Value outside " + ResourceWebEditValues.format(min) + " – "
                            + ResourceWebEditValues.format(max));
                }
                break;
            }
            case WidgetDefinition.TYPE_PLUS_MINUS: {
                /* PlusMinusWidget: latest value of the row ± increment, kept within the tile's range, rounded */
                String direction = valueNode.isTextual() ? valueNode.asText().trim().toLowerCase(Locale.ROOT) : "";
                if (!direction.equals("plus") && !direction.equals("minus")) {
                    return WebEdit.Reply.badRequest("Invalid value (\"plus\" or \"minus\")");
                }
                double increment = Math.max(0d, Math.min(PLUS_MINUS_MAX_INCREMENT, configNumber(widget, "increment", DEFAULT_INCREMENT)));
                JsonSample latest = store.latest(object.getId(), row.getAttribute());
                Double last = latest != null ? ResourceWebEditValues.parseDouble(latest.getValue()) : null;
                double current = last != null ? last : 0d;
                double next = current + (direction.equals("plus") ? increment : -increment);
                value = round(Math.max(PLUS_MINUS_MIN, Math.min(PLUS_MINUS_MAX, next)), widget.getDecimals());
                break;
            }
            default:
                return WebEdit.Reply.badRequest("The widget is no input widget");
        }
        /* JEVisAttribute.buildSample(new DateTime(), value) on "Value" of the row's object, no note */
        WebEdit.writeSample(store, object.getId(), ATTRIBUTE_VALUE,
                WebEdit.primitiveType(store, object, ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE),
                WebEdit.sample(now, BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(), null), ACTION_VALUE);
        return WebEdit.Reply.ok(widgetData(engine, definition, widget, id, zone, now));
    }

    private static boolean isFinite(JsonNode node) {
        return node.isNumber() && !Double.isNaN(node.asDouble()) && !Double.isInfinite(node.asDouble());
    }

    /**
     * {@code BigDecimal.setScale(decimals, HALF_UP)} like the widgets
     */
    static double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(Math.max(0, decimals), RoundingMode.HALF_UP).doubleValue();
    }

    private static double configNumber(WidgetDefinition widget, String key, double fallback) {
        Object value = widget.getConfig().get(key);
        return value instanceof Number ? ((Number) value).doubleValue() : fallback;
    }

    /**
     * @return the data answer of the widget alone (as in {@code POST /web/dashboards/{id}/data} for the dashboard's
     * time frame)
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> widgetData(Engine engine, DashboardDefinition definition, WidgetDefinition widget,
                                          long id, DateTimeZone zone, DateTime now) {
        DashboardDefinition single = new DashboardDefinition();
        single.setTimeFrame(definition.getTimeFrame());
        single.setWidgets(new ArrayList<>(Collections.singletonList(widget)));
        DashboardRequest request = new DashboardRequest().setZone(zone).setTimeFrame(definition.getTimeFrame()).setNow(now);
        Map<String, Object> data = ResourceWebDashboard.dataJson(engine.evaluate(single, id, request));
        Map<String, Object> widgets = (Map<String, Object>) data.get("widgets");
        Object result = widgets != null ? widgets.get(String.valueOf(widget.getUuid())) : null;
        if (result instanceof Map) return (Map<String, Object>) result;
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("from", data.get("from"));
        empty.put("to", data.get("to"));
        empty.put("rows", new ArrayList<>());
        return empty;
    }
}
