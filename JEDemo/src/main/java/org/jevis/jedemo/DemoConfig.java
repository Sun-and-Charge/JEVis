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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Settings of the JEDemo service object (class {@value #CLASS_NAME}).
 */
public final class DemoConfig {

    public static final String CLASS_NAME = "JEDemo";

    public static final String A_ORGANIZATION = "Organization";
    public static final String A_EXCLUDED = "Excluded Objects";
    public static final String A_SHIFTED_UNTIL = "Shifted Until";
    public static final String A_INVOICE_DIRECTORY = "Invoice Directory";
    public static final String A_ELECTRICITY_ROW = "Electricity Row";
    public static final String A_GAS_ROW = "Gas Row";
    public static final String A_ELECTRICITY_PRICE = "Electricity Price";
    public static final String A_GAS_PRICE = "Gas Price";
    public static final String A_ELECTRICITY_FEE = "Electricity Base Price";
    public static final String A_GAS_FEE = "Gas Base Price";
    public static final String A_REPLAY_STATE = "Replay State";
    public static final String A_INVOICE_CASES = "Invoice Cases";

    /**
     * Marker every configured organisation name must contain (case-insensitive).
     */
    public static final String DEMO_MARKER = "demo";

    static final BigDecimal DEFAULT_ELECTRICITY_PRICE = new BigDecimal("0.28");
    static final BigDecimal DEFAULT_GAS_PRICE = new BigDecimal("0.09");
    static final BigDecimal DEFAULT_ELECTRICITY_FEE = new BigDecimal("12.50");
    static final BigDecimal DEFAULT_GAS_FEE = new BigDecimal("15.00");

    private static final Logger logger = LogManager.getLogger(DemoConfig.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper CASE_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    List<Long> organisations = new ArrayList<>();
    List<Long> excluded = new ArrayList<>();
    LocalDate shiftedUntil;
    Long invoiceDirectory;
    Long electricityRow;
    Long gasRow;
    BigDecimal electricityPrice = DEFAULT_ELECTRICITY_PRICE;
    BigDecimal gasPrice = DEFAULT_GAS_PRICE;
    BigDecimal electricityFee = DEFAULT_ELECTRICITY_FEE;
    BigDecimal gasFee = DEFAULT_GAS_FEE;
    /**
     * stored offsets of rows with a short history, row id → "P13W" / "P5M"
     */
    Map<Long, String> replayState = new TreeMap<>();
    /**
     * the invoice cases of "Invoice Cases"; if empty, the old electricity/gas attributes are used
     */
    List<InvoiceCase> invoiceCases = new ArrayList<>();

    static DemoConfig read(JEVisObject service) throws JEVisException {
        DemoConfig config = new DemoConfig();
        config.organisations = parseIds(latest(service, A_ORGANIZATION));
        config.excluded = parseIds(latest(service, A_EXCLUDED));
        config.shiftedUntil = parseDate(latest(service, A_SHIFTED_UNTIL));
        config.invoiceDirectory = first(parseIds(latest(service, A_INVOICE_DIRECTORY)));
        config.electricityRow = first(parseIds(latest(service, A_ELECTRICITY_ROW)));
        config.gasRow = first(parseIds(latest(service, A_GAS_ROW)));
        config.electricityPrice = decimal(latest(service, A_ELECTRICITY_PRICE), DEFAULT_ELECTRICITY_PRICE);
        config.gasPrice = decimal(latest(service, A_GAS_PRICE), DEFAULT_GAS_PRICE);
        config.electricityFee = decimal(latest(service, A_ELECTRICITY_FEE), DEFAULT_ELECTRICITY_FEE);
        config.gasFee = decimal(latest(service, A_GAS_FEE), DEFAULT_GAS_FEE);
        config.replayState = parseState(latest(service, A_REPLAY_STATE));
        config.invoiceCases = parseInvoiceCases(latest(service, A_INVOICE_CASES));
        return config;
    }

    private static String latest(JEVisObject object, String attribute) throws JEVisException {
        JEVisAttribute att = object.getAttribute(attribute);
        if (att == null) return null;
        JEVisSample sample = att.getLatestSample();
        return sample != null ? sample.getValueAsString() : null;
    }

    private static Long first(List<Long> ids) {
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * Object ids of a target attribute value: ";" separated targets, each "id" or "id:Attribute".
     */
    public static List<Long> parseIds(String value) {
        List<Long> ids = new ArrayList<>();
        if (value == null) return ids;
        for (String part : value.split(";")) {
            String text = part.trim();
            int colon = text.indexOf(':');
            if (colon >= 0) text = text.substring(0, colon).trim();
            if (text.isEmpty()) continue;
            try {
                Long id = Long.parseLong(text);
                if (!ids.contains(id)) ids.add(id);
            } catch (NumberFormatException e) {
                logger.debug("Not a target: '{}'", part);
            }
        }
        return ids;
    }

    /**
     * "Shifted Until" as written by the Control Center date editor (ISO date-time with offset) or as a plain ISO
     * date; the calendar day in Europe/Berlin.
     */
    public static LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String text = value.trim();
        try {
            return OffsetDateTime.parse(text).atZoneSameInstant(ReplayPlanner.ZONE).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text.length() >= 10 ? text.substring(0, 10) : text);
            } catch (DateTimeParseException e2) {
                logger.warn("Invalid date '{}'", value);
                return null;
            }
        }
    }

    /**
     * "Shifted Until" in the format of the Control Center date editor: midnight in Europe/Berlin, ISO with millis.
     */
    public static String formatDate(LocalDate date) {
        return date.atStartOfDay(ReplayPlanner.ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"));
    }

    private static BigDecimal decimal(String value, BigDecimal fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            BigDecimal d = new BigDecimal(value.trim().replace(',', '.'));
            return d.signum() >= 0 ? d.stripTrailingZeros() : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * "Invoice Cases": a JSON array of {@link InvoiceCase} (or an object with the array in "cases"). Unreadable entries
     * are skipped with an error in the log.
     */
    static List<InvoiceCase> parseInvoiceCases(String json) {
        List<InvoiceCase> cases = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return cases;
        try {
            JsonNode node = CASE_MAPPER.readTree(json);
            if (node != null && node.isObject() && node.has("cases")) node = node.get("cases");
            if (node == null || !node.isArray()) {
                logger.error("'{}' is no JSON array, no invoice cases", A_INVOICE_CASES);
                return cases;
            }
            for (JsonNode entry : node) {
                try {
                    InvoiceCase invoiceCase = CASE_MAPPER.treeToValue(entry, InvoiceCase.class);
                    if (invoiceCase != null) cases.add(invoiceCase);
                } catch (Exception e) {
                    logger.error("Invalid invoice case in '{}': {}", A_INVOICE_CASES, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("Could not read '{}': {}", A_INVOICE_CASES, e.getMessage());
        }
        return cases;
    }

    static Map<Long, String> parseState(String json) {
        Map<Long, String> state = new TreeMap<>();
        if (json == null || json.trim().isEmpty()) return state;
        try {
            Map<String, String> raw = MAPPER.readValue(json, new TypeReference<Map<String, String>>() {
            });
            raw.forEach((k, v) -> {
                try {
                    if (ReplayPlanner.Offset.parse(v) != null) state.put(Long.parseLong(k.trim()), v.trim().toUpperCase(Locale.ROOT));
                } catch (NumberFormatException ignored) {
                    logger.debug("Invalid replay state key {}", k);
                }
            });
        } catch (Exception e) {
            logger.warn("Could not read '{}', starting with an empty state: {}", A_REPLAY_STATE, e.getMessage());
        }
        return state;
    }

    static String formatState(Map<Long, String> state) {
        Map<String, String> raw = new TreeMap<>();
        state.forEach((k, v) -> raw.put(String.valueOf(k), v));
        try {
            return MAPPER.writeValueAsString(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * @return true if the organisation name carries the demo marker
     */
    public static boolean isDemoName(String name) {
        return name != null && name.toLowerCase(Locale.ROOT).contains(DEMO_MARKER);
    }
}
