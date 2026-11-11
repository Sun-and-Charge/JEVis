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

import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.Assert.*;

public class InvoiceReaderTest {

    private static void assertAmount(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private static InvoiceException readFails(byte[] bytes) {
        try {
            InvoiceReader.read(bytes);
            fail("no exception");
            return null;
        } catch (InvoiceException e) {
            assertEquals(400, e.getStatus());
            return e;
        }
    }

    @Test
    public void zugferdPdf() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")));
        assertEquals("ZUGFeRD/Factur-X EN16931", invoice.format);
        assertEquals("R-1001", invoice.number);
        assertEquals(LocalDate.of(2026, 10, 5), invoice.date);
        assertEquals("Supplier A", invoice.seller);
        assertEquals("DE000000000", invoice.sellerVatId);
        assertEquals("Customer B", invoice.buyer);
        assertEquals(LocalDate.of(2026, 9, 1), invoice.periodStart);
        assertEquals(LocalDate.of(2026, 9, 30), invoice.periodEnd);
        assertEquals("EUR", invoice.currency);
        /* 750 + 264.20 + 345.68 + 88.58 (88.5805) + 12.50 */
        assertAmount("1460.96", invoice.net);
        assertAmount("277.58", invoice.tax);
        assertAmount("1738.54", invoice.gross);
        assertFalse(invoice.creditNote);
        assertEquals(5, invoice.lines.size());
        EInvoice.Line first = invoice.lines.get(0);
        assertEquals("Arbeitspreis Strom HT", first.label());
        assertEquals("KWH", first.unitCode);
        assertAmount("3000", first.quantity);
        assertAmount("0.25", first.unitPrice);
        assertAmount("750.00", first.net);
        assertAmount("19", first.taxRate);
        assertEquals("MON", invoice.lines.get(4).unitCode);
        assertTrue(invoice.notes.toString(), invoice.notes.contains("Zählpunkt " + TestInvoices.METER_POINT));
        assertNotNull(invoice.xml);
    }

    @Test
    public void xrechnungCii() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.cii(TestInvoices.gas("G-2002"), "XRECHNUNG"));
        assertTrue(invoice.format, invoice.format.startsWith("XRechnung"));
        assertTrue(invoice.format, invoice.format.endsWith(" CII"));
        assertEquals("G-2002", invoice.number);
        assertEquals("Supplier B", invoice.seller);
        assertNull(invoice.periodStart);
        assertEquals(3, invoice.lines.size());
        EInvoice.Line gas = invoice.lines.get(0);
        assertEquals(LocalDate.of(2026, 1, 1), gas.periodStart);
        assertEquals(LocalDate.of(2026, 3, 31), gas.periodEnd);
        assertTrue(gas.texts.toString(), gas.texts.contains("Marktlokation: " + TestInvoices.MARKET_LOCATION));
        assertEquals("MTQ", invoice.lines.get(1).unitCode);
        assertEquals("DAY", invoice.lines.get(2).unitCode);
        /* 900 + 0 + 45 */
        assertAmount("945.00", invoice.net);
        assertAmount("1124.55", invoice.gross);
    }

    @Test
    public void ciiXml() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.cii(TestInvoices.gasCubicMetres("D-1"), "EN16931"));
        assertEquals("ZUGFeRD/Factur-X EN16931 (XML)", invoice.format);
        assertEquals("Supplier D", invoice.seller);
        assertNull(invoice.sellerVatId);
    }

    @Test
    public void xrechnungUbl() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.ublDistrictHeating("W-3003"));
        assertEquals("XRechnung 3.0 UBL", invoice.format);
        assertEquals("W-3003", invoice.number);
        assertEquals(LocalDate.of(2026, 2, 10), invoice.date);
        assertEquals("Supplier C", invoice.seller);
        assertEquals("DE000000003", invoice.sellerVatId);
        assertEquals("Customer B", invoice.buyer);
        assertEquals(LocalDate.of(2026, 1, 1), invoice.periodStart);
        assertEquals(LocalDate.of(2026, 1, 31), invoice.periodEnd);
        assertEquals("EUR", invoice.currency);
        assertAmount("1330.00", invoice.net);
        assertAmount("252.70", invoice.tax);
        assertAmount("1582.70", invoice.gross);
        assertEquals(2, invoice.lines.size());
        EInvoice.Line heat = invoice.lines.get(0);
        assertEquals("Wärmearbeitspreis", heat.label());
        assertEquals("MWH", heat.unitCode);
        assertAmount("12.5", heat.quantity);
        assertAmount("1250.00", heat.net);
        assertEquals(LocalDate.of(2026, 1, 1), heat.periodStart);
        assertNull(invoice.lines.get(1).periodStart);
        assertTrue(invoice.deliveryTexts.toString(), invoice.deliveryTexts.contains(TestInvoices.MARKET_LOCATION));
        assertTrue(invoice.notes.contains("Abrechnung Januar 2026"));
    }

    @Test
    public void ublCreditNote() throws Exception {
        String xml = new String(TestInvoices.ublDistrictHeating("W-1"), StandardCharsets.UTF_8)
                .replace("<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>", "<cbc:InvoiceTypeCode>381</cbc:InvoiceTypeCode>");
        assertTrue(InvoiceReader.read(xml.getBytes(StandardCharsets.UTF_8)).creditNote);
    }

    @Test
    public void notAnEInvoice() throws Exception {
        assertEquals(InvoiceException.NOT_AN_E_INVOICE, readFails(TestInvoices.plainPdf()).getCode());
        assertEquals(InvoiceException.NOT_AN_E_INVOICE, readFails("just text".getBytes(StandardCharsets.UTF_8)).getCode());
        assertEquals(InvoiceException.NOT_AN_E_INVOICE,
                readFails("<?xml version=\"1.0\"?><order><id>1</id></order>".getBytes(StandardCharsets.UTF_8)).getCode());
    }

    @Test
    public void unreadable() {
        assertEquals(InvoiceException.UNREADABLE, readFails(new byte[0]).getCode());
        assertEquals(InvoiceException.UNREADABLE, readFails("%PDF-1.7 broken".getBytes(StandardCharsets.US_ASCII)).getCode());
    }

    @Test
    public void xmlWithDoctypeIsRejected() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\"><ID>&e;</ID></Invoice>";
        assertEquals(InvoiceException.NOT_AN_E_INVOICE, readFails(xml.getBytes(StandardCharsets.UTF_8)).getCode());
    }

    @Test
    public void formatNames() {
        assertEquals("XRechnung 2.3 CII (PDF)", InvoiceReader.format(InvoiceReader.Syntax.CII,
                "urn:cen.eu:en16931:2017#compliant#urn:xoev-de:kosit:standard:xrechnung_2.3", true, "factur-x.xml", false));
        assertEquals("ZUGFeRD 2.0 BASIC", InvoiceReader.format(InvoiceReader.Syntax.CII,
                "urn:cen.eu:en16931:2017#compliant#urn:zugferd.de:2p0:basic", true, "zugferd-invoice.xml", false));
        assertEquals("ZUGFeRD/Factur-X EXTENDED", InvoiceReader.format(InvoiceReader.Syntax.CII,
                "urn:cen.eu:en16931:2017#conformant#urn:factur-x.eu:1p0:extended", true, "factur-x.xml", false));
        assertEquals("ZUGFeRD 1.0 COMFORT", InvoiceReader.format(InvoiceReader.Syntax.CII,
                "urn:ferd:CrossIndustryDocument:invoice:1p0:comfort", true, "ZUGFeRD-invoice.xml", true));
        assertEquals("UBL Peppol BIS 3.0", InvoiceReader.format(InvoiceReader.Syntax.UBL,
                "urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0", false, null, false));
    }

    @Test
    public void parseDate() {
        assertEquals(LocalDate.of(2026, 9, 1), InvoiceReader.parseDate("20260901"));
        assertEquals(LocalDate.of(2026, 9, 1), InvoiceReader.parseDate("2026-09-01"));
        assertNull(InvoiceReader.parseDate("01.09.2026"));
        assertNull(InvoiceReader.parseDate(null));
    }
}
