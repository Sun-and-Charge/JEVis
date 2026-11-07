/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.iso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Net present value of an action (key "NPV" of the action JSON). Rates (interest, inflation) are percent. Year rows
 * and results are computed from the inputs like the Control Center's {@code NPVData.updateResults}: the stored results
 * can be outdated (they are only recomputed when an input is edited in the Control Center).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NpvData {

    @JsonProperty("Interest Rate")
    private Double interestRate = 10.0;
    @JsonProperty("Investment")
    private Double investment = 0.0;
    @JsonProperty("Saving")
    private Double saving = 0.0;
    @JsonProperty("Running Cost")
    private Double runningCost = 0.0;
    @JsonProperty("Inflation")
    private Double inflation = 3.1;
    @JsonProperty("Years")
    private Integer years = 0;
    @JsonProperty("Duration")
    private Integer duration = 0;
    @JsonProperty("Result")
    private Double result = 0.0;
    @JsonProperty("Profitability Index")
    private Double profitabilityIndex = 0.0;
    @JsonProperty("Result over X")
    private Double resultOverX = 0.0;
    @JsonProperty("Profitability Index over X")
    private Double profitabilityIndexOverX = 0.0;
    @JsonProperty("Sum Investment")
    private Double sumInvestment = 0.0;
    @JsonProperty("Sum Payout")
    private Double sumPayout = 0.0;
    @JsonProperty("Sum Net")
    private Double sumNet = 0.0;

    /**
     * Maximum number of computed year rows (protection against absurd stored values).
     */
    static final int MAX_YEARS = 200;

    private static double value(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() ? value : 0;
    }

    /** Profitability index like the Control Center: NaN (no investment and no return) becomes 0. */
    private static double index(double discountedSum, double investment) {
        double index = discountedSum / investment;
        return Double.isNaN(index) ? 0 : index;
    }

    static double round(double value) {
        return Math.round(value * 100) / 100d;
    }

    /**
     * One row per year 1..Years: {@code deposit = saving·(1+inflation/100)^y}, {@code cost = runningCost·
     * (1+inflation/100)^y} (both rounded to cents), {@code net = deposit - cost}, {@code discounted =
     * net/(1+interestRate/100)^y} (rounded).
     */
    public List<YearRow> yearRows() {
        List<YearRow> rows = new ArrayList<>();
        int count = Math.min(years != null ? years : 0, MAX_YEARS);
        double inflationFactor = 1 + value(inflation) / 100;
        double interestFactor = 1 + value(interestRate) / 100;
        for (int year = 1; year <= count; year++) {
            double deposit = round(value(saving) * Math.pow(inflationFactor, year));
            double cost = round(value(runningCost) * Math.pow(inflationFactor, year));
            double net = deposit - cost;
            double discounted = round(net / Math.pow(interestFactor, year));
            rows.add(new YearRow(year, deposit, cost, net, discounted));
        }
        return rows;
    }

    /**
     * @return the JSON of the web API ({@code interestRate, investment, …, yearRows}); non-finite numbers as null
     */
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("interestRate", IsoJson.finite(interestRate));
        json.put("investment", IsoJson.finite(investment));
        json.put("saving", IsoJson.finite(saving));
        json.put("runningCost", IsoJson.finite(runningCost));
        json.put("inflation", IsoJson.finite(inflation));
        json.put("years", years);
        json.put("duration", duration);
        List<YearRow> yearRows = yearRows();
        /* NPVData.updateResults: sums over the year rows, the investment counts as payout */
        double investmentValue = value(investment);
        double interestFactor = 1 + value(interestRate) / 100;
        int overX = duration != null ? duration : 0;
        double discountedSum = 0, discountedSumOverX = 0, deposits = 0, payouts = investmentValue, net = 0;
        for (YearRow row : yearRows) {
            double discounted = row.net / Math.pow(interestFactor, row.year);
            discountedSum += discounted;
            if (row.year <= overX) discountedSumOverX += discounted;
            deposits += row.deposit;
            payouts += row.cost;
            net += row.net;
        }
        json.put("result", round(-investmentValue + discountedSum));
        json.put("profitabilityIndex", IsoJson.finite(index(discountedSum, investmentValue)));
        json.put("resultOverX", round(-investmentValue + discountedSumOverX));
        json.put("profitabilityIndexOverX", IsoJson.finite(index(discountedSumOverX, investmentValue)));
        double depositsRounded = round(deposits);
        double payoutsRounded = round(payouts);
        json.put("sumDeposits", depositsRounded);
        json.put("sumPayouts", payoutsRounded);
        json.put("sumNet", round(depositsRounded - payoutsRounded));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (YearRow row : yearRows) rows.add(row.toJson());
        json.put("yearRows", rows);
        return json;
    }

    public Double getInterestRate() {
        return interestRate;
    }

    public Double getInvestment() {
        return investment;
    }

    public Double getSaving() {
        return saving;
    }

    public Double getRunningCost() {
        return runningCost;
    }

    public Double getInflation() {
        return inflation;
    }

    public Integer getYears() {
        return years;
    }

    public Integer getDuration() {
        return duration;
    }

    public Double getResult() {
        return result;
    }

    /**
     * A computed year of the net present value.
     */
    public static final class YearRow {
        private final int year;
        private final double deposit;
        private final double cost;
        private final double net;
        private final double discounted;

        YearRow(int year, double deposit, double cost, double net, double discounted) {
            this.year = year;
            this.deposit = deposit;
            this.cost = cost;
            this.net = net;
            this.discounted = discounted;
        }

        public int getYear() {
            return year;
        }

        public double getDeposit() {
            return deposit;
        }

        public double getCost() {
            return cost;
        }

        public double getNet() {
            return net;
        }

        public double getDiscounted() {
            return discounted;
        }

        Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("year", year);
            json.put("deposit", IsoJson.finite(deposit));
            json.put("cost", IsoJson.finite(cost));
            json.put("net", IsoJson.finite(net));
            json.put("discounted", IsoJson.finite(discounted));
            return json;
        }
    }
}
