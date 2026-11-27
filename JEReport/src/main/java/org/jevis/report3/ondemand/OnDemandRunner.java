/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.ondemand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.ws.json.JsonMailRequest;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.report3.data.report.ReportAttributes;
import org.jevis.report3.data.report.ReportExecutor;
import org.joda.time.DateTime;

import java.util.*;
import java.util.function.Function;

/**
 * R04 report on demand (docs/REPORTS-ENMS.md "Report on demand"): the web service stores a request as one sample of
 * "On Demand Request" at the report ({@code {id, start, requestedBy, requestedByName, mail}}); every
 * {@value #POLL_SECONDS} s this runner renders the open ones ({@link ReportExecutor#executeOnDemand}) and writes their
 * progress as samples of "On Demand Status" ({@code {id, status RUNNING|DONE|FAILED, message, xlsx, pdf, at}}).
 */
public final class OnDemandRunner {

    public static final String REQUEST = "On Demand Request";
    public static final String STATUS = "On Demand Status";
    public static final String RUNNING = "RUNNING";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final int POLL_SECONDS = 30;
    /** requests older than this are not run any more */
    static final int MAX_AGE_DAYS = 7;
    /** a run still RUNNING after this time was interrupted (service restart) */
    static final int STALE_MINUTES = 30;

    private static final Logger logger = LogManager.getLogger(OnDemandRunner.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JEVisDataSource ds;
    private final Function<JEVisObject, ReportExecutor> executors;

    public OnDemandRunner(JEVisDataSource ds, Function<JEVisObject, ReportExecutor> executors) {
        this.ds = ds;
        this.executors = executors;
    }

    /** a request as stored */
    public static final class Request {
        public final String id;
        public final DateTime start;
        public final Long requestedBy;
        public final boolean mail;
        /** addresses of the requesting user, resolved by the web service (the service user may not read users) */
        public final List<String> mailTo;
        public final DateTime requestedAt;

        Request(String id, DateTime start, Long requestedBy, boolean mail, List<String> mailTo, DateTime requestedAt) {
            this.id = id;
            this.start = start;
            this.requestedBy = requestedBy;
            this.mail = mail;
            this.mailTo = mailTo;
            this.requestedAt = requestedAt;
        }
    }

    /** a request sample, null if not readable */
    static Request request(String json, DateTime sampleTime) {
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.hasNonNull("id") || !node.hasNonNull("start")) return null;
            List<String> mailTo = new ArrayList<>();
            for (JsonNode address : node.path("mailTo")) {
                if (address.isTextual() && address.asText().contains("@")) mailTo.add(address.asText());
            }
            return new Request(node.get("id").asText(), new DateTime(node.get("start").asText()),
                    node.hasNonNull("requestedBy") ? node.get("requestedBy").asLong() : null,
                    node.path("mail").asBoolean(false), mailTo, sampleTime);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * @param statuses id → latest status and its time
     * @return the requests to run now: no status yet, or still RUNNING after {@value #STALE_MINUTES} minutes; not older
     * than {@value #MAX_AGE_DAYS} days; oldest first
     */
    static List<Request> pending(List<Request> requests, Map<String, Object[]> statuses, DateTime now) {
        List<Request> list = new ArrayList<>();
        for (Request r : requests) {
            if (r.requestedAt != null && r.requestedAt.isBefore(now.minusDays(MAX_AGE_DAYS))) continue;
            Object[] status = statuses.get(r.id);
            if (status == null) list.add(r);
            else if (RUNNING.equals(status[0]) && ((DateTime) status[1]).isBefore(now.minusMinutes(STALE_MINUTES))) list.add(r);
        }
        list.sort(Comparator.comparing(r -> r.requestedAt == null ? new DateTime(0) : r.requestedAt));
        return list;
    }

    /** checks all reports for open requests and runs them one after another */
    public void poll() {
        try {
            JEVisClass reportClass = ds.getJEVisClass(ReportAttributes.NAME);
            for (JEVisObject report : ds.getObjects(reportClass, true)) {
                JEVisAttribute requests = report.getAttribute(REQUEST);
                JEVisAttribute statuses = report.getAttribute(STATUS);
                if (requests == null || statuses == null) continue;
                ds.reloadAttribute(requests);
                if (!requests.hasSample()) continue;
                ds.reloadAttribute(statuses);
                DateTime now = new DateTime();
                List<Request> list = new ArrayList<>();
                for (JEVisSample s : requests.getSamples(now.minusDays(MAX_AGE_DAYS), now.plusMinutes(1))) {
                    Request r = request(s.getValueAsString(), s.getTimestamp());
                    if (r != null) list.add(r);
                }
                if (list.isEmpty()) continue;
                Map<String, Object[]> latest = new HashMap<>();
                for (JEVisSample s : statuses.getSamples(now.minusDays(MAX_AGE_DAYS + 1), now.plusMinutes(1))) {
                    try {
                        JsonNode node = MAPPER.readTree(s.getValueAsString());
                        if (node != null && node.hasNonNull("id")) latest.put(node.get("id").asText(), new Object[]{node.path("status").asText(), s.getTimestamp()});
                    } catch (Exception e) {
                        /* not a status */
                    }
                }
                for (Request r : pending(list, latest, now)) run(report, statuses, r);
            }
        } catch (Exception e) {
            logger.error("Polling the on-demand report requests failed", e);
        }
    }

    private void run(JEVisObject report, JEVisAttribute statuses, Request request) {
        logger.info("On-demand run {} of report {} from {}", request.id, report.getID(), request.start);
        status(statuses, request.id, RUNNING, null, null);
        try {
            ReportExecutor.OnDemandFiles files = executors.apply(report).executeOnDemand(request.start, request.id);
            status(statuses, request.id, DONE, null, files);
            if (request.mail && (!request.mailTo.isEmpty() || request.requestedBy != null)) mail(report, request, files);
        } catch (Exception e) {
            logger.error("On-demand run {} of report {} failed", request.id, report.getID(), e);
            status(statuses, request.id, FAILED, e.getMessage() != null ? e.getMessage() : e.toString(), null);
        }
    }

    private void status(JEVisAttribute statuses, String id, String status, String message, ReportExecutor.OnDemandFiles files) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("id", id);
            node.put("status", status);
            if (message != null) node.put("message", message.length() > 500 ? message.substring(0, 500) : message);
            if (files != null && files.xlsx != null) node.put("xlsx", files.xlsx.getFilename());
            if (files != null && files.pdf != null) node.put("pdf", files.pdf.getFilename());
            DateTime now = new DateTime();
            node.put("at", now.toString());
            statuses.buildSample(now, MAPPER.writeValueAsString(node)).commit();
        } catch (Exception e) {
            logger.error("Could not write the status of on-demand run {}", id, e);
        }
    }

    private void mail(JEVisObject report, Request request, ReportExecutor.OnDemandFiles files) {
        if (!(ds instanceof JEVisDataSourceWS)) return;
        try {
            JEVisFile file = files.pdf != null ? files.pdf : files.xlsx;
            JsonMailRequest mail = new JsonMailRequest();
            if (!request.mailTo.isEmpty()) mail.setTo(request.mailTo);
            else mail.setUserIds(Collections.singletonList(request.requestedBy));
            mail.setSubject("Bericht " + report.getName());
            mail.setText("Der angeforderte Bericht „" + report.getName() + "“ ab " + request.start.toString("dd.MM.yyyy")
                    + " ist angehängt.\n\nErstellt auf Anforderung in der JEVis Web-App.");
            String type = file.getFilename().endsWith(".pdf") ? "application/pdf" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            mail.setAttachments(Collections.singletonList(new JsonMailRequest.Attachment(file.getFilename(), type,
                    Base64.getEncoder().encodeToString(file.getBytes()))));
            ((JEVisDataSourceWS) ds).sendMail(mail);
        } catch (Exception e) {
            logger.error("Could not mail on-demand run {} of report {}", request.id, report.getID(), e);
        }
    }
}
