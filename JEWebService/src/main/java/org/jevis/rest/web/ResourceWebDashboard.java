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
import org.jevis.api.*;
import org.jevis.commons.chart.ChartDefinition;
import org.jevis.commons.chart.RowDefinition;
import org.jevis.commons.chart.RowResult;
import org.jevis.commons.chart.RowStatus;
import org.jevis.commons.dashboard.*;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.local.LocalDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import javax.security.sasl.AuthenticationException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Dashboards for the web app (see docs/WEB-API.md): the configuration of a dashboard, the values of its widgets for a
 * time frame, its background image and the images of Image widgets. Editing (stage E4) is in
 * {@link ResourceWebEditDashboard}.
 * <p>
 * The dashboard is read and evaluated by the headless core {@code org.jevis.commons.dashboard} on a read-only
 * {@link LocalDataSource} with the rights of the logged-in user. Dashboards and objects the user may not read answer
 * 404 (like those that do not exist).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebDashboard {

    static final String FILE_ATTRIBUTE = "File";
    private static final Logger logger = LogManager.getLogger(ResourceWebDashboard.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Path("dashboards/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response config(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        /* with what the editor needs (stage E4): version, rights, directory, canEnter per widget */
        return WebEdit.runSql(httpHeaders, "read dashboard " + id,
                (sql, store) -> ResourceWebEditDashboard.config(store, ResourceWebEditDashboard.sqlEngine(sql), id));
    }

    @POST
    @Path("dashboards/{id}/data")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response data(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        DashboardRequest request;
        try {
            request = parseRequest(body);
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }

        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            LocalDataSource ds = new LocalDataSource(sql);
            JEVisObject dashboard = findDashboard(ds, id);
            if (dashboard == null) return notFound("Dashboard not found");
            DashboardDefinition definition = new DashboardReader().read(dashboard);
            DashboardResult result = new DashboardEvaluator(ds).evaluate(dashboard, definition, request);
            return json(dataJson(result));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).type(MediaType.TEXT_PLAIN).build();
        } catch (Exception ex) {
            logger.error("Could not evaluate dashboard {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    @GET
    @Path("dashboards/{id}/background")
    public Response background(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            JEVisObject dashboard = findDashboard(new LocalDataSource(sql), id);
            if (dashboard == null) return notFound("Dashboard not found");
            return image(latestFile(dashboard.getAttribute(DashboardReader.BACKGROUND_ATTRIBUTE)));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the background of dashboard {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    /**
     * The image of an object for Image widgets: the latest sample of its attribute "File" (as in the Control Center),
     * otherwise of the first file attribute that holds an image.
     */
    @GET
    @Path("objects/{id}/image")
    public Response objectImage(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource sql = null;
        try {
            sql = new SQLDataSource(httpHeaders::getRequestHeader);
            JEVisObject object = new LocalDataSource(sql).getObject(id);
            if (object == null) return notFound("Image not found");
            byte[] bytes = latestFile(object.getAttribute(FILE_ATTRIBUTE));
            if (contentType(bytes) == null) {
                bytes = null;
                for (JEVisAttribute attribute : object.getAttributes()) {
                    if (FILE_ATTRIBUTE.equals(attribute.getName())) continue;
                    try {
                        if (attribute.getPrimitiveType() != JEVisConstants.PrimitiveType.FILE) continue;
                    } catch (Exception e) {
                        continue;
                    }
                    byte[] candidate = latestFile(attribute);
                    if (contentType(candidate) != null) {
                        bytes = candidate;
                        break;
                    }
                }
            }
            return image(bytes);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the image of object {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(sql);
        }
    }

    private static JEVisObject findDashboard(LocalDataSource ds, long id) throws Exception {
        JEVisObject object = ds.getObject(id);
        if (object == null || !DashboardReader.DASHBOARD_CLASS.equals(object.getJEVisClassName())) return null;
        return object;
    }

    private static byte[] latestFile(JEVisAttribute attribute) {
        try {
            if (attribute == null || !attribute.hasSample()) return null;
            JEVisSample sample = attribute.getLatestSample();
            JEVisFile file = sample != null ? sample.getValueAsFile() : null;
            return file != null ? file.getBytes() : null;
        } catch (Exception e) {
            logger.debug("Could not read file of attribute {}", attribute, e);
            return null;
        }
    }

    /**
     * Images only (by their content, not their file name); SVG gets a restrictive content security policy.
     */
    static Response image(byte[] bytes) {
        String type = contentType(bytes);
        if (type == null) return notFound("Image not found");
        Response.ResponseBuilder builder = Response.ok(bytes).type(type)
                .header("Cache-Control", "private, max-age=300")
                .header("X-Content-Type-Options", "nosniff");
        if ("image/svg+xml".equals(type)) {
            builder.header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox");
        }
        return builder.build();
    }

    /**
     * @return the image type of the bytes or {@code null} if they are no image (PNG, JPEG, GIF, BMP, WebP, SVG)
     */
    static String contentType(byte[] b) {
        if (b == null || b.length < 4) return null;
        if ((b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if ((b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff) return "image/jpeg";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b[0] == 'B' && b[1] == 'M') return "image/bmp";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        String head = new String(b, 0, Math.min(b.length, 1024), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        if (head.startsWith("﻿")) head = head.substring(1);
        if ((head.startsWith("<?xml") || head.startsWith("<svg") || head.startsWith("<!--")) && head.contains("<svg")) {
            return "image/svg+xml";
        }
        return null;
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN).build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }

    // ---------------------------------------------------------------- request

    /**
     * Parses the request of {@code POST /data}. An unknown time zone falls back to the time zone of the server, an
     * unknown time frame to the one of the dashboard.
     *
     * @throws IllegalArgumentException if the body is no JSON object, {@code date} is invalid or {@code CUSTOM} has no
     *                                  valid from/to
     */
    static DashboardRequest parseRequest(String body) {
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
        DashboardRequest request = new DashboardRequest().setZone(zone).setTimeFrame(text(root, "timeFrame"));
        String date = text(root, "date");
        if (date != null) {
            DateTime parsed = parseDate(date, zone);
            if (parsed == null) throw new IllegalArgumentException("Invalid date");
            request.setDate(parsed);
        }
        if (DashboardTimeFrames.CUSTOM.equals(request.getTimeFrame())) {
            DateTime from = parseDate(text(root, "from"), zone);
            DateTime to = parseDate(text(root, "to"), zone);
            if (from == null || to == null || to.isBefore(from)) {
                throw new IllegalArgumentException("CUSTOM needs valid from and to");
            }
            request.setFrom(from).setTo(to);
        }
        return request;
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? null : node.asText();
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

    static Map<String, Object> configJson(long id, String name, Map<String, String> localNames, DashboardDefinition definition,
                                          List<DashboardTimeFrames.Option> options) {
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
        result.put("width", finite(definition.getWidth()));
        result.put("height", finite(definition.getHeight()));
        Object zoom = definition.getZoomValue();
        result.put("zoom", zoom instanceof Double ? finite((Double) zoom) : zoom);
        Map<String, Object> background = new LinkedHashMap<>();
        background.put("color", definition.getBackgroundColor());
        background.put("image", definition.isBackgroundImage());
        background.put("mode", definition.getBackgroundMode());
        result.put("background", background);
        result.put("updateRate", definition.getUpdateRate());
        result.put("timeFrame", definition.getTimeFrame());
        List<Map<String, Object>> timeFrames = new ArrayList<>();
        for (DashboardTimeFrames.Option option : options) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", option.getId());
            if (option.isCustom()) t.put("name", option.getName());
            t.put("custom", option.isCustom());
            timeFrames.add(t);
        }
        result.put("timeFrames", timeFrames);

        List<Map<String, Object>> widgets = new ArrayList<>();
        for (WidgetDefinition widget : definition.getWidgets()) {
            widgets.add(widgetJson(widget));
        }
        result.put("widgets", widgets);
        return result;
    }

    private static Map<String, Object> widgetJson(WidgetDefinition widget) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("uuid", String.valueOf(widget.getUuid()));
        w.put("type", widget.getType());
        w.put("x", finite(widget.getX()));
        w.put("y", finite(widget.getY()));
        w.put("width", finite(widget.getWidth()));
        w.put("height", finite(widget.getHeight()));
        w.put("layer", widget.getLayer());
        w.put("title", widget.getTitle());
        w.put("tooltip", widget.getTooltip());
        Map<String, Object> style = new LinkedHashMap<>();
        style.put("background", widget.getBackground());
        style.put("fontColor", widget.getFontColor());
        style.put("fontSize", finite(widget.getFontSize()));
        style.put("fontWeight", widget.getFontWeight());
        style.put("italic", widget.isItalic());
        style.put("underline", widget.isUnderline());
        style.put("titlePosition", widget.getTitlePosition());
        style.put("borderSize", finite(widget.getBorderSize()));
        style.put("borderColor", widget.getBorderColor());
        style.put("cornerRadius", finite(widget.getCornerRadius()));
        style.put("shadow", widget.isShadow());
        w.put("style", style);
        w.put("decimals", widget.getDecimals());
        w.put("decimalsAuto", widget.isDecimalsAuto());
        w.put("showValue", widget.isShowValue());
        w.put("timeFrame", widget.getTimeFrame());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RowDefinition row : widget.getRows()) rows.add(ResourceWebAnalysis.rowConfigJson(row));
        w.put("rows", rows);
        ChartDefinition chart = WidgetDefinition.TYPE_CHART.equals(widget.getType()) ? widget.getChart() : null;
        w.put("chart", chart != null ? ResourceWebAnalysis.chartConfigJson(chart) : null);
        w.put("config", sanitize(widget.getConfig()));
        return w;
    }

    /**
     * @return a copy with non-finite numbers replaced by {@code null} (not valid in JSON)
     */
    @SuppressWarnings("unchecked")
    static Object sanitize(Object value) {
        if (value instanceof Double) return finite((Double) value);
        if (value instanceof Float) return finite(((Float) value).doubleValue());
        if (value instanceof Map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            ((Map<String, Object>) value).forEach((k, v) -> copy.put(k, sanitize(v)));
            return copy;
        }
        if (value instanceof List) {
            List<Object> copy = new ArrayList<>();
            for (Object item : (List<Object>) value) copy.add(sanitize(item));
            return copy;
        }
        return value;
    }

    static Map<String, Object> dataJson(DashboardResult dashboardResult) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeFrame", dashboardResult.getTimeFrame());
        result.put("from", string(dashboardResult.getFrom()));
        result.put("to", string(dashboardResult.getTo()));
        result.put("previous", string(dashboardResult.getPrevious()));
        result.put("next", string(dashboardResult.getNext()));
        Map<String, Object> widgets = new LinkedHashMap<>();
        dashboardResult.getWidgets().forEach((uuid, widget) -> widgets.put(uuid, widgetDataJson(widget)));
        result.put("widgets", widgets);
        return result;
    }

    private static Map<String, Object> widgetDataJson(DashboardResult.WidgetResult widget) {
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("from", string(widget.getFrom()));
        w.put("to", string(widget.getTo()));
        if (widget.getKind() == DashboardResult.WidgetResult.Kind.ALARMS) {
            w.put("alarmCount", widget.getAlarmCount());
            return w;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RowResult row : widget.getRows()) {
            Map<String, Object> r;
            if (widget.getKind() == DashboardResult.WidgetResult.Kind.SERIES) {
                r = ResourceWebAnalysis.rowDataJson(row);
            } else {
                r = new LinkedHashMap<>();
                r.put("key", row.getKey());
                r.put("status", row.getStatus() != null ? row.getStatus().name() : RowStatus.ERROR.name());
                r.put("message", row.getMessage());
                r.put("unit", row.getUnit());
                if (widget.getKind() == DashboardResult.WidgetResult.Kind.TEXT) {
                    r.put("text", row.getText());
                } else {
                    Object value = row.getValue();
                    if (value instanceof Number) {
                        r.put("value", finite(((Number) value).doubleValue()));
                    } else if (value != null) {
                        r.put("value", null);
                        r.put("text", value.toString());
                    } else {
                        r.put("value", null);
                        if (row.getText() != null) r.put("text", row.getText());
                    }
                }
            }
            r.put("lastTimestamp", string(row.getLastTimestamp()));
            if (widget.getKind() != DashboardResult.WidgetResult.Kind.LATEST) r.put("count", row.getCount() != null ? row.getCount() : 0);
            rows.add(r);
        }
        w.put("rows", rows);
        return w;
    }

    private static String string(DateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    private static Double finite(Double value) {
        return ResourceWebAnalysis.finite(value);
    }
}
