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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisClassHelper;
import org.jevis.commons.ws.sql.SQLDataSource;

import jakarta.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.security.sasl.AuthenticationException;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Jersey REST resource that handles JEVisClass (schema) CRUD operations and icon serving.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /classes}          — list all JEVisClasses</li>
 *   <li>{@code GET    /classes/{name}}   — fetch one class by name</li>
 *   <li>{@code POST   /classes/{name}}   — create or update a class incl. its types and
 *       relationships (sysadmin only, persisted in the class JSON files)</li>
 *   <li>{@code DELETE /classes/{name}}   — delete a class definition (sysadmin only)</li>
 *   <li>{@code GET    /classes/{name}/icon} — fetch the class icon image</li>
 *   <li>{@code POST   /classes/{name}/icon} — upload a new class icon (sysadmin only)</li>
 * </ul>
 *
 * @author Florian Simon <florian.simon@envidatec.com>
 */
@Path("/JEWebService/v1/classes")
public class ResourceClasses {

    private static final Logger logger = LogManager.getLogger(ResourceClasses.class);
    private SQLDataSource ds = null;
    private List<File> classIconFiles;

    private static List<File> listClassIconFiles(File dir) {
        List<File> fileTree = new ArrayList<>();
        if (dir == null || dir.listFiles() == null) {
            return fileTree;
        }
        for (File entry : dir.listFiles()) {
            if (entry.isFile()) {
                if (entry.getName().endsWith(".png") || entry.getName().endsWith(".jpg") || entry.getName().endsWith(".gif")) {
                    fileTree.add(entry);
                }
            } else {
                fileTree.addAll(listClassIconFiles(entry));
            }
        }
        return fileTree;
    }

    /**
     * Returns all known JEVisClasses as a JSON collection.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @return 200 OK with a collection of {@link org.jevis.commons.ws.json.JsonJEVisClass},
     *         401 UNAUTHORIZED, or 500 on error
     */
    @GET
    @Logged
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAll(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url) {
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            return getClassResponse(null);
        } catch (JEVisException jex) {
            logger.error(jex);
            return Response.serverError().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    /**
     * Returns the JEVisClass with the given name.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @param name        class name (e.g. {@code "Data"})
     * @return 200 OK with the {@link org.jevis.commons.ws.json.JsonJEVisClass},
     *         401 UNAUTHORIZED, 404 NOT FOUND if the class is unknown, or 500 on error
     */
    @GET
    @Logged
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{name}")
    public Response getJEVisClass(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @PathParam("name") String name) {

        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            return getClassResponse(name);

        } catch (JEVisException jex) {
            logger.catching(jex);
            return Response.serverError().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    /**
     * Deletes the JEVisClass definition file for the given class name, its icons and its
     * relationships in the files of other classes. Only system administrators may call this
     * endpoint.
     *
     * <p>The class is not deleted (409 CONFLICT, plain text message) as long as other classes
     * inherit from it or objects (also deleted ones) of the class exist.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @param name        class name to delete
     * @return 200 OK on success, 401 UNAUTHORIZED, 403 FORBIDDEN, 404 NOT FOUND, 409 CONFLICT, or 500 on error
     */
    @DELETE
    @Logged
    @Path("/{name}")
    @Produces(MediaType.TEXT_PLAIN)
    public Response deleteJEVisClass(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @PathParam("name") String name) {

        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            if (!ds.getUserManager().isSysAdmin()) {
                return Response.status(Response.Status.FORBIDDEN).entity("Only system administrators can delete classes.").build();
            }

            synchronized (ClassFileStore.LOCK) {
                ClassFileStore store = new ClassFileStore(Config.getClassDir());
                Map<String, File> index = store.index();
                File file = index.get(name);
                if (file == null) {
                    return Response.status(Response.Status.NOT_FOUND).entity("The class '" + name + "' does not exist.").build();
                }

                Set<String> heirs = ClassDefinitions.findDirectHeirs(name, Config.getClassCache().values());
                if (!heirs.isEmpty()) {
                    return Response.status(Response.Status.CONFLICT)
                            .entity("The class '" + name + "' cannot be deleted because other classes inherit from it: " + String.join(", ", heirs))
                            .build();
                }

                int objects = 0;
                List<JsonObject> allObjects = new ArrayList<>(ds.getObjects());
                allObjects.addAll(ds.getDeletedObjects());
                for (JsonObject object : allObjects) {
                    if (name.equals(object.getJevisClass())) {
                        objects++;
                    }
                }
                if (objects > 0) {
                    return Response.status(Response.Status.CONFLICT)
                            .entity("The class '" + name + "' cannot be deleted because " + objects + " objects (including deleted objects) use it.")
                            .build();
                }

                Files.delete(file.toPath());
                store.deleteIcons(name, null);
                List<String> changed = store.removeRelationshipsFromOtherFiles(index, name, Collections.emptySet());
                logger.info("Class '{}' deleted, relationships removed from: {}", name, changed);

                Config.reloadClassCache();
                new File(FileCache.CLASS_ICON_FILE).delete();
            }
            return Response.ok().build();

        } catch (JEVisException jex) {
            logger.catching(jex);
            return Response.serverError().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Error while deleting class '{}'", name, ex);
            return Response.serverError().entity(String.valueOf(ex.getMessage())).build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    /**
     * Creates or replaces the JEVisClass definition for the given name. Only system
     * administrators may call this endpoint.
     *
     * <p>The body is the complete class as the client knows it (as served by {@code GET}, i.e.
     * including inherited types and relationships stored in other class files). It is reduced to
     * the content of the class file by {@link ClassDefinitions#merge}: types inherited from the
     * super class are not stored; relationships of the class which are missing in the body are
     * also removed from the files of the other classes. New classes are stored next to the file
     * of their super class. The class cache is reloaded afterwards.
     *
     * <p>Renaming a class is not supported (the name in the path has to match the body). Own
     * types which are removed or whose primitive type changes are refused with 409 CONFLICT as
     * long as objects of the class or its heirs have samples in such an attribute.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @param name        class name to create or replace
     * @param input       JSON body of the {@link org.jevis.commons.ws.json.JsonJEVisClass}
     * @return 200 OK with the stored (completed) class, 400 BAD REQUEST, 401 UNAUTHORIZED,
     * 403 FORBIDDEN, 409 CONFLICT (plain text message), or 500 on error
     */
    @POST
    @Logged
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{name}")
    public Response postJEVisClass(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @PathParam("name") String name,
            String input) {

        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            if (!ds.getUserManager().isSysAdmin()) {
                return textResponse(Response.Status.FORBIDDEN, "Only system administrators can change classes.");
            }

            JsonJEVisClass json;
            try {
                json = ClassDefinitions.parse(input == null ? new byte[0] : input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                return textResponse(Response.Status.BAD_REQUEST, "Invalid class definition: " + ex.getMessage());
            }
            if (json == null || !name.equals(json.getName())) {
                return textResponse(Response.Status.BAD_REQUEST, "The class name in the path and in the body differ; renaming a class is not supported.");
            }

            synchronized (ClassFileStore.LOCK) {
                ClassFileStore store = new ClassFileStore(Config.getClassDir());
                Map<String, File> index = store.index();
                File file = index.get(name);
                JsonJEVisClass stored = file != null ? store.read(file) : null;

                ClassDefinitions.MergeResult result = ClassDefinitions.merge(json, stored, Config.getClassCache());

                Set<String> checkTypes = new TreeSet<>(result.getRemovedTypes());
                checkTypes.addAll(result.getChangedPrimitiveTypes());
                if (stored != null && !checkTypes.isEmpty()) {
                    Map<String, Integer> used = findAttributesWithSamples(name, checkTypes);
                    if (!used.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Map.Entry<String, Integer> entry : used.entrySet()) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append("'").append(entry.getKey()).append("' (").append(entry.getValue()).append(" objects)");
                        }
                        return textResponse(Response.Status.CONFLICT,
                                "The following types cannot be deleted or get another primitive type because objects have values in them: " + sb);
                    }
                }

                if (file == null) {
                    File superFile = result.getSuperClass() != null ? index.get(result.getSuperClass()) : null;
                    file = store.newClassFile(name, superFile);
                    if (file.exists()) {
                        return textResponse(Response.Status.CONFLICT, "The file " + file.getName() + " already exists for another class.");
                    }
                }

                store.write(file, result.getPersisted());
                List<String> changed = store.removeRelationshipsFromOtherFiles(index, name, result.getInvolvingRelationships());
                logger.info("Class '{}' saved to {}, relationships removed from: {}", name, file, changed);

                Config.reloadClassCache();
            }
            return Response.ok(Config.getClassCache().get(name), MediaType.APPLICATION_JSON).build();

        } catch (ClassDefinitions.ClassDefinitionException ex) {
            return textResponse(Response.Status.fromStatusCode(ex.getStatus()), ex.getMessage());
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception jex) {
            logger.error("Error while saving class '{}'", name, jex);
            return textResponse(Response.Status.INTERNAL_SERVER_ERROR, "Error while saving the class: " + jex.getMessage());
        } finally {
            Config.CloseDS(ds);
        }
    }

    private static Response textResponse(Response.Status status, String message) {
        return Response.status(status).entity(message).type(MediaType.TEXT_PLAIN_TYPE.withCharset("UTF-8")).build();
    }

    /**
     * Counts, per type name, the objects (also deleted ones) of the class and its heirs which have
     * samples in an attribute of that name.
     */
    private Map<String, Integer> findAttributesWithSamples(String className, Set<String> typeNames) throws JEVisException {
        List<String> classes = new ArrayList<>();
        classes.add(className);
        JEVisClassHelper.findHeir(className, classes);
        Set<String> classSet = new HashSet<>(classes);

        Set<Long> objectIDs = new HashSet<>();
        List<JsonObject> allObjects = new ArrayList<>(ds.getObjects());
        allObjects.addAll(ds.getDeletedObjects());
        for (JsonObject object : allObjects) {
            if (classSet.contains(object.getJevisClass())) {
                objectIDs.add(object.getId());
            }
        }

        Map<String, Integer> result = new TreeMap<>();
        if (objectIDs.isEmpty()) {
            return result;
        }
        for (JsonAttribute attribute : ds.getAttributeTable().getAllAttributes()) {
            if (objectIDs.contains(attribute.getObjectID()) && typeNames.contains(attribute.getType())
                    && attribute.getSampleCount() > 0) {
                result.merge(attribute.getType(), 1, Integer::sum);
            }
        }
        return result;
    }

    /**
     * Returns the icon image for the requested JEVisClass.
     *
     * <p>Searches the configured class icon directory for a file whose base name (without extension)
     * matches {@code name} (case-insensitive). Falls back to {@code MissingPlaceholder.png} if no
     * match is found.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @param name        class name whose icon is requested
     * @return 200 OK with the image bytes, 401 UNAUTHORIZED, or 500 on error
     */
    @GET
    @Logged
    @Path("/{name}/icon")
    @Produces({"image/png", "image/jpg", "image/gif"})
    public Response getClassIcon(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @PathParam("name") String name
    ) {
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            if (classIconFiles == null) {
                classIconFiles = listClassIconFiles(Config.getClassDir());
            }

            for (File icon : classIconFiles) {
                int lastDot = icon.getName().lastIndexOf(".");
                if (name.equalsIgnoreCase(icon.getName().substring(0, lastDot))) {
                    return Response.ok(ImageIO.read(icon)).build();
                }
            }
            File placeholder = new File(Config.getClassDir(), "MissingPlaceholder.png");
            return Response.ok(ImageIO.read(placeholder)).build();


        } catch (JEVisException jex) {
            logger.fatal(jex);
            return Response.serverError().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (IOException ioex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ioex.getMessage()).build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    /**
     * Helper that builds the class response from the in-memory cache.
     *
     * @param classname if {@code null} or empty, returns all classes; otherwise returns the single
     *                  named class or 404 if not found
     * @return JAX-RS {@link Response}
     */
    public Response getClassResponse(String classname) {
        if (classname == null || classname.isEmpty()) {
            return Response.ok(Config.getClassCache().values()).build();
        } else {
            if (Config.getClassCache().containsKey(classname)) {
                return Response.ok(Config.getClassCache().get(classname)).build();
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        }
    }


    /**
     * Uploads a new icon image for the given JEVisClass.
     * Only system administrators may call this endpoint. The uploaded image is stored as a PNG and
     * the icon file cache is invalidated.
     *
     * @param httpHeaders HTTP headers (used for authentication)
     * @param request     JAX-RS request context
     * @param url         URI info context
     * @param name        class name for which to store the icon
     * @param imageBytes  raw image bytes (PNG, JPG, or GIF)
     * @return 200 OK on success, 401 UNAUTHORIZED, or 500 on error
     */
    @POST
    @Path("/{name}/icon")
    @Consumes({"image/png", "image/jpg", "image/gif"})
    public Response postClassIcon(
            @Context HttpHeaders httpHeaders,
            @Context Request request,
            @Context UriInfo url,
            @PathParam("name") String name, byte[] imageBytes) {

        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);

            if (!ds.getUserManager().isSysAdmin()) {
                return Response.status(Response.Status.FORBIDDEN).build();
            }

            try {
                if (ClassDefinitions.checkClassName(name) != null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity(ClassDefinitions.checkClassName(name)).build();
                }
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                if (img == null) {
                    return Response.status(Response.Status.BAD_REQUEST).entity("The icon is not a readable image.").build();
                }
                synchronized (ClassFileStore.LOCK) {
                    ClassFileStore store = new ClassFileStore(Config.getClassDir());
                    /* next to the class file, other icons of the class would shadow the new one */
                    File newFile = store.iconFile(name, store.index().get(name));
                    //TODO: maybe resize the image to an default size
                    ImageIO.write(img, "png", newFile);
                    store.deleteIcons(name, newFile);
                }

                //clean cache
                File tmpZipFile = new File(FileCache.CLASS_ICON_FILE);
                tmpZipFile.delete();

                return Response.status(Response.Status.OK).build();
            } catch (IOException ioex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ioex.getMessage()).build();
            } finally {
                Config.CloseDS(ds);
            }


        } catch (JEVisException jex) {
            logger.error("ClassIcon upload error: {}", jex);
            return Response.serverError().build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Error while uploading class icon {}", ex);
            return Response.serverError().entity(ex).build();
        } finally {
            Config.CloseDS(ds);
        }

    }

    /**
     * Jersey lifecycle callback — clears transient per-request state after the response is committed.
     */
    @PostConstruct
    public void postConstruct() {
        if (ds != null) {
            ds.clear();
            ds = null;
        }

    }

}
