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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

/**
 * JSON storage and date formats shared by appointments (upstream issue #1855) and maintenance messages (#2663).
 * <p>
 * Each entry is one JEVis object whose "Data" attribute holds the entry as JSON (written by the Control Center) and
 * whose "Mail State" attribute holds the mail bookkeeping as JSON (written only by the JENotifier service), so the two
 * writers never overwrite each other. Dates are stored as local date-times without offset ("yyyy-MM-dd'T'HH:mm") and
 * are interpreted in the time zone of the reading program.
 */
public final class EntryFormat {

    /**
     * Attribute with the entry as JSON, written by the Control Center.
     */
    public static final String ATTRIBUTE_DATA = "Data";
    /**
     * Attribute with the mail bookkeeping as JSON, written by the JENotifier service.
     */
    public static final String ATTRIBUTE_MAIL_STATE = "Mail State";

    private static final Logger logger = LogManager.getLogger(EntryFormat.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final String STORAGE_PATTERN = "yyyy-MM-dd'T'HH:mm";
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    private static final String DISPLAY_PATTERN = "dd.MM.yyyy HH:mm";
    private static final String DISPLAY_DATE_PATTERN = "dd.MM.yyyy";

    private EntryFormat() {
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * @return the parsed object, or null if the text is empty or not valid JSON
     */
    public static <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        try {
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            logger.warn("Could not parse {} from '{}'", type.getSimpleName(), json, e);
            return null;
        }
    }

    /**
     * Storage format of due dates and start times (minutes).
     */
    public static String format(LocalDateTime time) {
        return time != null ? time.toString(STORAGE_PATTERN) : null;
    }

    /**
     * Storage format of timestamps (seconds), e.g. of an inform request.
     */
    public static String formatTimestamp(LocalDateTime time) {
        return time != null ? time.toString(TIMESTAMP_PATTERN) : null;
    }

    /**
     * @return the parsed date-time, or null if the text is empty or invalid
     */
    public static LocalDateTime parse(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim());
        } catch (Exception e) {
            logger.warn("Invalid date '{}'", text);
            return null;
        }
    }

    /**
     * Display format for mails and tables, e.g. "30.11.2026 09:00".
     */
    public static String display(LocalDateTime time) {
        return time != null ? time.toString(DISPLAY_PATTERN) : "";
    }

    public static String displayDate(LocalDate date) {
        return date != null ? date.toString(DISPLAY_DATE_PATTERN) : "";
    }

    /**
     * @return e.g. "2 h 30 min", "45 min", "3 h"; empty for null or 0
     */
    public static String displayDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "";
        }
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        } else if (rest == 0) {
            return hours + " h";
        }
        return hours + " h " + rest + " min";
    }

    static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
