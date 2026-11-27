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
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Automatic invoice check (R03, docs/ENERGY-INVOICES.md "Invoice check"): an invoice is matched with its accounting
 * configuration by meter point = market location; the configuration's cost calculation over the billing period gives
 * the measured consumption ({@value #MEASURED}) and the net amount according to the contract for the invoiced
 * quantity ({@value #EXPECTED}); the invoice's own consumption and net amount are compared with them, each with its
 * tolerance in % from the invoice folder. The result is stored at the invoice ("Check Result", JSON); a new deviation
 * is mailed to the folder's recipients.
 */
public final class InvoiceCheck {

    /** formula of the cost calculation template with the measured consumption of the period */
    public static final String MEASURED = "M_Verbrauch";
    /** formula with the net amount according to the contract for the invoiced quantity */
    public static final String EXPECTED = "R_Soll";
    public static final String ATTRIBUTE_RESULT = "Check Result";
    public static final String ATTRIBUTE_TOLERANCE_QUANTITY = "Check Tolerance Quantity";
    public static final String ATTRIBUTE_TOLERANCE_AMOUNT = "Check Tolerance Amount";
    public static final String ATTRIBUTE_RECIPIENTS = "Check Recipients";
    public static final String ATTRIBUTE_RECIPIENT_GROUP = "Check Recipient Group";
    public static final double DEFAULT_TOLERANCE_QUANTITY = 2.0;
    public static final double DEFAULT_TOLERANCE_AMOUNT = 1.0;

    public static final String OK = "OK";
    public static final String DEVIATION = "DEVIATION";
    public static final String NOT_CHECKABLE = "NOT_CHECKABLE";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private InvoiceCheck() {
    }

    /** the values of the invoice the check needs */
    public static final class Invoice {
        public long id;
        public String number;
        public String seller;
        public String meterPoint;
        public DateTime periodStart;
        public DateTime periodEnd;
        public Double consumption;
        public String consumptionUnit;
        public Double net;
        /** status of the previous check, null if none */
        public String previousStatus;
    }

    /** an accounting configuration with its market location */
    public static final class Contract {
        public final long id;
        public final String name;
        public final String marketLocation;

        public Contract(long id, String name, String marketLocation) {
            this.id = id;
            this.name = name;
            this.marketLocation = marketLocation;
        }
    }

    /** tolerances and recipients from the nearest invoice folder that sets them */
    public static final class Settings {
        public double toleranceQuantity = DEFAULT_TOLERANCE_QUANTITY;
        public double toleranceAmount = DEFAULT_TOLERANCE_AMOUNT;
        public String recipients;
        public Long recipientGroup;
    }

    /** where the check gets and puts its data (SQL in the service, a fake in tests) */
    public interface Env {
        Invoice invoice(long id) throws Exception;

        List<Contract> contracts() throws Exception;

        /** the formula results of the configuration's cost calculation for the period */
        Map<String, Double> calculate(long configuration, DateTime from, DateTime to) throws Exception;

        /** time zone of the configuration's site: the invoice period is a range of local dates there */
        default DateTimeZone zone(long configuration) {
            return DateTimeZone.getDefault();
        }

        Settings settings(long invoiceId) throws Exception;

        void store(long invoiceId, String json, DateTime now) throws Exception;

        /** mails the deviation; false if there is no mail server or no recipient */
        boolean mail(Settings settings, Invoice invoice, Map<String, Object> result) throws Exception;
    }

    /** compares one value; null if one of them is missing */
    static Map<String, Object> compare(Double expected, Double actual, double tolerance) {
        if (expected == null || actual == null || expected.isNaN() || actual.isNaN()) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("expected", round(expected, 3));
        json.put("invoiced", round(actual, 3));
        double difference = actual - expected;
        json.put("difference", round(difference, 3));
        /* no percentage against a measured 0 (unless the invoice says 0 too); an if, a ternary would unbox the null */
        Double percent;
        if (expected != 0) percent = difference / Math.abs(expected) * 100;
        else percent = actual == 0 ? 0.0 : null;
        json.put("deviationPercent", percent == null ? null : round(percent, 2));
        json.put("tolerancePercent", tolerance);
        json.put("ok", percent != null && Math.abs(percent) <= tolerance + 1e-9);
        return json;
    }

    private static double round(double value, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(value * f) / f;
    }

    /** meter points / market locations compared without spaces and case */
    static String normalise(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    /**
     * @param marketLocation the contract's field; several identifiers separated by ";" or "," (invoices name the
     *                       market location or the meter point, e.g. "10000000001; DE0000…0001")
     * @return whether one of them is the invoice's meter point
     */
    static boolean matches(String marketLocation, String meterPoint) {
        String wanted = normalise(meterPoint);
        if (marketLocation == null || wanted.isEmpty()) return false;
        for (String id : marketLocation.split("[;,]")) {
            if (!normalise(id).isEmpty() && normalise(id).equals(wanted)) return true;
        }
        return false;
    }

    /**
     * Checks the invoice and stores the result; never throws (a failure is stored as not checkable).
     *
     * @return the result JSON
     */
    public static Map<String, Object> check(Env env, long invoiceId, DateTime now) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", ISODateTimeFormat.dateTime().print(now));
        Invoice invoice = null;
        try {
            invoice = env.invoice(invoiceId);
            if (invoice == null) return result;
            evaluate(env, invoice, result);
        } catch (Exception e) {
            result.put("status", NOT_CHECKABLE);
            result.put("reason", "error");
            result.put("message", e.getMessage());
        }
        try {
            env.store(invoiceId, MAPPER.writeValueAsString(result), now);
        } catch (Exception e) {
            result.put("stored", false);
        }
        if (invoice != null && DEVIATION.equals(result.get("status")) && !DEVIATION.equals(invoice.previousStatus)) {
            try {
                Settings settings = env.settings(invoiceId);
                result.put("mailed", env.mail(settings, invoice, result));
            } catch (Exception e) {
                result.put("mailed", false);
            }
        }
        return result;
    }

    private static void evaluate(Env env, Invoice invoice, Map<String, Object> result) throws Exception {
        if (invoice.meterPoint == null || invoice.meterPoint.trim().isEmpty()) {
            notCheckable(result, "noMeterPoint");
            return;
        }
        Contract contract = null;
        int matches = 0;
        for (Contract c : env.contracts()) {
            if (matches(c.marketLocation, invoice.meterPoint)) {
                if (contract == null) contract = c;
                matches++;
            }
        }
        if (contract == null) {
            notCheckable(result, "noContract");
            return;
        }
        Map<String, Object> contractJson = new LinkedHashMap<>();
        contractJson.put("id", contract.id);
        contractJson.put("name", contract.name);
        result.put("contract", contractJson);
        if (matches > 1) result.put("ambiguous", matches);
        if (invoice.periodStart == null || invoice.periodEnd == null) {
            notCheckable(result, "noPeriod");
            return;
        }
        /* the period of the invoice is a range of days at the site, not of the server (which runs in UTC) */
        DateTimeZone zone = env.zone(contract.id);
        DateTime from = invoice.periodStart.toLocalDate().toDateTimeAtStartOfDay(zone);
        DateTime to = invoice.periodEnd.toLocalDate().plusDays(1).toDateTimeAtStartOfDay(zone).minusMillis(1);
        result.put("from", ISODateTimeFormat.dateTime().print(from));
        result.put("to", ISODateTimeFormat.dateTime().print(to));
        Map<String, Double> formulas;
        try {
            formulas = env.calculate(contract.id, from, to);
        } catch (Exception e) {
            notCheckable(result, "calculation");
            result.put("message", e.getMessage());
            return;
        }
        Settings settings = env.settings(invoice.id);
        Map<String, Object> quantity = compare(formulas.get(MEASURED), invoice.consumption, settings.toleranceQuantity);
        Map<String, Object> amount = compare(formulas.get(EXPECTED), invoice.net, settings.toleranceAmount);
        if (quantity != null) {
            quantity.put("unit", invoice.consumptionUnit);
            result.put("quantity", quantity);
        }
        if (amount != null) result.put("amount", amount);
        if (quantity == null && amount == null) {
            notCheckable(result, formulas.containsKey(MEASURED) || formulas.containsKey(EXPECTED) ? "noValues" : "noCheckFormulas");
            return;
        }
        boolean ok = (quantity == null || Boolean.TRUE.equals(quantity.get("ok"))) && (amount == null || Boolean.TRUE.equals(amount.get("ok")));
        result.put("status", ok ? OK : DEVIATION);
        if (quantity == null || amount == null) result.put("partial", true);
    }

    private static void notCheckable(Map<String, Object> result, String reason) {
        result.put("status", NOT_CHECKABLE);
        result.put("reason", reason);
    }

    /** the status of a stored result JSON, null if none */
    static String status(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.hasNonNull("status") ? node.get("status").asText() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** a date attribute value of the invoice ("2026-09-01T00:00:00.000+02:00"), null if none or not a date */
    static DateTime date(String value, DateTimeZone zone) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new DateTime(value.trim(), zone);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static Double number(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
