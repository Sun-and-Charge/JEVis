/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bookkeeping of one certificate in its attribute {@value EnergyCertificate#ATTRIBUTE_TRANSFER_STATE}, written by the
 * JENotifier: reminders sent (months before expiry, for the valid-until date they were sent for) and transfers per
 * target.
 */
public class CertificateState {

    private static final Gson GSON = new Gson();

    /** the valid-until date the reminders were sent for; a new date (renewed certificate) starts again */
    private String remindersFor;
    private List<Integer> remindersSent;
    private String lastReminder;
    private String lastError;
    /** transfer target id → time and certificate version of the last transfer */
    private Map<String, Transfer> transfers;

    public static class Transfer {
        private String at;
        private String version;

        public Transfer() {
        }

        public Transfer(String at, String version) {
            this.at = at;
            this.version = version;
        }

        public String getAt() {
            return at;
        }

        public String getVersion() {
            return version;
        }
    }

    public static CertificateState fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return new CertificateState();
        try {
            CertificateState state = GSON.fromJson(json, CertificateState.class);
            return state != null ? state : new CertificateState();
        } catch (Exception e) {
            return new CertificateState();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** @return the months already reminded for this valid-until date (empty after a renewal) */
    public List<Integer> sentFor(String validUntil) {
        if (validUntil == null || !validUntil.equals(remindersFor) || remindersSent == null) return new ArrayList<>();
        return remindersSent;
    }

    public void markSent(String validUntil, int months, String at) {
        if (validUntil == null || !validUntil.equals(remindersFor)) {
            remindersFor = validUntil;
            remindersSent = new ArrayList<>();
        }
        if (remindersSent == null) remindersSent = new ArrayList<>();
        if (!remindersSent.contains(months)) remindersSent.add(months);
        lastReminder = at;
        lastError = null;
    }

    public String getLastReminder() {
        return lastReminder;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Map<String, Transfer> getTransfers() {
        if (transfers == null) transfers = new LinkedHashMap<>();
        return transfers;
    }
}
