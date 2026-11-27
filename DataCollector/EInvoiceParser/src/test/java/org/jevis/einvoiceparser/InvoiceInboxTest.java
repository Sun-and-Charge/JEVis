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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** Unattended e-invoice import (R02) with a fake web service; neutral test data. */
class InvoiceInboxTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static byte[] pdf(String text) {
        return ("%PDF-1.7\n" + text).getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] xml(String text) {
        return ("<?xml version=\"1.0\"?><Invoice>" + text + "</Invoice>").getBytes(StandardCharsets.UTF_8);
    }

    static final class Fake implements InvoiceInbox.Server {
        final List<String> calls = new ArrayList<>();
        final Map<String, String> media = new HashMap<>();
        final Set<String> duplicates = new HashSet<>();
        final Map<String, InvoiceInbox.ServerError> parseErrors = new HashMap<>();
        InvoiceInbox.ServerError importError;
        long next = 100;

        private static String key(byte[] content) {
            return new String(content, StandardCharsets.ISO_8859_1);
        }

        @Override
        public JsonNode parse(byte[] content, String fileName, long directory) throws InvoiceInbox.ServerError {
            calls.add("parse " + fileName + " " + directory);
            InvoiceInbox.ServerError e = parseErrors.get(key(content));
            if (e != null) throw e;
            ObjectNode node = MAPPER.createObjectNode();
            node.put("number", "R-" + next);
            node.put("seller", "Versorger Nord");
            String medium = media.get(key(content));
            if (medium != null) node.put("medium", medium);
            if (duplicates.contains(key(content))) node.put("duplicate", 7);
            return node;
        }

        @Override
        public JsonNode importInvoice(byte[] content, String fileName, long directory, String medium) throws InvoiceInbox.ServerError {
            calls.add("import " + fileName + " " + medium);
            if (importError != null) throw importError;
            ObjectNode node = MAPPER.createObjectNode();
            node.put("invoiceId", next++);
            node.putObject("check").put("status", "DEVIATION");
            return node;
        }
    }

    @Test
    void importsInvoicesSkipsOtherFilesAndRemembersThem() {
        Fake fake = new Fake();
        byte[] a = pdf("A");
        byte[] b = xml("B");
        byte[] image = new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
        fake.media.put(new String(a, StandardCharsets.ISO_8859_1), "GAS");
        InvoiceInbox.Run run = InvoiceInbox.run(fake, Arrays.asList(a, b, image), 900, "ELECTRICITY", Collections.emptyList());
        assertEquals(2, run.of(InvoiceInbox.IMPORTED).size());
        assertEquals(1, run.of(InvoiceInbox.NOT_AN_INVOICE).size());
        assertFalse(run.hasErrors());
        assertEquals(3, run.remembered.size());
        /* detected medium first, else the default */
        assertTrue(fake.calls.get(1).endsWith(" GAS"), fake.calls.toString());
        assertTrue(fake.calls.get(3).endsWith(" ELECTRICITY"), fake.calls.toString());
        assertTrue(fake.calls.get(0).matches("parse e-rechnung-[0-9a-f]{8}\\.pdf 900"), fake.calls.get(0));
        assertTrue(fake.calls.get(2).contains(".xml"));
        assertTrue(run.of(InvoiceInbox.IMPORTED).get(0).detail.contains("Prüfung DEVIATION"));

        /* fetched again: nothing is sent to the server */
        Fake again = new Fake();
        InvoiceInbox.Run second = InvoiceInbox.run(again, Arrays.asList(a, b, image), 900, null, run.remembered);
        assertTrue(again.calls.isEmpty());
        assertEquals(3, second.of(InvoiceInbox.SKIPPED).size());
    }

    @Test
    void duplicatesAndBrokenFilesAreReportedOnceServerErrorsAgain() {
        Fake fake = new Fake();
        byte[] dup = pdf("dup");
        byte[] broken = pdf("broken");
        byte[] later = pdf("later");
        fake.duplicates.add(new String(dup, StandardCharsets.ISO_8859_1));
        fake.parseErrors.put(new String(broken, StandardCharsets.ISO_8859_1), new InvoiceInbox.ServerError(400, "NOT_AN_E_INVOICE", "plain PDF"));
        fake.parseErrors.put(new String(later, StandardCharsets.ISO_8859_1), new InvoiceInbox.ServerError(503, null, "down"));
        InvoiceInbox.Run run = InvoiceInbox.run(fake, Arrays.asList(dup, broken, later), 900, null, Collections.emptyList());
        assertEquals(1, run.of(InvoiceInbox.DUPLICATE).size());
        assertEquals(2, run.of(InvoiceInbox.ERROR).size());
        assertTrue(run.needsRetry());
        /* the duplicate and the broken PDF are remembered, the server error is tried again */
        assertEquals(2, run.remembered.size());
        String text = InvoiceInbox.mailText(run, "Postfach Rechnungen");
        assertTrue(text.contains("NOT_AN_E_INVOICE"), text);
        assertTrue(text.contains("Postfach Rechnungen"), text);

        /* 409 on import counts as handled */
        Fake conflict = new Fake();
        conflict.importError = new InvoiceInbox.ServerError(409, "DUPLICATE", "exists");
        InvoiceInbox.Run c = InvoiceInbox.run(conflict, Collections.singletonList(pdf("x")), 900, null, Collections.emptyList());
        assertEquals(1, c.of(InvoiceInbox.DUPLICATE).size());
        assertFalse(c.needsRetry());
    }

    @Test
    void helpers() {
        assertEquals(".pdf", InvoiceInbox.extension(pdf("")));
        assertEquals(".xml", InvoiceInbox.extension("﻿<?xml version=\"1.0\"?>".getBytes(StandardCharsets.UTF_8)));
        assertNull(InvoiceInbox.extension("hello world".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Long.valueOf(900), EInvoiceParser.target("900:Value"));
        assertNull(EInvoiceParser.target("abc"));
        assertEquals(Arrays.asList("a", "b"), EInvoiceParser.remembered("[\"a\",\"b\"]"));
        assertTrue(EInvoiceParser.remembered("kaputt").isEmpty());
        List<String> many = new ArrayList<>();
        for (int i = 0; i < InvoiceInbox.MAX_REMEMBERED + 5; i++) many.add("d" + i);
        InvoiceInbox.Run run = InvoiceInbox.run(new Fake(), Collections.emptyList(), 1, null, many);
        assertEquals(InvoiceInbox.MAX_REMEMBERED, run.remembered.size());
        assertEquals("d" + (InvoiceInbox.MAX_REMEMBERED + 4), run.remembered.get(run.remembered.size() - 1));
    }
}
