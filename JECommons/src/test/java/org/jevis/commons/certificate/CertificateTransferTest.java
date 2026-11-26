/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import com.sun.net.httpserver.HttpServer;
import org.jevis.api.*;
import org.jevis.commons.mail.MailMessage;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Export, schedule, HTTP channel and the JENotifier cycle of the energy certificates (BA-07); neutral test data. */
public class CertificateTransferTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");

    private static EnergyCertificate certificate(String validUntil) {
        EnergyCertificate c = new EnergyCertificate();
        c.setType(EnergyCertificate.Type.CONSUMPTION);
        c.setIssued("2017-03-01");
        c.setValidUntil(validUntil);
        c.setEnergyClass("C");
        c.setHeatConsumption(112.5);
        c.setIssuer("Ingenieurbüro \"Muster\"; Kassel");
        return c;
    }

    // ---------------------------------------------------------------- export

    @Test
    public void csvJsonAndZip() throws Exception {
        List<CertificateExport.Entry> entries = Arrays.asList(
                new CertificateExport.Entry(100, "Musterwerk", certificate("2027-02-28"), "Ausweis.pdf", "%PDF-1.4".getBytes(StandardCharsets.US_ASCII)),
                new CertificateExport.Entry(101, "Außenlager", certificate("2025-01-01"), null, null));
        LocalDate today = new LocalDate(2026, 11, 26);
        String csv = CertificateExport.csv(entries, today);
        assertTrue(csv.startsWith("﻿JEVis-ID;Gebäude;Art"));
        String[] lines = csv.split("\r\n");
        assertEquals(3, lines.length);
        assertTrue(lines[1].startsWith("100;Musterwerk;Verbrauchsausweis;;;2017-03-01;2027-02-28;läuft ab;C;112,5;"));
        assertTrue(lines[1].contains("\"Ingenieurbüro \"\"Muster\"\"; Kassel\""));
        assertTrue(lines[1].endsWith("pdf/100_Ausweis.pdf"));
        assertTrue(lines[2].contains("abgelaufen"));

        String json = CertificateExport.json(entries, today, new DateTime(2026, 11, 26, 15, 0, ZONE));
        assertTrue(json.contains("\"heatConsumption\": 112.5"));
        assertTrue(json.contains("\"status\": \"EXPIRED\""));

        byte[] zip = CertificateExport.zip(entries, today, new DateTime(2026, 11, 26, 15, 0, ZONE));
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = in.getNextEntry()) != null) files.put(e.getName(), readAll(in));
        }
        assertEquals(Arrays.asList("Energieausweise.csv", "Energieausweise.json", "pdf/100_Ausweis.pdf"), new ArrayList<>(files.keySet()));
        assertEquals("%PDF-1.4", new String(files.get("pdf/100_Ausweis.pdf"), StandardCharsets.US_ASCII));
        assertEquals("Energieausweise_20261126-1500.zip", CertificateExport.fileName(CertificateExport.Format.ZIP, new DateTime(2026, 11, 26, 15, 0, ZONE)));
        assertEquals(CertificateExport.Format.CSV, CertificateExport.Format.of("unknown"));
    }

    // ---------------------------------------------------------------- schedule

    @Test
    public void scheduleAndRetry() {
        DateTime now = new DateTime(2026, 11, 26, 10, 0, ZONE);
        TransferSchedule.LastTransfer never = new TransferSchedule.LastTransfer();
        assertFalse(TransferSchedule.isDue(TransferSchedule.Schedule.MANUAL, never, false, 3, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.MANUAL, never, true, 0, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.MONTHLY, never, false, 0, now));

        TransferSchedule.LastTransfer ok = new TransferSchedule.LastTransfer();
        ok.succeeded(new DateTime(2026, 11, 2, 6, 0, ZONE), 4);
        assertFalse(TransferSchedule.isDue(TransferSchedule.Schedule.MONTHLY, ok, false, 0, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.MONTHLY, ok, false, 0, new DateTime(2026, 12, 1, 0, 5, ZONE)));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.WEEKLY, ok, false, 0, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.DAILY, ok, false, 0, now));
        assertFalse(TransferSchedule.isDue(TransferSchedule.Schedule.ON_CHANGE, ok, false, 0, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.ON_CHANGE, ok, false, 1, now));

        /* a failed attempt waits an hour, also for a request */
        ok.failed(now.minusMinutes(10), "timeout");
        assertFalse(TransferSchedule.isDue(TransferSchedule.Schedule.DAILY, ok, true, 0, now));
        assertTrue(TransferSchedule.isDue(TransferSchedule.Schedule.DAILY, ok, true, 0, now.plusMinutes(51)));
        TransferSchedule.LastTransfer back = TransferSchedule.LastTransfer.fromJson(ok.toJson());
        assertEquals("timeout", back.getError());
        assertEquals(Integer.valueOf(4), back.getCount());
    }

    @Test
    public void stateStartsAgainForARenewedCertificate() {
        CertificateState state = new CertificateState();
        state.markSent("2027-02-28", 6, "2026-08-28T00:10:00");
        CertificateState back = CertificateState.fromJson(state.toJson());
        assertEquals(Collections.singletonList(6), back.sentFor("2027-02-28"));
        assertTrue(back.sentFor("2037-02-27").isEmpty());
    }

    // ---------------------------------------------------------------- HTTP

    @Test
    public void httpPostsTheFileWithToken() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final Map<String, String> got = new HashMap<>();
        server.createContext("/in", exchange -> {
            got.put("auth", exchange.getRequestHeaders().getFirst("Authorization"));
            got.put("type", exchange.getRequestHeaders().getFirst("Content-Type"));
            got.put("disposition", exchange.getRequestHeaders().getFirst("Content-Disposition"));
            got.put("body", new String(readAll(exchange.getRequestBody()), StandardCharsets.UTF_8));
            int status = exchange.getRequestURI().getQuery() != null ? 500 : 204;
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/in";
            CertificateHttp.post(base, "secret", "Energieausweise_1.csv", "a;b".getBytes(StandardCharsets.UTF_8), "text/csv; charset=UTF-8");
            assertEquals("Bearer secret", got.get("auth"));
            assertEquals("text/csv; charset=UTF-8", got.get("type"));
            assertEquals("attachment; filename*=UTF-8''Energieausweise_1.csv", got.get("disposition"));
            assertEquals("a;b", got.get("body"));
            try {
                CertificateHttp.post(base + "?fail", null, "x.csv", new byte[1], "text/csv");
                fail("500 must fail");
            } catch (java.io.IOException e) {
                assertTrue(e.getMessage().startsWith("HTTP 500"));
            }
            try {
                CertificateHttp.post("ftp://example.org/x", null, "x.csv", new byte[1], "text/csv");
                fail("ftp must fail");
            } catch (java.io.IOException e) {
                assertEquals("Only http and https URLs", e.getMessage());
            }
        } finally {
            server.stop(0);
        }
    }

    // ---------------------------------------------------------------- processor

    private JEVisDataSource ds;
    private final Map<String, String> values = new HashMap<>();
    private final List<MailMessage> mails = new ArrayList<>();
    private final List<String> http = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        ds = mock(JEVisDataSource.class);
        JEVisObject user = object(12, "user12", "User", null);
        values.put("12/E-Mail", "user@example.org");
        when(ds.getObject(12L)).thenReturn(user);
    }

    /** an object whose attributes read and write {@link #values} ("id/attribute") */
    private JEVisObject object(long id, String name, String className, JEVisObject parent) throws Exception {
        JEVisObject o = mock(JEVisObject.class);
        when(o.getID()).thenReturn(id);
        when(o.getName()).thenReturn(name);
        when(o.getJEVisClassName()).thenReturn(className);
        when(o.getParents()).thenReturn(parent != null ? Collections.singletonList(parent) : Collections.<JEVisObject>emptyList());
        when(o.getAttribute(any(String.class))).thenAnswer(inv -> attribute(id + "/" + inv.getArgument(0)));
        return o;
    }

    private JEVisAttribute attribute(String key) throws Exception {
        JEVisAttribute a = mock(JEVisAttribute.class);
        when(a.hasSample()).thenAnswer(inv -> values.containsKey(key));
        when(a.getLatestSample()).thenAnswer(inv -> {
            String v = values.get(key);
            if (v == null) return null;
            JEVisSample s = mock(JEVisSample.class);
            when(s.getValueAsString()).thenReturn(v);
            when(s.getTimestamp()).thenReturn(new DateTime(2026, 11, 1, 0, 0, ZONE));
            return s;
        });
        when(a.buildSample(any(DateTime.class), any())).thenAnswer(inv -> {
            String v = String.valueOf((Object) inv.getArgument(1));
            JEVisSample s = mock(JEVisSample.class);
            org.mockito.Mockito.doAnswer(c -> {
                values.put(key, v);
                return null;
            }).when(s).commit();
            return s;
        });
        return a;
    }

    private void classOf(String className, JEVisObject... objects) throws Exception {
        JEVisClass c = mock(JEVisClass.class);
        when(ds.getJEVisClass(className)).thenReturn(c);
        when(ds.getObjects(c, false)).thenReturn(Arrays.asList(objects));
    }

    private CertificateProcessor processor() {
        return new CertificateProcessor(ds, new CertificateProcessor.Channels() {
            @Override
            public void mail(MailMessage message) {
                mails.add(message);
            }

            @Override
            public String sftp(String host, int port, String user, String password, String directory, String knownHostKey,
                               String fileName, byte[] content) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void http(String url, String token, String fileName, byte[] content, String contentType) {
                http.add(url + " " + fileName + " " + new String(content, StandardCharsets.UTF_8).split("\r\n").length);
            }
        }, "https://jevis.example.org/app/");
    }

    @Test
    public void remindersOncePerMonthAndTransferOnRequest() throws Exception {
        JEVisObject org = object(1, "Demo", "Organization", null);
        JEVisObject building = object(10, "Musterwerk", "Building", org);
        JEVisObject dir = object(11, "Energieausweise", EnergyCertificate.DIRECTORY_CLASS_NAME, building);
        JEVisObject cert = object(100, "Verbrauchsausweis 2017", EnergyCertificate.CLASS_NAME, dir);
        EnergyCertificate c = certificate("2027-05-31");
        c.setResponsible(Collections.singletonList(12L));
        values.put("100/Data", CertificateLogic.toJson(c));
        values.put("100/Transfer State", "");
        JEVisObject target = object(60, "Liegenschaftsamt", CertificateProcessor.CLASS_TRANSFER, org);
        values.put("60/Enabled", "true");
        values.put("60/Channel", "HTTP");
        values.put("60/Format", "CSV");
        values.put("60/Schedule", "MANUAL");
        values.put("60/URL", "https://example.org/in");
        values.put("60/Last Transfer", "");
        classOf(EnergyCertificate.CLASS_NAME, cert);
        classOf(CertificateProcessor.CLASS_TRANSFER, target);

        CertificateProcessor p = processor();
        p.run(new DateTime(2026, 11, 29, 8, 0, ZONE));
        assertTrue("before the 6-month reminder", mails.isEmpty());
        p.run(new DateTime(2026, 11, 30, 8, 0, ZONE));
        assertEquals(1, mails.size());
        assertTrue(mails.get(0).getTo().contains("user@example.org"));
        assertTrue(mails.get(0).getSubject().contains("Musterwerk"));
        assertTrue(mails.get(0).getText().contains("https://jevis.example.org/app/certificates"));
        p.run(new DateTime(2026, 11, 30, 9, 0, ZONE));
        assertEquals("no repeated reminder", 1, mails.size());
        assertTrue("manual: nothing sent", http.isEmpty());

        values.put("60/Requested", "{\"at\":\"2026-11-30T09:01:00\",\"by\":\"demo\"}");
        p.run(new DateTime(2026, 11, 30, 9, 5, ZONE));
        assertEquals(Collections.singletonList("https://example.org/in Energieausweise_20261130-0905.csv 2"), http);
        assertEquals("", values.get("60/Requested"));
        TransferSchedule.LastTransfer last = TransferSchedule.LastTransfer.fromJson(values.get("60/Last Transfer"));
        assertTrue(last.isSuccess());
        assertEquals(Integer.valueOf(1), last.getCount());
        assertNotNull(CertificateState.fromJson(values.get("100/Transfer State")).getTransfers().get("60"));
        assertNotNull("the reminder bookkeeping is kept", CertificateState.fromJson(values.get("100/Transfer State")).getLastReminder());
    }

    @Test
    public void noResponsibleMeansNoReminderAndNoError() throws Exception {
        JEVisObject building = object(10, "Musterwerk", "Building", null);
        JEVisObject cert = object(100, "A", EnergyCertificate.CLASS_NAME, building);
        values.put("100/Data", CertificateLogic.toJson(certificate("2027-03-31")));
        values.put("100/Transfer State", "{\"lastError\":\"No recipients with an e-mail address\"}");
        classOf(EnergyCertificate.CLASS_NAME, cert);
        classOf(CertificateProcessor.CLASS_TRANSFER);
        CertificateProcessor.Result result = processor().run(new DateTime(2026, 11, 26, 10, 0, ZONE));
        assertTrue(mails.isEmpty());
        assertEquals(0, result.failed);
        assertNull(CertificateState.fromJson(values.get("100/Transfer State")).getLastError());
    }

    @Test
    public void onChangeSendsOnlyChangedAndRecordsErrors() throws Exception {
        JEVisObject org = object(1, "Demo", "Organization", null);
        JEVisObject building = object(10, "Musterwerk", "Building", org);
        JEVisObject other = object(20, "Anderes Werk", "Building", object(2, "Fremd", "Organization", null));
        JEVisObject cert = object(100, "A", EnergyCertificate.CLASS_NAME, building);
        JEVisObject foreign = object(200, "B", EnergyCertificate.CLASS_NAME, other);
        values.put("100/Data", CertificateLogic.toJson(certificate("2031-01-01")));
        values.put("200/Data", CertificateLogic.toJson(certificate("2031-01-01")));
        values.put("100/Transfer State", "");
        values.put("200/Transfer State", "");
        JEVisObject target = object(60, "Amt", CertificateProcessor.CLASS_TRANSFER, building);
        values.put("60/Enabled", "true");
        values.put("60/Channel", "MAIL");
        values.put("60/Schedule", "ON_CHANGE");
        values.put("60/Recipients", "amt@example.org");
        values.put("60/Last Transfer", "");
        classOf(EnergyCertificate.CLASS_NAME, cert, foreign);
        classOf(CertificateProcessor.CLASS_TRANSFER, target);

        CertificateProcessor p = processor();
        p.run(new DateTime(2026, 11, 26, 10, 0, ZONE));
        assertEquals(1, mails.size());
        MailMessage m = mails.get(0);
        assertEquals(Collections.singleton("amt@example.org"), m.getTo());
        String csv = new String(m.getAttachments().get(0).getContent(), StandardCharsets.UTF_8);
        assertEquals("only the certificate below the target's parent", 2, csv.split("\r\n").length);
        assertTrue(csv.contains("\r\n100;"));

        p.run(new DateTime(2026, 11, 26, 10, 5, ZONE));
        assertEquals("unchanged: nothing sent", 1, mails.size());

        values.put("60/Channel", "SFTP");
        values.put("60/Requested", "{}");
        p.run(new DateTime(2026, 11, 26, 10, 10, ZONE));
        TransferSchedule.LastTransfer last = TransferSchedule.LastTransfer.fromJson(values.get("60/Last Transfer"));
        assertFalse(last.isSuccess());
        assertEquals("No SFTP host", last.getError());
        assertEquals("", values.get("60/Requested"));
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toByteArray();
    }
}
