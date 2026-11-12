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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.datetime.PeriodHelper;
import org.jevis.commons.json.JsonLimitsConfig;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Manual values (stage E1, see docs/WEB-API.md), like the Control Center's "Enter data" dialog
 * ({@code EnterDataDialog}): the time stamp is aligned to the reading mode of the row, the value is checked against
 * the last counter reading and the limits of the clean data, written with the note {@code <manual entry text> <now>}
 * and the dependent rows are reset from that time on so that they are calculated again.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditValues {

    static final String CLASS_BASE_DATA = "Base Data";
    static final List<String> ROW_CLASSES = WebEdit.MANUAL_ROW_CLASSES;
    static final String ATTRIBUTE_VALUE = "Value";
    static final String ATTRIBUTE_PERIOD = "Period";
    static final String ATTRIBUTE_DIFFERENTIAL = "Conversion to Differential";
    static final String ATTRIBUTE_LIMITS = "Limits Configuration";
    static final String KEY_MANUAL = "menu.file.import.manual";
    static final String KEY_COUNTER_LOWER = "plugin.object.dialog.data.differror";
    static final String KEY_DIFF_SMALLER = "plugin.object.dialog.data.diff.smaller";
    static final String KEY_DIFF_BIGGER = "plugin.object.dialog.data.diff.bigger";
    static final String KEY_VALUE_SMALLER = "plugin.object.dialog.data.nodiff.smaller";
    static final String KEY_VALUE_BIGGER = "plugin.object.dialog.data.nodiff.bigger";
    /**
     * New key (not in the language files yet; German and English fallback below)
     */
    static final String KEY_EXISTS = "plugin.object.dialog.data.exists";
    static final String ACTION_ENTER = "manual-value";
    static final String ACTION_DELETE = "manual-value-delete";
    static final String ACTION_RESET = "manual-value-reset";

    private static final Logger logger = LogManager.getLogger(ResourceWebEditValues.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Reading modes of {@code EnterDataTypes}
     */
    enum Mode {SPECIFIC_DATETIME, DAY, MONTH, YEAR}

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("rows/{id}/entry")
    @Produces(MediaType.APPLICATION_JSON)
    public Response entry(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        return WebEdit.run(httpHeaders, "load the entry of " + id, store -> entry(store, id));
    }

    @POST
    @Path("rows/{id}/values")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response enter(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                          String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "enter a value for " + id, store -> enter(store, id, body, language, DateTime.now()));
    }

    @DELETE
    @Path("rows/{id}/values")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteValue(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                                @QueryParam("timestamp") String timestamp) {
        return WebEdit.run(httpHeaders, "delete a value of " + id, store -> deleteValue(store, id, timestamp));
    }

    // ---------------------------------------------------------------- row settings

    /**
     * What the entry needs to know about a row.
     */
    static final class Info {
        final JsonObject row;
        final WebEdit.WorkDay workDay;
        final boolean counter;
        final Period period;
        final String periodText;
        final Mode mode;
        final Double min;
        final Double max;
        final boolean hasLimits;

        Info(JsonObject row, WebEdit.WorkDay workDay, boolean counter, String periodText, Double min, Double max,
             boolean hasLimits) {
            this.row = row;
            this.workDay = workDay;
            this.counter = counter;
            this.periodText = periodText;
            this.period = period(periodText);
            this.mode = mode(counter, this.period);
            this.min = min;
            this.max = max;
            this.hasLimits = hasLimits;
        }
    }

    /**
     * @return the readable raw "Data" or "Base Data" row outside the recycle bin, else null
     */
    static JsonObject manualRow(WebEdit.Store store, long id) throws Exception {
        JsonObject row = WebEdit.readable(store, id);
        return row != null && ROW_CLASSES.contains(row.getJevisClass()) ? row : null;
    }

    /**
     * @return the first (lowest id) readable "Clean Data" (or subclass) child of the row outside the recycle bin, or null
     */
    static JsonObject cleanChild(WebEdit.Store store, JsonObject row) throws Exception {
        JsonObject clean = null;
        for (JsonObject child : store.lookup().readableChildren(row.getId(), store.classes().withHeirs(ResourceWebNotes.CLASS_CLEAN_DATA))) {
            if (ResourceWebIso.isDeleted(child)) continue;
            if (clean == null || child.getId() < clean.getId()) clean = child;
        }
        return clean;
    }

    static Info info(WebEdit.Store store, JsonObject row) throws Exception {
        JsonObject clean = cleanChild(store, row);
        JsonObject differential = clean != null ? clean : CLASS_BASE_DATA.equals(row.getJevisClass()) ? row : null;
        boolean counter = differential != null && ResourceWebAlarms.isTrue(store.latest(differential.getId(), ATTRIBUTE_DIFFERENTIAL));
        JsonSample period = store.latest(row.getId(), ATTRIBUTE_PERIOD);
        Double min = null;
        Double max = null;
        boolean hasLimits = false;
        if (clean != null) {
            JsonLimitsConfig limits = firstLimits(store.latest(clean.getId(), ATTRIBUTE_LIMITS));
            if (limits != null) {
                min = parseDouble(limits.getMin());
                max = parseDouble(limits.getMax());
                hasLimits = min != null || max != null;
            }
        }
        return new Info(row, store.workDay(row), counter, period != null ? period.getValue() : null, min, max, hasLimits);
    }

    /**
     * @return the first step of the limit configuration ({@code CleanDataObject.getLimitsConfig().get(0)}), or null
     */
    static JsonLimitsConfig firstLimits(JsonSample sample) {
        if (sample == null || sample.getValue() == null || sample.getValue().trim().isEmpty()) return null;
        try {
            JsonLimitsConfig[] configs = MAPPER.readValue(sample.getValue(), JsonLimitsConfig[].class);
            return configs != null && configs.length > 0 ? configs[0] : null;
        } catch (Exception e) {
            logger.debug("Could not read the limits {}", sample.getValue(), e);
            return null;
        }
    }

    static Double parseDouble(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static Period period(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        try {
            return new Period(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * {@code DataTypeBox.selectFromPeriod}: counters always by day; P1D and P1W by day, P1M by month, P1Y by year,
     * else date and time.
     */
    static Mode mode(boolean counter, Period period) {
        if (counter) return Mode.DAY;
        if (period == null) return Mode.SPECIFIC_DATETIME;
        if (period.equals(Period.days(1)) || period.equals(Period.weeks(1))) return Mode.DAY;
        if (period.equals(Period.months(1))) return Mode.MONTH;
        if (period.equals(Period.years(1))) return Mode.YEAR;
        return Mode.SPECIFIC_DATETIME;
    }

    // ---------------------------------------------------------------- time stamps

    /**
     * Like {@code EnterDataDialog}: year → 1 January 00:00, month → day 1 00:00, day → 00:00 of the day in the time
     * zone of the site; with a custom working day that starts on the day before (end before start) year and month
     * move to the start of the working day on the last day of the month before, days to the start of the working day
     * (on the day before if it starts then); date and time → to the second.
     *
     * @throws WebEdit.Stop {@code 400} if the time does not exist in the time zone
     */
    static DateTime align(DateTime input, Mode mode, WebEdit.WorkDay workDay) throws WebEdit.Stop {
        DateTimeZone zone = workDay.zone;
        DateTime local = input.withZone(zone);
        try {
            DateTime ts;
            switch (mode) {
                case YEAR:
                case MONTH:
                    LocalDate first = mode == Mode.YEAR ? new LocalDate(local.getYear(), 1, 1)
                            : new LocalDate(local.getYear(), local.getMonthOfYear(), 1);
                    if (workDay.startsDayBefore()) {
                        LocalDate last = first.minusDays(1);
                        ts = atWorkdayStart(last, workDay);
                    } else {
                        ts = first.toDateTimeAtStartOfDay(zone);
                    }
                    break;
                case DAY:
                    LocalDate day = local.toLocalDate();
                    if (workDay.isCustom()) {
                        ts = atWorkdayStart(workDay.startsDayBefore() ? day.minusDays(1) : day, workDay);
                    } else {
                        ts = day.toDateTimeAtStartOfDay(zone);
                    }
                    break;
                case SPECIFIC_DATETIME:
                default:
                    ts = local.withMillisOfSecond(0);
            }
            return ts;
        } catch (IllegalArgumentException e) {
            throw WebEdit.Stop.badRequest("Invalid timestamp");
        }
    }

    private static DateTime atWorkdayStart(LocalDate day, WebEdit.WorkDay workDay) {
        return day.toDateTimeAtStartOfDay(workDay.zone).withTime(workDay.start.getHour(), workDay.start.getMinute(),
                workDay.start.getSecond(), 0);
    }

    /**
     * {@code EnterDataDialog.getNextTS}: the next period after the last sample (with a working day that starts on the
     * day before moved back to the date it belongs to); the last sample itself without period.
     */
    static DateTime suggested(DateTime last, Period period, WebEdit.WorkDay workDay) {
        if (period == null || period.equals(Period.ZERO)) return last;
        DateTime next = PeriodHelper.getNextPeriod(last, period, 1, true, workDay.zone);
        if (workDay.startsDayBefore()) {
            next = next.plusDays(1).minusHours(workDay.start.getHour()).minusMinutes(workDay.start.getMinute())
                    .minusSeconds(workDay.start.getSecond()).withMillisOfSecond(0);
        }
        if (next.equals(last)) {
            try {
                next = last.plus(period.toStandardDuration().getMillis());
            } catch (Exception e) {
                logger.debug("Could not add the period {}", period, e);
            }
        }
        return next;
    }

    // ---------------------------------------------------------------- GET entry

    /**
     * {@code GET /rows/{id}/entry}
     */
    static WebEdit.Reply entry(WebEdit.Store store, long id) throws Exception {
        JsonObject row = manualRow(store, id);
        if (row == null) return WebEdit.Reply.notFound("Row not found");
        Info info = info(store, row);
        DateTimeZone zone = info.workDay.zone;
        JsonSample last = store.latest(id, ATTRIBUTE_VALUE);
        Long lastMillis = last != null ? ResourceWebNotes.millis(last.getTs()) : null;

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", row.getId());
        json.put("name", row.getName());
        json.put("unit", store.unit(id, ATTRIBUTE_VALUE));
        json.put("canEnter", store.rights().canWrite(row));
        json.put("canDelete", WebEdit.canDeleteSamples(store.rights(), row));
        json.put("counter", info.counter);
        json.put("period", info.periodText);
        json.put("mode", info.mode.name());
        json.put("timeZone", zone.getID());
        json.put("suggestedTimestamp", lastMillis != null
                ? WebEdit.iso(suggested(new DateTime(lastMillis, zone), info.period, info.workDay).getMillis(), zone) : null);
        if (lastMillis != null) {
            Map<String, Object> lastJson = new LinkedHashMap<>();
            lastJson.put("timestamp", WebEdit.iso(lastMillis, zone));
            lastJson.put("value", ResourceWebRegisters.number(last.getValue()));
            json.put("last", lastJson);
        } else {
            json.put("last", null);
        }
        if (info.hasLimits) {
            Map<String, Object> limits = new LinkedHashMap<>();
            limits.put("min", info.min);
            limits.put("max", info.max);
            json.put("limits", limits);
        } else {
            json.put("limits", null);
        }
        return WebEdit.Reply.ok(json);
    }

    // ---------------------------------------------------------------- POST value

    /**
     * {@code POST /rows/{id}/values} with {@code {timestamp, value, confirm}}
     */
    static WebEdit.Reply enter(WebEdit.Store store, long id, String body, String language, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonNode valueNode = json.get("value");
        if (valueNode == null || !valueNode.isNumber() || Double.isNaN(valueNode.asDouble())
                || Double.isInfinite(valueNode.asDouble())) {
            return WebEdit.Reply.badRequest("Missing or invalid value");
        }
        double value = valueNode.asDouble();
        boolean confirm = WebEdit.optionalBool(json, "confirm");
        String timestampText = WebEdit.text(json, "timestamp");

        JsonObject row = manualRow(store, id);
        if (row == null) return WebEdit.Reply.notFound("Row not found");
        if (!store.rights().canWrite(row)) return WebEdit.Reply.forbidden("Not allowed to enter values for this row");
        Info info = info(store, row);
        DateTimeZone zone = info.workDay.zone;
        DateTime timestamp = align(WebEdit.timestamp(timestampText, zone, "timestamp"), info.mode, info.workDay);

        List<String> warnings = warnings(store, info, timestamp, value, language);
        if (!warnings.isEmpty() && !confirm) {
            Map<String, Object> conflict = new LinkedHashMap<>();
            conflict.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
            conflict.put("value", value);
            conflict.put("warnings", warnings);
            return WebEdit.Reply.conflict(conflict);
        }

        String note = manualText(language) + " " + now;
        WebEdit.writeSample(store, id, ATTRIBUTE_VALUE,
                WebEdit.primitiveType(store, row, ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE),
                WebEdit.sample(timestamp, BigDecimal.valueOf(value).stripTrailingZeros().toPlainString(), note), ACTION_ENTER);
        resetDependents(store, row, timestamp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
        result.put("value", value);
        result.put("warnings", warnings);
        return WebEdit.Reply.created(result);
    }

    /**
     * Plausibility like {@code EnterDataDialog}: counter lower than the last reading, else the difference to the
     * reading one period earlier outside the limits; other rows: the value outside the limits. Plus an existing sample
     * at the time (would be overwritten).
     */
    static List<String> warnings(WebEdit.Store store, Info info, DateTime timestamp, double value, String language)
            throws Exception {
        List<String> warnings = new ArrayList<>();
        long id = info.row.getId();
        if (info.counter) {
            Double last = number(store.latest(id, ATTRIBUTE_VALUE));
            if (last != null && last > value) {
                warnings.add(text(KEY_COUNTER_LOWER, language));
            } else if (info.hasLimits) {
                double previous = value;
                if (info.period != null && !info.period.equals(Period.ZERO)) {
                    DateTime previousTs = timestamp.minus(info.period);
                    List<JsonSample> samples = store.samples(id, ATTRIBUTE_VALUE, previousTs, previousTs, 1);
                    Double sampleValue = samples != null && !samples.isEmpty() ? number(samples.get(samples.size() - 1)) : null;
                    if (sampleValue != null) previous = sampleValue;
                }
                double difference = value - previous;
                if (info.min != null && difference < info.min) {
                    warnings.add(text(KEY_DIFF_SMALLER, language) + " " + format(difference) + " < " + format(info.min));
                } else if (info.max != null && difference > info.max) {
                    warnings.add(text(KEY_DIFF_BIGGER, language) + " " + format(difference) + " > " + format(info.max));
                }
            }
        } else if (info.hasLimits) {
            if (info.min != null && value < info.min) {
                warnings.add(text(KEY_VALUE_SMALLER, language) + " " + format(value) + " < " + format(info.min));
            } else if (info.max != null && value > info.max) {
                warnings.add(text(KEY_VALUE_BIGGER, language) + " " + format(value) + " > " + format(info.max));
            }
        }
        List<JsonSample> existing = store.samples(id, ATTRIBUTE_VALUE, timestamp, timestamp, 1);
        if (existing != null && !existing.isEmpty()) {
            warnings.add(text(KEY_EXISTS, language) + " " + existing.get(0).getValue());
        }
        return warnings;
    }

    private static Double number(JsonSample sample) {
        return sample != null ? parseDouble(sample.getValue()) : null;
    }

    static String format(double value) {
        DecimalFormat format = new DecimalFormat("0.######", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return format.format(value);
    }

    /**
     * @return the text of the key in the language (fallback English); for keys missing in the language files a German
     * or English default
     */
    static String text(String key, String language) {
        String text = ResourceWebNotes.NoteTags.translate(key, language);
        if (text != null) return text;
        if (KEY_EXISTS.equals(key)) {
            return "de".equals(language) ? "Zu diesem Zeitpunkt gibt es schon einen Wert, er wird überschrieben:"
                    : "There is already a value at this time, it will be overwritten:";
        }
        if (KEY_MANUAL.equals(key)) return "de".equals(language) ? "Manuelle Dateneingabe" : "Entry of values";
        return key;
    }

    /**
     * @return the manual entry text ({@code menu.file.import.manual}) in the language
     */
    static String manualText(String language) {
        return text(KEY_MANUAL, language);
    }

    /**
     * @return whether the note of a sample marks a manual entry (starts with the manual entry text of any language)
     */
    static boolean isManual(String note) {
        if (note == null) return false;
        String trimmed = note.trim();
        Set<String> texts = new LinkedHashSet<>(Arrays.asList(text(KEY_MANUAL, "de"), text(KEY_MANUAL, "en")));
        for (String language : ResourceWebNotes.NoteTags.LANGUAGES) texts.add(text(KEY_MANUAL, language));
        for (String text : texts) {
            if (text != null && !text.isEmpty() && trimmed.startsWith(text)) return true;
        }
        return false;
    }

    /**
     * Like {@code CommonMethods.cleanDependentObjects}: deletes the "Value" samples of the dependent rows from the
     * time on, so that they are processed and calculated again. Only rows the user may delete samples of; the others
     * are left (and logged), like the Control Center whose delete is refused by the server.
     */
    static void resetDependents(WebEdit.Store store, JsonObject row, DateTime from) {
        List<Long> dependents;
        try {
            dependents = store.dependents(row);
        } catch (Exception e) {
            logger.error("Could not find the dependent rows of {}", row.getId(), e);
            return;
        }
        for (Long id : dependents) {
            try {
                JsonObject dependent = WebEdit.readable(store, id);
                if (dependent == null) continue;
                if (!WebEdit.canDeleteSamples(store.rights(), dependent)) {
                    logger.info("Not allowed to reset the dependent row {} of {}", id, row.getId());
                    continue;
                }
                JsonSample last = store.latest(id, ATTRIBUTE_VALUE);
                Long lastMillis = last != null ? ResourceWebNotes.millis(last.getTs()) : null;
                if (lastMillis == null || lastMillis < from.getMillis()) continue;
                WebEdit.deleteSamples(store, id, ATTRIBUTE_VALUE, from, new DateTime(lastMillis, from.getZone()), ACTION_RESET);
            } catch (Exception e) {
                logger.error("Could not reset the dependent row {} of {}", id, row.getId(), e);
            }
        }
    }

    // ---------------------------------------------------------------- DELETE value

    /**
     * {@code DELETE /rows/{id}/values?timestamp=…}: only manually entered samples; the dependent rows are reset from
     * that time on as after an entry.
     */
    static WebEdit.Reply deleteValue(WebEdit.Store store, long id, String timestampText) throws Exception {
        JsonObject row = manualRow(store, id);
        if (row == null) return WebEdit.Reply.notFound("Row not found");
        DateTime timestamp = WebEdit.timestamp(timestampText, store.workDay(row).zone, "timestamp");
        if (!WebEdit.canDeleteSamples(store.rights(), row)) return WebEdit.Reply.forbidden("Not allowed to delete values of this row");
        List<JsonSample> samples = store.samples(id, ATTRIBUTE_VALUE, timestamp, timestamp, 1);
        if (samples == null || samples.isEmpty()) return WebEdit.Reply.notFound("No value at this time");
        if (!isManual(samples.get(0).getNote())) return WebEdit.Reply.forbidden("Only manually entered values can be deleted");
        WebEdit.deleteSamples(store, id, ATTRIBUTE_VALUE, timestamp, timestamp, ACTION_DELETE);
        resetDependents(store, row, timestamp);
        return WebEdit.Reply.noContent();
    }
}
