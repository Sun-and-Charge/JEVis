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
import org.jevis.api.JEVisObject;
import org.jevis.commons.chart.*;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Analyses for the web app (see docs/WEB-API.md): the configuration of an analysis and its data for a time frame.
 * <p>
 * The analysis is read and evaluated by the headless core {@code org.jevis.commons.chart} on a read-only
 * {@link LocalDataSource} with the rights of the logged-in user. Analyses the user may not read answer 404 (like
 * analyses that do not exist).
 */
@Path("/JEWebService/v1/web/analyses")
public class ResourceWebAnalysis {

    private static final Logger logger = LogManager.getLogger(ResourceWebAnalysis.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response config(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            JEVisObject analysis = findAnalysis(new LocalDataSource(sql), id);
            if (analysis == null) return notFound();
            AnalysisDefinition definition = new AnalysisReader().read(analysis);
            return json(configJson(analysis.getID(), analysis.getName(), analysis.getLocalNameList(), definition));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read analysis {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    @POST
    @Path("{id}/data")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response data(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        EvaluationSettings settings;
        try {
            settings = parseSettings(body);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }

        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            LocalDataSource ds = new LocalDataSource(sql);
            JEVisObject analysis = findAnalysis(ds, id);
            if (analysis == null) return notFound();
            AnalysisDefinition definition = new AnalysisReader().read(analysis);
            AnalysisResult result = new AnalysisEvaluator(ds).evaluate(analysis, definition, settings);
            return json(dataJson(result, notes(sql, definition, result)));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not evaluate analysis {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    private static JEVisObject findAnalysis(LocalDataSource ds, long id) throws Exception {
        JEVisObject object = ds.getObject(id);
        if (object == null || !ResourceWebNavigation.CLASS_ANALYSIS.equals(object.getJEVisClassName())) return null;
        return object;
    }

    private static Response notFound() {
        return Response.status(Response.Status.NOT_FOUND).entity("Analysis not found").type(MediaType.TEXT_PLAIN).build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    // ---------------------------------------------------------------- request

    /**
     * Parses the request of {@code POST /data}. Unknown time frames fall back to the default (TODAY), unknown
     * aggregations and manipulations to NONE, an unknown time zone to the time zone of the server.
     *
     * @throws IllegalArgumentException if the body is no JSON object or {@code CUSTOM} has no valid from/to
     */
    static EvaluationSettings parseSettings(String body) {
        JsonNode root;
        try {
            root = body == null || body.trim().isEmpty() ? MAPPER.createObjectNode() : MAPPER.readTree(body);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON");
        }
        if (root == null || !root.isObject()) throw new IllegalArgumentException("Invalid JSON");

        DateTimeZone zone = DateTimeZone.getDefault();
        String zoneId = text(root, "timeZone");
        if (zoneId != null) {
            try {
                zone = DateTimeZone.forID(zoneId);
            } catch (IllegalArgumentException ex) {
                logger.debug("Unknown time zone {}", zoneId);
            }
        }

        TimeFrame timeFrame = TimeFrame.parse(text(root, "timeFrame"));
        if (timeFrame == null) timeFrame = AnalysisDefinition.DEFAULT_TIME_FRAME;
        EvaluationSettings settings = new EvaluationSettings().setZone(zone).setTimeFrame(timeFrame)
                .setAggregation(enumValue(AggregationPeriod.class, text(root, "aggregation"), AnalysisDefinition.DEFAULT_AGGREGATION))
                .setManipulation(enumValue(ManipulationMode.class, text(root, "manipulation"), AnalysisDefinition.DEFAULT_MANIPULATION));

        if (timeFrame == TimeFrame.CUSTOM) {
            DateTime from = parseDate(text(root, "from"), zone);
            DateTime to = parseDate(text(root, "to"), zone);
            if (from == null || to == null || to.isBefore(from)) {
                throw new IllegalArgumentException("CUSTOM needs valid from and to");
            }
            settings.setFrom(from).setTo(to);
        }
        return settings;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name, E fallback) {
        if (name == null) return fallback;
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    private static DateTime parseDate(String value, DateTimeZone zone) {
        if (value == null) return null;
        try {
            return new DateTime(value, zone).withZone(zone);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------- answers

    static Map<String, Object> configJson(long id, String name, Map<String, String> localNames, AnalysisDefinition definition) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", name);
        Map<String, String> names = new TreeMap<>();
        if (localNames != null) {
            localNames.forEach((key, value) -> {
                if (key != null && value != null && !value.trim().isEmpty()) names.put(key, value);
            });
        }
        result.put("names", names);
        result.put("chartsPerScreen", definition.getChartsPerScreen());
        result.put("horizontalPies", definition.getHorizontalPies());
        result.put("horizontalTables", definition.getHorizontalTables());
        result.put("autoSize", definition.isAutoSize());

        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("timeFrame", AnalysisDefinition.DEFAULT_TIME_FRAME.name());
        defaults.put("aggregation", AnalysisDefinition.DEFAULT_AGGREGATION.name());
        defaults.put("manipulation", AnalysisDefinition.DEFAULT_MANIPULATION.name());
        result.put("defaults", defaults);

        List<Map<String, Object>> charts = new ArrayList<>();
        for (ChartDefinition chart : definition.getCharts()) {
            charts.add(chartConfigJson(chart));
        }
        result.put("charts", charts);
        return result;
    }

    /**
     * @return the configuration of one chart (also used for the Chart widgets of dashboards)
     */
    static Map<String, Object> chartConfigJson(ChartDefinition chart) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", chart.getId());
        c.put("name", chart.getName());
        c.put("type", chart.getType() != null ? chart.getType().name() : null);
        c.put("minFractionDigits", chart.getMinFractionDigits());
        c.put("maxFractionDigits", chart.getMaxFractionDigits());
        c.put("height", finite(chart.getHeight()));
        c.put("orientation", chart.getOrientation());
        c.put("fixYAxisToZero", chart.isFixYAxisToZero());
        c.put("xAxisTitle", chart.getXAxisTitle());
        c.put("yAxisTitle", chart.getYAxisTitle());
        c.put("groupingInterval", finite(chart.getGroupingInterval()));
        c.put("colorMapping", chart.getColorMapping());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RowDefinition row : chart.getRows()) {
            rows.add(rowConfigJson(row));
        }
        c.put("rows", rows);
        return c;
    }

    static Map<String, Object> rowConfigJson(RowDefinition row) {
        boolean readable = Boolean.TRUE.equals(row.getReadable());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", row.getKey());
        r.put("objectId", row.getObjectId());
        r.put("attribute", row.getAttribute());
        r.put("name", readable ? row.getDisplayName() : null);
        r.put("color", row.getColor());
        r.put("axis", row.getAxis());
        r.put("chartType", row.getChartType() != null ? row.getChartType().name() : AnalysisChartType.DEFAULT.name());
        r.put("unit", readable ? row.getUnitDisplay() : null);
        r.put("decimalDigits", row.getDecimalDigits());
        r.put("calculation", row.isCalculation());
        r.put("bubbleType", row.getBubbleType());
        r.put("readable", readable);
        return r;
    }

    /**
     * The notes of the rows (W4.4): for every row that is not part of a pie chart the notes of its "Data Notes"
     * objects in the resolved time frame (empty for rows the user may not read). Errors only leave the notes empty.
     *
     * @return notes by row key
     */
    private static Map<String, List<Map<String, Object>>> notes(SQLDataSource sql, AnalysisDefinition definition,
                                                                AnalysisResult result) {
        Map<String, List<Map<String, Object>>> notes = new HashMap<>();
        Map<String, RowStatus> status = new HashMap<>();
        for (ChartResult chart : result.getCharts()) {
            for (RowResult row : chart.getRows()) status.put(row.getKey(), row.getStatus());
        }
        ResourceWebModules.Lookup lookup = null;
        Set<String> cleanDataClasses = null;
        ResourceWebNotes.Samples samples = ResourceWebNotes.sqlSamples(sql);
        for (ChartDefinition chart : definition.getCharts()) {
            for (RowDefinition row : chart.getRows()) {
                if (isPie(chart, row) || row.getKey() == null) continue;
                List<Map<String, Object>> rowNotes = new ArrayList<>();
                notes.put(row.getKey(), rowNotes);
                if (!Boolean.TRUE.equals(row.getReadable()) || status.get(row.getKey()) == RowStatus.NO_PERMISSION
                        || result.getFrom() == null || result.getTo() == null) {
                    continue;
                }
                try {
                    if (lookup == null) {
                        lookup = ResourceWebModules.lookup(sql);
                        cleanDataClasses = ResourceWebRegisters.Classes.fromCache().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA);
                    }
                    if (lookup.readable(row.getObjectId()) == null) continue;
                    rowNotes.addAll(ResourceWebNotes.rowNotes(row.getObjectId(), result.getFrom(), result.getTo(),
                            lookup, cleanDataClasses, samples));
                } catch (Exception ex) {
                    logger.warn("Could not load the notes of row {}", row.getKey(), ex);
                }
            }
        }
        return notes;
    }

    /**
     * @return true for rows shown as pie (the chart is a pie chart or the row is drawn as pie)
     */
    static boolean isPie(ChartDefinition chart, RowDefinition row) {
        return chart.getType() == AnalysisChartType.PIE || row.getChartType() == AnalysisChartType.PIE;
    }

    static Map<String, Object> dataJson(AnalysisResult analysisResult) {
        return dataJson(analysisResult, Collections.emptyMap());
    }

    /**
     * @param notes notes by row key; rows without entry (pie rows) get no {@code notes}
     */
    static Map<String, Object> dataJson(AnalysisResult analysisResult, Map<String, List<Map<String, Object>>> notes) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", analysisResult.getFrom() != null ? analysisResult.getFrom().toString() : null);
        result.put("to", analysisResult.getTo() != null ? analysisResult.getTo().toString() : null);
        List<Map<String, Object>> charts = new ArrayList<>();
        for (ChartResult chart : analysisResult.getCharts()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("id", chart.getId());
            List<Map<String, Object>> rows = new ArrayList<>();
            for (RowResult row : chart.getRows()) {
                Map<String, Object> r = rowDataJson(row);
                List<Map<String, Object>> rowNotes = notes.get(row.getKey());
                if (rowNotes != null) r.put("notes", rowNotes);
                rows.add(r);
            }
            c.put("rows", rows);
            charts.add(c);
        }
        result.put("charts", charts);
        return result;
    }

    static Map<String, Object> rowDataJson(RowResult row) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("key", row.getKey());
        r.put("status", row.getStatus() != null ? row.getStatus().name() : RowStatus.ERROR.name());
        r.put("message", row.getMessage());
        r.put("unit", row.getUnit());
        /* pie rows are loaded without aggregation (like the Control Center): only their value is sent, not the raw
           samples (which can be several MB for a year) */
        Double pieValue = finite(row.getPieValue());
        if (pieValue != null) r.put("pieValue", pieValue);
        List<Object[]> samples = new ArrayList<>();
        if (row.getSamples() != null && row.getPieValue() == null) {
            for (ChartSample sample : row.getSamples()) {
                Object value = sample.getValue();
                if (value instanceof Number) {
                    value = finite(((Number) value).doubleValue());
                } else if (value != null && !(value instanceof String)) {
                    value = value.toString();
                }
                samples.add(new Object[]{sample.getMillis(), value, sample.getNote()});
            }
        }
        r.put("samples", samples);
        RowStatistics stats = row.getStats();
        if (stats != null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("min", finite(stats.getMin()));
            s.put("max", finite(stats.getMax()));
            s.put("avg", finite(stats.getAvg()));
            s.put("sum", finite(stats.getSum()));
            s.put("count", stats.getCount());
            r.put("stats", s);
        } else {
            r.put("stats", null);
        }
        return r;
    }

    /**
     * @return the value or {@code null} for NaN and infinity (not valid in JSON)
     */
    static Double finite(Double value) {
        return value == null || value.isNaN() || value.isInfinite() ? null : value;
    }
}
