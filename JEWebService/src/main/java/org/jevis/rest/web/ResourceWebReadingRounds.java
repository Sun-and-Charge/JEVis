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
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.classes.JC;
import org.jevis.commons.object.ValueFormat;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Reading rounds and route sheet (stage E8.2, see docs/WEB-API.md): a "Reading Round" assigns meters in reading order
 * to readers (users or groups) with a period; the order of a period is not stored but computed from the readings
 * ({@link #schedule}, {@link #state}). Listing, editing (create, change, delete to the recycle bin), the editor
 * options, the route sheet as PDF ({@link RouteSheetPdf}) and the meters of a round for the reading page
 * ({@code GET /web/readings?round=…}, {@link #readings}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebReadingRounds {

    static final String CLASS_DIRECTORY = JC.Directory.ReadingRoundDirectory.name;
    static final String CLASS_ROUND = JC.ReadingRound.name;
    static final String CLASS_USER = "User";
    static final String CLASS_GROUP = "Group";
    static final String ATTRIBUTE_ENABLED = JC.ReadingRound.a_Enabled;
    static final String ATTRIBUTE_METERS = JC.ReadingRound.a_Meters;
    static final String ATTRIBUTE_READERS = JC.ReadingRound.a_Readers;
    static final String ATTRIBUTE_SUPERVISOR = JC.ReadingRound.a_Supervisor;
    static final String ATTRIBUTE_START_DATE = JC.ReadingRound.a_StartDate;
    static final String ATTRIBUTE_PERIOD = JC.ReadingRound.a_Period;
    static final String ATTRIBUTE_LEAD_DAYS = JC.ReadingRound.a_LeadDays;
    static final String ATTRIBUTE_REMINDER_DAYS = JC.ReadingRound.a_ReminderDays;
    static final String ATTRIBUTE_ESCALATION_DAYS = JC.ReadingRound.a_EscalationDays;
    static final String ATTRIBUTE_REMARKS = JC.ReadingRound.a_Remarks;
    static final String ATTRIBUTE_MAIL_STATE = JC.ReadingRound.a_MailState;
    /**
     * The attributes written by the web (all but "Mail State", written by the mails of E8.3); also those of the
     * version: a mail sent must not make the editor's version outdated
     */
    static final List<String> ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(ATTRIBUTE_ENABLED, ATTRIBUTE_METERS,
            ATTRIBUTE_READERS, ATTRIBUTE_SUPERVISOR, ATTRIBUTE_START_DATE, ATTRIBUTE_PERIOD, ATTRIBUTE_LEAD_DAYS,
            ATTRIBUTE_REMINDER_DAYS, ATTRIBUTE_ESCALATION_DAYS, ATTRIBUTE_REMARKS));
    static final List<String> PERIODS = Collections.unmodifiableList(Arrays.asList("P1W", "P2W", "P1M", "P3M", "P6M", "P1Y"));
    static final int DEFAULT_LEAD_DAYS = 3;
    static final int DEFAULT_REMINDER_DAYS = 3;
    static final int DEFAULT_ESCALATION_DAYS = 7;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_METERS = 500;
    static final int MAX_DAYS = 60;
    static final int MAX_REMARKS_LENGTH = 1000;
    static final String VERSION_NONE = ResourceWebEditRegisters.VERSION_NONE;
    static final String KIND_USER = "USER";
    static final String KIND_GROUP = "GROUP";
    static final String ACTION_CREATE = "round-create";
    static final String ACTION_SAVE = "round-save";
    static final String ACTION_DELETE = "round-delete";

    /**
     * The state of a round for its current due date, in the order of the list (most urgent first)
     */
    enum State {ESCALATED, LATE, OPEN, NOT_STARTED, DONE, INACTIVE}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Object CREATE_LOCK = new Object();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("reading-rounds")
    @Produces(MediaType.APPLICATION_JSON)
    public Response list(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang,
                         @QueryParam("site") Long site) {
        return WebEdit.run(httpHeaders, "load the reading rounds", store -> list(store, site, DateTime.now()));
    }

    @GET
    @Path("reading-rounds/options")
    @Produces(MediaType.APPLICATION_JSON)
    public Response options(@Context HttpHeaders httpHeaders, @QueryParam("site") Long site,
                            @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "load the reading round options", store -> options(store, site));
    }

    @POST
    @Path("reading-rounds")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response create(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang, String body) {
        return WebEdit.run(httpHeaders, "create a reading round", store -> create(store, body, DateTime.now()));
    }

    @PUT
    @Path("reading-rounds/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response update(@Context HttpHeaders httpHeaders, @PathParam("id") long id, @QueryParam("lang") String lang,
                           String body) {
        return WebEdit.run(httpHeaders, "change the reading round " + id, store -> update(store, id, body, DateTime.now()));
    }

    @DELETE
    @Path("reading-rounds/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version) {
        return WebEdit.run(httpHeaders, "delete the reading round " + id,
                store -> delete(store, id, version, DateTime.now()));
    }

    @GET
    @Path("reading-rounds/{id}/sheet.pdf")
    public Response sheet(@Context HttpHeaders httpHeaders, @Context UriInfo uriInfo, @PathParam("id") long id,
                          @QueryParam("lang") String lang, @QueryParam("due") String due) {
        String language = WebEdit.language(lang, httpHeaders);
        String appBase = appBase(ResourceWebRegisters.serverAddress(name -> {
            List<String> values = httpHeaders.getRequestHeader(name);
            return values != null && !values.isEmpty() ? ResourceWebRegisters.first(values.get(0)) : null;
        }, uriInfo.getRequestUri()));
        Response[] file = new Response[1];
        Response response = WebEdit.run(httpHeaders, "create the route sheet of " + id, store -> {
            WebEdit.Reply reply = sheet(store, id, language, due, appBase, DateTime.now());
            if (reply.body instanceof Pdf) {
                Pdf pdf = (Pdf) reply.body;
                file[0] = Response.ok(pdf.bytes).type("application/pdf")
                        .header("Content-Disposition", ResourceWebIso.contentDisposition(pdf.name, true))
                        .header("X-Content-Type-Options", "nosniff")
                        .header("Cache-Control", "private, no-store")
                        .build();
                return WebEdit.Reply.noContent();
            }
            return reply;
        });
        return file[0] != null ? file[0] : response;
    }

    /**
     * @param server the address of the web service ({@link ResourceWebRegisters#serverAddress}), null if unknown
     * @return the address without the default port of the scheme ({@code https://example.org}), "" if unknown
     */
    static String appBase(String server) {
        if (server == null) return "";
        if (server.startsWith("https://") && server.endsWith(":443")) return server.substring(0, server.length() - 4);
        if (server.startsWith("http://") && server.endsWith(":80")) return server.substring(0, server.length() - 3);
        return server;
    }

    /**
     * @return the link of the QR code of a meter on the route sheet: the reading page of the web app
     */
    static String meterLink(String appBase, long meterId) {
        return (appBase != null ? appBase : "") + "/app/readings?meter=" + meterId;
    }

    // ---------------------------------------------------------------- the data of a request

    /**
     * Objects, rights, the rows written automatically and the user's groups of one request (computed once).
     */
    static final class Session {
        final WebEdit.Store store;
        final ResourceWebModules.Lookup lookup;
        final ResourceWebRegisters.Classes classes;
        final WebEdit.Rights rights;
        final DateTime now;
        private Set<Long> automatic;
        private Set<Long> me;

        Session(WebEdit.Store store, DateTime now) throws Exception {
            this.store = store;
            this.lookup = store.lookup();
            this.classes = store.classes();
            this.rights = store.rights();
            this.now = now;
        }

        Set<Long> automatic() throws Exception {
            if (automatic == null) automatic = ResourceWebReadings.writtenAutomatically(store);
            return automatic;
        }

        Set<Long> me() throws Exception {
            if (me == null) {
                Set<Long> ids = store.userAndGroups();
                me = ids != null ? ids : Collections.emptySet();
            }
            return me;
        }
    }

    /**
     * The stored configuration of a round.
     */
    static final class Round {
        final JsonObject object;
        boolean enabled = true;
        List<Long> meters = new ArrayList<>();
        List<Long> readers = new ArrayList<>();
        List<Long> supervisor = new ArrayList<>();
        LocalDate start;
        String period;
        int leadDays = DEFAULT_LEAD_DAYS;
        int reminderDays = DEFAULT_REMINDER_DAYS;
        int escalationDays = DEFAULT_ESCALATION_DAYS;
        String remarks;

        Round(JsonObject object) {
            this.object = object;
        }
    }

    static Round read(WebEdit.Store store, JsonObject object) throws Exception {
        Round round = new Round(object);
        long id = object.getId();
        String enabled = value(store.latest(id, ATTRIBUTE_ENABLED));
        round.enabled = enabled == null || enabled.trim().isEmpty() || "1".equals(enabled.trim())
                || "true".equalsIgnoreCase(enabled.trim());
        round.meters = ids(value(store.latest(id, ATTRIBUTE_METERS)));
        round.readers = ids(value(store.latest(id, ATTRIBUTE_READERS)));
        round.supervisor = ids(value(store.latest(id, ATTRIBUTE_SUPERVISOR)));
        round.start = date(value(store.latest(id, ATTRIBUTE_START_DATE)));
        String period = value(store.latest(id, ATTRIBUTE_PERIOD));
        round.period = period != null && !period.trim().isEmpty() ? period.trim() : null;
        round.leadDays = days(value(store.latest(id, ATTRIBUTE_LEAD_DAYS)), DEFAULT_LEAD_DAYS);
        round.reminderDays = days(value(store.latest(id, ATTRIBUTE_REMINDER_DAYS)), DEFAULT_REMINDER_DAYS);
        round.escalationDays = days(value(store.latest(id, ATTRIBUTE_ESCALATION_DAYS)), DEFAULT_ESCALATION_DAYS);
        String remarks = value(store.latest(id, ATTRIBUTE_REMARKS));
        round.remarks = remarks != null && !remarks.trim().isEmpty() ? remarks : null;
        return round;
    }

    private static String value(JsonSample sample) {
        return sample != null ? sample.getValue() : null;
    }

    /**
     * @return the ids of a JSON array (numbers or numeric texts); empty if missing or invalid
     */
    static List<Long> ids(String json) {
        List<Long> ids = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return ids;
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isArray()) return ids;
            for (JsonNode element : node) {
                if (element.isIntegralNumber() && element.canConvertToLong()) {
                    ids.add(element.asLong());
                } else if (element.isTextual() && element.asText().trim().matches("\\d{1,18}")) {
                    ids.add(Long.parseLong(element.asText().trim()));
                }
            }
        } catch (Exception e) {
            /* not a JSON array: no ids */
        }
        return ids;
    }

    static String idsJson(List<Long> ids) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) json.append(',');
            json.append(ids.get(i));
        }
        return json.append(']').toString();
    }

    /**
     * @return the date of a {@code yyyy-MM-dd} text, null if missing or invalid
     */
    static LocalDate date(String text) {
        if (text == null || !DATE.matcher(text.trim()).matches()) return null;
        try {
            return LocalDate.parse(text.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int days(String text, int fallback) {
        if (text == null || text.trim().isEmpty()) return fallback;
        try {
            return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Math.round(Double.parseDouble(text.trim()))));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * @return the period of the text if it is positive (any ISO period, the web offers {@link #PERIODS}), else null
     */
    static Period period(String text) {
        Period period = ResourceWebEditValues.period(text);
        if (period == null || ReadingExpectation.isZero(period)) return null;
        for (int value : period.getValues()) {
            if (value < 0) return null;
        }
        return period;
    }

    // ---------------------------------------------------------------- order of a period

    /**
     * The current order of a round: {@code due} = the latest of start + n × period (n ≥ 0) that is not after today,
     * {@code nextDue} = start + (n + 1) × period (always counted from the start date, so a round starting on the 31st
     * is due on the last day of shorter months and on the 31st again); before the start {@code due} = null and
     * {@code nextDue} = the start date.
     */
    static final class Schedule {
        final LocalDate due;
        final LocalDate nextDue;

        Schedule(LocalDate due, LocalDate nextDue) {
            this.due = due;
            this.nextDue = nextDue;
        }
    }

    /**
     * @return the schedule; both null without start date or period
     */
    static Schedule schedule(LocalDate start, Period period, LocalDate today) {
        if (start == null || period == null) return new Schedule(null, null);
        if (today.isBefore(start)) return new Schedule(null, start);
        /* a first estimate by the length of the period in days, then step to the exact n */
        long approxDays = Math.max(1, period.getYears() * 365L + period.getMonths() * 30L + period.getWeeks() * 7L + period.getDays());
        long days = new org.joda.time.Duration(start.toDateTimeAtStartOfDay(DateTimeZone.UTC),
                today.toDateTimeAtStartOfDay(DateTimeZone.UTC)).getStandardDays();
        int n = (int) Math.max(0, Math.min(Integer.MAX_VALUE / 2, days / approxDays - 1));
        while (n > 0 && start.plus(period.multipliedBy(n)).isAfter(today)) n--;
        while (!start.plus(period.multipliedBy(n + 1)).isAfter(today)) n++;
        return new Schedule(start.plus(period.multipliedBy(n)), start.plus(period.multipliedBy(n + 1)));
    }

    static Schedule schedule(Round round, LocalDate today) {
        return schedule(round.start, period(round.period), today);
    }

    /**
     * @return the state of a round: {@code INACTIVE} (not enabled), {@code NOT_STARTED} (no due date),
     * {@code DONE} (all meters done), {@code ESCALATED} from due + escalation days, {@code LATE} from due + reminder
     * days, else {@code OPEN}
     */
    static State state(boolean enabled, LocalDate due, int done, int total, LocalDate today, int reminderDays,
                       int escalationDays) {
        if (!enabled) return State.INACTIVE;
        if (due == null) return State.NOT_STARTED;
        if (done >= total) return State.DONE;
        if (!today.isBefore(due.plusDays(escalationDays))) return State.ESCALATED;
        if (!today.isBefore(due.plusDays(reminderDays))) return State.LATE;
        return State.OPEN;
    }

    /**
     * One meter of a round with its state for the due date.
     */
    static final class MeterState {
        final JsonObject meter;
        final boolean readable;
        JsonObject row;
        boolean automatic;
        boolean done;
        JsonSample last;

        MeterState(JsonObject meter, boolean readable) {
            this.meter = meter;
            this.readable = readable;
        }
    }

    /**
     * The meters of the round (deleted ones and other objects left out, each id once) and whether each is done for
     * the due date: its row has a sample in [due − lead days, next due) in the time zone, or it is filled
     * automatically ({@link ResourceWebReadings#PROBLEM_AUTOMATIC}). Computed server-internally for all meters of
     * the round (also those the user may not read, which are only counted), so the state is the same for every user.
     */
    static List<MeterState> meterStates(Session context, Round round, LocalDate due, LocalDate nextDue,
                                        DateTimeZone zone) throws Exception {
        WebEdit.Store store = context.store;
        ResourceWebModules.Lookup lookup = context.lookup;
        List<MeterState> states = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : round.meters) {
            if (id == null || !seen.add(id)) continue;
            JsonObject meter = lookup.object(id);
            if (meter == null || ResourceWebIso.isDeleted(meter) || !ResourceWebEditRegisters.isMeter(context.classes, meter)) {
                continue;
            }
            MeterState state = new MeterState(meter, lookup.readable(id) != null);
            Long[] target = new Long[1];
            JsonObject row = null;
            if (ResourceWebRegisters.parseTarget(value(store.latest(id, ResourceWebRegisters.ATTRIBUTE_ONLINE_ID)), target, new String[1])) {
                row = lookup.object(target[0]);
                if (row != null && ResourceWebIso.isDeleted(row)) row = null;
            }
            state.row = row;
            if (row != null) {
                if (WebEdit.MANUAL_ROW_CLASSES.contains(row.getJevisClass())) {
                    Period rowPeriod = ResourceWebEditValues.period(value(store.latest(row.getId(), ResourceWebRegisters.ATTRIBUTE_PERIOD)));
                    List<JsonSample> samples = context.automatic().contains(row.getId()) || !ResourceWebReadings.subDaily(rowPeriod)
                            ? Collections.emptyList()
                            : store.latestSamples(row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE, ResourceWebReadings.READINGS);
                    state.automatic = ResourceWebReadings.isAutomatic(context.automatic(), row.getId(), rowPeriod,
                            samples != null ? samples : Collections.emptyList());
                }
                state.last = store.latest(row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE);
                state.done = state.automatic || (due != null && nextDue != null
                        && hasSample(store, row.getId(), due.minusDays(round.leadDays), nextDue, zone));
            }
            states.add(state);
        }
        return states;
    }

    /**
     * @return whether the row has a value sample in [from, to) (start of the days in the time zone)
     */
    static boolean hasSample(WebEdit.Store store, long rowId, LocalDate from, LocalDate to, DateTimeZone zone) throws Exception {
        DateTime start = from.toDateTimeAtStartOfDay(zone);
        DateTime end = to.toDateTimeAtStartOfDay(zone).minusMillis(1);
        if (end.isBefore(start)) return false;
        List<JsonSample> samples = store.samples(rowId, ResourceWebReadings.ATTRIBUTE_VALUE, start, end, 1);
        return samples != null && !samples.isEmpty();
    }

    // ---------------------------------------------------------------- objects

    static boolean isSite(JsonObject object) {
        return object != null && (ResourceWebNavigation.CLASS_BUILDING.equals(object.getJevisClass())
                || ResourceWebNavigation.CLASS_ORGANIZATION.equals(object.getJevisClass()));
    }

    /**
     * @return the readable round outside the recycle bin, else null
     */
    static JsonObject roundObject(WebEdit.Store store, long id) throws Exception {
        JsonObject object = WebEdit.readable(store, id);
        return object != null && CLASS_ROUND.equals(object.getJevisClass()) ? object : null;
    }

    /**
     * @return the first (lowest id) readable "Reading Round Directory" directly below the site outside the recycle
     * bin, null if there is none
     */
    static JsonObject roundDirectory(ResourceWebModules.Lookup lookup, JsonObject site) {
        JsonObject first = null;
        for (JsonObject child : lookup.readableChildren(site.getId(), Collections.singleton(CLASS_DIRECTORY))) {
            if (ResourceWebIso.isDeleted(child)) continue;
            if (first == null || child.getId() < first.getId()) first = child;
        }
        return first;
    }

    /**
     * @return the id of the site the round belongs to: the building or organisation its directory is in, else the
     * nearest readable site above it; null if none
     */
    static Long siteId(ResourceWebModules.Lookup lookup, JsonObject round) {
        Long directory = lookup.parent(round.getId());
        Long site = directory != null ? lookup.parent(directory) : null;
        if (site != null && isSite(lookup.object(site))) return site;
        Map<String, Object> nearest = lookup.site(round);
        return nearest != null && nearest.get("id") instanceof Number ? ((Number) nearest.get("id")).longValue() : null;
    }

    /**
     * @return the readable meters of the site in the order of the reading page ({@code GET /web/readings}): grouped by
     * "Measurement Directory" (group name), then by name
     */
    static List<JsonObject> siteMeters(Session context, Long siteId) {
        ResourceWebModules.Lookup lookup = context.lookup;
        Set<String> meterClasses = context.classes.withHeirs(ResourceWebRegisters.CLASS_MEASUREMENT_INSTRUMENT);
        List<Object[]> groups = new ArrayList<>();
        for (JsonObject directory : ResourceWebRegisters.readableOfClasses(lookup,
                Collections.singleton(ResourceWebRegisters.CLASS_MEASUREMENT_DIRECTORY))) {
            Map<String, Object> site = lookup.site(directory);
            if (siteId == null || site == null || !(site.get("id") instanceof Number)
                    || ((Number) site.get("id")).longValue() != siteId) {
                continue;
            }
            List<JsonObject> meters = ResourceWebRegisters.below(directory, meterClasses, lookup);
            meters.sort(Comparator.comparing((JsonObject m) -> lower(m.getName())).thenComparingLong(JsonObject::getId));
            JsonObject building = ResourceWebRegisters.nearestOfClass(directory, ResourceWebNavigation.CLASS_BUILDING, lookup);
            groups.add(new Object[]{lower(building != null ? building.getName() : directory.getName()),
                    String.valueOf(directory.getId()), meters});
        }
        groups.sort(Comparator.comparing((Object[] g) -> (String) g[0]).thenComparing(g -> (String) g[1]));
        Map<Long, JsonObject> result = new LinkedHashMap<>();
        for (Object[] group : groups) {
            @SuppressWarnings("unchecked")
            List<JsonObject> meters = (List<JsonObject>) group[2];
            for (JsonObject meter : meters) result.putIfAbsent(meter.getId(), meter);
        }
        return new ArrayList<>(result.values());
    }

    private static String lower(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    /**
     * @return whether the meter's row is filled automatically (E8.1 {@code AUTOMATIC}), server-internal
     */
    static boolean automatic(Session context, JsonObject meter) throws Exception {
        Round single = new Round(meter);
        single.meters = Collections.singletonList(meter.getId());
        List<MeterState> states = meterStates(context, single, null, null, DateTimeZone.getDefault());
        return !states.isEmpty() && states.get(0).automatic;
    }

    // ---------------------------------------------------------------- people

    /**
     * @return {@code USER}, {@code GROUP} or null for other classes
     */
    static String kind(JsonObject object) {
        if (object == null) return null;
        if (CLASS_USER.equals(object.getJevisClass())) return KIND_USER;
        if (CLASS_GROUP.equals(object.getJevisClass())) return KIND_GROUP;
        return null;
    }

    /**
     * @return the name of a user (first and last name, else the account) or group
     */
    static String personName(WebEdit.Store store, JsonObject object) throws Exception {
        if (CLASS_USER.equals(object.getJevisClass())) {
            String first = value(store.latest(object.getId(), "First Name"));
            String last = value(store.latest(object.getId(), "Last Name"));
            String name = ((first != null ? first.trim() : "") + " " + (last != null ? last.trim() : "")).trim();
            if (!name.isEmpty()) return name;
        }
        return object.getName();
    }

    /**
     * @return {@code {id, name, kind}} of a user or group, the name null if the user may not read it; null for ids
     * of other or deleted objects (left out)
     */
    static Map<String, Object> person(Session context, Long id) throws Exception {
        JsonObject object = id != null ? context.lookup.object(id) : null;
        if (object == null || ResourceWebIso.isDeleted(object) || kind(object) == null) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", context.lookup.readable(id) != null ? personName(context.store, object) : null);
        json.put("kind", kind(object));
        return json;
    }

    private static List<Map<String, Object>> people(Session context, List<Long> ids) throws Exception {
        List<Map<String, Object>> people = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || !seen.add(id)) continue;
            Map<String, Object> person = person(context, id);
            if (person != null) people.add(person);
        }
        return people;
    }

    // ---------------------------------------------------------------- version

    /**
     * @return the latest change of the round: the time of the latest sample of its attributes (without "Mail State"),
     * ISO; {@value ResourceWebEditRegisters#VERSION_NONE} without samples
     */
    static String version(WebEdit.Store store, long id) throws Exception {
        long max = Long.MIN_VALUE;
        for (String attribute : ATTRIBUTES) {
            JsonSample latest = store.latest(id, attribute);
            Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (millis != null) max = Math.max(max, millis);
        }
        return max != Long.MIN_VALUE ? WebEdit.iso(max, DateTimeZone.getDefault()) : VERSION_NONE;
    }

    /**
     * @throws WebEdit.Stop {@code 400} if missing or invalid
     */
    static boolean sameVersion(String given, String current) throws WebEdit.Stop {
        if (given == null || given.trim().isEmpty()) throw WebEdit.Stop.badRequest("version missing");
        String text = given.trim();
        if (VERSION_NONE.equals(text) || VERSION_NONE.equals(current)) return text.equals(current);
        try {
            return new DateTime(text).getMillis() == new DateTime(current).getMillis();
        } catch (IllegalArgumentException e) {
            throw WebEdit.Stop.badRequest("Invalid version");
        }
    }

    // ---------------------------------------------------------------- GET list

    /**
     * One round of {@code GET /reading-rounds} (also the answer of create, change and conflicts).
     */
    static Map<String, Object> roundJson(Session context, JsonObject object) throws Exception {
        WebEdit.Store store = context.store;
        Round round = read(store, object);
        DateTimeZone zone = store.workDay(object).zone;
        LocalDate today = context.now.withZone(zone).toLocalDate();
        Schedule schedule = schedule(round, today);
        List<MeterState> states = meterStates(context, round, schedule.due, schedule.nextDue, zone);
        int done = 0;
        for (MeterState state : states) {
            if (state.done) done++;
        }
        State roundState = state(round.enabled, schedule.due, done, states.size(), today, round.reminderDays,
                round.escalationDays);

        List<Map<String, Object>> meters = new ArrayList<>();
        for (MeterState state : states) meters.add(meterJson(context, state, zone));
        boolean mine = false;
        for (Long reader : round.readers) mine |= context.me().contains(reader);

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("site", context.lookup.site(object));
        json.put("directoryId", context.lookup.parent(object.getId()));
        json.put("enabled", round.enabled);
        json.put("period", round.period);
        json.put("startDate", round.start != null ? round.start.toString() : null);
        json.put("leadDays", round.leadDays);
        json.put("reminderDays", round.reminderDays);
        json.put("escalationDays", round.escalationDays);
        json.put("remarks", round.remarks);
        json.put("readers", people(context, round.readers));
        json.put("supervisor", people(context, round.supervisor));
        json.put("due", schedule.due != null ? schedule.due.toString() : null);
        json.put("nextDue", schedule.nextDue != null ? schedule.nextDue.toString() : null);
        json.put("state", roundState.name());
        json.put("done", done);
        json.put("total", states.size());
        json.put("meters", meters);
        json.put("mine", mine);
        json.put("canWrite", context.rights.canWrite(object));
        json.put("canDelete", context.rights.canDelete(object));
        json.put("version", version(store, object.getId()));
        return json;
    }

    /**
     * @return the meter of a round: {@code {id, readable: false}} if the user may not read it
     */
    static Map<String, Object> meterJson(Session context, MeterState state, DateTimeZone zone) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", state.meter.getId());
        if (!state.readable) {
            json.put("readable", false);
            return json;
        }
        json.put("name", state.meter.getName());
        String location = value(context.store.latest(state.meter.getId(), ResourceWebReadings.ATTRIBUTE_LOCATION));
        json.put("location", location != null && !location.trim().isEmpty() ? location.trim() : null);
        json.put("done", state.done);
        json.put("automatic", state.automatic);
        Map<String, Object> last = null;
        Long millis = state.last != null ? ResourceWebNotes.millis(state.last.getTs()) : null;
        if (millis != null && state.row != null && context.lookup.readable(state.row.getId()) != null) {
            last = new LinkedHashMap<>();
            last.put("timestamp", WebEdit.iso(millis, zone));
            last.put("value", ResourceWebRegisters.number(state.last.getValue()));
            last.put("unit", context.store.unit(state.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE));
        }
        json.put("last", last);
        json.put("readable", true);
        return json;
    }

    /**
     * {@code GET /reading-rounds?site=…}: the readable rounds, "mine" first, then by state and name.
     */
    static WebEdit.Reply list(WebEdit.Store store, Long site, DateTime now) throws Exception {
        Session context = new Session(store, now);
        List<Map<String, Object>> rounds = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(context.lookup, Collections.singleton(CLASS_ROUND))) {
            if (site != null) {
                Map<String, Object> siteJson = context.lookup.site(object);
                if (siteJson == null || !(siteJson.get("id") instanceof Number)
                        || ((Number) siteJson.get("id")).longValue() != site) {
                    continue;
                }
            }
            rounds.add(roundJson(context, object));
        }
        rounds.sort(Comparator.comparing((Map<String, Object> r) -> !(Boolean) r.get("mine"))
                .thenComparing(r -> State.valueOf((String) r.get("state")).ordinal())
                .thenComparing(r -> lower((String) r.get("name")))
                .thenComparingLong(r -> (Long) r.get("id")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", now.toLocalDate().toString());
        result.put("rounds", rounds);
        return WebEdit.Reply.ok(result);
    }

    // ---------------------------------------------------------------- options

    /**
     * @return the readable site (building or organisation outside the recycle bin)
     * @throws WebEdit.Stop {@code 400} without id, {@code 404} if not found
     */
    static JsonObject site(WebEdit.Store store, Long id) throws Exception {
        if (id == null) throw new WebEdit.Stop(new WebEdit.Reply(400, Collections.singletonMap("messages",
                Collections.singletonList("Missing site"))));
        JsonObject site = WebEdit.readable(store, id);
        if (!isSite(site)) throw WebEdit.Stop.notFound("Site not found");
        return site;
    }

    /**
     * @return whether the user may create a round at the site: in its directory, or (without one) by creating the
     * directory
     */
    static boolean canCreate(Session context, JsonObject site, JsonObject directory) {
        if (!context.classes.exists(CLASS_ROUND)) return false;
        if (directory != null) return context.rights.canCreate(directory, CLASS_ROUND);
        return context.classes.exists(CLASS_DIRECTORY) && context.rights.canCreate(site, CLASS_DIRECTORY);
    }

    /**
     * {@code GET /reading-rounds/options?site=…}
     */
    static WebEdit.Reply options(WebEdit.Store store, Long siteId) throws Exception {
        Session context = new Session(store, DateTime.now());
        JsonObject site = site(store, siteId);
        JsonObject directory = roundDirectory(context.lookup, site);

        List<Map<String, Object>> meters = new ArrayList<>();
        for (JsonObject meter : siteMeters(context, site.getId())) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", meter.getId());
            json.put("name", meter.getName());
            String location = value(store.latest(meter.getId(), ResourceWebReadings.ATTRIBUTE_LOCATION));
            json.put("location", location != null && !location.trim().isEmpty() ? location.trim() : null);
            json.put("medium", ResourceWebRegisters.medium(meter.getJevisClass(), context.classes));
            json.put("automatic", automatic(context, meter));
            meters.add(json);
        }

        List<Map<String, Object>> people = new ArrayList<>();
        for (JsonObject object : context.lookup.readable()) {
            String kind = kind(object);
            if (kind == null || ResourceWebIso.isDeleted(object)) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", object.getId());
            json.put("name", personName(store, object));
            json.put("kind", kind);
            String mail = KIND_USER.equals(kind) ? value(store.latest(object.getId(), "E-Mail")) : null;
            json.put("email", mail != null && !mail.trim().isEmpty());
            people.add(json);
        }
        people.sort(Comparator.comparing((Map<String, Object> p) -> lower((String) p.get("name")))
                .thenComparingLong(p -> (Long) p.get("id")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("canCreate", canCreate(context, site, directory));
        result.put("directoryId", directory != null ? directory.getId() : null);
        result.put("meters", meters);
        result.put("people", people);
        return WebEdit.Reply.ok(result);
    }

    // ---------------------------------------------------------------- input

    /**
     * The validated body of a create or change.
     */
    static final class Input {
        String name;
        boolean enabled = true;
        List<Long> meters = new ArrayList<>();
        List<Long> readers = new ArrayList<>();
        List<Long> supervisor = new ArrayList<>();
        LocalDate start;
        String period;
        int leadDays = DEFAULT_LEAD_DAYS;
        int reminderDays = DEFAULT_REMINDER_DAYS;
        int escalationDays = DEFAULT_ESCALATION_DAYS;
        String remarks;

        /**
         * @return the stored values by attribute (the attributes of {@link #ATTRIBUTES})
         */
        Map<String, String> stored() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put(ATTRIBUTE_ENABLED, String.valueOf(enabled));
            values.put(ATTRIBUTE_METERS, idsJson(meters));
            values.put(ATTRIBUTE_READERS, idsJson(readers));
            values.put(ATTRIBUTE_SUPERVISOR, idsJson(supervisor));
            values.put(ATTRIBUTE_START_DATE, start != null ? start.toString() : "");
            values.put(ATTRIBUTE_PERIOD, period != null ? period : "");
            values.put(ATTRIBUTE_LEAD_DAYS, String.valueOf(leadDays));
            values.put(ATTRIBUTE_REMINDER_DAYS, String.valueOf(reminderDays));
            values.put(ATTRIBUTE_ESCALATION_DAYS, String.valueOf(escalationDays));
            values.put(ATTRIBUTE_REMARKS, remarks != null ? remarks : "");
            return values;
        }
    }

    /**
     * @return the shortest length of the period in days ({@code P1M}: 28), for "lead days shorter than the period"
     */
    static int minDays(Period period) {
        return period.getYears() * 365 + period.getMonths() * 28 + period.getWeeks() * 7 + period.getDays();
    }

    /**
     * Validates the body; every problem is added to the messages.
     *
     * @param directory the round directory of the site, null if there is none yet
     * @param current   the round being changed (missing optional fields keep its values; its unreadable meters and
     *                  people may stay), null for a new round
     */
    static Input input(Session context, JsonNode json, Long siteId, JsonObject directory, Round current,
                       List<String> messages) throws Exception {
        Input input = new Input();
        input.name = name(context, json, directory, current, messages);

        JsonNode enabled = json.get("enabled");
        if (enabled == null || enabled.isNull()) {
            input.enabled = current == null || current.enabled;
        } else if (!enabled.isBoolean()) {
            messages.add("Invalid enabled");
        } else {
            input.enabled = enabled.asBoolean();
        }

        /* meters: readable meters of the site (meters the user may not read may stay in a round being changed) */
        input.meters = idList(json, "meters", true, messages);
        if (input.meters != null) {
            if (input.meters.isEmpty()) messages.add("At least one meter is needed");
            if (input.meters.size() > MAX_METERS) messages.add("At most " + MAX_METERS + " meters");
            Set<Long> siteMeters = new HashSet<>();
            for (JsonObject meter : siteMeters(context, siteId)) siteMeters.add(meter.getId());
            Set<Long> seen = new HashSet<>();
            for (Long id : input.meters) {
                if (!seen.add(id)) {
                    messages.add("Meter " + id + " is listed twice");
                } else if (!siteMeters.contains(id) && !kept(context, current != null ? current.meters : null, id)) {
                    messages.add("Meter " + id + " is no readable meter of the site");
                }
            }
        } else {
            input.meters = new ArrayList<>();
        }

        input.readers = people(context, json, "readers", true, current != null ? current.readers : null, messages);
        if (input.readers != null && input.readers.isEmpty() && json.get("readers") != null && json.get("readers").isArray()) {
            messages.add("At least one reader is needed");
        }
        if (input.readers == null) input.readers = new ArrayList<>();
        if (json.get("supervisor") == null || json.get("supervisor").isNull()) {
            input.supervisor = current != null ? new ArrayList<>(current.supervisor) : new ArrayList<>();
        } else {
            input.supervisor = people(context, json, "supervisor", false, current != null ? current.supervisor : null, messages);
            if (input.supervisor == null) input.supervisor = new ArrayList<>();
        }

        JsonNode start = json.get("startDate");
        if (start == null || start.isNull() || (start.isTextual() && start.asText().trim().isEmpty())) {
            messages.add("Missing startDate");
        } else if (!start.isTextual() || (input.start = date(start.asText())) == null) {
            messages.add("Invalid startDate (yyyy-MM-dd)");
        }

        JsonNode period = json.get("period");
        if (period == null || period.isNull() || (period.isTextual() && period.asText().trim().isEmpty())) {
            messages.add("Missing period");
        } else if (!period.isTextual() || !PERIODS.contains(period.asText().trim())) {
            messages.add("Invalid period (" + String.join(", ", PERIODS) + ")");
        } else {
            input.period = period.asText().trim();
        }

        input.leadDays = days(json, "leadDays", current != null ? current.leadDays : DEFAULT_LEAD_DAYS, messages);
        input.reminderDays = days(json, "reminderDays", current != null ? current.reminderDays : DEFAULT_REMINDER_DAYS, messages);
        input.escalationDays = days(json, "escalationDays", current != null ? current.escalationDays : DEFAULT_ESCALATION_DAYS, messages);
        if (input.period != null && input.leadDays >= minDays(Period.parse(input.period))) {
            messages.add("leadDays must be shorter than the period");
        }

        JsonNode remarks = json.get("remarks");
        if (remarks == null) {
            input.remarks = current != null ? current.remarks : null;
        } else if (!remarks.isNull()) {
            if (!remarks.isTextual()) {
                messages.add("Invalid remarks");
            } else {
                String text = remarks.asText().trim();
                if (text.length() > MAX_REMARKS_LENGTH) {
                    messages.add("Remarks longer than " + MAX_REMARKS_LENGTH + " characters");
                } else if (hasControl(text, true)) {
                    messages.add("Invalid characters in remarks");
                } else {
                    input.remarks = text.isEmpty() ? null : text;
                }
            }
        }
        return input;
    }

    private static boolean kept(Session context, List<Long> currentIds, Long id) {
        return currentIds != null && currentIds.contains(id) && context.lookup.readable(id) == null;
    }

    private static boolean hasControl(String text, boolean allowLineBreaks) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (allowLineBreaks && (c == '\n' || c == '\r' || c == '\t')) continue;
            if (Character.isISOControl(c)) return true;
        }
        return false;
    }

    private static String name(Session context, JsonNode json, JsonObject directory, Round current,
                               List<String> messages) {
        JsonNode node = json.get("name");
        if (node == null || node.isNull()) {
            messages.add("Missing name");
            return null;
        }
        if (!node.isTextual()) {
            messages.add("Invalid name");
            return null;
        }
        String name = node.asText().trim();
        if (name.isEmpty()) {
            messages.add("Missing name");
        } else if (name.length() > MAX_NAME_LENGTH) {
            messages.add("Name longer than " + MAX_NAME_LENGTH + " characters");
        } else if (hasControl(name, false)) {
            messages.add("Invalid characters in name");
        } else if (directory != null) {
            /* unique among all rounds of the directory, also those the user may not read */
            for (JsonObject other : context.lookup.all()) {
                if (!CLASS_ROUND.equals(other.getJevisClass()) || ResourceWebIso.isDeleted(other)) continue;
                if (current != null && other.getId() == current.object.getId()) continue;
                Long parent = context.lookup.parent(other.getId());
                if (parent != null && parent == directory.getId() && name.equalsIgnoreCase(String.valueOf(other.getName()).trim())) {
                    messages.add("A round named \"" + name + "\" already exists");
                    break;
                }
            }
        }
        return name;
    }

    /**
     * @return the ids of the array field, null if missing (with a message if required) or invalid
     */
    private static List<Long> idList(JsonNode json, String field, boolean required, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) {
            if (required) messages.add("Missing " + field);
            return null;
        }
        if (!node.isArray()) {
            messages.add("Invalid " + field);
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isIntegralNumber() || !element.canConvertToLong()) {
                messages.add("Invalid " + field);
                return null;
            }
            ids.add(element.asLong());
        }
        return ids;
    }

    /**
     * @return the ids of readable users and groups (or unreadable ones that stay in the round being changed)
     */
    private static List<Long> people(Session context, JsonNode json, String field, boolean required, List<Long> current,
                                     List<String> messages) {
        List<Long> ids = idList(json, field, required, messages);
        if (ids == null) return null;
        Set<Long> seen = new HashSet<>();
        for (Long id : ids) {
            JsonObject object = context.lookup.readable(id);
            if (object != null && ResourceWebIso.isDeleted(object)) object = null;
            if (!seen.add(id)) {
                messages.add(field + ": " + id + " is listed twice");
            } else if ((object == null || kind(object) == null) && !kept(context, current, id)) {
                messages.add(field + ": " + id + " is no readable user or group");
            }
        }
        return ids;
    }

    private static int days(JsonNode json, String field, int fallback, List<String> messages) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return fallback;
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            messages.add("Invalid " + field);
            return fallback;
        }
        int days = node.asInt();
        if (days < 0 || days > MAX_DAYS) {
            messages.add(field + " must be between 0 and " + MAX_DAYS);
            return fallback;
        }
        return days;
    }

    private static Long longField(JsonNode json, String field) {
        JsonNode node = json.get(field);
        if (node == null || node.isNull()) return null;
        if (node.isIntegralNumber() && node.canConvertToLong()) return node.asLong();
        if (node.isTextual() && node.asText().trim().matches("\\d{1,18}")) return Long.parseLong(node.asText().trim());
        return null;
    }

    private static void write(WebEdit.Store store, JsonObject round, Map<String, String> values, DateTime time,
                              String action) throws Exception {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String attribute = entry.getKey();
            int fallback = ATTRIBUTE_ENABLED.equals(attribute) ? JEVisConstants.PrimitiveType.BOOLEAN
                    : attribute.endsWith(" Days") ? JEVisConstants.PrimitiveType.LONG : JEVisConstants.PrimitiveType.STRING;
            WebEdit.writeSample(store, round.getId(), attribute, WebEdit.primitiveType(store, round, attribute, fallback),
                    WebEdit.sample(time, entry.getValue(), null), action);
        }
    }

    // ---------------------------------------------------------------- POST

    /**
     * {@code POST /reading-rounds} with {@code {site, name, enabled, meters, readers, supervisor, startDate, period,
     * leadDays, reminderDays, escalationDays, remarks}}: creates the round (and the site's "Reading Round Directory"
     * if missing; removed again if the round fails) with all attributes at now.
     */
    static WebEdit.Reply create(WebEdit.Store store, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        Session context = new Session(store, now);
        Long siteId = longField(json, "site");
        if (siteId == null) {
            throw ResourceWebEditIso.invalid(Collections.singletonList(json.has("site") && !json.get("site").isNull()
                    ? "Invalid site" : "Missing site"));
        }
        JsonObject site = site(store, siteId);
        ResourceWebModules.Lookup lookup = context.lookup;
        JsonObject directory = roundDirectory(lookup, site);

        List<String> messages = new ArrayList<>();
        if (!context.classes.exists(CLASS_ROUND)) messages.add("Unknown class: " + CLASS_ROUND);
        if (directory == null && !context.classes.exists(CLASS_DIRECTORY)) messages.add("Unknown class: " + CLASS_DIRECTORY);
        Input input = input(context, json, site.getId(), directory, null, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        if (!canCreate(context, site, directory)) return WebEdit.Reply.forbidden("Not allowed to create reading rounds here");

        synchronized (CREATE_LOCK) {
            JsonObject createdDirectory = null;
            JsonObject round = null;
            try {
                JsonObject target = roundDirectory(lookup, site);
                if (target == null) {
                    createdDirectory = store.createObject(CLASS_DIRECTORY, CLASS_DIRECTORY, site.getId());
                    store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT,
                            WebEdit.objectLog(createdDirectory.getId(), createdDirectory.getName(), ACTION_CREATE));
                    lookup.addReadable(createdDirectory, site.getId());
                    target = createdDirectory;
                    if (!context.rights.canCreate(target, CLASS_ROUND)) {
                        throw new WebEdit.Stop(WebEdit.Reply.forbidden("Not allowed to create reading rounds here"));
                    }
                }
                round = store.createObject(input.name, CLASS_ROUND, target.getId());
                store.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(round.getId(), round.getName(), ACTION_CREATE));
                lookup.addReadable(round, target.getId());
                write(store, round, input.stored(), now.withMillisOfSecond(0), ACTION_CREATE);
            } catch (Exception e) {
                rollback(store, round);
                rollback(store, createdDirectory);
                throw e;
            }
            return WebEdit.Reply.created(roundJson(context, round));
        }
    }

    private static void rollback(WebEdit.Store store, JsonObject object) {
        if (object == null) return;
        try {
            store.deleteObject(object);
            store.log(SQLDataSource.LOG_EVENT.DELETE_OBJECT,
                    WebEdit.objectLog(object.getId(), object.getName(), ACTION_CREATE + "-rollback"));
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger(ResourceWebReadingRounds.class)
                    .error("Could not remove {} after a failed create", object.getId(), e);
        }
    }

    // ---------------------------------------------------------------- PUT

    /**
     * {@code PUT /reading-rounds/{id}} with the fields of the create (without {@code site}) and {@code version}:
     * writes the changed attributes at now, may rename.
     */
    static WebEdit.Reply update(WebEdit.Store store, long id, String body, DateTime now) throws Exception {
        JsonNode json = WebEdit.body(body);
        Session context = new Session(store, now);
        JsonObject object = roundObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Round not found");
        if (!context.rights.canWrite(object)) return WebEdit.Reply.forbidden("Not allowed to change this round");
        Round current = read(store, object);
        Long parent = context.lookup.parent(object.getId());
        JsonObject directory = parent != null ? context.lookup.object(parent) : null;

        List<String> messages = new ArrayList<>();
        Input input = input(context, json, siteId(context.lookup, object), directory, current, messages);
        if (!messages.isEmpty()) throw ResourceWebEditIso.invalid(messages);
        JsonNode version = json.get("version");
        if (version != null && !version.isNull() && !version.isTextual()) throw WebEdit.Stop.badRequest("Invalid version");
        if (!sameVersion(version != null && !version.isNull() ? version.asText() : null, version(store, id))) {
            return WebEdit.Reply.conflict(roundJson(context, object));
        }

        Map<String, String> changed = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : input.stored().entrySet()) {
            String stored = value(store.latest(id, entry.getKey()));
            if (!entry.getValue().equals(stored != null ? stored : "")) changed.put(entry.getKey(), entry.getValue());
        }
        if (!changed.isEmpty()) {
            write(store, object, changed, ResourceWebEditRegisters.saveTime(store, id, changed.keySet(), now), ACTION_SAVE);
        }
        if (!input.name.equals(object.getName())) {
            store.rename(object, input.name);
            store.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, input.name, ACTION_SAVE));
            object.setName(input.name);
        }
        return WebEdit.Reply.ok(roundJson(context, object));
    }

    // ---------------------------------------------------------------- DELETE

    /**
     * {@code DELETE /reading-rounds/{id}?version=…}: to the recycle bin.
     */
    static WebEdit.Reply delete(WebEdit.Store store, long id, String version, DateTime now) throws Exception {
        Session context = new Session(store, now);
        JsonObject object = roundObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Round not found");
        if (!context.rights.canDelete(object)) return WebEdit.Reply.forbidden("Not allowed to delete this round");
        if (!sameVersion(version, version(store, id))) return WebEdit.Reply.conflict(roundJson(context, object));
        store.recycle(object);
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, object.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- readings of a round

    /**
     * {@code GET /readings?round=…}: as {@link ResourceWebReadings#list} with only the round's readable meters in
     * round order in one group named after the round, {@code doneInRound} per meter and the round's
     * {@code {id, name, due, nextDue, state}}.
     */
    static WebEdit.Reply readings(WebEdit.Store store, ResourceWebRegisters.Translations translations, long roundId,
                                  DateTime now) throws Exception {
        Session context = new Session(store, now);
        JsonObject object = roundObject(store, roundId);
        if (object == null) return WebEdit.Reply.notFound("Round not found");
        Round round = read(store, object);
        DateTimeZone zone = store.workDay(object).zone;
        LocalDate today = now.withZone(zone).toLocalDate();
        Schedule schedule = schedule(round, today);
        List<MeterState> states = meterStates(context, round, schedule.due, schedule.nextDue, zone);
        int done = 0;
        for (MeterState state : states) {
            if (state.done) done++;
        }
        ResourceWebRegisters.Data data = store.registerData();
        List<Map<String, Object>> meters = new ArrayList<>();
        for (MeterState state : states) {
            if (!state.readable) continue;
            Map<String, Object> json = ResourceWebReadings.meterJson(store, translations, data, state.meter, now,
                    context.automatic());
            json.put("doneInRound", state.done);
            meters.add(json);
        }
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("key", String.valueOf(object.getId()));
        group.put("name", object.getName());
        group.put("site", context.lookup.site(object));
        group.put("meters", meters);

        Map<String, Object> roundJson = new LinkedHashMap<>();
        roundJson.put("id", object.getId());
        roundJson.put("name", object.getName());
        roundJson.put("due", schedule.due != null ? schedule.due.toString() : null);
        roundJson.put("nextDue", schedule.nextDue != null ? schedule.nextDue.toString() : null);
        roundJson.put("state", state(round.enabled, schedule.due, done, states.size(), today, round.reminderDays,
                round.escalationDays).name());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", now.toLocalDate().toString());
        result.put("round", roundJson);
        result.put("groups", Collections.singletonList(group));
        return WebEdit.Reply.ok(result);
    }

    // ---------------------------------------------------------------- route sheet

    /**
     * The route sheet for {@code GET /reading-rounds/{id}/sheet.pdf}.
     */
    static final class Pdf {
        final byte[] bytes;
        final String name;

        Pdf(byte[] bytes, String name) {
            this.bytes = bytes;
            this.name = name;
        }
    }

    static String periodLabel(String period, String language) {
        boolean de = "de".equals(language);
        if (period == null) return null;
        switch (period) {
            case "P1W":
                return de ? "wöchentlich" : "weekly";
            case "P2W":
                return de ? "alle 2 Wochen" : "every 2 weeks";
            case "P1M":
                return de ? "monatlich" : "monthly";
            case "P3M":
                return de ? "vierteljährlich" : "quarterly";
            case "P6M":
                return de ? "halbjährlich" : "half-yearly";
            case "P1Y":
                return de ? "jährlich" : "yearly";
            default:
                return period;
        }
    }

    static String formatDate(LocalDate date, String language) {
        if (date == null) return null;
        return DateTimeFormat.forPattern("de".equals(language) ? "dd.MM.yyyy" : "yyyy-MM-dd").print(date);
    }

    /**
     * {@code GET /reading-rounds/{id}/sheet.pdf?due=…}
     *
     * @param dueText the due date ({@code yyyy-MM-dd}); null for the round's current due date, else its next one
     * @param appBase the address of the web app for the QR codes ({@link #appBase})
     * @return {@code 200} with a {@link Pdf}, else the error
     */
    static WebEdit.Reply sheet(WebEdit.Store store, long id, String language, String dueText, String appBase,
                               DateTime now) throws Exception {
        Session context = new Session(store, now);
        JsonObject object = roundObject(store, id);
        if (object == null) return WebEdit.Reply.notFound("Round not found");
        Round round = read(store, object);
        DateTimeZone zone = store.workDay(object).zone;
        LocalDate due;
        if (dueText != null && !dueText.trim().isEmpty()) {
            due = date(dueText);
            if (due == null) return WebEdit.Reply.badRequest("Invalid due (yyyy-MM-dd)");
        } else {
            Schedule schedule = schedule(round, now.withZone(zone).toLocalDate());
            due = schedule.due != null ? schedule.due : schedule.nextDue;
        }
        Period period = period(round.period);
        LocalDate nextDue = due != null && period != null ? due.plus(period) : null;
        List<MeterState> states = meterStates(context, round, due, nextDue, zone);

        RouteSheetPdf.Sheet sheet = new RouteSheetPdf.Sheet();
        sheet.language = language;
        sheet.roundName = object.getName();
        Map<String, Object> site = context.lookup.site(object);
        sheet.site = site != null ? String.valueOf(site.get("name")) : null;
        sheet.due = formatDate(due, language);
        sheet.period = periodLabel(round.period, language);
        for (Map<String, Object> reader : people(context, round.readers)) {
            if (reader.get("name") != null) sheet.readers.add((String) reader.get("name"));
        }
        sheet.remarks = round.remarks;
        sheet.printed = formatDate(now.withZone(zone).toLocalDate(), language);

        ResourceWebRegisters.Data data = store.registerData();
        int position = 0;
        for (MeterState state : states) {
            if (!state.readable) {
                sheet.hidden++;
                continue;
            }
            if (state.automatic) continue;
            JsonObject meter = state.meter;
            Map<String, JsonAttribute> attributes = data.attributes(meter.getId());
            RouteSheetPdf.Row row = new RouteSheetPdf.Row();
            row.position = ++position;
            row.name = meter.getName();
            row.location = text(attributes, ResourceWebReadings.ATTRIBUTE_LOCATION);
            row.serialNumber = text(attributes, ResourceWebReadings.ATTRIBUTE_SERIAL_NUMBER);
            row.meterPoint = text(attributes, ResourceWebReadings.ATTRIBUTE_METER_POINT);
            row.link = meterLink(appBase, meter.getId());

            ResourceWebReadings.Target target = ResourceWebReadings.target(store, meter, attributes);
            if (target.row != null) {
                Integer decimals = ValueFormat.parse(ResourceWebRegisters.text(attributes, ValueFormat.ATTRIBUTE_NAME));
                int places = decimals != null ? decimals : 3;
                String unit = store.unit(target.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE);
                String unitText = unit != null && !unit.trim().isEmpty() ? " " + unit.trim() : "";
                List<JsonSample> samples = store.latestSamples(target.row.getId(), ResourceWebReadings.ATTRIBUTE_VALUE,
                        ResourceWebReadings.READINGS);
                if (samples == null) samples = Collections.emptyList();
                JsonSample last = samples.isEmpty() ? null : samples.get(0);
                Long lastMillis = last != null ? ResourceWebNotes.millis(last.getTs()) : null;
                Double lastValue = last != null ? ResourceWebEditValues.parseDouble(last.getValue()) : null;
                if (lastMillis != null && lastValue != null) {
                    row.last = ResourceWebReadings.format(lastValue, language, places) + unitText + " ("
                            + formatDate(new DateTime(lastMillis, zone).toLocalDate(), language) + ")";
                }
                if (due != null) {
                    ResourceWebEditValues.Info info = ResourceWebEditValues.info(store, target.row);
                    ReadingExpectation.Expected expected = ReadingExpectation.estimate(info.counter,
                            ResourceWebReadings.hasPeriod(info), ResourceWebReadings.readings(samples),
                            due.toDateTimeAtStartOfDay(zone).getMillis());
                    if (expected != null) {
                        row.expected = ResourceWebReadings.format(expected.low, language, places) + " … "
                                + ResourceWebReadings.format(expected.high, language, places) + unitText;
                    }
                }
            }
            sheet.rows.add(row);
        }
        byte[] bytes = RouteSheetPdf.render(sheet);
        String name = "Laufzettel_" + object.getName() + "_" + (due != null ? due.toString() : "") + ".pdf";
        return WebEdit.Reply.ok(new Pdf(bytes, name));
    }

    private static String text(Map<String, JsonAttribute> attributes, String name) {
        String text = ResourceWebRegisters.text(attributes, name);
        return text != null && !text.trim().isEmpty() ? text.trim() : null;
    }
}
