/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Pure date rolling of the module entries of the demo: the dates inside the JSON of appointments, maintenance
 * messages, actions, nonconformities and obligations move by whole weeks. The JSON is edited as a tree, so keys the
 * job does not know are kept.
 */
public final class DateShifter {

    /**
     * Date fields of appointments (local date-times "yyyy-MM-dd'T'HH:mm").
     */
    public static final List<String> APPOINTMENT_FIELDS = Collections.unmodifiableList(Arrays.asList("due", "lastDone", "created"));
    /**
     * Date fields of maintenance messages (local date-times, minutes or seconds).
     */
    public static final List<String> MAINTENANCE_FIELDS = Collections.unmodifiableList(Arrays.asList("start", "doneAt", "created", "informRequested"));
    /**
     * Date fields of actions and nonconformities (ISO 8601 with offset, written by the Control Center with Gson).
     */
    public static final List<String> ACTION_FIELDS = Collections.unmodifiableList(Arrays.asList("Create Date", "Planned Date", "Done Date"));
    /**
     * Date fields of obligations of the legal register.
     */
    public static final List<String> OBLIGATION_FIELDS = Collections.unmodifiableList(Arrays.asList("issue date", "current version date", "Date of Examination"));

    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter LOCAL_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    /**
     * Joda's ISODateTimeFormat.dateTime() as used by the Control Center: millis and offset ("Z" for UTC).
     */
    private static final DateTimeFormatter ISO_MILLIS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            // Gson writes NaN / Infinity unquoted (serializeSpecialFloatingPointValues), keep it that way
            .disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
            .build();

    private DateShifter() {
    }

    /**
     * @return whole weeks between the last shift and today (floor), 0 if today is not later
     */
    public static int weeksToShift(LocalDate shiftedUntil, LocalDate today) {
        long days = ChronoUnit.DAYS.between(shiftedUntil, today);
        return days <= 0 ? 0 : (int) (days / 7);
    }

    /**
     * @return true if the entry JSON has a non-empty "recipients" list (such entries are not shifted, so no
     * reminder mails are triggered by the demo)
     */
    public static boolean hasRecipients(String json) throws IOException {
        JsonNode recipients = parse(json).get("recipients");
        if (recipients == null || recipients.isNull()) return false;
        if (recipients.isArray()) return recipients.size() > 0;
        return !recipients.asText("").trim().isEmpty();
    }

    /**
     * Shifts the local date-time fields of an appointment or maintenance entry ({@link #APPOINTMENT_FIELDS},
     * {@link #MAINTENANCE_FIELDS}). Each value keeps its format (date, minutes or seconds).
     *
     * @return the new compact JSON, or null if no field was changed
     */
    public static String shiftLocalFields(String json, List<String> fields, int weeks) throws IOException {
        ObjectNode root = parse(json);
        boolean changed = false;
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node == null || !node.isTextual()) continue;
            String shifted = shiftLocal(node.asText(), weeks);
            if (shifted != null && !shifted.equals(node.asText())) {
                root.put(field, shifted);
                changed = true;
            }
        }
        return changed ? MAPPER.writeValueAsString(root) : null;
    }

    /**
     * Shifts the ISO 8601 date fields of an action, nonconformity or obligation ({@link #ACTION_FIELDS},
     * {@link #OBLIGATION_FIELDS}).
     *
     * @return the new pretty printed JSON (as the Control Center writes it), or null if no field was changed
     */
    public static String shiftIsoFields(String json, List<String> fields, int weeks) throws IOException {
        ObjectNode root = parse(json);
        boolean changed = false;
        for (String field : fields) {
            JsonNode node = root.get(field);
            if (node == null || !node.isTextual()) continue;
            String shifted = shiftIso(node.asText(), weeks);
            if (shifted != null && !shifted.equals(node.asText())) {
                root.put(field, shifted);
                changed = true;
            }
        }
        return changed ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) : null;
    }

    /**
     * Shifts a local date ("yyyy-MM-dd"), date-time with minutes or with seconds by whole weeks, same format.
     *
     * @return the shifted text, or null if empty or not parseable
     */
    static String shiftLocal(String text, int weeks) {
        if (text == null) return null;
        String value = text.trim();
        if (value.isEmpty()) return null;
        try {
            if (value.length() == 10) return LocalDate.parse(value).plusWeeks(weeks).toString();
            LocalDateTime time = LocalDateTime.parse(value);
            LocalDateTime shifted = time.plusWeeks(weeks);
            if (value.length() == 16) return shifted.format(LOCAL_MINUTES);
            if (value.length() == 19) return shifted.format(LOCAL_SECONDS);
            return shifted.toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Shifts an ISO 8601 date-time with offset by whole weeks. If the offset is the one of Europe/Berlin at that
     * time, the shift keeps the local time across DST changes and the new offset is written; other offsets (e.g.
     * UTC) stay fixed. Output with milliseconds like Joda's ISO format.
     *
     * @return the shifted text, or null if empty or not parseable
     */
    static String shiftIso(String text, int weeks) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            OffsetDateTime time = OffsetDateTime.parse(text.trim());
            ZonedDateTime local = time.atZoneSameInstant(ReplayPlanner.ZONE);
            if (local.getOffset().equals(time.getOffset())) {
                return local.plusWeeks(weeks).format(ISO_MILLIS);
            }
            return time.plusWeeks(weeks).format(ISO_MILLIS);
        } catch (DateTimeParseException e) {
            return shiftLocal(text, weeks);
        }
    }

    /**
     * Shifts a sample time stamp by whole weeks in Europe/Berlin local time, so a note at 09:00 stays at 09:00 across
     * DST changes (a local time that does not exist moves forward, an ambiguous one keeps the earlier offset).
     */
    public static long shiftMillis(long millis, int weeks) {
        ZonedDateTime local = Instant.ofEpochMilli(millis).atZone(ReplayPlanner.ZONE);
        return local.toLocalDateTime().plusWeeks(weeks).atZone(ReplayPlanner.ZONE).toInstant().toEpochMilli();
    }

    private static ObjectNode parse(String json) throws IOException {
        JsonNode node = MAPPER.readTree(json == null ? "" : json);
        if (node == null || !node.isObject()) throw new IOException("Not a JSON object");
        return (ObjectNode) node;
    }
}
