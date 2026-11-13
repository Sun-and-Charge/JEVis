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

import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Expected value and due state of a meter reading (stage E8.1, see docs/WEB-API.md), ported from the Sun and Charge
 * app ({@code ExpectedValues.estimate}, {@code Due.state} in {@code shared/.../domain/ExpectedValue.kt}). The web app
 * computes the same in {@code readings/expected.ts}.
 */
final class ReadingExpectation {

    /**
     * Number of recent readings that are used (counters: the intervals between the last {@code WINDOW + 1})
     */
    static final int WINDOW = 10;
    /**
     * Extra tolerance on top of the observed spread
     */
    static final double MARGIN = 0.2;

    /**
     * Due state of a meter ({@code Due.state}; {@link #NONE} instead of the app's {@code OK} for rows without period)
     */
    enum Due {NONE, DUE, OK, OVERDUE}

    private ReadingExpectation() {
    }

    /**
     * A reading: time (epoch milliseconds) and value.
     */
    static final class Reading {
        final long millis;
        final double value;

        Reading(long millis, double value) {
            this.millis = millis;
            this.value = value;
        }
    }

    /**
     * The value expected for a new reading and the range that is still plausible.
     */
    static final class Expected {
        final double value;
        final double low;
        final double high;

        Expected(double value, double low, double high) {
            this.value = value;
            this.low = low;
            this.high = high;
        }

        boolean contains(double v) {
            return v >= low && v <= high;
        }
    }

    /**
     * @param counter   the row is a counter ("Conversion to Differential")
     * @param hasPeriod the row has a non-zero "Period"
     * @param readings  the recent readings, any order
     * @param at        time of the new reading (epoch milliseconds)
     * @return counters: {@link #meterReading}; other rows with a period: {@link #average}; else null
     */
    static Expected estimate(boolean counter, boolean hasPeriod, List<Reading> readings, long at) {
        if (counter) return meterReading(readings, at);
        if (hasPeriod) return average(readings);
        return null;
    }

    /**
     * The last reading plus the consumption at the average rate of the intervals of the last {@code WINDOW + 1}
     * readings; the range from the lowest to the highest rate, the spread at least {@code MARGIN} of the average rate,
     * low not below the last reading. Intervals with a decreasing value (meter change, overflow) or without time are
     * left out.
     *
     * @return null with fewer than 2 readings, no usable interval or a time not after the last reading
     */
    static Expected meterReading(List<Reading> readings, long at) {
        List<Reading> recent = lastSorted(readings, WINDOW + 1);
        if (recent.size() < 2) return null;
        Reading last = recent.get(recent.size() - 1);
        long seconds = Math.floorDiv(at - last.millis, 1000L);
        if (seconds <= 0) return null;

        List<Double> rates = new ArrayList<>();
        for (int i = 1; i < recent.size(); i++) {
            Reading a = recent.get(i - 1);
            Reading b = recent.get(i);
            long dt = Math.floorDiv(b.millis - a.millis, 1000L);
            if (dt <= 0 || b.value < a.value) continue;
            rates.add((b.value - a.value) / dt);
        }
        if (rates.isEmpty()) return null;
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double rate : rates) {
            sum += rate;
            min = Math.min(min, rate);
            max = Math.max(max, rate);
        }
        double average = sum / rates.size();
        double spread = Math.max(max - min, average * MARGIN);
        double low = Math.max(average - spread, 0.0) * seconds;
        double high = (average + spread) * seconds;
        return new Expected(last.value + average * seconds, last.value + low, last.value + high);
    }

    /**
     * The average of the last {@code WINDOW} values; range min − margin … max + margin with margin = max({@code MARGIN}
     * of (max − min), {@code MARGIN} of |average|).
     *
     * @return null with fewer than 2 values
     */
    static Expected average(List<Reading> readings) {
        List<Reading> recent = lastSorted(readings, WINDOW);
        if (recent.size() < 2) return null;
        double sum = 0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Reading reading : recent) {
            sum += reading.value;
            min = Math.min(min, reading.value);
            max = Math.max(max, reading.value);
        }
        double average = sum / recent.size();
        double margin = Math.max((max - min) * MARGIN, Math.abs(average) * MARGIN);
        return new Expected(average, min - margin, max + margin);
    }

    private static List<Reading> lastSorted(List<Reading> readings, int count) {
        List<Reading> sorted = new ArrayList<>(readings);
        sorted.sort(Comparator.comparingLong(r -> r.millis));
        return new ArrayList<>(sorted.subList(Math.max(0, sorted.size() - count), sorted.size()));
    }

    /**
     * {@code Due.state}: {@link Due#NONE} without period; {@link Due#DUE} without any reading; otherwise with
     * {@code next} = the next period after the last reading ({@link ResourceWebEditValues#suggested}, as
     * {@code GET /rows/{id}/entry}): {@link Due#OK} if {@code next} is after now, {@link Due#DUE} if
     * {@code next + period} is after now, else {@link Due#OVERDUE}.
     *
     * @param last the time of the last reading in the zone of the site, null without readings
     */
    static Due due(DateTime last, Period period, WebEdit.WorkDay workDay, DateTime now) {
        if (period == null || isZero(period)) return Due.NONE;
        if (last == null) return Due.DUE;
        DateTime next = ResourceWebEditValues.suggested(last, period, workDay);
        if (next.isAfter(now)) return Due.OK;
        if (next.plus(period).isAfter(now)) return Due.DUE;
        return Due.OVERDUE;
    }

    /**
     * @return whether the period has no length ({@code PT0S}, {@code P0D}, …)
     */
    static boolean isZero(Period period) {
        for (int value : period.getValues()) {
            if (value != 0) return false;
        }
        return true;
    }
}
