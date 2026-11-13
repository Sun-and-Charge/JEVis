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

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deterministic synthetic histories of the demo cost cases (same input, same values; no randomness between runs).
 * <p>
 * Profiles of the new rows of the setup plan (step 3 writes them from {@link #FROM} up to the setup time; afterwards
 * the JEDemo job continues them by replaying the previous year):
 * <ul>
 * <li>{@value #ELECTRICITY_SLP_COUNTER}: monthly meter reading (kWh) at the 1st 00:00, 2.5–3 MWh per month, a bit
 * more in winter;</li>
 * <li>{@value #GAS_SLP_COUNTER}: monthly meter reading (m³) at the 1st 00:00, about 900 m³ in January and 80 m³ in
 * July;</li>
 * <li>{@value #HEAT_COUNTER}: hourly heat meter reading (kWh), about 180 MWh per year (January ~32 MWh, July ~3 MWh),
 * spread over the days by heating degree days of a synthetic temperature and over the hours by a heating
 * profile;</li>
 * <li>{@value #GAS_PRICE_INDEX}, {@value #HEAT_PRICE_INDEX}: monthly price indices (2021 = 100) at the 1st 00:00 for
 * the price escalation clause of the district heating contract.</li>
 * </ul>
 * Stand-ins for existing live rows, only for the tests and the table in docs/DEMO-COSTS.md:
 * {@link #electricityRlm} (15-minute kWh, ~37 MWh and a peak of ~115–125 kW per month), {@link #gasRlm} (15-minute
 * kWh), {@link #chargePoint} (daily kWh per charge point).
 */
public final class History {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    /**
     * start of the synthetic histories
     */
    public static final LocalDate FROM = LocalDate.of(2025, 1, 1);

    public static final String ELECTRICITY_SLP_COUNTER = "ELECTRICITY_SLP_COUNTER";
    public static final String GAS_SLP_COUNTER = "GAS_SLP_COUNTER";
    public static final String HEAT_COUNTER = "HEAT_COUNTER";
    public static final String GAS_PRICE_INDEX = "GAS_PRICE_INDEX";
    public static final String HEAT_PRICE_INDEX = "HEAT_PRICE_INDEX";

    static final double ELECTRICITY_SLP_START = 15000;
    static final double GAS_SLP_START = 25000;
    static final double HEAT_START = 480000;

    /**
     * monthly mean temperature in °C, January … December
     */
    private static final double[] TEMPERATURE = {1.0, 1.8, 5.0, 9.0, 13.5, 16.5, 18.5, 18.0, 14.0, 9.5, 5.0, 2.0};
    /**
     * district heating, MWh per month (sum 180)
     */
    private static final double[] HEAT_MWH = {32, 27, 22, 14, 7, 4, 3, 3, 6, 13, 21, 28};
    /**
     * gas (small consumer), m³ per month
     */
    private static final double[] GAS_SLP_M3 = {900, 780, 650, 420, 220, 100, 80, 80, 160, 400, 650, 850};
    /**
     * share of the heating load of the gas RLM stand-in
     */
    private static final double[] GAS_RLM_SHARE = {1.0, 0.9, 0.7, 0.45, 0.2, 0.07, 0.05, 0.05, 0.15, 0.4, 0.7, 0.9};

    private History() {
    }

    /**
     * A sample of a history.
     */
    public static final class Point {
        private final Instant ts;
        private final double value;

        Point(Instant ts, double value) {
            this.ts = ts;
            this.value = value;
        }

        public Instant getTs() {
            return ts;
        }

        public double getValue() {
            return value;
        }

        @Override
        public String toString() {
            return ts + "=" + value;
        }
    }

    /**
     * The samples of a plan profile in [from, until].
     *
     * @throws IllegalArgumentException for an unknown profile
     */
    public static List<Point> generate(String profile, Instant from, Instant until) {
        List<Point> all;
        switch (profile) {
            case ELECTRICITY_SLP_COUNTER:
                all = monthlyCounter(ELECTRICITY_SLP_START, History::electricitySlpKWh, until);
                break;
            case GAS_SLP_COUNTER:
                all = monthlyCounter(GAS_SLP_START, History::gasSlpM3, until);
                break;
            case HEAT_COUNTER:
                all = heatCounter(until);
                break;
            case GAS_PRICE_INDEX:
                all = monthly(History::gasIndex, until);
                break;
            case HEAT_PRICE_INDEX:
                all = monthly(History::heatIndex, until);
                break;
            default:
                throw new IllegalArgumentException("Unknown history profile " + profile);
        }
        List<Point> result = new ArrayList<>();
        for (Point p : all) {
            if (!p.ts.isBefore(from) && !p.ts.isAfter(until)) result.add(p);
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------ noise

    /**
     * @return a deterministic value in [-1, 1) for the arguments (SplitMix64)
     */
    static double noise(long seed, long a, long b) {
        long z = seed * 0x9E3779B97F4A7C15L + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL + 0x632BE59BD9B4E019L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return ((z >>> 11) * 0x1.0p-53) * 2 - 1;
    }

    private static long index(YearMonth month) {
        return month.getYear() * 12L + month.getMonthValue();
    }

    private static double yearFactor(long seed, int year) {
        if (year == 2025) return 1.0;
        if (year == 2026) return 0.97;
        return 1 + 0.02 * noise(seed, year, 0);
    }

    private static double round(double value, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(value * f) / f;
    }

    // ------------------------------------------------------------------------------------------ monthly values

    /**
     * @return electricity of the small consumer (case B) in the month, kWh
     */
    public static double electricitySlpKWh(YearMonth month) {
        double base = 2750 + 250 * Math.cos(2 * Math.PI * (month.getMonthValue() - 1) / 12);
        return base * yearFactor(11, month.getYear()) * (1 + 0.03 * noise(12, index(month), 0));
    }

    /**
     * @return gas of the small consumer (case D) in the month, m³
     */
    public static double gasSlpM3(YearMonth month) {
        return GAS_SLP_M3[month.getMonthValue() - 1] * yearFactor(21, month.getYear()) * (1 + 0.05 * noise(22, index(month), 0));
    }

    /**
     * @return district heating (case E) in the month, kWh
     */
    public static double heatKWh(YearMonth month) {
        return 1000 * HEAT_MWH[month.getMonthValue() - 1] * yearFactor(31, month.getYear()) * (1 + 0.04 * noise(32, index(month), 0));
    }

    /**
     * @return gas price index (2021 = 100) published for the month, one decimal
     */
    public static double gasIndex(YearMonth month) {
        long k = index(month) - index(YearMonth.of(2024, 1));
        return round(136 + 10 * Math.pow(0.95, k) + 2.5 * Math.sin(2 * Math.PI * (k + 1) / 12), 1);
    }

    /**
     * @return heat price index (2021 = 100) published for the month, one decimal
     */
    public static double heatIndex(YearMonth month) {
        long k = index(month) - index(YearMonth.of(2024, 1));
        return round(127 + 0.12 * k + 1.0 * Math.sin(2 * Math.PI * k / 12 + 1), 1);
    }

    private interface MonthlyValue {
        double of(YearMonth month);
    }

    private static Instant start(YearMonth month) {
        return month.atDay(1).atStartOfDay(ZONE).toInstant();
    }

    /**
     * monthly samples at the 1st 00:00 from 2024-01 up to until
     */
    private static List<Point> monthly(MonthlyValue value, Instant until) {
        List<Point> points = new ArrayList<>();
        for (YearMonth m = YearMonth.of(2024, 1); !start(m).isAfter(until); m = m.plusMonths(1)) {
            points.add(new Point(start(m), value.of(m)));
        }
        return points;
    }

    /**
     * meter readings (whole units) at the 1st 00:00 from {@link #FROM}: the reading at the start of a month holds the
     * consumption of all months before
     */
    private static List<Point> monthlyCounter(double start, MonthlyValue consumption, Instant until) {
        List<Point> points = new ArrayList<>();
        double reading = start;
        for (YearMonth m = YearMonth.from(FROM); !start(m).isAfter(until); m = m.plusMonths(1)) {
            points.add(new Point(start(m), Math.round(reading)));
            reading += consumption.of(m);
        }
        return points;
    }

    /**
     * @return the monthly consumption as the values of a clean data row at the start of each month in [from, until)
     */
    public static List<Point> monthlyConsumption(String profile, YearMonth from, YearMonth until) {
        MonthlyValue value = ELECTRICITY_SLP_COUNTER.equals(profile) ? History::electricitySlpKWh : History::gasSlpM3;
        List<Point> points = new ArrayList<>();
        for (YearMonth m = from; m.isBefore(until); m = m.plusMonths(1)) points.add(new Point(start(m), value.of(m)));
        return points;
    }

    // ------------------------------------------------------------------------------------------ district heating

    /**
     * @return hourly heat consumption (kWh) of the month at the start of each hour; the sum is {@link #heatKWh}
     */
    public static List<Point> heatHourly(YearMonth month) {
        Instant from = start(month);
        Instant to = start(month.plusMonths(1));
        Map<Instant, Double> weights = new TreeMap<>();
        double total = 0;
        for (Instant t = from; t.isBefore(to); t = t.plus(1, ChronoUnit.HOURS)) {
            ZonedDateTime local = t.atZone(ZONE);
            LocalDate day = local.toLocalDate();
            double temperature = TEMPERATURE[month.getMonthValue() - 1] + 4 * noise(41, day.toEpochDay(), 0);
            double dayWeight = 2 + Math.max(0, 15 - temperature);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) dayWeight *= 0.85;
            int h = local.getHour();
            double hourWeight = h < 5 ? 0.7 : h < 9 ? 1.4 : h < 17 ? 1.0 : h < 22 ? 1.1 : 0.8;
            double w = dayWeight * hourWeight * (1 + 0.05 * noise(42, t.getEpochSecond(), 0));
            weights.put(t, w);
            total += w;
        }
        double kWh = heatKWh(month);
        List<Point> points = new ArrayList<>();
        for (Map.Entry<Instant, Double> e : weights.entrySet()) points.add(new Point(e.getKey(), kWh * e.getValue() / total));
        return points;
    }

    /**
     * hourly heat meter readings (whole kWh) from {@link #FROM}: the reading at an hour holds the consumption of all
     * hours before
     */
    private static List<Point> heatCounter(Instant until) {
        List<Point> points = new ArrayList<>();
        double reading = HEAT_START;
        for (YearMonth m = YearMonth.from(FROM); !start(m).isAfter(until); m = m.plusMonths(1)) {
            for (Point hour : heatHourly(m)) {
                if (hour.ts.isAfter(until)) return points;
                points.add(new Point(hour.ts, Math.round(reading)));
                reading += hour.value;
            }
        }
        return points;
    }

    // ------------------------------------------------------------------------------------------ stand-ins

    /**
     * Stand-in for the grid purchase of the main connection (case A): 15-minute kWh at the start of each quarter hour
     * in [from, until). Base load ~22 kW, working days 6–22 h ~62 kW more, one short peak per month (second Tuesday
     * 10:00–10:45) of ~30 kW more.
     */
    public static List<Point> electricityRlm(Instant from, Instant until) {
        List<Point> points = new ArrayList<>();
        for (Instant t = from; t.isBefore(until); t = t.plus(15, ChronoUnit.MINUTES)) {
            ZonedDateTime local = t.atZone(ZONE);
            long q = t.getEpochSecond() / 900;
            double kw = 22 + 3 * noise(51, q, 0);
            boolean workday = local.getDayOfWeek().getValue() <= 5;
            double h = local.getHour() + local.getMinute() / 60d;
            if (workday) {
                double shape = h < 6 ? 0 : h < 7 ? 0.6 : h < 20 ? 1.0 : h < 22 ? 0.7 : 0;
                kw += 62 * shape * (1 + 0.08 * noise(52, q, 0));
            }
            int day = local.getDayOfMonth();
            if (local.getDayOfWeek() == DayOfWeek.TUESDAY && day >= 8 && day <= 14 && h >= 10 && h < 10.75) {
                kw += 30 * (1 + 0.1 * noise(53, index(YearMonth.from(local)), 0));
            }
            points.add(new Point(t, kw / 4));
        }
        return points;
    }

    /**
     * Stand-in for the gas energy row of the main meter (case C): 15-minute kWh at the start of each quarter hour in
     * [from, until), heating load by month and hour, less on weekends.
     */
    public static List<Point> gasRlm(Instant from, Instant until) {
        List<Point> points = new ArrayList<>();
        for (Instant t = from; t.isBefore(until); t = t.plus(15, ChronoUnit.MINUTES)) {
            ZonedDateTime local = t.atZone(ZONE);
            long q = t.getEpochSecond() / 900;
            int h = local.getHour();
            double shape = h >= 5 && h < 8 ? 1.4 : h >= 8 && h < 17 ? 1.0 : h >= 17 && h < 21 ? 0.9 : 0.6;
            double day = local.getDayOfWeek().getValue() <= 5 ? 1 : 0.7;
            double kw = 5 + 70 * GAS_RLM_SHARE[local.getMonthValue() - 1] * shape * day + 3 * noise(61, q, 0);
            points.add(new Point(t, Math.max(0, kw) / 4));
        }
        return points;
    }

    /**
     * @return sums per clock hour (local time) at the start of the hour, like an hourly clean data row
     */
    public static List<Point> hourlySums(List<Point> quarterHours) {
        Map<Instant, Double> sums = new TreeMap<>();
        for (Point p : quarterHours) {
            Instant hour = p.ts.atZone(ZONE).truncatedTo(ChronoUnit.HOURS).toInstant();
            sums.merge(hour, p.value, Double::sum);
        }
        List<Point> points = new ArrayList<>();
        for (Map.Entry<Instant, Double> e : sums.entrySet()) points.add(new Point(e.getKey(), e.getValue()));
        return points;
    }

    /**
     * Stand-in for a charge point (case F, 1–3): daily kWh at the start of each day in [from, until).
     */
    public static List<Point> chargePoint(int number, LocalDate from, LocalDate until) {
        double[] workday = {16, 12, 8};
        double[] weekend = {2, 1, 0.5};
        List<Point> points = new ArrayList<>();
        for (LocalDate day = from; day.isBefore(until); day = day.plusDays(1)) {
            boolean work = day.getDayOfWeek().getValue() <= 5;
            double base = work ? workday[number - 1] : weekend[number - 1];
            double kWh = Math.max(0, base * (1 + 0.25 * noise(70 + number, day.toEpochDay(), 0)));
            points.add(new Point(day.atStartOfDay(ZONE).toInstant(), round(kWh, 2)));
        }
        return points;
    }
}
