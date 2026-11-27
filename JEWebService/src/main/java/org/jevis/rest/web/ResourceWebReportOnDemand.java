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
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.datetime.WorkDays;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.mail.ServerMail;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * R04 report on demand (docs/WEB-API.md "Report on demand"): the web app asks for a report of a chosen period; the
 * request is stored as a sample of "On Demand Request" at the report and JEReport ({@code OnDemandRunner}) renders it,
 * writes its progress to "On Demand Status" and the files to "On Demand Report" / "On Demand Report PDF" with the
 * request id as note.
 * <ul>
 *     <li>{@code GET reports/{id}/on-demand}: periods to choose from, the latest requests and their status</li>
 *     <li>{@code POST reports/{id}/on-demand} {@code {start, mail}}: a new request (write right on the report)</li>
 *     <li>{@code GET reports/{id}/on-demand/{requestId}/file?format=pdf|xlsx}: the file of a finished request</li>
 * </ul>
 */
@Path("/JEWebService/v1/web")
public class ResourceWebReportOnDemand {

    static final String ATTRIBUTE_REQUEST = "On Demand Request";
    static final String ATTRIBUTE_STATUS = "On Demand Status";
    static final String ATTRIBUTE_XLSX = "On Demand Report";
    static final String ATTRIBUTE_PDF = "On Demand Report PDF";
    static final String ATTRIBUTE_START_RECORD = "Start Record";
    static final String ATTRIBUTE_TIME_ZONE = "Time Zone";

    static final String PENDING = "PENDING";
    static final String RUNNING = "RUNNING";
    static final String DONE = "DONE";
    static final String FAILED = "FAILED";
    static final String EXPIRED = "EXPIRED";

    /** as OnDemandRunner: older requests are not run any more */
    static final int MAX_AGE_DAYS = 7;
    /** requests shown */
    static final int MAX_LISTED = 20;
    /** open (pending or running) requests per report */
    static final int MAX_OPEN = 3;
    /** periods offered */
    static final int PERIODS = 12;
    static final Set<String> SCHEDULES = new HashSet<>(Arrays.asList("DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY"));

    private static final Logger logger = LogManager.getLogger(ResourceWebReportOnDemand.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** data access of one request */
    interface Env {
        /** the report if the user may read it (not deleted, a report), else null */
        JsonObject report(long id) throws Exception;

        boolean canWrite(JsonObject report) throws Exception;

        /** latest value of the attribute or null */
        String latest(long id, String attribute) throws Exception;

        /** samples of the attribute from {@code from} on, oldest first */
        List<JsonSample> samples(long id, String attribute, DateTime from) throws Exception;

        void add(long id, String attribute, DateTime ts, String value) throws Exception;

        byte[] file(long id, String attribute, JsonSample sample) throws Exception;

        long userId();

        String userName();

        /** e-mail addresses of the user's account (the report is mailed to them) */
        List<String> mailAddresses();

        /** time zone of the site of the report (fallback when the report has no "Time Zone") */
        DateTimeZone siteZone(JsonObject report);
    }

    /** a client error with its HTTP status */
    static final class Problem extends Exception {
        final int status;

        Problem(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("reports/{id}/on-demand")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            return json(state(new SqlEnv(ds), id, DateTime.now()));
        } catch (Problem p) {
            return problem(p);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the on-demand requests of report {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @POST
    @Path("reports/{id}/on-demand")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response post(@Context HttpHeaders httpHeaders, @PathParam("id") long id, String body) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            Map<String, Object> request = request(new SqlEnv(ds), id, body, DateTime.now());
            ds.logUserAction(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, String.format("%s:%s report on demand %s", id, ATTRIBUTE_REQUEST, request.get("id")));
            return Response.status(Response.Status.CREATED).entity(MAPPER.writeValueAsString(request))
                    .type(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").build();
        } catch (Problem p) {
            return problem(p);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not request report {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("reports/{id}/on-demand/{requestId}/file")
    public Response file(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @PathParam("requestId") String requestId,
                         @QueryParam("format") String format, @QueryParam("inline") @DefaultValue("false") boolean inline) {
        String normalized;
        try {
            normalized = ResourceWebReports.format(format);
        } catch (IllegalArgumentException ex) {
            return problem(new Problem(400, ex.getMessage()));
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            Env env = new SqlEnv(ds);
            JsonObject report = env.report(id);
            if (report == null) throw new Problem(404, "Report not found");
            boolean pdf = ResourceWebReports.FORMAT_PDF.equals(normalized);
            String attribute = pdf ? ATTRIBUTE_PDF : ATTRIBUTE_XLSX;
            JsonSample sample = fileSample(env, id, attribute, normalized, requestId, DateTime.now());
            byte[] bytes = sample != null ? env.file(id, attribute, sample) : null;
            if (bytes == null) throw new Problem(404, "File not found");
            String name = sample.getValue() != null && !sample.getValue().trim().isEmpty() ? sample.getValue() : report.getName() + "." + normalized;
            boolean showInline = pdf && inline;
            Response.ResponseBuilder response = Response.ok(bytes).type(pdf ? ResourceWebReports.TYPE_PDF : ResourceWebReports.TYPE_XLSX)
                    .header("Content-Disposition", ResourceWebIso.contentDisposition(name, showInline))
                    .header("X-Content-Type-Options", "nosniff")
                    .header("Cache-Control", "private, no-store");
            if (!showInline) response.header("Content-Security-Policy", "default-src 'none'; sandbox");
            return response.build();
        } catch (Problem p) {
            return problem(p);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the on-demand file {} of report {}", requestId, id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- logic

    /** schedule of the report in upper case, null if it cannot be requested (custom or none) */
    static String schedule(Env env, long id) throws Exception {
        String schedule = env.latest(id, ResourceWebReports.ATTRIBUTE_SCHEDULE);
        if (schedule == null) return null;
        schedule = schedule.trim().toUpperCase(Locale.ROOT);
        return SCHEDULES.contains(schedule) ? schedule : null;
    }

    static DateTimeZone zone(Env env, JsonObject report) throws Exception {
        String zone = env.latest(report.getId(), ATTRIBUTE_TIME_ZONE);
        if (zone != null && !zone.trim().isEmpty()) {
            try {
                return DateTimeZone.forID(zone.trim());
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown time zone {} of report {}", zone, report.getId());
            }
        }
        return env.siteZone(report);
    }

    /** time of day the periods of the report start at (from "Start Record", e.g. a working day starting at 06:00) */
    static LocalTime dayStart(Env env, long id, DateTimeZone zone) throws Exception {
        String startRecord = env.latest(id, ATTRIBUTE_START_RECORD);
        if (startRecord == null || startRecord.trim().isEmpty()) return LocalTime.MIDNIGHT;
        try {
            return ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(startRecord.trim()).withZone(zone).toLocalTime();
        } catch (IllegalArgumentException e) {
            return LocalTime.MIDNIGHT;
        }
    }

    /** start of the period of the schedule that contains {@code time} */
    static DateTime periodStart(DateTime time, String schedule, LocalTime dayStart) {
        DateTime day = time.withTime(dayStart);
        if (day.isAfter(time)) day = day.minusDays(1);
        switch (schedule) {
            case "WEEKLY":
                return day.withDayOfWeek(1);
            case "MONTHLY":
                return day.withDayOfMonth(1);
            case "QUARTERLY":
                return day.withDayOfMonth(1).withMonthOfYear(((day.getMonthOfYear() - 1) / 3) * 3 + 1);
            case "YEARLY":
                return day.withDayOfYear(1);
            default:
                return day;
        }
    }

    static DateTime next(DateTime start, String schedule, int count) {
        switch (schedule) {
            case "WEEKLY":
                return start.plusWeeks(count);
            case "MONTHLY":
                return start.plusMonths(count);
            case "QUARTERLY":
                return start.plusMonths(3 * count);
            case "YEARLY":
                return start.plusYears(count);
            default:
                return start.plusDays(count);
        }
    }

    /** the current (incomplete) period and the {@value #PERIODS} periods before it, newest first */
    static List<Map<String, Object>> periods(String schedule, DateTimeZone zone, LocalTime dayStart, DateTime now) {
        List<Map<String, Object>> list = new ArrayList<>();
        DateTime current = periodStart(now.withZone(zone), schedule, dayStart);
        for (int i = 0; i <= PERIODS; i++) {
            DateTime start = next(current, schedule, -i);
            Map<String, Object> period = new LinkedHashMap<>();
            period.put("start", print(start));
            period.put("end", print(next(start, schedule, 1)));
            period.put("current", i == 0);
            list.add(period);
        }
        return list;
    }

    /** one stored request with its status */
    static final class Entry {
        String id;
        DateTime start;
        DateTime requestedAt;
        Long requestedBy;
        String requestedByName;
        boolean mail;
        String status = PENDING;
        String message;
        DateTime statusAt;
        boolean pdf;
        boolean xlsx;
    }

    static List<Entry> entries(Env env, long id, DateTime now) throws Exception {
        DateTime from = now.minusDays(MAX_AGE_DAYS * 4);
        Map<String, Entry> byId = new LinkedHashMap<>();
        for (JsonSample sample : env.samples(id, ATTRIBUTE_REQUEST, from)) {
            try {
                JsonNode node = MAPPER.readTree(sample.getValue());
                if (node == null || !node.hasNonNull("id") || !node.hasNonNull("start")) continue;
                Entry entry = new Entry();
                entry.id = node.get("id").asText();
                entry.start = ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(node.get("start").asText());
                entry.requestedAt = time(sample.getTs());
                entry.requestedBy = node.hasNonNull("requestedBy") ? node.get("requestedBy").asLong() : null;
                entry.requestedByName = node.hasNonNull("requestedByName") ? node.get("requestedByName").asText() : null;
                entry.mail = node.path("mail").asBoolean(false);
                byId.put(entry.id, entry);
            } catch (Exception e) {
                /* not a request */
            }
        }
        for (JsonSample sample : env.samples(id, ATTRIBUTE_STATUS, from)) {
            try {
                JsonNode node = MAPPER.readTree(sample.getValue());
                Entry entry = node != null && node.hasNonNull("id") ? byId.get(node.get("id").asText()) : null;
                if (entry == null) continue;
                entry.status = node.path("status").asText(PENDING);
                entry.message = node.hasNonNull("message") ? node.get("message").asText() : null;
                entry.statusAt = time(sample.getTs());
                entry.pdf = node.hasNonNull("pdf");
                entry.xlsx = node.hasNonNull("xlsx");
            } catch (Exception e) {
                /* not a status */
            }
        }
        for (Entry entry : byId.values()) {
            if (PENDING.equals(entry.status) && entry.requestedAt != null && entry.requestedAt.isBefore(now.minusDays(MAX_AGE_DAYS))) {
                entry.status = EXPIRED;
            }
        }
        List<Entry> list = new ArrayList<>(byId.values());
        list.sort(Comparator.comparing((Entry e) -> e.requestedAt == null ? new DateTime(0) : e.requestedAt).reversed());
        return list;
    }

    /** {@code GET}: periods, requests, rights */
    static Map<String, Object> state(Env env, long id, DateTime now) throws Exception {
        JsonObject report = env.report(id);
        if (report == null) throw new Problem(404, "Report not found");
        String schedule = schedule(env, id);
        DateTimeZone zone = zone(env, report);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("name", report.getName());
        result.put("schedule", schedule);
        result.put("timeZone", zone.getID());
        boolean canWrite = env.canWrite(report);
        result.put("canRequest", canWrite && schedule != null);
        result.put("reason", schedule == null ? "schedule" : !canWrite ? "rights" : null);
        result.put("periods", schedule != null ? periods(schedule, zone, dayStart(env, id, zone), now) : Collections.emptyList());
        List<Map<String, Object>> requests = new ArrayList<>();
        for (Entry entry : entries(env, id, now)) {
            if (requests.size() >= MAX_LISTED) break;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", entry.id);
            json.put("start", print(entry.start.withZone(zone)));
            json.put("end", schedule != null ? print(next(entry.start.withZone(zone), schedule, 1)) : null);
            json.put("requestedAt", entry.requestedAt != null ? print(entry.requestedAt.withZone(zone)) : null);
            json.put("requestedByName", entry.requestedByName);
            json.put("mine", entry.requestedBy != null && entry.requestedBy == env.userId());
            json.put("mail", entry.mail);
            json.put("status", entry.status);
            json.put("message", entry.message);
            json.put("statusAt", entry.statusAt != null ? print(entry.statusAt.withZone(zone)) : null);
            json.put("pdf", entry.pdf);
            json.put("xlsx", entry.xlsx);
            requests.add(json);
        }
        result.put("requests", requests);
        return result;
    }

    /** {@code POST}: stores a request, the answer is the request as listed */
    static Map<String, Object> request(Env env, long id, String body, DateTime now) throws Exception {
        JsonObject report = env.report(id);
        if (report == null) throw new Problem(404, "Report not found");
        if (!env.canWrite(report)) throw new Problem(403, "Not allowed to request this report");
        String schedule = schedule(env, id);
        if (schedule == null) throw new Problem(400, "The report has no schedule that can be requested");
        JsonNode node;
        try {
            node = body == null || body.trim().isEmpty() ? null : MAPPER.readTree(body);
        } catch (Exception e) {
            throw new Problem(400, "Invalid JSON");
        }
        if (node == null || !node.hasNonNull("start")) throw new Problem(400, "Missing start");
        DateTimeZone zone = zone(env, report);
        DateTime start;
        try {
            start = ISODateTimeFormat.dateTimeParser().withOffsetParsed().parseDateTime(node.get("start").asText()).withZone(zone);
        } catch (IllegalArgumentException e) {
            throw new Problem(400, "Invalid start");
        }
        LocalTime dayStart = dayStart(env, id, zone);
        if (!periodStart(start, schedule, dayStart).equals(start)) throw new Problem(400, "start is not the start of a period");
        if (start.isAfter(now)) throw new Problem(400, "start is in the future");
        int open = 0;
        for (Entry entry : entries(env, id, now)) {
            if (PENDING.equals(entry.status) || RUNNING.equals(entry.status)) open++;
        }
        if (open >= MAX_OPEN) throw new Problem(429, "Too many open requests for this report");

        boolean mail = node.path("mail").asBoolean(false);
        List<String> mailTo = mail ? env.mailAddresses() : Collections.emptyList();
        if (mail && mailTo.isEmpty()) throw new Problem(400, "No e-mail address in your user account");

        String requestId = UUID.randomUUID().toString();
        ObjectNode json = MAPPER.createObjectNode();
        json.put("id", requestId);
        json.put("start", print(start));
        json.put("requestedBy", env.userId());
        json.put("requestedByName", env.userName());
        json.put("mail", mail);
        /* the address is resolved here: the JEReport service user may not read user accounts */
        if (mail) mailTo.forEach(json.putArray("mailTo")::add);
        env.add(id, ATTRIBUTE_REQUEST, now, MAPPER.writeValueAsString(json));

        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("id", requestId);
        answer.put("start", print(start));
        answer.put("end", print(next(start, schedule, 1)));
        answer.put("requestedAt", print(now.withZone(zone)));
        answer.put("requestedByName", env.userName());
        answer.put("mine", true);
        answer.put("mail", mail);
        answer.put("status", PENDING);
        answer.put("pdf", false);
        answer.put("xlsx", false);
        return answer;
    }

    /**
     * The file sample of the request, null if there is none. JEReport stores the request id as note, but the server
     * replaces the note of a file sample on upload ("Uploaded by …"); so the file is found by the name in the DONE
     * status of the request: the newest sample with that name written up to one minute after that status.
     *
     * @param format {@code pdf} or {@code xlsx} (the key of the file name in the status)
     */
    static JsonSample fileSample(Env env, long id, String attribute, String format, String requestId, DateTime now) throws Exception {
        if (requestId == null || requestId.trim().isEmpty()) return null;
        DateTime from = now.minusDays(MAX_AGE_DAYS * 4);
        List<JsonSample> files = env.samples(id, attribute, from);
        JsonSample found = null;
        for (JsonSample sample : files) {
            if (requestId.equals(sample.getNote())) found = sample;
        }
        if (found != null) return found;

        String name = null;
        DateTime doneAt = null;
        for (JsonSample sample : env.samples(id, ATTRIBUTE_STATUS, from)) {
            try {
                JsonNode node = MAPPER.readTree(sample.getValue());
                if (node != null && requestId.equals(node.path("id").asText()) && DONE.equals(node.path("status").asText())
                        && node.hasNonNull(format)) {
                    name = node.get(format).asText();
                    doneAt = time(sample.getTs());
                }
            } catch (Exception e) {
                /* not a status */
            }
        }
        if (name == null || doneAt == null) return null;
        DateTime latest = doneAt.plusMinutes(1);
        for (JsonSample sample : files) {
            DateTime ts = time(sample.getTs());
            if (name.equals(sample.getValue()) && ts != null && !ts.isAfter(latest)) found = sample;
        }
        return found;
    }

    // ---------------------------------------------------------------- database

    static final class SqlEnv implements Env {
        private final SQLDataSource ds;
        private ResourceWebModules.Lookup lookup;

        SqlEnv(SQLDataSource ds) {
            this.ds = ds;
        }

        @Override
        public JsonObject report(long id) throws Exception {
            if (lookup == null) lookup = ResourceWebModules.lookup(ds);
            return ResourceWebReports.report(lookup, ResourceWebRegisters.Classes.fromCache(), id);
        }

        @Override
        public boolean canWrite(JsonObject report) throws Exception {
            return ds.getUserManager().canWrite(report);
        }

        @Override
        public String latest(long id, String attribute) throws Exception {
            JsonSample sample = ds.getLastSample(id, attribute);
            return sample != null ? sample.getValue() : null;
        }

        @Override
        public List<JsonSample> samples(long id, String attribute, DateTime from) throws Exception {
            return ds.getSamples(id, attribute, from, null, ResourceWebReports.FETCH_LIMIT);
        }

        @Override
        public void add(long id, String attribute, DateTime ts, String value) throws Exception {
            JsonSample sample = new JsonSample();
            sample.setTs(JsonFactory.sampleDTF.print(ts));
            sample.setValue(value);
            ds.setSamples(id, attribute, JEVisConstants.PrimitiveType.STRING, Collections.singletonList(sample));
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
            return ResourceWebAlarms.sampleFile(Config.getFileDir(), id, attribute, sample);
        }

        @Override
        public long userId() {
            return ds.getCurrentUser().getUserID();
        }

        @Override
        public String userName() {
            JEVisUserSQL user = ds.getCurrentUser();
            String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " " + (user.getLastName() != null ? user.getLastName() : "")).trim();
            return name.isEmpty() ? user.getAccountName() : name;
        }

        @Override
        public List<String> mailAddresses() {
            return ServerMail.addressesOfUser(ds, ds.getCurrentUser().getUserID());
        }

        @Override
        public DateTimeZone siteZone(JsonObject report) {
            try {
                return new WorkDays(ds, report).getDateTimeZone();
            } catch (Exception e) {
                return DateTimeZone.getDefault();
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private static DateTime time(String ts) {
        Long millis = ResourceWebNotes.millis(ts);
        return millis != null ? new DateTime(millis) : null;
    }

    private static String print(DateTime time) {
        return ISODateTimeFormat.dateTime().print(time);
    }

    private static Response problem(Problem p) {
        return Response.status(p.status).entity(p.getMessage()).type(MediaType.TEXT_PLAIN).header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON).header("Cache-Control", "no-store").build();
    }
}
