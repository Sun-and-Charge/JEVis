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
package org.jevis.rest.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.classes.JC;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Endpoint mapping of {@link ResourceInvoices} and the import ({@link InvoiceImport}) on an in-memory store.
 */
public class ResourceInvoicesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    private static final long ROOT = 1;
    private static final long DIRECTORY = 10;
    private static final long OTHER_FOLDER = 20;

    private MemoryInvoiceStore store;

    @Before
    public void setUp() {
        store = new MemoryInvoiceStore();
        store.add(ROOT, "Organisation", "Organization", 0);
        store.add(DIRECTORY, "Energierechnungen", InvoiceImport.CLASS_DIRECTORY, ROOT);
        store.add(OTHER_FOLDER, "Data", "Data Directory", ROOT);
    }

    private static JsonNode body(Response response) throws Exception {
        return MAPPER.readTree((String) response.getEntity());
    }

    private Response importFile(byte[] file, String name, String medium) throws Exception {
        return ResourceInvoices.importInvoice(file, name, DIRECTORY, medium, store, ZONE);
    }

    private static DateTime day(int year, int month, int day) {
        return new DateTime(year, month, day, 0, 0, ZONE);
    }

    // ---------------------------------------------------------------- parse

    @Test
    public void parse() throws Exception {
        Response response = ResourceInvoices.parse(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")),
                "invoice.pdf", DIRECTORY, store);
        assertEquals(200, response.getStatus());
        JsonNode json = body(response);
        assertEquals("invoice.pdf", json.get("fileName").asText());
        assertEquals("R-1001", json.get("number").asText());
        assertEquals("ELECTRICITY", json.get("medium").asText());
        assertEquals(4321.0, json.get("consumption").get("value").asDouble(), 1e-9);
        assertEquals("kWh", json.get("consumption").get("unit").asText());
        assertTrue(json.get("duplicate").isNull());
        assertEquals(5, json.get("items").size());
    }

    @Test
    public void parseErrors() throws Exception {
        Response response = ResourceInvoices.parse(TestInvoices.plainPdf(), "scan.pdf", null, store);
        assertEquals(400, response.getStatus());
        assertEquals(InvoiceException.NOT_AN_E_INVOICE, body(response).get("error").asText());
        assertFalse(body(response).get("message").asText().isEmpty());

        response = ResourceInvoices.parse("%PDF-broken".getBytes(StandardCharsets.US_ASCII), "x.pdf", null, store);
        assertEquals(400, response.getStatus());
        assertEquals(InvoiceException.UNREADABLE, body(response).get("error").asText());
    }

    @Test
    public void parseFindsDuplicateBelowDirectory() throws Exception {
        assertEquals(201, importFile(TestInvoices.ublDistrictHeating("W-3003"), "w.xml", "DISTRICT_HEATING").getStatus());
        long invoiceId = store.ofClass(InvoiceImport.CLASS_INVOICE).get(0).getId();

        JsonNode json = body(ResourceInvoices.parse(TestInvoices.ublDistrictHeating("W-3003"), "w.xml", DIRECTORY, store));
        assertEquals(invoiceId, json.get("duplicate").asLong());
        /* another number */
        json = body(ResourceInvoices.parse(TestInvoices.ublDistrictHeating("W-3004"), "w.xml", DIRECTORY, store));
        assertTrue(json.get("duplicate").isNull());
        /* without directory, or a directory of another class: no duplicate check */
        json = body(ResourceInvoices.parse(TestInvoices.ublDistrictHeating("W-3003"), "w.xml", null, store));
        assertTrue(json.get("duplicate").isNull());
        json = body(ResourceInvoices.parse(TestInvoices.ublDistrictHeating("W-3003"), "w.xml", OTHER_FOLDER, store));
        assertTrue(json.get("duplicate").isNull());
        /* not readable */
        store.unreadable.add(DIRECTORY);
        json = body(ResourceInvoices.parse(TestInvoices.ublDistrictHeating("W-3003"), "w.xml", DIRECTORY, store));
        assertTrue(json.get("duplicate").isNull());
    }

    @Test
    public void fileSize() throws Exception {
        assertEquals(3, ResourceInvoices.read(new ByteArrayInputStream(new byte[3]), 3).length);
        try {
            ResourceInvoices.read(new ByteArrayInputStream(new byte[4]), 3);
            fail();
        } catch (InvoiceException e) {
            assertEquals(413, e.getStatus());
            assertEquals(413, ResourceInvoices.error(e).getStatus());
            assertEquals(InvoiceException.TOO_LARGE, body(ResourceInvoices.error(e)).get("error").asText());
        }
        try {
            ResourceInvoices.read((java.io.InputStream) null, 3);
            fail();
        } catch (InvoiceException e) {
            assertEquals(400, e.getStatus());
        }
    }

    // ---------------------------------------------------------------- import

    @Test
    public void importCreatesFolderSeriesAndInvoice() throws Exception {
        byte[] pdf = TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001"));
        Response response = importFile(pdf, "invoice.pdf", "ELECTRICITY");
        assertEquals(201, response.getStatus());
        JsonNode json = body(response);

        /* medium folder */
        long folderId = json.get("folderId").asLong();
        JsonObject folder = store.objects.get(folderId);
        assertEquals(InvoiceImport.CLASS_DIRECTORY, folder.getJevisClass());
        assertEquals("Strom", folder.getName());
        assertEquals(DIRECTORY, folder.getParent());
        assertEquals("Electricity", store.localNames.get(folderId).get("en"));
        assertEquals("ELECTRICITY", store.latest(folderId, JC.Directory.EnergyInvoicesDirectory.a_Medium));

        /* series */
        Map<String, Long> series = new HashMap<>();
        json.get("series").fields().forEachRemaining(e -> series.put(e.getKey(), e.getValue().asLong()));
        assertEquals(new HashSet<>(Arrays.asList("CONSUMPTION", "NET", "GROSS")), series.keySet());
        for (Map.Entry<String, Long> entry : series.entrySet()) {
            JsonObject object = store.objects.get(entry.getValue());
            assertEquals(InvoiceImport.CLASS_DATA, object.getJevisClass());
            assertEquals(folderId, object.getParent());
            assertEquals(entry.getKey(), store.latest(object.getId(), JC.Data.EnergyInvoiceData.a_Series));
        }
        assertEquals("Verbrauch laut Rechnung", store.objects.get(series.get("CONSUMPTION")).getName());
        assertEquals("kWh", store.unit(series.get("CONSUMPTION"), "Value"));
        assertEquals("€", store.unit(series.get("NET"), "Value"));
        JsonSample consumption = store.sampleAt(series.get("CONSUMPTION"), "Value", day(2026, 9, 1));
        assertEquals("4321", consumption.getValue());
        assertEquals("R-1001", consumption.getNote());
        assertEquals("1460.96", store.sampleAt(series.get("NET"), "Value", day(2026, 9, 1)).getValue());
        assertEquals("1738.54", store.sampleAt(series.get("GROSS"), "Value", day(2026, 9, 1)).getValue());

        /* invoice */
        long invoiceId = json.get("invoiceId").asLong();
        JsonObject invoice = store.objects.get(invoiceId);
        assertEquals(InvoiceImport.CLASS_INVOICE, invoice.getJevisClass());
        assertEquals("R-1001 Supplier A", invoice.getName());
        assertEquals(folderId, invoice.getParent());
        assertEquals("R-1001", store.latest(invoiceId, JC.EnergyInvoice.a_InvoiceNumber));
        assertEquals("ZUGFeRD/Factur-X EN16931", store.latest(invoiceId, JC.EnergyInvoice.a_Format));
        assertEquals("Supplier A", store.latest(invoiceId, JC.EnergyInvoice.a_Seller));
        assertEquals("DE000000000", store.latest(invoiceId, JC.EnergyInvoice.a_SellerVATID));
        assertEquals("Customer B", store.latest(invoiceId, JC.EnergyInvoice.a_Buyer));
        assertEquals("2026-09-01T00:00:00.000+02:00", store.latest(invoiceId, JC.EnergyInvoice.a_PeriodStart));
        assertEquals("2026-09-30T00:00:00.000+02:00", store.latest(invoiceId, JC.EnergyInvoice.a_PeriodEnd));
        assertEquals("2026-10-05T00:00:00.000+02:00", store.latest(invoiceId, JC.EnergyInvoice.a_InvoiceDate));
        assertEquals("ELECTRICITY", store.latest(invoiceId, JC.EnergyInvoice.a_Medium));
        assertEquals(TestInvoices.METER_POINT, store.latest(invoiceId, JC.EnergyInvoice.a_MeterPoint));
        assertEquals("4321", store.latest(invoiceId, JC.EnergyInvoice.a_Consumption));
        assertEquals("kWh", store.unit(invoiceId, JC.EnergyInvoice.a_Consumption));
        assertEquals("1460.96", store.latest(invoiceId, JC.EnergyInvoice.a_NetAmount));
        assertEquals("277.58", store.latest(invoiceId, JC.EnergyInvoice.a_TaxAmount));
        assertEquals("1738.54", store.latest(invoiceId, JC.EnergyInvoice.a_GrossAmount));
        assertEquals("€", store.unit(invoiceId, JC.EnergyInvoice.a_GrossAmount));
        assertEquals("EUR", store.latest(invoiceId, JC.EnergyInvoice.a_Currency));
        JsonNode items = MAPPER.readTree(store.latest(invoiceId, JC.EnergyInvoice.a_LineItems));
        assertEquals(5, items.size());
        assertEquals("Grundpreis", items.get(4).get("description").asText());
        /* all at the period start with the invoice number as note */
        JsonSample number = store.sampleAt(invoiceId, JC.EnergyInvoice.a_InvoiceNumber, day(2026, 9, 1));
        assertEquals("R-1001", number.getNote());
        assertEquals(Integer.valueOf(JEVisConstants.PrimitiveType.DOUBLE),
                store.primitiveTypes.get(invoiceId + "/" + JC.EnergyInvoice.a_NetAmount));
        /* file */
        JsonSample file = store.sampleAt(invoiceId, JC.EnergyInvoice.a_InvoiceFile, day(2026, 9, 1));
        assertEquals("invoice.pdf", file.getValue());
        assertArrayEquals(pdf, store.files.get(invoiceId + "/" + JC.EnergyInvoice.a_InvoiceFile + "/invoice.pdf"));
        assertEquals(Integer.valueOf(JEVisConstants.PrimitiveType.FILE),
                store.primitiveTypes.get(invoiceId + "/" + JC.EnergyInvoice.a_InvoiceFile));
    }

    @Test
    public void secondInvoiceIsAddedToTheSeries() throws Exception {
        JsonNode first = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf", "ELECTRICITY"));
        /* same period, another meter point / invoice */
        JsonNode second = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1002")), "b.pdf", "ELECTRICITY"));
        assertEquals(first.get("folderId").asLong(), second.get("folderId").asLong());
        assertEquals(first.get("series"), second.get("series"));
        assertEquals(1, store.ofClass(InvoiceImport.CLASS_DIRECTORY).size() - 1);
        assertEquals(3, store.ofClass(InvoiceImport.CLASS_DATA).size());
        assertEquals(2, store.ofClass(InvoiceImport.CLASS_INVOICE).size());

        JsonSample consumption = store.sampleAt(first.get("series").get("CONSUMPTION").asLong(), "Value", day(2026, 9, 1));
        assertEquals("8642", consumption.getValue());
        assertEquals("R-1001, R-1002", consumption.getNote());
        assertEquals("2921.92", store.sampleAt(first.get("series").get("NET").asLong(), "Value", day(2026, 9, 1)).getValue());

        /* another medium gets its own folder */
        JsonNode gas = body(importFile(TestInvoices.cii(TestInvoices.gas("G-2002"), "XRECHNUNG"), "g.xml", "GAS"));
        assertNotEquals(first.get("folderId").asLong(), gas.get("folderId").asLong());
        assertEquals("Gas", store.objects.get(gas.get("folderId").asLong()).getName());
        /* gas: time stamp is the start of the period of the lines */
        assertEquals("10000", store.sampleAt(gas.get("series").get("CONSUMPTION").asLong(), "Value", day(2026, 1, 1)).getValue());
    }

    @Test
    public void creditNoteIsSubtracted() throws Exception {
        JsonNode invoice = body(importFile(TestInvoices.ublDistrictHeating("W-4001"), "w.xml", "DISTRICT_HEATING"));
        byte[] creditNote = new String(TestInvoices.ublDistrictHeating("W-4002"), java.nio.charset.StandardCharsets.UTF_8)
                .replace("<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>", "<cbc:InvoiceTypeCode>381</cbc:InvoiceTypeCode>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        JsonNode parsed = body(ResourceInvoices.parse(creditNote, "g.xml", DIRECTORY, store));
        assertEquals(-12500, parsed.get("consumption").get("value").asDouble(), 0.001);
        assertEquals(-1330, parsed.get("net").asDouble(), 0.001);
        assertEquals(-1582.7, parsed.get("gross").asDouble(), 0.001);
        assertTrue(parsed.get("warnings").toString().contains("credit note"));

        JsonNode credit = body(importFile(creditNote, "g.xml", "DISTRICT_HEATING"));
        assertEquals(invoice.get("folderId").asLong(), credit.get("folderId").asLong());
        JsonSample consumption = store.sampleAt(invoice.get("series").get("CONSUMPTION").asLong(), "Value", day(2026, 1, 1));
        assertEquals(0, Double.parseDouble(consumption.getValue()), 0.001);
        assertEquals("W-4001, W-4002", consumption.getNote());
        assertEquals(0, Double.parseDouble(store.sampleAt(invoice.get("series").get("GROSS").asLong(), "Value",
                day(2026, 1, 1)).getValue()), 0.001);
        assertEquals(-1330, Double.parseDouble(store.sampleAt(credit.get("invoiceId").asLong(), JC.EnergyInvoice.a_NetAmount,
                day(2026, 1, 1)).getValue()), 0.001);
    }

    @Test
    public void duplicate() throws Exception {
        long invoiceId = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf", "ELECTRICITY"))
                .get("invoiceId").asLong();
        int writes = store.writes;
        /* the duplicate is found in any medium folder below the directory */
        Response response = importFile(TestInvoices.cii(TestInvoices.electricity("R-1001"), "EN16931"), "a.xml", "OTHER");
        assertEquals(409, response.getStatus());
        assertEquals("DUPLICATE", body(response).get("error").asText());
        assertEquals(invoiceId, body(response).get("existing").asLong());
        assertEquals(writes, store.writes);
    }

    @Test
    public void failedImportIsRolledBack() throws Exception {
        String value = org.jevis.commons.classes.JC.Data.EnergyInvoiceData.a_Value;
        /* the first import fails at the last step: folder, series and invoice are removed again */
        store.failOnAttribute = value;
        assertImportFails(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf");
        assertEquals(3, store.objects.size());
        assertTrue(store.files.isEmpty());

        /* a second invoice of the same period fails: the sums of the first one stay as they were */
        store.failOnAttribute = null;
        JsonNode first = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf", "ELECTRICITY"));
        long consumption = first.get("series").get("CONSUMPTION").asLong();
        String before = store.sampleAt(consumption, value, day(2026, 9, 1)) == null ? null
                : store.sampleAt(consumption, value, day(2026, 9, 1)).getValue();
        int objects = store.objects.size();
        store.failOnAttribute = value;
        assertImportFails(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1002")), "b.pdf");
        assertEquals(objects, store.objects.size());
        assertEquals(before, store.sampleAt(consumption, value, day(2026, 9, 1)).getValue());
    }

    /** the endpoint answers 500 for this exception */
    private void assertImportFails(byte[] file, String name) throws Exception {
        try {
            importFile(file, name, "ELECTRICITY");
            fail("the import should fail");
        } catch (IllegalStateException expected) {
            assertEquals("test failure", expected.getMessage());
        }
    }

    @Test
    public void rights() throws Exception {
        /* no create right on the directory */
        store.noCreate.add(DIRECTORY);
        Response response = importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf", "ELECTRICITY");
        assertEquals(403, response.getStatus());
        assertEquals(0, store.writes);
        store.noCreate.clear();

        JsonNode first = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")), "a.pdf", "ELECTRICITY"));
        int writes = store.writes;
        /* no write right on an existing series */
        store.noWrite.add(first.get("series").get("GROSS").asLong());
        response = importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1002")), "b.pdf", "ELECTRICITY");
        assertEquals(403, response.getStatus());
        assertEquals("FORBIDDEN", body(response).get("error").asText());
        assertEquals(writes, store.writes);
        store.noWrite.clear();
        /* no create right on the medium folder */
        store.noCreate.add(first.get("folderId").asLong());
        assertEquals(403, importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1002")), "b.pdf", "ELECTRICITY").getStatus());
        assertEquals(writes, store.writes);
    }

    @Test
    public void wrongRequest() throws Exception {
        byte[] file = TestInvoices.cii(TestInvoices.gas("G-1"), "EN16931");
        Response response = importFile(file, "g.xml", "WATER");
        assertEquals(400, response.getStatus());
        assertEquals(InvoiceException.INVALID_MEDIUM, body(response).get("error").asText());
        assertEquals(400, importFile(file, "g.xml", null).getStatus());

        response = ResourceInvoices.importInvoice(file, "g.xml", OTHER_FOLDER, "GAS", store, ZONE);
        assertEquals(400, response.getStatus());
        assertEquals(InvoiceException.INVALID_DIRECTORY, body(response).get("error").asText());
        assertEquals(400, ResourceInvoices.importInvoice(file, "g.xml", null, "GAS", store, ZONE).getStatus());
        assertEquals(400, ResourceInvoices.importInvoice(file, "g.xml", 999L, "GAS", store, ZONE).getStatus());

        response = importFile(TestInvoices.plainPdf(), "scan.pdf", "GAS");
        assertEquals(400, response.getStatus());
        assertEquals(InvoiceException.NOT_AN_E_INVOICE, body(response).get("error").asText());
        assertEquals(0, store.writes);
    }

    @Test
    public void mediumFolderIsFoundByMediumNotByName() throws Exception {
        JsonObject renamed = store.add(30, "Gas renamed", InvoiceImport.CLASS_DIRECTORY, DIRECTORY);
        store.setSample(30, JC.Directory.EnergyInvoicesDirectory.a_Medium, 0, day(2026, 1, 1), "GAS", null);
        JsonObject wrongName = store.add(31, "Strom", InvoiceImport.CLASS_DIRECTORY, DIRECTORY);
        store.setSample(31, JC.Directory.EnergyInvoicesDirectory.a_Medium, 0, day(2026, 1, 1), "OTHER", null);
        JsonNode json = body(importFile(TestInvoices.cii(TestInvoices.gas("G-1"), "EN16931"), "g.xml", "GAS"));
        assertEquals(renamed.getId(), json.get("folderId").asLong());
        json = body(importFile(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1")), "r.pdf", "ELECTRICITY"));
        assertNotEquals(wrongName.getId(), json.get("folderId").asLong());

        /* the chosen directory is itself the medium folder */
        JsonNode inFolder = body(ResourceInvoices.importInvoice(TestInvoices.cii(TestInvoices.gas("G-2"), "EN16931"), "g2.xml",
                renamed.getId(), "GAS", store, ZONE));
        assertEquals(renamed.getId(), inFolder.get("folderId").asLong());
    }

    @Test
    public void consumptionUnitMismatch() throws Exception {
        JsonNode first = body(importFile(TestInvoices.cii(TestInvoices.gasCubicMetres("D-1"), "EN16931"), "d.xml", "GAS"));
        assertEquals("m³", store.unit(first.get("series").get("CONSUMPTION").asLong(), "Value"));
        JsonNode second = body(importFile(TestInvoices.cii(TestInvoices.gas("G-1"), "EN16931"), "g.xml", "GAS"));
        assertEquals(1, second.get("warnings").size());
        assertNull(store.sampleAt(first.get("series").get("CONSUMPTION").asLong(), "Value", day(2026, 1, 1)));
        /* costs are added anyway */
        assertNotNull(store.sampleAt(first.get("series").get("NET").asLong(), "Value", day(2026, 1, 1)));
    }

    @Test
    public void helpers() {
        assertEquals("invoice.pdf", InvoiceImport.safeFileName("C:\\Users\\x\\invoice.pdf"));
        assertEquals("a_b.pdf", InvoiceImport.safeFileName("../a:b.pdf"));
        assertEquals("invoice", InvoiceImport.safeFileName(null));
        assertEquals("invoice", InvoiceImport.safeFileName("dir/"));
        assertEquals("€", InvoiceImport.currencySymbol("EUR"));
        assertEquals("CHF", InvoiceImport.currencySymbol("CHF"));
        assertEquals("XXY", InvoiceImport.currencySymbol("XXY"));
        assertNull(InvoiceImport.currencySymbol(null));
        assertEquals(Medium.DISTRICT_HEATING, Medium.parse(" district_heating "));
        assertNull(Medium.parse("WATER"));
    }
}
