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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An invoice entered by hand (R01, docs/ENERGY-INVOICES.md "Manual invoices"): the form of the web app becomes the same
 * {@link EInvoice} model a ZUGFeRD/XRechnung file gives, so storage, duplicate check and the series are the import's.
 * Format {@value #FORMAT}; the consumption follows from the lines with energy units (as for e-invoices) unless it is
 * given; missing line amounts are quantity × unit price, missing totals the sums of the lines.
 */
public final class ManualInvoice {

    public static final String FORMAT = "MANUAL";
    public static final int MAX_LINES = 200;
    public static final int MAX_TEXT = 255;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ManualInvoice() {
    }

    /** the invoice and its analysis (period, consumption, meter point) */
    public static final class Parsed {
        public final EInvoice invoice;
        public final InvoiceAnalysis.Analysis analysis;
        public final Medium medium;

        Parsed(EInvoice invoice, InvoiceAnalysis.Analysis analysis, Medium medium) {
            this.invoice = invoice;
            this.analysis = analysis;
            this.medium = medium;
        }
    }

    /**
     * Reads {@code {medium, seller, sellerVatId?, buyer?, number, date, periodStart?, periodEnd?, meterPoint?,
     * currency?, creditNote?, consumption?: {value, unit}, lines?: [{description, quantity?, unit?, unitPrice?, net?,
     * taxRate?}], net?, tax?, gross?}}.
     *
     * @throws InvoiceException 400 {@code INVALID} with the problems in the message
     */
    public static Parsed parse(String body) throws InvoiceException {
        JsonNode json;
        try {
            json = MAPPER.readTree(body == null ? "" : body);
        } catch (Exception e) {
            throw invalid("The invoice data is no JSON object.");
        }
        if (json == null || !json.isObject()) throw invalid("The invoice data is no JSON object.");
        List<String> problems = new ArrayList<>();

        Medium medium = Medium.parse(text(json, "medium", problems));
        if (medium == null) problems.add("medium: one of ELECTRICITY, GAS, DISTRICT_HEATING, OTHER expected");

        EInvoice invoice = new EInvoice();
        invoice.format = FORMAT;
        invoice.seller = required(json, "seller", problems);
        invoice.sellerVatId = text(json, "sellerVatId", problems);
        invoice.buyer = text(json, "buyer", problems);
        invoice.number = required(json, "number", problems);
        invoice.date = date(json, "date", true, problems);
        LocalDate periodStart = date(json, "periodStart", false, problems);
        LocalDate periodEnd = date(json, "periodEnd", false, problems);
        if ((periodStart == null) != (periodEnd == null)) problems.add("periodStart and periodEnd: both or none expected");
        if (periodStart != null && periodEnd != null && periodEnd.isBefore(periodStart)) problems.add("periodEnd: before periodStart");
        String currency = text(json, "currency", problems);
        invoice.currency = currency != null ? currency.toUpperCase(Locale.ROOT) : "EUR";
        if (!invoice.currency.matches("[A-Z]{3}")) problems.add("currency: ISO 4217 code expected, e.g. EUR");
        JsonNode credit = json.get("creditNote");
        if (credit != null && !credit.isNull() && !credit.isBoolean()) problems.add("creditNote: boolean expected");
        invoice.creditNote = credit != null && credit.asBoolean(false);

        JsonNode lines = json.get("lines");
        BigDecimal lineNet = BigDecimal.ZERO;
        BigDecimal lineTax = BigDecimal.ZERO;
        boolean taxKnown = true;
        if (lines != null && !lines.isNull()) {
            if (!lines.isArray()) {
                problems.add("lines: array expected");
            } else if (lines.size() > MAX_LINES) {
                problems.add("lines: at most " + MAX_LINES);
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    JsonNode node = lines.get(i);
                    String at = "lines[" + i + "].";
                    if (!node.isObject()) {
                        problems.add("lines[" + i + "]: object expected");
                        continue;
                    }
                    EInvoice.Line line = new EInvoice.Line();
                    line.description = required(node, "description", problems, at);
                    line.quantity = number(node, "quantity", problems, at);
                    String unit = text(node, "unit", problems, at);
                    line.unitCode = unit != null ? unit.toUpperCase(Locale.ROOT) : null;
                    line.unitPrice = number(node, "unitPrice", problems, at);
                    line.net = number(node, "net", problems, at);
                    line.taxRate = number(node, "taxRate", problems, at);
                    if (line.net == null && line.quantity != null && line.unitPrice != null) {
                        line.net = line.quantity.multiply(line.unitPrice).setScale(2, RoundingMode.HALF_UP);
                    }
                    if (line.net == null) {
                        problems.add(at + "net: amount or quantity and unit price expected");
                        continue;
                    }
                    line.periodStart = periodStart;
                    line.periodEnd = periodEnd;
                    lineNet = lineNet.add(line.net);
                    if (line.taxRate != null) {
                        lineTax = lineTax.add(line.net.multiply(line.taxRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                    } else {
                        taxKnown = false;
                    }
                    invoice.lines.add(line);
                }
            }
        }

        BigDecimal net = number(json, "net", problems, "");
        BigDecimal tax = number(json, "tax", problems, "");
        BigDecimal gross = number(json, "gross", problems, "");
        boolean hasLines = !invoice.lines.isEmpty();
        invoice.net = net != null ? net : hasLines ? lineNet : null;
        invoice.tax = tax != null ? tax : hasLines && taxKnown ? lineTax : null;
        invoice.gross = gross != null ? gross : invoice.net != null && invoice.tax != null ? invoice.net.add(invoice.tax) : null;
        if (invoice.net == null) problems.add("net: amount or lines expected");
        if (invoice.gross == null) problems.add("gross: amount or tax rates of all lines expected");

        if (!problems.isEmpty()) throw invalid(String.join("; ", problems));

        InvoiceAnalysis.Analysis analysis = InvoiceAnalysis.analyse(invoice);
        if (periodStart != null) {
            analysis.periodStart = periodStart;
            analysis.periodEnd = periodEnd;
        }
        JsonNode consumption = json.get("consumption");
        if (consumption != null && !consumption.isNull()) {
            BigDecimal value = number(consumption, "value", problems, "consumption.");
            String unit = text(consumption, "unit", problems, "consumption.");
            String unitName = unit != null ? InvoiceAnalysis.unitName(unit) : null;
            if (value == null || unitName == null || !(unitName.equals("kWh") || unitName.equals("m³") || unitName.equals("MWh"))) {
                problems.add("consumption: value and unit kWh, MWh or m³ expected");
            } else if (unitName.equals("MWh")) {
                analysis.consumption = new InvoiceAnalysis.Consumption(value.multiply(BigDecimal.valueOf(1000)), "kWh");
            } else {
                analysis.consumption = new InvoiceAnalysis.Consumption(value, unitName);
            }
            if (!problems.isEmpty()) throw invalid(String.join("; ", problems));
            if (invoice.creditNote && analysis.consumption.value.signum() > 0) {
                analysis.consumption = new InvoiceAnalysis.Consumption(analysis.consumption.value.negate(), analysis.consumption.unit);
            }
        }
        String meterPoint = text(json, "meterPoint", problems);
        analysis.meterPoint = meterPoint;
        analysis.medium = medium;
        return new Parsed(invoice, analysis, medium);
    }

    private static InvoiceException invalid(String message) {
        return new InvoiceException(400, InvoiceException.INVALID, message);
    }

    private static String text(JsonNode json, String key, List<String> problems) {
        return text(json, key, problems, "");
    }

    private static String text(JsonNode json, String key, List<String> problems, String at) {
        JsonNode node = json.get(key);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) {
            problems.add(at + key + ": text expected");
            return null;
        }
        String value = node.asText().trim();
        if (value.length() > MAX_TEXT) {
            problems.add(at + key + ": at most " + MAX_TEXT + " characters");
            return null;
        }
        return value.isEmpty() ? null : value;
    }

    private static String required(JsonNode json, String key, List<String> problems) {
        return required(json, key, problems, "");
    }

    private static String required(JsonNode json, String key, List<String> problems, String at) {
        int before = problems.size();
        String value = text(json, key, problems, at);
        if (value == null && problems.size() == before) problems.add(at + key + ": required");
        return value;
    }

    private static LocalDate date(JsonNode json, String key, boolean required, List<String> problems) {
        String value = text(json, key, problems);
        if (value == null) {
            if (required) problems.add(key + ": required");
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            problems.add(key + ": date yyyy-MM-dd expected");
            return null;
        }
    }

    private static BigDecimal number(JsonNode json, String key, List<String> problems, String at) {
        JsonNode node = json.get(key);
        if (node == null || node.isNull()) return null;
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual() && !node.asText().trim().isEmpty()) {
            try {
                return new BigDecimal(node.asText().trim());
            } catch (NumberFormatException e) {
                /* reported below */
            }
        }
        if (node.isTextual() && node.asText().trim().isEmpty()) return null;
        problems.add(at + key + ": number expected");
        return null;
    }
}
