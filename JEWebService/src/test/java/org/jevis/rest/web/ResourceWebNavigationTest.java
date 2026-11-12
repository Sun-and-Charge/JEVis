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

import org.jevis.commons.ws.json.JsonObject;
import org.jevis.web.WebAppHttpHandlerAccess;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebNavigationTest {

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /** parent ids as the web service reads them from the parent relationships */
    private static Map<Long, Long> parents(Collection<JsonObject> objects) {
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : objects) parents.put(o.getId(), o.getParent());
        return parents;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    private static Map<String, Object> site(Map<String, Object> result, long id) {
        for (Map<String, Object> site : list(result, "sites")) {
            if (((Long) site.get("id")) == id) return site;
        }
        return null;
    }

    @Test
    public void itemsAreAssignedToTheNearestReadableSite() {
        JsonObject org = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
        JsonObject dir = obj(2, "Analysen", "Analyses Directory", 1);
        JsonObject orgAnalysis = obj(3, "Overview", ResourceWebNavigation.CLASS_ANALYSIS, 2);
        JsonObject building = obj(4, "Building A", ResourceWebNavigation.CLASS_BUILDING, 1);
        JsonObject dashDir = obj(5, "Dashboards", "Analyses Directory", 4);
        JsonObject dashboard = obj(6, "Energy", ResourceWebNavigation.CLASS_DASHBOARD, 5);
        List<JsonObject> all = Arrays.asList(org, dir, orgAnalysis, building, dashDir, dashboard);

        Map<String, Object> result = ResourceWebNavigation.build(all, all, parents(all));

        assertEquals(2, list(result, "sites").size());
        assertEquals(1, list(site(result, 1), "analyses").size());
        assertEquals(3L, list(site(result, 1), "analyses").get(0).get("id"));
        assertEquals(6L, list(site(result, 4), "dashboards").get(0).get("id"));
        assertEquals(1L, site(result, 4).get("parentId"));
        assertNull(site(result, 1).get("parentId"));
    }

    @Test
    public void unreadableObjectsAreNeitherListedNorUsedAsParent() {
        JsonObject org = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
        JsonObject building = obj(4, "Secret building", ResourceWebNavigation.CLASS_BUILDING, 1);
        JsonObject dashboard = obj(6, "Energy", ResourceWebNavigation.CLASS_DASHBOARD, 4);
        JsonObject hiddenAnalysis = obj(7, "Hidden", ResourceWebNavigation.CLASS_ANALYSIS, 4);
        List<JsonObject> all = Arrays.asList(org, building, dashboard, hiddenAnalysis);

        /* the building is not readable: the dashboard goes to the organisation, the analysis is not listed */
        Map<String, Object> result = ResourceWebNavigation.build(all, Arrays.asList(org, dashboard), parents(all));

        assertEquals(1, list(result, "sites").size());
        assertEquals(6L, list(site(result, 1), "dashboards").get(0).get("id"));
        assertTrue(list(site(result, 1), "analyses").isEmpty());
        assertFalse(result.toString().contains("Secret building"));
        assertFalse(result.toString().contains("Hidden"));
    }

    @Test
    public void itemsWithoutReadableSiteGoToOther() {
        JsonObject analysis = obj(3, "Loose", ResourceWebNavigation.CLASS_ANALYSIS, 99);
        Map<String, Object> result = ResourceWebNavigation.build(Collections.singletonList(analysis), Collections.singletonList(analysis), parents(Collections.singletonList(analysis)));

        @SuppressWarnings("unchecked")
        Map<String, Object> other = (Map<String, Object>) result.get("other");
        assertEquals(3L, list(other, "analyses").get(0).get("id"));
        assertTrue(list(result, "sites").isEmpty());
    }

    @Test
    public void objectsInTheRecycleBinAreNotListed() {
        JsonObject org = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
        JsonObject kept = obj(3, "Kept", ResourceWebNavigation.CLASS_ANALYSIS, 1);
        JsonObject deleted = obj(5, "Deleted", ResourceWebNavigation.CLASS_ANALYSIS, 0);
        deleted.setDeleteTS("2026-11-12T20:00:00.000Z");
        JsonObject deletedDashboard = obj(6, "Deleted board", ResourceWebNavigation.CLASS_DASHBOARD, 0);
        deletedDashboard.setDeleteTS("2026-11-12T20:00:00.000Z");
        List<JsonObject> all = Arrays.asList(org, kept, deleted, deletedDashboard);
        Map<String, Object> result = ResourceWebNavigation.build(all, all, parents(all));

        @SuppressWarnings("unchecked")
        Map<String, Object> other = (Map<String, Object>) result.get("other");
        assertTrue(list(other, "analyses").isEmpty());
        assertTrue(list(other, "dashboards").isEmpty());
        assertEquals(1, list(site(result, 1), "analyses").size());
    }

    @Test
    public void parentCyclesDoNotLoop() {
        JsonObject a = obj(1, "A", "Directory", 2);
        JsonObject b = obj(2, "B", "Directory", 1);
        JsonObject analysis = obj(3, "X", ResourceWebNavigation.CLASS_ANALYSIS, 1);
        Map<Long, JsonObject> byId = new HashMap<>();
        for (JsonObject o : Arrays.asList(a, b, analysis)) byId.put(o.getId(), o);
        assertNull(ResourceWebNavigation.nearestSite(analysis, byId, parents(byId.values()), new HashSet<>(byId.keySet())));
    }

    @Test
    public void spaFallbackOnlyForPathsWithoutExtension() {
        assertFalse(WebAppHttpHandlerAccess.hasExtension("/analyses/123"));
        assertFalse(WebAppHttpHandlerAccess.hasExtension("/"));
        assertFalse(WebAppHttpHandlerAccess.hasExtension("/.well-known/x"));
        assertTrue(WebAppHttpHandlerAccess.hasExtension("/assets/index-abc123.js"));
        assertTrue(WebAppHttpHandlerAccess.hasExtension("/favicon.png"));
    }
}
