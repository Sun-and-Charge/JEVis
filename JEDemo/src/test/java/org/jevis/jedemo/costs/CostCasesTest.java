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
import org.jevis.commons.trc.RCTemplate;
import org.jevis.commons.trc.TemplateFormula;
import org.jevis.commons.trc.TemplateHandler;
import org.jevis.commons.trc.TemplateInput;
import org.jevis.commons.trc.TemplateOutput;
import org.jevis.commons.trc.TrcResult;
import org.jevis.jedemo.InvoiceBuilder;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The six templates evaluated with the shared result calculator on the in-memory demo organisation, against the
 * invoices the JEDemo job makes for the same data.
 */
public class CostCasesTest {

    private static DemoWorld world;
    /**
     * case → month → {Messung netto, Rechnung netto}
     */
    private static final Map<String, Map<YearMonth, double[]>> NET = new LinkedHashMap<>();

    @BeforeClass
    public static void setUp() throws Exception {
        world = new DemoWorld();
    }

    @Test
    public void templatesParseLikeInTheControlCenter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (CostCase c : CostCases.cases()) {
            TemplateFactory.Built built = TemplateFactory.build(c);
            RCTemplate parsed = TemplateHandler.parse(mapper.writeValueAsBytes(TemplateFactory.templateJson(built)));
            assertNotNull(c.key, parsed);
            assertEquals(c.key, built.getTemplate().getTemplateInputs().size(), parsed.getTemplateInputs().size());
            assertEquals(c.key, built.getTemplate().getTemplateFormulas().size(), parsed.getTemplateFormulas().size());
            assertEquals(c.key, built.getTemplate().getTemplateOutputs().size(), parsed.getTemplateOutputs().size());
            assertEquals(c.key + ": formula inputs", parsed.getTemplateFormulas().size(), parsed.getTemplateFormulaInputs().size());
            Set<String> names = new HashSet<>();
            Set<String> outputs = new HashSet<>();
            Set<String> cells = new HashSet<>();
            for (TemplateOutput o : parsed.getTemplateOutputs()) {
                assertTrue(c.key + ": unique output id", outputs.add(o.getId()));
                assertTrue(c.key + ": one output per cell " + o.getRow() + "/" + o.getColumn(), cells.add(o.getRow() + "/" + o.getColumn()));
            }
            for (TemplateInput i : parsed.getTemplateInputs()) {
                assertTrue(c.key + ": unique name " + i.getVariableName(), names.add(i.getVariableName()));
                assertTrue(c.key + ": identifier " + i.getVariableName(), i.getVariableName().matches("[A-Za-z0-9_]+"));
                assertNotNull(c.key + ": bound " + i.getVariableName(), built.getBindings().get(i.getId()));
            }
            for (TemplateFormula f : parsed.getTemplateFormulas()) {
                assertTrue(c.key + ": unique name " + f.getName(), names.add(f.getName()));
                assertTrue(c.key + ": identifier " + f.getName(), f.getName().matches("[A-Za-z0-9_]+"));
                assertTrue(c.key + ": output of " + f.getName(), outputs.contains(f.getOutput()));
                TemplateFormula original = null;
                for (TemplateFormula o : built.getTemplate().getTemplateFormulas()) if (o.getId().equals(f.getId())) original = o;
                assertNotNull(original);
                assertEquals(c.key + ": all inputs of " + f.getName() + " known", original.getInputIds(), f.getInputIds());
            }
        }
    }

    @Test
    public void everyMonthWithoutWarningsAndMeasurementMatchesTheInvoice() throws Exception {
        for (CostCase c : CostCases.cases()) {
            Map<YearMonth, double[]> net = new LinkedHashMap<>();
            for (YearMonth m = DemoWorld.FIRST; !m.isAfter(DemoWorld.LAST); m = m.plusMonths(1)) {
                TrcResult result = world.calculate(c, m);
                assertTrue(c.key + " " + m + ": " + result.getWarnings(), result.getWarnings().isEmpty());
                double mNet = world.value(c, result, "M_Netto");
                double mGross = world.value(c, result, "M_Brutto");
                assertEquals(c.key + " " + m + " gross", mNet * 1.19, mGross, 0.01);
                if (!c.hasInvoices()) {
                    net.put(m, new double[]{mNet, Double.NaN});
                    continue;
                }
                InvoiceBuilder.Spec spec = world.invoices.get(c.key).get(m);
                InvoiceBuilder.Amounts amounts = InvoiceBuilder.amounts(spec);
                double rNet = world.value(c, result, "R_Netto");
                assertEquals(c.key + " " + m + " invoiced net in the template", amounts.getNet().doubleValue(), rNet, 1e-6);
                assertEquals(c.key + " " + m + " invoiced gross", amounts.getGross().doubleValue(), world.value(c, result, "R_Brutto"), 1e-6);
                assertEquals(c.key + " " + m + " invoiced kWh", spec.getKWh().doubleValue(), world.value(c, result, "R_Verbrauch"), 1e-6);
                double check = world.value(c, result, "Pruefung_Rechnung");
                double diffNet = world.value(c, result, "D_Netto");
                assertEquals(diffNet, mNet - rNet, 1e-6);
                boolean deviation = !world.invoiceCases.get(c.key).deviations(m).isEmpty();
                if (!deviation) {
                    assertEquals(c.key + " " + m + ": invoice = contract × invoiced quantity (line rounding only)", 0, check, 0.08);
                    assertEquals(c.key + " " + m + ": Messung = Rechnung (rounding to whole kWh only)", 0, diffNet, 0.30);
                }
                net.put(m, new double[]{mNet, rNet});
            }
            NET.put(c.key, net);
        }
        writeTable();
    }

    @Test
    public void deviationsShowUp() throws Exception {
        /* A: metering billed twice in March 2026 */
        CostCase a = CostCases.byKey("A");
        TrcResult march = world.calculate(a, YearMonth.of(2026, 3));
        double msb = world.value(a, march, "M_MSB");
        assertEquals(450d / 365 * 31, msb, 1e-6);
        assertEquals(msb, world.value(a, march, "Pruefung_Rechnung"), 0.02);
        assertEquals(-msb, world.value(a, march, "D_Netto"), 0.30);
        /* A: last year's work price in July 2026 */
        TrcResult july = world.calculate(a, YearMonth.of(2026, 7));
        double kWh = world.value(a, july, "R_Verbrauch");
        assertEquals(kWh * (14.8 - 13.5) / 100, world.value(a, july, "Pruefung_Rechnung"), 0.02);
        /* C: estimated consumption +4 % in February 2026: consumption differs, the invoice matches its quantity */
        CostCase c = CostCases.byKey("C");
        TrcResult feb = world.calculate(c, YearMonth.of(2026, 2));
        double measured = world.value(c, feb, "M_Verbrauch");
        assertEquals(-0.04 * measured, world.value(c, feb, "D_Verbrauch"), 1);
        assertEquals(0, world.value(c, feb, "Pruefung_Rechnung"), 0.08);
        assertTrue(world.value(c, feb, "D_Netto") < -10);
        /* D: last year's work price in January 2026 */
        CostCase d = CostCases.byKey("D");
        TrcResult jan = world.calculate(d, YearMonth.of(2026, 1));
        assertEquals(world.value(d, jan, "R_Verbrauch") * (6.9 - 6.4) / 100, world.value(d, jan, "Pruefung_Rechnung"), 0.02);
        /* E: metering price billed twice in April 2026 */
        CostCase e = CostCases.byKey("E");
        TrcResult april = world.calculate(e, YearMonth.of(2026, 4));
        assertEquals(240d / 365 * 30, world.value(e, april, "Pruefung_Rechnung"), 0.02);
    }

    @Test
    public void numbersArePlausible() throws Exception {
        YearMonth nov = YearMonth.of(2025, 11);
        CostCase a = CostCases.byKey("A");
        TrcResult ra = world.calculate(a, nov);
        assertEquals("A: ~37 MWh", 37000, world.value(a, ra, "M_Verbrauch"), 3000);
        assertEquals("A: peak 115–125 kW", 120, world.value(a, ra, "Leistung"), 6);
        assertEquals(135, world.value(a, ra, "Satz_GRID_DEMAND"), 1e-9);
        assertEquals(14.8, world.value(a, ra, "Satz_ENERGY"), 1e-9);
        assertEquals("A: 30 days of metering in November", 420d / 365 * 30, world.value(a, ra, "M_MSB"), 1e-6);
        assertEquals("Lieferant", "Energieversorger Nord", world.text(a, ra, "Text_Lieferant"));
        double netA = world.value(a, ra, "M_Netto");
        assertTrue("A: order of 8–10 k€ net per month: " + netA, netA > 7500 && netA < 10500);

        CostCase b = CostCases.byKey("B");
        double netB = world.value(b, world.calculate(b, nov), "M_Netto");
        assertTrue("B: " + netB, netB > 750 && netB < 1100);

        CostCase c = CostCases.byKey("C");
        TrcResult rc = world.calculate(c, YearMonth.of(2026, 1));
        assertEquals(11.4, world.value(c, rc, "Wert_Brennwert"), 1e-9);
        assertEquals(1, world.value(c, rc, "Wert_Zustandszahl"), 1e-9);
        assertTrue("C: hourly peak", world.value(c, rc, "Leistung") > 80);

        CostCase d = CostCases.byKey("D");
        TrcResult rd = world.calculate(d, YearMonth.of(2026, 1));
        double m3 = world.value(d, rd, "M_Volumen");
        assertEquals(m3 * 0.9535 * 11.4, world.value(d, rd, "M_Verbrauch"), 1e-6);
        assertTrue("D: January ~900 m³: " + m3, m3 > 780 && m3 < 1000);

        CostCase e = CostCases.byKey("E");
        TrcResult re = world.calculate(e, YearMonth.of(2026, 1));
        double g = world.value(e, re, "Wert_Gaspreisindex_G_2021_100");
        double w = world.value(e, re, "Wert_Waermepreisindex_W_2021_100");
        assertEquals(95 * (0.35 + 0.40 * g / 100 + 0.25 * w / 100) / 10, world.value(e, re, "Satz_ENERGY"), 1e-9);
        assertEquals(150 * 48d / 12, world.value(e, re, "M_CAPACITY"), 1e-9);
        double heat = world.value(e, re, "M_Verbrauch");
        assertTrue("E: January ~32 MWh: " + heat, heat > 28000 && heat < 36000);

        CostCase f = CostCases.byKey("F");
        TrcResult rf = world.calculate(f, nov);
        assertEquals("DE-MO 101E", world.text(f, rf, "Text_Kennzeichen_LP1"));
        assertEquals("4300 Technik", world.text(f, rf, "Text_Kostenstelle_LP3"));
        double kWh1 = world.value(f, rf, "Verbrauch_LP1");
        assertEquals(kWh1 * 28 / 100, world.value(f, rf, "Netto_LP1"), 1e-9);
        assertEquals(world.value(f, rf, "Netto_LP1") + world.value(f, rf, "Netto_LP2") + world.value(f, rf, "Netto_LP3"),
                world.value(f, rf, "M_Netto"), 1e-9);
        /* the charging profile: 150–250 kWh per point and month, 450–750 kWh in all, at 28–30 ct/kWh net */
        for (YearMonth m = YearMonth.of(2025, 11); !m.isAfter(DemoWorld.LAST); m = m.plusMonths(1)) {
            TrcResult r = world.calculate(f, m);
            double kWh = 0;
            for (int point = 1; point <= 3; point++) {
                double k = world.value(f, r, "Verbrauch_LP" + point);
                assertTrue("F " + m + " LP" + point + ": " + k, k >= 150 && k <= 250);
                kWh += k;
            }
            double net = world.value(f, r, "M_Netto");
            assertTrue("F " + m + ": " + kWh + " kWh, " + net + " EUR", kWh >= 450 && kWh <= 750 && net >= 125 && net <= 230);
        }
    }

    @Test
    public void yearViewSumsTheMonths() throws Exception {
        /* yearly amounts and quantities add up over the year; the demand charge is the 1/12 share of the year's peak */
        CostCase b = CostCases.byKey("B");
        double sum = 0;
        for (int month = 1; month <= 12; month++) {
            sum += world.value(b, world.calculate(b, YearMonth.of(2025, month)), "M_GRID_BASE");
        }
        assertEquals(55, sum, 1e-6);
    }

    /**
     * The table of docs/DEMO-COSTS.md: written to target/costdemo-table.md and printed.
     */
    private static void writeTable() throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("| Monat |");
        for (String key : NET.keySet()) sb.append(' ').append(key).append(" Messung |").append(key.equals("F") ? "" : " " + key + " Rechnung |");
        sb.append('\n').append("|---|");
        for (String key : NET.keySet()) sb.append("---:|").append(key.equals("F") ? "" : "---:|");
        sb.append('\n');
        for (YearMonth m = DemoWorld.FIRST; !m.isAfter(DemoWorld.LAST); m = m.plusMonths(1)) {
            sb.append("| ").append(m).append(" |");
            for (Map.Entry<String, Map<YearMonth, double[]>> e : NET.entrySet()) {
                double[] v = e.getValue().get(m);
                sb.append(' ').append(money(v[0])).append(" |");
                if (!e.getKey().equals("F")) sb.append(' ').append(money(v[1])).append(" |");
            }
            sb.append('\n');
        }
        System.out.println("Monthly net amounts (EUR) of the demo cost cases:\n" + sb);
        Path file = Paths.get("target", "costdemo-table.md");
        Files.createDirectories(file.getParent());
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String money(double value) {
        return String.format(Locale.GERMANY, "%,.2f", value);
    }

    static List<CostCase> cases() {
        return CostCases.cases();
    }
}
