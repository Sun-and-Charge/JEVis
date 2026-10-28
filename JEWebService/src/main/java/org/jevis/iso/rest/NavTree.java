/**
 * Copyright (C) JEVis contributors
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
package org.jevis.iso.rest;

import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.iso.add.JEVisClassTree;
import org.jevis.iso.add.JEVisObjectTree;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
@Path("/JEWebService/v1/tree")
public class NavTree {

    /**
     * @param httpHeaders
     * @return
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getObject(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url
    ) throws Exception {
        SQLDataSource ds = null;
        try {

            ds = new SQLDataSource(httpHeaders, request, url);
            ds.preload(SQLDataSource.PRELOAD.ALL_OBJECT);
            ds.preload(SQLDataSource.PRELOAD.ALL_REL);

            final String objects = "Objects";
            final String classes = "Classes";

            String output = "<div class=\"tabnav\">\n" +
                    "    <button class=\"tabnavlinks\" onclick=\"openNavTab(event, 'Objects')\" id=\"tabObjects\">" +
                    objects +
                    "</button>\n" +
                    "    <button class=\"tabnavlinks\" onclick=\"openNavTab(event, 'Classes')\" id=\"tabClasses\">" +
                    classes +
                    "</button>\n" +
                    "</div>";

            output += "<div id=\"Objects\" class=\"tabnavcontent\">";

            JEVisObjectTree tt = new JEVisObjectTree(ds, httpHeaders.getRequestHeader("authorization").get(0));
            output += tt.buildTree();

            output += "</div>\n" +
                    "<div id=\"Classes\" class=\"tabnavcontent\">";

            JEVisClassTree ct = new JEVisClassTree(ds, httpHeaders.getRequestHeader("authorization").get(0));

            output += ct.buildTree();
            output += "</div>";

            return Response.ok(output).build();
        } finally {
            Config.CloseDS(ds);
        }
    }
}
