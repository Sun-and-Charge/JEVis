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

import org.jevis.jedemo.ReplayPlanner.Offset;
import org.jevis.jedemo.ReplayPlanner.Sample;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class ReplayPlannerTest {

    private static Instant berlin(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(ReplayPlanner.ZONE).toInstant();
    }

    private static ZonedDateTime local(Instant i) {
        return i.atZone(ReplayPlanner.ZONE);
    }

    /** samples every {@code step} from {@code from} (inclusive) to {@code to} (inclusive), value = index */
    private static List<Sample> series(Instant from, Instant to, Duration step) {
        List<Sample> list = new ArrayList<>();
        int i = 0;
        for (Instant t = from; !t.isAfter(to); t = t.plus(step)) list.add(new Sample(t, (double) i++));
        return list;
    }

    // ------------------------------------------------------------------ offset rules

    @Test
    public void offsetIs52WeeksForAFullYear() {
        Instant first = berlin(2025, 10, 1, 0, 0);
        assertEquals(Offset.weeks(52), ReplayPlanner.offset(first, berlin(2026, 9, 30, 0, 0), false)); // 364 days
        assertEquals(Offset.weeks(52), ReplayPlanner.offset(first, berlin(2026, 11, 6, 0, 0), false));
    }

    @Test
    public void offsetIsHistoryInWholeWeeksBelowAYear() {
        Instant first = berlin(2026, 8, 1, 0, 0);
        // 97 days -> 13 weeks
        assertEquals(Offset.weeks(13), ReplayPlanner.offset(first, berlin(2026, 11, 6, 0, 0), false));
        // 363 days -> 51 weeks
        assertEquals(Offset.weeks(51), ReplayPlanner.offset(berlin(2025, 11, 8, 0, 0), berlin(2026, 11, 6, 0, 0), false));
    }

    @Test
    public void historyShorterThanAWeekIsNotReplayed() {
        Instant first = berlin(2026, 11, 1, 0, 0);
        assertNull(ReplayPlanner.offset(first, berlin(2026, 11, 3, 0, 0), false));
        assertNull(ReplayPlanner.offset(first, berlin(2026, 11, 1, 4, 0), false)); // a few hours of test data
        assertEquals(Offset.weeks(1), ReplayPlanner.offset(first, berlin(2026, 11, 8, 0, 0), false));
        assertNull(ReplayPlanner.offset(berlin(2026, 10, 20, 0, 0), berlin(2026, 11, 1, 0, 0), true));
    }

    @Test
    public void offsetOfCalendarRowsInMonths() {
        assertEquals(Offset.months(12), ReplayPlanner.offset(berlin(2025, 11, 1, 0, 0), berlin(2026, 11, 1, 0, 0), true));
        assertEquals(Offset.months(12), ReplayPlanner.offset(berlin(2020, 1, 1, 0, 0), berlin(2026, 11, 1, 0, 0), true));
        assertEquals(Offset.months(5), ReplayPlanner.offset(berlin(2026, 6, 1, 0, 0), berlin(2026, 11, 1, 0, 0), true));
        assertEquals(Offset.months(1), ReplayPlanner.offset(berlin(2026, 10, 1, 0, 0), berlin(2026, 11, 1, 0, 0), true));
    }

    @Test
    public void offsetTextRoundTrip() {
        assertEquals(Offset.weeks(13), Offset.parse(Offset.weeks(13).toString()));
        assertEquals(Offset.months(5), Offset.parse("p5m"));
        assertNull(Offset.parse("P0W"));
        assertNull(Offset.parse("PT15M"));
        assertNull(Offset.parse(null));
        assertTrue(ReplayPlanner.isFullYear(Offset.weeks(52)));
        assertTrue(ReplayPlanner.isFullYear(Offset.months(12)));
        assertFalse(ReplayPlanner.isFullYear(Offset.weeks(13)));
    }

    // ------------------------------------------------------------------ calendar detection

    @Test
    public void calendarPeriodFromPeriodAttribute() {
        assertTrue(ReplayPlanner.isCalendarPeriod("P1M", null));
        assertTrue(ReplayPlanner.isCalendarPeriod("P3M", null));
        assertTrue(ReplayPlanner.isCalendarPeriod("P1Y", null));
        assertFalse(ReplayPlanner.isCalendarPeriod("PT15M", null));
        assertFalse(ReplayPlanner.isCalendarPeriod("P1D", null));
        assertFalse(ReplayPlanner.isCalendarPeriod("P1W", null));
    }

    @Test
    public void calendarPeriodFromSpacingWithoutPeriod() {
        List<Instant> monthly = Arrays.asList(berlin(2026, 1, 1, 0, 0), berlin(2026, 2, 1, 0, 0),
                berlin(2026, 3, 1, 0, 0), berlin(2026, 4, 1, 0, 0));
        List<Instant> daily = Arrays.asList(berlin(2026, 1, 1, 0, 0), berlin(2026, 1, 2, 0, 0),
                berlin(2026, 1, 3, 0, 0), berlin(2026, 2, 3, 0, 0));
        assertTrue(ReplayPlanner.isCalendarPeriod(null, monthly));
        assertTrue(ReplayPlanner.isCalendarPeriod("PT0S", monthly)); // irregular period: spacing decides
        assertFalse(ReplayPlanner.isCalendarPeriod("", daily));
        assertFalse(ReplayPlanner.isCalendarPeriod(null, null));
    }

    // ------------------------------------------------------------------ window

    @Test
    public void windowCopiesFromLastSampleUntilNow() {
        Offset offset = Offset.weeks(52);
        Instant last = berlin(2026, 10, 29, 12, 0);
        Instant now = berlin(2026, 11, 2, 9, 7);
        Instant end = ReplayPlanner.targetEnd(last, now, offset);
        assertEquals(now, end);

        List<Sample> source = series(berlin(2025, 10, 25, 0, 0), berlin(2025, 11, 10, 0, 0), Duration.ofMinutes(15));
        List<Sample> plan = ReplayPlanner.plan(source, last, 5.0, offset, end, false);

        assertFalse(plan.isEmpty());
        assertEquals(berlin(2026, 10, 29, 12, 15), plan.get(0).getTimestamp());
        assertEquals(berlin(2026, 11, 2, 9, 0), plan.get(plan.size() - 1).getTimestamp());
        for (Sample s : plan) {
            assertTrue(s.getTimestamp().isAfter(last));
            assertFalse(s.getTimestamp().isAfter(now));
        }
        // same weekday and time of day as the source year
        Sample firstCopy = plan.get(0);
        ZonedDateTime src = local(firstCopy.getTimestamp()).minusWeeks(52);
        assertEquals(local(firstCopy.getTimestamp()).getDayOfWeek(), src.getDayOfWeek());
        assertEquals(local(firstCopy.getTimestamp()).toLocalTime(), src.toLocalTime());
    }

    @Test
    public void sourceRangeCoversTheWindow() {
        Offset offset = Offset.weeks(52);
        Instant last = berlin(2026, 10, 29, 12, 0);
        Instant end = berlin(2026, 11, 2, 9, 0);
        ReplayPlanner.Range range = ReplayPlanner.sourceRange(last, end, offset, false);
        assertFalse(range.getFrom().isAfter(local(last).minusWeeks(52).toInstant()));
        assertFalse(range.getTo().isBefore(local(end).minusWeeks(52).toInstant()));
    }

    @Test
    public void nothingToDoWhenUpToDate() {
        Instant last = berlin(2026, 11, 2, 9, 0);
        assertEquals(last, ReplayPlanner.targetEnd(last, last, Offset.weeks(52)));
        List<Sample> source = series(berlin(2025, 10, 25, 0, 0), berlin(2025, 11, 10, 0, 0), Duration.ofHours(1));
        assertTrue(ReplayPlanner.plan(source, last, 1.0, Offset.weeks(52), last, false).isEmpty());
    }

    @Test
    public void catchUpIsLimitedToEightWeeks() {
        Offset offset = Offset.weeks(52);
        Instant last = berlin(2026, 6, 1, 0, 0);
        Instant now = berlin(2026, 11, 11, 10, 0);
        Instant end = ReplayPlanner.targetEnd(last, now, offset);
        assertEquals(local(last).plusWeeks(8).toInstant(), end);

        List<Sample> source = series(berlin(2025, 5, 1, 0, 0), berlin(2025, 11, 30, 0, 0), Duration.ofDays(1));
        List<Sample> plan = ReplayPlanner.plan(source, last, 0.0, offset, end, false);
        assertEquals(56, plan.size());
        assertFalse(plan.get(plan.size() - 1).getTimestamp().isAfter(end));
    }

    @Test
    public void catchUpOfCalendarRowsIsLimitedToThreeMonths() {
        Instant last = berlin(2026, 3, 1, 0, 0);
        Instant now = berlin(2026, 11, 11, 10, 0);
        assertEquals(berlin(2026, 6, 1, 0, 0), ReplayPlanner.targetEnd(last, now, Offset.months(12)));
    }

    @Test
    public void nonNumericValuesAreCopiedAsTheyAre() {
        Instant last = berlin(2026, 11, 1, 0, 0);
        List<Sample> source = Arrays.asList(
                new Sample(berlin(2025, 11, 3, 8, 0), "an"),
                new Sample(berlin(2025, 11, 3, 18, 0), "aus"));
        List<Sample> plan = ReplayPlanner.plan(source, last, "aus", Offset.weeks(52), berlin(2026, 11, 11, 0, 0), true);
        assertEquals(2, plan.size());
        assertEquals("an", plan.get(0).getValue());
        assertEquals(berlin(2026, 11, 2, 8, 0), plan.get(0).getTimestamp());
    }

    // ------------------------------------------------------------------ counters

    @Test
    public void counterContinuesFromLastValue() {
        Instant last = berlin(2026, 11, 2, 0, 0);
        // source: the sample mapped onto "last" is the baseline, then +5, +7
        List<Sample> source = Arrays.asList(
                new Sample(berlin(2025, 11, 3, 0, 0), 100.0),
                new Sample(berlin(2025, 11, 3, 1, 0), 105.0),
                new Sample(berlin(2025, 11, 3, 2, 0), 112.0));
        List<Sample> plan = ReplayPlanner.plan(source, last, 5000.0, Offset.weeks(52), berlin(2026, 11, 11, 0, 0), true);
        assertEquals(2, plan.size());
        assertEquals(5005.0, (Double) plan.get(0).getValue(), 1e-9);
        assertEquals(5012.0, (Double) plan.get(1).getValue(), 1e-9);
    }

    @Test
    public void counterResetCountsAsZero() {
        Instant last = berlin(2026, 11, 2, 0, 0);
        List<Sample> source = Arrays.asList(
                new Sample(berlin(2025, 11, 3, 0, 0), 900.0),
                new Sample(berlin(2025, 11, 3, 1, 0), 950.0),
                new Sample(berlin(2025, 11, 3, 2, 0), 3.0),   // counter exchanged / overflow
                new Sample(berlin(2025, 11, 3, 3, 0), 10.0));
        List<Sample> plan = ReplayPlanner.plan(source, last, 2000.0, Offset.weeks(52), berlin(2026, 11, 11, 0, 0), true);
        assertEquals(3, plan.size());
        assertEquals(2050.0, (Double) plan.get(0).getValue(), 1e-9);
        assertEquals(2050.0, (Double) plan.get(1).getValue(), 1e-9);
        assertEquals(2057.0, (Double) plan.get(2).getValue(), 1e-9);
        for (int i = 1; i < plan.size(); i++) {
            assertTrue((Double) plan.get(i).getValue() >= (Double) plan.get(i - 1).getValue());
        }
    }

    @Test
    public void counterWithoutBaselineStartsWithoutJump() {
        Instant last = berlin(2026, 11, 2, 0, 0);
        List<Sample> source = Arrays.asList(
                new Sample(berlin(2025, 11, 3, 1, 0), 105.0),
                new Sample(berlin(2025, 11, 3, 2, 0), 112.0));
        List<Sample> plan = ReplayPlanner.plan(source, last, 7.0, Offset.weeks(52), berlin(2026, 11, 11, 0, 0), true);
        assertEquals(7.0, (Double) plan.get(0).getValue(), 1e-9);
        assertEquals(14.0, (Double) plan.get(1).getValue(), 1e-9);
    }

    // ------------------------------------------------------------------ DST

    @Test
    public void weekShiftKeepsLocalTimeAcrossDst() {
        // source in winter time, target in summer time (2026-03-29 DST starts): local time stays
        Instant source = berlin(2026, 3, 23, 10, 0);
        Instant target = ReplayPlanner.target(source, Offset.weeks(1));
        assertEquals(LocalDateTime.of(2026, 3, 30, 10, 0), local(target).toLocalDateTime());
        assertEquals(Duration.ofHours(7 * 24 - 1), Duration.between(source, target));
    }

    @Test
    public void onlyRisingValuesLookLikeACounter() {
        Instant t = berlin(2025, 11, 11, 0, 0);
        List<Sample> rising = new ArrayList<>();
        List<Sample> interval = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            Instant ts = t.plus(Duration.ofMinutes(15L * i));
            rising.add(new Sample(ts, 1000.0 + i * 0.5 - (i == 100 ? 999.0 : 0))); // one meter change is allowed
            interval.add(new Sample(ts, i % 3 == 0 ? 0.05 : 0.07));               // consumption per interval
        }
        assertTrue(ReplayPlanner.looksLikeCounter(rising));
        assertFalse(ReplayPlanner.looksLikeCounter(interval));
        assertFalse(ReplayPlanner.looksLikeCounter(Collections.singletonList(new Sample(t, "text"))));
    }

    @Test
    public void springGapSamplesAreSkipped() {
        // 2026-03-29 02:00-03:00 does not exist in Berlin; source one week earlier has samples there
        Offset offset = Offset.weeks(1);
        assertNull(ReplayPlanner.target(berlin(2026, 3, 22, 2, 30), offset));

        List<Sample> source = series(berlin(2026, 3, 22, 0, 0), berlin(2026, 3, 22, 5, 0), Duration.ofMinutes(15));
        List<Sample> plan = ReplayPlanner.plan(source, berlin(2026, 3, 28, 23, 45), 0.0, offset,
                berlin(2026, 3, 29, 5, 0), false);
        // 21 source samples, 4 fall into the gap
        assertEquals(17, plan.size());
        for (Sample s : plan) assertNotEquals(2, local(s.getTimestamp()).getHour());
    }

    @Test
    public void autumnOverlapDoesNotProduceDuplicates() {
        // 2026-10-25 02:00-03:00 occurs twice in Berlin; source one week later has a normal hour there
        Offset offset = Offset.weeks(52);
        List<Sample> source = series(berlin(2025, 10, 26, 0, 0), berlin(2025, 10, 26, 5, 0), Duration.ofMinutes(15));
        // 2025-10-26 is itself a DST change day; 52 weeks later is 2026-10-25, also a DST change day
        List<Sample> plan = ReplayPlanner.plan(source, berlin(2026, 10, 24, 23, 45), 0.0, offset,
                berlin(2026, 10, 25, 6, 0), false);
        Set<Instant> unique = new HashSet<>();
        for (Sample s : plan) assertTrue(unique.add(s.getTimestamp()));
        assertEquals(source.size(), plan.size()); // both change days align, every sample keeps its offset

        // source on a normal day, target in the overlap: the doubled hour gets one copy only
        Offset week = Offset.weeks(1);
        List<Sample> normal = series(berlin(2026, 10, 18, 0, 0), berlin(2026, 10, 18, 5, 0), Duration.ofMinutes(15));
        List<Sample> plan2 = ReplayPlanner.plan(normal, berlin(2026, 10, 24, 23, 45), 0.0, week,
                berlin(2026, 10, 25, 6, 0), false);
        Set<Instant> unique2 = new HashSet<>();
        for (Sample s : plan2) assertTrue(unique2.add(s.getTimestamp()));
        assertEquals(normal.size(), plan2.size());
    }

    // ------------------------------------------------------------------ calendar rows

    @Test
    public void monthlyRowReplaysByCalendarMonths() {
        // monthly values on the 1st at 00:00 Berlin, history Nov 2025 .. Oct 2026 (11 months span)
        List<Sample> source = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            ZonedDateTime t = LocalDateTime.of(2025, 11, 1, 0, 0).plusMonths(i).atZone(ReplayPlanner.ZONE);
            source.add(new Sample(t.toInstant(), 100.0 + i));
        }
        Instant first = source.get(0).getTimestamp();
        Instant last = source.get(source.size() - 1).getTimestamp(); // 2026-10-01
        assertTrue(ReplayPlanner.isCalendarPeriod("P1M", null));
        Offset offset = ReplayPlanner.offset(first, last, true);
        assertEquals(Offset.months(11), offset);

        Instant now = berlin(2027, 3, 15, 12, 0);
        Instant end = ReplayPlanner.targetEnd(last, now, offset);
        assertEquals(berlin(2027, 1, 1, 0, 0), end); // at most 3 months per cycle

        List<Sample> plan = ReplayPlanner.plan(source, last, 111.0, offset, end, false);
        assertEquals(3, plan.size());
        assertEquals(berlin(2026, 11, 1, 0, 0), plan.get(0).getTimestamp());
        assertEquals(101.0, (Double) plan.get(0).getValue(), 1e-9); // Dec 2025 + 11 months
        assertEquals(berlin(2026, 12, 1, 0, 0), plan.get(1).getTimestamp());
        assertEquals(berlin(2027, 1, 1, 0, 0), plan.get(2).getTimestamp());
        for (Sample s : plan) {
            assertEquals(1, local(s.getTimestamp()).getDayOfMonth());
            assertEquals(0, local(s.getTimestamp()).getHour()); // summer and winter months: local midnight
        }
    }

    @Test
    public void monthEndIsClamped() {
        Instant target = ReplayPlanner.target(berlin(2026, 1, 31, 0, 0), Offset.months(1));
        assertEquals(LocalDateTime.of(2026, 2, 28, 0, 0), local(target).toLocalDateTime());

        // Jan 30 and Jan 31 both land on Feb 28: only one copy
        List<Sample> source = Arrays.asList(new Sample(berlin(2026, 1, 30, 0, 0), 1.0),
                new Sample(berlin(2026, 1, 31, 0, 0), 2.0));
        List<Sample> plan = ReplayPlanner.plan(source, berlin(2026, 2, 1, 0, 0), 0.0, Offset.months(1),
                berlin(2026, 3, 1, 0, 0), false);
        assertEquals(1, plan.size());
    }

    @Test
    public void yearlyRowReplaysByTwelveMonths() {
        List<Sample> source = Arrays.asList(
                new Sample(berlin(2023, 1, 1, 0, 0), 10.0),
                new Sample(berlin(2024, 1, 1, 0, 0), 11.0),
                new Sample(berlin(2025, 1, 1, 0, 0), 12.0),
                new Sample(berlin(2026, 1, 1, 0, 0), 13.0));
        assertTrue(ReplayPlanner.isCalendarPeriod("P1Y", null));
        Offset offset = ReplayPlanner.offset(source.get(0).getTimestamp(), source.get(3).getTimestamp(), true);
        assertEquals(Offset.months(12), offset);

        Instant last = source.get(3).getTimestamp();
        Instant now = berlin(2026, 11, 11, 0, 0);
        // 3 months catch-up limit: nothing due before 2027-01-01
        assertEquals(berlin(2026, 4, 1, 0, 0), ReplayPlanner.targetEnd(last, now, offset));
        assertTrue(ReplayPlanner.plan(source, last, 13.0, offset, ReplayPlanner.targetEnd(last, now, offset), false).isEmpty());

        // the job then looks up to now; the next yearly value appears on 2027-01-01
        assertTrue(ReplayPlanner.plan(source, last, 13.0, offset, now, false).isEmpty());
        Instant later = berlin(2027, 1, 2, 0, 0);
        List<Sample> plan = ReplayPlanner.plan(source, last, 13.0, offset, later, false);
        assertEquals(1, plan.size());
        assertEquals(berlin(2027, 1, 1, 0, 0), plan.get(0).getTimestamp());
        assertEquals(13.0, (Double) plan.get(0).getValue(), 1e-9);
    }

    @Test
    public void catchUpLimitCountsFromTheFirstCopy() {
        // the source has a gap: nothing within 8 weeks after the last sample, then daily values
        Offset offset = Offset.weeks(52);
        Instant last = berlin(2026, 3, 1, 0, 0);
        Instant now = berlin(2026, 11, 11, 0, 0);
        List<Sample> source = series(berlin(2025, 6, 1, 0, 0), berlin(2025, 11, 30, 0, 0), Duration.ofDays(1));
        assertTrue(ReplayPlanner.plan(source, last, 0.0, offset, ReplayPlanner.targetEnd(last, now, offset), false).isEmpty());
        List<Sample> plan = ReplayPlanner.plan(source, last, 0.0, offset, now, false);
        assertEquals(57, plan.size()); // first copy plus 8 weeks
        assertEquals(local(plan.get(0).getTimestamp()).plusWeeks(8).toInstant(), plan.get(plan.size() - 1).getTimestamp());
    }
}
