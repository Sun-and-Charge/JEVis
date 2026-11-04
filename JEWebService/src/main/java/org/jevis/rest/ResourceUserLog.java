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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.json.JsonUserLogEntry;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.userlog.UserLogService;
import org.jevis.commons.ws.sql.userlog.UserLogUtil;

import javax.security.sasl.AuthenticationException;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.util.List;

/**
 * User activity log (OpenJEVis #2341).
 * <ul>
 *     <li>{@code GET ?user=<id>&from=<ms>&until=<ms>&offset=<n>&limit=<n>}: entries newest first, merged from the
 *     table {@code user_log} and the old {@code Activities} samples. Sys Admins can read every user (without
 *     {@code user}: all users), other users only their own log.</li>
 *     <li>{@code POST {"action":"USER_LOGIN|USER_LOGOUT","details":"..."}}: client events of the current user.</li>
 * </ul>
 */
@Path("/JEWebService/v1/userlog")
public class ResourceUserLog {

    private static final Logger logger = LogManager.getLogger(ResourceUserLog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CLIENT_DETAILS = 1000;

    @GET
    @Logged
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpHeaders httpHeaders, @Context Request request, @Context UriInfo url,
                        @QueryParam("user") Long user,
                        @QueryParam("from") Long from,
                        @QueryParam("until") Long until,
                        @QueryParam("offset") Integer offset,
                        @QueryParam("limit") Integer limit) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders, request, url);
            JEVisUserSQL current = ds.getCurrentUser();
            Long userId = user;
            if (!current.isSysAdmin()) {
                if (userId != null && userId != current.getUserID()) {
                    return Response.status(Response.Status.FORBIDDEN).entity("Only Sys Admins can read the log of other users").build();
                }
                userId = current.getUserID();
            }
            if (from != null && until != null && from > until) {
                return Response.status(Response.Status.BAD_REQUEST).entity("from is after until").build();
            }
            int pageLimit = UserLogUtil.clampLimit(limit);
            int pageOffset = UserLogUtil.clampOffset(offset, pageLimit);
            List<JsonUserLogEntry> entries = UserLogService.read(ds, userId, from, until, pageOffset, pageLimit);
            return Response.ok(MAPPER.writeValueAsString(entries)).type(MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store").build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the user log", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @POST
    @Logged
    @Consumes(MediaType.APPLICATION_JSON)
    public Response post(@Context HttpHeaders httpHeaders, @Context Request request, @Context UriInfo url, String body) {
        SQLDataSource ds = null;
        try {
            JsonNode json;
            try {
                json = MAPPER.readTree(body == null ? "" : body);
            } catch (Exception ex) {
                json = null;
            }
            if (json == null || !json.isObject()) {
                return Response.status(Response.Status.BAD_REQUEST).entity("JSON object expected").build();
            }
            String action = json.path("action").asText("");
            if (!UserLogUtil.ACTION_LOGIN.equals(action) && !UserLogUtil.ACTION_LOGOUT.equals(action)) {
                return Response.status(Response.Status.BAD_REQUEST).entity("Only USER_LOGIN and USER_LOGOUT can be logged by a client").build();
            }
            String details = json.hasNonNull("details") ? UserLogUtil.truncate(json.get("details").asText(), MAX_CLIENT_DETAILS) : null;
            ds = new SQLDataSource(httpHeaders, request, url);
            /* client events are logged for all users, also Sys Admins, like the old Control Center log */
            UserLogService.log(ds, ds.getCurrentUser(), action, null, details);
            return Response.noContent().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not write the user log", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }
}
