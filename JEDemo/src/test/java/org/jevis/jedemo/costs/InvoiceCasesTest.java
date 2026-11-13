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
package org.jevis.jedemo.costs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.jedemo.ContractValues;
import org.jevis.jedemo.InvoiceBuilder;
import org.jevis.jedemo.InvoiceBuilder.Amounts;
import org.jevis.jedemo.InvoiceBuilder.Line;
import org.jevis.jedemo.InvoiceBuilder.Spec;
import org.jevis.jedemo.InvoiceCalculator;
import org.jevis.jedemo.InvoiceCase;
import org.jevis.jedemo.Ref;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mustangproject.Invoice;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.TransactionCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.Assert.*;

/**
 * The invoices of the demo cost cases: amounts by hand for one case, the deviations, and the ZUGFeRD files.
 */
public class InvoiceCasesTest {

    private static DemoWorld world;

    @BeforeClass
    public static void setUp() throws Exception {
        world = new DemoWorld();
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    private static Line line(Spec spec, String text) {
        Line found = null;
        for (Line l : spec.getLines()) {
            if (l.getText().equals(text)) {
                assertNull("one line " + text, found);
                found = l;
            }
        }
        assertNotNull("line " + text, found);
        return found;
    }

    @Test
    public void electricitySlpByHand() throws Exception {
        /* 3000 kWh in March 2026 at Außenlager Süd */
        FakeData data = new FakeData();
        data.addPlan();
        data.object(Ref.key("B.clean"), "Bereinigte Daten", "Clean Data").set(InvoiceCalculator.start(YearMonth.of(2026, 3)), 3000);
        InvoiceCase ic = CostCases.invoiceCase(CostCases.byKey("B"));
        InvoiceCase resolved = new ObjectMapper().treeToValue(
                SetupPlan.resolveKeys(new ObjectMapper().valueToTree(ic), key -> data.id(Ref.key(key))), InvoiceCase.class);
        ContractValues values = new ContractValues(data.ds);
        YearMonth march = YearMonth.of(2026, 3);
        InvoiceCalculator.Measurement measured = InvoiceCalculator.measure(resolved, march, values);
        assertNull(measured.getProblem());
        assertNull(measured.getPeak());
        InvoiceCalculator.Result result = InvoiceCalculator.calculate(resolved, march, measured.getConsumption(), null,
                InvoiceCalculator.Prices.of(values), "Sun and Charge Demo", LocalDate.of(2026, 4, 5));
        Spec spec = result.getSpec();
        assertTrue(result.getWarnings().isEmpty());
        assertEquals("EN-202603-SS", spec.getNumber());
        assertEquals(10, spec.getLines().size());
        assertEquals(d("525.00"), line(spec, "Arbeitspreis").getNet());                  // 3000 × 17.5 ct
        assertEquals(d("15.29"), line(spec, "Grundpreis").getNet());                     // 180 €/a × 31/365
        assertEquals(d("31"), line(spec, "Grundpreis").getQuantity());
        assertEquals("DAY", line(spec, "Grundpreis").getUnitCode());
        assertEquals(d("5.10"), line(spec, "Netzentgelt Grundpreis").getNet());          // 60 €/a × 31/365
        assertEquals(d("237.00"), line(spec, "Netzentgelt Arbeitspreis").getNet());      // 7.9 ct
        assertEquals(d("9.34"), line(spec, "Messstellenbetrieb").getNet());              // 110 €/a × 31/365
        assertEquals(d("47.70"), line(spec, "Konzessionsabgabe").getNet());              // 1.59 ct
        assertEquals(d("13.38"), line(spec, "KWKG-Umlage").getNet());                    // 0.446 ct
        assertEquals(d("46.77"), line(spec, "Aufschlag für besondere Netznutzung").getNet());           // 1.559 ct
        assertEquals(d("28.23"), line(spec, "Offshore-Netzumlage").getNet());            // 0.941 ct
        assertEquals(d("61.50"), line(spec, "Stromsteuer").getNet());                    // 2.05 ct
        Amounts amounts = InvoiceBuilder.amounts(spec);
        assertEquals(d("989.31"), amounts.getNet());
        assertEquals(d("187.97"), amounts.getVat());
        assertEquals(d("1177.28"), amounts.getGross());
        TransactionCalculator calc = new TransactionCalculator(InvoiceBuilder.invoice(spec));
        assertEquals(0, amounts.getNet().compareTo(calc.getTaxBasis()));
        assertEquals(0, amounts.getGross().compareTo(calc.getGrandTotal()));
    }

    @Test
    public void deviations() {
        Spec march = world.invoices.get("A").get(YearMonth.of(2026, 3));
        Spec february = world.invoices.get("A").get(YearMonth.of(2026, 2));
        assertEquals("metering twice", february.getLines().size() + 1, march.getLines().size());
        long msb = march.getLines().stream().filter(l -> l.getText().equals("Messstellenbetrieb")).count();
        assertEquals(2, msb);
        assertEquals("450 €/a × 31/365", d("38.22"),
                march.getLines().stream().filter(l -> l.getText().equals("Messstellenbetrieb")).findFirst().get().getNet());
        assertEquals("450 €/a × 28/365", d("34.52"), line(february, "Messstellenbetrieb").getNet());

        Spec july = world.invoices.get("A").get(YearMonth.of(2026, 7));
        assertEquals("last year's work price", 0, d("0.148").compareTo(line(july, "Arbeitspreis").getPrice()));
        assertEquals(0, d("0.135").compareTo(line(world.invoices.get("A").get(YearMonth.of(2026, 8)), "Arbeitspreis").getPrice()));

        Spec gas = world.invoices.get("C").get(YearMonth.of(2026, 2));
        Spec gasJan = world.invoices.get("C").get(YearMonth.of(2026, 1));
        assertEquals("all energy lines with the estimated quantity", line(gas, "Arbeitspreis").getQuantity(),
                line(gas, "Energiesteuer").getQuantity());
        assertTrue(gasJan.getKWh().signum() > 0);

        Spec heat = world.invoices.get("E").get(YearMonth.of(2026, 4));
        assertEquals(2, heat.getLines().stream().filter(l -> l.getText().equals("Messpreis")).count());
    }

    @Test
    public void linesOfTheCases() {
        YearMonth nov = YearMonth.of(2025, 11);
        Spec a = world.invoices.get("A").get(nov);
        assertEquals("EN-202511-SR", a.getNumber());
        Line demand = line(a, "Netzentgelt Leistungspreis");
        assertEquals("KWT", demand.getUnitCode());
        assertEquals(0, d("11.25").compareTo(demand.getPrice()));
        assertTrue("peak of the year", demand.getQuantity().doubleValue() > 110 && demand.getQuantity().doubleValue() < 130);
        assertEquals("30 days", d("30"), line(a, "Messstellenbetrieb").getQuantity());

        Spec c = world.invoices.get("C").get(nov);
        assertEquals("EN-202511-GR", c.getNumber());
        assertEquals(0, d("1.5").compareTo(line(c, "Netzentgelt Leistungspreis").getPrice()));

        Spec dSpec = world.invoices.get("D").get(nov);
        assertEquals("EN-202511-GS", dSpec.getNumber());
        assertTrue(dSpec.getNotes().get(1).startsWith("Verbrauch "));
        assertTrue(dSpec.getNotes().get(1).contains("Zustandszahl 0,9535 × Brennwert 11,4 kWh/m³"));

        Spec e = world.invoices.get("E").get(nov);
        assertEquals("EN-202511-FW", e.getNumber());
        assertEquals(InvoiceBuilder.Medium.DISTRICT_HEATING, e.getMedium());
        Line capacity = line(e, "Leistungspreis");
        assertEquals(0, d("150").compareTo(capacity.getQuantity()));
        assertEquals(d("575.00"), capacity.getNet());   // 46 €/kW/a in 2025 / 12 × 150 kW
        double g = History.gasIndex(nov);
        double w = History.heatIndex(nov);
        assertEquals(95 * (0.35 + 0.40 * g / 100 + 0.25 * w / 100) / 1000, line(e, "Arbeitspreis (Preisgleitklausel)").getPrice().doubleValue(), 1e-9);
        assertEquals(LocalDate.of(2025, 12, 5), e.getIssueDate());
    }

    @Test
    public void zugferdFilesAreReadable() throws Exception {
        for (String key : world.invoices.keySet()) {
            Spec spec = world.invoices.get(key).get(YearMonth.of(2026, 3));
            byte[] pdf = InvoiceBuilder.pdf(spec);
            ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
            importer.setInputStream(new ByteArrayInputStream(pdf));
            Invoice invoice = importer.extractInvoice();
            assertEquals(spec.getNumber(), invoice.getNumber());
            IZUGFeRDExportableItem[] items = invoice.getZFItems();
            assertEquals(key, spec.getLines().size(), items.length);
            for (int i = 0; i < items.length; i++) {
                Line line = spec.getLines().get(i);
                assertEquals(line.getText(), items[i].getProduct().getName());
                assertEquals(line.getUnitCode(), items[i].getProduct().getUnit());
                assertEquals(key + " " + line.getText(), 0, line.getPrice().compareTo(items[i].getPrice()));
            }
            assertEquals(key, 0, InvoiceBuilder.amounts(spec).getGross().compareTo(new TransactionCalculator(invoice).getGrandTotal()));
            String xml = importer.getUTF8();
            if (!"E".equals(key)) assertTrue(key, xml.contains(world.invoiceCases.get(key).meterPoint));
        }
    }

    @Test
    public void caseFHasNoInvoices() {
        assertNull(CostCases.invoiceCase(CostCases.byKey("F")));
        List<InvoiceCase> cases = CostCases.invoiceCases();
        assertEquals(5, cases.size());
        for (InvoiceCase ic : cases) assertFalse("symbolic keys are problems until resolved", ic.problems().isEmpty());
    }
}
