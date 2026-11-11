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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure replay rules for one raw data row: offset, window, target timestamps and counter values. No JEVis access.
 * <p>
 * A row is continued by copying its own history: source samples are written again {@link Offset} later. Rows with a
 * daily or shorter period move by whole weeks (weekday and time of day stay), rows with a calendar period (months,
 * quarters, years) by whole calendar months (day of month and time stay, end of month is clamped). The arithmetic
 * is done in local time of {@link #ZONE}; samples that fall into a DST gap or collide with another sample at the
 * target are skipped.
 */
public final class ReplayPlanner {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final int MAX_CATCH_UP_WEEKS = 8;
    public static final int MAX_CATCH_UP_MONTHS = 3;
    public static final int FULL_YEAR_WEEKS = 52;
    public static final int FULL_YEAR_MONTHS = 12;
    /**
     * median spacing from which a row without "Period" counts as calendar based
     */
    static final Duration CALENDAR_SPACING = Duration.ofDays(28);

    private static final Pattern ISO_PERIOD = Pattern.compile(
            "P(?:(\\d+)Y)?(?:(\\d+)M)?(?:(\\d+)W)?(?:(\\d+)D)?(?:T(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+(?:\\.\\d+)?)S)?)?");
    private static final Pattern OFFSET_TEXT = Pattern.compile("P(\\d+)([WM])");

    private ReplayPlanner() {
    }

    public enum Unit {WEEKS, MONTHS}

    /**
     * Distance between a source sample and its copy: a number of whole weeks or whole calendar months.
     */
    public static final class Offset {
        private final Unit unit;
        private final int amount;

        public Offset(Unit unit, int amount) {
            if (amount < 1) throw new IllegalArgumentException("Offset must be at least 1");
            this.unit = Objects.requireNonNull(unit);
            this.amount = amount;
        }

        public static Offset weeks(int weeks) {
            return new Offset(Unit.WEEKS, weeks);
        }

        public static Offset months(int months) {
            return new Offset(Unit.MONTHS, months);
        }

        public Unit getUnit() {
            return unit;
        }

        public int getAmount() {
            return amount;
        }

        LocalDateTime add(LocalDateTime time) {
            return unit == Unit.WEEKS ? time.plusWeeks(amount) : time.plusMonths(amount);
        }

        ZonedDateTime subtract(ZonedDateTime time) {
            return unit == Unit.WEEKS ? time.minusWeeks(amount) : time.minusMonths(amount);
        }

        /**
         * @return e.g. "P13W" or "P5M"
         */
        @Override
        public String toString() {
            return "P" + amount + (unit == Unit.WEEKS ? "W" : "M");
        }

        /**
         * @return the offset written by {@link #toString()}, or null if the text is not one
         */
        public static Offset parse(String text) {
            if (text == null) return null;
            Matcher m = OFFSET_TEXT.matcher(text.trim().toUpperCase(Locale.ROOT));
            if (!m.matches()) return null;
            int amount = Integer.parseInt(m.group(1));
            if (amount < 1) return null;
            return new Offset("W".equals(m.group(2)) ? Unit.WEEKS : Unit.MONTHS, amount);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Offset)) return false;
            Offset other = (Offset) o;
            return unit == other.unit && amount == other.amount;
        }

        @Override
        public int hashCode() {
            return Objects.hash(unit, amount);
        }
    }

    /**
     * One sample: timestamp and value (Double, Long, String, Boolean, ...).
     */
    public static final class Sample {
        private final Instant timestamp;
        private final Object value;

        public Sample(Instant timestamp, Object value) {
            this.timestamp = Objects.requireNonNull(timestamp);
            this.value = value;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public String toString() {
            return timestamp + "=" + value;
        }
    }

    /**
     * Source time range to read for one cycle, both bounds inclusive.
     */
    public static final class Range {
        private final Instant from;
        private final Instant to;

        Range(Instant from, Instant to) {
            this.from = from;
            this.to = to;
        }

        public Instant getFrom() {
            return from;
        }

        public Instant getTo() {
            return to;
        }
    }

    /**
     * A row is calendar based if its period has a month or year part (P1M, P3M, P1Y, ...). Without a usable period
     * ("" or a zero period like PT0S) the median spacing of the given timestamps decides (at least 28 days).
     *
     * @param period     value of the row's "Period" attribute, may be null
     * @param timestamps recent sample timestamps of the row (any order), used only without a period
     */
    public static boolean isCalendarPeriod(String period, List<Instant> timestamps) {
        Boolean byPeriod = calendarFromPeriod(period);
        if (byPeriod != null) return byPeriod;
        Duration median = medianSpacing(timestamps);
        return median != null && median.compareTo(CALENDAR_SPACING) >= 0;
    }

    /**
     * @return true for a period with a month or year part, false for a shorter non-zero period, null if the period
     * is missing, zero or not readable (then the spacing of the samples decides)
     */
    public static Boolean calendarFromPeriod(String period) {
        if (period == null || period.trim().isEmpty()) return null;
        Matcher m = ISO_PERIOD.matcher(period.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) return null;
        if (positive(m.group(1)) || positive(m.group(2))) return true;
        if (positive(m.group(3)) || positive(m.group(4)) || positive(m.group(5)) || positive(m.group(6))
                || positive(m.group(7))) return false;
        return null;
    }

    private static boolean positive(String group) {
        return group != null && Double.parseDouble(group) > 0;
    }

    /**
     * @return the median distance between consecutive timestamps, or null with fewer than two
     */
    static Duration medianSpacing(List<Instant> timestamps) {
        if (timestamps == null || timestamps.size() < 2) return null;
        List<Instant> sorted = new ArrayList<>(timestamps);
        sorted.sort(Comparator.naturalOrder());
        List<Duration> gaps = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            Duration gap = Duration.between(sorted.get(i - 1), sorted.get(i));
            if (!gap.isZero()) gaps.add(gap);
        }
        if (gaps.isEmpty()) return null;
        gaps.sort(Comparator.naturalOrder());
        return gaps.get(gaps.size() / 2);
    }

    /**
     * Offset of a row from its history.
     * <ul>
     * <li>week rows: 52 weeks if the history spans at least 364 days, otherwise the span in whole weeks;</li>
     * <li>calendar rows: 12 months if the history spans at least 12 months, otherwise the span in whole months.</li>
     * </ul>
     * A history shorter than one week (one month for calendar rows), e.g. a few hours of test data, is not replayed:
     * the result is null.
     *
     * @param first    first sample of the row
     * @param last     last sample of the row
     * @param calendar result of {@link #isCalendarPeriod}
     */
    public static Offset offset(Instant first, Instant last, boolean calendar) {
        ZonedDateTime from = first.atZone(ZONE);
        ZonedDateTime to = last.atZone(ZONE);
        if (calendar) {
            long months = ChronoUnit.MONTHS.between(from, to);
            if (months < 1) return null;
            return Offset.months((int) Math.min(FULL_YEAR_MONTHS, months));
        }
        long days = ChronoUnit.DAYS.between(from, to);
        if (days >= FULL_YEAR_WEEKS * 7L) return Offset.weeks(FULL_YEAR_WEEKS);
        if (days < 7) return null;
        return Offset.weeks((int) (days / 7));
    }

    /**
     * Whether the source values behave like a counter reading: numeric and rising, at most 1 % falling steps (a meter
     * change or reset). Interval values (consumption per period) fall all the time.
     */
    public static boolean looksLikeCounter(List<Sample> source) {
        Double previous = null;
        int steps = 0, falling = 0;
        for (Sample s : source) {
            if (!(s.getValue() instanceof Number)) return false;
            double v = ((Number) s.getValue()).doubleValue();
            if (previous != null) {
                steps++;
                if (v < previous) falling++;
            }
            previous = v;
        }
        return steps == 0 || falling <= steps / 100;
    }

    /**
     * @return true if the offset is the one of a full year, i.e. does not depend on the history length
     */
    public static boolean isFullYear(Offset offset) {
        return offset.getUnit() == Unit.WEEKS ? offset.getAmount() == FULL_YEAR_WEEKS
                : offset.getAmount() == FULL_YEAR_MONTHS;
    }

    /**
     * End of this cycle's target window: now, but at most 8 weeks (week rows) or 3 months (calendar rows) after the
     * row's last sample.
     */
    public static Instant targetEnd(Instant last, Instant now, Offset offset) {
        Instant limit = catchUpLimit(last, offset);
        return limit.isBefore(now) ? limit : now;
    }

    /**
     * @return the time 8 weeks (week rows) or 3 months (calendar rows) after {@code from}
     */
    static Instant catchUpLimit(Instant from, Offset offset) {
        ZonedDateTime l = from.atZone(ZONE);
        return (offset.getUnit() == Unit.WEEKS ? l.plusWeeks(MAX_CATCH_UP_WEEKS)
                : l.plusMonths(MAX_CATCH_UP_MONTHS)).toInstant();
    }

    /**
     * Source range to read: {@code (last - offset, targetEnd - offset]} widened by a margin, so DST shifts, clamped
     * month ends and (for counters) the source sample before the window are included. {@link #plan} filters by the
     * target timestamps.
     */
    public static Range sourceRange(Instant last, Instant targetEnd, Offset offset, boolean counter) {
        Duration margin;
        if (offset.getUnit() == Unit.MONTHS) {
            margin = Duration.ofDays(400);
        } else {
            margin = counter ? Duration.ofDays(8) : Duration.ofDays(1);
        }
        Instant from = offset.subtract(last.atZone(ZONE)).toInstant().minus(margin);
        Instant to = offset.subtract(targetEnd.atZone(ZONE)).toInstant().plus(Duration.ofDays(4));
        return new Range(from, to);
    }

    /**
     * Target timestamp of a source sample: local time in {@link #ZONE} plus the offset.
     *
     * @return the target, or null if the local target time does not exist (spring DST gap)
     */
    public static Instant target(Instant source, Offset offset) {
        ZonedDateTime src = source.atZone(ZONE);
        LocalDateTime local = offset.add(src.toLocalDateTime());
        List<ZoneOffset> valid = ZONE.getRules().getValidOffsets(local);
        if (valid.isEmpty()) return null;
        ZoneOffset zoneOffset = valid.contains(src.getOffset()) ? src.getOffset() : valid.get(0);
        return local.toInstant(zoneOffset);
    }

    /**
     * Plans the copies for one cycle.
     * <p>
     * Source samples whose target lies in {@code (lastTarget, targetEnd]} are copied. Values are copied as they are,
     * except for counters: the copy is the row's last value plus the positive difference to the previous source
     * sample, so the reading keeps rising; a negative difference (counter reset) counts as 0. Differences of skipped
     * samples (DST gap or collision) are carried to the next copy.
     *
     * @param source         source samples (any order), as read for {@link #sourceRange}
     * @param lastTarget     timestamp of the row's last sample
     * @param lastValue      value of the row's last sample (start of the counter)
     * @param offset         offset of the row
     * @param targetEnd      result of {@link #targetEnd}
     * @param counter        true if the row is a counter reading
     * @return the samples to write, sorted by time, without duplicates
     */
    public static List<Sample> plan(List<Sample> source, Instant lastTarget, Object lastValue, Offset offset,
                                    Instant targetEnd, boolean counter) {
        List<Sample> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparing(Sample::getTimestamp));

        boolean counting = counter && lastValue instanceof Number;
        double running = counting ? ((Number) lastValue).doubleValue() : 0;
        Double previous = null;

        List<Sample> result = new ArrayList<>();
        Set<Instant> used = new HashSet<>();
        for (Sample s : sorted) {
            Instant t = target(s.getTimestamp(), offset);
            if (t == null) continue;
            if (!t.isAfter(lastTarget)) {
                if (counting && s.getValue() instanceof Number) previous = ((Number) s.getValue()).doubleValue();
                continue;
            }
            if (t.isAfter(targetEnd)) continue;
            if (!used.add(t)) continue;

            Object value = s.getValue();
            if (counting && value instanceof Number) {
                double v = ((Number) value).doubleValue();
                if (previous != null) running += Math.max(0, v - previous);
                previous = v;
                value = running;
            }
            result.add(new Sample(t, value));
        }
        result.sort(Comparator.comparing(Sample::getTimestamp));
        if (!result.isEmpty()) {
            // catch-up limit, counted from the first copy (sparse rows may have nothing right after their last sample)
            Instant cap = catchUpLimit(result.get(0).getTimestamp(), offset);
            result.removeIf(s -> s.getTimestamp().isAfter(cap));
        }
        return result;
    }
}
