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

import java.util.Objects;

/**
 * Mail bookkeeping of an appointment or maintenance message, stored as JSON in the "Mail State" attribute. Only the
 * JENotifier service writes it.
 * <p>
 * The "...For" fields hold the key the mail was sent for (the due date of an appointment, the request/done timestamp of
 * a maintenance message), so each mail goes out once per key.
 */
public class MailState {

    /**
     * Appointment: due date for which the reminder was sent.
     */
    private String reminderSentFor;
    /**
     * Appointment: due date for which the due/overdue mail was sent.
     */
    private String dueMailSentFor;
    /**
     * Maintenance: inform request timestamp that was handled.
     */
    private String informSentFor;
    /**
     * Maintenance: done timestamp for which the "finished" mail was sent.
     */
    private String doneMailSentFor;
    /**
     * Timestamp and kind of the last mail sent, for display.
     */
    private String lastMail;
    private String lastMailKind;
    /**
     * Last error (no recipients, SMTP failure); cleared after a successful mail.
     */
    private String lastError;

    public String getReminderSentFor() {
        return reminderSentFor;
    }

    public void setReminderSentFor(String reminderSentFor) {
        this.reminderSentFor = reminderSentFor;
    }

    public String getDueMailSentFor() {
        return dueMailSentFor;
    }

    public void setDueMailSentFor(String dueMailSentFor) {
        this.dueMailSentFor = dueMailSentFor;
    }

    public String getInformSentFor() {
        return informSentFor;
    }

    public void setInformSentFor(String informSentFor) {
        this.informSentFor = informSentFor;
    }

    public String getDoneMailSentFor() {
        return doneMailSentFor;
    }

    public void setDoneMailSentFor(String doneMailSentFor) {
        this.doneMailSentFor = doneMailSentFor;
    }

    public String getLastMail() {
        return lastMail;
    }

    public void setLastMail(String lastMail) {
        this.lastMail = lastMail;
    }

    public String getLastMailKind() {
        return lastMailKind;
    }

    public void setLastMailKind(String lastMailKind) {
        this.lastMailKind = lastMailKind;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailState)) return false;
        MailState that = (MailState) o;
        return Objects.equals(reminderSentFor, that.reminderSentFor)
                && Objects.equals(dueMailSentFor, that.dueMailSentFor)
                && Objects.equals(informSentFor, that.informSentFor)
                && Objects.equals(doneMailSentFor, that.doneMailSentFor)
                && Objects.equals(lastMail, that.lastMail)
                && Objects.equals(lastMailKind, that.lastMailKind)
                && Objects.equals(lastError, that.lastError);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reminderSentFor, dueMailSentFor, informSentFor, doneMailSentFor, lastMail, lastMailKind, lastError);
    }
}
