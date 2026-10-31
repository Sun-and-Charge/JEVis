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

import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

/**
 * Plain-text reminder and due mails of appointments, German first, then English.
 */
public final class AppointmentMails {

    static final String SEPARATOR = "----------------------------------------";

    private AppointmentMails() {
    }

    public static String recurrenceDe(Appointment.Recurrence recurrence) {
        switch (recurrence) {
            case MONTHLY:
                return "monatlich";
            case QUARTERLY:
                return "vierteljährlich";
            case YEARLY:
                return "jährlich";
            default:
                return "einmalig";
        }
    }

    public static String recurrenceEn(Appointment.Recurrence recurrence) {
        switch (recurrence) {
            case MONTHLY:
                return "monthly";
            case QUARTERLY:
                return "quarterly";
            case YEARLY:
                return "yearly";
            default:
                return "once";
        }
    }

    /**
     * @param linkedObjectName name of the linked JEVis object, or null
     * @param today            the current date, decides between "due today" and "overdue"
     * @return the mail without recipients
     */
    public static MailMessage build(AppointmentLogic.MailKind kind, Appointment appointment, String linkedObjectName, LocalDate today) {
        LocalDateTime due = appointment.getDueDate();
        String dueText = EntryFormat.display(due);
        String dueDate = due != null ? EntryFormat.displayDate(due.toLocalDate()) : "";
        boolean overdue = kind == AppointmentLogic.MailKind.DUE && due != null && due.toLocalDate().isBefore(today);

        String subject;
        String introDe;
        String introEn;
        if (kind == AppointmentLogic.MailKind.REMINDER) {
            subject = "Erinnerung / Reminder: " + appointment.getTitle() + " - fällig am / due on " + dueDate;
            introDe = "der folgende Termin ist am " + dueText + " fällig.";
            introEn = "the following appointment is due on " + dueText + ".";
        } else if (overdue) {
            subject = "Überfällig / Overdue: " + appointment.getTitle() + " - fällig seit / due since " + dueDate;
            introDe = "der folgende Termin ist seit " + dueText + " fällig und noch nicht erledigt.";
            introEn = "the following appointment has been due since " + dueText + " and is not done yet.";
        } else {
            subject = "Heute fällig / Due today: " + appointment.getTitle();
            introDe = "der folgende Termin ist heute fällig (" + dueText + ").";
            introEn = "the following appointment is due today (" + dueText + ").";
        }

        StringBuilder text = new StringBuilder();
        text.append("Hallo,\n\n").append(introDe).append("\n\n");
        text.append("Termin: ").append(appointment.getTitle()).append('\n');
        text.append("Fällig: ").append(dueText).append('\n');
        text.append("Wiederholung: ").append(recurrenceDe(appointment.getRecurrence())).append('\n');
        if (!EntryFormat.isBlank(linkedObjectName)) {
            text.append("Bezug: ").append(linkedObjectName).append('\n');
        }
        if (!EntryFormat.isBlank(appointment.getDescription())) {
            text.append("Beschreibung:\n").append(appointment.getDescription().trim()).append('\n');
        }
        text.append("\nBitte markieren Sie den Termin nach der Erledigung im JEVis Control Center (Modul \"Termine\") als erledigt.\n");
        text.append("Diese Nachricht wurde automatisch von JEVis versendet.\n\n");
        text.append(SEPARATOR).append("\n\n");
        text.append("Hello,\n\n").append(introEn).append("\n\n");
        text.append("Appointment: ").append(appointment.getTitle()).append('\n');
        text.append("Due: ").append(dueText).append('\n');
        text.append("Recurrence: ").append(recurrenceEn(appointment.getRecurrence())).append('\n');
        if (!EntryFormat.isBlank(linkedObjectName)) {
            text.append("Related to: ").append(linkedObjectName).append('\n');
        }
        if (!EntryFormat.isBlank(appointment.getDescription())) {
            text.append("Description:\n").append(appointment.getDescription().trim()).append('\n');
        }
        text.append("\nPlease mark the appointment as done in the JEVis Control Center (module \"Appointments\") once it is completed.\n");
        text.append("This message was sent automatically by JEVis.\n");

        return new MailMessage().subject(subject).text(text.toString(), false);
    }
}
