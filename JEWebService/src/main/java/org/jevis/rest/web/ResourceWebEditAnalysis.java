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
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisUnit;
import org.jevis.commons.chart.*;
import org.jevis.commons.unit.ChartUnits.QuantityUnits;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonI18n;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonUnit;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Editing of analyses for the web app (stage E3, see docs/WEB-API.md): create, change, copy and delete analyses and
 * the helpers of the editor (directories, row picker, units, preview).
 * <p>
 * The analysis is stored like the Control Center's {@code AnalysisHandler.saveDataModel}: a new file sample of
 * "Analysis File" with the JSON of {@code AnalysisHandler.toJsonNode}, written by {@link AnalysisWriter} as a patch of
 * the latest stored JSON. Rights are checked like {@code SaveAnalysisDialog} with the rights of the logged-in user.
 * The JAX-RS methods of the paths below {@code /web/analyses} are in {@link ResourceWebAnalysis}.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditAnalysis {

    static final String CLASS_ANALYSIS = ResourceWebNavigation.CLASS_ANALYSIS;
    static final String CLASS_DIRECTORY = "Analyses Directory";
    static final String CLASS_CLEAN_DATA = "Clean Data";
    static final String ATTRIBUTE_FILE = AnalysisReader.ANALYSIS_FILE_ATTRIBUTE_NAME;
    static final String ATTRIBUTE_DATA_MODEL = AnalysisReader.DATA_MODEL_ATTRIBUTE_NAME;
    static final String ATTRIBUTE_VALUE = AnalysisReader.VALUE_ATTRIBUTE_NAME;
    /**
     * Classes a row may have ({@code TreeSelectionDialog.allData}/{@code allCleanData})
     */
    static final List<String> ROW_CLASSES = Collections.unmodifiableList(Arrays.asList("Data", "Clean Data",
            "Base Data", "Math Data", "Forecast Data", "String Data"));
    static final String VERSION_NONE = "none";
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_TEXT_LENGTH = 1000;
    static final int MAX_SEARCH_HITS = 200;
    static final int MIN_SEARCH_LENGTH = 2;
    static final String ACTION_CREATE = "analysis-create";
    static final String ACTION_SAVE = "analysis-save";
    static final String ACTION_COPY = "analysis-copy";
    static final String ACTION_DELETE = "analysis-delete";
    static final List<String> COLOR_MAPPINGS = Collections.unmodifiableList(Arrays.asList("LIME_YELLOW_RED",
            "BLUE_CYAN_GREEN_YELLOW_RED", "INFRARED_1", "INFRARED_2", "INFRARED_3", "INFRARED_4", "BLUE_GREEN_RED",
            "BLUE_BLACK_RED", "BLUE_YELLOW_RED", "BLUE_TRANSPARENT_RED", "GREEN_BLACK_RED", "GREEN_YELLOW_RED",
            "RAINBOW", "BLACK_WHITE", "WHITE_BLACK"));
    static final List<String> ORIENTATIONS = Collections.unmodifiableList(Arrays.asList("HORIZONTAL", "VERTICAL"));
    static final List<String> BUBBLE_TYPES = Collections.unmodifiableList(Arrays.asList("NONE", "X", "Y", "SIZE"));
    static final List<String> FILLS = Collections.unmodifiableList(Arrays.asList("NONE", "ABOVE", "BELOW"));
    private static final Pattern COLOR = Pattern.compile("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?");
    private static final Pattern TIME = Pattern.compile("\\d{2}:\\d{2}(:\\d{2}(\\.\\d{1,3})?)?");
    /* keys of the GET answer that are not part of the definition (ignored when sent back) */
    private static final Set<String> READ_ONLY_TOP = new HashSet<>(Arrays.asList("id", "name", "names", "defaults",
            "version", "canWrite", "canDelete", "directoryId"));
    private static final Set<String> READ_ONLY_ROW = new HashSet<>(Arrays.asList("key", "readable", "unit", "rowClass",
            "calculationId"));
    /**
     * One create at a time, so two analyses do not get the same name
     */
    private static final Object CREATE_LOCK = new Object();
    private static final Logger logger = LogManager.getLogger(ResourceWebEditAnalysis.class);

    // ---------------------------------------------------------------- endpoints of the row picker

    @GET
    @Path("rows/tree")
    @Produces(MediaType.APPLICATION_JSON)
    public Response tree(@Context HttpHeaders httpHeaders, @QueryParam("parent") String parent,
                         @QueryParam("q") String q, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "read the row tree", store -> tree(store, parent, q, language));
    }

    @GET
    @Path("rows/{id}/units")
    @Produces(MediaType.APPLICATION_JSON)
    public Response units(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                          @QueryParam("attribute") String attribute) {
        return WebEdit.runSql(httpHeaders, "read the units of " + id,
                (ds, store) -> units(store, sqlEngine(ds), id, attribute));
    }

    // ---------------------------------------------------------------- access beyond the store

    /**
     * What the analysis endpoints need from the JEVis API of the request (production: the read-only
     * {@link LocalDataSource} with the rights of the user).
     */
    interface Engine {
        /**
         * @return the analysis as W2 reads it ({@link AnalysisReader#read}, rows resolved), null if it cannot be read
         */
        AnalysisDefinition read(long id) throws Exception;

        /**
         * @return the analysis in the legacy format ("Data Model"/"Charts"), rows not resolved
         */
        AnalysisDefinition readLegacy(long id) throws Exception;

        /**
         * {@code ChartTools.getCalculationId}: the calculation whose output is the row (for a clean data row its data
         * object)
         *
         * @return the id of the calculation, -1 if there is none
         */
        long calculationId(long rowId);

        /**
         * @return the display unit of the attribute, null if none
         */
        JsonUnit displayUnit(long id, String attribute);

        /**
         * @param definition read by {@link AnalysisReader} (keys set, rows not resolved)
         * @param contextId  the analysis or directory whose site defines the working day, may be null
         * @return the answer of {@code POST /web/analyses/{id}/data}
         */
        Map<String, Object> data(AnalysisDefinition definition, Long contextId, EvaluationSettings settings) throws Exception;
    }

    static Engine sqlEngine(SQLDataSource sql) {
        return new Engine() {
            private LocalDataSource local;
            private CalculationLookup calculations;

            private LocalDataSource local() {
                if (local == null) local = new LocalDataSource(sql);
                return local;
            }

            @Override
            public AnalysisDefinition read(long id) throws Exception {
                JEVisObject object = local().getObject(id);
                if (object == null || !CLASS_ANALYSIS.equals(object.getJEVisClassName())) return null;
                return new AnalysisReader().read(object);
            }

            @Override
            public AnalysisDefinition readLegacy(long id) {
                JEVisObject object = local().getObject(id);
                return object != null ? new AnalysisReader().readLegacy(local(), object) : new AnalysisDefinition();
            }

            @Override
            public long calculationId(long rowId) {
                try {
                    JEVisObject object = local().getObject(rowId);
                    if (object == null) return -1;
                    if (calculations == null) calculations = new CalculationLookup(local());
                    return calculations.isObjectCalculated(CommonMethods.getFirstParentalDataObject(object));
                } catch (Exception e) {
                    logger.warn("Could not find the calculation of {}", rowId, e);
                    return -1;
                }
            }

            @Override
            public JsonUnit displayUnit(long id, String attribute) {
                JsonAttribute json = sql.getAttribute(id, attribute);
                return json != null ? json.getDisplayUnit() : null;
            }

            @Override
            public Map<String, Object> data(AnalysisDefinition definition, Long contextId, EvaluationSettings settings) {
                LocalDataSource ds = local();
                new AnalysisReader().resolveRows(ds, definition);
                JEVisObject context = contextId != null ? ds.getObject(contextId) : null;
                AnalysisResult result = new AnalysisEvaluator(ds).evaluate(context, definition, settings);
                Map<String, Map<String, Object>> editInfo = new HashMap<>();
                Map<String, List<Map<String, Object>>> notes = ResourceWebAnalysis.notes(sql, definition, result, editInfo);
                return ResourceWebAnalysis.dataJson(result, notes, editInfo);
            }
        };
    }

    // ---------------------------------------------------------------- reading

    /**
     * {@code GET /web/analyses/{id}}: the W2 configuration with what the editor needs
     */
    static WebEdit.Reply config(WebEdit.Store store, Engine engine, long id) throws Exception {
        JsonObject object = analysisObject(store, id);
        AnalysisDefinition definition = object != null ? engine.read(id) : null;
        if (definition == null) return WebEdit.Reply.notFound("Analysis not found");
        return WebEdit.Reply.ok(answer(store, object, definition));
    }

    /**
     * @return the readable analysis outside the recycle bin, else null
     */
    static JsonObject analysisObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && CLASS_ANALYSIS.equals(object.getJevisClass()) ? object : null;
    }

    /**
     * @return the GET answer: W2 configuration, the editable settings and what the user may do
     */
    static Map<String, Object> answer(WebEdit.Store store, JsonObject object, AnalysisDefinition definition) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        Map<String, Object> json = ResourceWebAnalysis.configJson(object.getId(), object.getName(), names(object), definition);
        addEditable(json, definition, lookup);
        json.put("version", version(versionSample(store, object.getId())));
        WebEdit.Rights rights = store.rights();
        json.put("canWrite", rights.canWrite(object));
        json.put("canDelete", rights.canDelete(object));
        Long parent = lookup.parent(object.getId());
        JsonObject directory = parent != null ? WebEdit.readable(store, parent) : null;
        json.put("directoryId", directory != null && CLASS_DIRECTORY.equals(directory.getJevisClass()) ? directory.getId() : null);
        return json;
    }

    /**
     * Adds the settings of the definition that the W2 answer does not have (all additive).
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> addEditable(Map<String, Object> json, AnalysisDefinition definition,
                                           ResourceWebModules.Lookup lookup) {
        json.put("forcedInterval", definition.getForcedInterval());
        Map<String, Object> dayFilter = new LinkedHashMap<>();
        dayFilter.put("weekdays", definition.getDayFilterWeekdays());
        dayFilter.put("holidays", definition.isDayFilterHolidays());
        dayFilter.put("dates", definition.getDayFilterDates());
        json.put("dayFilter", dayFilter);
        List<Map<String, Object>> lines = new ArrayList<>();
        for (LimitLineDefinition line : definition.getLimitLines()) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("value", ResourceWebAnalysis.finite(line.getValue()));
            l.put("label", line.getLabel());
            l.put("color", WebColor.toHex(line.getColor(), "#ff0000"));
            l.put("axis", line.getAxis());
            l.put("fill", line.getFill());
            lines.add(l);
        }
        json.put("limitLines", lines);

        List<Map<String, Object>> charts = (List<Map<String, Object>>) json.get("charts");
        for (int i = 0; i < definition.getCharts().size() && charts != null && i < charts.size(); i++) {
            ChartDefinition chart = definition.getCharts().get(i);
            Map<String, Object> c = charts.get(i);
            c.put("fractionDigitsAuto", chart.getFractionDigitsAuto());
            c.put("filterEnabled", chart.isFilterEnabled());
            c.put("coloringEnabled", chart.isColoringEnabled());
            c.put("showColumnSums", chart.isShowColumnSums());
            c.put("showRowSums", chart.isShowRowSums());
            c.put("dayStart", timeText(chart.getDayStart()));
            c.put("dayEnd", timeText(chart.getDayEnd()));
            c.put("filterTexts", new LinkedHashMap<>(chart.getFilterTexts()));
            List<Map<String, Object>> rows = (List<Map<String, Object>>) c.get("rows");
            for (int j = 0; j < chart.getRows().size() && rows != null && j < rows.size(); j++) {
                RowDefinition row = chart.getRows().get(j);
                Map<String, Object> r = rows.get(j);
                boolean readable = Boolean.TRUE.equals(row.getReadable());
                JsonObject object = readable ? lookup.readable(row.getObjectId()) : null;
                r.put("rowClass", object != null ? object.getJevisClass() : null);
                r.put("unitPrefix", readable ? row.getUnitPrefix() : null);
                r.put("unitFormula", readable ? row.getUnitFormula() : null);
                r.put("unitLabel", readable ? row.getUnitLabel() : null);
                r.put("calculationId", readable ? row.getCalculationId() : null);
                Map<String, Object> interval = new LinkedHashMap<>();
                interval.put("enabled", row.isIntervalEnabled());
                interval.put("start", row.getIntervalStart());
                interval.put("end", row.getIntervalEnd());
                r.put("interval", interval);
                r.put("css", row.getCss());
            }
        }
        return json;
    }

    /**
     * @return {@code HH:mm:ss} of a stored day start/end ({@code HH:mm:ss.SSS}), null for none
     */
    static String timeText(String stored) {
        if (stored == null || stored.trim().isEmpty()) return null;
        try {
            return LocalTime.parse(stored.trim()).toString("HH:mm:ss");
        } catch (IllegalArgumentException e) {
            return stored;
        }
    }

    /**
     * @return the latest sample of "Analysis File", for an analysis in the legacy format the latest of "Data Model"
     */
    static JsonSample versionSample(WebEdit.Store store, long id) throws Exception {
        JsonSample file = store.latest(id, ATTRIBUTE_FILE);
        return file != null ? file : store.latest(id, ATTRIBUTE_DATA_MODEL);
    }

    static String version(JsonSample sample) {
        String version = WebEdit.version(sample);
        return version != null ? version : VERSION_NONE;
    }

    /**
     * @return whether the version sent is the current one ({@value #VERSION_NONE}: never saved)
     * @throws WebEdit.Stop {@code 400} if missing or invalid
     */
    static boolean sameVersion(String given, JsonSample current) throws WebEdit.Stop {
        if (given != null && VERSION_NONE.equals(given.trim())) return current == null;
        return WebEdit.sameVersion(given, current);
    }

    static Map<String, String> names(JsonObject object) {
        Map<String, String> names = new TreeMap<>();
        if (object.getI18n() != null) {
            for (JsonI18n i18n : object.getI18n()) {
                if (i18n.getKey() != null && i18n.getValue() != null && !i18n.getValue().trim().isEmpty()) {
                    names.put(i18n.getKey(), i18n.getValue());
                }
            }
        }
        return names;
    }

    /**
     * @return the name in the language (local name), else the name
     */
    static String localName(JsonObject object, String language) {
        String local = names(object).get(language);
        return local != null ? local : object.getName();
    }

    /**
     * The stored state of an analysis: the latest JSON (null if never saved in the current format) and its definition
     */
    static final class Stored {
        final JsonObject object;
        final JsonSample version;
        final ObjectNode tree;
        final AnalysisDefinition definition;

        Stored(JsonObject object, JsonSample version, ObjectNode tree, AnalysisDefinition definition) {
            this.object = object;
            this.version = version;
            this.tree = tree;
            this.definition = definition;
        }
    }

    static Stored stored(WebEdit.Store store, Engine engine, JsonObject object) throws Exception {
        long id = object.getId();
        JsonSample file = store.latest(id, ATTRIBUTE_FILE);
        if (file != null) {
            ObjectNode tree = new AnalysisWriter().parse(store.file(id, ATTRIBUTE_FILE, file));
            AnalysisDefinition definition = tree != null ? new AnalysisReader().readAnalysisFile(tree) : new AnalysisDefinition();
            return new Stored(object, file, tree, definition);
        }
        AnalysisDefinition legacy = engine.readLegacy(id);
        return new Stored(object, store.latest(id, ATTRIBUTE_DATA_MODEL), null,
                legacy != null ? legacy : new AnalysisDefinition());
    }

    /**
     * Resolves the rows of a definition read from the written JSON (like {@link AnalysisReader#resolveRows}, with the
     * objects of the request): readable flag and object name (for a clean data row the name of its data object).
     */
    static void resolveRows(WebEdit.Store store, AnalysisDefinition definition) throws Exception {
        for (ChartDefinition chart : definition.getCharts()) {
            for (RowDefinition row : chart.getRows()) {
                JsonObject object = WebEdit.readable(store, row.getObjectId());
                row.setReadable(object != null);
                if (object == null) continue;
                row.setObjectName(objectName(store, row.getObjectId()));
            }
        }
    }

    /**
     * @return the name a row shows without own name ({@code ChartDataRow.getName()}): the object name, for a clean
     * data row the name of its data object; null if not readable
     */
    static String objectName(WebEdit.Store store, long id) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        JsonObject object = WebEdit.readable(store, id);
        if (object == null) return null;
        JsonObject named = object;
        if (CLASS_CLEAN_DATA.equals(object.getJevisClass())) {
            Set<Long> visited = new HashSet<>();
            Long parent = lookup.parent(object.getId());
            while (parent != null && visited.add(parent)) {
                JsonObject p = lookup.readable(parent);
                if (p == null) break;
                if ("Data".equals(p.getJevisClass()) || "Base Data".equals(p.getJevisClass())) {
                    named = p;
                    break;
                }
                parent = lookup.parent(p.getId());
            }
        }
        return named.getName();
    }

    // ---------------------------------------------------------------- writing

    /**
     * {@code POST /web/analyses} with {@code {directoryId, name, definition}}
     */
    static WebEdit.Reply create(WebEdit.Store store, Engine engine, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        Long directoryId = longValue(json, "directoryId");
        if (directoryId == null) return WebEdit.Reply.badRequest("Missing directoryId");
        JsonObject directory = directory(store, directoryId);
        if (directory == null) return WebEdit.Reply.notFound("Directory not found");
        if (!store.rights().canCreate(directory, CLASS_ANALYSIS)) {
            return WebEdit.Reply.forbidden("Not allowed to create analyses in this directory");
        }
        List<String> messages = new ArrayList<>();
        String name = name(json, messages, true);
        JsonNode definitionJson = json.get("definition");
        AnalysisDefinition definition = definitionJson == null || definitionJson.isNull() ? new AnalysisDefinition()
                : new Parser(store, engine, null, false, messages).parse(definitionJson);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        ObjectNode tree = new AnalysisWriter().patch(null, definition, now);
        return createAnalysis(store, directory, name, tree, language, now, ACTION_CREATE);
    }

    /**
     * {@code POST /web/analyses/{id}/copy} with {@code {directoryId, name, definition?}} ("Speichern unter")
     */
    static WebEdit.Reply copy(WebEdit.Store store, Engine engine, long id, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject source = analysisObject(store, id);
        if (source == null) return WebEdit.Reply.notFound("Analysis not found");
        Long directoryId = longValue(json, "directoryId");
        if (directoryId == null) return WebEdit.Reply.badRequest("Missing directoryId");
        JsonObject directory = directory(store, directoryId);
        if (directory == null) return WebEdit.Reply.notFound("Directory not found");
        if (!store.rights().canCreate(directory, CLASS_ANALYSIS)) {
            return WebEdit.Reply.forbidden("Not allowed to create analyses in this directory");
        }
        List<String> messages = new ArrayList<>();
        String name = name(json, messages, true);
        Stored stored = stored(store, engine, source);
        JsonNode definitionJson = json.get("definition");
        AnalysisWriter writer = new AnalysisWriter();
        ObjectNode tree;
        if (definitionJson == null || definitionJson.isNull()) {
            tree = stored.tree != null ? stored.tree.deepCopy() : writer.toJson(stored.definition, now);
        } else {
            AnalysisDefinition definition = new Parser(store, engine, stored.definition, false, messages).parse(definitionJson);
            tree = writer.patch(stored.tree, definition, now);
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        return createAnalysis(store, directory, name, tree, language, now, ACTION_COPY);
    }

    private static WebEdit.Reply createAnalysis(WebEdit.Store store, JsonObject directory, String name, ObjectNode tree,
                                                String language, DateTime now, String action) throws Exception {
        synchronized (CREATE_LOCK) {
            JsonObject existing = nameTaken(store, directory.getId(), name, null);
            if (existing != null) return nameTakenReply(existing);
            JsonObject analysis = store.createObject(name, CLASS_ANALYSIS, directory.getId());
            store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(analysis.getId(), analysis.getName(), action));
            store.lookup().addReadable(analysis, directory.getId());
            try {
                save(store, analysis, tree, language, now, action);
            } catch (Exception e) {
                rollback(store, analysis, action);
                throw e;
            }
            return WebEdit.Reply.created(written(store, analysis, tree));
        }
    }

    /**
     * {@code PUT /web/analyses/{id}} with {@code {version, definition, name?}}
     */
    static WebEdit.Reply update(WebEdit.Store store, Engine engine, long id, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = analysisObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Analysis not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this analysis");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        List<String> messages = new ArrayList<>();
        String name = json.has("name") && !json.get("name").isNull() ? name(json, messages, true) : null;
        Stored stored = stored(store, engine, object);
        JsonNode definitionJson = json.get("definition");
        AnalysisDefinition definition = null;
        if (definitionJson != null && !definitionJson.isNull()) {
            definition = new Parser(store, engine, stored.definition, false, messages).parse(definitionJson);
        } else if (name == null && messages.isEmpty()) {
            messages.add("Missing definition");
        }
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!sameVersion(version, stored.version)) return WebEdit.Reply.conflict(current(store, engine, object));
        if (name != null && !name.equals(object.getName())) {
            Long parent = store.lookup().parent(id);
            JsonObject existing = parent != null ? nameTaken(store, parent, name, id) : null;
            if (existing != null) return nameTakenReply(existing);
            store.rename(object, name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, ACTION_SAVE));
            object.setName(name);
        }
        if (definition == null) return WebEdit.Reply.ok(current(store, engine, object));
        ObjectNode tree = new AnalysisWriter().patch(stored.tree, definition, now);
        save(store, object, tree, language, now, ACTION_SAVE);
        return WebEdit.Reply.ok(written(store, object, tree));
    }

    /**
     * {@code DELETE /web/analyses/{id}?version=…}: moves the analysis to the recycle bin
     */
    static WebEdit.Reply delete(WebEdit.Store store, Engine engine, long id, String version) throws Exception {
        JsonObject object = analysisObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Analysis not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this analysis");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!sameVersion(version, versionSample(store, id))) return WebEdit.Reply.conflict(current(store, engine, object));
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }

    /**
     * Writes the JSON as new file sample of "Analysis File" ({@code AnalysisHandler.saveDataModel}).
     */
    static void save(WebEdit.Store store, JsonObject analysis, ObjectNode tree, String language, DateTime now,
                     String action) throws Exception {
        DateTime time = saveTime(store, analysis.getId(), now);
        String fileName = AnalysisWriter.fileName(fileNamePart(localName(analysis, language)), time);
        int count = store.writeFile(analysis.getId(), ATTRIBUTE_FILE, time, fileName, new AnalysisWriter().write(tree));
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(analysis.getId(), ATTRIBUTE_FILE, count, action));
    }

    /**
     * @return now to the second, after the latest file sample (waits for the next second if there already is one in
     * this second; the samples have second precision)
     */
    static DateTime saveTime(WebEdit.Store store, long id, DateTime now) throws Exception {
        return saveTime(store, id, ATTRIBUTE_FILE, now);
    }

    /**
     * @return now to the second, after the latest sample of the attribute (waits for the next second if there already
     * is one in this second; the samples have second precision)
     */
    static DateTime saveTime(WebEdit.Store store, long id, String attribute, DateTime now) throws Exception {
        DateTime time = now.withMillisOfSecond(0);
        JsonSample latest = store.latest(id, attribute);
        Long last = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
        if (last != null && last >= time.getMillis()) {
            time = new DateTime(last, now.getZone()).withMillisOfSecond(0).plusSeconds(1);
        }
        if (time.isAfter(now)) store.waitUntil(time);
        return time;
    }

    /**
     * @return the name without characters that are not allowed in a stored file name ({@link WebEdit#storedFile})
     */
    static String fileNamePart(String name) {
        StringBuilder sb = new StringBuilder();
        String text = name != null ? name : "";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(Character.isISOControl(c) || "\\/:*?\"<>|".indexOf(c) >= 0 ? '_' : c);
        }
        String safe = sb.toString().trim();
        while (safe.contains("..")) safe = safe.replace("..", ".");
        return safe.isEmpty() ? CLASS_ANALYSIS : safe;
    }

    /**
     * @return the answer for the JSON just written (without reading it back from the data source)
     */
    private static Map<String, Object> written(WebEdit.Store store, JsonObject analysis, ObjectNode tree) throws Exception {
        AnalysisDefinition definition = new AnalysisReader().readAnalysisFile(tree);
        definition.setId(analysis.getId());
        resolveRows(store, definition);
        return answer(store, analysis, definition);
    }

    /**
     * @return the current analysis (body of a {@code 409})
     */
    private static Map<String, Object> current(WebEdit.Store store, Engine engine, JsonObject analysis) throws Exception {
        AnalysisDefinition definition = engine.read(analysis.getId());
        return answer(store, analysis, definition != null ? definition : new AnalysisDefinition());
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
     * @return the readable "Analyses Directory" outside the recycle bin, else null
     */
    static JsonObject directory(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && CLASS_DIRECTORY.equals(object.getJevisClass()) ? object : null;
    }

    /**
     * Names are unique within a directory ({@code SaveAnalysisDialog}: the names of the children)
     *
     * @return a readable child outside the recycle bin with the name (other than {@code except}), else null
     */
    static JsonObject nameTaken(WebEdit.Store store, long parent, String name, Long except) throws Exception {
        for (JsonObject child : store.lookup().readableChildren(parent)) {
            if (!ResourceWebIso.isDeleted(child) && name.equals(child.getName())
                    && (except == null || child.getId() != except)) {
                return child;
            }
        }
        return null;
    }

    private static WebEdit.Reply nameTakenReply(JsonObject existing) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("nameTaken", true);
        json.put("id", existing.getId());
        return WebEdit.Reply.conflict(json);
    }

    /**
     * @return the trimmed name (1–{@value #MAX_NAME_LENGTH} characters, no control characters); problems are added
     * to the messages
     */
    static String name(JsonNode json, List<String> messages, boolean required) {
        JsonNode node = json.get("name");
        if (node == null || node.isNull()) {
            if (required) messages.add("Missing name");
            return null;
        }
        if (!node.isTextual()) {
            messages.add("Invalid name");
            return null;
        }
        String name = node.asText().trim();
        if (name.isEmpty()) {
            messages.add("Missing name");
        } else if (name.length() > MAX_NAME_LENGTH) {
            messages.add("Name longer than " + MAX_NAME_LENGTH + " characters");
        } else {
            for (int i = 0; i < name.length(); i++) {
                if (Character.isISOControl(name.charAt(i))) {
                    messages.add("Invalid characters in name");
                    break;
                }
            }
        }
        return name;
    }

    private static Long longValue(JsonNode json, String field) throws WebEdit.Stop {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (!node.canConvertToLong() || !node.isIntegralNumber()) throw WebEdit.Stop.badRequest("Invalid " + field);
        return node.asLong();
    }

    // ---------------------------------------------------------------- definition sent

    /**
     * Reads the definition sent into an {@link AnalysisDefinition} and validates it; all problems are collected in
     * the messages. Keys not sent keep the stored value (charts by id, rows by object and attribute), new charts and
     * rows get the Control Center's defaults.
     */
    static final class Parser {
        private final WebEdit.Store store;
        private final Engine engine;
        private final AnalysisDefinition stored;
        private final boolean preview;
        private final List<String> messages;
        private final Map<String, List<Map<String, String>>> units = new HashMap<>();

        /**
         * @param stored  the stored analysis, null for a new one
         * @param preview unreadable rows are allowed (they answer NO_PERMISSION)
         */
        Parser(WebEdit.Store store, Engine engine, AnalysisDefinition stored, boolean preview, List<String> messages) {
            this.store = store;
            this.engine = engine;
            this.stored = stored;
            this.preview = preview;
            this.messages = messages;
        }

        AnalysisDefinition parse(JsonNode json) throws Exception {
            AnalysisDefinition base = stored != null ? stored : new AnalysisDefinition();
            AnalysisDefinition definition = new AnalysisDefinition();
            definition.setAutoSize(base.isAutoSize());
            definition.setChartsPerScreen(base.getChartsPerScreen());
            definition.setHorizontalPies(base.getHorizontalPies());
            definition.setHorizontalTables(base.getHorizontalTables());
            definition.setForcedInterval(base.getForcedInterval());
            definition.setDayFilterWeekdays(base.getDayFilterWeekdays());
            definition.setDayFilterHolidays(base.isDayFilterHolidays());
            definition.setDayFilterDates(base.getDayFilterDates());
            for (LimitLineDefinition line : base.getLimitLines()) definition.getLimitLines().add(copy(line));
            for (ChartDefinition chart : base.getCharts()) definition.getCharts().add(copy(chart));
            if (json == null || !json.isObject()) {
                messages.add("Invalid definition");
                return definition;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String key = field.getKey();
                JsonNode value = field.getValue();
                switch (key) {
                    case "chartsPerScreen":
                        definition.setChartsPerScreen(integer(value, key, 1, 20, definition.getChartsPerScreen()));
                        break;
                    case "horizontalPies":
                        definition.setHorizontalPies(integer(value, key, 1, 20, definition.getHorizontalPies()));
                        break;
                    case "horizontalTables":
                        definition.setHorizontalTables(integer(value, key, 1, 20, definition.getHorizontalTables()));
                        break;
                    case "autoSize":
                        definition.setAutoSize(bool(value, key, definition.isAutoSize()));
                        break;
                    case "forcedInterval":
                        String forced = text(value, key, "");
                        definition.setForcedInterval(forced != null ? forced.trim() : "");
                        break;
                    case "dayFilter":
                        dayFilter(definition, value);
                        break;
                    case "limitLines":
                        limitLines(definition, value);
                        break;
                    case "charts":
                        charts(definition, value);
                        break;
                    default:
                        if (!READ_ONLY_TOP.contains(key)) messages.add("Unknown key: " + key);
                }
            }
            return definition;
        }

        private void dayFilter(AnalysisDefinition definition, JsonNode value) {
            if (value == null || value.isNull()) {
                definition.setDayFilterWeekdays("");
                definition.setDayFilterHolidays(false);
                definition.setDayFilterDates("");
                return;
            }
            if (!value.isObject()) {
                messages.add("Invalid dayFilter");
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                switch (field.getKey()) {
                    case "weekdays":
                        String weekdays = text(field.getValue(), "dayFilter.weekdays", "");
                        if (weekdays == null) break;
                        weekdays = weekdays.trim();
                        for (String part : weekdays.split(",")) {
                            if (part.trim().isEmpty()) continue;
                            if (!part.trim().matches("[1-7]")) {
                                messages.add("Invalid dayFilter.weekdays: " + part.trim());
                                break;
                            }
                        }
                        definition.setDayFilterWeekdays(weekdays);
                        break;
                    case "holidays":
                        definition.setDayFilterHolidays(bool(field.getValue(), "dayFilter.holidays", false));
                        break;
                    case "dates":
                        String dates = text(field.getValue(), "dayFilter.dates", "");
                        if (dates == null) break;
                        dates = dates.trim();
                        for (String part : dates.split("[,;\\s]+")) {
                            if (part.trim().isEmpty()) continue;
                            try {
                                if (!part.trim().matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
                                LocalDate.parse(part.trim());
                            } catch (IllegalArgumentException e) {
                                messages.add("Invalid dayFilter.dates: " + part.trim());
                                break;
                            }
                        }
                        definition.setDayFilterDates(dates);
                        break;
                    default:
                        messages.add("Unknown key: dayFilter." + field.getKey());
                }
            }
        }

        private void limitLines(AnalysisDefinition definition, JsonNode value) {
            definition.getLimitLines().clear();
            if (value == null || value.isNull()) return;
            if (!value.isArray()) {
                messages.add("Invalid limitLines");
                return;
            }
            int index = 0;
            for (JsonNode node : value) {
                String prefix = "limitLines[" + index++ + "].";
                if (!node.isObject()) {
                    messages.add("Invalid " + prefix.substring(0, prefix.length() - 1));
                    continue;
                }
                LimitLineDefinition line = new LimitLineDefinition();
                Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    String key = prefix + field.getKey();
                    switch (field.getKey()) {
                        case "value":
                            line.setValue(number(field.getValue(), key, line.getValue()));
                            break;
                        case "label":
                            String label = text(field.getValue(), key, "");
                            line.setLabel(label != null ? label : "");
                            break;
                        case "color":
                            line.setColor(color(field.getValue(), key, line.getColor()));
                            break;
                        case "axis":
                            line.setAxis(integer(field.getValue(), key, 0, 1, line.getAxis()));
                            break;
                        case "fill":
                            line.setFill(oneOf(field.getValue(), key, FILLS, line.getFill()));
                            break;
                        default:
                            messages.add("Unknown key: " + key);
                    }
                }
                definition.getLimitLines().add(line);
            }
        }

        private void charts(AnalysisDefinition definition, JsonNode value) throws Exception {
            List<ChartDefinition> charts = new ArrayList<>();
            definition.setCharts(charts);
            if (value == null || value.isNull()) return;
            if (!value.isArray()) {
                messages.add("Invalid charts");
                return;
            }
            Set<Integer> ids = new HashSet<>();
            int maxId = -1;
            for (JsonNode node : value) {
                JsonNode id = node.get("id");
                if (id != null && id.canConvertToInt()) maxId = Math.max(maxId, id.asInt());
            }
            if (stored != null) {
                for (ChartDefinition chart : stored.getCharts()) maxId = Math.max(maxId, chart.getId());
            }
            int index = 0;
            for (JsonNode node : value) {
                String prefix = "charts[" + index++ + "].";
                if (!node.isObject()) {
                    messages.add("Invalid " + prefix.substring(0, prefix.length() - 1));
                    continue;
                }
                int id;
                JsonNode idNode = node.get("id");
                if (idNode == null || idNode.isNull()) {
                    /* NewSelectionDialog.getNextChartId */
                    id = ++maxId;
                } else if (!idNode.isIntegralNumber() || !idNode.canConvertToInt() || idNode.asInt() < 0) {
                    messages.add("Invalid " + prefix + "id");
                    continue;
                } else {
                    id = idNode.asInt();
                }
                if (!ids.add(id)) {
                    messages.add("Duplicate chart id: " + id);
                    continue;
                }
                ChartDefinition storedChart = storedChart(id);
                ChartDefinition chart = storedChart != null ? copy(storedChart) : new ChartDefinition();
                chart.setId(id);
                chart(chart, node, prefix);
                charts.add(chart);
            }
        }

        private void chart(ChartDefinition chart, JsonNode node, String prefix) throws Exception {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                String key = prefix + field.getKey();
                switch (field.getKey()) {
                    case "id":
                        break;
                    case "name":
                        String name = text(value, key, "");
                        chart.setName(name != null ? name : "");
                        break;
                    case "type":
                        chart.setType(chartType(value, key, chart.getType()));
                        break;
                    case "minFractionDigits":
                        chart.setMinFractionDigits(integer(value, key, 0, 20, chart.getMinFractionDigits()));
                        break;
                    case "maxFractionDigits":
                        chart.setMaxFractionDigits(integer(value, key, 0, 20, chart.getMaxFractionDigits()));
                        break;
                    case "fractionDigitsAuto":
                        if (value == null || value.isNull()) {
                            chart.setFractionDigitsAuto(null);
                        } else {
                            chart.setFractionDigitsAuto(bool(value, key, false));
                        }
                        break;
                    case "height":
                        chart.setHeight(value == null || value.isNull() ? -1d : number(value, key, chart.getHeight()));
                        break;
                    case "orientation":
                        chart.setOrientation(oneOf(value, key, ORIENTATIONS, chart.getOrientation()));
                        break;
                    case "fixYAxisToZero":
                        chart.setFixYAxisToZero(bool(value, key, chart.isFixYAxisToZero()));
                        break;
                    case "xAxisTitle":
                        chart.setXAxisTitle(text(value, key, null));
                        break;
                    case "yAxisTitle":
                        chart.setYAxisTitle(text(value, key, null));
                        break;
                    case "groupingInterval":
                        chart.setGroupingInterval(number(value, key, chart.getGroupingInterval()));
                        break;
                    case "colorMapping":
                        chart.setColorMapping(oneOf(value, key, COLOR_MAPPINGS, chart.getColorMapping()));
                        break;
                    case "filterEnabled":
                        chart.setFilterEnabled(bool(value, key, chart.isFilterEnabled()));
                        break;
                    case "coloringEnabled":
                        chart.setColoringEnabled(bool(value, key, chart.isColoringEnabled()));
                        break;
                    case "showColumnSums":
                        chart.setShowColumnSums(bool(value, key, chart.isShowColumnSums()));
                        break;
                    case "showRowSums":
                        chart.setShowRowSums(bool(value, key, chart.isShowRowSums()));
                        break;
                    case "dayStart":
                        chart.setDayStart(time(value, key, chart.getDayStart()));
                        break;
                    case "dayEnd":
                        chart.setDayEnd(time(value, key, chart.getDayEnd()));
                        break;
                    case "filterTexts":
                        filterTexts(chart, value, key);
                        break;
                    case "rows":
                        rows(chart, value, prefix);
                        break;
                    default:
                        messages.add("Unknown key: " + key);
                }
            }
        }

        private void filterTexts(ChartDefinition chart, JsonNode value, String key) {
            Map<String, String> texts = new LinkedHashMap<>();
            if (value != null && !value.isNull()) {
                if (!value.isObject()) {
                    messages.add("Invalid " + key);
                    return;
                }
                Iterator<Map.Entry<String, JsonNode>> it = value.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> entry = it.next();
                    String text = text(entry.getValue(), key + "." + entry.getKey(), null);
                    if (text != null && !text.isEmpty()) texts.put(entry.getKey(), text);
                }
            }
            chart.setFilterTexts(texts);
        }

        private void rows(ChartDefinition chart, JsonNode value, String chartPrefix) throws Exception {
            List<RowDefinition> rows = new ArrayList<>();
            chart.setRows(rows);
            if (value == null || value.isNull()) return;
            if (!value.isArray()) {
                messages.add("Invalid " + chartPrefix + "rows");
                return;
            }
            int index = 0;
            for (JsonNode node : value) {
                String prefix = chartPrefix + "rows[" + index++ + "].";
                if (!node.isObject()) {
                    messages.add("Invalid " + prefix.substring(0, prefix.length() - 1));
                    continue;
                }
                JsonNode idNode = node.get("objectId");
                if (idNode == null || !idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
                    messages.add("Missing or invalid " + prefix + "objectId");
                    continue;
                }
                long objectId = idNode.asLong();
                String attribute = text(node.get("attribute"), prefix + "attribute", ATTRIBUTE_VALUE);
                if (attribute == null || attribute.trim().isEmpty()) attribute = ATTRIBUTE_VALUE;
                RowDefinition storedRow = storedRow(chart.getId(), objectId, attribute);
                JsonObject object = WebEdit.readable(store, objectId);
                if (object == null) {
                    if (storedRow != null) {
                        /* a stored row the user may not read keeps its stored settings */
                        rows.add(copy(storedRow));
                    } else if (preview) {
                        RowDefinition row = new RowDefinition();
                        row.setObjectId(objectId);
                        row.setAttribute(attribute);
                        rows.add(row);
                    } else {
                        messages.add("Row " + objectId + " not found or not readable");
                    }
                    continue;
                }
                if (!ROW_CLASSES.contains(object.getJevisClass())) {
                    messages.add("Row " + objectId + " is no data row (" + object.getJevisClass() + ")");
                    continue;
                }
                if (store.classes().type(object.getJevisClass(), attribute) == null) {
                    messages.add("Row " + objectId + " has no attribute " + attribute);
                    continue;
                }
                RowDefinition row;
                if (storedRow != null) {
                    row = copy(storedRow);
                } else {
                    /* ChartTab: a new row is drawn with the chart type, colour and unit of the attribute */
                    row = new RowDefinition();
                    row.setObjectId(objectId);
                    row.setAttribute(attribute);
                    row.setChartType(AnalysisChartType.DEFAULT);
                    row.setColor(WebColor.DEFAULT_ROW_COLOR);
                    Map<String, String> own = units(objectId, attribute).get(0);
                    row.setUnitPrefix(own.get("prefix"));
                    row.setUnitFormula(own.get("formula"));
                    row.setUnitLabel(own.get("label"));
                }
                row(row, storedRow, node, prefix);
                rows.add(row);
            }
        }

        private void row(RowDefinition row, RowDefinition storedRow, JsonNode node, String prefix) throws Exception {
            boolean unitSent = false;
            boolean calculationSent = false;
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode value = field.getValue();
                String key = prefix + field.getKey();
                switch (field.getKey()) {
                    case "objectId":
                    case "attribute":
                        break;
                    case "name":
                        String name = text(value, key, "");
                        name = name != null ? name : "";
                        /* GET sends the object name for rows without a name (W2 display name): keep them without */
                        if (storedRow != null && (storedRow.getName() == null || storedRow.getName().isEmpty())
                                && name.equals(objectName(store, row.getObjectId()))) {
                            name = "";
                        }
                        row.setName(name);
                        break;
                    case "color":
                        row.setColor(color(value, key, row.getColor()));
                        break;
                    case "axis":
                        row.setAxis(integer(value, key, 0, 1, row.getAxis()));
                        break;
                    case "chartType":
                        row.setChartType(chartType(value, key, row.getChartType()));
                        break;
                    case "unitPrefix":
                        row.setUnitPrefix(text(value, key, row.getUnitPrefix()));
                        unitSent = true;
                        break;
                    case "unitFormula":
                        row.setUnitFormula(text(value, key, row.getUnitFormula()));
                        unitSent = true;
                        break;
                    case "unitLabel":
                        row.setUnitLabel(text(value, key, row.getUnitLabel()));
                        unitSent = true;
                        break;
                    case "decimalDigits":
                        row.setDecimalDigits(integer(value, key, -1, 20, row.getDecimalDigits()));
                        break;
                    case "calculation":
                        row.setCalculation(bool(value, key, row.isCalculation()));
                        calculationSent = true;
                        break;
                    case "bubbleType":
                        row.setBubbleType(oneOf(value, key, BUBBLE_TYPES, row.getBubbleType()));
                        break;
                    case "interval":
                        interval(row, value, key);
                        break;
                    case "css":
                        String css = text(value, key, "");
                        row.setCss(css != null ? css : "");
                        break;
                    default:
                        if (!READ_ONLY_ROW.contains(field.getKey())) messages.add("Unknown key: " + key);
                }
            }
            if (unitSent && !allowedUnit(row, storedRow)) {
                messages.add("Invalid unit of row " + row.getObjectId() + ": " + row.getUnitLabel());
            }
            if (!row.isCalculation()) {
                row.setCalculationId(-1);
            } else if (calculationSent || row.getCalculationId() == -1) {
                long calculation = engine.calculationId(row.getObjectId());
                if (calculation == -1 && storedRow != null && storedRow.isCalculation() && storedRow.getCalculationId() != -1) {
                    calculation = storedRow.getCalculationId();
                }
                if (calculation == -1) {
                    messages.add("Row " + row.getObjectId() + " is not calculated (no calculation)");
                } else {
                    row.setCalculationId(calculation);
                }
            }
        }

        private void interval(RowDefinition row, JsonNode value, String key) {
            if (value == null || value.isNull()) {
                row.setIntervalEnabled(false);
                return;
            }
            if (!value.isObject()) {
                messages.add("Invalid " + key);
                return;
            }
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldKey = key + "." + field.getKey();
                switch (field.getKey()) {
                    case "enabled":
                        row.setIntervalEnabled(bool(field.getValue(), fieldKey, row.isIntervalEnabled()));
                        break;
                    case "start":
                        row.setIntervalStart(dateTime(field.getValue(), fieldKey, row.getIntervalStart()));
                        break;
                    case "end":
                        row.setIntervalEnd(dateTime(field.getValue(), fieldKey, row.getIntervalEnd()));
                        break;
                    default:
                        messages.add("Unknown key: " + fieldKey);
                }
            }
        }

        private boolean allowedUnit(RowDefinition row, RowDefinition storedRow) {
            if (storedRow != null && Objects.equals(storedRow.getUnitPrefix(), row.getUnitPrefix())
                    && Objects.equals(storedRow.getUnitFormula(), row.getUnitFormula())
                    && Objects.equals(storedRow.getUnitLabel(), row.getUnitLabel())) {
                return true;
            }
            for (Map<String, String> unit : units(row.getObjectId(), row.getAttribute())) {
                if (Objects.equals(unit.get("prefix"), row.getUnitPrefix()) && Objects.equals(unit.get("formula"), row.getUnitFormula())
                        && Objects.equals(unit.get("label"), row.getUnitLabel())) {
                    return true;
                }
            }
            return false;
        }

        private List<Map<String, String>> units(long id, String attribute) {
            return units.computeIfAbsent(id + "/" + attribute, k -> unitChoices(engine.displayUnit(id, attribute)));
        }

        private ChartDefinition storedChart(int id) {
            if (stored == null) return null;
            for (ChartDefinition chart : stored.getCharts()) {
                if (chart.getId() == id) return chart;
            }
            return null;
        }

        /**
         * @return the stored row of the object and attribute, in the same chart first
         */
        private RowDefinition storedRow(int chartId, long objectId, String attribute) {
            if (stored == null) return null;
            RowDefinition other = null;
            for (ChartDefinition chart : stored.getCharts()) {
                for (RowDefinition row : chart.getRows()) {
                    if (row.getObjectId() == objectId && attribute.equals(row.getAttribute())) {
                        if (chart.getId() == chartId) return row;
                        if (other == null) other = row;
                    }
                }
            }
            return other;
        }

        // ------------------------------------------------------------ values

        private String text(JsonNode value, String key, String fallback) {
            if (value == null || value.isNull()) return null;
            if (!value.isTextual()) {
                messages.add("Invalid " + key);
                return fallback;
            }
            if (value.asText().length() > MAX_TEXT_LENGTH) {
                messages.add(key + " longer than " + MAX_TEXT_LENGTH + " characters");
                return fallback;
            }
            return value.asText();
        }

        private boolean bool(JsonNode value, String key, boolean fallback) {
            if (value == null || !value.isBoolean()) {
                messages.add("Invalid " + key);
                return fallback;
            }
            return value.asBoolean();
        }

        private int integer(JsonNode value, String key, int min, int max, int fallback) {
            if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
                    || value.asInt() < min || value.asInt() > max) {
                messages.add("Invalid " + key + " (" + min + "–" + max + ")");
                return fallback;
            }
            return value.asInt();
        }

        private double number(JsonNode value, String key, double fallback) {
            if (value == null || !value.isNumber() || Double.isNaN(value.asDouble()) || Double.isInfinite(value.asDouble())) {
                messages.add("Invalid " + key);
                return fallback;
            }
            return value.asDouble();
        }

        private String oneOf(JsonNode value, String key, List<String> allowed, String fallback) {
            if (value == null || !value.isTextual() || !allowed.contains(value.asText())) {
                messages.add("Invalid " + key + ": " + (value != null ? value.asText() : null));
                return fallback;
            }
            return value.asText();
        }

        private AnalysisChartType chartType(JsonNode value, String key, AnalysisChartType fallback) {
            if (value != null && value.isTextual()) {
                for (AnalysisChartType type : AnalysisChartType.values()) {
                    if (type.name().equals(value.asText())) return type;
                }
            }
            messages.add("Invalid " + key + ": " + (value != null ? value.asText() : null));
            return fallback;
        }

        private String color(JsonNode value, String key, String fallback) {
            if (value == null || !value.isTextual() || !COLOR.matcher(value.asText().trim()).matches()) {
                messages.add("Invalid " + key + " (#rrggbb or #rrggbbaa)");
                return fallback;
            }
            return value.asText().trim().toLowerCase(Locale.ROOT);
        }

        /**
         * @return {@code HH:mm:ss} (also {@code HH:mm}), null for none
         */
        private String time(JsonNode value, String key, String fallback) {
            if (value == null || value.isNull() || (value.isTextual() && value.asText().trim().isEmpty())) return null;
            String text = value.isTextual() ? value.asText().trim() : null;
            if (text != null && TIME.matcher(text).matches()) {
                try {
                    LocalTime.parse(text);
                    return text;
                } catch (IllegalArgumentException e) {
                    /* invalid */
                }
            }
            messages.add("Invalid " + key + " (HH:mm:ss)");
            return fallback;
        }

        /**
         * @return the time stamp as Joda's {@code DateTime.toString()} (like {@code ChartData}), the stored text if it
         * is the same instant; null for none
         */
        private String dateTime(JsonNode value, String key, String current) {
            if (value == null || value.isNull()) return null;
            if (value.isTextual()) {
                String text = value.asText().trim();
                try {
                    DateTime time = DateTime.parse(text);
                    if (current != null) {
                        try {
                            if (DateTime.parse(current).getMillis() == time.getMillis()) return current;
                        } catch (IllegalArgumentException ignored) {
                            /* stored text is no time stamp */
                        }
                    }
                    return time.toString();
                } catch (IllegalArgumentException e) {
                    /* invalid */
                }
            }
            messages.add("Invalid " + key);
            return current;
        }
    }

    /**
     * The units offered for a row like the Control Center's unit box ({@code ChartUnitBox}): first the row's own unit
     * (the display unit of the attribute), then the units of its quantity.
     *
     * @return {@code prefix, formula, label} as stored in the analysis
     */
    static List<Map<String, String>> unitChoices(JsonUnit displayUnit) {
        List<Map<String, String>> result = new ArrayList<>();
        JEVisUnit own = null;
        try {
            if (displayUnit != null) own = new JEVisUnitImp(displayUnit);
        } catch (Exception e) {
            logger.debug("Could not read unit {}", displayUnit, e);
        }
        if (own != null) {
            addUnit(result, own);
        } else {
            RowDefinition defaults = new RowDefinition();
            Map<String, String> unit = new LinkedHashMap<>();
            unit.put("prefix", defaults.getUnitPrefix());
            unit.put("formula", defaults.getUnitFormula());
            unit.put("label", defaults.getUnitLabel());
            result.add(unit);
            return result;
        }
        try {
            QuantityUnits qu = new QuantityUnits();
            List<JEVisUnit> choices = new ArrayList<>();
            if (qu.isTimeUnit(own)) choices.addAll(qu.getTimeUnits());
            if (qu.isEnergyUnit(own)) choices.addAll(qu.getEnergyUnits());
            if (qu.isVolumeUnit(own)) choices.addAll(qu.getVolumeUnits());
            if (qu.isMassUnit(own)) choices.addAll(qu.getMassUnits());
            if (qu.isPressureUnit(own)) choices.addAll(qu.getPressureUnits());
            if (qu.isVolumeFlowUnit(own)) choices.addAll(qu.getVolumeFlowUnits());
            if (qu.isMassFlowUnit(own)) choices.addAll(qu.getMassFlowUnits());
            /* as ChartUnitBox (which offers the volume units for money units) */
            if (qu.isMoneyUnit(own)) choices.addAll(qu.getVolumeUnits());
            for (JEVisUnit unit : choices) addUnit(result, unit);
        } catch (Exception e) {
            logger.warn("Could not list the units for {}", own.getLabel(), e);
        }
        return result;
    }

    private static void addUnit(List<Map<String, String>> result, JEVisUnit unit) {
        Map<String, String> json = new LinkedHashMap<>();
        json.put("prefix", String.valueOf(unit.getPrefix()));
        json.put("formula", unit.getFormula());
        json.put("label", unit.getLabel());
        if (!result.contains(json)) result.add(json);
    }

    static LimitLineDefinition copy(LimitLineDefinition source) {
        LimitLineDefinition line = new LimitLineDefinition();
        line.setValue(source.getValue());
        line.setLabel(source.getLabel());
        line.setColor(source.getColor());
        line.setAxis(source.getAxis());
        line.setFill(source.getFill());
        return line;
    }

    static ChartDefinition copy(ChartDefinition source) {
        ChartDefinition chart = new ChartDefinition();
        chart.setId(source.getId());
        chart.setName(source.getName());
        chart.setType(source.getType());
        chart.setMinFractionDigits(source.getMinFractionDigits());
        chart.setMaxFractionDigits(source.getMaxFractionDigits());
        chart.setFractionDigitsAuto(source.getFractionDigitsAuto());
        chart.setHeight(source.getHeight());
        chart.setOrientation(source.getOrientation());
        chart.setFixYAxisToZero(source.isFixYAxisToZero());
        chart.setXAxisTitle(source.getXAxisTitle());
        chart.setYAxisTitle(source.getYAxisTitle());
        chart.setGroupingInterval(source.getGroupingInterval());
        chart.setColorMapping(source.getColorMapping());
        chart.setFilterEnabled(source.isFilterEnabled());
        chart.setColoringEnabled(source.isColoringEnabled());
        chart.setShowColumnSums(source.isShowColumnSums());
        chart.setShowRowSums(source.isShowRowSums());
        chart.setDayStart(source.getDayStart());
        chart.setDayEnd(source.getDayEnd());
        chart.setFilterTexts(new LinkedHashMap<>(source.getFilterTexts()));
        List<RowDefinition> rows = new ArrayList<>();
        for (RowDefinition row : source.getRows()) rows.add(copy(row));
        chart.setRows(rows);
        return chart;
    }

    static RowDefinition copy(RowDefinition source) {
        RowDefinition row = new RowDefinition();
        row.setObjectId(source.getObjectId());
        row.setAttribute(source.getAttribute());
        row.setName(source.getName());
        row.setColor(source.getColor());
        row.setAxis(source.getAxis());
        row.setChartType(source.getChartType());
        row.setUnitPrefix(source.getUnitPrefix());
        row.setUnitFormula(source.getUnitFormula());
        row.setUnitLabel(source.getUnitLabel());
        row.setDecimalDigits(source.getDecimalDigits());
        row.setCalculation(source.isCalculation());
        row.setCalculationId(source.getCalculationId());
        row.setBubbleType(source.getBubbleType());
        row.setIntervalEnabled(source.isIntervalEnabled());
        row.setIntervalStart(source.getIntervalStart());
        row.setIntervalEnd(source.getIntervalEnd());
        row.setCss(source.getCss());
        row.setAggregation(source.getAggregation());
        row.setManipulation(source.getManipulation());
        return row;
    }

    // ---------------------------------------------------------------- helpers of the editor

    /**
     * {@code GET /web/analyses/directories}: the readable "Analyses Directory" objects
     */
    static WebEdit.Reply directories(WebEdit.Store store, String language) throws Exception {
        return directories(store, language, CLASS_DIRECTORY, CLASS_ANALYSIS);
    }

    /**
     * @return the readable directories of the class outside the recycle bin with path and the create right for the
     * item class, sorted by path
     */
    static WebEdit.Reply directories(WebEdit.Store store, String language, String directoryClass, String itemClass)
            throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        WebEdit.Rights rights = store.rights();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonObject directory : lookup.readableOfClass(directoryClass)) {
            if (ResourceWebIso.isDeleted(directory)) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", directory.getId());
            json.put("name", localName(directory, language));
            json.put("path", path(lookup, directory, language));
            json.put("canCreate", rights.canCreate(directory, itemClass));
            result.add(json);
        }
        AlphanumComparator ac = new AlphanumComparator();
        result.sort((a, b) -> ac.compare(String.valueOf(a.get("path")), String.valueOf(b.get("path"))));
        return WebEdit.Reply.ok(result);
    }

    /**
     * @return the local names of the readable objects above the object and of the object, joined by {@code " / "}
     */
    static String path(ResourceWebModules.Lookup lookup, JsonObject object, String language) {
        LinkedList<String> names = new LinkedList<>();
        names.addFirst(localName(object, language));
        Set<Long> visited = new HashSet<>();
        visited.add(object.getId());
        Long parent = lookup.parent(object.getId());
        while (parent != null && visited.add(parent)) {
            JsonObject p = lookup.readable(parent);
            if (p != null && !ResourceWebIso.isDeleted(p)) names.addFirst(localName(p, language));
            parent = lookup.parent(parent);
        }
        return String.join(" / ", names);
    }

    /**
     * {@code GET /web/rows/tree?parent=…&q=…}: the row picker
     */
    static WebEdit.Reply tree(WebEdit.Store store, String parentParam, String q, String language) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        Long parent = null;
        if (parentParam != null && !parentParam.trim().isEmpty()) {
            try {
                parent = Long.parseLong(parentParam.trim());
            } catch (NumberFormatException e) {
                return WebEdit.Reply.badRequest("Invalid parent");
            }
            if (WebEdit.readable(store, parent) == null) return WebEdit.Reply.notFound("Object not found");
        }
        List<JsonObject> objects = new ArrayList<>();
        boolean search = q != null && !q.trim().isEmpty();
        if (search) {
            String text = q.trim().toLowerCase(Locale.ROOT);
            if (text.length() < MIN_SEARCH_LENGTH) {
                return WebEdit.Reply.badRequest("q needs at least " + MIN_SEARCH_LENGTH + " characters");
            }
            Collection<JsonObject> candidates = parent != null ? descendants(lookup, parent) : lookup.readable();
            for (JsonObject object : candidates) {
                if (ResourceWebIso.isDeleted(object)) continue;
                String local = localName(object, language);
                if ((local != null && local.toLowerCase(Locale.ROOT).contains(text))
                        || (object.getName() != null && object.getName().toLowerCase(Locale.ROOT).contains(text))) {
                    objects.add(object);
                }
            }
        } else if (parent != null) {
            for (JsonObject child : lookup.readableChildren(parent)) {
                if (!ResourceWebIso.isDeleted(child)) objects.add(child);
            }
        } else {
            for (JsonObject site : lookup.readable()) {
                if (isSite(site) && !ResourceWebIso.isDeleted(site) && !belowReadableSite(lookup, site)) objects.add(site);
            }
        }
        AlphanumComparator ac = new AlphanumComparator();
        objects.sort((a, b) -> ac.compare(localName(a, language), localName(b, language)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonObject object : objects) {
            if (result.size() >= MAX_SEARCH_HITS && search) break;
            Map<String, Object> json = treeNode(store, lookup, object, language);
            if (search) {
                Long objectParent = lookup.parent(object.getId());
                JsonObject p = objectParent != null ? lookup.readable(objectParent) : null;
                json.put("path", p != null ? path(lookup, p, language) : "");
            }
            result.add(json);
        }
        return WebEdit.Reply.ok(result);
    }

    private static Map<String, Object> treeNode(WebEdit.Store store, ResourceWebModules.Lookup lookup, JsonObject object,
                                                String language) {
        boolean selectable = ROW_CLASSES.contains(object.getJevisClass());
        boolean hasChildren = false;
        for (JsonObject child : lookup.readableChildren(object.getId())) {
            if (!ResourceWebIso.isDeleted(child)) {
                hasChildren = true;
                break;
            }
        }
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", localName(object, language));
        json.put("class", object.getJevisClass());
        json.put("hasChildren", hasChildren);
        json.put("selectable", selectable);
        String unit = null;
        List<String> attributes = new ArrayList<>();
        if (selectable) {
            try {
                unit = store.unit(object.getId(), ATTRIBUTE_VALUE);
            } catch (Exception e) {
                logger.debug("Could not read the unit of {}", object.getId(), e);
            }
            attributes.add(ATTRIBUTE_VALUE);
        }
        json.put("unit", unit);
        json.put("attributes", attributes);
        return json;
    }

    private static boolean isSite(JsonObject object) {
        return ResourceWebNavigation.CLASS_ORGANIZATION.equals(object.getJevisClass())
                || ResourceWebNavigation.CLASS_BUILDING.equals(object.getJevisClass());
    }

    /**
     * @return whether a readable organisation or building is above the object
     */
    private static boolean belowReadableSite(ResourceWebModules.Lookup lookup, JsonObject object) {
        Set<Long> visited = new HashSet<>();
        Long parent = lookup.parent(object.getId());
        while (parent != null && visited.add(parent)) {
            JsonObject p = lookup.readable(parent);
            if (p != null && !ResourceWebIso.isDeleted(p) && isSite(p)) return true;
            parent = lookup.parent(parent);
        }
        return false;
    }

    private static List<JsonObject> descendants(ResourceWebModules.Lookup lookup, long parent) {
        List<JsonObject> result = new ArrayList<>();
        Deque<Long> open = new ArrayDeque<>(Collections.singletonList(parent));
        Set<Long> visited = new HashSet<>();
        while (!open.isEmpty()) {
            long id = open.poll();
            if (!visited.add(id)) continue;
            for (JsonObject child : lookup.readableChildren(id)) {
                result.add(child);
                open.add(child.getId());
            }
        }
        return result;
    }

    /**
     * {@code GET /web/rows/{id}/units?attribute=Value}
     */
    static WebEdit.Reply units(WebEdit.Store store, Engine engine, long id, String attribute) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        if (object == null) return WebEdit.Reply.notFound("Row not found");
        String name = attribute == null || attribute.trim().isEmpty() ? ATTRIBUTE_VALUE : attribute.trim();
        return WebEdit.Reply.ok(unitChoices(engine.displayUnit(id, name)));
    }

    /**
     * {@code POST /web/analyses/preview/data} with {@code {definition, id?, directoryId?, …W2 data request…}}: the W2
     * data answer for an unsaved definition
     */
    static WebEdit.Reply preview(WebEdit.Store store, Engine engine, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        EvaluationSettings settings;
        try {
            settings = ResourceWebAnalysis.parseSettings(body);
        } catch (IllegalArgumentException e) {
            return WebEdit.Reply.badRequest(e.getMessage());
        }
        Long analysisId = longValue(json, "id");
        Long directoryId = longValue(json, "directoryId");
        JsonObject analysis = analysisId != null ? analysisObject(store, analysisId) : null;
        AnalysisDefinition stored = analysis != null ? stored(store, engine, analysis).definition : null;
        List<String> messages = new ArrayList<>();
        AnalysisDefinition definition = new Parser(store, engine, stored, true, messages).parse(json.get("definition"));
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        AnalysisDefinition evaluated = new AnalysisReader().readAnalysisFile(new AnalysisWriter().toJson(definition, now));
        if (analysis != null) evaluated.setId(analysis.getId());
        Long context = analysis != null ? Long.valueOf(analysis.getId())
                : directoryId != null && directory(store, directoryId) != null ? directoryId : null;
        return WebEdit.Reply.ok(engine.data(evaluated, context, settings));
    }
}
