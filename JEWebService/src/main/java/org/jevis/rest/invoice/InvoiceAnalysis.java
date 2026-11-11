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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Energy specific analysis of an {@link EInvoice}: billing period, consumption, meter point, medium guess and the
 * JSON answer of {@code POST /invoices/parse} (see docs/ENERGY-INVOICES.md). Pure functions.
 */
public final class InvoiceAnalysis {

    public static final String UNIT_KWH = "kWh";
    public static final String UNIT_M3 = "m³";

    /** Zählpunkt / Messlokation: "DE" + 31 letters or digits */
    private static final Pattern METER_POINT = Pattern.compile("(?<![A-Za-z0-9])((?i:DE)[0-9A-Za-z]{31})(?![A-Za-z0-9])");
    /** Marktlokation: 11 digits after the keyword */
    private static final Pattern MARKET_LOCATION = Pattern.compile(
            "(?iu)(marktlokation|markt-lokation|malo)(-?id)?[^0-9\\n]{0,40}?(?<!\\d)(\\d{11})(?!\\d)");
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("\\d{11}");

    private static final Map<Medium, Pattern> KEYWORDS = new LinkedHashMap<>();
    private static final Map<String, String> UNIT_NAMES = new HashMap<>();

    static {
        KEYWORDS.put(Medium.ELECTRICITY, Pattern.compile("(?iu)strom|elektri|electri|wirkarbeit|blindarbeit"));
        /* not "Gasse" (street) or "Gast..."; not "Wärmepumpe" (heat pump electricity) */
        KEYWORDS.put(Medium.GAS, Pattern.compile("(?iu)\\b(erd|bio)?gas(?!se|t)|brennwert|zustandszahl"));
        KEYWORDS.put(Medium.DISTRICT_HEATING, Pattern.compile("(?iu)fernw(ä|ae)rme|w(ä|ae)rme(?!pumpe)|district heating|\\bheat"));

        String[][] units = {
                {"KWH", UNIT_KWH}, {"MWH", "MWh"}, {"MTQ", UNIT_M3}, {"MON", "month"}, {"DAY", "day"}, {"ANN", "year"},
                {"C62", "piece"}, {"H87", "piece"}, {"LS", "lump sum"}, {"KWT", "kW"}, {"WEE", "week"}, {"HUR", "h"},
                {"LTR", "l"}, {"KGM", "kg"}, {"TNE", "t"}, {"P1", "%"}};
        for (String[] unit : units) UNIT_NAMES.put(unit[0], unit[1]);
    }

    private InvoiceAnalysis() {
    }

    /**
     * Consumption of an invoice with unit kWh or m³.
     */
    public static final class Consumption {
        public final BigDecimal value;
        public final String unit;

        public Consumption(BigDecimal value, String unit) {
            this.value = value;
            this.unit = unit;
        }
    }

    /**
     * Result of the analysis.
     */
    public static final class Analysis {
        public LocalDate periodStart;
        public LocalDate periodEnd;
        /** null if no line has an energy unit */
        public Consumption consumption;
        public String meterPoint;
        public Medium medium;
        public final List<String> mediumReasons = new ArrayList<>();
        /** warnings of the reader and of the analysis */
        public final List<String> warnings = new ArrayList<>();
    }

    /**
     * Analyses the invoice.
     */
    public static Analysis analyse(EInvoice invoice) {
        Analysis analysis = new Analysis();
        analysis.warnings.addAll(invoice.warnings);
        if (invoice.number == null) analysis.warnings.add("The invoice has no invoice number.");
        if (invoice.net == null || invoice.gross == null) analysis.warnings.add("The invoice totals are incomplete.");

        LocalDate[] period = period(invoice);
        analysis.periodStart = period[0];
        analysis.periodEnd = period[1];
        if (analysis.periodStart == null) {
            analysis.warnings.add("The invoice has no billing period, the invoice date is used as time stamp.");
        }

        analysis.consumption = consumption(invoice.lines, analysis.warnings);
        if (invoice.creditNote) {
            /* a credit note reduces the sums: totals and consumption are negative (whether the document has them
               positive, as EN 16931 wants, or already negative); the line items stay as in the document */
            invoice.net = negative(invoice.net);
            invoice.tax = negative(invoice.tax);
            invoice.gross = negative(invoice.gross);
            if (analysis.consumption != null) {
                analysis.consumption = new Consumption(negative(analysis.consumption.value), analysis.consumption.unit);
            }
            analysis.warnings.add("The document is a credit note, its amounts and the consumption are imported as negative values.");
        }
        analysis.meterPoint = meterPoint(invoice, analysis.warnings);
        guessMedium(invoice, analysis);
        return analysis;
    }

    static BigDecimal negative(BigDecimal value) {
        return value == null ? null : value.abs().negate();
    }

    /**
     * @return billing period of the header, else the earliest start and the latest end of the lines; {null, null}
     * if there is none
     */
    static LocalDate[] period(EInvoice invoice) {
        if (invoice.periodStart != null) return new LocalDate[]{invoice.periodStart, invoice.periodEnd};
        LocalDate start = null;
        LocalDate end = null;
        for (EInvoice.Line line : invoice.lines) {
            if (line.periodStart != null && (start == null || line.periodStart.isBefore(start))) start = line.periodStart;
            if (line.periodEnd != null && (end == null || line.periodEnd.isAfter(end))) end = line.periodEnd;
        }
        return new LocalDate[]{start, start != null ? end : null};
    }

    /**
     * @return the date of the samples: period start, else invoice date, else today
     */
    public static LocalDate timestampDate(EInvoice invoice, Analysis analysis) {
        if (analysis.periodStart != null) return analysis.periodStart;
        if (invoice.date != null) return invoice.date;
        return LocalDate.now();
    }

    /**
     * Sum of the line quantities with energy units: KWH, MWH (×1000 kWh) or MTQ (m³); kWh is preferred if there are
     * both. Lines with other units (MON, DAY, C62, ... = fixed fees) are not counted.
     * <p>
     * Energy invoices often name the same consumption several times: energy price (maybe split into HT and NT),
     * grid fee, electricity tax, levies. The lines of one period are therefore split into the largest number of
     * groups with the same sum (e.g. HT 3000 + NT 1321 | grid fee 4321 | tax 4321) and one group is counted.
     *
     * @param warnings gets a warning if the consumption was named several times
     * @return the consumption, null if there is no line with an energy unit
     */
    static Consumption consumption(List<EInvoice.Line> lines, List<String> warnings) {
        List<EInvoice.Line> kwh = new ArrayList<>();
        List<EInvoice.Line> m3 = new ArrayList<>();
        for (EInvoice.Line line : lines) {
            if (line.quantity == null || line.unitCode == null) continue;
            String code = line.unitCode.toUpperCase(Locale.ROOT);
            if (code.equals("KWH") || code.equals("MWH")) kwh.add(line);
            else if (code.equals("MTQ")) m3.add(line);
        }
        List<EInvoice.Line> chosen = !kwh.isEmpty() ? kwh : m3;
        if (chosen.isEmpty()) return null;
        String unit = !kwh.isEmpty() ? UNIT_KWH : UNIT_M3;

        /* quantities per line period */
        Map<String, List<BigDecimal>> periods = new LinkedHashMap<>();
        for (EInvoice.Line line : chosen) {
            BigDecimal quantity = line.quantity;
            if ("MWH".equalsIgnoreCase(line.unitCode)) quantity = quantity.multiply(BigDecimal.valueOf(1000));
            periods.computeIfAbsent(line.periodStart + "|" + line.periodEnd, k -> new ArrayList<>()).add(quantity);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int maxGroups = 1;
        for (List<BigDecimal> quantities : periods.values()) {
            int groups = groups(quantities);
            maxGroups = Math.max(maxGroups, groups);
            BigDecimal total = BigDecimal.ZERO;
            for (BigDecimal quantity : quantities) total = total.add(quantity);
            sum = sum.add(groups > 1 ? total.divide(BigDecimal.valueOf(groups), 6, java.math.RoundingMode.HALF_UP).stripTrailingZeros() : total);
        }
        if (maxGroups > 1 && warnings != null) {
            warnings.add("The energy lines name the same consumption " + maxGroups + " times (e.g. energy price, grid fee,"
                    + " taxes), it was counted once.");
        }
        return new Consumption(sum, unit);
    }

    /**
     * @return the largest number of groups with the same sum the positive quantities can be split into (1 if there
     * are negative quantities or too many lines)
     */
    static int groups(List<BigDecimal> quantities) {
        int n = quantities.size();
        if (n < 2 || n > 24) return 1;
        long[] values = new long[n];
        long total = 0;
        long max = 0;
        for (int i = 0; i < n; i++) {
            BigDecimal scaled = quantities.get(i).movePointRight(3);
            if (quantities.get(i).signum() <= 0 || scaled.stripTrailingZeros().scale() > 0) return 1;
            values[i] = scaled.longValueExact();
            total += values[i];
            max = Math.max(max, values[i]);
        }
        Arrays.sort(values);
        for (int k = n; k >= 2; k--) {
            if (total % k != 0) continue;
            long target = total / k;
            if (target < max) continue;
            if (fill(values, n - 1, new long[k], target)) return k;
        }
        return 1;
    }

    /** backtracking: puts values[index..0] (sorted ascending, largest first) into bins of the target sum */
    private static boolean fill(long[] values, int index, long[] bins, long target) {
        if (index < 0) return true;
        long value = values[index];
        for (int b = 0; b < bins.length; b++) {
            if (b > 0 && bins[b] == bins[b - 1]) continue;
            if (bins[b] + value > target) continue;
            bins[b] += value;
            if (fill(values, index - 1, bins, target)) return true;
            bins[b] -= value;
            if (bins[b] == 0) break;
        }
        return false;
    }

    /**
     * Meter point (Zählpunkt / Messlokation "DE" + 31 characters, or Marktlokation with 11 digits) from the delivery
     * location, the notes, the line texts and, for a Zählpunkt, the rest of the XML. Several are joined with ", ".
     *
     * @param warnings gets a warning if there are several
     * @return the meter point(s) or null
     */
    static String meterPoint(EInvoice invoice, List<String> warnings) {
        List<String> texts = new ArrayList<>();
        texts.addAll(invoice.deliveryTexts);
        texts.addAll(invoice.notes);
        for (EInvoice.Line line : invoice.lines) {
            if (line.name != null) texts.add(line.name);
            if (line.description != null) texts.add(line.description);
            texts.addAll(line.texts);
        }

        Set<String> found = new LinkedHashSet<>();
        for (String text : texts) {
            Matcher matcher = METER_POINT.matcher(text);
            while (matcher.find()) found.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        if (found.isEmpty() && invoice.xml != null) {
            Matcher matcher = METER_POINT.matcher(invoice.xml);
            while (matcher.find()) found.add(matcher.group(1).toUpperCase(Locale.ROOT));
        }
        for (String text : texts) {
            Matcher matcher = MARKET_LOCATION.matcher(text);
            while (matcher.find()) found.add(matcher.group(3));
        }
        for (String text : invoice.deliveryTexts) {
            if (ELEVEN_DIGITS.matcher(text).matches()) found.add(text);
        }
        if (found.isEmpty()) return null;
        if (found.size() > 1 && warnings != null) {
            warnings.add("The invoice names several meter points: " + String.join(", ", found) + ".");
        }
        return String.join(", ", found);
    }

    /**
     * Guesses the medium from the units of the lines (m³ → gas, kWh → electricity, weak), the texts of the lines and
     * notes and the seller name. Unknown → {@link Medium#OTHER} with a warning.
     */
    static void guessMedium(EInvoice invoice, Analysis analysis) {
        Map<Medium, Integer> scores = new EnumMap<>(Medium.class);
        Map<Medium, List<String>> reasons = new EnumMap<>(Medium.class);
        for (Medium medium : Medium.values()) {
            scores.put(medium, 0);
            reasons.put(medium, new ArrayList<>());
        }

        boolean kwh = false;
        boolean m3 = false;
        for (EInvoice.Line line : invoice.lines) {
            if (line.unitCode == null) continue;
            String code = line.unitCode.toUpperCase(Locale.ROOT);
            if (code.equals("KWH") || code.equals("MWH")) kwh = true;
            if (code.equals("MTQ")) m3 = true;
        }
        if (m3) score(scores, reasons, Medium.GAS, 3, "unit m³");
        if (kwh) score(scores, reasons, Medium.ELECTRICITY, 1, "unit kWh");

        for (EInvoice.Line line : invoice.lines) {
            keywords(line.name, "line text", scores, reasons);
            keywords(line.description, "line text", scores, reasons);
            for (String text : line.texts) keywords(text, "line text", scores, reasons);
        }
        for (String note : invoice.notes) keywords(note, "note", scores, reasons);
        keywords(invoice.seller, "seller", scores, reasons);

        Medium best = Medium.OTHER;
        int bestScore = 0;
        boolean tie = false;
        for (Medium medium : new Medium[]{Medium.ELECTRICITY, Medium.GAS, Medium.DISTRICT_HEATING}) {
            int score = scores.get(medium);
            if (score > bestScore) {
                best = medium;
                bestScore = score;
                tie = false;
            } else if (score == bestScore && score > 0) {
                tie = true;
            }
        }
        analysis.medium = best;
        analysis.mediumReasons.addAll(reasons.get(best));
        if (best == Medium.OTHER) {
            analysis.warnings.add("The medium could not be detected, please choose it.");
        } else if (tie) {
            analysis.warnings.add("The medium is uncertain, please check it.");
        }
    }

    private static void keywords(String text, String source, Map<Medium, Integer> scores, Map<Medium, List<String>> reasons) {
        if (text == null || text.isEmpty()) return;
        for (Map.Entry<Medium, Pattern> entry : KEYWORDS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(text);
            if (matcher.find()) {
                score(scores, reasons, entry.getKey(), 2, source + " '" + matcher.group() + "'");
            }
        }
    }

    private static void score(Map<Medium, Integer> scores, Map<Medium, List<String>> reasons, Medium medium, int points,
                              String reason) {
        List<String> list = reasons.get(medium);
        /* the same reason (e.g. the same word in many lines) counts once */
        if (list.contains(reason)) return;
        list.add(reason);
        scores.put(medium, scores.get(medium) + points);
    }

    /**
     * @return readable unit of a UN/ECE unit code (KWH → kWh, MTQ → m³, MON → month, ...), else the code
     */
    public static String unitName(String code) {
        if (code == null) return null;
        String name = UNIT_NAMES.get(code.toUpperCase(Locale.ROOT));
        return name != null ? name : code;
    }

    // ---------------------------------------------------------------- duplicates

    /**
     * @return true if an existing invoice (its "Seller VAT ID", "Seller" and "Invoice Number") is the same invoice:
     * same number and same seller, by VAT ID if both have one, else by name (both case-insensitive, without spaces)
     */
    public static boolean isSameInvoice(EInvoice invoice, String existingVatId, String existingSeller, String existingNumber) {
        if (invoice.number == null || existingNumber == null) return false;
        if (!normalise(invoice.number).equals(normalise(existingNumber))) return false;
        String vat = normalise(invoice.sellerVatId);
        String otherVat = normalise(existingVatId);
        if (!vat.isEmpty() && !otherVat.isEmpty()) return vat.equals(otherVat);
        return normalise(invoice.seller).equals(normalise(existingSeller));
    }

    private static String normalise(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- JSON

    /**
     * @return the line items as list of maps (description, quantity, unit, unitPrice, net, taxRate, periodStart,
     * periodEnd)
     */
    public static List<Map<String, Object>> itemsJson(EInvoice invoice) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (EInvoice.Line line : invoice.lines) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("description", line.label());
            item.put("quantity", number(line.quantity));
            item.put("unit", unitName(line.unitCode));
            item.put("unitPrice", number(line.unitPrice));
            item.put("net", number(line.net));
            item.put("taxRate", number(line.taxRate));
            item.put("periodStart", date(line.periodStart));
            item.put("periodEnd", date(line.periodEnd));
            items.add(item);
        }
        return items;
    }

    /**
     * @param duplicate id of an existing invoice with the same seller and number, or null
     * @return the answer of {@code POST /invoices/parse}
     */
    public static Map<String, Object> toJson(EInvoice invoice, Analysis analysis, String fileName, Long duplicate) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("fileName", fileName);
        json.put("format", invoice.format);
        json.put("number", invoice.number);
        json.put("date", date(invoice.date));
        json.put("seller", invoice.seller);
        json.put("sellerVatId", invoice.sellerVatId);
        json.put("buyer", invoice.buyer);
        json.put("periodStart", date(analysis.periodStart));
        json.put("periodEnd", date(analysis.periodEnd));
        json.put("currency", invoice.currency);
        json.put("net", number(invoice.net));
        json.put("tax", number(invoice.tax));
        json.put("gross", number(invoice.gross));
        if (analysis.consumption != null) {
            Map<String, Object> consumption = new LinkedHashMap<>();
            consumption.put("value", number(analysis.consumption.value));
            consumption.put("unit", analysis.consumption.unit);
            json.put("consumption", consumption);
        } else {
            json.put("consumption", null);
        }
        json.put("meterPoint", analysis.meterPoint);
        json.put("medium", analysis.medium.name());
        json.put("mediumReasons", new ArrayList<>(analysis.mediumReasons));
        json.put("items", itemsJson(invoice));
        json.put("duplicate", duplicate);
        json.put("warnings", new ArrayList<>(analysis.warnings));
        return json;
    }

    static Double number(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    static String date(LocalDate date) {
        return date != null ? date.toString() : null;
    }
}
