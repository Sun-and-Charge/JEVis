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
package org.jevis.commons.reading;

import org.jevis.commons.appointment.MailState;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Test;

import static org.jevis.commons.reading.RoundMailLogic.MailKind.*;
import static org.junit.Assert.*;

public class RoundMailLogicTest {

    private static final LocalDate DUE = new LocalDate(2026, 11, 1);

    private static RoundMailInfo round(String state) {
        RoundMailInfo round = new RoundMailInfo();
        round.id = 7001;
        round.name = "Monatsablesung";
        round.enabled = true;
        round.due = DUE;
        round.state = state;
        round.reminderDays = 3;
        round.escalationDays = 7;
        round.total = 5;
        round.done = 2;
        return round;
    }

    @Test
    public void noMailBeforeDueOrInStatesWithoutMails() {
        assertNull(RoundMailLogic.pendingMail(round("OPEN"), null, DUE.minusDays(1)));
        for (String state : new String[]{"INACTIVE", "NOT_STARTED", "DONE"}) {
            assertNull(state, RoundMailLogic.pendingMail(round(state), null, DUE.plusDays(10)));
        }
        RoundMailInfo disabled = round("OPEN");
        disabled.enabled = false;
        assertNull(RoundMailLogic.pendingMail(disabled, null, DUE));
        RoundMailInfo noDue = round("OPEN");
        noDue.due = null;
        assertNull(RoundMailLogic.pendingMail(noDue, null, DUE));
        assertNull(RoundMailLogic.pendingMail(null, null, DUE));
    }

    @Test
    public void kindsByDay() {
        assertEquals(ORDER, RoundMailLogic.pendingMail(round("OPEN"), null, DUE));
        assertEquals(ORDER, RoundMailLogic.pendingMail(round("OPEN"), null, DUE.plusDays(2)));
        assertEquals(REMINDER, RoundMailLogic.pendingMail(round("LATE"), null, DUE.plusDays(3)));
        assertEquals(ESCALATION, RoundMailLogic.pendingMail(round("ESCALATED"), null, DUE.plusDays(7)));
    }

    @Test
    public void eachKindOncePerDue() {
        MailState state = new MailState();
        RoundMailLogic.markSent(ORDER, DUE, state, new LocalDateTime(2026, 11, 1, 6, 0));
        assertEquals("2026-11-01", state.getDueMailSentFor());
        assertNull(state.getReminderSentFor());
        assertEquals("ORDER", state.getLastMailKind());
        assertEquals("2026-11-01T06:00:00", state.getLastMail());
        assertNull(RoundMailLogic.pendingMail(round("OPEN"), state, DUE.plusDays(1)));
        assertEquals(REMINDER, RoundMailLogic.pendingMail(round("LATE"), state, DUE.plusDays(3)));

        RoundMailLogic.markSent(REMINDER, DUE, state, new LocalDateTime(2026, 11, 4, 6, 0));
        assertNull(RoundMailLogic.pendingMail(round("LATE"), state, DUE.plusDays(4)));
        assertEquals(ESCALATION, RoundMailLogic.pendingMail(round("ESCALATED"), state, DUE.plusDays(8)));

        RoundMailLogic.markSent(ESCALATION, DUE, state, new LocalDateTime(2026, 11, 9, 6, 0));
        assertNull(RoundMailLogic.pendingMail(round("ESCALATED"), state, DUE.plusDays(20)));

        /* the next due date starts again with the order */
        RoundMailInfo next = round("OPEN");
        next.due = new LocalDate(2026, 12, 1);
        assertEquals(ORDER, RoundMailLogic.pendingMail(next, state, next.due));
    }

    @Test
    public void lateStartSendsOnlyTheLatestKindAndMarksTheEarlierOnes() {
        MailState state = new MailState();
        assertEquals(ESCALATION, RoundMailLogic.pendingMail(round("ESCALATED"), state, DUE.plusDays(9)));
        RoundMailLogic.markSent(ESCALATION, DUE, state, new LocalDateTime(2026, 11, 10, 6, 0));
        assertEquals("2026-11-01", state.getDueMailSentFor());
        assertEquals("2026-11-01", state.getReminderSentFor());
        assertEquals("2026-11-01", state.getEscalationSentFor());

        MailState late = new MailState();
        RoundMailLogic.markSent(REMINDER, DUE, late, new LocalDateTime(2026, 11, 5, 6, 0));
        assertEquals("2026-11-01", late.getDueMailSentFor());
        assertNull(late.getEscalationSentFor());
    }

    @Test
    public void reminderAfterEscalationIsNotSent() {
        RoundMailInfo round = round("ESCALATED");
        round.reminderDays = 10;
        round.escalationDays = 5;
        MailState state = new MailState();
        assertEquals(ESCALATION, RoundMailLogic.pendingMail(round, state, DUE.plusDays(5)));
        RoundMailLogic.markSent(ESCALATION, DUE, state, new LocalDateTime(2026, 11, 6, 6, 0));
        assertNull(RoundMailLogic.pendingMail(round, state, DUE.plusDays(12)));
    }

    @Test
    public void markHandledKeepsTheLastMail() {
        MailState state = new MailState();
        RoundMailLogic.markSent(REMINDER, DUE, state, new LocalDateTime(2026, 11, 4, 6, 0));
        state.setLastError("x");
        RoundMailLogic.markHandled(ESCALATION, DUE, state);
        assertEquals("2026-11-01", state.getEscalationSentFor());
        assertEquals("REMINDER", state.getLastMailKind());
        assertEquals("x", state.getLastError());
    }

    @Test
    public void markSentClearsTheError() {
        MailState state = new MailState();
        state.setLastError("No recipients with an e-mail address");
        RoundMailLogic.markSent(ORDER, DUE, state, new LocalDateTime(2026, 11, 1, 6, 0));
        assertNull(state.getLastError());
    }
}
