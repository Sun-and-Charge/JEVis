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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.trc.AccountingTemplateHandler;
import org.jevis.commons.trc.RCTemplate;
import org.jevis.commons.trc.ResultCalculator;
import org.jevis.commons.trc.SelectionTemplate;
import org.jevis.commons.trc.TemplateHandler;
import org.jevis.commons.trc.TrcResult;
import org.jevis.commons.trc.TrcTimeFrames;
import org.jevis.jedemo.ContractValues;
import org.jevis.jedemo.InvoiceBuilder;
import org.jevis.jedemo.InvoiceCalculator;
import org.jevis.jedemo.InvoiceCase;
import org.jevis.jedemo.Ref;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The demo organisation in memory: the objects of the setup plan, synthetic rows (plan profiles and stand-ins for the
 * existing live rows) and the invoices of every case from {@link #FIRST} to {@link #LAST}, made by the JEDemo invoice
 * code and stored in the invoice series as the import does.
 */
final class DemoWorld {

    static final YearMonth FIRST = YearMonth.of(2025, 1);
    static final YearMonth LAST = YearMonth.of(2026, 10);
    static final String BUYER = "Sun and Charge Demo";
    static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    static final LocalDate TODAY = LocalDate.of(2026, 11, 12);

    final FakeData data = new FakeData();
    final ObjectMapper mapper = new ObjectMapper();
    /**
     * resolved invoice cases by case key
     */
    final Map<String, InvoiceCase> invoiceCases = new LinkedHashMap<>();
    final Map<String, Map<YearMonth, InvoiceBuilder.Spec>> invoices = new LinkedHashMap<>();
    private final Map<String, RCTemplate> templates = new LinkedHashMap<>();
    private final Map<String, TemplateFactory.Built> built = new LinkedHashMap<>();

    DemoWorld() throws Exception {
        data.addPlan();
        Instant from = History.FROM.atStartOfDay(History.ZONE).toInstant();
        Instant until = LAST.plusMonths(2).atDay(1).atStartOfDay(History.ZONE).toInstant();

        /* stand-ins of the existing live rows */
        data.object(Ref.id(CostCases.CLEAN_GRID_PURCHASE), "Bereinigte Daten", "Clean Data").add(History.electricityRlm(from, until));
        List<History.Point> gas = History.gasRlm(from, until);
        data.object(Ref.id(CostCases.CLEAN_GAS_ENERGY), "Bereinigte Daten", "Clean Data").add(gas);
        data.object(Ref.key("C.hourly"), "Stundenwerte", "Clean Data").add(History.hourlySums(gas));
        data.object(Ref.id(CostCases.HEATING_VALUE), "Gas - Brennwert", "Base Data")
                .set(LocalDate.of(2020, 1, 1).atStartOfDay(History.ZONE).toInstant(), 11.4);
        /* charge points: the clean rows equal the replaced raw rows (15-minute kWh, no differential conversion); the
         * live rows start 2025-11, the stand-ins with the other rows so every month of the table has values */
        Instant last = until.minusSeconds(900);
        data.object(Ref.id(CostCases.CLEAN_CHARGE_POINT_1), "Bereinigte Daten", "Clean Data")
                .add(History.chargingPoint(CostCases.CHARGE_POINT_1, from, last));
        data.object(Ref.id(CostCases.CLEAN_CHARGE_POINT_2), "Bereinigte Daten", "Clean Data")
                .add(History.chargingPoint(CostCases.CHARGE_POINT_2, from, last));
        data.object(Ref.id(CostCases.CLEAN_CHARGE_POINT_3), "Bereinigte Daten", "Clean Data")
                .add(History.chargingPoint(CostCases.CHARGE_POINT_3, from, last));

        /* new rows: clean values as the processor would write them (consumption at the start of the interval) */
        YearMonth end = LAST.plusMonths(2);
        data.object(Ref.key("B.clean"), "Bereinigte Daten", "Clean Data")
                .add(History.monthlyConsumption(History.ELECTRICITY_SLP_COUNTER, FIRST, end));
        data.object(Ref.key("D.clean"), "Bereinigte Daten", "Clean Data")
                .add(History.monthlyConsumption(History.GAS_SLP_COUNTER, FIRST, end));
        FakeData.Obj heat = data.object(Ref.key("E.clean"), "Bereinigte Daten", "Clean Data");
        for (YearMonth m = FIRST; m.isBefore(end); m = m.plusMonths(1)) heat.add(History.heatHourly(m));
        Instant indexEnd = YearMonth.of(2030, 12).atDay(1).atStartOfDay(History.ZONE).toInstant();
        data.object(Ref.key("E.indexGas"), "Preisindex Erdgas", "Base Data")
                .add(History.generate(History.GAS_PRICE_INDEX, Instant.EPOCH, indexEnd));
        data.object(Ref.key("E.indexHeat"), "Preisindex Wärme", "Base Data")
                .add(History.generate(History.HEAT_PRICE_INDEX, Instant.EPOCH, indexEnd));

        /* the invoices, stored in the series of each case folder */
        ContractValues values = new ContractValues(data.ds);
        for (InvoiceCase ic : CostCases.invoiceCases()) {
            InvoiceCase resolved = resolve(ic);
            invoiceCases.put(ic.key, resolved);
            CostCase c = CostCases.byKey(ic.key);
            Map<YearMonth, InvoiceBuilder.Spec> specs = new LinkedHashMap<>();
            for (YearMonth m = FIRST; !m.isAfter(LAST); m = m.plusMonths(1)) {
                InvoiceCalculator.Measurement measured = InvoiceCalculator.measure(resolved, m, values);
                if (measured.getProblem() != null) throw new IllegalStateException(ic.key + " " + m + ": " + measured.getProblem());
                InvoiceCalculator.Result result = InvoiceCalculator.calculate(resolved, m, measured.getConsumption(), measured.getPeak(),
                        InvoiceCalculator.Prices.of(values), BUYER, InvoiceCalculator.issueDate(m, TODAY));
                InvoiceBuilder.Spec spec = result.getSpec();
                InvoiceBuilder.Amounts amounts = InvoiceBuilder.amounts(spec);
                Instant start = InvoiceCalculator.start(m);
                data.object(data.id(c.invoiceConsumption)).set(start, spec.getKWh().doubleValue());
                data.object(data.id(c.invoiceNet)).set(start, amounts.getNet().doubleValue());
                data.object(data.id(c.invoiceGross)).set(start, amounts.getGross().doubleValue());
                specs.put(m, spec);
            }
            invoices.put(ic.key, specs);
        }
    }

    InvoiceCase resolve(InvoiceCase ic) throws Exception {
        JsonNode node = SetupPlan.resolveKeys(mapper.valueToTree(ic), key -> data.id(Ref.key(key)));
        return mapper.treeToValue(node, InvoiceCase.class);
    }

    TemplateFactory.Built built(CostCase c) {
        return built.computeIfAbsent(c.key, k -> TemplateFactory.build(c));
    }

    /**
     * The template as the Control Center reads it, bound by the accounting configuration (keys resolved).
     */
    RCTemplate template(CostCase c) throws Exception {
        RCTemplate template = templates.get(c.key);
        if (template != null) return template;
        TemplateFactory.Built b = built(c);
        template = TemplateHandler.parse(mapper.writeValueAsBytes(TemplateFactory.templateJson(b)));
        JsonNode configuration = SetupPlan.resolveKeys(TemplateFactory.configurationJson(b), key -> data.id(Ref.key(key)));
        SelectionTemplate selection = AccountingTemplateHandler.parse(mapper.writeValueAsBytes(configuration));
        ResultCalculator.link(template, selection, null);
        templates.put(c.key, template);
        return template;
    }

    TrcResult calculate(CostCase c, YearMonth m) throws Exception {
        DateTime start = new DateTime(InvoiceCalculator.start(m).toEpochMilli(), ZONE);
        DateTime end = new DateTime(InvoiceCalculator.end(m).toEpochMilli() - 1, ZONE);
        TrcTimeFrames frames = new TrcTimeFrames(ZONE, new DateTime(2026, 11, 12, 12, 0, ZONE));
        return new ResultCalculator(data.ds, frames).calculate(template(c), start, end);
    }

    /**
     * @return the value of the output of the formula, NaN if it has none
     */
    double value(CostCase c, TrcResult result, String formula) {
        String output = built(c).output(formula);
        if (output == null) throw new IllegalArgumentException("formula " + formula + " has no output");
        TrcResult.Cell cell = result.cell(output);
        return cell != null && cell.getValue() != null ? cell.getValue() : Double.NaN;
    }

    String text(CostCase c, TrcResult result, String formula) {
        TrcResult.Cell cell = result.cell(built(c).output(formula));
        return cell != null ? cell.getText() : null;
    }
}
