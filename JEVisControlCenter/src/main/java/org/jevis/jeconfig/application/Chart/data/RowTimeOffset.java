/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.Chart.data;

import org.joda.time.DateTime;

/**
 * Row key {@code "timeOffset": {"amount": -1, "unit": "YEARS"}} of an analysis (tender point 03), as written by the web
 * app: where the row's values come from relative to the chart's time frame (-1 YEARS: last year, -2 HOURS: two hours
 * before). Same meaning as {@code org.jevis.commons.chart.TimeOffset} on the server.
 */
public class RowTimeOffset {

    public static final String[] UNITS = {"MINUTES", "HOURS", "DAYS", "WEEKS", "MONTHS", "YEARS"};

    private int amount;
    private String unit = "YEARS";

    public RowTimeOffset() {
    }

    public RowTimeOffset(int amount, String unit) {
        this.amount = amount;
        this.unit = unit;
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /** {@code time} moved by the offset ({@code direction} 1) or back ({@code direction} -1). */
    public static DateTime apply(DateTime time, int amount, String unit, int direction) {
        if (time == null || amount == 0) return time;
        int n = amount * direction;
        switch (unit == null ? "MINUTES" : unit) {
            case "HOURS":
                return time.plusHours(n);
            case "DAYS":
                return time.plusDays(n);
            case "WEEKS":
                return time.plusWeeks(n);
            case "MONTHS":
                return time.plusMonths(n);
            case "YEARS":
                return time.plusYears(n);
            case "MINUTES":
            default:
                return time.plusMinutes(n);
        }
    }
}
