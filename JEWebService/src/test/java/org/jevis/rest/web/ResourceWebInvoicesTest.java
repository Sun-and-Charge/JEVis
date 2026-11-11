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
import jakarta.ws.rs.core.Response;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonUnit;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebInvoicesTest {

    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final LocalDate TODAY = new LocalDate(2026, 11, 11);
    private static final String INVOICE = ResourceWebInvoices.CLASS_INVOICE;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    // ---------------------------------------------------------------- objects

    private static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    /* Organisation 1 > site 2 "Musterwerk" > 3 "Energieverträge" > 4 "Energierechnungen" > 5 "Strom" > invoices:
       100 October, 101 credit note September, 102 2023 (outside), 103 only invoice date (August), 104 hidden,
       105 recycle bin, 106 without dates, 107 PDF without stored name, 108 XML; 109 is no invoice */
    private static final JsonObject ORG = obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0);
    private static final JsonObject SITE = obj(2, "Musterwerk", ResourceWebNavigation.CLASS_BUILDING, 1);
    private static final JsonObject CONTRACTS = obj(3, "Energieverträge", "Energy Contracting Directory", 2);
    private static final JsonObject INVOICES = obj(4, "Energierechnungen", "Energy Invoices Directory", 3);
    private static final JsonObject POWER = obj(5, "Strom", "Energy Invoices Directory", 4);
    private static final JsonObject OCTOBER = obj(100, "EN-202610-S", INVOICE, 5);
    private static final JsonObject CREDIT = obj(101, "EN-202609-G", INVOICE, 5);
    private static final JsonObject OLD = obj(102, "EN-202301", INVOICE, 5);
    private static final JsonObject AUGUST = obj(103, "EN-202608", INVOICE, 5);
    private static final JsonObject HIDDEN = obj(104, "Verborgen", INVOICE, 5);
    private static final JsonObject BIN = obj(105, "Alt", INVOICE, 5);
    private static final JsonObject NO_DATE = obj(106, "Ohne Datum", INVOICE, 5);
    private static final JsonObject NO_NAME = obj(107, "Ohne Name", INVOICE, 5);
    private static final JsonObject XML = obj(108, "XRechnung", INVOICE, 5);
    private static final JsonObject NO_INVOICE = obj(109, "Verbrauch laut Rechnung", "Energy Invoice Data", 5);

    static {
        BIN.setDeleteTS("2026-11-01T00:00:00.000+01:00");
    }

    private static final List<JsonObject> ALL = Arrays.asList(ORG, SITE, CONTRACTS, INVOICES, POWER, OCTOBER, CREDIT,
            OLD, AUGUST, HIDDEN, BIN, NO_DATE, NO_NAME, XML, NO_INVOICE);

    private static ResourceWebModules.Lookup lookup(List<JsonObject> all) {
        List<JsonObject> readable = new ArrayList<>(all);
        readable.remove(HIDDEN);
        Map<Long, Long> parents = new HashMap<>();
        for (JsonObject o : all) parents.put(o.getId(), o.getParent());
        return new ResourceWebModules.Lookup(all, readable, parents);
    }

    private static ResourceWebModules.Lookup lookup() {
        return lookup(ALL);
    }

    private static ResourceWebRegisters.Classes classes() {
        return ResourceWebContractsTest.classes();
    }

    // ---------------------------------------------------------------- store

    private static class Store implements ResourceWebContracts.Store {
        final Map<Long, Map<String, JsonAttribute>> attributes = new HashMap<>();
        final Map<String, byte[]> files = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        Store set(long id, String name, String value, String unit) {
            JsonAttribute attribute = new JsonAttribute();
            attribute.setType(name);
            JsonSample sample = new JsonSample();
            sample.setTs("2026-10-01T00:00:00.000+02:00");
            sample.setValue(value);
            attribute.setLatestValue(sample);
            attribute.setSampleCount(1);
            JsonUnit jsonUnit = new JsonUnit();
            jsonUnit.setLabel(unit != null ? unit : "");
            attribute.setDisplayUnit(jsonUnit);
            attributes.computeIfAbsent(id, k -> new HashMap<>()).put(name, attribute);
            return this;
        }

        Store set(long id, String name, String value) {
            return set(id, name, value, null);
        }

        Store file(long id, String name, String fileName, byte[] content) {
            set(id, name, fileName);
            files.put(id + "/" + name, content);
            return this;
        }

        @Override
        public Map<String, JsonAttribute> attributes(long id) {
            calls.add("attributes " + id);
            return attributes.getOrDefault(id, Collections.emptyMap());
        }

        @Override
        public List<JsonSample> latest(long id, String attribute, int limit) {
            calls.add("latest " + id + "/" + attribute);
            return Collections.emptyList();
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
            calls.add("file " + id + "/" + attribute);
            return files.get(id + "/" + attribute);
        }
    }

    private static final String ITEMS = "[{\"description\":\"Arbeitspreis\",\"quantity\":41250.0,\"unit\":\"kWh\","
            + "\"unitPrice\":0.28,\"net\":11550.0,\"taxRate\":19.0,\"periodStart\":null,\"periodEnd\":null}]";

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static Store store() {
        Store store = new Store();
        store.set(100, ResourceWebInvoices.ATTRIBUTE_NUMBER, "EN-202610-S")
                .set(100, ResourceWebInvoices.ATTRIBUTE_DATE, "2026-11-03T00:00:00.000+01:00")
                .set(100, ResourceWebInvoices.ATTRIBUTE_FORMAT, "ZUGFeRD 2.3 EN16931")
                .set(100, ResourceWebInvoices.ATTRIBUTE_SELLER, "Energieversorger Nord")
                .set(100, ResourceWebInvoices.ATTRIBUTE_SELLER_VAT_ID, "DE000000000")
                .set(100, ResourceWebInvoices.ATTRIBUTE_BUYER, "Musterwerk GmbH")
                .set(100, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-10-01T00:00:00.000+02:00")
                .set(100, ResourceWebInvoices.ATTRIBUTE_PERIOD_END, "2026-10-31T00:00:00.000+01:00")
                .set(100, ResourceWebInvoices.ATTRIBUTE_MEDIUM, "ELECTRICITY")
                .set(100, ResourceWebInvoices.ATTRIBUTE_METER_POINT, "DE0000000000000000000000000000001")
                .set(100, ResourceWebInvoices.ATTRIBUTE_CONSUMPTION, "41250.0", "kWh")
                .set(100, ResourceWebInvoices.ATTRIBUTE_NET, "11550.0", "€")
                .set(100, ResourceWebInvoices.ATTRIBUTE_TAX, "2194.5", "€")
                .set(100, ResourceWebInvoices.ATTRIBUTE_GROSS, "13744.5", "€")
                .set(100, ResourceWebInvoices.ATTRIBUTE_CURRENCY, "EUR")
                .set(100, ResourceWebInvoices.ATTRIBUTE_LINE_ITEMS, ITEMS)
                .file(100, ResourceWebInvoices.ATTRIBUTE_FILE, "EN-202610-S.pdf", bytes("%PDF-1.7 october"))
                /* another file attribute must never be served */
                .file(100, "Attachment", "anhang.pdf", bytes("%PDF-1.7 attachment"));
        store.set(101, ResourceWebInvoices.ATTRIBUTE_NUMBER, "EN-202609-G")
                .set(101, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-09-01T00:00:00.000+02:00")
                .set(101, ResourceWebInvoices.ATTRIBUTE_GROSS, "-119.0", "€")
                .set(101, ResourceWebInvoices.ATTRIBUTE_LINE_ITEMS, "[{\"description\": ");
        store.set(102, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2023-01-01T00:00:00.000+01:00");
        store.set(103, ResourceWebInvoices.ATTRIBUTE_DATE, "2026-08-15T00:00:00.000+02:00")
                .set(103, ResourceWebInvoices.ATTRIBUTE_LINE_ITEMS, "{\"description\":\"kein Array\"}");
        store.set(104, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-10-01T00:00:00.000+02:00")
                .file(104, ResourceWebInvoices.ATTRIBUTE_FILE, "geheim.pdf", bytes("%PDF-secret"));
        store.set(105, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-10-01T00:00:00.000+02:00");
        store.set(106, ResourceWebInvoices.ATTRIBUTE_NUMBER, "ohne");
        store.set(107, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-07-01T00:00:00.000+02:00")
                .file(107, ResourceWebInvoices.ATTRIBUTE_FILE, "", bytes("%PDF-1.4 no name"));
        store.set(108, ResourceWebInvoices.ATTRIBUTE_PERIOD_START, "2026-06-01T00:00:00.000+02:00")
                .file(108, ResourceWebInvoices.ATTRIBUTE_FILE, "xrechnung.xml",
                        bytes("﻿  <?xml version=\"1.0\"?><Invoice/>"));
        return store;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("invoices");
    }

    private static List<Object> ids(Map<String, Object> answer) {
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> i : list(answer)) ids.add(i.get("id"));
        return ids;
    }

    private static LocalDate day(String text) {
        return LocalDate.parse(text);
    }

    // ---------------------------------------------------------------- range

    @Test
    public void defaultRangeIs24MonthsUpToEndOfMonth() {
        LocalDate[] range = ResourceWebInvoices.dayRange(null, null, ZONE, TODAY);
        assertEquals(day("2024-12-01"), range[0]);
        assertEquals(day("2026-11-30"), range[1]);
        range = ResourceWebInvoices.dayRange(" ", "2026-02-28", ZONE, TODAY);
        assertEquals(day("2024-03-01"), range[0]);
        assertEquals(day("2026-02-28"), range[1]);
        range = ResourceWebInvoices.dayRange("2025-11-01", "2026-11-30", ZONE, TODAY);
        assertEquals(day("2025-11-01"), range[0]);
        assertEquals(day("2026-11-30"), range[1]);
        /* exactly ten years */
        range = ResourceWebInvoices.dayRange("2016-11-30", "2026-11-30", ZONE, TODAY);
        assertEquals(day("2016-11-30"), range[0]);
    }

    @Test
    public void invalidRanges() {
        String[][] bad = {{"2026-12-01", "2026-01-01"}, {"2016-11-29", "2026-11-30"}, {"gestern", null},
                {null, "30.11.2026"}, {"2026-13-01", "2026-12-01"}};
        for (String[] b : bad) {
            try {
                ResourceWebInvoices.dayRange(b[0], b[1], ZONE, TODAY);
                fail(Arrays.toString(b));
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
    }

    @Test
    public void endpointAnswersBadRequestBeforeDatabase() {
        ResourceWebInvoices resource = new ResourceWebInvoices();
        assertEquals(400, resource.invoices(null, "2026-12-01", "2026-01-01", "Europe/Berlin").getStatus());
        assertEquals(400, resource.invoices(null, "2000-01-01", "2026-01-01", null).getStatus());
        assertEquals(400, resource.invoices(null, "x", null, null).getStatus());
    }

    // ---------------------------------------------------------------- list

    @Test
    public void listInRangeNewestFirst() {
        Map<String, Object> answer = ResourceWebInvoices.invoicesJson(lookup(), classes(), store(),
                day("2025-12-01"), day("2026-11-30"));
        /* by period start, else invoice date; not hidden, in the recycle bin, without dates, outside, no invoice */
        assertEquals(Arrays.asList(100L, 101L, 103L, 107L, 108L), ids(answer));
        assertEquals(false, answer.get("truncated"));

        /* the bounds are inclusive */
        assertEquals(Collections.singletonList(101L), ids(ResourceWebInvoices.invoicesJson(lookup(), classes(), store(),
                day("2026-09-01"), day("2026-09-01"))));
        assertEquals(Collections.singletonList(102L), ids(ResourceWebInvoices.invoicesJson(lookup(), classes(), store(),
                day("2016-12-01"), day("2023-01-01"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listEntry() throws Exception {
        Store store = store();
        Map<String, Object> answer = ResourceWebInvoices.invoicesJson(lookup(), classes(), store,
                day("2025-12-01"), day("2026-11-30"));
        Map<String, Object> october = list(answer).get(0);
        assertEquals(100L, october.get("id"));
        assertEquals("EN-202610-S", october.get("number"));
        assertEquals("2026-11-03", october.get("date"));
        assertEquals("ZUGFeRD 2.3 EN16931", october.get("format"));
        assertEquals("Energieversorger Nord", october.get("seller"));
        assertEquals("Musterwerk GmbH", october.get("buyer"));
        assertEquals("2026-10-01", october.get("periodStart"));
        assertEquals("2026-10-31", october.get("periodEnd"));
        assertEquals("ELECTRICITY", october.get("medium"));
        assertEquals("DE0000000000000000000000000000001", october.get("meterPoint"));
        assertEquals(41250.0, october.get("consumption"));
        assertEquals("kWh", october.get("consumptionUnit"));
        assertEquals(11550.0, october.get("net"));
        assertEquals(2194.5, october.get("tax"));
        assertEquals(13744.5, october.get("gross"));
        assertEquals("EUR", october.get("currency"));
        assertEquals(false, october.get("credit"));
        assertEquals("EN-202610-S.pdf", october.get("file"));
        assertEquals("Energieverträge / Energierechnungen / Strom", october.get("path"));
        assertEquals("Musterwerk", ((Map<String, Object>) october.get("site")).get("name"));
        /* the detail fields only in the detail */
        assertFalse(october.containsKey("items"));
        assertFalse(october.containsKey("sellerVatId"));

        Map<String, Object> credit = list(answer).get(1);
        assertEquals(true, credit.get("credit"));
        assertEquals(-119.0, credit.get("gross"));
        assertNull(credit.get("date"));
        assertNull(credit.get("net"));
        assertNull(credit.get("file"));
        assertNull(credit.get("consumptionUnit"));

        /* no file is read for the list */
        for (String call : store.calls) assertFalse(call, call.startsWith("file"));
        String text = new ObjectMapper().writeValueAsString(answer);
        assertFalse(text.contains("anhang.pdf"));
    }

    @Test
    public void listTruncated() {
        List<JsonObject> all = new ArrayList<>(ALL);
        Store store = new Store();
        for (int i = 0; i < ResourceWebInvoices.MAX_INVOICES + 1; i++) {
            JsonObject invoice = obj(10000 + i, "R" + i, INVOICE, 5);
            all.add(invoice);
            store.set(invoice.getId(), ResourceWebInvoices.ATTRIBUTE_PERIOD_START,
                    new LocalDate(2026, 1, 1).plusDays(i % 300).toString());
        }
        Map<String, Object> answer = ResourceWebInvoices.invoicesJson(lookup(all), classes(), store,
                day("2026-01-01"), day("2026-12-31"));
        assertEquals(ResourceWebInvoices.MAX_INVOICES, list(answer).size());
        assertEquals(true, answer.get("truncated"));
        /* newest first; equal days by id, newest first */
        assertEquals("2026-10-27", list(answer).get(0).get("periodStart"));
        assertEquals(10000L + 1799, list(answer).get(0).get("id"));
    }

    // ---------------------------------------------------------------- detail

    @Test
    @SuppressWarnings("unchecked")
    public void detailWithItems() {
        Map<String, Object> october = ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 100L);
        assertEquals("EN-202610-S", october.get("number"));
        assertEquals("DE000000000", october.get("sellerVatId"));
        List<Map<String, Object>> items = (List<Map<String, Object>>) october.get("items");
        assertEquals(1, items.size());
        assertEquals("Arbeitspreis", items.get(0).get("description"));
        assertEquals(41250.0, items.get(0).get("quantity"));
        assertTrue(items.get(0).containsKey("periodStart"));
        assertNull(items.get(0).get("periodStart"));

        /* broken JSON, no array, no items */
        assertEquals(Collections.emptyList(), ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 101L).get("items"));
        assertEquals(Collections.emptyList(), ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 103L).get("items"));
        assertEquals(Collections.emptyList(), ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 106L).get("items"));
        assertNull(ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 106L).get("sellerVatId"));
        /* also outside of any time frame */
        assertNotNull(ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), 102L));
    }

    @Test
    public void detailNotFound() {
        for (long id : new long[]{HIDDEN.getId(), BIN.getId(), NO_INVOICE.getId(), POWER.getId(), 4711L}) {
            assertNull(String.valueOf(id), ResourceWebInvoices.invoiceDetailJson(lookup(), classes(), store(), id));
        }
    }

    // ---------------------------------------------------------------- file

    private static String body(Response response) {
        return new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
    }

    @Test
    public void filePdf() throws Exception {
        Store store = store();
        Response attachment = ResourceWebInvoices.fileResponse(lookup(), classes(), store, 100L, false);
        assertEquals(200, attachment.getStatus());
        assertEquals("%PDF-1.7 october", body(attachment));
        assertEquals("application/pdf", attachment.getMediaType().toString());
        assertEquals("attachment; filename=\"EN-202610-S.pdf\"; filename*=UTF-8''EN-202610-S.pdf",
                attachment.getHeaderString("Content-Disposition"));
        assertEquals("nosniff", attachment.getHeaderString("X-Content-Type-Options"));
        assertEquals("private, no-store", attachment.getHeaderString("Cache-Control"));
        assertEquals("default-src 'none'; sandbox", attachment.getHeaderString("Content-Security-Policy"));

        Response inline = ResourceWebInvoices.fileResponse(lookup(), classes(), store, 100L, true);
        assertTrue(inline.getHeaderString("Content-Disposition").startsWith("inline; "));
        assertNull(inline.getHeaderString("Content-Security-Policy"));
        /* only "Invoice File" is read */
        for (String call : store.calls) {
            if (call.startsWith("file")) assertEquals("file 100/Invoice File", call);
        }
    }

    @Test
    public void fileXmlNeverInline() throws Exception {
        Response xml = ResourceWebInvoices.fileResponse(lookup(), classes(), store(), XML.getId(), true);
        assertEquals(200, xml.getStatus());
        assertEquals("application/xml", xml.getMediaType().toString());
        assertTrue(xml.getHeaderString("Content-Disposition").startsWith("attachment; filename=\"xrechnung.xml\""));
        assertNotNull(xml.getHeaderString("Content-Security-Policy"));
    }

    @Test
    public void fileWithoutStoredNameUsesObjectName() throws Exception {
        Response response = ResourceWebInvoices.fileResponse(lookup(), classes(), store(), NO_NAME.getId(), false);
        assertEquals(200, response.getStatus());
        assertTrue(response.getHeaderString("Content-Disposition").contains("filename=\"Ohne Name.pdf\""));
    }

    @Test
    public void fileNotFound() throws Exception {
        Store store = store();
        for (long id : new long[]{HIDDEN.getId(), BIN.getId(), NO_INVOICE.getId(), 4711L}) {
            assertEquals(404, ResourceWebInvoices.fileResponse(lookup(), classes(), store, id, false).getStatus());
        }
        for (String call : store.calls) assertFalse(call, call.startsWith("file"));
        /* no file */
        assertEquals(404, ResourceWebInvoices.fileResponse(lookup(), classes(), store, CREDIT.getId(), false).getStatus());
        for (String call : store.calls) assertFalse(call, call.startsWith("file"));
    }

    @Test
    public void unsafeStoredNameIsNotServed() throws Exception {
        Store store = new Store() {
            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return ResourceWebAlarms.sampleFile(folder.getRoot(), id, attribute, sample);
            }
        };
        for (String name : new String[]{"../../etc/passwd", "..\\x.pdf", "sub/x.pdf"}) {
            store.set(100, ResourceWebInvoices.ATTRIBUTE_FILE, name);
            assertEquals(name, 404, ResourceWebInvoices.fileResponse(lookup(), classes(), store, 100L, false).getStatus());
        }
    }

    @Test
    public void contentTypeFromBytes() {
        assertEquals("application/pdf", ResourceWebInvoices.contentType(bytes("%PDF-1.7")));
        assertEquals("application/xml", ResourceWebInvoices.contentType(bytes("<?xml version=\"1.0\"?>")));
        assertEquals("application/xml", ResourceWebInvoices.contentType(bytes("﻿\r\n <rsm:CrossIndustryInvoice/>")));
        assertEquals("application/octet-stream", ResourceWebInvoices.contentType(bytes("PK\u0003\u0004")));
        assertEquals("application/octet-stream", ResourceWebInvoices.contentType(bytes("%PD")));
        assertEquals("application/octet-stream", ResourceWebInvoices.contentType(new byte[0]));
        assertEquals("application/octet-stream", ResourceWebInvoices.contentType(null));
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void moduleCount() {
        List<JsonObject> readable = new ArrayList<>(ALL);
        readable.remove(HIDDEN);
        /* 100–103, 106–108; not hidden 104, not 105 in the recycle bin */
        assertEquals(7, ResourceWebContracts.moduleCounts(readable, classes()).get("invoices"));
        assertEquals(0, ResourceWebContracts.moduleCounts(readable, classes()).get("contracts"));
    }
}
