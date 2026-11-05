/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.*;

import javax.security.sasl.AuthenticationException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.*;
import java.util.function.Function;

/**
 * Read-only effective rights view (OpenJEVis #1211, "Wer darf was?"). The rights are computed with the rules of
 * {@link UserRightManagerForWS} (see {@link PermissionExplainer}), so they match what the web service allows.
 * <ul>
 *     <li>{@code GET object/{id}}: the owner groups of the object and the effective rights of the users, with the
 *     group memberships (and covering roles) each right comes from. The caller must be able to read the object.
 *     Sys Admins and users with write right on the object get all users that have at least one right
 *     ({@code complete=true}); other users only get their own row ({@code complete=false}).</li>
 *     <li>{@code GET user/{id}?object={oid}}: the group memberships and roles of a user and, if {@code object} is
 *     given, his effective rights on that object. Sys Admins can ask for every user, other users only for themselves
 *     and only for objects they can read.</li>
 * </ul>
 */
@Path("/JEWebService/v1/permissions")
public class ResourcePermissions {

    private static final Logger logger = LogManager.getLogger(ResourcePermissions.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GET
    @Logged
    @Path("object/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getObjectPermissions(@Context HttpHeaders httpHeaders, @Context Request request, @Context UriInfo url,
                                         @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            UserRightManagerForWS um = ds.getUserManager();
            JEVisUserSQL current = ds.getCurrentUser();

            JsonObject object = ds.getObject(id);
            if (object == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("Object not found").build();
            }
            if (!canRead(um, object)) {
                return Response.status(Response.Status.FORBIDDEN).entity("No read right on this object").build();
            }
            boolean allUsers = um.isSysAdmin() || um.canWriteWOE(object);

            /* owner groups first, before the relationship cache of this request is loaded (as in normal requests) */
            PermissionExplainer.ObjectOwners owners = um.getOwners(object);
            PermissionExplainer.RoleIndex roles = PermissionExplainer.RoleIndex.of(roleRelationships(ds));
            Function<Long, String> names = nameResolver(ds);

            JsonObjectPermissions result = new JsonObjectPermissions();
            result.setObjectId(object.getId());
            result.setObjectName(object.getName());
            result.setJevisClass(object.getJevisClass());
            result.setPublicObject(object.getisPublic());
            result.setComplete(allUsers);
            for (Long groupId : owners.getAllGroups()) {
                result.getOwnerGroups().add(new JsonPermissionRef(groupId, names.apply(groupId)));
            }

            if (allUsers) {
                for (JEVisUserSQL user : um.getAllUsers()) {
                    JsonEffectiveRights rights = um.explain(user, owners, roles, names);
                    if (rights.hasAnyRight()) {
                        result.getUsers().add(rights);
                    }
                }
                result.getUsers().sort(Comparator.comparing(r -> r.getUserName() == null ? "" : r.getUserName().toLowerCase(Locale.ROOT)));
            } else {
                result.getUsers().add(um.explain(current, owners, roles, names));
            }

            return ok(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not explain the rights on object {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Logged
    @Path("user/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUserPermissions(@Context HttpHeaders httpHeaders, @Context Request request, @Context UriInfo url,
                                       @PathParam("id") long id,
                                       @QueryParam("object") Long objectId) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            UserRightManagerForWS um = ds.getUserManager();
            JEVisUserSQL current = ds.getCurrentUser();

            if (!um.isSysAdmin() && current.getUserID() != id) {
                return Response.status(Response.Status.FORBIDDEN).entity("Only Sys Admins can read the rights of other users").build();
            }
            JEVisUserSQL user = null;
            for (JEVisUserSQL candidate : um.getAllUsers()) {
                if (candidate.getUserID() == id) {
                    user = candidate;
                    break;
                }
            }
            if (user == null) {
                return Response.status(Response.Status.NOT_FOUND).entity("User not found").build();
            }

            JsonObject object = null;
            PermissionExplainer.ObjectOwners owners = null;
            if (objectId != null) {
                object = ds.getObject(objectId);
                if (object == null) {
                    return Response.status(Response.Status.NOT_FOUND).entity("Object not found").build();
                }
                if (!canRead(um, object)) {
                    return Response.status(Response.Status.FORBIDDEN).entity("No read right on this object").build();
                }
                owners = um.getOwners(object);
            }

            PermissionExplainer.RoleIndex roles = PermissionExplainer.RoleIndex.of(roleRelationships(ds));
            Function<Long, String> names = nameResolver(ds);
            List<JsonRelationship> memberships = um.getMemberships(user.getUserID());

            JsonUserPermissions result = new JsonUserPermissions();
            result.setUserId(user.getUserID());
            result.setUserName(user.getAccountName());
            result.setSysAdmin(user.isSysAdmin());
            result.setEnabled(user.isEnabled());
            result.setGroups(PermissionExplainer.groupRights(user.getUserID(), memberships, roles, names));
            result.getGroups().sort(Comparator.comparing(g -> g.getGroupName() == null ? "" : g.getGroupName().toLowerCase(Locale.ROOT)));
            result.setRoles(PermissionExplainer.roleRights(user.getUserID(), roles, names));
            if (object != null) {
                result.setObjectId(object.getId());
                result.setObjectName(object.getName());
                result.setObjectRights(PermissionExplainer.explain(user, owners, memberships, roles, names));
            }
            return ok(result);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not explain the rights of user {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    private static boolean canRead(UserRightManagerForWS um, JsonObject object) {
        try {
            return um.canRead(object);
        } catch (Exception ex) {
            return false;
        }
    }

    private static List<JsonRelationship> roleRelationships(SQLDataSource ds) {
        List<JsonRelationship> rels = new ArrayList<>();
        for (JsonRelationship rel : ds.getRelationships()) {
            int type = rel.getType();
            if (type >= JEVisConstants.ObjectRelationship.ROLE_MEMBER && type <= JEVisConstants.ObjectRelationship.ROLE_DELETE) {
                rels.add(rel);
            }
        }
        return rels;
    }

    /**
     * Resolves object names once per id; unknown objects give {@code null}.
     */
    private static Function<Long, String> nameResolver(SQLDataSource ds) {
        Map<Long, String> cache = new HashMap<>();
        return id -> {
            if (id == null) return null;
            if (cache.containsKey(id)) return cache.get(id);
            String name = null;
            try {
                JsonObject obj = ds.getObject(id);
                if (obj != null) name = obj.getName();
            } catch (Exception ex) {
                logger.debug("Could not resolve the name of object {}", id, ex);
            }
            cache.put(id, name);
            return name;
        };
    }

    private static Response ok(Object entity) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(entity)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }
}
