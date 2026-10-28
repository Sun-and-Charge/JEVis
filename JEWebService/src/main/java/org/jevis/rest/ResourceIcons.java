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
package org.jevis.rest;

import org.apache.logging.log4j.LogManager;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;

import javax.annotation.PostConstruct;
import javax.security.sasl.AuthenticationException;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author fs
 */
@Path("/JEWebService/v1/classicons")
public class ResourceIcons {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(ResourceIcons.class);

    public final static String TEMPDIR = "JEWebService";
    public final static String TEMPFILE = "allIcons.zip";
    private SQLDataSource ds = null;

    private static List<File> listIconFilesRecursive(File dir) {
        List<File> result = new ArrayList<>();
        if (dir == null || dir.listFiles() == null) return result;
        for (File entry : dir.listFiles()) {
            if (entry.isFile()) {
                String n = entry.getName();
                if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".gif")) {
                    result.add(entry);
                }
            } else {
                result.addAll(listIconFilesRecursive(entry));
            }
        }
        return result;
    }

    @GET
    @Logged
    @Produces({"application/zip"})
    public Response get(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url) {


        try {
            ds = new SQLDataSource(httpHeaders, request, url);

            File tmpZipFile = new File(FileCache.CLASS_ICON_FILE);
            if (tmpZipFile.exists()) {
                return Response.ok(new FileInputStream(tmpZipFile), MediaType.valueOf("application/zip")).build();
            } else {
                tmpZipFile.getParentFile().mkdirs();
            }

            //else, create new file
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(tmpZipFile));
            zos.setMethod(ZipOutputStream.DEFLATED);
            zos.setLevel(9);//because we cache it

            byte[] buffer = new byte[1024];

            for (File imageFile : listIconFilesRecursive(Config.getClassDir())) {
                ZipEntry ze = new ZipEntry(imageFile.getName());
                zos.putNextEntry(ze);
                FileInputStream in = new FileInputStream(imageFile);

                int len;
                while ((len = in.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                in.close();

            }
            zos.closeEntry();
            zos.close();

            Response re = Response.ok(new FileInputStream(tmpZipFile), MediaType.valueOf("application/zip")).build();

            return re;

        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception jex) {
            jex.printStackTrace();
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    @PostConstruct
    public void postConstruct() {
        if (ds != null) {
            ds.clear();
            ds = null;
        }

    }
}
