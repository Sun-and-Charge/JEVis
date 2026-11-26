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
package org.jevis.commons.alarm;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.List;

/**
 * Decisions of the data outage check (pure; docs/ALARMS-OUTAGE.md).
 */
public final class OutageLogic {

    public enum Decision {NONE, START, END}

    private OutageLogic() {
    }

    /**
     * @param open    the open outage of the object, null if none
     * @param newest  newest value (row) or readout (source), null if there is none
     * @param minutes allowed age
     * @return START when no outage is open and the newest value is older than {@code minutes} (or missing); END when an
     * outage is open and any value newer than its start arrived (also a late delivery of old values); NONE otherwise
     */
    public static Decision decide(OutageState.Open open, DateTime newest, int minutes, DateTime now) {
        if (open != null) return resumed(open, newest) ? Decision.END : Decision.NONE;
        boolean stale = newest == null || newest.isBefore(now.minusMinutes(minutes));
        return stale ? Decision.START : Decision.NONE;
    }

    /**
     * An open outage also ends when the newest value is newer than the one it started with, even if that new value is
     * itself already older than the allowed age (e.g. a late delivery of old values only).
     */
    public static boolean resumed(OutageState.Open open, DateTime newest) {
        if (newest == null) return false;
        DateTime since = parse(open.since);
        return since == null || newest.isAfter(since);
    }

    /**
     * Late delivery: values with time stamps before the outage was found arrived later (the outage time is filled);
     * otherwise the outage time stays a gap.
     *
     * @param newTimestamps time stamps of the values after {@code since}
     */
    public static boolean backfilled(OutageState.Open open, List<DateTime> newTimestamps) {
        DateTime since = parse(open.since);
        DateTime opened = parse(open.opened);
        if (opened == null) return false;
        for (DateTime ts : newTimestamps) {
            if ((since == null || ts.isAfter(since)) && ts.isBefore(opened)) return true;
        }
        return false;
    }

    /**
     * @param time        "HH:mm" in the time zone of the configuration
     * @param lastSummary when the last summary was sent, null if never
     * @return the summary of today is due: now is at or after today's time and none was sent since then
     */
    public static boolean summaryDue(String time, DateTimeZone zone, DateTime lastSummary, DateTime now) {
        int[] hm = hourMinute(time);
        if (hm == null) return false;
        DateTime local = now.withZone(zone != null ? zone : DateTimeZone.getDefault());
        DateTime today = local.withTime(hm[0], hm[1], 0, 0);
        return !local.isBefore(today) && (lastSummary == null || lastSummary.isBefore(today));
    }

    /** @return {hour, minute} of "HH:mm", null if invalid */
    public static int[] hourMinute(String time) {
        if (time == null || !time.trim().matches("\\d{1,2}:\\d{2}")) return null;
        String[] p = time.trim().split(":");
        int h = Integer.parseInt(p[0]);
        int m = Integer.parseInt(p[1]);
        return h < 24 && m < 60 ? new int[]{h, m} : null;
    }

    public static DateTime parse(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try {
            return new DateTime(iso);
        } catch (Exception e) {
            return null;
        }
    }
}
