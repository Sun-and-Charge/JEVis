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

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.ResourceAuth;
import org.jevis.rest.ResourcePermissions;

import javax.security.sasl.AuthenticationException;

/**
 * Stage E10.1 web administration of users, groups and rights (docs/WEB-API.md "E10.1 users, groups, rights"). The
 * logic is in {@link WebAdmin}; the "who may do what" views and the API keys wrap {@link ResourcePermissions} and
 * {@link ResourceAuth}.
 */
@Path("/JEWebService/v1/web/admin")
public class ResourceWebAdmin {

    private static final Logger logger = LogManager.getLogger(ResourceWebAdmin.class);

    interface EnvAction {
        WebEdit.Reply apply(WebAdmin.Env env) throws Exception;
    }

    static Response run(HttpHeaders headers, String what, EnvAction action) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(headers::getRequestHeader);
            WebAdmin.Env env = new WebAdminSql(ds);
            try {
                return action.apply(env).toResponse();
            } catch (WebEdit.Stop stop) {
                return stop.reply.toResponse();
            }
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not {}", what, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- scope

    @GET
    @Path("scope")
    @Produces(MediaType.APPLICATION_JSON)
    public Response scope(@Context HttpHeaders headers) {
        return run(headers, "read the administration scope", env -> WebEdit.Reply.ok(WebAdmin.scope(env)));
    }

    // ---------------------------------------------------------------- users

    @GET
    @Path("users")
    @Produces(MediaType.APPLICATION_JSON)
    public Response users(@Context HttpHeaders headers, @QueryParam("q") String q, @QueryParam("site") Long site) {
        return run(headers, "list the users", env -> WebEdit.Reply.ok(WebAdmin.users(env, q, site)));
    }

    @POST
    @Path("users")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createUser(@Context HttpHeaders headers, String body) {
        return run(headers, "create a user", env -> WebAdmin.createUser(env, body));
    }

    @PUT
    @Path("users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateUser(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "change user " + id, env -> WebAdmin.updateUser(env, id, body));
    }

    @POST
    @Path("users/{id}/password")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response setPassword(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "set the password of user " + id, env -> WebAdmin.setPassword(env, id, body));
    }

    @POST
    @Path("users/{id}/mfa-reset")
    public Response resetMfa(@Context HttpHeaders headers, @PathParam("id") long id) {
        return run(headers, "reset the two-factor authentication of user " + id, env -> WebAdmin.resetMfa(env, id));
    }

    @PUT
    @Path("users/{id}/groups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setUserGroups(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "change the groups of user " + id, env -> WebAdmin.setUserGroups(env, id, body));
    }

    // ---------------------------------------------------------------- groups

    @GET
    @Path("groups")
    @Produces(MediaType.APPLICATION_JSON)
    public Response groups(@Context HttpHeaders headers, @QueryParam("q") String q, @QueryParam("site") Long site) {
        return run(headers, "list the groups", env -> WebEdit.Reply.ok(WebAdmin.groups(env, q, site)));
    }

    @POST
    @Path("groups")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createGroup(@Context HttpHeaders headers, String body) {
        return run(headers, "create a group", env -> WebAdmin.createGroup(env, body));
    }

    @PUT
    @Path("groups/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response renameGroup(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "rename group " + id, env -> WebAdmin.renameGroup(env, id, body));
    }

    @DELETE
    @Path("groups/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteGroup(@Context HttpHeaders headers, @PathParam("id") long id, @QueryParam("version") String version) {
        return run(headers, "delete group " + id, env -> WebAdmin.deleteGroup(env, id, version));
    }

    @GET
    @Path("groups/{id}/members")
    @Produces(MediaType.APPLICATION_JSON)
    public Response members(@Context HttpHeaders headers, @PathParam("id") long id) {
        return run(headers, "list the members of group " + id, env -> WebEdit.Reply.ok(WebAdmin.members(env, id)));
    }

    @PUT
    @Path("groups/{id}/members")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response setMembers(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "change the members of group " + id, env -> WebAdmin.setMembers(env, id, body));
    }

    // ---------------------------------------------------------------- site sharing

    @GET
    @Path("sites/{id}/sharing")
    @Produces(MediaType.APPLICATION_JSON)
    public Response sharing(@Context HttpHeaders headers, @PathParam("id") long id) {
        return run(headers, "read the sharing of site " + id, env -> WebEdit.Reply.ok(WebAdmin.sharing(env, id)));
    }

    @POST
    @Path("sites/{id}/sharing/preview")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response previewSharing(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "plan the sharing of site " + id, env -> WebEdit.Reply.ok(WebAdmin.preview(env, id, body)));
    }

    @POST
    @Path("sites/{id}/sharing/apply")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response applySharing(@Context HttpHeaders headers, @PathParam("id") long id, String body) {
        return run(headers, "apply the sharing of site " + id, env -> WebAdmin.apply(env, id, body));
    }

    // ---------------------------------------------------------------- who may do what

    @GET
    @Path("rights")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rights(@Context HttpHeaders headers, @QueryParam("object") Long object, @QueryParam("user") Long user) {
        if (user != null) return ResourcePermissions.userPermissions(headers, user, object, true);
        if (object != null) return new ResourcePermissions().getObjectPermissions(headers, null, null, object);
        return Response.status(Response.Status.BAD_REQUEST).entity("object or user missing").build();
    }

    // ---------------------------------------------------------------- API keys (Sys Admins, interactive login)

    @GET
    @Path("apikeys")
    @Produces(MediaType.APPLICATION_JSON)
    public Response apiKeys(@Context HttpHeaders headers) {
        return new ResourceAuth().listApiKeys(headers, null, null);
    }

    @POST
    @Path("apikeys")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createApiKey(@Context HttpHeaders headers, String body) {
        return new ResourceAuth().createApiKey(headers, null, null, body);
    }

    @DELETE
    @Path("apikeys/{id}")
    public Response revokeApiKey(@Context HttpHeaders headers, @PathParam("id") String id) {
        return new ResourceAuth().revokeApiKey(headers, null, null, id);
    }
}
