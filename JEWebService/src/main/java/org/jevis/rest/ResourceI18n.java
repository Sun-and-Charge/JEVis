/**
 * Copyright (C) 2013 - 2016 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
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


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.json.JsonI18nClass;
import org.jevis.commons.ws.sql.Config;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * resource service to translate jevis
 *
 * @author Florian Simon<florian.simon@openjevis.org>
 */
@Path("/JEWebService/v1/i18n")
public class ResourceI18n {

    private static final Logger logger = LogManager.getLogger(ResourceI18n.class);
    private List<JsonI18nClass> files;

    /**
     * Translations loaded by {@link #cached()}, reloaded after {@link #CACHE_MILLIS}
     */
    static final long CACHE_MILLIS = 5 * 60 * 1000L;
    private static List<JsonI18nClass> cache;
    private static long cacheTime;

    private static List<File> listClassI18nFiles(File dir) {
        List<File> fileTree = new ArrayList<>();
        if (dir == null || dir.listFiles() == null) {
            return fileTree;
        }
        for (File entry : dir.listFiles()) {
            if (entry.isFile()) {
                if (entry.getName().endsWith(".json")) {
                    fileTree.add(entry);
                }
            } else {
                fileTree.addAll(listClassI18nFiles(entry));
            }
        }
        return fileTree;
    }

    /**
     * Reads all translation files ({@code *.json}) below the directory; files that cannot be read are logged and
     * left out.
     */
    public static List<JsonI18nClass> loadAll(File dir) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        List<JsonI18nClass> result = new ArrayList<>();
        for (File file : listClassI18nFiles(dir)) {
            try {
                result.add(mapper.readValue(file, JsonI18nClass.class));
            } catch (Exception fex) {
                logger.error("Error while loading i18n file '{}':", file.getName(), fex);
            }
        }
        return result;
    }

    /**
     * @return the translations of the i18n directory ({@link Config#getI18nDir()}), cached for a few minutes
     */
    public static synchronized List<JsonI18nClass> cached() {
        long now = System.currentTimeMillis();
        if (cache == null || now - cacheTime > CACHE_MILLIS) {
            cache = Collections.unmodifiableList(loadAll(Config.getI18nDir()));
            cacheTime = now;
        }
        return cache;
    }

    /**
     * @param jclass
     * @param request
     * @param url
     * @param httpHeaders
     * @return
     */
    @GET
    @Logged
    @Produces(MediaType.APPLICATION_JSON)
    public Response getClassI18n(
            @DefaultValue("") @QueryParam("jclass") String jclass,
            @Context Request request,
            @Context UriInfo url,
            @Context HttpHeaders httpHeaders) {
        try {

            if (files == null) {
                files = loadAll(Config.getI18nDir());
            }

            if (!jclass.isEmpty()) {
                for (JsonI18nClass i18class : files) {
                    if (i18class.getJevisclass().equalsIgnoreCase(jclass)) {
                        return Response.ok(i18class).build();
                    }
                }
            }

            return Response.ok(files).build();
        } catch (Exception ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

}
