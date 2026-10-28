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

import org.apache.commons.io.IOUtils;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.iso.classes.ISO50001;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStream;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
@Path("/JEWebService/v1/resources")
public class Resources {

    /**
     * @param httpHeaders
     * @param name
     * @return
     */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getObject(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @DefaultValue("") @QueryParam("name") String name
    ) {

        return Response.ok().build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("jquery")
    public Response getjQuery(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws IOException {
        String content;
        content = "";

        String fileName = "/jquery.js";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("jqueryui")
    public Response getjqueryui(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws IOException {
        String content;
        content = "";

        String fileName = "/jqueryui.js";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("chartbundle")
    public Response getchartbundle(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws IOException {
        String content;
        content = "";

        String fileName = "/chartbundle.js";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("utils")
    public Response getutils(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws IOException {
        String content;
        content = "";

        String fileName = "/utils.js";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    /**
     * @param theme
     * @return
     */
    @GET
    @Produces("text/css")
    @Path("styles")
    public Response getstyles(
            @DefaultValue("") @QueryParam("theme") String theme
    ) throws IOException {
        String content;
        content = "";
        InputStream in = null;

        if (theme.equals("")) {
            String fileName = "/styles.css";
            in = Resources.class.getResourceAsStream(fileName);
        } else {
            String fileName = "/themes/" + theme;
            in = Resources.class.getResourceAsStream(fileName);
        }

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    @GET
    @Produces("text/javascript")
    @Path("scripts")
    public Response gettools(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws Exception {

        String fileName = "/" + name;
        InputStream in = Resources.class.getResourceAsStream(fileName);

        String scriptdata = IOUtils.toString(in);

        return Response.ok(scriptdata).build();
    }

    @GET
    @Produces("text/css")
    @Path("navbar.css")
    public Response getnavbar(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws IOException {
        String content;
        content = "";

        String fileName = "/navbar.css";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        content = IOUtils.toString(in);

        return Response.ok(content).build();
    }

    /**
     * @param httpHeaders
     * @param org
     * @return
     */
    @GET
    @Produces("image/png")
    @Path("logo")
    public Response getlogo(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @DefaultValue("") @QueryParam("org") Boolean org
    ) throws Exception {
        SQLDataSource ds = null;
        String fileName = "";
        InputStream in = null;
        byte[] imageData = null;

        if (!org) {
            fileName = "/logo.png";
            in = Resources.class.getResourceAsStream(fileName);
            imageData = IOUtils.toByteArray(in);
        } else {

            ds = new SQLDataSource(httpHeaders, request, url);
            ds.preload(SQLDataSource.PRELOAD.ALL_OBJECT);
            ds.preload(SQLDataSource.PRELOAD.ALL_REL);

            for (JsonObject obj : ds.getObjects(ISO50001.getJc().getOrganizationDir().toString(), true)) {
                for (JsonAttribute att : obj.getAttributes()) {
                    if (att.getType().equals("Company Logo")) {
                        imageData = att.getLatestValue().getValue().getBytes();
                    }
                }
            }
        }
        return Response.ok(imageData).build();
    }

    @GET
    @Produces("image/png")
    @Path("image")
    public Response getimage(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws Exception {
        String content;
        content = "";

        String fileName = "/" + name;
        InputStream in = Resources.class.getResourceAsStream(fileName);

        byte[] imageData = IOUtils.toByteArray(in);

        return Response.ok(imageData).build();
    }

    @GET
    @Produces("image/png")
    @Path("treeview-icons")
    public Response gettreeviewicons(
            @Context HttpHeaders httpHeaders,
            @DefaultValue("") @QueryParam("name") String name
    ) throws Exception {
        String content;
        content = "";

        String fileName = "/treeview-icons.png";
        InputStream in = Resources.class.getResourceAsStream(fileName);

        byte[] imageData = IOUtils.toByteArray(in);

        return Response.ok(imageData).build();
    }
}
