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

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which mail a reading round needs (stage E8.3, docs/WEB-API.md): the order on the due date, a reminder
 * "Reminder Days" after it and an escalation to the supervisor "Escalation Days" after it, each once per due date.
 * <p>
 * At most one mail per round and cycle: when several kinds apply at once (a round created after its due date, a
 * stopped service) only the latest kind is sent and the earlier ones count as sent for that due date.
 */
public final class RoundMailLogic {

    /**
     * In the order of escalation; a kind counts as sent when a later kind was sent for the same due date.
     */
    public enum MailKind {ORDER, REMINDER, ESCALATION}

    /**
     * States of rounds that never get a mail.
     */
    static final Set<String> NO_MAIL_STATES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "INACTIVE", "NOT_STARTED", "DONE")));

    private RoundMailLogic() {
    }

    /**
     * @return the mail key of a due date ({@code yyyy-MM-dd})
     */
    public static String key(LocalDate due) {
        return due != null ? due.toString() : null;
    }

    /**
     * @return the latest kind whose day has come for the round's due date, or null (no due date, before it, or a
     * round in a state without mails)
     */
    public static MailKind latestApplicable(RoundMailInfo round, LocalDate today) {
        if (round == null || !round.enabled || round.due == null || today == null
                || round.state == null || NO_MAIL_STATES.contains(round.state)) {
            return null;
        }
        LocalDate due = round.due;
        if (!today.isBefore(due.plusDays(Math.max(0, round.escalationDays)))) return MailKind.ESCALATION;
        if (!today.isBefore(due.plusDays(Math.max(0, round.reminderDays)))) return MailKind.REMINDER;
        if (!today.isBefore(due)) return MailKind.ORDER;
        return null;
    }

    /**
     * @return the mail to send now, or null if none is due or the latest applicable kind was already sent (or
     * counted as sent) for the round's due date
     */
    public static MailKind pendingMail(RoundMailInfo round, MailState state, LocalDate today) {
        MailKind kind = latestApplicable(round, today);
        if (kind == null) return null;
        MailState mailState = state != null ? state : new MailState();
        return isSent(kind, mailState, key(round.due)) ? null : kind;
    }

    /**
     * @return whether the kind was sent for the key (a later kind sent counts, as it marks the earlier ones)
     */
    public static boolean isSent(MailKind kind, MailState state, String key) {
        if (key == null || state == null) return false;
        switch (kind) {
            case ORDER:
                return key.equals(state.getDueMailSentFor());
            case REMINDER:
                return key.equals(state.getReminderSentFor());
            default:
                return key.equals(state.getEscalationSentFor());
        }
    }

    /**
     * Records that the mail was sent for the due date: the kind and all earlier kinds, the time and kind of the last
     * mail; clears the last error.
     */
    public static void markSent(MailKind kind, LocalDate due, MailState state, LocalDateTime now) {
        markHandled(kind, due, state);
        state.setLastMail(EntryFormat.formatTimestamp(now));
        state.setLastMailKind(kind.name());
        state.setLastError(null);
    }

    /**
     * Records the kind and all earlier kinds as handled for the due date without a mail (e.g. an escalation of a
     * round without supervisor); the last mail stays as it is.
     */
    public static void markHandled(MailKind kind, LocalDate due, MailState state) {
        String key = key(due);
        state.setDueMailSentFor(key);
        if (kind.ordinal() >= MailKind.REMINDER.ordinal()) state.setReminderSentFor(key);
        if (kind == MailKind.ESCALATION) state.setEscalationSentFor(key);
    }
}
