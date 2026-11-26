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
import org.joda.time.DateTime;

/**
 * When a transfer target (class "Energy Certificate Transfer") is due, and its bookkeeping in "Last Transfer".
 * <ul>
 *     <li>{@code MANUAL}: only on request ("Jetzt übermitteln");</li>
 *     <li>{@code DAILY} / {@code WEEKLY} / {@code MONTHLY}: once per calendar day / ISO week / month, all
 *     certificates;</li>
 *     <li>{@code ON_CHANGE}: when a certificate of the scope changed since its last transfer to this target.</li>
 * </ul>
 * After a failed attempt the target waits {@value #RETRY_MINUTES} minutes before the next try (no mail flood).
 */
public final class TransferSchedule {

    public enum Schedule {
        MANUAL, DAILY, WEEKLY, MONTHLY, ON_CHANGE;

        /** @return the schedule of the text, MANUAL if empty or unknown */
        public static Schedule of(String text) {
            if (text != null) {
                for (Schedule s : values()) if (s.name().equalsIgnoreCase(text.trim())) return s;
            }
            return MANUAL;
        }
    }

    public static final int RETRY_MINUTES = 60;

    private static final Gson GSON = new Gson();

    private TransferSchedule() {
    }

    /** Content of the attribute "Last Transfer". Times ISO with offset. */
    public static class LastTransfer {
        /** the last attempt */
        private String at;
        private boolean success;
        private Integer count;
        private String error;
        /** the last successful transfer */
        private String lastSuccess;
        /** SFTP: fingerprint of the server key accepted on first use */
        private String hostKey;

        public static LastTransfer fromJson(String json) {
            if (json == null || json.trim().isEmpty()) return new LastTransfer();
            try {
                LastTransfer l = GSON.fromJson(json, LastTransfer.class);
                return l != null ? l : new LastTransfer();
            } catch (Exception e) {
                return new LastTransfer();
            }
        }

        public String toJson() {
            return GSON.toJson(this);
        }

        public void succeeded(DateTime now, int count) {
            this.at = now.toString();
            this.success = true;
            this.count = count;
            this.error = null;
            this.lastSuccess = this.at;
        }

        public void failed(DateTime now, String error) {
            this.at = now.toString();
            this.success = false;
            this.error = error;
        }

        public String getAt() {
            return at;
        }

        public boolean isSuccess() {
            return success;
        }

        public Integer getCount() {
            return count;
        }

        public String getError() {
            return error;
        }

        public String getLastSuccess() {
            return lastSuccess;
        }

        public String getHostKey() {
            return hostKey;
        }

        public void setHostKey(String hostKey) {
            this.hostKey = hostKey;
        }
    }

    /**
     * @param requested "Jetzt übermitteln" is pending
     * @param changed   number of certificates in the scope changed since their last transfer to this target
     */
    public static boolean isDue(Schedule schedule, LastTransfer last, boolean requested, int changed, DateTime now) {
        DateTime attempt = parse(last.getAt());
        if (!last.isSuccess() && attempt != null && attempt.plusMinutes(RETRY_MINUTES).isAfter(now)) return false;
        if (requested) return true;
        DateTime success = parse(last.getLastSuccess());
        switch (schedule) {
            case DAILY:
                return success == null || !success.withZone(now.getZone()).toLocalDate().equals(now.toLocalDate());
            case WEEKLY:
                if (success == null) return true;
                DateTime s = success.withZone(now.getZone());
                return s.getWeekyear() != now.getWeekyear() || s.getWeekOfWeekyear() != now.getWeekOfWeekyear();
            case MONTHLY:
                if (success == null) return true;
                DateTime m = success.withZone(now.getZone());
                return m.getYear() != now.getYear() || m.getMonthOfYear() != now.getMonthOfYear();
            case ON_CHANGE:
                return changed > 0;
            case MANUAL:
            default:
                return false;
        }
    }

    static DateTime parse(String iso) {
        if (iso == null || iso.trim().isEmpty()) return null;
        try {
            return new DateTime(iso.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
