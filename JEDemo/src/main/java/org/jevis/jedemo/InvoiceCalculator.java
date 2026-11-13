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

import org.jevis.api.JEVisException;
import org.jevis.jedemo.InvoiceBuilder.Line;
import org.jevis.jedemo.InvoiceBuilder.Medium;
import org.jevis.jedemo.InvoiceBuilder.Spec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makes the invoice of a demo cost case ({@link InvoiceCase}) for one month: the lines priced from the contract objects
 * as the cost calculation templates price them, plus the deviations declared for the month.
 */
public final class InvoiceCalculator {

    static final ZoneId ZONE = ReplayPlanner.ZONE;
    private static final int PRICE_SCALE = 10;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private InvoiceCalculator() {
    }

    /**
     * Prices and values of the contract objects (see {@link ContractValues}).
     */
    public interface Prices {
        /**
         * @return last sample at or before the instant, null if none
         */
        BigDecimal at(Ref object, String attribute, Instant instant) throws JEVisException;

        /**
         * @return last sample in [from, to), null if none
         */
        BigDecimal lastIn(Ref object, String attribute, Instant from, Instant to) throws JEVisException;

        /**
         * @return value of the latest ranging table for the determination value, null if none
         */
        BigDecimal ranging(Ref object, String attribute, double determination) throws JEVisException;

        static Prices of(ContractValues values) {
            return new Prices() {
                @Override
                public BigDecimal at(Ref object, String attribute, Instant instant) throws JEVisException {
                    return values.at(object, attribute, instant);
                }

                @Override
                public BigDecimal lastIn(Ref object, String attribute, Instant from, Instant to) throws JEVisException {
                    return values.lastIn(object, attribute, from, to);
                }

                @Override
                public BigDecimal ranging(Ref object, String attribute, double determination) throws JEVisException {
                    return values.ranging(object, attribute, determination);
                }
            };
        }
    }

    /**
     * The invoice and what was noticed on the way.
     */
    public static final class Result {
        private final Spec spec;
        private final List<String> warnings;

        Result(Spec spec, List<String> warnings) {
            this.spec = spec;
            this.warnings = warnings;
        }

        public Spec getSpec() {
            return spec;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }

    public static Instant start(YearMonth period) {
        return period.atDay(1).atStartOfDay(ZONE).toInstant();
    }

    public static Instant end(YearMonth period) {
        return start(period.plusMonths(1));
    }

    /**
     * @return start of the peak period: the start of the year (year to date) or of the month
     */
    public static Instant peakStart(InvoiceCase invoiceCase, YearMonth period) {
        boolean yearToDate = invoiceCase.peak == null || invoiceCase.peak.yearToDate;
        return yearToDate ? start(YearMonth.of(period.getYear(), 1)) : start(period);
    }

    /**
     * Consumption and peak of a month, or why they cannot be measured.
     */
    public static final class Measurement {
        private final BigDecimal consumption;
        private final BigDecimal peak;
        private final String problem;

        Measurement(BigDecimal consumption, BigDecimal peak, String problem) {
            this.consumption = consumption;
            this.peak = peak;
            this.problem = problem;
        }

        /**
         * @return sum of the consumption row in the month
         */
        public BigDecimal getConsumption() {
            return consumption;
        }

        /**
         * @return maximum of the peak row in the peak period × factor, null without peak
         */
        public BigDecimal getPeak() {
            return peak;
        }

        /**
         * @return null if measured, else the reason (row missing, no data)
         */
        public String getProblem() {
            return problem;
        }
    }

    /**
     * Sum of the consumption row in [start of month, start of next month) and the peak (maximum of the peak row from
     * the start of the year or month to the end of the month, times the factor), as the templates aggregate them.
     */
    public static Measurement measure(InvoiceCase invoiceCase, YearMonth period, ContractValues values) throws JEVisException {
        Instant start = start(period);
        Instant end = end(period);
        ContractValues.Aggregate consumption = values.aggregate(invoiceCase.consumption.row, start, end);
        if (consumption == null) return new Measurement(null, null, "consumption row " + invoiceCase.consumption.row + " not found");
        if (consumption.getCount() == 0) return new Measurement(null, null, "no data in row " + invoiceCase.consumption.row);
        BigDecimal peak = null;
        if (invoiceCase.peak != null) {
            ContractValues.Aggregate p = values.aggregate(invoiceCase.peak.row, peakStart(invoiceCase, period), end);
            if (p == null || p.getMax() == null) return new Measurement(null, null, "no data in peak row " + invoiceCase.peak.row);
            peak = BigDecimal.valueOf(p.getMax()).multiply(BigDecimal.valueOf(invoiceCase.peak.factor));
        }
        return new Measurement(BigDecimal.valueOf(consumption.getSum()), peak, null);
    }

    /**
     * @param consumption sum of the consumption row in the month (kWh, or m³ with a heating value)
     * @param peak        maximum of the peak row in the peak period times its factor, null without peak
     * @param buyer       name of the demo organisation
     * @param issueDate   invoice date
     * @throws IllegalStateException if a price the invoice needs is missing
     */
    public static Result calculate(InvoiceCase invoiceCase, YearMonth period, BigDecimal consumption, BigDecimal peak,
                                   Prices prices, String buyer, LocalDate issueDate) throws JEVisException {
        List<String> warnings = new ArrayList<>();
        List<InvoiceCase.Deviation> deviations = invoiceCase.deviations(period);
        Instant start = start(period);
        Instant lastYear = period.minusYears(1).atDay(1).atStartOfDay(ZONE).toInstant();
        List<String> notes = new ArrayList<>();
        if (invoiceCase.meterPoint != null && !invoiceCase.meterPoint.trim().isEmpty()) {
            String label = invoiceCase.meterPointLabel != null ? invoiceCase.meterPointLabel.trim() + " " : "";
            notes.add(label + invoiceCase.meterPoint.trim());
        }

        BigDecimal kWh = consumption;
        InvoiceCase.Consumption c = invoiceCase.consumption;
        if (c != null && c.heatingValue != null) {
            BigDecimal heatingValue = require(prices.at(c.heatingValue.object, c.heatingValue.attribute, start), "heating value " + c.heatingValue);
            BigDecimal stateNumber = c.stateNumber != null
                    ? require(prices.at(c.stateNumber.object, c.stateNumber.attribute, start), "state number " + c.stateNumber)
                    : BigDecimal.ONE;
            kWh = consumption.multiply(stateNumber).multiply(heatingValue);
            notes.add(String.format(Locale.GERMANY, "Verbrauch %s m³ × Zustandszahl %s × Brennwert %s kWh/m³",
                    plain(consumption.setScale(0, RoundingMode.HALF_UP)), plain(stateNumber), plain(heatingValue)));
        }
        for (InvoiceCase.Deviation deviation : deviations) {
            if (InvoiceCase.QUANTITY_FACTOR.equals(deviation.type) && deviation.factor != null) {
                kWh = kWh.multiply(BigDecimal.valueOf(deviation.factor));
                warnings.add("deviation: consumption × " + deviation.factor);
            }
        }
        kWh = kWh.setScale(0, RoundingMode.HALF_UP);

        BigDecimal vat = invoiceCase.vat != null ? prices.at(invoiceCase.vat.object, invoiceCase.vat.attribute, start) : null;
        if (vat == null) {
            vat = InvoiceBuilder.VAT_PERCENT;
            warnings.add("no VAT rate, 19 % used");
        }

        List<Line> lines = new ArrayList<>();
        for (InvoiceCase.Line definition : invoiceCase.lines) {
            boolean previousYear = has(deviations, InvoiceCase.PREVIOUS_YEAR_PRICE, definition.key);
            YearMonth priced = previousYear ? period.minusYears(1) : period;
            Instant at = previousYear ? lastYear : start;
            if (previousYear) warnings.add("deviation: " + definition.key + " priced as " + priced);
            Line line = line(invoiceCase, definition, period, priced, at, kWh, peak, prices);
            lines.add(line);
            if (has(deviations, InvoiceCase.DUPLICATE_LINE, definition.key)) {
                lines.add(line);
                warnings.add("deviation: " + definition.key + " billed twice");
            }
        }

        Medium medium = Medium.valueOf(invoiceCase.medium.trim().toUpperCase(Locale.ROOT));
        Spec spec = new Spec(medium, InvoiceBuilder.number(period, invoiceCase.suffix), period, invoiceCase.seller,
                invoiceCase.sellerVatId, notes, buyer, issueDate, vat.stripTrailingZeros(), lines, kWh);
        return new Result(spec, warnings);
    }

    private static Line line(InvoiceCase invoiceCase, InvoiceCase.Line definition, YearMonth period, YearMonth priced,
                             Instant at, BigDecimal kWh, BigDecimal peak, Prices prices) throws JEVisException {
        String type = definition.type != null ? definition.type.trim().toUpperCase(Locale.ROOT) : InvoiceCase.ENERGY;
        String text = definition.text != null ? definition.text : definition.key;
        InvoiceCase.Value price = definition.price;
        switch (type) {
            case InvoiceCase.ENERGY: {
                BigDecimal p = perKWh(require(prices.at(price.object, price.attribute, at), definition.key), definition.priceUnit);
                return new Line(text, "KWH", kWh, p);
            }
            case InvoiceCase.INDEXED: {
                BigDecimal base = perKWh(require(prices.at(price.object, price.attribute, at), definition.key), definition.priceUnit);
                BigDecimal factor = BigDecimal.valueOf(definition.index != null ? definition.index.constant : 1d);
                if (definition.index != null && definition.index.terms != null) {
                    for (InvoiceCase.Term term : definition.index.terms) {
                        BigDecimal value = require(prices.at(term.value.object, term.value.attribute, at), definition.key + " index " + term.value);
                        factor = factor.add(BigDecimal.valueOf(term.weight).multiply(value)
                                .divide(BigDecimal.valueOf(term.base), 12, RoundingMode.HALF_UP));
                    }
                }
                return new Line(text, "KWH", kWh, base.multiply(factor).setScale(PRICE_SCALE, RoundingMode.HALF_UP));
            }
            case InvoiceCase.YEARLY: {
                Instant from = YearMonth.of(priced.getYear(), 1).atDay(1).atStartOfDay(ZONE).toInstant();
                Instant to = YearMonth.of(priced.getYear() + 1, 1).atDay(1).atStartOfDay(ZONE).toInstant();
                BigDecimal yearly = require(prices.lastIn(price.object, price.attribute, from, to), definition.key + " in " + priced.getYear());
                int daysOfYear = period.atDay(1).lengthOfYear();
                BigDecimal perDay = yearly.divide(BigDecimal.valueOf(daysOfYear), PRICE_SCALE, RoundingMode.HALF_UP);
                return new Line(text, "DAY", BigDecimal.valueOf(period.lengthOfMonth()), perDay);
            }
            case InvoiceCase.MONTHLY: {
                BigDecimal p = require(prices.at(price.object, price.attribute, at), definition.key);
                return new Line(text, "MON", BigDecimal.ONE, p);
            }
            case InvoiceCase.DEMAND: {
                if (peak == null) throw new IllegalStateException("no peak for " + definition.key);
                BigDecimal yearly = require(prices.ranging(price.object, price.attribute, peak.doubleValue()), definition.key);
                return new Line(text, "KWT", peak.setScale(3, RoundingMode.HALF_UP),
                        yearly.divide(TWELVE, PRICE_SCALE, RoundingMode.HALF_UP));
            }
            case InvoiceCase.CAPACITY: {
                BigDecimal capacity = require(prices.at(definition.capacity.object, definition.capacity.attribute, at), definition.key + " capacity");
                BigDecimal yearly = require(prices.at(price.object, price.attribute, at), definition.key);
                return new Line(text, "KWT", capacity.stripTrailingZeros(), yearly.divide(TWELVE, PRICE_SCALE, RoundingMode.HALF_UP));
            }
            default:
                throw new IllegalStateException("unknown line type " + type + " in case " + invoiceCase.key);
        }
    }

    private static boolean has(List<InvoiceCase.Deviation> deviations, String type, String line) {
        for (InvoiceCase.Deviation deviation : deviations) {
            if (type.equals(deviation.type) && line != null && line.equals(deviation.line)) return true;
        }
        return false;
    }

    /**
     * @return the price in EUR/kWh: ct/kWh ÷ 100, EUR/MWh ÷ 1000, else as it is
     */
    static BigDecimal perKWh(BigDecimal price, String unit) {
        String u = unit != null ? unit.replace(" ", "").toLowerCase(Locale.ROOT) : "ct/kwh";
        if (u.startsWith("ct")) return price.movePointLeft(2);
        if (u.contains("mwh")) return price.movePointLeft(3);
        return price;
    }

    private static BigDecimal require(BigDecimal value, String what) {
        if (value == null) throw new IllegalStateException("no value for " + what);
        return value;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /**
     * @return the invoice date of a month: the 5th of the next month, or today if that is later than today
     */
    public static LocalDate issueDate(YearMonth period, LocalDate today) {
        LocalDate date = period.plusMonths(1).atDay(5);
        return date.isAfter(today) ? today : date;
    }
}
