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
import java.time.LocalDate;
import java.util.*;

import static org.junit.Assert.*;

public class InvoiceAnalysisTest {

    private static EInvoice.Line line(String name, String unit, String quantity) {
        EInvoice.Line line = new EInvoice.Line();
        line.name = name;
        line.unitCode = unit;
        line.quantity = quantity != null ? new BigDecimal(quantity) : null;
        return line;
    }

    private static void assertConsumption(String value, String unit, InvoiceAnalysis.Consumption consumption) {
        assertNotNull(consumption);
        assertEquals(0, new BigDecimal(value).compareTo(consumption.value));
        assertEquals(unit, consumption.unit);
    }

    // ---------------------------------------------------------------- generated invoices

    @Test
    public void electricity() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.zugferdPdf(TestInvoices.electricity("R-1001")));
        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        /* HT 3000 + NT 1321 | grid fee 4321 | electricity tax 4321: the consumption is named three times */
        assertConsumption("4321", "kWh", analysis.consumption);
        assertEquals(Medium.ELECTRICITY, analysis.medium);
        assertTrue(analysis.mediumReasons.toString(), analysis.mediumReasons.contains("unit kWh"));
        assertTrue(analysis.mediumReasons.toString(), analysis.mediumReasons.contains("line text 'Strom'"));
        assertEquals(TestInvoices.METER_POINT, analysis.meterPoint);
        assertEquals(LocalDate.of(2026, 9, 1), analysis.periodStart);
        assertEquals(LocalDate.of(2026, 9, 30), analysis.periodEnd);
        assertEquals(1, analysis.warnings.size());
        assertTrue(analysis.warnings.get(0), analysis.warnings.get(0).startsWith("The energy lines name the same consumption 3 times"));

        Map<String, Object> json = InvoiceAnalysis.toJson(invoice, analysis, "invoice.pdf", null);
        assertEquals(Arrays.asList("fileName", "format", "number", "date", "seller", "sellerVatId", "buyer", "periodStart",
                "periodEnd", "currency", "net", "tax", "gross", "consumption", "meterPoint", "medium", "mediumReasons",
                "items", "duplicate", "warnings"), new ArrayList<>(json.keySet()));
        assertEquals("invoice.pdf", json.get("fileName"));
        assertEquals("2026-10-05", json.get("date"));
        assertEquals("2026-09-01", json.get("periodStart"));
        assertEquals(1460.96, (Double) json.get("net"), 1e-9);
        assertEquals("ELECTRICITY", json.get("medium"));
        assertNull(json.get("duplicate"));
        @SuppressWarnings("unchecked")
        Map<String, Object> consumption = (Map<String, Object>) json.get("consumption");
        assertEquals(4321.0, (Double) consumption.get("value"), 1e-9);
        assertEquals("kWh", consumption.get("unit"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) json.get("items");
        assertEquals(5, items.size());
        assertEquals(Arrays.asList("description", "quantity", "unit", "unitPrice", "net", "taxRate", "periodStart", "periodEnd"),
                new ArrayList<>(items.get(0).keySet()));
        assertEquals("Arbeitspreis Strom HT", items.get(0).get("description"));
        assertEquals("kWh", items.get(0).get("unit"));
        assertEquals(0.25, (Double) items.get(0).get("unitPrice"), 1e-9);
        assertEquals(19.0, (Double) items.get(0).get("taxRate"), 1e-9);
        assertEquals("month", items.get(4).get("unit"));
    }

    @Test
    public void gasInKilowattHoursAndCubicMetres() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.cii(TestInvoices.gas("G-2002"), "XRECHNUNG"));
        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        /* kWh preferred over the m³ of the information line */
        assertConsumption("10000", "kWh", analysis.consumption);
        assertEquals(Medium.GAS, analysis.medium);
        assertTrue(analysis.mediumReasons.toString(), analysis.mediumReasons.contains("unit m³"));
        assertTrue(analysis.mediumReasons.toString(), analysis.mediumReasons.contains("line text 'Erdgas'"));
        assertEquals(TestInvoices.MARKET_LOCATION, analysis.meterPoint);
        /* period from the lines */
        assertEquals(LocalDate.of(2026, 1, 1), analysis.periodStart);
        assertEquals(LocalDate.of(2026, 3, 31), analysis.periodEnd);
        assertEquals(LocalDate.of(2026, 1, 1), InvoiceAnalysis.timestampDate(invoice, analysis));
        assertTrue(analysis.warnings.toString(), analysis.warnings.isEmpty());
    }

    @Test
    public void gasInCubicMetres() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.cii(TestInvoices.gasCubicMetres("D-1"), "EN16931"));
        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        assertConsumption("300", "m³", analysis.consumption);
        assertEquals(Medium.GAS, analysis.medium);
        assertEquals(Collections.singletonList("unit m³"), analysis.mediumReasons);
        assertNull(analysis.meterPoint);
        assertEquals("m³", InvoiceAnalysis.itemsJson(invoice).get(0).get("unit"));
        assertEquals("piece", InvoiceAnalysis.itemsJson(invoice).get(1).get("unit"));
    }

    @Test
    public void districtHeating() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.ublDistrictHeating("W-3003"));
        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        /* 12.5 MWh */
        assertConsumption("12500", "kWh", analysis.consumption);
        assertEquals(Medium.DISTRICT_HEATING, analysis.medium);
        assertTrue(analysis.mediumReasons.toString(), analysis.mediumReasons.contains("line text 'Wärme'"));
        assertEquals(TestInvoices.MARKET_LOCATION, analysis.meterPoint);
        Map<String, Object> json = InvoiceAnalysis.toJson(invoice, analysis, "w.xml", 42L);
        assertEquals(42L, json.get("duplicate"));
        assertEquals("XRechnung 3.0 UBL", json.get("format"));
        assertEquals(1582.70, (Double) json.get("gross"), 1e-9);
    }

    @Test
    public void unknownMedium() throws Exception {
        EInvoice invoice = InvoiceReader.read(TestInvoices.cii(TestInvoices.unknownMedium("E-1"), "EN16931"));
        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        assertEquals(Medium.OTHER, analysis.medium);
        assertTrue(analysis.mediumReasons.isEmpty());
        assertNull(analysis.consumption);
        assertTrue(analysis.warnings.toString(), analysis.warnings.contains("The medium could not be detected, please choose it."));
        assertNull(InvoiceAnalysis.toJson(invoice, analysis, null, null).get("consumption"));
    }

    // ---------------------------------------------------------------- pure functions

    @Test
    public void consumption() {
        List<String> warnings = new ArrayList<>();
        assertNull(InvoiceAnalysis.consumption(Arrays.asList(line("Grundpreis", "MON", "1"), line("x", null, "5")), warnings));
        assertConsumption("1500", "kWh", InvoiceAnalysis.consumption(
                Arrays.asList(line("a", "MWH", "1"), line("b", "KWH", "500"), line("c", "MTQ", "100")), warnings));
        assertConsumption("150.5", "m³", InvoiceAnalysis.consumption(
                Arrays.asList(line("a", "MTQ", "100"), line("b", "MTQ", "50.5"), line("c", "DAY", "30")), warnings));
        assertTrue(warnings.isEmpty());

        /* same quantity in another period is counted */
        EInvoice.Line january = line("a", "KWH", "100");
        january.periodStart = LocalDate.of(2026, 1, 1);
        EInvoice.Line february = line("a", "KWH", "100");
        february.periodStart = LocalDate.of(2026, 2, 1);
        assertConsumption("200", "kWh", InvoiceAnalysis.consumption(Arrays.asList(january, february), warnings));
        assertTrue(warnings.isEmpty());

        /* two meter points, each with energy and grid fee */
        assertConsumption("1300", "kWh", InvoiceAnalysis.consumption(Arrays.asList(line("a", "KWH", "500"),
                line("b", "KWH", "800"), line("c", "KWH", "500"), line("d", "KWH", "800")), warnings));
        assertEquals(1, warnings.size());
        warnings.clear();
        /* negative quantities (corrections) are simply added */
        assertConsumption("400", "kWh", InvoiceAnalysis.consumption(
                Arrays.asList(line("a", "KWH", "500"), line("b", "KWH", "-100")), warnings));
        assertTrue(warnings.isEmpty());

        /* same quantity in kWh and MWh is the same consumption */
        assertConsumption("1000", "kWh", InvoiceAnalysis.consumption(
                Arrays.asList(line("a", "MWH", "1"), line("b", "KWH", "1000.00")), warnings));
        assertEquals(1, warnings.size());
    }

    @Test
    public void meterPoint() {
        EInvoice invoice = new EInvoice();
        assertNull(InvoiceAnalysis.meterPoint(invoice, null));

        invoice.notes.add("Ihre Marktlokation (MaLo-ID): 51234567890, Zähler 1APA0000000001");
        assertEquals("51234567890", InvoiceAnalysis.meterPoint(invoice, null));

        EInvoice zp = new EInvoice();
        zp.lines.add(line("Strom Zählpunkt de0000000000000000000000000000001", "KWH", "1"));
        assertEquals(TestInvoices.METER_POINT, InvoiceAnalysis.meterPoint(zp, null));

        /* too long or too short is no Zählpunkt, an IBAN neither */
        EInvoice wrong = new EInvoice();
        wrong.notes.add("DE00000000000000000000000000000012 DE000000000000000000000000000001 DE02120300000000202051");
        wrong.notes.add("Kundennummer 12345678901");
        assertNull(InvoiceAnalysis.meterPoint(wrong, null));

        /* in the XML only */
        EInvoice xml = new EInvoice();
        xml.xml = "<x><MeterID>" + TestInvoices.METER_POINT + "</MeterID></x>";
        assertEquals(TestInvoices.METER_POINT, InvoiceAnalysis.meterPoint(xml, null));

        /* several */
        EInvoice several = new EInvoice();
        several.deliveryTexts.add(TestInvoices.MARKET_LOCATION);
        several.notes.add("Zählpunkt " + TestInvoices.METER_POINT);
        List<String> warnings = new ArrayList<>();
        assertEquals(TestInvoices.METER_POINT + ", " + TestInvoices.MARKET_LOCATION, InvoiceAnalysis.meterPoint(several, warnings));
        assertEquals(1, warnings.size());
    }

    @Test
    public void mediumKeywords() {
        assertEquals(Medium.ELECTRICITY, guess("Wärmepumpenstrom", null));
        assertEquals(Medium.GAS, guess("Biogas Lieferung", null));
        assertEquals(Medium.OTHER, guess("Lieferung Gartengasse 5", null));
        assertEquals(Medium.DISTRICT_HEATING, guess("Fernwaerme Grundpreis", null));
        assertEquals(Medium.GAS, guess("Brennwert 11,2 kWh/m³", "KWH"));
        assertEquals(Medium.ELECTRICITY, guess("Wirkarbeit", "MWH"));
        /* seller name */
        EInvoice invoice = new EInvoice();
        invoice.seller = "Fernwärme Supplier F";
        InvoiceAnalysis.Analysis analysis = new InvoiceAnalysis.Analysis();
        InvoiceAnalysis.guessMedium(invoice, analysis);
        assertEquals(Medium.DISTRICT_HEATING, analysis.medium);
        assertEquals(Collections.singletonList("seller 'Fernwärme'"), analysis.mediumReasons);
        /* tie */
        EInvoice tie = new EInvoice();
        tie.notes.add("Strom und Gas");
        InvoiceAnalysis.Analysis tieAnalysis = new InvoiceAnalysis.Analysis();
        InvoiceAnalysis.guessMedium(tie, tieAnalysis);
        assertEquals(Medium.ELECTRICITY, tieAnalysis.medium);
        assertEquals(Collections.singletonList("The medium is uncertain, please check it."), tieAnalysis.warnings);
    }

    private static Medium guess(String text, String unit) {
        EInvoice invoice = new EInvoice();
        invoice.lines.add(line(text, unit, "1"));
        InvoiceAnalysis.Analysis analysis = new InvoiceAnalysis.Analysis();
        InvoiceAnalysis.guessMedium(invoice, analysis);
        return analysis.medium;
    }

    @Test
    public void sameInvoice() {
        EInvoice invoice = new EInvoice();
        invoice.number = "R-1001";
        invoice.seller = "Supplier A";
        invoice.sellerVatId = "DE000000000";
        assertTrue(InvoiceAnalysis.isSameInvoice(invoice, "DE 000000000", "Supplier A GmbH", "R-1001"));
        assertFalse(InvoiceAnalysis.isSameInvoice(invoice, "DE000000009", "Supplier A", "R-1001"));
        assertFalse(InvoiceAnalysis.isSameInvoice(invoice, "DE000000000", "Supplier A", "R-1002"));
        /* without VAT ID on one side: by name */
        assertTrue(InvoiceAnalysis.isSameInvoice(invoice, null, "supplier a", "R-1001"));
        assertFalse(InvoiceAnalysis.isSameInvoice(invoice, null, "Supplier B", "R-1001"));
        invoice.number = null;
        assertFalse(InvoiceAnalysis.isSameInvoice(invoice, "DE000000000", "Supplier A", null));
    }

    @Test
    public void unitNames() {
        assertEquals("kWh", InvoiceAnalysis.unitName("KWH"));
        assertEquals("m³", InvoiceAnalysis.unitName("mtq"));
        assertEquals("XYZ", InvoiceAnalysis.unitName("XYZ"));
        assertNull(InvoiceAnalysis.unitName(null));
    }
}
