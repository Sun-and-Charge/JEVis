/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of EInvoiceParser.
 * <p>
 * EInvoiceParser is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * EInvoiceParser is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * EInvoiceParser. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * EInvoiceParser is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.einvoiceparser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.driver.*;
import org.jevis.commons.ws.json.JsonMailRequest;
import org.jevis.jeapi.ws.InvoiceRequestException;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * DataCollector parser "E-Invoice Parser" (R02): below any channel that fetches files (EMail, sFTP, FTP, HTTP, local
 * folder) it imports ZUGFeRD / Factur-X / XRechnung files as energy invoices ({@link InvoiceInbox}) instead of
 * producing measurements. Attributes: "Target Directory" (Energy Invoices Directory), "Default Medium", "Error
 * Recipients", "Processed Files" (kept by the parser), "Last Run" (summary of the last run).
 */
public class EInvoiceParser implements Parser {

    public static final String CLASS_NAME = "E-Invoice Parser";
    public static final String TARGET_DIRECTORY = "Target Directory";
    public static final String DEFAULT_MEDIUM = "Default Medium";
    public static final String ERROR_RECIPIENTS = "Error Recipients";
    public static final String PROCESSED_FILES = "Processed Files";
    public static final String LAST_RUN = "Last Run";
    static final int MAX_FILE = 20 * 1024 * 1024;

    private static final Logger logger = LogManager.getLogger(EInvoiceParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JEVisObject parserObject;
    private final ParserReport report = new ParserReport();

    @Override
    public void initialize(JEVisObject parserObject) {
        this.parserObject = parserObject;
    }

    @Override
    public void parse(List<InputStream> input, DateTimeZone timezone) {
        try {
            JEVisDataSource ds = parserObject.getDataSource();
            if (!(ds instanceof JEVisDataSourceWS)) {
                error("The E-Invoice Parser needs the web service data source (JEVisDataSourceWS)");
                return;
            }
            Long directory = target(latest(TARGET_DIRECTORY));
            if (directory == null) {
                error("No Target Directory set at E-Invoice Parser " + parserObject.getID());
                return;
            }
            List<byte[]> files = new ArrayList<>();
            for (InputStream in : input) {
                try {
                    files.add(read(in));
                } catch (IOException e) {
                    logger.error("Could not read an input of parser {}", parserObject.getID(), e);
                }
            }
            InvoiceInbox.Run run = InvoiceInbox.run(new WsServer((JEVisDataSourceWS) ds), files, directory,
                    latest(DEFAULT_MEDIUM), remembered(latest(PROCESSED_FILES)));
            logger.info("E-Invoice Parser {}: {}", parserObject.getID(), run.entries);

            write(PROCESSED_FILES, MAPPER.writeValueAsString(run.remembered));
            write(LAST_RUN, summary(run));
            JEVisObject channel = parserObject.getParents().isEmpty() ? null : parserObject.getParents().get(0);
            /* the data sources move their last readout only with measurements; files that must be fetched again
               (server not reachable, no rights) keep it where it was */
            if (channel != null && !run.needsRetry() && !files.isEmpty()) JEVisImporterAdapter.setLastReadout(channel, new DateTime());
            if (run.hasErrors()) {
                for (InvoiceInbox.Entry e : run.of(InvoiceInbox.ERROR)) report.addError(new LineError(0, 0, null, e.toString()));
                mail(ds, run, channel != null ? channel.getName() : null);
            }
        } catch (Exception e) {
            error("E-Invoice Parser " + (parserObject != null ? parserObject.getID() : "?") + " failed: " + e.getMessage());
            logger.error("E-Invoice Parser failed", e);
        }
    }

    @Override
    public List<Result> getResult() {
        /* invoices are imported directly, there are no measurements */
        return new ArrayList<>();
    }

    @Override
    public ParserReport getReport() {
        return report;
    }

    private void error(String message) {
        logger.error(message);
        report.addError(new LineError(0, 0, null, message));
    }

    private String latest(String attribute) {
        try {
            JEVisAttribute a = parserObject.getAttribute(attribute);
            JEVisSample s = a != null ? a.getLatestSample() : null;
            return s != null ? s.getValueAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void write(String attribute, String value) {
        try {
            JEVisAttribute a = parserObject.getAttribute(attribute);
            if (a != null) a.buildSample(new DateTime(), value).commit();
        } catch (Exception e) {
            logger.error("Could not write {} of parser {}", attribute, parserObject.getID(), e);
        }
    }

    /** "id" or "id:attribute" */
    static Long target(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String id = value.trim().split("[:;]")[0].trim();
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<String> remembered(String json) {
        List<String> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node != null && node.isArray()) for (JsonNode d : node) list.add(d.asText());
        } catch (Exception e) {
            logger.warn("Processed Files is no JSON array, starting anew");
        }
        return list;
    }

    static String summary(InvoiceInbox.Run run) {
        StringBuilder s = new StringBuilder(new DateTime().toString("yyyy-MM-dd HH:mm")).append(": ")
                .append(run.of(InvoiceInbox.IMPORTED).size()).append(" importiert, ")
                .append(run.of(InvoiceInbox.DUPLICATE).size()).append(" schon vorhanden, ")
                .append(run.of(InvoiceInbox.ERROR).size()).append(" Fehler");
        for (InvoiceInbox.Entry e : run.entries) {
            if (!InvoiceInbox.SKIPPED.equals(e.outcome) && !InvoiceInbox.NOT_AN_INVOICE.equals(e.outcome)) s.append('\n').append(e);
        }
        return s.toString();
    }

    static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_FILE) throw new IOException("larger than 20 MB");
        }
        return out.toByteArray();
    }

    private void mail(JEVisDataSource ds, InvoiceInbox.Run run, String channel) {
        String recipients = latest(ERROR_RECIPIENTS);
        if (recipients == null || recipients.trim().isEmpty()) return;
        List<String> to = new ArrayList<>();
        for (String a : recipients.split("[;,\\s]+")) if (a.contains("@")) to.add(a.trim());
        if (to.isEmpty()) return;
        try {
            JsonMailRequest mail = new JsonMailRequest();
            mail.setTo(to);
            mail.setSubject("E-Rechnungen: Fehler beim automatischen Import");
            mail.setText(InvoiceInbox.mailText(run, channel));
            ((JEVisDataSourceWS) ds).sendMail(mail);
        } catch (Exception e) {
            logger.error("Could not send the error mail of parser {}", parserObject.getID(), e);
        }
    }

    /** the web service calls over the data source; each file goes to a temporary file with the given name */
    static final class WsServer implements InvoiceInbox.Server {
        private final JEVisDataSourceWS ds;

        WsServer(JEVisDataSourceWS ds) {
            this.ds = ds;
        }

        @Override
        public JsonNode parse(byte[] content, String fileName, long directory) throws InvoiceInbox.ServerError {
            return call(content, fileName, file -> ds.parseInvoice(file, directory));
        }

        @Override
        public JsonNode importInvoice(byte[] content, String fileName, long directory, String medium) throws InvoiceInbox.ServerError {
            return call(content, fileName, file -> ds.importInvoice(file, directory, medium));
        }

        private interface Call {
            JsonNode run(File file) throws InvoiceRequestException;
        }

        private static JsonNode call(byte[] content, String fileName, Call call) throws InvoiceInbox.ServerError {
            Path dir = null;
            Path file = null;
            try {
                dir = Files.createTempDirectory("jevis-einvoice-");
                file = dir.resolve(fileName);
                Files.write(file, content);
                return call.run(file.toFile());
            } catch (InvoiceRequestException e) {
                throw new InvoiceInbox.ServerError(e.getHttpStatus(), e.getErrorCode(), e.getMessage());
            } catch (IOException e) {
                throw new InvoiceInbox.ServerError(0, null, e.getMessage());
            } finally {
                try {
                    if (file != null) Files.deleteIfExists(file);
                    if (dir != null) Files.deleteIfExists(dir);
                } catch (IOException e) {
                    logger.warn("Could not delete the temporary invoice file", e);
                }
            }
        }
    }
}
