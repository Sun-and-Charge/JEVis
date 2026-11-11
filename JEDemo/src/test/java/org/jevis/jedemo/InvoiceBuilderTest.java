/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import org.jevis.jedemo.InvoiceBuilder.Amounts;
import org.jevis.jedemo.InvoiceBuilder.Medium;
import org.jevis.jedemo.InvoiceBuilder.Spec;
import org.junit.Test;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.junit.Assert.*;

public class InvoiceBuilderTest {

    private static Spec electricity() {
        return new Spec(Medium.ELECTRICITY, YearMonth.of(2026, 10), new BigDecimal("4321.4"), new BigDecimal("0.28"),
                new BigDecimal("12.50"), "Sun and Charge Demo", LocalDate.of(2026, 11, 1));
    }

    private static Spec gas() {
        return new Spec(Medium.GAS, YearMonth.of(2026, 10), new BigDecimal("10000.5"), new BigDecimal("0.09"),
                new BigDecimal("15"), "Demo Werk", LocalDate.of(2026, 11, 1));
    }

    @Test
    public void numbers() {
        assertEquals("EN-202610-S", InvoiceBuilder.number(Medium.ELECTRICITY, YearMonth.of(2026, 10)));
        assertEquals("EN-202601-G", InvoiceBuilder.number(Medium.GAS, YearMonth.of(2026, 1)));
        assertEquals("EN-202610-S.pdf", InvoiceBuilder.fileName(electricity()));
    }

    @Test
    public void amounts() {
        // 4321 kWh * 0.28 = 1209.88 + 12.50 = 1222.38; VAT 19 % = 232.25; gross 1454.63
        Amounts e = InvoiceBuilder.amounts(electricity());
        assertEquals(new BigDecimal("1222.38"), e.getNet());
        assertEquals(new BigDecimal("232.25"), e.getVat());
        assertEquals(new BigDecimal("1454.63"), e.getGross());

        // 10001 kWh * 0.09 = 900.09 + 15.00 = 915.09; VAT 173.87; gross 1088.96
        Amounts g = InvoiceBuilder.amounts(gas());
        assertEquals(new BigDecimal("915.09"), g.getNet());
        assertEquals(new BigDecimal("173.87"), g.getVat());
        assertEquals(new BigDecimal("1088.96"), g.getGross());
    }

    @Test
    public void noFeeLineWithoutFee() {
        Spec spec = new Spec(Medium.GAS, YearMonth.of(2026, 2), new BigDecimal("100"), new BigDecimal("0.10"),
                BigDecimal.ZERO, "Demo", LocalDate.of(2026, 3, 1));
        assertEquals(new BigDecimal("10.00"), InvoiceBuilder.amounts(spec).getNet());
        assertEquals(1, InvoiceBuilder.invoice(spec).getZFItems().length);
    }

    @Test
    public void mustangCalculatesTheSameAmounts() {
        for (Spec spec : new Spec[]{electricity(), gas()}) {
            TransactionCalculator calc = new TransactionCalculator(InvoiceBuilder.invoice(spec));
            Amounts amounts = InvoiceBuilder.amounts(spec);
            assertEquals(0, amounts.getNet().compareTo(calc.getTaxBasis()));
            assertEquals(0, amounts.getGross().compareTo(calc.getGrandTotal()));
        }
    }

    @Test
    public void pdfIsAReadableZugferdInvoice() throws Exception {
        Spec spec = electricity();
        byte[] pdf = InvoiceBuilder.pdf(spec);
        assertTrue(pdf.length > 1000);
        assertEquals("%PDF", new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));

        ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
        importer.setInputStream(new ByteArrayInputStream(pdf));
        assertNotNull(importer.getMeta());
        Invoice invoice = importer.extractInvoice();

        assertEquals("EN-202610-S", invoice.getNumber());
        assertEquals(InvoiceBuilder.SUPPLIER, invoice.getSender().getName());
        assertEquals("Sun and Charge Demo", invoice.getRecipient().getName());
        IZUGFeRDExportableItem[] items = invoice.getZFItems();
        assertEquals(2, items.length);
        assertEquals("Arbeitspreis Strom", items[0].getProduct().getName());
        assertEquals("KWH", items[0].getProduct().getUnit());
        assertEquals(0, new BigDecimal("4321").compareTo(items[0].getQuantity()));
        assertEquals("Grundpreis", items[1].getProduct().getName());
        assertEquals("MON", items[1].getProduct().getUnit());
        assertEquals(0, new BigDecimal("1454.63").compareTo(new TransactionCalculator(invoice).getGrandTotal()));
        assertTrue(importer.getUTF8().contains(InvoiceBuilder.ELECTRICITY_METER_POINT));
        assertTrue(importer.getUTF8().contains("20261001"));
        assertTrue(importer.getUTF8().contains("20261031"));
    }

    @Test
    public void gasPdfIsReadable() throws Exception {
        ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
        importer.setInputStream(new ByteArrayInputStream(InvoiceBuilder.pdf(gas())));
        Invoice invoice = importer.extractInvoice();
        assertEquals("EN-202610-G", invoice.getNumber());
        assertEquals("Arbeitspreis Erdgas", invoice.getZFItems()[0].getProduct().getName());
        assertTrue(importer.getUTF8().contains(InvoiceBuilder.GAS_MARKET_LOCATION));
    }
}
