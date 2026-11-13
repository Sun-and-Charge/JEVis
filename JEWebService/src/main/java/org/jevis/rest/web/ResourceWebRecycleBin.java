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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.CachedAccessControl;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;

import java.util.*;

/**
 * The recycle bin of the web app for analyses and dashboards (see docs/WEB-API.md, "Recycle bin"): the deleted
 * analyses or dashboards the user may read, restoring them (to the original place or to a chosen directory) and
 * deleting them for good.
 * <p>
 * The rules are those of {@code ResourceObject}: restoring and deleting for good need the delete right on the object,
 * restoring to another directory also the create right there; restoring is {@link SQLDataSource#restoreObject} with
 * {@code requireParent}, so an object whose original place is gone is not restored without a target.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebRecycleBin {

    static final String KIND_ANALYSES = "analyses";
    static final String KIND_DASHBOARDS = "dashboards";
    static final String ACTION_RESTORE = "recycle-restore";
    static final String ACTION_DELETE = "recycle-delete";

    private static final Logger logger = LogManager.getLogger(ResourceWebRecycleBin.class);

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("recycle")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @QueryParam("kind") String kind, @QueryParam("lang") String lang) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.runSql(httpHeaders, "read the recycle bin", (sql, store) -> list(store, sqlBin(sql), kind, language));
    }

    @POST
    @Path("recycle/{id}/restore")
    @Produces(MediaType.APPLICATION_JSON)
    public Response restore(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                            String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.runSql(httpHeaders, "restore " + id,
                (sql, store) -> restore(store, sqlBin(sql), id, body, language));
    }

    @DELETE
    @Path("recycle/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        return WebEdit.runSql(httpHeaders, "delete " + id + " for good", (sql, store) -> delete(store, sqlBin(sql), id));
    }

    // ---------------------------------------------------------------- data access

    /**
     * The recycle bin operations of one request (production: the {@link SQLDataSource} of the request).
     */
    interface Bin {
        /**
         * @return the places the object was deleted from (DELETED_PARENT relationships), empty if it went to the bin
         * together with a deleted object above it
         */
        List<Long> deletedParents(long id);

        /**
         * {@link SQLDataSource#restoreObject(JsonObject, Long, boolean)} with {@code requireParent}
         *
         * @throws JEVisException code 409 if the object has no place to go back to
         */
        void restore(JsonObject object, Long parent) throws Exception;

        /**
         * {@link SQLDataSource#deleteObject(JsonObject)}: the object and its subtree for good
         */
        void deleteForever(JsonObject object) throws Exception;
    }

    static Bin sqlBin(SQLDataSource ds) {
        return new Bin() {
            private Map<Long, List<Long>> deletedParents;

            @Override
            public List<Long> deletedParents(long id) {
                if (deletedParents == null) {
                    deletedParents = new HashMap<>();
                    for (JsonRelationship rel : ds.getRelationships(JEVisConstants.ObjectRelationship.DELETED_PARENT)) {
                        deletedParents.computeIfAbsent(rel.getFrom(), k -> new ArrayList<>()).add(rel.getTo());
                    }
                }
                return deletedParents.getOrDefault(id, Collections.emptyList());
            }

            @Override
            public void restore(JsonObject object, Long parent) throws Exception {
                ds.restoreObject(object, parent, true);
                invalidate();
            }

            @Override
            public void deleteForever(JsonObject object) throws Exception {
                ds.deleteObject(object);
                invalidate();
            }

            private void invalidate() {
                try {
                    CachedAccessControl.getInstance(ds, true).invalidate();
                } catch (Exception e) {
                    logger.error("Could not invalidate the access control", e);
                }
            }
        };
    }

    // ---------------------------------------------------------------- kinds

    /**
     * @return the class of the items of the kind, null for unknown kinds
     */
    static String itemClass(String kind) {
        if (KIND_ANALYSES.equals(kind)) return ResourceWebEditAnalysis.CLASS_ANALYSIS;
        if (KIND_DASHBOARDS.equals(kind)) return ResourceWebEditDashboard.CLASS_DASHBOARD;
        return null;
    }

    /**
     * @return the kind of an item class, null if the class is not handled by this bin
     */
    static String kind(String jevisClass) {
        if (ResourceWebEditAnalysis.CLASS_ANALYSIS.equals(jevisClass)) return KIND_ANALYSES;
        if (ResourceWebEditDashboard.CLASS_DASHBOARD.equals(jevisClass)) return KIND_DASHBOARDS;
        return null;
    }

    static String directoryClass(String kind) {
        return KIND_ANALYSES.equals(kind) ? ResourceWebEditAnalysis.CLASS_DIRECTORY : ResourceWebEditDashboard.CLASS_DIRECTORY;
    }

    // ---------------------------------------------------------------- requests

    /**
     * {@code GET /web/recycle?kind=analyses|dashboards}: the readable analyses or dashboards in the recycle bin, newest
     * first
     */
    static WebEdit.Reply list(WebEdit.Store store, Bin bin, String kind, String language) throws Exception {
        String itemClass = itemClass(kind);
        if (itemClass == null) return WebEdit.Reply.badRequest("kind must be analyses or dashboards");
        ResourceWebModules.Lookup lookup = store.lookup();
        List<JsonObject> deleted = new ArrayList<>();
        for (JsonObject object : lookup.readable()) {
            if (ResourceWebIso.isDeleted(object) && itemClass.equals(object.getJevisClass())) deleted.add(object);
        }
        deleted.sort(Comparator.comparingLong(ResourceWebRecycleBin::deletedMillis).reversed()
                .thenComparing(JsonObject::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonObject object : deleted) result.add(entry(store, bin, object, language));
        return WebEdit.Reply.ok(result);
    }

    /**
     * One entry of the bin: name, kind, deletion time and user, original place and what the user may do
     */
    static Map<String, Object> entry(WebEdit.Store store, Bin bin, JsonObject object, String language) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        WebEdit.Rights rights = store.rights();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", ResourceWebEditAnalysis.localName(object, language));
        json.put("kind", kind(object.getJevisClass()));
        json.put("deletedAt", deletedAt(object));
        json.put("deletedBy", deletedBy(lookup, object));
        JsonObject parent = originalParent(lookup, bin, object);
        boolean parentGone = parent == null;
        /* only readable places are named */
        json.put("path", parent != null && lookup.readable(parent.getId()) != null
                ? ResourceWebEditAnalysis.path(lookup, parent, language) : null);
        json.put("parentGone", parentGone);
        boolean canDelete = rights.canDelete(object);
        json.put("canRestore", canDelete);
        json.put("canDeleteForever", canDelete);
        return json;
    }

    /**
     * @return the first original parent that still exists outside the recycle bin, null if there is none
     */
    static JsonObject originalParent(ResourceWebModules.Lookup lookup, Bin bin, JsonObject object) {
        for (Long id : bin.deletedParents(object.getId())) {
            JsonObject parent = lookup.object(id);
            if (parent != null && !ResourceWebIso.isDeleted(parent)) return parent;
        }
        return null;
    }

    /**
     * {@code POST /web/recycle/{id}/restore} with an optional {@code {"parent": id}}
     */
    static WebEdit.Reply restore(WebEdit.Store store, Bin bin, long id, String body, String language) throws Exception {
        JsonObject object = binObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Not in the recycle bin");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to restore this object");
        Long parentId = body != null && !body.trim().isEmpty() ? parent(WebEdit.body(body)) : null;
        ResourceWebModules.Lookup lookup = store.lookup();
        String kind = kind(object.getJevisClass());
        JsonObject target;
        if (parentId != null) {
            target = WebEdit.readable(store, parentId);
            if (target == null || !directoryClass(kind).equals(target.getJevisClass())) {
                return WebEdit.Reply.notFound("Directory not found");
            }
            if (!store.rights().canCreate(target, object.getJevisClass())) {
                return WebEdit.Reply.forbidden("Not allowed to create in this directory");
            }
        } else {
            target = originalParent(lookup, bin, object);
            if (target == null) return parentGone();
        }
        try {
            bin.restore(object, parentId);
        } catch (JEVisException e) {
            if (e.getCode() == 409) return parentGone();
            if (e.getCode() == 3021) return WebEdit.Reply.forbidden("Not allowed to restore this object");
            throw e;
        }
        store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT,
                "restore " + WebEdit.objectLog(id, object.getName(), ACTION_RESTORE));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", ResourceWebEditAnalysis.localName(object, language));
        json.put("kind", kind);
        json.put("parentId", target.getId());
        json.put("path", lookup.readable(target.getId()) != null
                ? ResourceWebEditAnalysis.path(lookup, target, language) : null);
        return WebEdit.Reply.ok(json);
    }

    /**
     * {@code DELETE /web/recycle/{id}}: deletes an analysis or dashboard in the recycle bin for good
     */
    static WebEdit.Reply delete(WebEdit.Store store, Bin bin, long id) throws Exception {
        JsonObject object = binObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Not in the recycle bin");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this object");
        store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        bin.deleteForever(object);
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * @return the readable analysis or dashboard if it is in the recycle bin, else null
     */
    static JsonObject binObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = store.lookup().readable(id);
        if (object == null || !ResourceWebIso.isDeleted(object) || kind(object.getJevisClass()) == null) return null;
        return object;
    }

    private static WebEdit.Reply parentGone() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("parentGone", true);
        return WebEdit.Reply.conflict(json);
    }

    private static Long parent(JsonNode body) throws WebEdit.Stop {
        JsonNode node = body.get("parent");
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToLong()) throw WebEdit.Stop.badRequest("Invalid parent");
        return node.asLong();
    }

    /**
     * @return the deletion time (ISO date-time; older databases only have the day)
     */
    static String deletedAt(JsonObject object) {
        String deleted = object.getDeleted();
        return deleted != null && !deleted.trim().isEmpty() ? deleted : object.getDeleteTS();
    }

    static long deletedMillis(JsonObject object) {
        String text = deletedAt(object);
        if (text == null) return 0L;
        try {
            return new DateTime(text.trim()).getMillis();
        } catch (IllegalArgumentException e) {
            return 0L;
        }
    }

    /**
     * Like {@code ResourceObject.addDeletedByNames}: the name of the user object, even if the caller may not read it
     */
    static String deletedBy(ResourceWebModules.Lookup lookup, JsonObject object) {
        Long user = object.getDeletedBy();
        if (user == null) return null;
        JsonObject userObject = lookup.object(user);
        return userObject != null ? userObject.getName() : null;
    }
}
