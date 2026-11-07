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
package org.jevis.commons.iso;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reading the ISO 50001 module data of the Control Center (action plans, nonconformities, legal register) without
 * JavaFX.
 * <p>
 * The items keep their content as JSON in the file attribute "Data", written by the Control Center with Gson: keys
 * with spaces, Joda dates as ISO 8601 strings with offset, and numbers that may be {@code NaN} or {@code Infinity}.
 * Unknown keys are ignored. Plan settings are attributes holding {@code ;} separated lists.
 */
public final class IsoJson {

    /**
     * File attribute of the items with their JSON.
     */
    public static final String ATTRIBUTE_DATA = "Data";

    private static final Logger logger = LogManager.getLogger(IsoJson.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
            .build();

    private IsoJson() {
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * @return the parsed object, or null if the text is empty or not valid JSON
     */
    public static <T> T read(String json, Class<T> type) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            logger.warn("Could not parse {}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * @return the non-empty, trimmed parts of a {@code ;} separated list (empty for null)
     */
    public static List<String> splitList(String value) {
        List<String> list = new ArrayList<>();
        if (value == null) return list;
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) list.add(trimmed);
        }
        return list;
    }

    /**
     * Number of an item like the Control Center's action table: prefix, a space and the number with three digits,
     * e.g. "AP 003"; without prefix only the digits.
     */
    public static String number(String prefix, Integer no) {
        String digits = String.format(Locale.ROOT, "%03d", no != null ? no : 0);
        return prefix == null || prefix.trim().isEmpty() ? digits : prefix.trim() + " " + digits;
    }

    /**
     * @return the value, or null if it is null, NaN or infinite (not representable in JSON)
     */
    public static Double finite(Double value) {
        return value != null && !value.isNaN() && !value.isInfinite() ? value : null;
    }

    /**
     * @return the date as stored (ISO 8601), or null if empty
     */
    static String date(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /**
     * @return the parsed ISO 8601 date, or null if empty or invalid
     */
    public static DateTime parseDate(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return DateTime.parse(value.trim());
        } catch (Exception e) {
            logger.debug("Invalid date '{}'", value);
            return null;
        }
    }

    /**
     * @return the calendar date of the stored date in the given time zone, or null
     */
    public static LocalDate localDate(String value, DateTimeZone zone) {
        DateTime date = parseDate(value);
        return date != null ? date.withZone(zone).toLocalDate() : null;
    }

    /**
     * Days from the stored date to today (calendar days like the Control Center's
     * {@code Days.daysBetween(done.withTimeAtStartOfDay(), now.withTimeAtStartOfDay())}).
     *
     * @return the days, or null without (valid) date
     */
    public static Integer daysSince(String value, LocalDate today, DateTimeZone zone) {
        LocalDate date = localDate(value, zone);
        return date != null && today != null ? Days.daysBetween(date, today).getDays() : null;
    }

    /**
     * @return the text if it is an absolute http(s) URL, otherwise null
     */
    public static String httpUrl(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!(lower.startsWith("http://") || lower.startsWith("https://"))) return null;
        try {
            java.net.URI uri = new java.net.URI(trimmed);
            return uri.getHost() != null && !uri.getHost().isEmpty() ? trimmed : null;
        } catch (Exception e) {
            return null;
        }
    }

    static String text(String value) {
        return value != null ? value : "";
    }
}
