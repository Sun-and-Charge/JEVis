/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;

import java.util.Locale;
import java.util.Objects;

/**
 * Relative time reference of an analysis row (tender point 03): where the row's values come from, relative to the
 * time frame of the chart. The values of the shifted range are shown on the chart's time axis, e.g. {@code -1 YEARS}
 * lays last year's values over the current time frame and {@code -2 HOURS} pairs the outside temperature of 10:00 with
 * the heat of 12:00 in a scatter chart. Stored as row key
 * {@code "timeOffset": {"amount": -1, "unit": "YEARS"}}; a plain number (minutes) is read as well.
 */
public final class TimeOffset {

    public enum Unit {MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS}

    public static final TimeOffset NONE = new TimeOffset(0, Unit.MINUTES);

    /** limits of the amount per unit (about ten years either way) */
    public static int maxAmount(Unit unit) {
        switch (unit) {
            case MINUTES:
                return 5_256_000;
            case HOURS:
                return 87_600;
            case DAYS:
                return 3_650;
            case WEEKS:
                return 520;
            case MONTHS:
                return 120;
            case YEARS:
            default:
                return 10;
        }
    }

    private final int amount;
    private final Unit unit;

    public TimeOffset(int amount, Unit unit) {
        this.amount = amount;
        this.unit = unit != null ? unit : Unit.MINUTES;
    }

    public int getAmount() {
        return amount;
    }

    public Unit getUnit() {
        return unit;
    }

    public boolean isZero() {
        return amount == 0;
    }

    /** {@code time} moved by the offset ({@code direction} 1) or back ({@code direction} -1). */
    public DateTime apply(DateTime time, int direction) {
        if (time == null || amount == 0) return time;
        int n = amount * direction;
        switch (unit) {
            case MINUTES:
                return time.plusMinutes(n);
            case HOURS:
                return time.plusHours(n);
            case DAYS:
                return time.plusDays(n);
            case WEEKS:
                return time.plusWeeks(n);
            case MONTHS:
                return time.plusMonths(n);
            case YEARS:
            default:
                return time.plusYears(n);
        }
    }

    /** Reads the row key: {@code {"amount", "unit"}} or a number of minutes; missing or invalid: {@link #NONE}. */
    public static TimeOffset fromJson(JsonNode node) {
        if (node == null || node.isNull()) return NONE;
        if (node.isNumber()) return new TimeOffset(node.asInt(), Unit.MINUTES);
        if (!node.isObject()) return NONE;
        Unit unit;
        try {
            unit = Unit.valueOf(node.path("unit").asText("MINUTES").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
        int amount = node.path("amount").asInt(0);
        if (Math.abs(amount) > maxAmount(unit)) return NONE;
        return new TimeOffset(amount, unit);
    }

    public ObjectNode toJson() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("amount", amount);
        node.put("unit", unit.name());
        return node;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeOffset)) return false;
        TimeOffset other = (TimeOffset) o;
        return (amount == 0 && other.amount == 0) || (amount == other.amount && unit == other.unit);
    }

    @Override
    public int hashCode() {
        return amount == 0 ? 0 : Objects.hash(amount, unit);
    }

    @Override
    public String toString() {
        return amount + " " + unit;
    }
}
