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
package org.jevis.commons.maintenance;

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.mail.MailMessage;

/**
 * Plain-text inform and "finished" mails of maintenance messages, German first, then English.
 */
public final class MaintenanceMails {

    private static final String SEPARATOR = "----------------------------------------";

    private MaintenanceMails() {
    }

    public static String typeDe(MaintenanceEntry.Type type) {
        return type == MaintenanceEntry.Type.KNOWN_ISSUE ? "Bekanntes Problem" : "Geplante Wartung";
    }

    public static String typeEn(MaintenanceEntry.Type type) {
        return type == MaintenanceEntry.Type.KNOWN_ISSUE ? "Known issue" : "Planned maintenance";
    }

    public static String statusDe(MaintenanceEntry.Status status) {
        switch (status) {
            case IN_PROGRESS:
                return "In Bearbeitung";
            case DONE:
                return "Erledigt";
            default:
                return "Geplant";
        }
    }

    public static String statusEn(MaintenanceEntry.Status status) {
        switch (status) {
            case IN_PROGRESS:
                return "In progress";
            case DONE:
                return "Finished";
            default:
                return "Planned";
        }
    }

    /**
     * @return the mail without recipients
     */
    public static MailMessage build(MaintenanceLogic.MailKind kind, MaintenanceEntry entry) {
        boolean done = kind == MaintenanceLogic.MailKind.DONE;
        String start = EntryFormat.display(entry.getStartDate());
        String duration = EntryFormat.displayDuration(entry.getDurationMinutes());

        String subject;
        if (done) {
            subject = "Abgeschlossen / Finished: " + entry.getTitle();
        } else {
            subject = typeDe(entry.getType()) + " / " + typeEn(entry.getType()) + ": " + entry.getTitle();
            if (!start.isEmpty()) {
                subject += " (" + start + ")";
            }
        }

        StringBuilder text = new StringBuilder();
        text.append("Hallo,\n\n");
        if (done) {
            text.append(entry.getType() == MaintenanceEntry.Type.KNOWN_ISSUE
                    ? "das folgende bekannte Problem ist behoben."
                    : "die folgende Wartung ist abgeschlossen.");
        } else {
            text.append(entry.getType() == MaintenanceEntry.Type.KNOWN_ISSUE
                    ? "wir informieren Sie über ein bekanntes Problem."
                    : "wir informieren Sie über eine geplante Wartung.");
        }
        text.append("\n\n");
        text.append("Art: ").append(typeDe(entry.getType())).append('\n');
        text.append("Titel: ").append(entry.getTitle()).append('\n');
        if (!start.isEmpty()) {
            text.append("Beginn: ").append(start).append('\n');
        }
        if (!duration.isEmpty()) {
            text.append("Voraussichtliche Dauer: ").append(duration).append('\n');
        }
        text.append("Status: ").append(statusDe(entry.getStatus())).append('\n');
        if (!entry.getImpact().trim().isEmpty()) {
            text.append("Auswirkungen:\n").append(entry.getImpact().trim()).append('\n');
        }
        if (!entry.getDescription().trim().isEmpty()) {
            text.append("Beschreibung:\n").append(entry.getDescription().trim()).append('\n');
        }
        text.append("\nDiese Nachricht wurde automatisch von JEVis versendet.\n\n");
        text.append(SEPARATOR).append("\n\n");

        text.append("Hello,\n\n");
        if (done) {
            text.append(entry.getType() == MaintenanceEntry.Type.KNOWN_ISSUE
                    ? "the following known issue has been resolved."
                    : "the following maintenance has been finished.");
        } else {
            text.append(entry.getType() == MaintenanceEntry.Type.KNOWN_ISSUE
                    ? "we would like to inform you about a known issue."
                    : "we would like to inform you about a planned maintenance.");
        }
        text.append("\n\n");
        text.append("Type: ").append(typeEn(entry.getType())).append('\n');
        text.append("Title: ").append(entry.getTitle()).append('\n');
        if (!start.isEmpty()) {
            text.append("Start: ").append(start).append('\n');
        }
        if (!duration.isEmpty()) {
            text.append("Expected duration: ").append(duration).append('\n');
        }
        text.append("Status: ").append(statusEn(entry.getStatus())).append('\n');
        if (!entry.getImpact().trim().isEmpty()) {
            text.append("Impact:\n").append(entry.getImpact().trim()).append('\n');
        }
        if (!entry.getDescription().trim().isEmpty()) {
            text.append("Description:\n").append(entry.getDescription().trim()).append('\n');
        }
        text.append("\nThis message was sent automatically by JEVis.\n");

        return new MailMessage().subject(subject).text(text.toString(), false);
    }
}
