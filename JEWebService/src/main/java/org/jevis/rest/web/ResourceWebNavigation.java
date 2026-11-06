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
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonI18n;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Navigation of the web app: the organisations and buildings the user may read, each with the analyses and
 * dashboards below it.
 * <p>
 * An analysis or dashboard is assigned to its nearest readable organisation or building. Items without such a site
 * are listed under {@code other}. Objects the user may not read are never part of the answer, not even as names of
 * parents.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebNavigation {

    static final String CLASS_ORGANIZATION = "Organization";
    static final String CLASS_BUILDING = "Building";
    static final String CLASS_ANALYSIS = "Analysis";
    static final String CLASS_DASHBOARD = "Dashboard Analysis";
    static final String CLASS_DASHBOARD_COLLECTION = "Dashboard Collection";
    static final String ATTRIBUTE_START_DASHBOARD = "Start dashboard";
    static final String ATTRIBUTE_DASHBOARDS = "Dashboards";

    private static final Logger logger = LogManager.getLogger(ResourceWebNavigation.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Path("navigation")
    @Produces(MediaType.APPLICATION_JSON)
    public Response navigation(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            JEVisUserSQL user = ds.getCurrentUser();
            List<JsonObject> all = ds.getObjects();
            List<JsonObject> readable = ds.getUserManager().filterList(all);
            /* the parent is a relationship (from child to parent), it is not filled in the object list */
            Map<Long, Long> parents = new HashMap<>();
            for (JsonRelationship rel : ds.getRelationships(JEVisConstants.ObjectRelationship.PARENT)) {
                parents.put(rel.getFrom(), rel.getTo());
            }

            Map<String, Object> result = build(all, readable, parents);
            Map<String, Object> me = new LinkedHashMap<>();
            me.put("id", user.getUserID());
            me.put("name", user.getAccountName());
            me.put("sysAdmin", user.isSysAdmin());
            result.put("user", me);
            result.put("startDashboard", startDashboard(ds, user.getUserID(), readable));
            return Response.ok(MAPPER.writeValueAsString(result)).type(MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store").build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not build the navigation", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    /**
     * @param all      all objects (to walk up the parents)
     * @param readable the objects the user may read
     * @param parents  parent id by object id
     * @return {@code sites} (sorted by name, with {@code parentId} of the nearest readable parent site) and
     * {@code other} (analyses and dashboards without a readable site)
     */
    static Map<String, Object> build(Collection<JsonObject> all, Collection<JsonObject> readable, Map<Long, Long> parents) {
        Map<Long, JsonObject> byId = new HashMap<>();
        for (JsonObject object : all) byId.put(object.getId(), object);
        Set<Long> readableIds = new HashSet<>();
        for (JsonObject object : readable) readableIds.add(object.getId());

        Map<Long, Map<String, Object>> sites = new LinkedHashMap<>();
        List<JsonObject> sorted = new ArrayList<>(readable);
        sorted.sort(Comparator.comparing(o -> o.getName() == null ? "" : o.getName().toLowerCase(Locale.ROOT)));
        for (JsonObject object : sorted) {
            if (isSite(object)) {
                Map<String, Object> site = entry(object);
                site.put("className", object.getJevisClass());
                Long parent = nearestSite(object, byId, parents, readableIds);
                site.put("parentId", parent);
                site.put("analyses", new ArrayList<Map<String, Object>>());
                site.put("dashboards", new ArrayList<Map<String, Object>>());
                sites.put(object.getId(), site);
            }
        }

        Map<String, Object> other = new LinkedHashMap<>();
        other.put("analyses", new ArrayList<Map<String, Object>>());
        other.put("dashboards", new ArrayList<Map<String, Object>>());
        for (JsonObject object : sorted) {
            String list;
            if (CLASS_ANALYSIS.equals(object.getJevisClass())) {
                list = "analyses";
            } else if (CLASS_DASHBOARD.equals(object.getJevisClass())) {
                list = "dashboards";
            } else {
                continue;
            }
            Long siteId = nearestSite(object, byId, parents, readableIds);
            Map<String, Object> target = siteId != null && sites.containsKey(siteId) ? sites.get(siteId) : other;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) target.get(list);
            items.add(entry(object));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sites", new ArrayList<>(sites.values()));
        result.put("other", other);
        return result;
    }

    /**
     * The start dashboard of the user ({@code DashboardControl.getUserSelectedDashboard}): the attribute "Start
     * dashboard" of the user, if readable; for a Dashboard Collection its first readable dashboard (attribute
     * "Dashboards").
     */
    private static Long startDashboard(SQLDataSource ds, long userId, Collection<JsonObject> readable) {
        try {
            JsonSample sample = ds.getLastSample(userId, ATTRIBUTE_START_DASHBOARD);
            if (sample == null) return null;
            Map<Long, JsonObject> readableById = new HashMap<>();
            for (JsonObject object : readable) readableById.put(object.getId(), object);
            return startDashboard(sample.getValue(), readableById, collectionId -> {
                try {
                    JsonSample dashboards = ds.getLastSample(collectionId, ATTRIBUTE_DASHBOARDS);
                    return dashboards != null ? dashboards.getValue() : null;
                } catch (Exception e) {
                    return null;
                }
            });
        } catch (Exception e) {
            logger.warn("Could not read the start dashboard of user {}", userId, e);
            return null;
        }
    }

    /**
     * @param target                 value of "Start dashboard" (object id, optionally with ":attribute")
     * @param readableById           the objects the user may read
     * @param dashboardsOfCollection value of "Dashboards" of a collection (comma separated ids)
     * @return the id of the start dashboard or {@code null}
     */
    static Long startDashboard(String target, Map<Long, JsonObject> readableById,
                               java.util.function.Function<Long, String> dashboardsOfCollection) {
        Long id = firstId(target);
        JsonObject object = id != null ? readableById.get(id) : null;
        if (object == null) return null;
        if (CLASS_DASHBOARD.equals(object.getJevisClass())) return id;
        if (CLASS_DASHBOARD_COLLECTION.equals(object.getJevisClass())) {
            String list = dashboardsOfCollection.apply(id);
            if (list == null) return null;
            for (String part : list.split(",")) {
                Long dashboardId = firstId(part);
                JsonObject dashboard = dashboardId != null ? readableById.get(dashboardId) : null;
                if (dashboard != null && CLASS_DASHBOARD.equals(dashboard.getJevisClass())) return dashboardId;
            }
        }
        return null;
    }

    private static Long firstId(String value) {
        if (value == null) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\s*(\\d+)").matcher(value);
        if (!matcher.find()) return null;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isSite(JsonObject object) {
        return CLASS_ORGANIZATION.equals(object.getJevisClass()) || CLASS_BUILDING.equals(object.getJevisClass());
    }

    /**
     * @return the id of the nearest readable organisation or building above the object, or null
     */
    static Long nearestSite(JsonObject object, Map<Long, JsonObject> byId, Map<Long, Long> parents, Set<Long> readableIds) {
        Set<Long> visited = new HashSet<>();
        JsonObject current = byId.get(parents.get(object.getId()));
        while (current != null && visited.add(current.getId())) {
            if (isSite(current) && readableIds.contains(current.getId())) return current.getId();
            current = byId.get(parents.get(current.getId()));
        }
        return null;
    }

    private static Map<String, Object> entry(JsonObject object) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", object.getId());
        entry.put("name", object.getName());
        Map<String, String> names = new LinkedHashMap<>();
        if (object.getI18n() != null) {
            for (JsonI18n i18n : object.getI18n()) {
                if (i18n.getKey() != null && i18n.getValue() != null && !i18n.getValue().trim().isEmpty()) {
                    names.put(i18n.getKey(), i18n.getValue());
                }
            }
        }
        entry.put("names", names);
        return entry;
    }
}
