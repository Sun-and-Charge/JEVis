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
import org.jevis.commons.appointment.MailState;
import org.joda.time.LocalDateTime;

/**
 * Mail rules of maintenance messages (upstream issue #2663).
 * <p>
 * The Control Center has no SMTP credentials. "Inform" stores a request timestamp in the entry; setting the status to
 * done stores a done timestamp. The JENotifier service sends one mail per timestamp on its next cycle:
 * <ul>
 * <li>"finished" mail: status done, not yet sent for this done timestamp, and the users were informed (or an inform
 * was requested) before. A pending inform request is then dropped, the finished mail contains everything.</li>
 * <li>inform mail: a request timestamp that has not been handled yet.</li>
 * </ul>
 */
public final class MaintenanceLogic {

    public enum MailKind {INFORM, DONE}

    private MaintenanceLogic() {
    }

    /**
     * Requests an inform mail; each call leads to one mail.
     */
    public static void requestInform(MaintenanceEntry entry, LocalDateTime now) {
        entry.setInformRequested(EntryFormat.formatTimestamp(now));
    }

    /**
     * Sets the status and keeps the done timestamp consistent: set when switching to done, cleared when reopened.
     */
    public static void changeStatus(MaintenanceEntry entry, MaintenanceEntry.Status status, LocalDateTime now) {
        MaintenanceEntry.Status old = entry.getStatus();
        entry.setStatus(status);
        if (status == MaintenanceEntry.Status.DONE && (old != MaintenanceEntry.Status.DONE || entry.getDoneAt() == null)) {
            entry.setDoneAt(EntryFormat.formatTimestamp(now));
        } else if (status != MaintenanceEntry.Status.DONE) {
            entry.setDoneAt(null);
        }
    }

    /**
     * @return true if an inform mail was requested and not handled yet
     */
    public static boolean isInformPending(MaintenanceEntry entry, MailState state) {
        return entry.getInformRequested() != null
                && (state == null || !entry.getInformRequested().equals(state.getInformSentFor()));
    }

    /**
     * @return the mail that is pending, or null
     */
    public static MailKind pendingMail(MaintenanceEntry entry, MailState state) {
        if (entry == null) {
            return null;
        }
        MailState mailState = state != null ? state : new MailState();
        if (entry.getStatus() == MaintenanceEntry.Status.DONE && entry.getDoneAt() != null
                && !entry.getDoneAt().equals(mailState.getDoneMailSentFor())
                && (mailState.getInformSentFor() != null || entry.getInformRequested() != null)) {
            return MailKind.DONE;
        }
        if (isInformPending(entry, mailState)) {
            return MailKind.INFORM;
        }
        return null;
    }

    public static void markSent(MailKind kind, MaintenanceEntry entry, MailState state, LocalDateTime now) {
        if (kind == MailKind.DONE) {
            state.setDoneMailSentFor(entry.getDoneAt());
        }
        if (entry.getInformRequested() != null) {
            state.setInformSentFor(entry.getInformRequested());
        }
        state.setLastMail(EntryFormat.formatTimestamp(now));
        state.setLastMailKind(kind.name());
        state.setLastError(null);
    }
}
