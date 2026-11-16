/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * Review of contradictory readings (stage E8.5, see docs/WEB-API.md): the samples of the meters' rows that are a
 * counter decrease, far from the expected value ({@link ReadingExpectation}) or outside the limits, and the decision
 * of the user: confirm (a note at the sample) or ignore (the sample is removed, a note keeps the information). Nothing
 * is changed automatically.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebReadingsReview {

    static final String ISSUE_COUNTER_DECREASE = "COUNTER_DECREASE";
    static final String ISSUE_FAR_FROM_EXPECTED = "FAR_FROM_EXPECTED";
    static final String ISSUE_LIMIT = "LIMIT";
    static final String ACTION_CONFIRM = "CONFIRM";
    static final String ACTION_IGNORE = "IGNORE";
    /**
     * Web action of the user action log of both decisions
     */
    static final String LOG_ACTION = "reading-review";
    static final String LOG_ACTION_ROLLBACK = "reading-review-rollback";
    static final String CONFIRMED_DE = "Ablesung geprüft";
    static final String CONFIRMED_EN = "Reading checked";
    static final String IGNORED_DE = "Ablesung ignoriert";
    static final String IGNORED_EN = "Reading ignored";
    static final int DEFAULT_DAYS = 365;
    static final int MAX_DAYS = 1095;
    static final int MAX_ITEMS = 500;
    static final int MAX_COMMENT_LENGTH = 500;
    /**
     * Samples read per row (newest first): the window of {@link #MAX_DAYS} days of daily readings and the samples
     * before it; older samples of rows with more values in the window are not checked
     */
    static final int MAX_ROW_SAMPLES = 2000;
    /**
     * The navigation count is only computed for users with at most this many meters (else {@code null}), so that
     * the navigation stays fast; the review list itself has no such limit
     */
    static final int MAX_NAVIGATION_METERS = 300;

    private static final Logger logger = LogManager.getLogger(ResourceWebReadingsReview.class);

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("readings/review")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang,
                         @QueryParam("days") String days, @QueryParam("site") Long site) {
        return WebEdit.run(httpHeaders, "load the readings to review",
                store -> list(store, site, days(days), DateTime.now()));
    }

    @POST
    @Path("readings/review/{meterId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response review(@Context HttpHeaders httpHeaders, @PathParam("meterId") long meterId,
                           @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "review a reading of " + meterId,
                store -> review(store, meterId, body, language, DateTime.now()));
    }

    /**
     * @return the days of the parameter, {@value #DEFAULT_DAYS} if missing
     * @throws WebEdit.Stop {@code 400} if not a number from 1 to {@value #MAX_DAYS}
     */
    static int days(String text) throws WebEdit.Stop {
        if (text == null || text.trim().isEmpty()) return DEFAULT_DAYS;
        try {
            int days = Integer.parseInt(text.trim());
            if (days >= 1 && days <= MAX_DAYS) return days;
        } catch (NumberFormatException e) {
            /* below */
        }
        throw WebEdit.Stop.badRequest("days must be a number from 1 to " + MAX_DAYS);
    }

    // ---------------------------------------------------------------- the row of a meter

    /**
     * A meter with a row that is read by hand, and what the review needs of the row.
     */
    static final class MeterRow {
        final JsonObject meter;
        final JsonObject row;
        final ResourceWebEditValues.Info info;
        final Map<String, Object> site;
        /**
         * the latest samples of the row, newest first (at most {@link #MAX_ROW_SAMPLES})
         */
        final List<JsonSample> latest;

        MeterRow(JsonObject meter, JsonObject row, ResourceWebEditValues.Info info, Map<String, Object> site,
                 List<JsonSample> latest) {
            this.meter = meter;
            this.row = row;
            this.info = info;
            this.site = site;
            this.latest = latest;
        }
    }

    /**
     * @param automatic rows written by data sources or calculations ({@link ResourceWebReadings#writtenAutomatically})
     * @return the meter with its row, null if it has no usable row or the row is filled automatically
     */
    static MeterRow meterRow(WebEdit.Store store, JsonObject meter, Map<String, Object> site, Set<Long> automatic)
            throws Exception {
        ResourceWebReadings.Target target = ResourceWebReadings.target(store, meter, null);
        if (target.row == null) return null;
        ResourceWebEditValues.Info info = ResourceWebEditValues.info(store, target.row);
        List<JsonSample> latest = store.latestSamples(target.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE, MAX_ROW_SAMPLES);
        if (latest == null) latest = Collections.emptyList();
        List<JsonSample> recent = latest.subList(0, Math.min(latest.size(), ResourceWebReadings.READINGS));
        if (ResourceWebReadings.isAutomatic(automatic, target.row.getId(), info.period, recent)) return null;
        return new MeterRow(meter, target.row, info, site, latest);
    }

    // ---------------------------------------------------------------- checking the samples

    /**
     * A numeric sample of the row.
     */
    static final class Point {
        final long millis;
        final double value;
        final JsonSample sample;

        Point(long millis, double value, JsonSample sample) {
            this.millis = millis;
            this.value = value;
            this.sample = sample;
        }
    }

    /**
     * @param samples any order
     * @return the numeric samples, oldest first
     */
    static List<Point> points(List<JsonSample> samples) {
        List<Point> points = new ArrayList<>();
        for (JsonSample sample : samples) {
            Long millis = ResourceWebNotes.millis(sample.getTs());
            Double value = ResourceWebEditValues.parseDouble(sample.getValue());
            if (millis != null && value != null) points.add(new Point(millis, value, sample));
        }
        points.sort(Comparator.comparingLong(p -> p.millis));
        return points;
    }

    /**
     * The result of checking one sample against the samples before it.
     */
    static final class Check {
        final Point point;
        final Point previous;
        final ReadingExpectation.Expected expected;
        final List<String> issues;

        Check(Point point, Point previous, ReadingExpectation.Expected expected, List<String> issues) {
            this.point = point;
            this.previous = previous;
            this.expected = expected;
            this.issues = issues;
        }
    }

    /**
     * @return whether the note of a sample marks a meter exchange (contains "Meter Tausch", any case)
     */
    static boolean isExchange(JsonSample sample) {
        return sample != null && sample.getNote() != null
                && sample.getNote().toLowerCase(Locale.ROOT).contains(ResourceWebEditRegisters.NOTE_EXCHANGE.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks the sample {@code index} of the points against the (at most {@link ResourceWebReadings#READINGS})
     * samples before it: {@value #ISSUE_COUNTER_DECREASE} (counter lower than the previous sample, neither of both
     * marked as a meter exchange; a decrease at a meter exchange has no issue at all),
     * {@value #ISSUE_FAR_FROM_EXPECTED} (outside the E8.1 expectation at its time; not together with a counter
     * decrease) and {@value #ISSUE_LIMIT} (as the E1 plausibility).
     *
     * @param oneEarlier the value of the counter one period earlier (E1 limit check), null if there is none
     */
    static Check check(ResourceWebEditValues.Info info, List<Point> points, int index, Double oneEarlier) {
        Point point = points.get(index);
        Point previous = index > 0 ? points.get(index - 1) : null;
        List<ReadingExpectation.Reading> before = new ArrayList<>();
        for (int i = Math.max(0, index - ResourceWebReadings.READINGS); i < index; i++) {
            before.add(new ReadingExpectation.Reading(points.get(i).millis, points.get(i).value));
        }
        ReadingExpectation.Expected expected = ReadingExpectation.estimate(info.counter,
                ResourceWebReadings.hasPeriod(info), before, point.millis);

        List<String> issues = new ArrayList<>();
        boolean decrease = info.counter && previous != null && point.value < previous.value;
        if (decrease && (isExchange(point.sample) || isExchange(previous.sample))) {
            return new Check(point, previous, expected, issues);
        }
        if (decrease) issues.add(ISSUE_COUNTER_DECREASE);
        if (!decrease && expected != null && !expected.contains(point.value)) issues.add(ISSUE_FAR_FROM_EXPECTED);
        if (info.hasLimits) {
            if (info.counter) {
                if (!decrease && oneEarlier != null) {
                    double difference = point.value - oneEarlier;
                    if (outside(info, difference)) issues.add(ISSUE_LIMIT);
                }
            } else if (outside(info, point.value)) {
                issues.add(ISSUE_LIMIT);
            }
        }
        return new Check(point, previous, expected, issues);
    }

    private static boolean outside(ResourceWebEditValues.Info info, double value) {
        return (info.min != null && value < info.min) || (info.max != null && value > info.max);
    }

    /**
     * @return the value of the counter exactly one period before the sample (E1: {@code timestamp - period}), null
     * without limits, period or such a sample
     */
    static Double oneEarlier(WebEdit.Store store, MeterRow meterRow, List<Point> points, int index,
                             Map<Long, Double> byMillis) throws Exception {
        ResourceWebEditValues.Info info = meterRow.info;
        if (!info.counter || !info.hasLimits || !ResourceWebReadings.hasPeriod(info)) return null;
        DateTime earlier = new DateTime(points.get(index).millis, info.workDay.zone).minus(info.period);
        if (!points.isEmpty() && earlier.getMillis() >= points.get(0).millis) return byMillis.get(earlier.getMillis());
        List<JsonSample> samples = store.samples(meterRow.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE, earlier, earlier, 1);
        return samples != null && !samples.isEmpty() ? ResourceWebEditValues.parseDouble(samples.get(0).getValue()) : null;
    }

    private static Map<Long, Double> byMillis(List<Point> points) {
        Map<Long, Double> map = new HashMap<>();
        for (Point point : points) map.put(point.millis, point.value);
        return map;
    }

    /**
     * @return the times (epoch milliseconds) of the notes of the row's "Data Notes" object in the time frame that
     * confirm a reading (text starts with {@value #CONFIRMED_DE} or {@value #CONFIRMED_EN})
     */
    static Set<Long> confirmed(WebEdit.Store store, ResourceWebNotes.NotesTarget target, DateTime from, DateTime to)
            throws Exception {
        Set<Long> times = new HashSet<>();
        if (target == null || target.notesObject == null) return times;
        for (ResourceWebNotes.Note note : ResourceWebNotes.notesOf(target.notesObject.getId(), from, to, store::samples,
                ResourceWebNotes.FETCH_LIMIT)) {
            if (isConfirmation(note.text)) times.add(note.millis);
        }
        return times;
    }

    static boolean isConfirmation(String text) {
        if (text == null) return false;
        String trimmed = text.trim();
        return trimmed.startsWith(CONFIRMED_DE) || trimmed.startsWith(CONFIRMED_EN);
    }

    // ---------------------------------------------------------------- GET

    /**
     * {@code GET /readings/review?days=…&site=…}
     */
    static WebEdit.Reply list(WebEdit.Store store, Long site, int days, DateTime now) throws Exception {
        return WebEdit.Reply.ok(review(store, site, days, now, Integer.MAX_VALUE));
    }

    /**
     * {@code modules.readingsReview} of the navigation: the number of items with the default days.
     *
     * @return null if the user may read more than {@value #MAX_NAVIGATION_METERS} meters (too costly for the
     * navigation)
     */
    static Integer navigationCount(WebEdit.Store store, DateTime now) throws Exception {
        Map<String, Object> review = review(store, null, DEFAULT_DAYS, now, MAX_NAVIGATION_METERS);
        return review != null ? (Integer) review.get("count") : null;
    }

    /**
     * @param maxMeters null is returned if there are more meters
     * @return {@code {items, count, truncated}}
     */
    static Map<String, Object> review(WebEdit.Store store, Long site, int days, DateTime now, int maxMeters)
            throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        Set<String> meterClasses = classes.withHeirs(ResourceWebRegisters.CLASS_MEASUREMENT_INSTRUMENT);
        Map<Long, JsonObject> meters = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> sites = new HashMap<>();
        for (JsonObject directory : ResourceWebRegisters.readableOfClasses(lookup,
                Collections.singleton(ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY))) {
            Map<String, Object> siteJson = lookup.site(directory);
            Object siteId = siteJson != null ? siteJson.get("id") : null;
            if (site != null && !(siteId instanceof Number && ((Number) siteId).longValue() == site)) continue;
            for (JsonObject meter : ResourceWebRegisters.below(directory, meterClasses, lookup)) {
                if (meters.putIfAbsent(meter.getId(), meter) == null) sites.put(meter.getId(), siteJson);
            }
        }
        if (meters.size() > maxMeters) return null;

        DateTime from = now.minusDays(days);
        Set<Long> automatic = meters.isEmpty() ? Collections.emptySet() : ResourceWebReadings.writtenAutomatically(store);
        List<Map<String, Object>> items = new ArrayList<>();
        for (JsonObject meter : meters.values()) {
            MeterRow meterRow = meterRow(store, meter, sites.get(meter.getId()), automatic);
            if (meterRow != null) items.addAll(items(store, meterRow, from));
        }
        items.sort(Comparator.comparingLong((Map<String, Object> item) -> (Long) item.get("millis")).reversed()
                .thenComparing(item -> String.valueOf(item.get("meterName")).toLowerCase(Locale.ROOT))
                .thenComparingLong(item -> (Long) item.get("meterId")));
        boolean truncated = items.size() > MAX_ITEMS;
        if (truncated) items = new ArrayList<>(items.subList(0, MAX_ITEMS));
        for (Map<String, Object> item : items) item.remove("millis");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("count", items.size());
        result.put("truncated", truncated);
        return result;
    }

    /**
     * @return the items of the samples of the row from the time on that have an issue and are not confirmed (with
     * the internal key {@code millis} for sorting)
     */
    static List<Map<String, Object>> items(WebEdit.Store store, MeterRow meterRow, DateTime from) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        List<Point> points = points(meterRow.latest);
        if (points.isEmpty() || points.get(points.size() - 1).millis < from.getMillis()) return items;
        Map<Long, Double> byMillis = byMillis(points);
        RowJson rowJson = null;
        Set<Long> confirmed = null;
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            if (point.millis < from.getMillis()) continue;
            Check check = check(meterRow.info, points, i, oneEarlier(store, meterRow, points, i, byMillis));
            if (check.issues.isEmpty()) continue;
            if (rowJson == null) {
                rowJson = rowJson(store, meterRow);
                confirmed = confirmed(store, rowJson.notes, from, new DateTime(points.get(points.size() - 1).millis));
            }
            if (confirmed.contains(point.millis)) continue;
            Map<String, Object> item = itemJson(store, meterRow, rowJson, check);
            item.put("millis", point.millis);
            items.add(item);
        }
        return items;
    }

    /**
     * The row of the items of a meter and where its notes are stored.
     */
    static final class RowJson {
        final Map<String, Object> json;
        final ResourceWebNotes.NotesTarget notes;
        final boolean canNote;
        final boolean canExchange;

        RowJson(Map<String, Object> json, ResourceWebNotes.NotesTarget notes, boolean canNote, boolean canExchange) {
            this.json = json;
            this.notes = notes;
            this.canNote = canNote;
            this.canExchange = canExchange;
        }
    }

    static RowJson rowJson(WebEdit.Store store, MeterRow meterRow) throws Exception {
        WebEdit.Rights rights = store.rights();
        JsonObject row = meterRow.row;
        ResourceWebNotes.NotesTarget notes = ResourceWebNotes.notesTarget(row.getId(), store.lookup(), store.classes());
        boolean canNote = ResourceWebNotes.canAddNote(notes, rights);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", row.getId());
        json.put("name", row.getName());
        json.put("unit", store.unit(row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE));
        json.put("counter", meterRow.info.counter);
        json.put("canEnter", rights.canWrite(row));
        json.put("canDelete", WebEdit.canDeleteSamples(rights, row));
        json.put("canNote", canNote);
        boolean canExchange = meterRow.info.counter && rights.canWrite(meterRow.meter) && rights.canWrite(row);
        return new RowJson(json, notes, canNote, canExchange);
    }

    static Map<String, Object> itemJson(WebEdit.Store store, MeterRow meterRow, RowJson rowJson, Check check) {
        DateTimeZone zone = meterRow.info.workDay.zone;
        String note = check.point.sample.getNote();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("meterId", meterRow.meter.getId());
        item.put("meterName", meterRow.meter.getName());
        item.put("site", meterRow.site);
        item.put("row", new LinkedHashMap<>(rowJson.json));
        item.put("timestamp", WebEdit.iso(check.point.millis, zone));
        item.put("value", check.point.value);
        item.put("note", note != null && !note.trim().isEmpty() ? note : null);
        item.put("manual", ResourceWebEditValues.isManual(note));
        if (check.previous != null) {
            Map<String, Object> previous = new LinkedHashMap<>();
            previous.put("timestamp", WebEdit.iso(check.previous.millis, zone));
            previous.put("value", check.previous.value);
            item.put("previous", previous);
        } else {
            item.put("previous", null);
        }
        item.put("expected", ResourceWebReadings.expectedJson(check.expected));
        item.put("issues", check.issues);
        item.put("canExchange", rowJson.canExchange);
        return item;
    }

    // ---------------------------------------------------------------- POST

    /**
     * {@code POST /readings/review/{meterId}} with {@code {timestamp, action, comment, value}}.
     */
    static WebEdit.Reply review(WebEdit.Store store, long meterId, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        String action = WebEdit.text(json, "action");
        if (!ACTION_CONFIRM.equals(action) && !ACTION_IGNORE.equals(action)) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("action must be CONFIRM or IGNORE"));
        }
        String timestampText = WebEdit.text(json, "timestamp");
        if (timestampText == null || timestampText.trim().isEmpty()) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("Missing timestamp"));
        }
        String comment = comment(json);
        JsonNode valueNode = json.get("value");
        if (valueNode != null && !valueNode.isNull() && !valueNode.isNumber()) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid value"));
        }
        Double listedValue = valueNode != null && valueNode.isNumber() ? valueNode.asDouble() : null;

        JsonObject meter = ResourceWebReadings.meter(store, meterId);
        if (meter == null) return WebEdit.Reply.notFound("Meter not found");
        ResourceWebReadings.Target target = ResourceWebReadings.target(store, meter, null);
        if (target.row == null) return problem(target.problem, "The meter has no usable data row");
        MeterRow meterRow = meterRow(store, meter, store.lookup().site(meter), ResourceWebReadings.writtenAutomatically(store));
        if (meterRow == null) {
            return problem(ResourceWebReadings.PROBLEM_AUTOMATIC, "The data row of the meter is filled automatically");
        }
        JsonObject row = meterRow.row;
        DateTimeZone zone = meterRow.info.workDay.zone;
        DateTime timestamp;
        try {
            timestamp = WebEdit.timestamp(timestampText, zone, "timestamp");
        } catch (WebEdit.Stop stop) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid timestamp"));
        }
        List<JsonSample> atTime = store.samples(row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE, timestamp, timestamp, 1);
        JsonSample sample = atTime != null && !atTime.isEmpty() ? atTime.get(0) : null;
        Double stored = sample != null ? ResourceWebEditValues.parseDouble(sample.getValue()) : null;
        if (stored == null) return WebEdit.Reply.notFound("No reading at this time");

        RowJson rowJson = rowJson(store, meterRow);
        WebEdit.Rights rights = store.rights();
        if (!rowJson.canNote) return WebEdit.Reply.forbidden("Not allowed to add notes to the row of this meter");
        if (ACTION_IGNORE.equals(action) && !WebEdit.canDeleteSamples(rights, row)) {
            return WebEdit.Reply.forbidden("Not allowed to delete values of the row of this meter");
        }
        Check check = checkAt(store, meterRow, timestamp);
        if (listedValue != null && Double.compare(listedValue, stored) != 0) {
            return WebEdit.Reply.conflict(itemJson(store, meterRow, rowJson, check));
        }

        String unit = store.unit(row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE);
        String text = ACTION_CONFIRM.equals(action) ? confirmText(stored, unit, language)
                : ignoreText(stored, unit, check.issues, language);
        if (comment != null) text += " – " + comment;

        if (ACTION_CONFIRM.equals(action)) {
            writeReviewNote(store, rowJson.notes, timestamp, text);
        } else {
            WebEdit.deleteSamples(store, row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE, timestamp, timestamp, LOG_ACTION);
            try {
                writeReviewNote(store, rowJson.notes, timestamp, text);
            } catch (Exception e) {
                restore(store, row, sample);
                if (e instanceof WebEdit.Stop) return ((WebEdit.Stop) e).reply;
                throw e;
            }
            ResourceWebEditValues.resetDependents(store, row, timestamp);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
        result.put("action", action);
        return WebEdit.Reply.created(result);
    }

    private static WebEdit.Reply problem(String problem, String message) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("messages", Collections.singletonList(message));
        json.put("problem", problem);
        return new WebEdit.Reply(400, json);
    }

    /**
     * @return the trimmed comment, null if missing or empty
     * @throws WebEdit.Stop {@code 400 {messages}} if not a text, longer than {@value #MAX_COMMENT_LENGTH} characters
     *                      or with control characters other than line breaks
     */
    static String comment(JsonNode json) throws WebEdit.Stop {
        JsonNode node = json.get("comment");
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid comment"));
        String comment = node.asText().trim();
        if (comment.isEmpty()) return null;
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("Comment longer than " + MAX_COMMENT_LENGTH + " characters"));
        }
        for (int i = 0; i < comment.length(); i++) {
            char c = comment.charAt(i);
            if (Character.isISOControl(c) && c != '\n' && c != '\r') {
                throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid characters in comment"));
            }
        }
        return comment;
    }

    /**
     * @return the check of the sample at the time (its issues may be empty), with the samples before it
     */
    static Check checkAt(WebEdit.Store store, MeterRow meterRow, DateTime timestamp) throws Exception {
        List<JsonSample> samples = meterRow.latest;
        Long oldest = samples.isEmpty() ? null : ResourceWebNotes.millis(samples.get(samples.size() - 1).getTs());
        if (oldest == null || oldest > timestamp.getMillis()) {
            /* older than the latest samples: the samples before it */
            samples = store.samples(meterRow.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE,
                    timestamp.minusDays(MAX_DAYS), timestamp, ResourceWebNotes.FETCH_LIMIT);
        }
        List<Point> points = points(samples);
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).millis == timestamp.getMillis()) {
                return check(meterRow.info, points, i, oneEarlier(store, meterRow, points, i, byMillis(points)));
            }
        }
        throw WebEdit.Stop.notFound("No reading at this time");
    }

    static String confirmText(double value, String unit, String language) {
        boolean de = "de".equals(language);
        return (de ? CONFIRMED_DE : CONFIRMED_EN) + ": " + valueText(value, unit, language)
                + (de ? " ist plausibel." : " is plausible.");
    }

    static String ignoreText(double value, String unit, List<String> issues, String language) {
        boolean de = "de".equals(language);
        String text = (de ? IGNORED_DE : IGNORED_EN) + ": " + valueText(value, unit, language);
        if (issues.isEmpty()) return text;
        List<String> words = new ArrayList<>();
        for (String issue : issues) words.add(issueText(issue, language));
        return text + " (" + String.join(", ", words) + ")";
    }

    private static String valueText(double value, String unit, String language) {
        String number = ResourceWebReadings.format(value, "de".equals(language) ? "de" : "en", 6);
        return unit != null && !unit.trim().isEmpty() ? number + " " + unit.trim() : number;
    }

    /**
     * @return the issue in words as the web app shows it
     */
    static String issueText(String issue, String language) {
        boolean de = "de".equals(language);
        switch (issue) {
            case ISSUE_COUNTER_DECREASE:
                return de ? "Rücklauf" : "counter decrease";
            case ISSUE_FAR_FROM_EXPECTED:
                return de ? "Abweichung vom Erwartungswert" : "deviation from the expected value";
            case ISSUE_LIMIT:
                return de ? "Grenzwert" : "limit";
            default:
                return issue;
        }
    }

    /**
     * Writes the note of a decision at the time (as {@code POST /rows/{id}/notes}, creating the "Data Notes" object if
     * missing). An existing note at that time is kept below the new text (one note per time stamp; its tags are kept).
     * If writing fails, what was written is undone as far as possible and the exception is thrown.
     */
    static void writeReviewNote(WebEdit.Store store, ResourceWebNotes.NotesTarget target, DateTime timestamp, String text)
            throws Exception {
        boolean created = target.notesObject == null;
        JsonObject notesObject = ResourceWebEditNotes.ensureNotesObject(store, target, LOG_ACTION);
        ResourceWebNotes.Note existing = created ? null
                : ResourceWebEditNotes.noteAt(store, notesObject.getId(), timestamp.getMillis());
        try {
            if (existing != null) {
                ResourceWebEditNotes.writeNote(store, notesObject, target.row, timestamp, text + "\n" + existing.text,
                        null, LOG_ACTION);
            } else {
                ResourceWebEditNotes.writeNote(store, notesObject, target.row, timestamp, text, new ArrayList<>(), LOG_ACTION);
            }
        } catch (Exception e) {
            undoNote(store, notesObject, created, existing, timestamp);
            throw e;
        }
    }

    private static void undoNote(WebEdit.Store store, JsonObject notesObject, boolean created,
                                 ResourceWebNotes.Note existing, DateTime timestamp) {
        try {
            if (created) {
                store.deleteObject(notesObject);
            } else if (existing == null) {
                for (String attribute : Arrays.asList(ResourceWebNotes.ATTRIBUTE_VALUE, ResourceWebNotes.ATTRIBUTE_TAG,
                        ResourceWebNotes.ATTRIBUTE_USER)) {
                    WebEdit.deleteSamples(store, notesObject.getId(), attribute, timestamp, timestamp, LOG_ACTION_ROLLBACK);
                }
            } else {
                WebEdit.writeSample(store, notesObject.getId(), ResourceWebNotes.ATTRIBUTE_VALUE,
                        JEVisConstants.PrimitiveType.STRING, WebEdit.sample(timestamp, existing.text, null), LOG_ACTION_ROLLBACK);
                if (existing.user != null) {
                    WebEdit.writeSample(store, notesObject.getId(), ResourceWebNotes.ATTRIBUTE_USER,
                            JEVisConstants.PrimitiveType.STRING, WebEdit.sample(timestamp, existing.user, null), LOG_ACTION_ROLLBACK);
                }
            }
        } catch (Exception e) {
            logger.error("Could not undo the note of {} at {}", notesObject.getId(), timestamp, e);
        }
    }

    /**
     * Writes the deleted sample back (same time stamp, value and note) after the note could not be written.
     */
    private static void restore(WebEdit.Store store, JsonObject row, JsonSample sample) {
        try {
            JsonSample copy = new JsonSample();
            copy.setTs(sample.getTs());
            copy.setValue(sample.getValue());
            copy.setNote(sample.getNote());
            WebEdit.writeSample(store, row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE,
                    WebEdit.primitiveType(store, row, ResourceWebReadings.ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE),
                    copy, LOG_ACTION_ROLLBACK);
        } catch (Exception e) {
            logger.error("Could not write back the sample of {} at {}", row.getId(), sample.getTs(), e);
        }
    }
}
