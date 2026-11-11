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

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.text.Collator;
import java.util.*;

/**
 * Reports for the web app, view only (see docs/WEB-API.md, W4.6), like the Control Center's "Report Plugin": JEReport
 * writes one file sample per run into "Last Report PDF" (PDF) and "Last Report" (XLSX); an XLSX belongs to the PDF
 * written within one minute of it.
 * <p>
 * Only reports ("Report" and subclasses) the user may read and that are not in the recycle bin are used. Only the files
 * of "Last Report PDF" and "Last Report" are given, never the template or any other attribute.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebReports {

    static final String CLASS_REPORT = "Report";
    static final String CLASS_PERIODIC_REPORT = "Periodic Report";
    static final String ATTRIBUTE_ENABLED = "Enabled";
    static final String ATTRIBUTE_SCHEDULE = "Schedule";
    static final String ATTRIBUTE_PDF = "Last Report PDF";
    static final String ATTRIBUTE_XLSX = "Last Report";
    static final String FORMAT_PDF = "pdf";
    static final String FORMAT_XLSX = "xlsx";
    static final String TYPE_PDF = "application/pdf";
    static final String TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /**
     * An XLSX belongs to the PDF written within this time of it (as {@code ReportPlugin.loadReport})
     */
    static final long XLSX_WINDOW_MILLIS = 60_000L;
    /**
     * Upper bound of the file samples read per attribute
     */
    static final int FETCH_LIMIT = 100000;

    private static final Logger logger = LogManager.getLogger(ResourceWebReports.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("reports")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reports(@Context HttpHeaders httpHeaders) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return json(reportsJson(lookup, ResourceWebRegisters.Classes.fromCache(), sqlStore(ds), DateTimeZone.getDefault()));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the reports", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("reports/{id}/files")
    @Produces(MediaType.APPLICATION_JSON)
    public Response files(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Map<String, Object> result = filesJson(lookup, ResourceWebRegisters.Classes.fromCache(), sqlStore(ds), id,
                    DateTimeZone.getDefault());
            return result != null ? json(result) : notFound("Report not found");
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the files of report {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("reports/{id}/file")
    public Response file(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                         @QueryParam("format") String format, @QueryParam("ts") String ts,
                         @QueryParam("inline") @DefaultValue("false") boolean inline) {
        String normalizedFormat;
        Long millis;
        try {
            normalizedFormat = format(format);
            millis = timestamp(ts);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return fileResponse(lookup, ResourceWebRegisters.Classes.fromCache(), sqlStore(ds), id, normalizedFormat,
                    millis, inline);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the {} file of report {}", format, id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- parameters

    /**
     * @return {@code pdf} (default) or {@code xlsx}
     * @throws IllegalArgumentException for other values
     */
    static String format(String format) {
        if (format == null || format.trim().isEmpty()) return FORMAT_PDF;
        String value = format.trim().toLowerCase(Locale.ROOT);
        if (FORMAT_PDF.equals(value) || FORMAT_XLSX.equals(value)) return value;
        throw new IllegalArgumentException("Invalid format");
    }

    /**
     * @return the epoch milliseconds of the ISO 8601 time stamp, null if not set (the newest file)
     * @throws IllegalArgumentException for an invalid time stamp
     */
    static Long timestamp(String ts) {
        if (ts == null || ts.trim().isEmpty()) return null;
        try {
            return ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(ts.trim()).getMillis();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid ts");
        }
    }

    // ---------------------------------------------------------------- data access

    /**
     * Samples and files of one request.
     */
    interface Store {
        /**
         * @return all samples of the attribute, oldest first (file samples: the file name, not the bytes)
         */
        List<JsonSample> all(long id, String attribute) throws Exception;

        /**
         * @return the latest sample of the attribute or null
         */
        JsonSample latest(long id, String attribute) throws Exception;

        /**
         * @return the sample of the attribute at the time or null
         */
        JsonSample at(long id, String attribute, long millis) throws Exception;

        /**
         * @return the content of a file sample, null if the file does not exist
         */
        byte[] file(long id, String attribute, JsonSample sample) throws Exception;
    }

    static Store sqlStore(SQLDataSource ds) {
        return new Store() {
            @Override
            public List<JsonSample> all(long id, String attribute) throws Exception {
                return ds.getSamples(id, attribute, null, null, FETCH_LIMIT);
            }

            @Override
            public JsonSample latest(long id, String attribute) throws Exception {
                return ds.getLastSample(id, attribute);
            }

            @Override
            public JsonSample at(long id, String attribute, long millis) throws Exception {
                DateTime time = new DateTime(millis);
                for (JsonSample sample : ds.getSamples(id, attribute, time, time, 10)) {
                    Long sampleMillis = ResourceWebNotes.millis(sample.getTs());
                    if (sampleMillis != null && sampleMillis == millis) return sample;
                }
                return null;
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return ResourceWebAlarms.sampleFile(Config.getFileDir(), id, attribute, sample);
            }
        };
    }

    // ---------------------------------------------------------------- reports

    /**
     * @return the readable reports ("Report" and subclasses) that are not in the recycle bin
     */
    static List<JsonObject> reports(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes) {
        return ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_REPORT));
    }

    /**
     * @return the report if the user may read it, it is not in the recycle bin and it is a report; else null
     */
    static JsonObject report(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        return classes.withHeirs(CLASS_REPORT).contains(object.getJevisClass()) ? object : null;
    }

    /**
     * One output of a report: a PDF sample and the XLSX sample written within one minute of it (or null).
     */
    static final class Output {
        final long millis;
        final String name;
        final Long xlsxMillis;

        Output(long millis, String name, Long xlsxMillis) {
            this.millis = millis;
            this.name = name;
            this.xlsxMillis = xlsxMillis;
        }
    }

    /**
     * @return the outputs of the report, newest first; samples without valid time stamp are skipped
     */
    static List<Output> outputs(long id, Store store) throws Exception {
        TreeMap<Long, JsonSample> xlsxByTime = new TreeMap<>();
        for (JsonSample sample : store.all(id, ATTRIBUTE_XLSX)) {
            Long millis = ResourceWebNotes.millis(sample.getTs());
            if (millis != null) xlsxByTime.put(millis, sample);
        }
        List<Output> outputs = new ArrayList<>();
        for (JsonSample sample : store.all(id, ATTRIBUTE_PDF)) {
            Long millis = ResourceWebNotes.millis(sample.getTs());
            if (millis == null) continue;
            outputs.add(new Output(millis, sample.getValue(), matchingXlsx(xlsxByTime, millis)));
        }
        outputs.sort(Comparator.comparingLong((Output o) -> o.millis).reversed());
        return outputs;
    }

    /**
     * Like {@code ReportPlugin.loadReport}: the first XLSX from one minute before the PDF on, if it is less than one
     * minute after the PDF.
     *
     * @return the time of the matching XLSX or null
     */
    static Long matchingXlsx(TreeMap<Long, JsonSample> xlsxByTime, long pdfMillis) {
        Long key = xlsxByTime.ceilingKey(pdfMillis - XLSX_WINDOW_MILLIS);
        return key != null && key < pdfMillis + XLSX_WINDOW_MILLIS ? key : null;
    }

    /**
     * {@code GET /reports}: the readable reports, sorted by site, path and name.
     */
    static Map<String, Object> reportsJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                           Store store, DateTimeZone zone) {
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        Set<String> periodicClasses = classes.withHeirs(CLASS_PERIODIC_REPORT);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject report : reports(lookup, classes)) {
            long id = report.getId();
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", id);
            json.put("name", report.getName());
            json.put("className", report.getJevisClass());
            json.put("path", ResourceWebNotes.path(report, lookup, buildingClasses));
            json.put("site", ResourceWebNotes.siteJson(report, lookup, buildingClasses));
            json.put("enabled", ResourceWebAlarms.isTrue(latest(id, ATTRIBUTE_ENABLED, store)));
            JsonSample schedule = periodicClasses.contains(report.getJevisClass())
                    ? latest(id, ATTRIBUTE_SCHEDULE, store) : null;
            json.put("schedule", schedule != null && schedule.getValue() != null && !schedule.getValue().trim().isEmpty()
                    ? schedule.getValue() : null);
            List<Output> outputs;
            try {
                outputs = outputs(id, store);
            } catch (Exception e) {
                logger.warn("Could not read the outputs of report {}", id, e);
                outputs = Collections.emptyList();
            }
            Map<String, Object> latest = null;
            if (!outputs.isEmpty()) {
                Output newest = outputs.get(0);
                latest = new LinkedHashMap<>();
                latest.put("timestamp", print(newest.millis, zone));
                latest.put("pdf", true);
                latest.put("xlsx", newest.xlsxMillis != null);
                latest.put("name", newest.name);
            }
            json.put("latest", latest);
            json.put("count", outputs.size());
            list.add(json);
        }
        list.sort(order());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reports", list);
        return result;
    }

    /**
     * By site name (reports without site last), path (without path first) and name; case-insensitive, locale-aware.
     */
    @SuppressWarnings("unchecked")
    static Comparator<Map<String, Object>> order() {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        collator.setStrength(Collator.SECONDARY);
        Comparator<String> text = Comparator.nullsFirst((a, b) -> collator.compare(a, b));
        return Comparator.comparing((Map<String, Object> r) -> {
                    Map<String, Object> site = (Map<String, Object>) r.get("site");
                    return site != null ? (String) site.get("name") : null;
                }, Comparator.nullsLast((String a, String b) -> collator.compare(a, b)))
                .thenComparing(r -> (String) r.get("path"), text)
                .thenComparing(r -> (String) r.get("name"), text)
                .thenComparingLong(r -> (Long) r.get("id"));
    }

    /**
     * {@code GET /reports/{id}/files}: all outputs of the report, newest first; null if the report is not readable.
     */
    static Map<String, Object> filesJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                         Store store, long id, DateTimeZone zone) throws Exception {
        JsonObject report = report(lookup, classes, id);
        if (report == null) return null;
        List<Map<String, Object>> files = new ArrayList<>();
        for (Output output : outputs(id, store)) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("timestamp", print(output.millis, zone));
            json.put("name", output.name);
            json.put("xlsx", output.xlsxMillis != null ? print(output.xlsxMillis, zone) : null);
            files.add(json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", report.getName());
        result.put("files", files);
        return result;
    }

    /**
     * {@code GET /reports/{id}/file}: the bytes of one output (only "Last Report PDF" or "Last Report").
     *
     * @param format {@code pdf} or {@code xlsx}
     * @param millis time of the sample, null for the newest
     */
    static Response fileResponse(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, Store store,
                                 long id, String format, Long millis, boolean inline) throws Exception {
        JsonObject report = report(lookup, classes, id);
        if (report == null) return notFound("Report not found");
        boolean pdf = FORMAT_PDF.equals(format);
        String attribute = pdf ? ATTRIBUTE_PDF : ATTRIBUTE_XLSX;
        JsonSample sample = millis != null ? store.at(id, attribute, millis) : store.latest(id, attribute);
        byte[] bytes = sample != null ? store.file(id, attribute, sample) : null;
        if (bytes == null) return notFound("File not found");
        String name = sample.getValue();
        if (name == null || name.trim().isEmpty()) name = report.getName() + "." + format;
        boolean showInline = pdf && inline;
        Response.ResponseBuilder response = Response.ok(bytes).type(pdf ? TYPE_PDF : TYPE_XLSX)
                .header("Content-Disposition", ResourceWebIso.contentDisposition(name, showInline))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store");
        /* a sandbox would keep the browsers' PDF viewers from showing an inline PDF */
        if (!showInline) response.header("Content-Security-Policy", "default-src 'none'; sandbox");
        return response.build();
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @param readable the objects the user may read
     * @return {@code reports}: number of readable reports (not in the recycle bin)
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable, ResourceWebRegisters.Classes classes) {
        Set<String> reportClasses = classes.withHeirs(CLASS_REPORT);
        int reports = 0;
        for (JsonObject object : readable) {
            if (reportClasses.contains(object.getJevisClass()) && !ResourceWebIso.isDeleted(object)) reports++;
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("reports", reports);
        return modules;
    }

    // ---------------------------------------------------------------- helpers

    private static JsonSample latest(long id, String attribute, Store store) {
        try {
            return store.latest(id, attribute);
        } catch (Exception e) {
            logger.warn("Could not read {} of {}", attribute, id, e);
            return null;
        }
    }

    private static String print(long millis, DateTimeZone zone) {
        return ISODateTimeFormat.dateTime().print(new DateTime(millis, zone));
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }
}
