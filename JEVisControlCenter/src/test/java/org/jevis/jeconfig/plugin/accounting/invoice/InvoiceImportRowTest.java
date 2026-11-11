/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.accounting.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.File;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.Assert.*;

public class InvoiceImportRowTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /* example answer of POST /invoices/parse from docs/ENERGY-INVOICES.md, neutral data */
    private static final String PARSE_ANSWER = "{"
            + "\"fileName\": \"invoice.pdf\", \"format\": \"ZUGFeRD 2.3 EN16931\","
            + "\"number\": \"R-1001\", \"date\": \"2026-10-05\", \"seller\": \"Supplier A\", \"sellerVatId\": \"DE000000000\","
            + "\"buyer\": \"Customer B\", \"periodStart\": \"2026-09-01\", \"periodEnd\": \"2026-09-30\", \"currency\": \"EUR\","
            + "\"net\": 1234.56, \"tax\": 234.57, \"gross\": 1469.13,"
            + "\"consumption\": { \"value\": 4321.0, \"unit\": \"kWh\" },"
            + "\"meterPoint\": \"DE0000000000000000000000000000001\","
            + "\"medium\": \"ELECTRICITY\", \"mediumReasons\": [\"unit kWh\", \"line text 'Strom'\"],"
            + "\"items\": [ { \"description\": \"Arbeitspreis\", \"quantity\": 4321.0, \"unit\": \"kWh\", \"unitPrice\": 0.25,"
            + "\"net\": 1080.25, \"taxRate\": 19.0, \"periodStart\": null, \"periodEnd\": null } ],"
            + "\"duplicate\": null, \"warnings\": []"
            + "}";

    private static JsonNode json(String s) throws Exception {
        return MAPPER.readTree(s);
    }

    @Test
    public void mapsAllFields() throws Exception {
        File file = new File("upload-1.pdf");
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(file, json(PARSE_ANSWER));

        assertSame(file, row.getFile());
        assertEquals("upload-1.pdf", row.getFileName());
        assertEquals("ZUGFeRD 2.3 EN16931", row.getFormat());
        assertEquals("R-1001", row.getNumber());
        assertEquals(LocalDate.of(2026, 10, 5), row.getDate());
        assertEquals("Supplier A", row.getSeller());
        assertEquals("DE000000000", row.getSellerVatId());
        assertEquals("Customer B", row.getBuyer());
        assertEquals(LocalDate.of(2026, 9, 1), row.getPeriodStart());
        assertEquals(LocalDate.of(2026, 9, 30), row.getPeriodEnd());
        assertEquals("EUR", row.getCurrency());
        assertEquals(1234.56, row.getNet(), 1e-9);
        assertEquals(234.57, row.getTax(), 1e-9);
        assertEquals(1469.13, row.getGross(), 1e-9);
        assertEquals(4321.0, row.getConsumption(), 1e-9);
        assertEquals("kWh", row.getConsumptionUnit());
        assertEquals("DE0000000000000000000000000000001", row.getMeterPoint());
        assertEquals(InvoiceMedium.ELECTRICITY, row.getDetectedMedium());
        assertEquals(InvoiceMedium.ELECTRICITY, row.getMedium());
        assertEquals(Arrays.asList("unit kWh", "line text 'Strom'"), row.getMediumReasons());
        assertTrue(row.getWarnings().isEmpty());
        assertNull(row.getDuplicateId());
    }

    @Test
    public void newInvoiceIsSelected() throws Exception {
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("a.xml"), json(PARSE_ANSWER));
        assertEquals(InvoiceImportRow.Status.NEW, row.getStatus());
        assertTrue(row.isSelected());
        assertTrue(row.isImportable());
    }

    @Test
    public void duplicateIsNotSelected() throws Exception {
        String answer = PARSE_ANSWER.replace("\"duplicate\": null", "\"duplicate\": 4711");
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("a.pdf"), json(answer));
        assertEquals(InvoiceImportRow.Status.DUPLICATE, row.getStatus());
        assertEquals(Long.valueOf(4711), row.getDuplicateId());
        assertFalse(row.isSelected());
        assertTrue(row.isImportable());
    }

    @Test
    public void missingOptionalValues() throws Exception {
        String answer = "{\"format\": \"XRechnung 3.0 UBL\", \"number\": \"R-2\", \"consumption\": null,"
                + " \"medium\": \"SOMETHING\", \"periodStart\": null, \"net\": \"12.5\", \"gross\": null}";
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("b.xml"), json(answer));
        assertNull(row.getConsumption());
        assertNull(row.getConsumptionUnit());
        assertNull(row.getPeriodStart());
        assertNull(row.getDate());
        assertNull(row.getGross());
        assertEquals(12.5, row.getNet(), 1e-9);
        assertEquals(InvoiceMedium.OTHER, row.getMedium());
        assertTrue(row.getMediumReasons().isEmpty());
        assertEquals(InvoiceImportRow.Status.NEW, row.getStatus());
    }

    @Test
    public void gasInCubicMetres() throws Exception {
        String answer = "{\"number\": \"G-7\", \"consumption\": {\"value\": 812.5, \"unit\": \"m³\"}, \"medium\": \"GAS\","
                + " \"warnings\": [\"no meter point\"]}";
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("c.pdf"), json(answer));
        assertEquals(812.5, row.getConsumption(), 1e-9);
        assertEquals("m³", row.getConsumptionUnit());
        assertEquals(InvoiceMedium.GAS, row.getMedium());
        assertEquals(Arrays.asList("no meter point"), row.getWarnings());
    }

    @Test
    public void dateTimeIsCutToDate() throws Exception {
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("d.pdf"), json("{\"date\": \"2026-10-05T00:00:00Z\", \"periodEnd\": \"05.10.2026\"}"));
        assertEquals(LocalDate.of(2026, 10, 5), row.getDate());
        assertNull(row.getPeriodEnd());
    }

    @Test
    public void mediumCanBeChanged() throws Exception {
        InvoiceImportRow row = InvoiceImportRow.fromParseResult(new File("a.pdf"), json(PARSE_ANSWER));
        row.setMedium(InvoiceMedium.DISTRICT_HEATING);
        assertEquals("DISTRICT_HEATING", row.getMedium().getCode());
        assertEquals(InvoiceMedium.ELECTRICITY, row.getDetectedMedium());
        row.setMedium(null);
        assertEquals(InvoiceMedium.OTHER, row.getMedium());
    }

    @Test
    public void errorRow() {
        InvoiceImportRow row = InvoiceImportRow.fromError(new File("scan.pdf"), 400, "NOT_AN_E_INVOICE", "no XML found");
        assertEquals("scan.pdf", row.getFileName());
        assertEquals(InvoiceImportRow.Status.ERROR, row.getStatus());
        assertFalse(row.isSelected());
        assertFalse(row.isImportable());
        assertEquals("no XML found", row.getMessage());
        assertEquals("plugin.accounting.invoices.error.noeinvoice", row.getMessageKey());
    }

    @Test
    public void errorKeys() {
        assertEquals("plugin.accounting.invoices.error.noeinvoice", InvoiceImportRow.errorKey(400, "NOT_AN_E_INVOICE"));
        assertEquals("plugin.accounting.invoices.error.unreadable", InvoiceImportRow.errorKey(400, "UNREADABLE"));
        assertEquals("plugin.accounting.invoices.error.duplicate", InvoiceImportRow.errorKey(409, "DUPLICATE"));
        assertEquals("plugin.accounting.invoices.error.unauthorized", InvoiceImportRow.errorKey(401, null));
        assertEquals("plugin.accounting.invoices.error.forbidden", InvoiceImportRow.errorKey(403, null));
        assertEquals("plugin.accounting.invoices.error.notsupported", InvoiceImportRow.errorKey(404, null));
        assertEquals("plugin.accounting.invoices.error.toolarge", InvoiceImportRow.errorKey(413, null));
        assertNull(InvoiceImportRow.errorKey(400, null));
        assertNull(InvoiceImportRow.errorKey(500, "SOMETHING"));
        assertNull(InvoiceImportRow.errorKey(0, null));
    }

    @Test
    public void importResults() throws Exception {
        InvoiceImportRow imported = InvoiceImportRow.fromParseResult(new File("a.pdf"), json(PARSE_ANSWER));
        imported.markImported(100L);
        assertEquals(InvoiceImportRow.Status.IMPORTED, imported.getStatus());
        assertEquals(Long.valueOf(100), imported.getImportedId());
        assertFalse(imported.isSelected());
        assertFalse(imported.isImportable());

        InvoiceImportRow duplicate = InvoiceImportRow.fromParseResult(new File("a.pdf"), json(PARSE_ANSWER));
        duplicate.markDuplicate(55L);
        assertEquals(InvoiceImportRow.Status.SKIPPED, duplicate.getStatus());
        assertEquals(Long.valueOf(55), duplicate.getDuplicateId());

        InvoiceImportRow failed = InvoiceImportRow.fromParseResult(new File("a.pdf"), json(PARSE_ANSWER));
        failed.markFailed(403, null, "Forbidden");
        assertEquals(InvoiceImportRow.Status.FAILED, failed.getStatus());
        assertEquals("plugin.accounting.invoices.error.forbidden", failed.getMessageKey());
        assertEquals("Forbidden", failed.getMessage());
    }
}
