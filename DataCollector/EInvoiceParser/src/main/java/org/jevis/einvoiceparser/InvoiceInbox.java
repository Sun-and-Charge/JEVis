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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Unattended import of e-invoices (R02, docs/ENERGY-INVOICES.md "Unattended import"): the files a data source fetched
 * (mail attachments, sFTP/FTP/folder files) are imported as invoices into one Energy Invoices Directory through the
 * web service, which also runs the invoice check (R03). Files already handled (SHA-256) are skipped, so a mail or
 * file fetched again is neither imported nor reported twice; duplicates on the server (409) count as handled.
 */
public final class InvoiceInbox {

    /** at most this many digests are remembered */
    public static final int MAX_REMEMBERED = 1000;
    public static final List<String> MEDIA = Collections.unmodifiableList(Arrays.asList("ELECTRICITY", "GAS", "DISTRICT_HEATING", "OTHER"));

    /** the web service calls the inbox needs */
    public interface Server {
        /** POST /invoices/parse: {@code medium}, {@code duplicate}, {@code number}, {@code seller} … */
        JsonNode parse(byte[] content, String fileName, long directory) throws ServerError;

        /** POST /invoices/import: {@code invoiceId}, {@code check} … */
        JsonNode importInvoice(byte[] content, String fileName, long directory, String medium) throws ServerError;
    }

    /** an error answer of the web service */
    public static final class ServerError extends Exception {
        public final int status;
        public final String code;

        public ServerError(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }

    /** what happened to one file */
    public static final class Entry {
        public final String name;
        public final String outcome;
        public final String detail;
        /** an error that may go away (server, rights): the file is tried again in the next run */
        public final boolean retry;

        Entry(String name, String outcome, String detail) {
            this(name, outcome, detail, false);
        }

        Entry(String name, String outcome, String detail, boolean retry) {
            this.name = name;
            this.outcome = outcome;
            this.detail = detail;
            this.retry = retry;
        }

        @Override
        public String toString() {
            return name + ": " + outcome + (detail != null ? " (" + detail + ")" : "");
        }
    }

    public static final String IMPORTED = "imported";
    public static final String DUPLICATE = "duplicate";
    public static final String SKIPPED = "skipped";
    public static final String NOT_AN_INVOICE = "notAnInvoice";
    public static final String ERROR = "error";

    /** the result of one run */
    public static final class Run {
        public final List<Entry> entries = new ArrayList<>();
        /** digests to remember (the given ones plus the newly handled ones, newest last) */
        public final List<String> remembered = new ArrayList<>();

        public List<Entry> of(String outcome) {
            List<Entry> list = new ArrayList<>();
            for (Entry e : entries) if (e.outcome.equals(outcome)) list.add(e);
            return list;
        }

        public boolean hasErrors() {
            return !of(ERROR).isEmpty();
        }

        /** whether a file must be fetched again (the data source must not move its last readout) */
        public boolean needsRetry() {
            for (Entry e : entries) if (e.retry) return true;
            return false;
        }
    }

    private InvoiceInbox() {
    }

    /** PDF or XML by content: the extension of the file name for the server, null for anything else */
    static String extension(byte[] content) {
        if (content == null || content.length < 5) return null;
        String head = new String(content, 0, Math.min(content.length, 512), StandardCharsets.ISO_8859_1).trim();
        if (head.startsWith("%PDF")) return ".pdf";
        if (head.startsWith("﻿")) head = head.substring(1);
        if (head.startsWith("ï»¿")) head = head.substring(3);
        if (head.startsWith("<?xml") || head.startsWith("<")) return ".xml";
        return null;
    }

    static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Imports the files.
     *
     * @param files         contents of the fetched files in fetch order
     * @param directory     the Energy Invoices Directory
     * @param defaultMedium medium when the server does not detect one ({@code OTHER} if null)
     * @param remembered    digests of files handled before
     */
    public static Run run(Server server, List<byte[]> files, long directory, String defaultMedium, Collection<String> remembered) {
        Run run = new Run();
        LinkedHashSet<String> known = new LinkedHashSet<>(remembered);
        String fallback = defaultMedium != null && MEDIA.contains(defaultMedium) ? defaultMedium : "OTHER";
        int n = 0;
        for (byte[] content : files) {
            n++;
            if (content == null || content.length == 0) continue;
            String digest = sha256(content);
            String ext = extension(content);
            String name = "e-rechnung-" + digest.substring(0, 8) + (ext != null ? ext : "");
            if (known.contains(digest)) {
                run.entries.add(new Entry(name, SKIPPED, "already handled"));
                continue;
            }
            if (ext == null) {
                /* other attachments (signatures, images): not an invoice, not an error */
                run.entries.add(new Entry("Datei " + n, NOT_AN_INVOICE, null));
                known.add(digest);
                continue;
            }
            try {
                JsonNode parsed = server.parse(content, name, directory);
                String label = label(parsed, name);
                if (parsed.hasNonNull("duplicate")) {
                    run.entries.add(new Entry(label, DUPLICATE, "#" + parsed.get("duplicate").asText()));
                    known.add(digest);
                    continue;
                }
                String detected = parsed.hasNonNull("medium") ? parsed.get("medium").asText() : null;
                String medium = detected != null && MEDIA.contains(detected) && !"OTHER".equals(detected) ? detected : fallback;
                JsonNode imported = server.importInvoice(content, name, directory, medium);
                JsonNode check = imported.get("check");
                String status = check != null && check.hasNonNull("status") ? check.get("status").asText() : null;
                run.entries.add(new Entry(label, IMPORTED, "#" + imported.path("invoiceId").asText() + (status != null ? ", Prüfung " + status : "")));
                known.add(digest);
            } catch (ServerError e) {
                if (e.status == 409) {
                    run.entries.add(new Entry(name, DUPLICATE, e.getMessage()));
                    known.add(digest);
                } else if (e.status == 400 && ("NOT_AN_E_INVOICE".equals(e.code) || "UNREADABLE".equals(e.code))) {
                    /* a PDF or XML that is no e-invoice: reported once, not tried again */
                    run.entries.add(new Entry(name, ERROR, e.code + ": " + e.getMessage()));
                    known.add(digest);
                } else {
                    /* server down, no rights, …: not remembered, tried again in the next run */
                    run.entries.add(new Entry(name, ERROR, e.status + (e.code != null ? " " + e.code : "") + ": " + e.getMessage(), true));
                }
            } catch (Exception e) {
                run.entries.add(new Entry(name, ERROR, e.toString(), true));
            }
        }
        List<String> all = new ArrayList<>(known);
        run.remembered.addAll(all.subList(Math.max(0, all.size() - MAX_REMEMBERED), all.size()));
        return run;
    }

    private static String label(JsonNode parsed, String fallback) {
        String number = parsed.hasNonNull("number") ? parsed.get("number").asText() : null;
        String seller = parsed.hasNonNull("seller") ? parsed.get("seller").asText() : null;
        if (number == null && seller == null) return fallback;
        return (number != null ? number : "?") + (seller != null ? " " + seller : "");
    }

    /** German plain text of a run for the error mail */
    public static String mailText(Run run, String channel) {
        StringBuilder text = new StringBuilder("Beim automatischen Import von E-Rechnungen")
                .append(channel != null ? " (" + channel + ")" : "").append(" sind Fehler aufgetreten.\n\n");
        for (Entry e : run.of(ERROR)) text.append("Fehler: ").append(e.name).append(" – ").append(e.detail).append('\n');
        List<Entry> imported = run.of(IMPORTED);
        if (!imported.isEmpty()) {
            text.append("\nImportiert:\n");
            for (Entry e : imported) text.append("  ").append(e.name).append(" (").append(e.detail).append(")\n");
        }
        text.append("\nDateien mit Fehlern werden nicht erneut gemeldet, außer der Server war nicht erreichbar oder es fehlten Rechte.\n");
        return text.toString();
    }
}
