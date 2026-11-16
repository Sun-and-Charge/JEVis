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

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.reading.RoundMailInfo;
import org.jevis.commons.reading.RoundMailLogic;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Demo reading rounds send no mails: the marks of {@link RoundMailMarker} leave the JENotifier's
 * {@link RoundMailLogic} nothing to send on any day.
 */
public class RoundMailMarkerTest {

    private static final LocalDate START = new LocalDate(2026, 1, 1);
    private static final Period MONTH = Period.months(1);

    @Test
    public void scheduleIsTheWebServicesOne() {
        RoundMailMarker.Schedule s = RoundMailMarker.schedule(START, MONTH, new LocalDate(2026, 11, 16));
        assertEquals(new LocalDate(2026, 11, 1), s.due);
        assertEquals(new LocalDate(2026, 12, 1), s.nextDue);
        s = RoundMailMarker.schedule(START, MONTH, new LocalDate(2026, 12, 1));
        assertEquals(new LocalDate(2026, 12, 1), s.due);
        s = RoundMailMarker.schedule(START, MONTH, new LocalDate(2025, 12, 31));
        assertNull("before the start", s.due);
        assertEquals(START, s.nextDue);
        /* counted from the start: a round on the 31st stays on month ends */
        s = RoundMailMarker.schedule(new LocalDate(2026, 1, 31), MONTH, new LocalDate(2026, 3, 30));
        assertEquals(new LocalDate(2026, 2, 28), s.due);
        assertEquals(new LocalDate(2026, 3, 31), s.nextDue);
        assertNull(RoundMailMarker.schedule(null, MONTH, START));
    }

    @Test
    public void marksMoveToTheNextDueDateWhenTheirKindCanNoLongerApply() {
        /* due 2026-11-01, reminder 3, escalation 7 */
        assertArrayEquals(new String[]{"2026-11-01", "2026-11-01", "2026-11-01"}, marks(2026, 11, 1));
        assertArrayEquals(new String[]{"2026-11-01", "2026-11-01", "2026-11-01"}, marks(2026, 11, 3));
        assertArrayEquals(new String[]{"2026-12-01", "2026-11-01", "2026-11-01"}, marks(2026, 11, 4));
        assertArrayEquals(new String[]{"2026-12-01", "2026-12-01", "2026-11-01"}, marks(2026, 11, 8));
        assertArrayEquals(new String[]{"2026-12-01", "2026-12-01", "2026-11-01"}, marks(2026, 11, 30));
        assertArrayEquals("before the start every mark is the start date", new String[]{"2026-01-01", "2026-01-01", "2026-01-01"},
                marks(2025, 12, 20));
    }

    private static String[] marks(int y, int m, int d) {
        return RoundMailMarker.marks(START, MONTH, 3, 7, new LocalDate(y, m, d));
    }

    /**
     * Every day of two years: after the job ran, the JENotifier finds no pending mail; and also on the next day before
     * the job ran again (e.g. the first day of the next period), as long as reminder and escalation come at least one
     * day after the due date and the earlier of them within the period (the demo rounds: 3 / 7 days, monthly).
     */
    @Test
    public void theNotifierNeverFindsAPendingMail() {
        int[][] days = {{3, 7}, {0, 7}, {7, 0}, {3, 3}, {10, 7}, {1, 1}, {6, 30}};
        String[] periods = {"P1M", "P1W", "P2W", "P3M", "P1Y"};
        int[] shortest = {28, 7, 14, 89, 365};
        int nextDayChecks = 0;
        for (int i = 0; i < periods.length; i++) {
            String p = periods[i];
            for (int[] rd : days) {
                int first = Math.min(rd[0], rd[1]);
                boolean inAdvance = first >= 1 && first < shortest[i];
                LocalDate start = new LocalDate(2026, 1, 31);
                String state = null;
                for (LocalDate today = new LocalDate(2025, 12, 1); today.isBefore(new LocalDate(2028, 1, 1)); today = today.plusDays(1)) {
                    String updated = RoundMailMarker.update(state, start.toString(), p, String.valueOf(rd[0]), String.valueOf(rd[1]), today);
                    if (updated != null) state = updated;
                    String what = p + " " + rd[0] + "/" + rd[1] + " " + today;
                    assertNull(what, pending(start, p, rd, state, today));
                    if (inAdvance) {
                        nextDayChecks++;
                        assertNull(what + " (next day, before the job ran)", pending(start, p, rd, state, today.plusDays(1)));
                    }
                }
            }
        }
        assertTrue(nextDayChecks > 10000);
    }

    private static RoundMailLogic.MailKind pending(LocalDate start, String period, int[] rd, String stateJson, LocalDate today) {
        RoundMailMarker.Schedule s = RoundMailMarker.schedule(start, RoundMailMarker.period(period), today);
        RoundMailInfo round = new RoundMailInfo();
        round.enabled = true;
        round.due = s.due;
        round.state = s.due == null ? "NOT_STARTED" : "OPEN";
        round.reminderDays = rd[0];
        round.escalationDays = rd[1];
        return RoundMailLogic.pendingMail(round, EntryFormat.fromJson(stateJson, MailState.class), today);
    }

    @Test
    public void updateClearsTheLastMailKeepsTheErrorAndIsNullWhenUnchanged() {
        LocalDate today = new LocalDate(2026, 11, 16);
        String json = RoundMailMarker.update("{\"dueMailSentFor\":\"2026-11-01\",\"lastMail\":\"2026-11-01T06:00:00\","
                + "\"lastMailKind\":\"ORDER\",\"lastError\":\"x\"}", "2026-01-01", "P1M", "3", "7", today);
        MailState state = EntryFormat.fromJson(json, MailState.class);
        assertEquals("2026-12-01", state.getDueMailSentFor());
        assertEquals("2026-12-01", state.getReminderSentFor());
        assertEquals("2026-11-01", state.getEscalationSentFor());
        assertNull(state.getLastMail());
        assertNull(state.getLastMailKind());
        assertEquals("x", state.getLastError());
        assertNull("unchanged: nothing to write", RoundMailMarker.update(json, "2026-01-01", "P1M", "3", "7", today));
        assertNull("other order of the fields", RoundMailMarker.update("{\"escalationSentFor\":\"2026-11-01\",\"lastError\":\"x\","
                + "\"reminderSentFor\":\"2026-12-01\",\"dueMailSentFor\":\"2026-12-01\"}", "2026-01-01", "P1M", "3", "7", today));
        assertNotNull("empty days: the web service's defaults 3 / 7", RoundMailMarker.update(null, "2026-01-01", "P1M", "", null, today));
        assertEquals(json.replace(",\"lastError\":\"x\"", ""), RoundMailMarker.update("not json", "2026-01-01", "P1M", "3", "7", today));
    }

    @Test
    public void roundsWithoutScheduleAreLeftAlone() {
        LocalDate today = new LocalDate(2026, 11, 16);
        assertNull(RoundMailMarker.update(null, null, "P1M", "3", "7", today));
        assertNull(RoundMailMarker.update(null, "2026-1-1", "P1M", "3", "7", today));
        assertNull(RoundMailMarker.update(null, "2026-01-01", "P0M", "3", "7", today));
        assertNull(RoundMailMarker.update(null, "2026-01-01", "monthly", "3", "7", today));
    }
}
