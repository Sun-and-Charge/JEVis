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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.chart.TimeRange;
import org.jevis.commons.dashboard.DashboardTimeFrames;
import org.jevis.commons.trc.*;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Cost and result calculation for the web app, view only (see docs/WEB-API.md, W4.8): the accounting configurations
 * and result calculation templates, the definition of one (time frames, inputs) and its result for a time frame,
 * calculated by the shared {@link ResultCalculator} on a {@link LocalDataSource} with the rights of the user.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebCosts {

    static final String CLASS_TEMPLATE = "Result Calculation Template";
    static final String CLASS_CONFIGURATION = ResourceWebContracts.CLASS_CONFIGURATION;
    static final String CLASS_CLEAN_DATA = "Clean Data";
    static final String KIND_CONFIGURATION = "configuration";
    static final String KIND_TEMPLATE = "template";
    static final int MAX_OPTIONS = 500;
    static final int MAX_CUSTOM_YEARS = 10;

    private static final Logger logger = LogManager.getLogger(ResourceWebCosts.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Error answer with a plain text message.
     */
    static class WebException extends Exception {
        final int status;

        WebException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    /**
     * The calculation (production: {@link ResultCalculator} on a {@link LocalDataSource}).
     */
    interface Calculator {
        TrcResult calculate(RCTemplate template, DateTime from, DateTime to, DateTimeZone zone);
    }

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("costs")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return json(listJson(lookup, ResourceWebRegisters.Classes.fromCache(), ResourceWebContracts.sqlStore(ds)));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the cost calculations", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("costs/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response definition(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return json(definitionJson(lookup, ResourceWebRegisters.Classes.fromCache(), ResourceWebContracts.sqlStore(ds),
                    id, ResourceWebRegisters.language(lang)));
        } catch (WebException ex) {
            return error(ex);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the cost calculation {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @POST
    @Path("costs/{id}/result")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response result(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(sql);
            LocalDataSource local = new LocalDataSource(sql);
            Calculator calculator = (template, from, to, zone) ->
                    new ResultCalculator(local, new TrcTimeFrames(zone, null)).calculate(template, from, to);
            return json(resultJson(lookup, ResourceWebRegisters.Classes.fromCache(), ResourceWebContracts.sqlStore(sql),
                    id, body, language(httpHeaders), DateTime.now(), calculator));
        } catch (WebException ex) {
            return error(ex);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not calculate the cost calculation {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    // ---------------------------------------------------------------- list

    /**
     * {@code GET /costs}: the readable configurations (with their template) and templates.
     */
    static Map<String, Object> listJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                        ResourceWebContracts.Store store) {
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Map<String, Object>> configurations = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_CONFIGURATION))) {
            Map<String, Object> json = header(object, lookup, buildingClasses);
            SelectionTemplate selection = selection(object.getId(), store);
            Long templateId = selection != null ? selection.getTemplateSelection() : null;
            JsonObject template = templateObject(lookup, classes, templateId);
            Map<String, Object> templateJson = new LinkedHashMap<>();
            templateJson.put("id", templateId);
            templateJson.put("name", template != null ? template.getName() : null);
            templateJson.put("readable", template != null);
            json.put("template", templateJson);
            configurations.add(json);
        }
        configurations.sort(ResourceWebReports.order());
        List<Map<String, Object>> templates = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_TEMPLATE))) {
            templates.add(header(object, lookup, buildingClasses));
        }
        templates.sort(ResourceWebReports.order());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configurations", configurations);
        result.put("templates", templates);
        return result;
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
     * @return the template if the user may read it and it is not in the recycle bin, else null
     */
    static JsonObject templateObject(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, Long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        return classes.withHeirs(CLASS_TEMPLATE).contains(object.getJevisClass()) ? object : null;
    }

    private static byte[] templateFile(long id, ResourceWebContracts.Store store) {
        try {
            JsonAttribute attribute = ResourceWebContracts.attributes(id, store).get(TemplateHandler.TEMPLATE_FILE);
            JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
            return sample != null ? store.file(id, TemplateHandler.TEMPLATE_FILE, sample) : null;
        } catch (Exception e) {
            logger.debug("Could not read the template file of {}", id, e);
            return null;
        }
    }

    static SelectionTemplate selection(long id, ResourceWebContracts.Store store) {
        return AccountingTemplateHandler.parse(templateFile(id, store));
    }

    static RCTemplate template(long id, ResourceWebContracts.Store store) {
        return TemplateHandler.parse(templateFile(id, store));
    }

    // ---------------------------------------------------------------- definition

    /**
     * A configuration or template with its parsed template, inputs bound for configurations.
     */
    static final class Subject {
        JsonObject object;
        String kind;
        JsonObject templateObject;
        RCTemplate template;
        SelectionTemplate selection;
        List<Selector> selectors = new ArrayList<>();
    }

    /**
     * One object selector of the Control Center's input pane: a single input or the grouped inputs of one class.
     */
    static final class Selector {
        final List<TemplateInput> inputs = new ArrayList<>();
        String label;
        List<JsonObject> options;
        /**
         * the configured object (configurations), null if unbound
         */
        Long bound;

        TemplateInput first() {
            return inputs.get(0);
        }
    }

    /**
     * @throws WebException 404 if {@code id} is no readable configuration/template, 409 if the template of a
     *                      configuration is missing, not readable or broken
     */
    static Subject subject(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                           ResourceWebContracts.Store store, long id) throws WebException {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) throw new WebException(404, "Not found");
        Subject subject = new Subject();
        subject.object = object;
        if (classes.withHeirs(CLASS_CONFIGURATION).contains(object.getJevisClass())) {
            subject.kind = KIND_CONFIGURATION;
            subject.selection = selection(id, store);
            Long templateId = subject.selection != null ? subject.selection.getTemplateSelection() : null;
            subject.templateObject = templateObject(lookup, classes, templateId);
            if (subject.templateObject == null) throw new WebException(409, "The template is missing or not readable");
            subject.template = template(subject.templateObject.getId(), store);
            if (subject.template == null) throw new WebException(409, "The template cannot be read");
            ResultCalculator.link(subject.template, subject.selection, null);
        } else if (classes.withHeirs(CLASS_TEMPLATE).contains(object.getJevisClass())) {
            subject.kind = KIND_TEMPLATE;
            subject.templateObject = object;
            subject.template = template(id, store);
            if (subject.template == null) subject.template = new RCTemplate();
        } else {
            throw new WebException(404, "Not found");
        }
        subject.selectors = selectors(subject, lookup, classes);
        return subject;
    }

    /**
     * {@code OutputView.updateViewInputFlowPane}: the inputs with {@code group == false} each with their own selector
     * (sorted by variable name), then one selector per class for the other inputs (sorted by class name).
     */
    static List<Selector> selectors(Subject subject, ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes) {
        AlphanumComparator alphanum = new AlphanumComparator();
        List<Selector> ungrouped = new ArrayList<>();
        Map<String, Selector> grouped = new TreeMap<>(Comparator.nullsFirst(alphanum::compare));
        for (TemplateInput input : subject.template.getTemplateInputs()) {
            if (input.getVariableType() == null) continue;
            if (input.getGroup() == null || input.getGroup()) {
                grouped.computeIfAbsent(input.getObjectClass(), k -> new Selector()).inputs.add(input);
            } else if (!InputVariableType.FORMULA.toString().equals(input.getVariableType())) {
                Selector selector = new Selector();
                selector.inputs.add(input);
                ungrouped.add(selector);
            }
        }
        ungrouped.sort((a, b) -> alphanum.compare(String.valueOf(a.first().getVariableName()), String.valueOf(b.first().getVariableName())));
        List<Selector> result = new ArrayList<>(ungrouped);
        result.addAll(grouped.values());
        for (Selector selector : result) {
            List<String> names = new ArrayList<>();
            for (TemplateInput input : selector.inputs) names.add(input.getVariableName());
            selector.label = String.join(", ", names);
            selector.options = options(lookup, selector.first().getObjectClass(), selector.first().getFilter());
            if (KIND_CONFIGURATION.equals(subject.kind)) {
                for (TemplateInput input : selector.inputs) {
                    Long id = input.getObjectID();
                    if (id != null && id > 0) {
                        selector.bound = id;
                        break;
                    }
                }
            }
        }
        return result;
    }

    /**
     * {@code OutputView.createObjectSelector}: readable objects of the class (not its subclasses, not in the recycle
     * bin) whose name (for clean data "data row \ clean data") contains all words of the filter; sorted by that name,
     * at most {@link #MAX_OPTIONS}.
     */
    static List<JsonObject> options(ResourceWebModules.Lookup lookup, String className, String filter) {
        List<JsonObject> result = new ArrayList<>();
        if (className == null) return result;
        List<String> words = new ArrayList<>();
        if (filter != null) {
            if (filter.contains(" ")) {
                words.addAll(Arrays.asList(filter.toLowerCase(Locale.ROOT).split(" ")));
            } else {
                words.add(filter.toLowerCase(Locale.ROOT));
            }
        }
        for (JsonObject object : lookup.readableOfClass(className)) {
            if (ResourceWebIso.isDeleted(object)) continue;
            String name = realName(object, lookup).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String word : words) {
                if (!name.contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) result.add(object);
        }
        AlphanumComparator alphanum = new AlphanumComparator();
        result.sort((a, b) -> alphanum.compare(realName(a, lookup), realName(b, lookup)));
        return result.size() > MAX_OPTIONS ? new ArrayList<>(result.subList(0, MAX_OPTIONS)) : result;
    }

    /**
     * {@code TRCPlugin.getRealName}: clean data with the name of its data row ({@code "Data row \ Clean data"}, if the
     * data row is readable).
     */
    static String realName(JsonObject object, ResourceWebModules.Lookup lookup) {
        String name = object.getName() != null ? object.getName() : "";
        if (!CLASS_CLEAN_DATA.equals(object.getJevisClass())) return name;
        Set<Long> visited = new HashSet<>();
        Long current = lookup.parent(object.getId());
        while (current != null && visited.add(current)) {
            JsonObject parent = lookup.object(current);
            if (parent == null) break;
            if ("Data".equals(parent.getJevisClass()) || "Base Data".equals(parent.getJevisClass())) {
                return lookup.readable(current) != null ? parent.getName() + " \\ " + name : name;
            }
            current = lookup.parent(current);
        }
        return name;
    }

    private static Map<String, Object> objectRef(Long id, ResourceWebModules.Lookup lookup) {
        if (id == null) return null;
        JsonObject object = lookup.readable(id);
        if (object != null && ResourceWebIso.isDeleted(object)) object = null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("name", object != null ? realName(object, lookup) : null);
        json.put("readable", object != null);
        return json;
    }

    /**
     * @return the object a selector uses without selection: the configured one, else the first option (as the Control
     * Center selects the first object); null if there is none
     */
    static Long defaultObject(Selector selector) {
        if (selector.bound != null) return selector.bound;
        return selector.options.isEmpty() ? null : selector.options.get(0).getId();
    }

    /**
     * {@code GET /costs/{id}}.
     */
    static Map<String, Object> definitionJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                              ResourceWebContracts.Store store, long id, String language) throws WebException {
        Subject subject = subject(lookup, classes, store, id);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", subject.object.getId());
        json.put("kind", subject.kind);
        json.put("name", subject.object.getName());
        json.put("templateName", subject.templateObject.getName());
        List<Map<String, Object>> timeFrames = new ArrayList<>();
        List<String> enabled = TrcTimeFrames.enabled(subject.template);
        for (String timeFrame : enabled) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", timeFrame);
            entry.put("name", timeFrameName(timeFrame, language));
            timeFrames.add(entry);
        }
        json.put("timeFrames", timeFrames);
        json.put("defaultTimeFrame", enabled.isEmpty() ? null : enabled.get(0));
        json.put("contract", KIND_CONFIGURATION.equals(subject.kind) ? contractJson(subject.object, lookup, classes, store) : null);
        List<Map<String, Object>> inputs = new ArrayList<>();
        boolean selectable = KIND_TEMPLATE.equals(subject.kind);
        for (Selector selector : subject.selectors) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", selector.first().getId());
            entry.put("variableName", selector.first().getVariableName());
            entry.put("label", selector.label);
            entry.put("objectClass", selector.first().getObjectClass());
            entry.put("selectable", selectable);
            entry.put("selected", objectRef(defaultObject(selector), lookup));
            List<Map<String, Object>> options = new ArrayList<>();
            if (selectable) {
                for (JsonObject option : selector.options) {
                    Map<String, Object> o = new LinkedHashMap<>();
                    o.put("id", option.getId());
                    o.put("name", realName(option, lookup));
                    options.add(o);
                }
            }
            entry.put("options", options);
            inputs.add(entry);
        }
        json.put("inputs", inputs);
        Map<String, Object> grid = new LinkedHashMap<>();
        int rows = 0;
        int columns = 0;
        for (TemplateOutput output : subject.template.getTemplateOutputs()) {
            rows = Math.max(rows, value(output.getRow(), 0) + value(output.getRowSpan(), 1));
            columns = Math.max(columns, value(output.getColumn(), 0) + value(output.getColSpan(), 1));
        }
        grid.put("rows", rows);
        grid.put("columns", columns);
        json.put("grid", grid);
        return json;
    }

    private static int value(Integer value, int fallback) {
        return value != null ? value : fallback;
    }

    /**
     * @return the contract fields of the configuration as {@code /contracts} (without id, name, path, site)
     */
    static Map<String, Object> contractJson(JsonObject object, ResourceWebModules.Lookup lookup,
                                            ResourceWebRegisters.Classes classes, ResourceWebContracts.Store store) {
        Map<String, Object> json = ResourceWebContracts.configurationJson(object, lookup, store,
                classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING));
        json.remove("id");
        json.remove("name");
        json.remove("path");
        json.remove("site");
        return json;
    }

    /**
     * @return the translated name of the time frame (as {@code TimeFrame.getListName()}), the id if unknown
     */
    static String timeFrameName(String id, String language) {
        List<String> parts = new ArrayList<>();
        for (String key : TrcTimeFrames.nameKeys(id)) {
            String text = ResourceWebNotes.NoteTags.translate(key, language);
            if (text == null) return id;
            parts.add(text);
        }
        return parts.isEmpty() ? id : String.join(" ", parts);
    }

    // ---------------------------------------------------------------- result

    /**
     * Request of {@code POST /costs/{id}/result}.
     */
    static final class Request {
        String timeFrame;
        DateTime from;
        DateTime to;
        /** reference date of the period ({@code null} = now) */
        DateTime date;
        DateTimeZone zone;
        Map<String, Long> selections = new LinkedHashMap<>();
    }

    static Request parseRequest(String body) throws WebException {
        JsonNode root;
        try {
            root = body == null || body.trim().isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (Exception ex) {
            throw new WebException(400, "Invalid JSON");
        }
        if (root == null || !root.isObject()) throw new WebException(400, "Invalid JSON");
        Request request = new Request();
        request.zone = DateTimeZone.getDefault();
        String zoneId = ResourceWebContracts.text(root, "timeZone");
        if (zoneId != null) {
            try {
                request.zone = DateTimeZone.forID(zoneId);
            } catch (IllegalArgumentException ex) {
                throw new WebException(400, "Unknown time zone");
            }
        }
        request.timeFrame = ResourceWebContracts.text(root, "timeFrame");
        if (request.timeFrame == null) throw new WebException(400, "Missing timeFrame");
        String date = ResourceWebContracts.text(root, "date");
        if (date != null && !DashboardTimeFrames.CUSTOM.equals(request.timeFrame)) {
            request.date = parseDate(date, request.zone, "date");
        }
        if (DashboardTimeFrames.CUSTOM.equals(request.timeFrame)) {
            request.from = parseDate(ResourceWebContracts.text(root, "from"), request.zone, "from");
            request.to = parseDate(ResourceWebContracts.text(root, "to"), request.zone, "to");
            if (request.to.isBefore(request.from)) throw new WebException(400, "to is before from");
            if (request.to.isAfter(request.from.plusYears(MAX_CUSTOM_YEARS))) {
                throw new WebException(400, "At most " + MAX_CUSTOM_YEARS + " years");
            }
        }
        JsonNode selections = root.get("selections");
        if (selections != null && !selections.isNull()) {
            if (!selections.isObject()) throw new WebException(400, "Invalid selections");
            Iterator<Map.Entry<String, JsonNode>> fields = selections.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue() == null || field.getValue().isNull()) continue;
                if (!field.getValue().canConvertToLong()) throw new WebException(400, "Invalid selection " + field.getKey());
                request.selections.put(field.getKey(), field.getValue().asLong());
            }
        }
        return request;
    }

    private static DateTime parseDate(String value, DateTimeZone zone, String name) throws WebException {
        if (value == null) throw new WebException(400, "CUSTOM needs " + name);
        try {
            return new DateTime(value.trim(), zone);
        } catch (IllegalArgumentException ex) {
            throw new WebException(400, "Invalid " + name);
        }
    }

    /**
     * Binds the objects to the inputs: configurations as the Control Center (inputs of a group without object get the
     * object of the group, unbound selectors the first option), templates by the selections (else the first option).
     *
     * @throws WebException 400 if a selection is for an unknown input or not one of the options
     */
    static void bind(Subject subject, Map<String, Long> selections) throws WebException {
        Map<String, Selector> byId = new HashMap<>();
        for (Selector selector : subject.selectors) byId.put(selector.first().getId(), selector);
        if (KIND_TEMPLATE.equals(subject.kind)) {
            for (Map.Entry<String, Long> selection : selections.entrySet()) {
                Selector selector = byId.get(selection.getKey());
                if (selector == null) throw new WebException(400, "Unknown input " + selection.getKey());
                boolean option = false;
                for (JsonObject object : selector.options) {
                    if (selection.getValue().equals(object.getId())) option = true;
                }
                if (!option) throw new WebException(400, "Object " + selection.getValue() + " is no option of " + selection.getKey());
            }
        }
        for (Selector selector : subject.selectors) {
            Long object;
            if (KIND_TEMPLATE.equals(subject.kind) && selections.containsKey(selector.first().getId())) {
                object = selections.get(selector.first().getId());
            } else {
                object = defaultObject(selector);
            }
            for (TemplateInput input : selector.inputs) {
                boolean unbound = input.getObjectID() == null || input.getObjectID() <= 0;
                if (KIND_TEMPLATE.equals(subject.kind) || unbound) {
                    input.setObjectID(object != null ? object : -1L);
                }
            }
        }
    }

    /**
     * {@code POST /costs/{id}/result}.
     *
     * @param now reference for the time frame
     */
    static Map<String, Object> resultJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                          ResourceWebContracts.Store store, long id, String body, String language,
                                          DateTime now, Calculator calculator) throws WebException {
        Request request = parseRequest(body);
        Subject subject = subject(lookup, classes, store, id);
        DateTime from = request.from;
        DateTime to = request.to;
        DateTime previous = null;
        DateTime next = null;
        if (from == null) {
            if (!TrcTimeFrames.enabled(subject.template).contains(request.timeFrame)) {
                throw new WebException(400, "Unknown timeFrame");
            }
            TrcTimeFrames frames = new TrcTimeFrames(request.zone, now);
            DateTime reference = request.date == null || request.date.isAfter(now) ? now : request.date;
            TimeRange range = frames.interval(request.timeFrame, reference);
            if (range == null) throw new WebException(400, "Unknown timeFrame");
            from = range.getStart();
            to = range.getEnd();
            previous = frames.previous(request.timeFrame, reference);
            next = frames.next(request.timeFrame, reference);
        }
        bind(subject, request.selections);
        TrcResult result = calculator.calculate(subject.template, from, to, request.zone);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("from", iso(from, request.zone));
        json.put("to", iso(to, request.zone));
        json.put("previous", previous == null ? null : iso(previous, request.zone));
        json.put("next", next == null ? null : iso(next, request.zone));
        int rows = 0;
        int columns = 0;
        List<Map<String, Object>> cells = new ArrayList<>();
        for (TrcResult.Cell cell : result.getCells()) {
            rows = Math.max(rows, cell.getRow() + cell.getRowSpan());
            columns = Math.max(columns, cell.getColumn() + cell.getColumnSpan());
            cells.add(cellJson(cell, lookup, request.zone));
        }
        json.put("rows", rows);
        json.put("columns", columns);
        json.put("cells", cells);
        List<String> warnings = new ArrayList<>();
        for (TrcResult.Warning warning : result.getWarnings()) {
            String text = warningText(warning, language);
            if (!warnings.contains(text)) warnings.add(text);
        }
        json.put("warnings", warnings);
        return json;
    }

    static Map<String, Object> cellJson(TrcResult.Cell cell, ResourceWebModules.Lookup lookup, DateTimeZone zone) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("row", cell.getRow());
        json.put("column", cell.getColumn());
        json.put("rowSpan", cell.getRowSpan());
        json.put("columnSpan", cell.getColumnSpan());
        json.put("kind", cell.getKind().name().toLowerCase(Locale.ROOT));
        if (cell.getKind() == TrcResult.Kind.SEPARATOR) {
            json.put("orientation", cell.getOrientation());
            return json;
        }
        json.put("name", cell.getName());
        json.put("nameBold", cell.isNameBold());
        json.put("value", cell.getKind() == TrcResult.Kind.VALUE ? cell.getValue() : null);
        json.put("text", cell.getKind() == TrcResult.Kind.TEXT ? cell.getText() : null);
        json.put("unit", cell.getUnit());
        json.put("resultBold", cell.isResultBold());
        json.put("tooltip", cell.getTooltip());
        Map<String, Object> analysis = null;
        Long objectId = cell.getAnalysisObjectId();
        if (objectId != null && lookup.readable(objectId) != null && !ResourceWebIso.isDeleted(lookup.readable(objectId))) {
            analysis = new LinkedHashMap<>();
            analysis.put("objectId", objectId);
            analysis.put("attribute", cell.getAnalysisAttribute());
            analysis.put("from", iso(cell.getAnalysisFrom(), zone));
            analysis.put("to", iso(cell.getAnalysisTo(), zone));
        }
        json.put("analysis", analysis);
        return json;
    }

    private static String iso(DateTime dateTime, DateTimeZone zone) {
        return dateTime != null ? ISODateTimeFormat.dateTime().print(dateTime.withZone(zone)) : null;
    }

    /**
     * @return {@code de} for a German first acceptable language, else {@code en}
     */
    static String language(HttpHeaders headers) {
        try {
            List<Locale> locales = headers.getAcceptableLanguages();
            if (locales != null && !locales.isEmpty() && "de".equals(locales.get(0).getLanguage())) return "de";
        } catch (Exception e) {
            logger.debug("No Accept-Language", e);
        }
        return "en";
    }

    /**
     * @return the warning in German ({@code de}) or English
     */
    static String warningText(TrcResult.Warning warning, String language) {
        boolean de = "de".equals(language);
        String formula = warning.getFormula() != null ? warning.getFormula() : "";
        String input = warning.getInput() != null ? warning.getInput() : "";
        switch (warning.getType()) {
            case FORMULA_FAILED:
                return de ? "Formel \"" + formula + "\" konnte nicht berechnet werden"
                        : "Formula \"" + formula + "\" could not be calculated";
            case FORMULA_CYCLE:
                return de ? "Formel \"" + formula + "\" hängt von sich selbst ab"
                        : "Formula \"" + formula + "\" depends on itself";
            case INPUT_NO_OBJECT:
                return de ? "Formel \"" + formula + "\": Eingabe \"" + input + "\" hat kein Objekt"
                        : "Formula \"" + formula + "\": input \"" + input + "\" has no object";
            case INPUT_NOT_READABLE:
                return de ? "Formel \"" + formula + "\": Eingabe \"" + input + "\" ist nicht lesbar"
                        : "Formula \"" + formula + "\": input \"" + input + "\" is not readable";
            case INPUT_NO_VALUES:
            default:
                return de ? "Formel \"" + formula + "\": Eingabe \"" + input + "\" hat keine Werte im Zeitraum"
                        : "Formula \"" + formula + "\": input \"" + input + "\" has no values in the time frame";
        }
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @param readable the objects the user may read
     * @return {@code costs}: number of readable accounting configurations plus result calculation templates (not in
     * the recycle bin)
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable, ResourceWebRegisters.Classes classes) {
        Set<String> costClasses = new HashSet<>(classes.withHeirs(CLASS_CONFIGURATION));
        costClasses.addAll(classes.withHeirs(CLASS_TEMPLATE));
        int costs = 0;
        for (JsonObject object : readable) {
            if (!ResourceWebIso.isDeleted(object) && costClasses.contains(object.getJevisClass())) costs++;
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("costs", costs);
        return modules;
    }

    // ---------------------------------------------------------------- helpers

    private static Response error(WebException ex) {
        return Response.status(ex.status).entity(ex.getMessage()).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }
}
