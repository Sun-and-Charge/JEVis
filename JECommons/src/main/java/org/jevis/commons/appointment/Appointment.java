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

import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * An appointment with a due date, an optional recurrence and a reminder (upstream issue #1855), stored as JSON in the
 * "Data" attribute of an object of class {@value #CLASS_NAME}.
 */
public class Appointment {

    public static final String CLASS_NAME = "Appointment";
    public static final String DIRECTORY_CLASS_NAME = "Appointment Directory";
    public static final String PLUGIN_CLASS_NAME = "Appointment Plugin";

    public enum Recurrence {
        NONE(0), MONTHLY(1), QUARTERLY(3), YEARLY(12);

        private final int months;

        Recurrence(int months) {
            this.months = months;
        }

        public int getMonths() {
            return months;
        }
    }

    public enum Status {OPEN, DONE}

    private String title;
    private String description;
    private String due;
    private Recurrence recurrence;
    /**
     * Day of month of a monthly/quarterly/yearly series, so that e.g. the 31st comes back after a short month.
     */
    private Integer anchorDay;
    private Integer reminderDays;
    private List<Long> recipients;
    private Long linkedObject;
    private Status status;
    private String lastDone;
    private String created;
    private String createdBy;

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDue() {
        return due;
    }

    public void setDue(String due) {
        this.due = due;
    }

    public LocalDateTime getDueDate() {
        return EntryFormat.parse(due);
    }

    public void setDueDate(LocalDateTime due) {
        this.due = EntryFormat.format(due);
    }

    public Recurrence getRecurrence() {
        return recurrence != null ? recurrence : Recurrence.NONE;
    }

    public void setRecurrence(Recurrence recurrence) {
        this.recurrence = recurrence;
    }

    public Integer getAnchorDay() {
        return anchorDay;
    }

    public void setAnchorDay(Integer anchorDay) {
        this.anchorDay = anchorDay;
    }

    public int getReminderDays() {
        return reminderDays != null ? Math.max(0, reminderDays) : 0;
    }

    public void setReminderDays(int reminderDays) {
        this.reminderDays = reminderDays;
    }

    public List<Long> getRecipients() {
        if (recipients == null) {
            recipients = new ArrayList<>();
        }
        return recipients;
    }

    public void setRecipients(List<Long> recipients) {
        this.recipients = recipients;
    }

    public Long getLinkedObject() {
        return linkedObject;
    }

    public void setLinkedObject(Long linkedObject) {
        this.linkedObject = linkedObject;
    }

    public Status getStatus() {
        return status != null ? status : Status.OPEN;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getLastDone() {
        return lastDone;
    }

    public void setLastDone(String lastDone) {
        this.lastDone = lastDone;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
}
