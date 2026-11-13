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
import org.glassfish.jersey.media.multipart.FormDataBodyPart;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.object.ValueFormat;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.ResourceI18n;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;

import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

/**
 * Manual meter reading (stage E8.1, see docs/WEB-API.md), the counterpart of the Sun and Charge app: the meters the
 * user may read with their data row, last readings, due state and expected value ({@link ReadingExpectation}),
 * entering a reading (as {@code POST /rows/{id}/values} of E1, {@link ResourceWebEditValues#write}) and a photo of
 * the reading in the meter's attribute "Reading Photo".
 */
@Path("/JEWebService/v1/web")
public class ResourceWebReadings {

    static final String ATTRIBUTE_READING_PHOTO = "Reading Photo";
    static final String ATTRIBUTE_VALUE = ResourceWebRegisters.ATTRIBUTE_VALUE;
    static final String ATTRIBUTE_SERIAL_NUMBER = "Serial Number";
    static final String ATTRIBUTE_METER_POINT = "Meter Point";
    static final String ATTRIBUTE_LOCATION = "Location";
    static final String PROBLEM_NO_TARGET = "NO_TARGET";
    static final String PROBLEM_NOT_READABLE = "NOT_READABLE";
    static final String PROBLEM_NOT_MANUAL = "NOT_MANUAL";
    /**
     * the row is filled automatically: target of a data source channel/data point or of a calculation output, or a
     * row with a period shorter than a day and no manual reading among the last readings (e.g. 15-minute values)
     */
    static final String PROBLEM_AUTOMATIC = "AUTOMATIC";
    static final List<String> CHANNEL_TARGET_ATTRIBUTES = Arrays.asList("Target", "Target ID", "Target Id");
    static final String CLASS_CALCULATION_OUTPUT = "Output";
    static final String ATTRIBUTE_CALCULATION_OUTPUT = "Output";
    /**
     * Readings per meter: the counter intervals of {@link ReadingExpectation#WINDOW} + 1 readings
     */
    static final int READINGS = ReadingExpectation.WINDOW + 1;
    static final int MAX_NOTE_LENGTH = 500;
    static final int MAX_PHOTO_SIZE = 15 * 1024 * 1024;
    static final String ACTION_ENTER = "reading-enter";
    static final String ACTION_PHOTO = "reading-photo";

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("readings")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang,
                         @QueryParam("site") Long site, @QueryParam("round") Long round) {
        return WebEdit.run(httpHeaders, "load the meter readings", store -> round != null
                ? ResourceWebReadingRounds.readings(store, translations(store, lang), round, DateTime.now())
                : list(store, translations(store, lang), site, DateTime.now()));
    }

    @POST
    @Path("readings/{meterId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response enter(@Context HttpHeaders httpHeaders, @PathParam("meterId") long meterId,
                          @QueryParam("lang") String lang, String body) {
        String language = WebEdit.language(lang, httpHeaders);
        return WebEdit.run(httpHeaders, "enter a reading of " + meterId,
                store -> enter(store, meterId, body, language, DateTime.now()));
    }

    @POST
    @Path("readings/{meterId}/photo")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadPhoto(@Context HttpHeaders httpHeaders, @PathParam("meterId") long meterId,
                                @QueryParam("timestamp") String timestamp,
                                @FormDataParam("file") FormDataBodyPart file) {
        return WebEdit.run(httpHeaders, "upload the reading photo of " + meterId, store -> {
            try (InputStream in = file != null ? file.getValueAs(InputStream.class) : null) {
                return uploadPhoto(store, meterId, timestamp, in, MAX_PHOTO_SIZE);
            }
        });
    }

    @GET
    @Path("readings/{meterId}/photo")
    public Response photo(@Context HttpHeaders httpHeaders, @PathParam("meterId") long meterId,
                          @QueryParam("timestamp") String timestamp) {
        Response[] file = new Response[1];
        Response response = WebEdit.run(httpHeaders, "read the reading photo of " + meterId, store -> {
            WebEdit.Reply reply = photo(store, meterId, timestamp);
            if (reply.body instanceof Photo) {
                Photo photo = (Photo) reply.body;
                file[0] = ResourceWebRegisters.fileResponse(photo.bytes, photo.name, true);
                return WebEdit.Reply.noContent();
            }
            return reply;
        });
        return file[0] != null ? file[0] : response;
    }

    static ResourceWebRegisters.Translations translations(WebEdit.Store store, String lang) {
        return new ResourceWebRegisters.Translations(ResourceI18n.cached(), ResourceWebRegisters.language(lang),
                store.classes());
    }

    // ---------------------------------------------------------------- the row of a meter

    /**
     * The data row of a meter (its "Online ID", whatever attribute it names; as the meter exchange of E5), or the
     * problem why it cannot be read in.
     */
    static final class Target {
        final JsonObject row;
        final String problem;

        private Target(JsonObject row, String problem) {
            this.row = row;
            this.problem = problem;
        }
    }

    static Target target(WebEdit.Store store, JsonObject meter, Map<String, JsonAttribute> attributes) throws Exception {
        String online = attributes != null ? ResourceWebRegisters.text(attributes, ResourceWebRegisters.ATTRIBUTE_ONLINE_ID)
                : value(store.latest(meter.getId(), ResourceWebRegisters.ATTRIBUTE_ONLINE_ID));
        Long[] targetId = new Long[1];
        if (!ResourceWebRegisters.parseTarget(online, targetId, new String[1])) return new Target(null, PROBLEM_NO_TARGET);
        JsonObject row = WebEdit.readable(store, targetId[0]);
        if (row == null) return new Target(null, PROBLEM_NOT_READABLE);
        if (!WebEdit.MANUAL_ROW_CLASSES.contains(row.getJevisClass())) return new Target(null, PROBLEM_NOT_MANUAL);
        return new Target(row, null);
    }

    private static String value(JsonSample sample) {
        return sample != null ? sample.getValue() : null;
    }

    /**
     * @return the readable meter outside the recycle bin, else null
     */
    static JsonObject meter(WebEdit.Store store, long id) throws Exception {
        JsonObject meter = WebEdit.readable(store, id);
        return ResourceWebEditRegisters.isMeter(store.classes(), meter) ? meter : null;
    }

    /**
     * @param samples newest first
     * @return the numeric readings of the samples
     */
    static List<ReadingExpectation.Reading> readings(List<JsonSample> samples) {
        List<ReadingExpectation.Reading> readings = new ArrayList<>();
        for (JsonSample sample : samples) {
            Long millis = ResourceWebNotes.millis(sample.getTs());
            Double value = ResourceWebEditValues.parseDouble(sample.getValue());
            if (millis != null && value != null) readings.add(new ReadingExpectation.Reading(millis, value));
        }
        return readings;
    }

    static boolean hasPeriod(ResourceWebEditValues.Info info) {
        return info.period != null && !ReadingExpectation.isZero(info.period);
    }

    // ---------------------------------------------------------------- GET

    /**
     * {@code GET /readings?site=…}: one group per "Measurement Directory" (as the meter register) with its meters.
     */
    static WebEdit.Reply list(WebEdit.Store store, ResourceWebRegisters.Translations translations, Long site,
                              DateTime now) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        ResourceWebRegisters.Data data = store.registerData();
        Set<String> meterClasses = classes.withHeirs(ResourceWebRegisters.CLASS_MEASUREMENT_INSTRUMENT);
        Set<Long> automatic = writtenAutomatically(store);
        List<Map<String, Object>> groups = new ArrayList<>();
        for (JsonObject directory : ResourceWebRegisters.readableOfClasses(lookup,
                Collections.singleton(ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY))) {
            Map<String, Object> siteJson = lookup.site(directory);
            if (site != null && (siteJson == null || !site.equals(toLong(siteJson.get("id"))))) continue;
            List<Map<String, Object>> meters = new ArrayList<>();
            for (JsonObject meter : ResourceWebRegisters.below(directory, meterClasses, lookup)) {
                meters.add(meterJson(store, translations, data, meter, now, automatic));
            }
            meters.sort(Comparator.comparing((Map<String, Object> m) -> lower(m.get("name")))
                    .thenComparingLong(m -> (Long) m.get("id")));
            JsonObject building = ResourceWebRegisters.nearestOfClass(directory, ResourceWebNavigation.CLASS_BUILDING, lookup);
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("key", String.valueOf(directory.getId()));
            group.put("name", building != null ? building.getName() : directory.getName());
            group.put("site", siteJson);
            group.put("meters", meters);
            groups.add(group);
        }
        groups.sort(Comparator.comparing((Map<String, Object> g) -> lower(g.get("name")))
                .thenComparing(g -> String.valueOf(g.get("key"))));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", now.toLocalDate().toString());
        result.put("groups", groups);
        return WebEdit.Reply.ok(result);
    }

    private static Long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private static String lower(Object value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * @return ids of the rows written by the data collector (targets of channels and data points) or by JECalc
     * (calculation outputs); server-internal, over all objects
     */
    static Set<Long> writtenAutomatically(WebEdit.Store store) throws Exception {
        Set<Long> ids = new HashSet<>();
        for (JsonObject o : store.lookup().all()) {
            String cls = o.getJevisClass();
            List<String> names;
            if (CLASS_CALCULATION_OUTPUT.equals(cls)) {
                names = Collections.singletonList(ATTRIBUTE_CALCULATION_OUTPUT);
            } else if (cls != null && (cls.endsWith("Channel") || cls.endsWith("Data Point") || cls.equals("SQL Request"))) {
                names = CHANNEL_TARGET_ATTRIBUTES;
            } else {
                continue;
            }
            for (String name : names) {
                try {
                    JsonSample sample = store.latest(o.getId(), name);
                    if (sample != null) ids.addAll(targetIds(sample.getValue()));
                } catch (Exception e) {
                    /* no readable target value: not counted */
                }
            }
        }
        return ids;
    }

    /**
     * @return the object ids of a target value ("123:Value", "123", several separated by ";" or ",")
     */
    static Set<Long> targetIds(String value) {
        Set<Long> ids = new HashSet<>();
        if (value == null) return ids;
        for (String part : value.split("[;,]")) {
            String id = part.trim();
            int colon = id.indexOf(':');
            if (colon >= 0) id = id.substring(0, colon).trim();
            if (id.matches("\\d{1,18}")) ids.add(Long.parseLong(id));
        }
        return ids;
    }

    /**
     * @param automatic rows written by data sources or calculations ({@link #writtenAutomatically})
     * @param samples   the last readings of the row
     * @return whether the row is filled automatically ({@value #PROBLEM_AUTOMATIC}): written by a data source or
     * calculation, or with a period shorter than a day and no manual reading among the samples
     */
    static boolean isAutomatic(Set<Long> automatic, long rowId, Period period, List<JsonSample> samples) {
        if (automatic.contains(rowId)) return true;
        if (!subDaily(period)) return false;
        for (JsonSample sample : samples) {
            if (ResourceWebEditValues.isManual(sample.getNote())) return false;
        }
        return true;
    }

    /**
     * @return true if the period is shorter than a day (no years, months, weeks or days)
     */
    static boolean subDaily(Period period) {
        return period != null && !period.equals(Period.ZERO) && period.getYears() == 0 && period.getMonths() == 0
                && period.getWeeks() == 0 && period.getDays() == 0;
    }

    static Map<String, Object> meterJson(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                         ResourceWebRegisters.Data data, JsonObject meter, DateTime now) throws Exception {
        return meterJson(store, translations, data, meter, now, Collections.emptySet());
    }

    /**
     * One meter of {@code GET /readings}.
     *
     * @param automatic rows written by data sources or calculations ({@link #writtenAutomatically})
     */
    static Map<String, Object> meterJson(WebEdit.Store store, ResourceWebRegisters.Translations translations,
                                         ResourceWebRegisters.Data data, JsonObject meter, DateTime now,
                                         Set<Long> automatic) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        WebEdit.Rights rights = store.rights();
        Map<String, JsonAttribute> attributes = data.attributes(meter.getId());
        boolean hasPhoto = classes.type(meter.getJevisClass(), ATTRIBUTE_READING_PHOTO) != null;

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", meter.getId());
        json.put("name", meter.getName());
        json.put("className", meter.getJevisClass());
        json.put("classLabel", translations.className(meter.getJevisClass()));
        json.put("medium", ResourceWebRegisters.medium(meter.getJevisClass(), classes));
        json.put("path", ResourceWebRegisters.path(meter, lookup));
        json.put("serialNumber", text(attributes, ATTRIBUTE_SERIAL_NUMBER));
        json.put("meterPoint", text(attributes, ATTRIBUTE_METER_POINT));
        json.put("location", text(attributes, ATTRIBUTE_LOCATION));
        json.put("canPhoto", hasPhoto && rights.canWrite(meter));

        Target target = target(store, meter, attributes);
        if (target.row == null) {
            json.put("canExchange", false);
            json.put("row", null);
            json.put("due", ReadingExpectation.Due.NONE.name());
            json.put("suggestedTimestamp", null);
            json.put("expected", null);
            json.put("readings", new ArrayList<>());
            json.put("problem", target.problem);
            return json;
        }
        JsonObject row = target.row;
        ResourceWebEditValues.Info info = ResourceWebEditValues.info(store, row);
        DateTimeZone zone = info.workDay.zone;
        json.put("canExchange", info.counter && rights.canWrite(meter) && rights.canWrite(row));

        Map<String, Object> rowJson = ResourceWebEditValues.rowJson(store, info);
        rowJson.put("limits", ResourceWebEditValues.limitsJson(info));
        Integer decimals = ValueFormat.parse(ResourceWebRegisters.text(attributes, ValueFormat.ATTRIBUTE_NAME));
        if (decimals == null) decimals = data.decimals(row.getId(), new HashMap<>());
        rowJson.put("decimals", decimals);
        json.put("row", rowJson);

        List<JsonSample> samples = store.latestSamples(row.getId(), ATTRIBUTE_VALUE, READINGS);
        if (samples == null) samples = Collections.emptyList();
        Long lastMillis = samples.isEmpty() ? null : ResourceWebNotes.millis(samples.get(0).getTs());
        DateTime last = lastMillis != null ? new DateTime(lastMillis, zone) : null;
        DateTime suggested = last != null ? ResourceWebEditValues.suggested(last, info.period, info.workDay) : null;

        boolean isAutomatic = isAutomatic(automatic, row.getId(), info.period, samples);
        json.put("due", isAutomatic ? ReadingExpectation.Due.NONE.name()
                : ReadingExpectation.due(last, info.period, info.workDay, now).name());
        json.put("suggestedTimestamp", suggested != null ? WebEdit.iso(suggested.getMillis(), zone) : null);
        long at = suggested != null && !suggested.isAfter(now) ? suggested.getMillis() : now.getMillis();
        json.put("expected", expectedJson(ReadingExpectation.estimate(info.counter, hasPeriod(info), readings(samples), at)));

        Set<Long> photos = hasPhoto ? photoTimes(store, meter.getId(), samples) : Collections.emptySet();
        List<Map<String, Object>> readings = new ArrayList<>();
        for (JsonSample sample : samples) {
            Long millis = ResourceWebNotes.millis(sample.getTs());
            Map<String, Object> reading = new LinkedHashMap<>();
            reading.put("timestamp", millis != null ? WebEdit.iso(millis, zone) : ResourceWebRegisters.isoTimestamp(sample.getTs()));
            reading.put("value", ResourceWebRegisters.number(sample.getValue()));
            reading.put("note", sample.getNote() != null && !sample.getNote().trim().isEmpty() ? sample.getNote() : null);
            reading.put("manual", ResourceWebEditValues.isManual(sample.getNote()));
            reading.put("photo", millis != null && photos.contains(millis));
            readings.add(reading);
        }
        json.put("readings", readings);
        json.put("problem", isAutomatic ? PROBLEM_AUTOMATIC : null);
        return json;
    }

    private static String text(Map<String, JsonAttribute> attributes, String name) {
        String text = ResourceWebRegisters.text(attributes, name);
        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }

    /**
     * @param samples the readings, newest first
     * @return the times (epoch milliseconds) of the "Reading Photo" samples of the meter between the oldest and the
     * newest reading
     */
    static Set<Long> photoTimes(WebEdit.Store store, long meterId, List<JsonSample> samples) throws Exception {
        Set<Long> times = new HashSet<>();
        if (samples.isEmpty()) return times;
        Long newest = ResourceWebNotes.millis(samples.get(0).getTs());
        Long oldest = ResourceWebNotes.millis(samples.get(samples.size() - 1).getTs());
        if (newest == null || oldest == null) return times;
        List<JsonSample> photos = store.samples(meterId, ATTRIBUTE_READING_PHOTO, new DateTime(Math.min(oldest, newest)),
                new DateTime(Math.max(oldest, newest)), 100000);
        if (photos == null) return times;
        for (JsonSample photo : photos) {
            Long millis = ResourceWebNotes.millis(photo.getTs());
            if (millis != null && photo.getValue() != null && !photo.getValue().trim().isEmpty()) times.add(millis);
        }
        return times;
    }

    static Map<String, Object> expectedJson(ReadingExpectation.Expected expected) {
        if (expected == null) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("value", expected.value);
        json.put("low", expected.low);
        json.put("high", expected.high);
        return json;
    }

    // ---------------------------------------------------------------- POST reading

    /**
     * {@code POST /readings/{meterId}} with {@code {timestamp, value, note, confirm}}: as {@code POST /rows/{id}/values}
     * for the meter's row, the note appended to the manual entry note, plus the expected value warning.
     */
    static WebEdit.Reply enter(WebEdit.Store store, long meterId, String body, String language, DateTime now)
            throws Exception {
        JsonNode json = WebEdit.body(body);
        JsonNode valueNode = json.get("value");
        if (valueNode == null || !valueNode.isNumber() || Double.isNaN(valueNode.asDouble())
                || Double.isInfinite(valueNode.asDouble())) {
            return WebEdit.Reply.badRequest("Missing or invalid value");
        }
        double value = valueNode.asDouble();
        boolean confirm = WebEdit.optionalBool(json, "confirm");
        String timestampText = WebEdit.text(json, "timestamp");
        String note = note(json);

        JsonObject meter = meter(store, meterId);
        if (meter == null) return WebEdit.Reply.notFound("Meter not found");
        Target target = target(store, meter, null);
        if (target.row == null) {
            Map<String, Object> problem = new LinkedHashMap<>();
            problem.put("messages", Collections.singletonList(problemMessage(target.problem)));
            problem.put("problem", target.problem);
            return new WebEdit.Reply(400, problem);
        }
        if (!store.rights().canWrite(target.row)) {
            return WebEdit.Reply.forbidden("Not allowed to enter values for the row of this meter");
        }
        ResourceWebEditValues.Info info = ResourceWebEditValues.info(store, target.row);
        return ResourceWebEditValues.write(store, info, value, timestampText, confirm, language, now, note,
                ACTION_ENTER, (i, timestamp, v, warnings) -> expectedWarning(store, i, timestamp, v, language, warnings));
    }

    private static String problemMessage(String problem) {
        switch (problem) {
            case PROBLEM_NO_TARGET:
                return "The meter has no data row (Online ID)";
            case PROBLEM_NOT_READABLE:
                return "The data row of the meter is not readable";
            default:
                return "The data row of the meter takes no manual values";
        }
    }

    /**
     * @return the trimmed note, null if missing or empty
     * @throws WebEdit.Stop {@code 400 {messages}} if not a text, longer than {@value #MAX_NOTE_LENGTH} characters or
     *                      with control characters
     */
    static String note(JsonNode json) throws WebEdit.Stop {
        JsonNode node = json.get("note");
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid note"));
        String note = node.asText().trim();
        if (note.isEmpty()) return null;
        if (note.length() > MAX_NOTE_LENGTH) {
            throw ResourceWebEditIso.invalid(Collections.singletonList("Note longer than " + MAX_NOTE_LENGTH + " characters"));
        }
        for (int i = 0; i < note.length(); i++) {
            if (Character.isISOControl(note.charAt(i))) {
                throw ResourceWebEditIso.invalid(Collections.singletonList("Invalid characters in note"));
            }
        }
        return note;
    }

    /**
     * Adds the warning for a value outside the expected range at the time (from the readings before the new value);
     * not when the counter warning (smaller than the last reading) is already there.
     */
    static void expectedWarning(WebEdit.Store store, ResourceWebEditValues.Info info, DateTime timestamp, double value,
                                String language, List<String> warnings) throws Exception {
        if (warnings.contains(ResourceWebEditValues.text(ResourceWebEditValues.KEY_COUNTER_LOWER, language))) return;
        List<JsonSample> samples = store.latestSamples(info.row.getId(), ATTRIBUTE_VALUE, READINGS);
        ReadingExpectation.Expected expected = ReadingExpectation.estimate(info.counter, hasPeriod(info),
                readings(samples != null ? samples : Collections.emptyList()), timestamp.getMillis());
        if (expected == null || expected.contains(value)) return;
        warnings.add(ResourceWebEditValues.text(ResourceWebEditValues.KEY_EXPECTED, language) + " "
                + format(value, language, 6) + " (" + format(expected.low, language, 3) + " … "
                + format(expected.high, language, 3) + ")");
    }

    /**
     * @return the number with the grouping and decimal separator of the language ({@code de}: {@code 12.345,6})
     */
    static String format(double value, String language, int maxDecimals) {
        Locale locale = language != null && !language.isEmpty() ? Locale.forLanguageTag(language) : Locale.ENGLISH;
        DecimalFormat format = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale));
        format.setMaximumFractionDigits(maxDecimals);
        return format.format(value);
    }

    // ---------------------------------------------------------------- photos

    /**
     * The content of a reading photo for {@code GET /readings/{meterId}/photo}.
     */
    static final class Photo {
        final byte[] bytes;
        final String name;

        Photo(byte[] bytes, String name) {
            this.bytes = bytes;
            this.name = name;
        }
    }

    /**
     * {@code POST /readings/{meterId}/photo?timestamp=…}: a file sample of "Reading Photo" at the time of the reading.
     */
    static WebEdit.Reply uploadPhoto(WebEdit.Store store, long meterId, String timestampText, InputStream in, int max)
            throws Exception {
        JsonObject meter = meter(store, meterId);
        if (meter == null) return WebEdit.Reply.notFound("Meter not found");
        JsonType type = store.classes().type(meter.getJevisClass(), ATTRIBUTE_READING_PHOTO);
        if (type == null || type.getPrimitiveType() != JEVisConstants.PrimitiveType.FILE) {
            return WebEdit.Reply.badRequest("The meter has no attribute " + ATTRIBUTE_READING_PHOTO);
        }
        if (!store.rights().canWrite(meter)) return WebEdit.Reply.forbidden("Not allowed to change this meter");
        DateTimeZone zone = store.workDay(meter).zone;
        DateTime timestamp = WebEdit.timestamp(timestampText, zone, "timestamp").withMillisOfSecond(0);
        if (in == null) return WebEdit.Reply.badRequest("Missing file");
        byte[] bytes = ResourceWebEditIso.read(in, max);
        if (bytes == null) return new WebEdit.Reply(413, "The file is larger than " + (max / (1024 * 1024)) + " MB");
        String extension = photoExtension(bytes);
        if (extension == null) return WebEdit.Reply.badRequest("The file is no image (JPEG, PNG, WebP or HEIC)");
        String name = "Ablesung_" + DateTimeFormat.forPattern("yyyyMMdd_HHmm").withZone(zone).print(timestamp)
                + "." + extension;
        int count = store.writeFile(meterId, type.getName(), timestamp, name, bytes);
        store.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(meterId, type.getName(), count, ACTION_PHOTO));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("timestamp", WebEdit.iso(timestamp.getMillis(), zone));
        json.put("file", name);
        return WebEdit.Reply.created(json);
    }

    /**
     * @return the file extension of a JPEG, PNG, WebP or HEIC image detected from the bytes, else null
     */
    static String photoExtension(byte[] bytes) {
        String image = ResourceWebRegisters.imageType(bytes);
        if ("image/jpeg".equals(image)) return "jpg";
        if ("image/png".equals(image)) return "png";
        if ("image/webp".equals(image)) return "webp";
        if (isHeic(bytes)) return "heic";
        return null;
    }

    /**
     * @return whether the bytes start with an ISO media "ftyp" box of a HEIC/HEIF brand
     */
    static boolean isHeic(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return false;
        if (bytes[4] != 'f' || bytes[5] != 't' || bytes[6] != 'y' || bytes[7] != 'p') return false;
        String brand = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.ISO_8859_1);
        return Arrays.asList("heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1").contains(brand);
    }

    /**
     * {@code GET /readings/{meterId}/photo?timestamp=…}
     *
     * @return {@code 200} with a {@link Photo}, else the error
     */
    static WebEdit.Reply photo(WebEdit.Store store, long meterId, String timestampText) throws Exception {
        JsonObject meter = meter(store, meterId);
        if (meter == null) return WebEdit.Reply.notFound("Meter not found");
        JsonType type = store.classes().type(meter.getJevisClass(), ATTRIBUTE_READING_PHOTO);
        if (type == null || type.getPrimitiveType() != JEVisConstants.PrimitiveType.FILE) {
            return WebEdit.Reply.notFound("Photo not found");
        }
        DateTime timestamp = WebEdit.timestamp(timestampText, store.workDay(meter).zone, "timestamp");
        List<JsonSample> samples = store.samples(meterId, type.getName(), timestamp, timestamp, 1);
        JsonSample sample = samples != null && !samples.isEmpty() ? samples.get(0) : null;
        if (sample == null || sample.getValue() == null || sample.getValue().trim().isEmpty()) {
            return WebEdit.Reply.notFound("Photo not found");
        }
        byte[] bytes = store.file(meterId, type.getName(), sample);
        if (bytes == null) return WebEdit.Reply.notFound("Photo not found");
        return WebEdit.Reply.ok(new Photo(bytes, sample.getValue()));
    }
}
