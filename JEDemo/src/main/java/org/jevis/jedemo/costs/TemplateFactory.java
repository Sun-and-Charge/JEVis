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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.commons.trc.AccountingTemplateHandler;
import org.jevis.commons.trc.InputVariableType;
import org.jevis.commons.trc.RCTemplate;
import org.jevis.commons.trc.SelectionTemplate;
import org.jevis.commons.trc.TemplateFormula;
import org.jevis.commons.trc.TemplateHandler;
import org.jevis.commons.trc.TemplateInput;
import org.jevis.commons.trc.TemplateOutput;
import org.jevis.commons.trc.TrcTimeFrames;
import org.jevis.jedemo.InvoiceCase;
import org.jevis.jedemo.Ref;
import org.jevis.jedemo.costs.CostCase.ChargePoint;
import org.jevis.jedemo.costs.CostCase.IndexTerm;
import org.jevis.jedemo.costs.CostCase.Position;
import org.jevis.jedemo.costs.CostCase.Value;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the result calculation template ("Template File" of a "Result Calculation Template", as the Control Center's
 * TRC plugin writes it) and the accounting configuration ("Template File" of an "Accounting Configuration") of a demo
 * cost case.
 * <p>
 * Layout of the cases with invoices: columns Position | Satz | Messung | Rechnung | Differenz. "Messung" prices the
 * measured consumption with the contract, "Rechnung" prices the invoiced consumption (series "Verbrauch laut
 * Rechnung" of the case's invoice folder) with the same contract and shows the invoiced net and gross amounts,
 * "Differenz" = Messung − Rechnung. Below: "Soll laut Vertrag" (sum of the Rechnung lines) and "Abweichung Rechnung −
 * Vertrag" (invoiced net − Soll), which shows billing errors independent of the consumption.
 * <p>
 * How the formulas use the calculator: every input has a formula of its own and the cost lines only combine formula
 * results (fixed values), so no line mixes inputs with different time steps. Prices are {@code NON_PERIODIC} (last
 * sample at or before the start), yearly amounts {@code YEARLY_VALUE} (prorated to the days), the peak is the
 * {@code MAX} of the year up to the end of the month (time restriction fixed "P1Y", reducing "NONE"), the demand
 * charge a {@code RANGING_VALUE} of the grid operator billed with 1/12 per month.
 * <p>
 * Ids are name-based UUIDs of the case and the element, so the files are the same on every run.
 */
public final class TemplateFactory {

    static final String[] HEADER = {"Position", "Satz", "Messung", "Rechnung", "Differenz"};
    static final int COL_SATZ = 1;
    static final int COL_M = 2;
    static final int COL_R = 3;
    static final int COL_D = 4;

    private TemplateFactory() {
    }

    /**
     * The template and configuration of a case, and the object every input is bound to.
     */
    public static final class Built {
        private final CostCase costCase;
        private final RCTemplate template;
        private final SelectionTemplate selection;
        private final Map<String, Ref> bindings;
        private final Map<String, String> outputs;

        Built(CostCase costCase, RCTemplate template, SelectionTemplate selection, Map<String, Ref> bindings, Map<String, String> outputs) {
            this.costCase = costCase;
            this.template = template;
            this.selection = selection;
            this.bindings = bindings;
            this.outputs = outputs;
        }

        public CostCase getCostCase() {
            return costCase;
        }

        public RCTemplate getTemplate() {
            return template;
        }

        public SelectionTemplate getSelection() {
            return selection;
        }

        /**
         * @return template input id → bound object
         */
        public Map<String, Ref> getBindings() {
            return bindings;
        }

        /**
         * @return id of the output of a formula by formula name (e.g. "M_Netto"), null if it has no output
         */
        public String output(String formulaName) {
            return outputs.get(formulaName);
        }
    }

    /**
     * @return template and configuration of the case
     */
    public static Built build(CostCase c) {
        Builder b = new Builder(c);
        if (!c.chargePoints.isEmpty()) {
            b.chargingBill();
        } else {
            b.costBill();
        }
        RCTemplate template = new RCTemplate();
        template.setTemplateInputs(b.inputs);
        template.setTemplateFormulas(b.formulas);
        template.setTemplateOutputs(b.outputs);
        Map<String, Boolean> frames = new LinkedHashMap<>();
        for (String id : TrcTimeFrames.REDUCED) frames.put(id, "P1M".equals(id) || "P1Y".equals(id));
        template.setIntervalSelectorConfiguration(frames);

        SelectionTemplate selection = new SelectionTemplate();
        for (TemplateInput input : b.inputs) {
            TemplateInput selected = new TemplateInput();
            selected.setId(input.getId());
            selected.setVariableName(input.getVariableName());
            selected.clone(input);
            Ref ref = b.bindings.get(input.getId());
            selected.setObjectID(ref != null && ref.isResolved() ? ref.getId() : null);
            selection.getSelectedInputs().add(selected);
        }
        selection.setContractNumber(c.contractNumber);
        selection.setContractType(c.contractType.name());
        selection.setMarketLocationNumber(c.marketLocation);
        selection.setContractDate("2024-10-15T00:00:00.000+02:00");
        selection.setFirstRate("2025-02-01T00:00:00.000+01:00");
        selection.setPeriodOfNotice("2027-09-30T00:00:00.000+02:00");
        selection.setContractStart(c.contractStart);
        selection.setContractEnd(c.contractEnd);
        return new Built(c, template, selection, b.bindings, b.formulaOutputs);
    }

    /**
     * @return the "Template File" JSON as the Control Center writes it ({@link TemplateHandler#toJsonNode()})
     */
    public static JsonNode templateJson(Built built) {
        TemplateHandler handler = new TemplateHandler();
        handler.setRcTemplate(built.template);
        return handler.toJsonNode();
    }

    /**
     * @return the "Template File" JSON of the accounting configuration
     * ({@link AccountingTemplateHandler#toJsonNode()}); inputs bound to objects that step 3 still has to create carry
     * {@code "objectID": null} and {@code "objectKey": "@key"}, the template {@code "templateSelection": null} and
     * {@code "templateKey"}
     */
    public static JsonNode configurationJson(Built built) {
        AccountingTemplateHandler handler = new AccountingTemplateHandler();
        handler.setSelectionTemplate(built.selection);
        ObjectNode node = (ObjectNode) handler.toJsonNode();
        ArrayNode inputs = (ArrayNode) node.get("selectedInputs");
        for (JsonNode input : inputs) {
            Ref ref = built.bindings.get(input.get("id").asText());
            if (ref != null && !ref.isResolved()) ((ObjectNode) input).put("objectKey", ref.toString());
        }
        node.put("templateKey", "@" + templateKey(built.costCase));
        return node;
    }

    static String templateKey(CostCase c) {
        return "T." + c.key;
    }

    static String configurationKey(CostCase c) {
        return "K." + c.key;
    }

    // ============================================================================================ builder

    private static final class Builder {
        final CostCase c;
        final List<TemplateInput> inputs = new ArrayList<>();
        final List<TemplateFormula> formulas = new ArrayList<>();
        final List<TemplateOutput> outputs = new ArrayList<>();
        final Map<String, Ref> bindings = new LinkedHashMap<>();
        final Map<String, String> formulaOutputs = new LinkedHashMap<>();
        int row;

        Builder(CostCase c) {
            this.c = c;
        }

        String uuid(String kind, String name) {
            return UUID.nameUUIDFromBytes(("jevis-costdemo:" + c.key + ":" + kind + ":" + name).getBytes(StandardCharsets.UTF_8)).toString();
        }

        TemplateInput input(String variable, String objectClass, String attribute, InputVariableType type, Ref object,
                            boolean group, String filter) {
            TemplateInput input = new TemplateInput();
            input.setId(uuid("input", variable));
            input.setVariableName(variable);
            input.setObjectClass(objectClass);
            input.setAttributeName(attribute);
            input.setVariableType(type.toString());
            input.setQuantity(type == InputVariableType.SUM);
            input.setGroup(group);
            input.setFilter(filter);
            input.setTimeRestrictionEnabled(false);
            inputs.add(input);
            bindings.put(input.getId(), object);
            return input;
        }

        /**
         * @param uses the inputs and formulas the expression uses
         */
        TemplateFormula formula(String name, String expression, Object... uses) {
            TemplateFormula formula = new TemplateFormula();
            formula.setId(uuid("formula", name));
            formula.setName(name);
            formula.setFormula(expression);
            formula.setTimeRestrictionEnabled(false);
            List<String> ids = new ArrayList<>();
            for (Object use : uses) {
                if (use instanceof TemplateInput) ids.add(((TemplateInput) use).getId());
                else if (use instanceof TemplateFormula) ids.add(((TemplateFormula) use).getId());
                else throw new IllegalArgumentException("not an input or formula: " + use);
            }
            formula.setInputIds(ids);
            formulas.add(formula);
            return formula;
        }

        TemplateOutput cell(int row, int column, int colSpan, String unit, TemplateFormula formula) {
            TemplateOutput out = new TemplateOutput();
            out.setId(uuid("output", row + ":" + column));
            out.setRow(row);
            out.setColumn(column);
            out.setColSpan(colSpan);
            out.setRowSpan(1);
            out.setUnit(unit);
            out.setResultBold(false);
            out.setNameBold(false);
            out.setShowTooltip(false);
            out.setShowAnalysisLink(false);
            out.setLink(false);
            out.setSeparator(false);
            if (formula != null) {
                formula.setOutput(out.getId());
                formulaOutputs.put(formula.getName(), out.getId());
            }
            outputs.add(out);
            return out;
        }

        TemplateOutput value(int column, String unit, TemplateFormula formula) {
            return cell(row, column, 1, unit, formula);
        }

        void label(int column, String text, boolean bold) {
            TemplateOutput out = cell(row, column, 1, null, null);
            out.setName(text);
            out.setNameBold(bold);
        }

        void separator(int colSpan) {
            TemplateOutput out = cell(row, 0, colSpan, null, null);
            out.setName("Trenner " + row);
            out.setSeparator(true);
            row++;
        }

        static String p(Object formulaOrInput) {
            String name = formulaOrInput instanceof TemplateFormula ? ((TemplateFormula) formulaOrInput).getName()
                    : ((TemplateInput) formulaOrInput).getVariableName();
            return "(" + name + ")";
        }

        /**
         * a formula with only this input
         */
        TemplateFormula of(String name, TemplateInput input) {
            return formula(name, input.getVariableName(), input);
        }

        TemplateFormula difference(String name, TemplateFormula m, TemplateFormula r) {
            return formula(name, p(m) + "-" + p(r), m, r);
        }

        TemplateFormula sum(String name, List<TemplateFormula> parts) {
            StringBuilder sb = new StringBuilder();
            for (TemplateFormula f : parts) {
                if (sb.length() > 0) sb.append('+');
                sb.append(p(f));
            }
            return formula(name, sb.length() > 0 ? sb.toString() : "0", parts.toArray());
        }

        TemplateInput price(String variable, Value value, InputVariableType type) {
            boolean group = !"Base Data".equals(value.objectClass) && !"Clean Data".equals(value.objectClass);
            return input(variable, value.objectClass, value.attribute, type, value.object, group, group ? null : value.filter);
        }

        void header(String... labels) {
            for (int i = 0; i < labels.length; i++) label(i, labels[i], true);
            row++;
            separator(labels.length);
        }

        // -------------------------------------------------------------------------------- cases A–E

        void costBill() {
            header(HEADER);

            /* info rows: names of the contract partners */
            for (Value info : c.infos) {
                String id = ident(info.label);
                TemplateInput name = input("Name_" + id, info.objectClass, "name", InputVariableType.STRING, info.object, true, null);
                label(0, info.label, false);
                cell(row, COL_SATZ, 4, null, formula("Text_" + id, name.getVariableName(), name));
                row++;
            }
            separator(HEADER.length);

            /* consumption */
            TemplateFormula measured;
            if (c.heatingValue != null) {
                TemplateInput volume = input("Volumen_Zaehler", c.consumption.objectClass, "Value", InputVariableType.SUM,
                        c.consumption.object, false, c.consumption.filter);
                TemplateFormula fVolume = of("M_Volumen", volume);
                TemplateFormula fHs = of("Brennwert", price("Brennwert_Wert", c.heatingValue, InputVariableType.NON_PERIODIC));
                TemplateFormula fZ = of("Zustandszahl", price("Zustandszahl_Wert", c.stateNumber, InputVariableType.NON_PERIODIC));
                label(0, "Volumen (Zählerstandsdifferenz)", false);
                value(COL_M, "m³", fVolume).setShowAnalysisLink(true);
                row++;
                label(0, "Brennwert", false);
                value(COL_SATZ, "kWh/m³", fHs);
                row++;
                label(0, "Zustandszahl", false);
                value(COL_SATZ, null, fZ);
                row++;
                measured = formula("M_Verbrauch", p(fVolume) + "*" + p(fZ) + "*" + p(fHs), fVolume, fZ, fHs);
            } else {
                TemplateInput consumption = input("Verbrauch_Zaehler", c.consumption.objectClass, "Value", InputVariableType.SUM,
                        c.consumption.object, false, c.consumption.filter);
                measured = of("M_Verbrauch", consumption);
            }
            TemplateInput invoiced = input("Verbrauch_Rechnung", "Energy Invoice Data", "Value", InputVariableType.SUM,
                    c.invoiceConsumption, false, "Verbrauch laut Rechnung");
            TemplateFormula rConsumption = of("R_Verbrauch", invoiced);
            label(0, "Verbrauch", true);
            TemplateOutput mOut = value(COL_M, "kWh", measured);
            if (c.heatingValue == null) mOut.setShowAnalysisLink(true);
            value(COL_R, "kWh", rConsumption).setShowAnalysisLink(true);
            value(COL_D, "kWh", difference("D_Verbrauch", measured, rConsumption));
            row++;

            /* facts in the Satz column */
            Map<Value, TemplateFormula> facts = new LinkedHashMap<>();
            for (Value fact : c.facts) {
                String id = ident(fact.label);
                TemplateFormula f = of("Wert_" + id, price(id + "_Wert", fact, InputVariableType.NON_PERIODIC));
                facts.put(fact, f);
                label(0, fact.label, false);
                value(COL_SATZ, fact.unit == null || fact.unit.isEmpty() ? null : fact.unit, f);
                row++;
            }

            /* peak */
            TemplateInput peakInput = null;
            TemplateFormula peak = null;
            if (c.peak != null) {
                String variable = c.peakFactor == 1 ? "Spitze_Stunde" : "Spitze_Viertelstunde";
                peakInput = input(variable, c.peak.objectClass, "Value", InputVariableType.MAX, c.peak.object, false, c.peak.filter);
                String expression = c.peakFactor == 1 ? peakInput.getVariableName() : p(peakInput) + "*" + number(c.peakFactor);
                peak = formula("Leistung", expression, peakInput);
                peak.setTimeRestrictionEnabled(true);
                peak.setFixedTimeFrame("P1Y");
                peak.setReducingTimeFrame(TrcTimeFrames.NONE);
                label(0, "Leistung (Höchstwert seit Jahresbeginn)", false);
                value(COL_M, c.peakUnit, peak);
                row++;
            }
            separator(HEADER.length);

            /* positions */
            List<TemplateFormula> mLines = new ArrayList<>();
            List<TemplateFormula> rLines = new ArrayList<>();
            for (Position pos : c.positions) {
                String k = pos.key;
                TemplateFormula satz;
                TemplateFormula m;
                TemplateFormula r;
                String satzUnit;
                switch (pos.type) {
                    case InvoiceCase.ENERGY: {
                        satz = of("Satz_" + k, price(k + "_Preis", pos.price, InputVariableType.NON_PERIODIC));
                        satzUnit = "ct/kWh";
                        m = formula("M_" + k, p(measured) + "*" + p(satz) + "/100", measured, satz);
                        r = formula("R_" + k, p(rConsumption) + "*" + p(satz) + "/100", rConsumption, satz);
                        break;
                    }
                    case InvoiceCase.INDEXED: {
                        TemplateFormula base = of("Basis_" + k, price(k + "_Basispreis", pos.price, InputVariableType.NON_PERIODIC));
                        label(0, pos.price.label, false);
                        value(COL_SATZ, "€/MWh", base);
                        row++;
                        StringBuilder factor = new StringBuilder(number(pos.indexConstant));
                        List<Object> uses = new ArrayList<>();
                        uses.add(base);
                        for (IndexTerm term : pos.terms) {
                            TemplateFormula index = facts.get(term.value);
                            if (index == null) throw new IllegalStateException("index " + term.label + " is no fact of case " + c.key);
                            factor.append('+').append(number(term.weight)).append('*').append(p(index)).append('/').append(number(term.base));
                            uses.add(index);
                        }
                        /* EUR/MWh → ct/kWh */
                        satz = formula("Satz_" + k, p(base) + "*(" + factor + ")/10", uses.toArray());
                        satzUnit = "ct/kWh";
                        m = formula("M_" + k, p(measured) + "*" + p(satz) + "/100", measured, satz);
                        r = formula("R_" + k, p(rConsumption) + "*" + p(satz) + "/100", rConsumption, satz);
                        break;
                    }
                    case InvoiceCase.YEARLY: {
                        satz = of("Satz_" + k, price(k + "_Jahrespreis", pos.price, InputVariableType.NON_PERIODIC));
                        satzUnit = "€/a";
                        m = of("M_" + k, price(k + "_anteilig", pos.price, InputVariableType.YEARLY_VALUE));
                        r = formula("R_" + k, p(m), m);
                        break;
                    }
                    case InvoiceCase.DEMAND: {
                        if (peak == null) throw new IllegalStateException("demand charge without peak in case " + c.key);
                        TemplateInput table = price(k + "_Preis", pos.price, InputVariableType.RANGING_VALUE);
                        table.setDependency(peakInput.getId());
                        satz = of("Satz_" + k, table);
                        satzUnit = "€/kW/a";
                        m = formula("M_" + k, p(satz) + "*" + p(peak) + "/12", satz, peak);
                        r = formula("R_" + k, p(m), m);
                        break;
                    }
                    case InvoiceCase.CAPACITY: {
                        TemplateFormula capacity = facts.get(pos.capacity);
                        if (capacity == null) throw new IllegalStateException("capacity is no fact of case " + c.key);
                        satz = of("Satz_" + k, price(k + "_Preis", pos.price, InputVariableType.NON_PERIODIC));
                        satzUnit = "€/kW/a";
                        m = formula("M_" + k, p(satz) + "*" + p(capacity) + "/12", satz, capacity);
                        r = formula("R_" + k, p(m), m);
                        break;
                    }
                    default:
                        throw new IllegalStateException("unknown position type " + pos.type);
                }
                label(0, pos.label, false);
                value(COL_SATZ, satzUnit, satz);
                value(COL_M, "€", m);
                value(COL_R, "€", r);
                value(COL_D, "€", difference("D_" + k, m, r));
                mLines.add(m);
                rLines.add(r);
                row++;
            }
            separator(HEADER.length);

            /* totals */
            TemplateFormula mNet = sum("M_Netto", mLines);
            TemplateFormula rNet = of("R_Netto", input("Netto_Rechnung", "Energy Invoice Data", "Value", InputVariableType.SUM,
                    c.invoiceNet, false, "Kosten netto laut Rechnung"));
            TemplateFormula rGross = of("R_Brutto", input("Brutto_Rechnung", "Energy Invoice Data", "Value", InputVariableType.SUM,
                    c.invoiceGross, false, "Kosten brutto laut Rechnung"));
            TemplateFormula vat = of("Satz_USt", price("USt_Satz", c.vat, InputVariableType.NON_PERIODIC));
            TemplateFormula mVat = formula("M_USt", p(mNet) + "*" + p(vat) + "/100", mNet, vat);
            TemplateFormula rVat = formula("R_USt", p(rGross) + "-" + p(rNet), rGross, rNet);
            TemplateFormula mGross = formula("M_Brutto", p(mNet) + "+" + p(mVat), mNet, mVat);

            label(0, "Nettobetrag", true);
            value(COL_M, "€", mNet).setResultBold(true);
            value(COL_R, "€", rNet).setResultBold(true);
            value(COL_D, "€", difference("D_Netto", mNet, rNet)).setResultBold(true);
            row++;
            label(0, "Umsatzsteuer", false);
            value(COL_SATZ, "%", vat);
            value(COL_M, "€", mVat);
            value(COL_R, "€", rVat);
            value(COL_D, "€", difference("D_USt", mVat, rVat));
            row++;
            label(0, "Bruttobetrag", true);
            value(COL_M, "€", mGross).setResultBold(true);
            value(COL_R, "€", rGross).setResultBold(true);
            value(COL_D, "€", difference("D_Brutto", mGross, rGross)).setResultBold(true);
            row++;
            separator(HEADER.length);

            TemplateFormula due = sum("R_Soll", rLines);
            label(0, "Soll laut Vertrag (Rechnungsmenge)", false);
            value(COL_R, "€", due);
            row++;
            label(0, "Abweichung Rechnung zu Vertrag (netto)", true);
            value(COL_D, "€", formula("Pruefung_Rechnung", p(rNet) + "-" + p(due), rNet, due)).setResultBold(true);
            row++;
        }

        // -------------------------------------------------------------------------------- case F

        void chargingBill() {
            header("Ladepunkt", "Kennzeichen", "Kostenstelle", "Personalnr.", "Verbrauch", "Preis", "Netto");
            TemplateInput price = price("Ladestrompreis", c.chargePrice, InputVariableType.NON_PERIODIC);
            List<TemplateFormula> energies = new ArrayList<>();
            List<TemplateFormula> amounts = new ArrayList<>();
            int n = 0;
            for (ChargePoint cp : c.chargePoints) {
                n++;
                String lp = "LP" + n;
                label(0, cp.label, false);
                TemplateInput plate = input("Kennzeichen_" + lp, "String Data", "Value", InputVariableType.STRING, cp.plate, false, cp.label + " Kennzeichen");
                TemplateInput cost = input("Kostenstelle_" + lp, "String Data", "Value", InputVariableType.STRING, cp.costCenter, false, cp.label + " Kostenstelle");
                TemplateInput person = input("Personalnummer_" + lp, "String Data", "Value", InputVariableType.STRING, cp.person, false, cp.label + " Personalnummer");
                value(1, null, formula("Text_Kennzeichen_" + lp, plate.getVariableName(), plate));
                value(2, null, formula("Text_Kostenstelle_" + lp, cost.getVariableName(), cost));
                value(3, null, formula("Text_Personalnummer_" + lp, person.getVariableName(), person));
                TemplateInput energy = input("Ladestrom_" + lp, cp.row.objectClass, "Value", InputVariableType.SUM, cp.row.object, false, cp.row.filter);
                TemplateFormula kWh = of("Verbrauch_" + lp, energy);
                value(4, "kWh", kWh).setShowAnalysisLink(true);
                value(5, "ct/kWh", formula("Preis_" + lp, p(price), price));
                TemplateFormula amount = formula("Netto_" + lp, p(kWh) + "*" + p(price) + "/100", kWh, price);
                value(6, "€", amount);
                energies.add(kWh);
                amounts.add(amount);
                row++;
            }
            separator(7);
            TemplateFormula totalKWh = sum("Verbrauch_Summe", energies);
            TemplateFormula net = sum("M_Netto", amounts);
            TemplateFormula vat = of("Satz_USt", price("USt_Satz", c.vat, InputVariableType.NON_PERIODIC));
            TemplateFormula mVat = formula("M_USt", p(net) + "*" + p(vat) + "/100", net, vat);
            label(0, "Nettobetrag", true);
            value(4, "kWh", totalKWh);
            value(6, "€", net).setResultBold(true);
            row++;
            label(0, "Umsatzsteuer", false);
            value(5, "%", vat);
            value(6, "€", mVat);
            row++;
            label(0, "Bruttobetrag", true);
            value(6, "€", formula("M_Brutto", p(net) + "+" + p(mVat), net, mVat)).setResultBold(true);
            row++;
        }
    }

    /**
     * @return the label as an identifier: letters and digits, umlauts replaced, other characters as "_"
     */
    static String ident(String label) {
        String s = label.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("Ä", "Ae")
                .replace("Ö", "Oe").replace("Ü", "Ue").replace("ß", "ss");
        s = s.replaceAll("[^A-Za-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s;
    }

    static String number(double value) {
        if (value == Math.rint(value)) return String.valueOf((long) value);
        return String.format(Locale.ROOT, "%s", value);
    }
}
