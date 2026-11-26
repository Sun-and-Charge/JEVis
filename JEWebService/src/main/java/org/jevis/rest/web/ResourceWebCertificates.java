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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.certificate.CertificateLogic;
import org.jevis.commons.certificate.CertificateState;
import org.jevis.commons.certificate.EnergyCertificate;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import javax.security.sasl.AuthenticationException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Energy certificates of the buildings (tender point BA-07, docs/ENERGY-CERTIFICATES.md): overview, create, change,
 * delete, the PDF, and "transfer now". Same rules as the other write endpoints (stage E1): the user's own rights,
 * {@code version} + {@code 409}, user action log.
 */
@Path("/JEWebService/v1/web/certificates")
public class ResourceWebCertificates {

    static final String CLASS_BUILDING = "Building";
    static final String CLASS_TRANSFER = "Energy Certificate Transfer";
    static final String ATTRIBUTE_REQUESTED = "Requested";
    static final String ATTRIBUTE_LAST_TRANSFER = "Last Transfer";
    static final int MAX_FILE_SIZE = 20 * 1024 * 1024;
    static final String ACTION = "certificate";

    private static final Logger logger = LogManager.getLogger(ResourceWebCertificates.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Object CREATE_LOCK = new Object();
    private static final List<String> TRANSFER_SHOWN = Arrays.asList("Enabled", "Channel", "Format", "Schedule");

    // ---------------------------------------------------------------- endpoints

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @QueryParam("site") Long site) {
        return WebEdit.run(httpHeaders, "load the energy certificates",
                store -> WebEdit.Reply.ok(list(store, site, LocalDate.now())));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, String body) {
        return WebEdit.run(httpHeaders, "create an energy certificate", store -> create(store, body, DateTime.now()));
    }

    @PUT
    @Path("{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        return WebEdit.run(httpHeaders, "change the energy certificate " + id, store -> update(store, id, body, DateTime.now()));
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("version") String version) {
        return WebEdit.run(httpHeaders, "delete the energy certificate " + id, store -> delete(store, id, version));
    }

    @POST
    @Path("{id}/file")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response upload(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "store the PDF of the energy certificate " + id, store -> {
            String name = file != null && file.getContentDisposition() != null
                    ? ResourceWebEditIso.headerFileName(file.getContentDisposition().getFileName()) : null;
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return upload(store, id, name, in, MAX_FILE_SIZE, DateTime.now());
            }
        });
    }

    @GET
    @Path("{id}/file")
    public Response download(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            WebEdit.Store store = WebEdit.sqlStore(ds);
            JsonObject certificate = certificate(store, id);
            JsonSample sample = certificate != null ? store.latest(id, EnergyCertificate.ATTRIBUTE_FILE) : null;
            byte[] bytes = sample != null ? store.file(id, EnergyCertificate.ATTRIBUTE_FILE, sample) : null;
            if (bytes == null) return Response.status(404).entity("File not found").type(MediaType.TEXT_PLAIN).build();
            String name = sample.getValue();
            return Response.ok(bytes, "application/pdf")
                    .header("Content-Disposition", "inline; filename*=UTF-8''"
                            + URLEncoder.encode(name, StandardCharsets.UTF_8.name()).replace("+", "%20"))
                    .header("Cache-Control", "no-store").build();
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the PDF of the energy certificate {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @DELETE
    @Path("{id}/file")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteFile(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        return WebEdit.run(httpHeaders, "delete the PDF of the energy certificate " + id, store -> deleteFile(store, id));
    }

    @POST
    @Path("transfers/{id}/run")
    @Produces(MediaType.APPLICATION_JSON)
    public Response runTransfer(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        return WebEdit.run(httpHeaders, "request the transfer " + id, store -> requestTransfer(store, id, DateTime.now()));
    }

    // ---------------------------------------------------------------- reading

    /**
     * {@code GET /web/certificates}: certificates, the buildings to create in and the transfer targets
     */
    static Map<String, Object> list(WebEdit.Store store, Long site, LocalDate today) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        WebEdit.Rights rights = store.rights();
        List<Map<String, Object>> certificates = new ArrayList<>();
        for (JsonObject object : lookup.readableOfClass(EnergyCertificate.CLASS_NAME)) {
            if (ResourceWebIso.isDeleted(object) || !inSite(lookup, object, site)) continue;
            certificates.add(certificateJson(store, object, today));
        }
        certificates.sort(Comparator.comparing((Map<String, Object> c) -> (String) c.get("validUntil"),
                Comparator.nullsLast(Comparator.<String>naturalOrder())).thenComparing(c -> String.valueOf(c.get("name"))));

        List<Map<String, Object>> buildings = new ArrayList<>();
        for (JsonObject building : lookup.readableOfClass(CLASS_BUILDING)) {
            if (ResourceWebIso.isDeleted(building) || !inSite(lookup, building, site)) continue;
            JsonObject directory = directory(lookup, building);
            boolean canCreate = directory != null
                    ? rights.canCreate(directory, EnergyCertificate.CLASS_NAME)
                    : rights.canCreate(building, EnergyCertificate.DIRECTORY_CLASS_NAME);
            Map<String, Object> entry = new LinkedHashMap<>(ResourceWebNavigation.entry(building));
            entry.put("directory", directory != null ? directory.getId() : null);
            entry.put("canCreate", canCreate);
            buildings.add(entry);
        }
        buildings.sort(Comparator.comparing(b -> String.valueOf(b.get("name")).toLowerCase(Locale.ROOT)));

        List<Map<String, Object>> transfers = new ArrayList<>();
        for (JsonObject object : lookup.readableOfClass(CLASS_TRANSFER)) {
            if (ResourceWebIso.isDeleted(object) || !inSite(lookup, object, site)) continue;
            transfers.add(transferJson(store, object));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today.toString());
        result.put("certificates", certificates);
        result.put("buildings", buildings);
        result.put("transfers", transfers);
        result.put("people", ResourceWebEditAlarms.peopleOptions(store, lookup));
        return result;
    }

    static Map<String, Object> certificateJson(WebEdit.Store store, JsonObject object, LocalDate today) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        JsonSample data = store.latest(object.getId(), EnergyCertificate.ATTRIBUTE_DATA);
        EnergyCertificate c = CertificateLogic.fromJson(data != null ? data.getValue() : null);
        JsonSample state = store.latest(object.getId(), EnergyCertificate.ATTRIBUTE_TRANSFER_STATE);
        CertificateState s = CertificateState.fromJson(state != null ? state.getValue() : null);
        JsonSample file = store.latest(object.getId(), EnergyCertificate.ATTRIBUTE_FILE);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("site", lookup.site(object));
        json.put("building", building(lookup, object));
        json.put("directory", lookup.directory(object));
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = c != null ? MAPPER.readValue(CertificateLogic.toJson(c), LinkedHashMap.class)
                : new LinkedHashMap<>();
        if (c != null) fields.put("reminderMonths", c.getReminderMonths());
        json.put("data", fields);
        json.put("validUntil", c != null ? c.getValidUntil() : null);
        CertificateLogic.Status status = c != null ? CertificateLogic.status(c, today) : CertificateLogic.Status.UNKNOWN;
        json.put("status", status.name());
        LocalDate until = c != null ? CertificateLogic.date(c.getValidUntil()) : null;
        json.put("daysLeft", until != null ? Days.daysBetween(today, until).getDays() : null);
        json.put("responsible", lookup.recipients(c != null ? c.getResponsible() : Collections.<Long>emptyList()));
        if (file != null && file.getValue() != null) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", file.getValue());
            json.put("file", f);
        } else {
            json.put("file", null);
        }
        json.put("lastReminder", s.getLastReminder());
        json.put("reminderError", s.getLastError());
        Map<String, Object> transferred = new LinkedHashMap<>();
        for (Map.Entry<String, CertificateState.Transfer> t : s.getTransfers().entrySet()) {
            transferred.put(t.getKey(), t.getValue().getAt());
        }
        json.put("transfers", transferred);
        json.put("canWrite", store.rights().canWrite(object));
        json.put("canDelete", store.rights().canDelete(object));
        json.put("version", WebEdit.version(data));
        return json;
    }

    static Map<String, Object> transferJson(WebEdit.Store store, JsonObject object) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("site", store.lookup().site(object));
        for (String attribute : TRANSFER_SHOWN) {
            JsonSample sample = store.latest(object.getId(), attribute);
            String key = attribute.substring(0, 1).toLowerCase(Locale.ROOT) + attribute.substring(1);
            json.put(key, sample != null ? sample.getValue() : null);
        }
        JsonSample last = store.latest(object.getId(), ATTRIBUTE_LAST_TRANSFER);
        json.put("lastTransfer", jsonOrNull(last != null ? last.getValue() : null));
        JsonSample requested = store.latest(object.getId(), ATTRIBUTE_REQUESTED);
        json.put("requested", requested != null && requested.getValue() != null && !requested.getValue().isEmpty()
                ? jsonOrNull(requested.getValue()) : null);
        json.put("canRun", canRun(store.rights(), object));
        return json;
    }

    private static Object jsonOrNull(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            return text;
        }
    }

    // ---------------------------------------------------------------- writing

    /**
     * {@code POST /web/certificates} with {@code {building, data}}
     */
    static WebEdit.Reply create(WebEdit.Store store, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonNode buildingNode = json.get("building");
        if (buildingNode == null || !buildingNode.canConvertToLong()) return WebEdit.Reply.badRequest("Missing building");
        ResourceWebModules.Lookup lookup = store.lookup();
        JsonObject building = WebEdit.readable(store, buildingNode.asLong());
        if (building == null || !CLASS_BUILDING.equals(building.getJevisClass())) return WebEdit.Reply.notFound("Building not found");
        EnergyCertificate certificate = certificate(json.get("data"));
        checkPeople(lookup, certificate, Collections.<Long>emptyList());
        certificate.setCreated(now.toString("yyyy-MM-dd'T'HH:mm:ss"));
        certificate.setCreatedBy(store.userName());

        synchronized (CREATE_LOCK) {
            JsonObject directory = directory(lookup, building);
            if (directory != null ? !store.rights().canCreate(directory, EnergyCertificate.CLASS_NAME)
                    : !store.rights().canCreate(building, EnergyCertificate.DIRECTORY_CLASS_NAME)) {
                return WebEdit.Reply.forbidden("Not allowed to add energy certificates to this building");
            }
            String action = ACTION + "-create";
            JsonObject createdDirectory = null;
            JsonObject object = null;
            try {
                if (directory == null) {
                    createdDirectory = store.createObject("Energieausweise", EnergyCertificate.DIRECTORY_CLASS_NAME, building.getId());
                    store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(createdDirectory.getId(), createdDirectory.getName(), action));
                    lookup.addReadable(createdDirectory, building.getId());
                    directory = createdDirectory;
                }
                object = store.createObject(objectName(certificate), EnergyCertificate.CLASS_NAME, directory.getId());
                store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), action));
                lookup.addReadable(object, directory.getId());
                save(store, object, certificate, now, action);
            } catch (Exception e) {
                rollback(store, object, action);
                rollback(store, createdDirectory, action);
                throw e;
            }
            return WebEdit.Reply.created(certificateJson(store, object, now.toLocalDate()));
        }
    }

    /**
     * {@code PUT /web/certificates/{id}} with {@code {version, data}}: replaces the data; created/createdBy are kept
     */
    static WebEdit.Reply update(WebEdit.Store store, long id, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonObject object = certificate(store, id);
        if (object == null) return WebEdit.Reply.notFound("Certificate not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this certificate");
        String version = WebEdit.text(json, "version");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        EnergyCertificate certificate = certificate(json.get("data"));
        JsonSample data = store.latest(id, EnergyCertificate.ATTRIBUTE_DATA);
        if (!WebEdit.sameVersion(version, data)) return WebEdit.Reply.conflict(certificateJson(store, object, now.toLocalDate()));
        EnergyCertificate old = CertificateLogic.fromJson(data != null ? data.getValue() : null);
        checkPeople(store.lookup(), certificate, old != null ? old.getResponsible() : Collections.<Long>emptyList());
        if (old != null) {
            certificate.setCreated(old.getCreated());
            certificate.setCreatedBy(old.getCreatedBy());
        }
        save(store, object, certificate, now, ACTION + "-update");
        String name = objectName(certificate);
        if (!name.equals(object.getName())) {
            store.rename(object, name);
            object.setName(name);
        }
        return WebEdit.Reply.ok(certificateJson(store, object, now.toLocalDate()));
    }

    /**
     * {@code DELETE /web/certificates/{id}?version=}: to the recycle bin
     */
    static WebEdit.Reply delete(WebEdit.Store store, long id, String version) throws Exception {
        JsonObject object = certificate(store, id);
        if (object == null) return WebEdit.Reply.notFound("Certificate not found");
        if (!store.rights().canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this certificate");
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        if (!WebEdit.sameVersion(version, store.latest(id, EnergyCertificate.ATTRIBUTE_DATA))) {
            return WebEdit.Reply.conflict(certificateJson(store, object, LocalDate.now()));
        }
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION + "-delete"));
        return WebEdit.Reply.noContent();
    }

    /**
     * {@code POST /web/certificates/{id}/file}: a PDF as new sample of "File"
     */
    static WebEdit.Reply upload(WebEdit.Store store, long id, String fileName, InputStream in, int max, DateTime now)
            throws Exception {
        JsonObject object = certificate(store, id);
        if (object == null) return WebEdit.Reply.notFound("Certificate not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this certificate");
        if (in == null) return WebEdit.Reply.badRequest("Missing file");
        byte[] bytes = ResourceWebEditIso.read(in, max);
        if (bytes == null) return new WebEdit.Reply(413, "The file is larger than " + (max / (1024 * 1024)) + " MB");
        if (!isPdf(bytes)) return WebEdit.Reply.badRequest("The file is not a PDF");
        String name = ResourceWebEditIso.uploadName(fileName);
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name = name + ".pdf";
        DateTime ts = now.withMillisOfSecond(0);
        JsonSample latest = store.latest(id, EnergyCertificate.ATTRIBUTE_FILE);
        if (latest != null && ResourceWebNotes.millis(latest.getTs()) != null && ResourceWebNotes.millis(latest.getTs()) >= ts.getMillis()) {
            store.waitUntil(ts.plusSeconds(1));
            ts = ts.plusSeconds(1);
        }
        int count = store.writeFile(id, EnergyCertificate.ATTRIBUTE_FILE, ts, name, bytes);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(id, EnergyCertificate.ATTRIBUTE_FILE, count, ACTION + "-file"));
        return WebEdit.Reply.created(certificateJson(store, object, now.toLocalDate()));
    }

    /**
     * {@code DELETE /web/certificates/{id}/file}: removes all file samples (write right)
     */
    static WebEdit.Reply deleteFile(WebEdit.Store store, long id) throws Exception {
        JsonObject object = certificate(store, id);
        if (object == null) return WebEdit.Reply.notFound("Certificate not found");
        if (!store.rights().canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this certificate");
        WebEdit.deleteSamples(store, id, EnergyCertificate.ATTRIBUTE_FILE, new DateTime(0L, DateTimeZone.UTC),
                new DateTime(4102444800000L, DateTimeZone.UTC), ACTION + "-file-delete");
        return WebEdit.Reply.noContent();
    }

    /**
     * {@code POST /web/certificates/transfers/{id}/run}: sets "Requested"; the JENotifier sends in its next cycle
     */
    static WebEdit.Reply requestTransfer(WebEdit.Store store, long id, DateTime now) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        if (object == null || !CLASS_TRANSFER.equals(object.getJevisClass())) return WebEdit.Reply.notFound("Transfer not found");
        if (!canRun(store.rights(), object)) return WebEdit.Reply.forbidden("Not allowed to start this transfer");
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("at", now.withMillisOfSecond(0).toString());
        request.put("by", store.account());
        WebEdit.writeSample(store, id, ATTRIBUTE_REQUESTED, JEVisConstants.PrimitiveType.STRING,
                WebEdit.sample(now, MAPPER.writeValueAsString(request), null), ACTION + "-transfer-request");
        return new WebEdit.Reply(202, transferJson(store, object));
    }

    // ---------------------------------------------------------------- helpers

    static boolean canRun(WebEdit.Rights rights, JsonObject transfer) {
        return rights.canWrite(transfer) || rights.canExecute(transfer);
    }

    /**
     * @return the certificate from the {@code data} field
     * @throws WebEdit.Stop {@code 400} with {@code field:key} of the first problem ({@link CertificateLogic#validate})
     */
    static EnergyCertificate certificate(JsonNode data) throws Exception {
        if (data == null || !data.isObject()) throw WebEdit.Stop.badRequest("Missing data");
        EnergyCertificate certificate = CertificateLogic.fromJson(MAPPER.writeValueAsString(data));
        if (certificate == null) throw WebEdit.Stop.badRequest("Invalid data");
        certificate.setCreated(null);
        certificate.setCreatedBy(null);
        List<String[]> problems = CertificateLogic.validate(certificate);
        if (!problems.isEmpty()) throw WebEdit.Stop.badRequest(problems.get(0)[0] + ":" + problems.get(0)[1]);
        return certificate;
    }

    /**
     * Responsible persons must be users or groups; ids already stored are kept even if the user may not read them.
     *
     * @throws WebEdit.Stop {@code 400 responsible:person}
     */
    static void checkPeople(ResourceWebModules.Lookup lookup, EnergyCertificate certificate, List<Long> stored)
            throws WebEdit.Stop {
        List<Long> ids = new ArrayList<>();
        for (Long id : certificate.getResponsible()) {
            if (id == null || ids.contains(id)) continue;
            JsonObject person = lookup.readable(id);
            if (!stored.contains(id) && (person == null || ResourceWebReadingRounds.kind(person) == null)) {
                throw WebEdit.Stop.badRequest("responsible:person");
            }
            ids.add(id);
        }
        certificate.setResponsible(ids);
    }

    /**
     * @return a readable certificate outside the recycle bin, else null
     */
    static JsonObject certificate(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && EnergyCertificate.CLASS_NAME.equals(object.getJevisClass()) ? object : null;
    }

    private static void save(WebEdit.Store store, JsonObject object, EnergyCertificate certificate, DateTime now,
                             String action) throws Exception {
        DateTime ts = now.withMillisOfSecond(0);
        JsonSample latest = store.latest(object.getId(), EnergyCertificate.ATTRIBUTE_DATA);
        Long last = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
        /* one sample per second: a second save in the same second gets the next second */
        if (last != null && last >= ts.getMillis()) ts = new DateTime(last + 1000L, ts.getZone());
        WebEdit.writeSample(store, object.getId(), EnergyCertificate.ATTRIBUTE_DATA, JEVisConstants.PrimitiveType.STRING,
                WebEdit.sample(ts, CertificateLogic.toJson(certificate), store.account()), action);
    }

    /**
     * @return "Verbrauchsausweis 2017" / "Bedarfsausweis 2017" (object name; the web app shows its own labels)
     */
    static String objectName(EnergyCertificate c) {
        String type = c.getType() == EnergyCertificate.Type.DEMAND ? "Bedarfsausweis" : "Verbrauchsausweis";
        LocalDate issued = CertificateLogic.date(c.getIssued());
        return issued != null ? type + " " + issued.getYear() : type;
    }

    static boolean isPdf(byte[] bytes) {
        return bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
    }

    /**
     * @return the certificate directory of the building, null if none (readable, outside the recycle bin)
     */
    static JsonObject directory(ResourceWebModules.Lookup lookup, JsonObject building) {
        for (JsonObject child : lookup.readableChildren(building.getId(),
                Collections.singletonList(EnergyCertificate.DIRECTORY_CLASS_NAME))) {
            if (!ResourceWebIso.isDeleted(child)) return child;
        }
        return null;
    }

    /**
     * @return the nearest building above the object ({@code id, name, names}), null if none or not readable
     */
    static Map<String, Object> building(ResourceWebModules.Lookup lookup, JsonObject object) {
        Long id = lookup.parent(object.getId());
        for (int depth = 0; id != null && depth < 50; depth++) {
            JsonObject o = lookup.object(id);
            if (o == null) return null;
            if (CLASS_BUILDING.equals(o.getJevisClass())) {
                return lookup.readable(id) != null ? ResourceWebNavigation.entry(o) : null;
            }
            id = lookup.parent(id);
        }
        return null;
    }

    /**
     * @return whether the object lies below the site (always true without site)
     */
    static boolean inSite(ResourceWebModules.Lookup lookup, JsonObject object, Long site) {
        if (site == null) return true;
        Long id = object.getId();
        for (int depth = 0; id != null && depth < 50; depth++) {
            if (id.equals(site)) return true;
            id = lookup.parent(id);
        }
        return false;
    }

    /**
     * @return number of readable certificates outside the recycle bin, for the navigation
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable) {
        int count = 0;
        for (JsonObject object : readable) {
            if (EnergyCertificate.CLASS_NAME.equals(object.getJevisClass()) && !ResourceWebIso.isDeleted(object)) count++;
        }
        return Collections.singletonMap("certificates", count);
    }

    private static void rollback(WebEdit.Store store, JsonObject object, String action) {
        if (object == null) return;
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), action + "-rollback"));
        } catch (Exception e) {
            logger.error("Could not remove {} after a failed create", object.getId(), e);
        }
    }
}
