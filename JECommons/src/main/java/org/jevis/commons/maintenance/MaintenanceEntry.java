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
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * A planned maintenance or known issue that users are informed about (upstream issue #2663), stored as JSON in the
 * "Data" attribute of an object of class {@value #CLASS_NAME}.
 */
public class MaintenanceEntry {

    public static final String CLASS_NAME = "Maintenance Message";
    public static final String DIRECTORY_CLASS_NAME = "Maintenance Directory";
    public static final String PLUGIN_CLASS_NAME = "Maintenance Plugin";

    public enum Type {MAINTENANCE, KNOWN_ISSUE}

    public enum Status {PLANNED, IN_PROGRESS, DONE}

    private Type type;
    private String title;
    private String description;
    private String start;
    private Integer durationMinutes;
    private String impact;
    private Status status;
    private List<Long> recipients;
    /**
     * Timestamp of the last click on "Inform"; the JENotifier sends one mail per value.
     */
    private String informRequested;
    /**
     * Timestamp at which the status was set to done; the JENotifier sends one "finished" mail per value.
     */
    private String doneAt;
    private String created;
    private String createdBy;

    public Type getType() {
        return type != null ? type : Type.MAINTENANCE;
    }

    public void setType(Type type) {
        this.type = type;
    }

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

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public LocalDateTime getStartDate() {
        return EntryFormat.parse(start);
    }

    public void setStartDate(LocalDateTime start) {
        this.start = EntryFormat.format(start);
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getImpact() {
        return impact != null ? impact : "";
    }

    public void setImpact(String impact) {
        this.impact = impact;
    }

    public Status getStatus() {
        return status != null ? status : Status.PLANNED;
    }

    public void setStatus(Status status) {
        this.status = status;
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

    public String getInformRequested() {
        return informRequested;
    }

    public void setInformRequested(String informRequested) {
        this.informRequested = informRequested;
    }

    public String getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(String doneAt) {
        this.doneAt = doneAt;
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
