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
package org.jevis.commons.appointment;

import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

/**
 * Due dates, recurrence and reminder rules of appointments (upstream issue #1855).
 * <p>
 * An open appointment gets at most two mails per due date: a reminder from "reminder days" before the due date on,
 * and a due mail on the due date (or later, as overdue mail, if the service was not running). If the reminder window
 * was missed completely only the due mail is sent.
 */
public final class AppointmentLogic {

    public enum MailKind {REMINDER, DUE}

    private static final int MAX_STEPS = 1200;

    private AppointmentLogic() {
    }

    /**
     * @param anchorDay day of month of the series; null uses the day of {@code due}
     * @return the next due date after {@code due}, or null for {@link Appointment.Recurrence#NONE}
     */
    public static LocalDateTime nextDue(LocalDateTime due, Appointment.Recurrence recurrence, Integer anchorDay) {
        if (due == null || recurrence == null || recurrence == Appointment.Recurrence.NONE) {
            return null;
        }
        LocalDateTime next = due.plusMonths(recurrence.getMonths());
        int day = anchorDay != null && anchorDay > 0 ? anchorDay : due.getDayOfMonth();
        int max = next.dayOfMonth().getMaximumValue();
        return next.withDayOfMonth(Math.min(day, max));
    }

    /**
     * Marks the appointment as done. A recurring appointment stays open and moves to its next due date that lies after
     * today (an appointment overdue for several periods does not create overdue follow-ups).
     *
     * @return the new due date, or null if the appointment is not recurring and is now done
     */
    public static LocalDateTime complete(Appointment appointment, LocalDateTime now) {
        appointment.setLastDone(EntryFormat.formatTimestamp(now));
        LocalDateTime due = appointment.getDueDate();
        if (due == null || appointment.getRecurrence() == Appointment.Recurrence.NONE) {
            appointment.setStatus(Appointment.Status.DONE);
            return null;
        }
        Integer anchor = appointment.getAnchorDay() != null ? appointment.getAnchorDay() : due.getDayOfMonth();
        appointment.setAnchorDay(anchor);
        LocalDate today = now.toLocalDate();
        LocalDateTime next = nextDue(due, appointment.getRecurrence(), anchor);
        int steps = 0;
        while (!next.toLocalDate().isAfter(today) && steps++ < MAX_STEPS) {
            next = nextDue(next, appointment.getRecurrence(), anchor);
        }
        appointment.setDueDate(next);
        appointment.setStatus(Appointment.Status.OPEN);
        return next;
    }

    /**
     * @return the first day on which the reminder is sent, or null if no reminder is configured
     */
    public static LocalDate reminderDay(Appointment appointment) {
        LocalDateTime due = appointment.getDueDate();
        if (due == null || appointment.getReminderDays() <= 0) {
            return null;
        }
        return due.toLocalDate().minusDays(appointment.getReminderDays());
    }

    /**
     * @return the mail that is due at {@code now}, or null
     */
    public static MailKind pendingMail(Appointment appointment, MailState state, LocalDateTime now) {
        if (appointment == null || appointment.getStatus() != Appointment.Status.OPEN) {
            return null;
        }
        LocalDateTime due = appointment.getDueDate();
        if (due == null) {
            return null;
        }
        MailState mailState = state != null ? state : new MailState();
        String key = EntryFormat.format(due);
        LocalDate today = now.toLocalDate();
        if (!today.isBefore(due.toLocalDate())) {
            return key.equals(mailState.getDueMailSentFor()) ? null : MailKind.DUE;
        }
        LocalDate reminderDay = reminderDay(appointment);
        if (reminderDay != null && !today.isBefore(reminderDay) && !key.equals(mailState.getReminderSentFor())) {
            return MailKind.REMINDER;
        }
        return null;
    }

    /**
     * Records that the mail was sent. A due mail also counts as reminder for the same due date.
     */
    public static void markSent(MailKind kind, Appointment appointment, MailState state, LocalDateTime now) {
        String key = EntryFormat.format(appointment.getDueDate());
        if (kind == MailKind.DUE) {
            state.setDueMailSentFor(key);
        }
        state.setReminderSentFor(key);
        state.setLastMail(EntryFormat.formatTimestamp(now));
        state.setLastMailKind(kind.name());
        state.setLastError(null);
    }

    /**
     * @return true if the appointment is open and its due day is before today
     */
    public static boolean isOverdue(Appointment appointment, LocalDate today) {
        LocalDateTime due = appointment.getDueDate();
        return appointment.getStatus() == Appointment.Status.OPEN && due != null && due.toLocalDate().isBefore(today);
    }

    /**
     * @return true if the appointment is open, not overdue and its reminder period has started
     */
    public static boolean isDueSoon(Appointment appointment, LocalDate today) {
        LocalDateTime due = appointment.getDueDate();
        if (appointment.getStatus() != Appointment.Status.OPEN || due == null || due.toLocalDate().isBefore(today)) {
            return false;
        }
        LocalDate reminderDay = reminderDay(appointment);
        LocalDate from = reminderDay != null ? reminderDay : due.toLocalDate();
        return !today.isBefore(from);
    }
}
