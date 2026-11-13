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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One monthly demo invoice of the JEDemo job, an entry of the JSON array in the attribute "Invoice Cases" of the
 * service object (see docs/DEMO.md). The invoice lines are priced from the contract objects as the cost calculation
 * templates price them, so the "Messung" and "Rechnung" columns of the demo cost cases match, except for the declared
 * {@link Deviation deviations}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"key", "name", "medium", "suffix", "directory", "seller", "sellerVatId", "meterPointLabel",
        "meterPoint", "consumption", "peak", "vat", "lines", "deviations"})
public class InvoiceCase {

    public static final String ENERGY = "ENERGY";
    public static final String INDEXED = "INDEXED";
    public static final String YEARLY = "YEARLY";
    public static final String MONTHLY = "MONTHLY";
    public static final String DEMAND = "DEMAND";
    public static final String CAPACITY = "CAPACITY";

    public static final String DUPLICATE_LINE = "DUPLICATE_LINE";
    public static final String PREVIOUS_YEAR_PRICE = "PREVIOUS_YEAR_PRICE";
    public static final String QUANTITY_FACTOR = "QUANTITY_FACTOR";

    /**
     * short key of the case, e.g. "A"
     */
    public String key;
    /**
     * name for the log and the invoice note
     */
    public String name;
    /**
     * medium of the invoice import: ELECTRICITY, GAS or DISTRICT_HEATING
     */
    public String medium;
    /**
     * end of the invoice number: "EN-202610-" + suffix
     */
    public String suffix;
    /**
     * Energy Invoices Directory of the case (its "Medium" should be the medium, then the import stores the invoices
     * and the series there)
     */
    public Ref directory;
    public String seller;
    public String sellerVatId;
    /**
     * text in front of the meter point in the invoice note, e.g. "Zählpunkt" or "Marktlokation"
     */
    public String meterPointLabel;
    public String meterPoint;
    public Consumption consumption;
    public Peak peak;
    /**
     * VAT rate in percent (last sample at or before the start of the month)
     */
    public Value vat;
    public List<Line> lines = new ArrayList<>();
    public List<Deviation> deviations = new ArrayList<>();

    /**
     * An attribute of an object, e.g. the price "Energy Price HT" of a supplier.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"object", "attribute"})
    public static class Value {
        public Ref object;
        public String attribute;

        public Value() {
        }

        public Value(Ref object, String attribute) {
            this.object = object;
            this.attribute = attribute;
        }

        @Override
        public String toString() {
            return object + ":" + attribute;
        }
    }

    /**
     * The consumption of the month: sum of the row in [start of month, start of next month). With a heating value
     * the row holds volumes (m³) and the energy is volume × state number × heating value (both at the start of the
     * month).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"row", "completeRow", "heatingValue", "stateNumber"})
    public static class Consumption {
        public Ref row;
        /**
         * row whose latest sample must reach the start of the next month before the month is billed (default
         * {@link #row}); for monthly counter readings the raw row
         */
        public Ref completeRow;
        public Value heatingValue;
        public Value stateNumber;
    }

    /**
     * The peak for the demand charge: maximum of the row times the factor (4 for 15-minute kWh → kW), from the start
     * of the year (or month) to the end of the billed month.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"row", "factor", "yearToDate"})
    public static class Peak {
        public Ref row;
        public double factor = 1;
        public boolean yearToDate = true;
    }

    /**
     * An invoice line. Types:
     * <ul>
     * <li>{@value #ENERGY}: consumption × price ({@link #priceUnit} ct/kWh, EUR/kWh or EUR/MWh), unit KWH;</li>
     * <li>{@value #INDEXED}: like ENERGY with the price = base price × (constant + Σ weight × index / base index),
     * see {@link Index};</li>
     * <li>{@value #YEARLY}: EUR per year prorated to the days of the month (last sample in the calendar year), unit
     * DAY;</li>
     * <li>{@value #MONTHLY}: EUR per month, unit MON;</li>
     * <li>{@value #DEMAND}: peak × price of the ranging table (EUR/kW/a) / 12, unit KWT;</li>
     * <li>{@value #CAPACITY}: capacity ({@link #capacity}, kW) × price (EUR/kW/a) / 12, unit KWT.</li>
     * </ul>
     * Prices are the last sample at or before the start of the month.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"key", "text", "type", "price", "priceUnit", "capacity", "index"})
    public static class Line {
        public String key;
        public String text;
        public String type;
        public Value price;
        public String priceUnit;
        public Value capacity;
        public Index index;
    }

    /**
     * Price escalation clause: price = base × (constant + Σ weight × value / base value).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Index {
        public double constant;
        public List<Term> terms = new ArrayList<>();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"weight", "value", "base"})
    public static class Term {
        public double weight;
        public Value value;
        public double base = 100;
    }

    /**
     * A deliberate billing error of one month, so the cost calculation has something to find:
     * <ul>
     * <li>{@value #DUPLICATE_LINE}: the line {@link #line} is billed twice;</li>
     * <li>{@value #PREVIOUS_YEAR_PRICE}: the line {@link #line} is priced as one year earlier;</li>
     * <li>{@value #QUANTITY_FACTOR}: the billed consumption is multiplied by {@link #factor} (estimated reading).</li>
     * </ul>
     * {@link #month} is "yyyy-MM" (that month) or "MM" (that month of every year).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonPropertyOrder({"month", "type", "line", "factor", "note"})
    public static class Deviation {
        public String month;
        public String type;
        public String line;
        public Double factor;
        public String note;

        public boolean appliesTo(YearMonth period) {
            if (month == null) return false;
            String m = month.trim();
            if (m.length() == 2 || m.length() == 1) {
                try {
                    return Integer.parseInt(m) == period.getMonthValue();
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            try {
                return YearMonth.parse(m).equals(period);
            } catch (RuntimeException e) {
                return false;
            }
        }
    }

    /**
     * @return the deviations of the month
     */
    public List<Deviation> deviations(YearMonth period) {
        List<Deviation> result = new ArrayList<>();
        if (deviations == null) return result;
        for (Deviation deviation : deviations) {
            if (deviation != null && deviation.appliesTo(period)) result.add(deviation);
        }
        return result;
    }

    /**
     * @return the problems that keep the case from being billed (unresolved keys, missing settings), empty if none
     */
    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (key == null || key.trim().isEmpty()) problems.add("no key");
        if (suffix == null || !suffix.matches("[A-Za-z0-9-]{1,10}")) problems.add("invalid suffix '" + suffix + "'");
        try {
            InvoiceBuilder.Medium.valueOf(String.valueOf(medium).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add("invalid medium '" + medium + "'");
        }
        unresolved(problems, "directory", directory);
        if (consumption == null) {
            problems.add("no consumption");
        } else {
            unresolved(problems, "consumption.row", consumption.row);
            if (consumption.completeRow != null) unresolved(problems, "consumption.completeRow", consumption.completeRow);
            if (consumption.heatingValue != null) unresolved(problems, "consumption.heatingValue", consumption.heatingValue.object);
            if (consumption.stateNumber != null) unresolved(problems, "consumption.stateNumber", consumption.stateNumber.object);
        }
        if (peak != null) unresolved(problems, "peak.row", peak.row);
        if (vat != null) unresolved(problems, "vat", vat.object);
        if (lines == null || lines.isEmpty()) problems.add("no lines");
        else {
            for (Line line : lines) {
                if (line.price != null) unresolved(problems, "line " + line.key, line.price.object);
                if (line.capacity != null) unresolved(problems, "line " + line.key + " capacity", line.capacity.object);
                if (line.index != null && line.index.terms != null) {
                    for (Term term : line.index.terms) {
                        if (term.value != null) unresolved(problems, "line " + line.key + " index", term.value.object);
                    }
                }
                if (DEMAND.equals(line.type) && peak == null) problems.add("line " + line.key + " needs a peak");
            }
        }
        return problems;
    }

    private static void unresolved(List<String> problems, String what, Ref ref) {
        if (ref == null) problems.add(what + " missing");
        else if (!ref.isResolved()) problems.add(what + " is the unresolved key " + ref);
    }

    @Override
    public String toString() {
        return key + " (" + name + ")";
    }
}
