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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.datatype.scheduler.SchedulerHandler;
import org.jevis.commons.datatype.scheduler.cron.CronScheduler;
import org.jevis.commons.datatype.scheduler.handler.SchedulerContainsHandler;
import org.jevis.commons.json.JsonAlarmConfig;
import org.jevis.commons.json.JsonLimitsConfig;
import org.jevis.commons.json.JsonScheduler;
import org.jevis.commons.json.JsonSchedulerRule;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.*;

import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

/**
 * Stage E7: alarm configurations (E7.1) and the alarm settings of a row (E7.2).
 */
public class ResourceWebEditAlarmsTest {

    private static final DateTimeZone ZONE = FakeEditStore.ZONE;
    private static final DateTime NOW = new DateTime(2026, 11, 16, 10, 7, 30, 123, ZONE);
    private static final DateTime T1 = new DateTime(2026, 11, 10, 9, 0, 0, ZONE);
    private static final String CONFIG = ResourceWebEditAlarms.CLASS_CONFIGURATION;
    private static final String DIRECTORY = ResourceWebEditAlarms.CLASS_DIRECTORY;
    private static final String MAIL = ResourceWebEditAlarms.CLASS_EMAIL;
    private static final String CLEAN = "Clean Data";
    private static final int S = JEVisConstants.PrimitiveType.STRING;
    private static final int B = JEVisConstants.PrimitiveType.BOOLEAN;

    // ---------------------------------------------------------------- Control Center formats

    private static JsonSchedulerRule rule(String id, String months, String dayOfMonth, String days, int sh, int sm, int eh, int em) {
        JsonSchedulerRule rule = new JsonSchedulerRule();
        rule.setId(id);
        rule.setMonths(months);
        rule.setDayOfMonth(dayOfMonth);
        rule.setDayOfWeek(days);
        rule.setStartTimeHours(String.valueOf(sh));
        rule.setStartTimeMinutes(String.valueOf(sm));
        rule.setEndTimeHours(String.valueOf(eh));
        rule.setEndTimeMinutes(String.valueOf(em));
        return rule;
    }

    /**
     * "Limits Configuration" as the Control Center's {@code LimitEditor} writes it
     */
    static String ccLimits() {
        JsonLimitsConfig step1 = new JsonLimitsConfig();
        step1.setName("Stufe 1");
        step1.setMin("10.0");
        step1.setMax("100");
        step1.setTypeOfSubstituteValue("STATIC");
        step1.setReferenceperiod("DAY");
        JsonLimitsConfig step2 = new JsonLimitsConfig();
        step2.setName("Stufe 2");
        step2.setMin("0.0");
        step2.setMax("500.0");
        step2.setTypeOfSubstituteValue("default value");
        step2.setDefaultMinValue("1.0");
        step2.setDefaultMaxValue("400.0");
        return Arrays.asList(step1, step2).toString();
    }

    /**
     * a silent time the web cannot show as weekdays + range (some months, last day of the month)
     */
    static JsonScheduler complexSchedule() {
        JsonScheduler scheduler = new JsonScheduler();
        scheduler.setTimezone("Europe/Berlin");
        scheduler.getRules().add(rule("0", "1, 2, 3", "LAST", "1, 2", 8, 0, 17, 30));
        return scheduler;
    }

    /**
     * Monday to Friday 22:00 to 06:00 as two rules (a rule does not cross midnight)
     */
    static JsonScheduler nightSchedule() {
        JsonScheduler scheduler = new JsonScheduler();
        scheduler.setTimezone("Europe/Berlin");
        scheduler.getRules().add(rule("0", AlarmFormats.ALL_MONTHS, "*", "1, 2, 3, 4, 5", 22, 0, 23, 59));
        scheduler.getRules().add(rule("1", AlarmFormats.ALL_MONTHS, "*", "2, 3, 4, 5, 6", 0, 0, 6, 0));
        return scheduler;
    }

    /**
     * "Alarm Config" as the Control Center's {@code AlarmEditor} writes it
     */
    static String ccAlarms() {
        JsonAlarmConfig rule0 = new JsonAlarmConfig();
        rule0.setName("Statischer Alarm");
        rule0.setId("0");
        rule0.setLimit("50");
        rule0.setOperator("BIGGER");
        rule0.setTolerance("5");
        rule0.setSilentTime(complexSchedule());
        rule0.setStandbyTime(nightSchedule());
        JsonAlarmConfig rule1 = new JsonAlarmConfig();
        rule1.setName("Dynamischer Alarm");
        rule1.setId("1");
        rule1.setLimitData("43:Value");
        rule1.setOperator("SMALLER");
        return Arrays.asList(rule0, rule1).toString();
    }

    /**
     * the Control Center's default ({@code AlarmEditor.createDefaultConfig}): two rules without limit
     */
    static String ccDefaultAlarms() {
        JsonAlarmConfig rule0 = new JsonAlarmConfig();
        rule0.setName("Statischer Alarm");
        rule0.setId("0");
        rule0.setOperator("SMALLER");
        JsonAlarmConfig rule1 = new JsonAlarmConfig();
        rule1.setName("Dynamischer Alarm");
        rule1.setId("1");
        rule1.setOperator("SMALLER");
        return Arrays.asList(rule0, rule1).toString();
    }

    // ---------------------------------------------------------------- setup

    /* Organisation 1 > building 2 "Werk Nord" > alarm directory 3 (configurations 50 with notification 51, 52 with
       notifications 53 and 54, 55 COMPLETE), alarm directory 4 (no create right); 2 > data directory 9 > data 40 >
       clean 41, data 42 > clean 43, data 44 > hidden clean 45; users 70 (readable), 72 (hidden), group 71;
       alarm directory 5 directly below the root (no site) */
    static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore();
        store.type(ResourceWebNavigation.CLASS_ORGANIZATION, null);
        store.type(ResourceWebNavigation.CLASS_BUILDING, null);
        store.type(DIRECTORY, null);
        store.type("Data Directory", null);
        store.type("Data", null, "Value", JEVisConstants.PrimitiveType.DOUBLE);
        store.type(CLEAN, null, "Value", JEVisConstants.PrimitiveType.DOUBLE, "Limits Enabled", B,
                "Limits Configuration", S, "Alarm Enabled", B, "Alarm Config", S);
        store.type(CONFIG, null, "Enabled", B, "Disable Link", B, "Include Limits In Notification", B,
                "Alarm Scope", S, "Alarm Objects", S, "Time Stamp", S, "Alarm Period", S, "Bind to Data", B);
        store.type("Notification", null);
        store.type(MAIL, "Notification", "Enabled", B, "Recipients", S, "Recipient Users", S, "Recipient Groups", S,
                "Carbon Copys", S, "Blind Carbon Copys", S, "Subject", S, "Message", S, "HTML E-Mail", B);
        store.type("User", null);
        store.type("Group", null);

        store.add(obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                obj(2, "Werk Nord", ResourceWebNavigation.CLASS_BUILDING, 1),
                obj(3, "Alarme", DIRECTORY, 2),
                obj(4, "Alarme Archiv", DIRECTORY, 2),
                obj(5, "Lose Alarme", DIRECTORY, 0),
                obj(9, "Daten", "Data Directory", 2),
                obj(40, "Zähler Bezug", "Data", 9),
                obj(41, "Bezug bereinigt", CLEAN, 40),
                obj(42, "Zähler Einspeisung", "Data", 9),
                obj(43, "Einspeisung bereinigt", CLEAN, 42),
                obj(44, "Zähler Halle", "Data", 9),
                obj(45, "Halle bereinigt", CLEAN, 44),
                obj(50, "Strom", CONFIG, 3),
                obj(51, "Strom Benachrichtigung", MAIL, 50),
                obj(52, "Doppelt", CONFIG, 3),
                obj(53, "Mail 1", MAIL, 52),
                obj(54, "Mail 2", MAIL, 52),
                obj(55, "Alles", CONFIG, 3),
                obj(70, "demo.reader", "User", 1),
                obj(71, "Technik", "Group", 1),
                obj(72, "demo.hidden", "User", 1));
        store.hidden.addAll(Arrays.asList(45L, 72L));
        store.write.addAll(Arrays.asList(41L, 43L, 50L, 51L, 52L, 55L));
        store.delete.add(50L);
        store.create.add(3L);

        store.sample(50, "Enabled", T1, "true", null)
                .sample(50, "Alarm Scope", T1, "SELECTED", null)
                .sample(50, "Alarm Objects", T1, "41:Value;43:Value", null)
                .sample(50, "Alarm Period", T1, "DAILY", null)
                .sample(50, "Bind to Data", T1, "false", null)
                .sample(50, "Include Limits In Notification", T1, "true", null)
                .sample(50, "Disable Link", T1, "false", null)
                .sample(50, "Time Stamp", T1, "2026-11-10T00:00:00.000+01:00", null)
                .sample(51, "Enabled", T1, "true", null)
                .sample(51, "Recipients", T1, "a@example.org;b@example.org", null)
                .sample(51, "Recipient Users", T1, "70:Value;72:Value", null)
                .sample(51, "Recipient Groups", T1, "71:Value", null)
                .sample(51, "Carbon Copys", T1, "", null)
                .sample(51, "Subject", T1, "Alarm Strom", null)
                .sample(51, "Message", T1, "Bitte prüfen", null)
                .sample(51, "HTML E-Mail", T1, "false", null)
                .sample(55, "Enabled", T1, "false", null)
                .sample(55, "Alarm Scope", T1, "COMPLETE", null)
                .sample(41, "Limits Enabled", T1, "true", null)
                .sample(41, "Limits Configuration", T1, ccLimits(), null)
                .sample(41, "Alarm Enabled", T1, "true", null)
                .sample(41, "Alarm Config", T1, ccAlarms(), null);
        return store;
    }

    private static JsonNode body(WebEdit.Reply reply) throws Exception {
        return FakeEditStore.json(reply);
    }

    private static JsonNode configuration(JsonNode settings, long id) {
        for (JsonNode c : settings.get("configurations")) {
            if (c.get("id").asLong() == id) return c;
        }
        return null;
    }

    private static List<String> messages(WebEdit.Reply reply) throws Exception {
        List<String> list = new ArrayList<>();
        for (JsonNode m : body(reply).get("messages")) list.add(m.asText());
        return list;
    }

    private static boolean contains(List<String> messages, String part) {
        for (String m : messages) if (m.contains(part)) return true;
        return false;
    }

    // ---------------------------------------------------------------- formats

    @Test
    public void listTextWritesTheControlCenterFormat() throws Exception {
        assertEquals(ccLimits(), AlarmFormats.listText(AlarmFormats.parseList(ccLimits())));
        assertEquals(ccAlarms(), AlarmFormats.listText(AlarmFormats.parseList(ccAlarms())));
        assertEquals(ccDefaultAlarms(), AlarmFormats.listText(AlarmFormats.parseList(ccDefaultAlarms())));
        assertEquals("[]", AlarmFormats.listText(new ArrayList<>()));
        assertNull(AlarmFormats.parseList("no json"));
        assertEquals(1, AlarmFormats.parseList("{\"name\":\"x\"}").size());
    }

    @Test
    public void simpleScheduleBothWaysAcrossMidnight() throws Exception {
        JsonNode night = AlarmFormats.MAPPER.valueToTree(nightSchedule());
        AlarmFormats.Simple simple = AlarmFormats.simple(night);
        assertNotNull(simple);
        assertEquals(new TreeSet<>(Arrays.asList(1, 2, 3, 4, 5)), simple.days);
        assertEquals("22:00", AlarmFormats.time(simple.from));
        assertEquals("06:00", AlarmFormats.time(simple.to));
        /* written back exactly as the Control Center's ScheduleEditor would */
        assertEquals(night, AlarmFormats.schedulerNode(simple, "Europe/Berlin"));

        Map<String, Object> json = AlarmFormats.scheduleJson(night);
        assertEquals(true, json.get("simple"));
        assertEquals(Arrays.asList("MON", "TUE", "WED", "THU", "FRI"), json.get("days"));

        /* Saturday and Sunday 23:00 to 01:30: Sunday is followed by Monday */
        ObjectNode sent = AlarmFormats.MAPPER.createObjectNode();
        sent.put("simple", true);
        sent.putArray("days").add("SAT").add("SUN");
        sent.put("from", "23:00");
        sent.put("to", "01:30");
        List<String> messages = new ArrayList<>();
        JsonNode written = AlarmFormats.schedule(sent, null, "Europe/Berlin", "silent", messages);
        assertTrue(messages.isEmpty());
        assertEquals("6, 7", written.get("rules").get(0).get("dayOfWeek").asText());
        assertEquals("23", written.get("rules").get(0).get("startTimeHours").asText());
        assertEquals("1, 7", written.get("rules").get(1).get("dayOfWeek").asText());
        assertEquals("1", written.get("rules").get(1).get("endTimeHours").asText());
        assertEquals("30", written.get("rules").get(1).get("endTimeMinutes").asText());

        /* JEAlarm reads it: Sunday 00:30 and Monday 01:00 are inside, Monday 02:00 and Saturday 12:00 not */
        CronScheduler cron = SchedulerHandler.BuildScheduler(written.toString());
        SchedulerContainsHandler handler = new SchedulerContainsHandler();
        assertTrue(handler.contains(new DateTime(2026, 11, 15, 0, 30, ZONE), cron));
        assertTrue(handler.contains(new DateTime(2026, 11, 16, 1, 0, ZONE), cron));
        assertTrue(handler.contains(new DateTime(2026, 11, 14, 23, 30, ZONE), cron));
        assertFalse(handler.contains(new DateTime(2026, 11, 16, 2, 0, ZONE), cron));
        assertFalse(handler.contains(new DateTime(2026, 11, 14, 12, 0, ZONE), cron));

        /* a simple range within the day is one rule */
        AlarmFormats.Simple day = new AlarmFormats.Simple(Arrays.asList(1, 3), 8 * 60, 17 * 60 + 30, null);
        ObjectNode one = AlarmFormats.schedulerNode(day, "UTC");
        assertEquals(1, one.get("rules").size());
        assertTrue(day.sameAs(AlarmFormats.simple(one)));
    }

    @Test
    public void nonSimpleScheduleIsKept() throws Exception {
        JsonNode complex = AlarmFormats.MAPPER.valueToTree(complexSchedule());
        assertNull(AlarmFormats.simple(complex));
        Map<String, Object> json = AlarmFormats.scheduleJson(complex);
        assertEquals(false, json.get("simple"));
        ObjectNode sent = AlarmFormats.MAPPER.valueToTree(json);
        List<String> messages = new ArrayList<>();
        assertSame(complex, AlarmFormats.schedule(sent, complex, "UTC", "silent", messages));
        assertTrue(messages.isEmpty());
        sent.put("text", "{\"timezone\":\"UTC\",\"rules\":[]}");
        AlarmFormats.schedule(sent, complex, "UTC", "silent", messages);
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains("Control Center"));
    }

    // ---------------------------------------------------------------- E7.1 list

    @Test
    public void settingsListConfigurationsTargetsAndWarnings() throws Exception {
        FakeEditStore store = store();
        JsonNode settings = body(ResourceWebEditAlarms.settings(store, "de"));
        assertEquals(3, settings.get("configurations").size());

        JsonNode strom = configuration(settings, 50);
        assertEquals("Strom", strom.get("name").asText());
        assertEquals("Alarme", strom.get("path").asText());
        assertEquals(2, strom.get("site").get("id").asLong());
        assertTrue(strom.get("enabled").asBoolean());
        assertEquals("SELECTED", strom.get("scope").asText());
        assertEquals("DAILY", strom.get("period").asText());
        assertTrue(strom.get("includeLimits").asBoolean());
        assertFalse(strom.get("bindToData").asBoolean());
        assertEquals("2026-11-10T00:00:00.000+01:00", strom.get("timeStamp").asText());
        assertEquals(WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault()), strom.get("version").asText());
        assertTrue(strom.get("canWrite").asBoolean());
        assertTrue(strom.get("canDelete").asBoolean());
        assertFalse(strom.get("multipleNotifications").asBoolean());

        JsonNode objects = strom.get("objects");
        assertEquals(2, objects.size());
        assertEquals(41, objects.get(0).get("id").asLong());
        assertEquals("Value", objects.get(0).get("attribute").asText());
        assertEquals("Bezug bereinigt", objects.get(0).get("name").asText());
        assertEquals("Daten / Zähler Bezug", objects.get(0).get("path").asText());
        assertTrue(objects.get(0).get("readable").asBoolean());

        JsonNode mail = strom.get("notification");
        assertEquals(51, mail.get("id").asLong());
        assertEquals(Arrays.asList("a@example.org", "b@example.org"),
                Arrays.asList(mail.get("recipients").get(0).asText(), mail.get("recipients").get(1).asText()));
        assertEquals(0, mail.get("cc").size());
        assertEquals(2, mail.get("recipientUsers").size());
        assertEquals("USER", mail.get("recipientUsers").get(0).get("kind").asText());
        assertEquals("demo.reader", mail.get("recipientUsers").get(0).get("name").asText());
        /* a user the reader may not see: id only */
        assertTrue(mail.get("recipientUsers").get(1).get("name").isNull());
        assertEquals("GROUP", mail.get("recipientGroups").get(0).get("kind").asText());
        assertEquals("Alarm Strom", mail.get("subject").asText());
        assertFalse(mail.get("html").asBoolean());

        JsonNode doppelt = configuration(settings, 52);
        assertTrue(doppelt.get("multipleNotifications").asBoolean());
        assertTrue(doppelt.get("notification").isNull());
        assertEquals("NONE", doppelt.get("scope").asText());
        assertEquals("NONE", doppelt.get("period").asText());
        assertEquals("none", doppelt.get("version").asText());

        JsonNode targets = settings.get("targets");
        assertEquals(1, targets.size());
        assertEquals(3, targets.get(0).get("id").asLong());
        assertEquals("Werk Nord", targets.get(0).get("site").get("name").asText());

        /* readable users and groups for the recipients */
        assertEquals(2, settings.get("people").size());
    }

    // ---------------------------------------------------------------- E7.1 round trip and PUT

    private static ObjectNode putBody(JsonNode configuration) {
        ObjectNode body = AlarmFormats.MAPPER.createObjectNode();
        body.put("version", configuration.get("version").asText());
        for (String field : Arrays.asList("name", "enabled", "scope", "period", "bindToData", "includeLimits", "disableLink")) {
            body.set(field, configuration.get(field));
        }
        ArrayNode objects = body.putArray("objects");
        for (JsonNode o : configuration.get("objects")) {
            objects.addObject().put("id", o.get("id").asLong()).put("attribute", o.get("attribute").asText());
        }
        JsonNode mail = configuration.get("notification");
        if (mail != null && !mail.isNull()) {
            ObjectNode n = body.putObject("notification");
            for (String field : Arrays.asList("enabled", "recipients", "cc", "bcc", "subject", "message", "html")) {
                n.set(field, mail.get(field));
            }
            for (String field : Arrays.asList("recipientUsers", "recipientGroups")) {
                ArrayNode ids = n.putArray(field);
                for (JsonNode p : mail.get(field)) ids.add(p.get("id").asLong());
            }
        }
        return body;
    }

    @Test
    public void configurationReadAndWrittenBackUnchanged() throws Exception {
        FakeEditStore store = store();
        JsonNode strom = configuration(body(ResourceWebEditAlarms.settings(store, "de")), 50);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, putBody(strom).toString(), "de", NOW));
        assertEquals(200, reply.status);
        assertTrue(store.writes.toString(), store.writes.isEmpty());
        assertTrue(store.logs.isEmpty());
        assertEquals("41:Value;43:Value", store.latest(50, "Alarm Objects").getValue());
        assertEquals("70:Value;72:Value", store.latest(51, "Recipient Users").getValue());
        assertEquals("a@example.org;b@example.org", store.latest(51, "Recipients").getValue());
        assertEquals(strom, configuration(body(ResourceWebEditAlarms.settings(store, "de")), 50));
    }

    @Test
    public void configurationChangesWrittenAsTheControlCenter() throws Exception {
        FakeEditStore store = store();
        JsonNode strom = configuration(body(ResourceWebEditAlarms.settings(store, "de")), 50);
        ObjectNode put = putBody(strom);
        put.put("name", "Strom Bezug");
        put.put("period", "HOURLY");
        put.put("bindToData", true);
        ((ArrayNode) put.get("objects")).remove(1);
        ObjectNode mail = (ObjectNode) put.get("notification");
        mail.putArray("recipients").add("a@example.org").add("neu@example.org");
        mail.putArray("cc").add("chef@example.org");
        mail.putArray("recipientUsers").add(70);
        mail.put("subject", "Neu");
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, put.toString(), "de", NOW));
        assertEquals(200, reply.status);
        assertEquals("HOURLY", store.latest(50, "Alarm Period").getValue());
        assertEquals("true", store.latest(50, "Bind to Data").getValue());
        assertEquals("41:Value", store.latest(50, "Alarm Objects").getValue());
        assertEquals("a@example.org;neu@example.org", store.latest(51, "Recipients").getValue());
        assertEquals("chef@example.org", store.latest(51, "Carbon Copys").getValue());
        assertEquals("70:Value", store.latest(51, "Recipient Users").getValue());
        assertEquals("71:Value", store.latest(51, "Recipient Groups").getValue());
        assertEquals("Neu", store.latest(51, "Subject").getValue());
        assertEquals(7, store.writes.size());
        assertTrue(store.renames.contains("50 Strom Bezug"));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + WebEdit.sampleLog(50, "Alarm Period", 1, "alarm-config-save")));
        JsonNode answer = body(reply);
        assertEquals(WebEdit.iso(NOW.getMillis(), DateTimeZone.getDefault()), answer.get("version").asText());

        /* the old version is outdated now: 409 with the current configuration */
        WebEdit.Reply conflict = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, put.toString(), "de", NOW.plusMinutes(1)));
        assertEquals(409, conflict.status);
        assertEquals("HOURLY", body(conflict).get("period").asText());
    }

    @Test
    public void configurationValidation() throws Exception {
        FakeEditStore store = store();
        String version = WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault());
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50,
                "{\"version\":\"" + version + "\",\"objects\":[],\"period\":\"CUSTOM\",\"scope\":\"ALL\","
                        + "\"notification\":{\"recipients\":[\"kein-mail\"],\"subject\":\"" + repeat('x', 201) + "\","
                        + "\"recipientUsers\":[41]}}", "de", NOW));
        assertEquals(400, reply.status);
        List<String> messages = messages(reply);
        assertTrue(messages.toString(), contains(messages, "scope: one of"));
        assertTrue(messages.toString(), contains(messages, "needs at least one object"));
        assertTrue(messages.toString(), contains(messages, "CUSTOM"));
        assertTrue(messages.toString(), contains(messages, "invalid address kein-mail"));
        assertTrue(messages.toString(), contains(messages, "subject longer"));
        assertTrue(messages.toString(), contains(messages, "recipientUsers: 41"));

        /* objects: readable data rows only; the hidden row 45 is refused */
        reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50,
                "{\"version\":\"" + version + "\",\"objects\":[{\"id\":45},{\"id\":3}]}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(contains(messages(reply), "objects: 45 is no readable data row"));
        assertTrue(contains(messages(reply), "objects: 3 is no readable data row"));

        reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, "{\"enabled\":false}", "de", NOW));
        assertEquals(400, reply.status);
        assertEquals("version missing", reply.body);

        /* several notifications: the web edits none */
        reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 52,
                "{\"version\":\"none\",\"notification\":{\"subject\":\"x\"}}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(contains(messages(reply), "several notifications"));
        assertTrue(store.writes.isEmpty());
    }

    private static String repeat(char c, int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    @Test
    public void configurationRightsAndMissing() throws Exception {
        FakeEditStore store = store();
        store.write.remove(50L);
        String version = WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault());
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50,
                "{\"version\":\"" + version + "\",\"enabled\":false}", "de", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 41,
                "{\"version\":\"" + version + "\"}", "de", NOW)).status);
        store.hidden.add(55L);
        store.add();
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 55,
                "{\"version\":\"none\"}", "de", NOW)).status);
        /* the notification needs its own write right */
        store.write.add(50L);
        store.write.remove(51L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50,
                "{\"version\":\"" + version + "\",\"notification\":{\"subject\":\"x\"}}", "de", NOW)).status);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void notificationCreatedWhenMissing() throws Exception {
        FakeEditStore store = store();
        store.create.add(55L);
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 55,
                "{\"version\":\"" + WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault()) + "\",\"notification\":"
                        + "{\"recipients\":[\"x@example.org\"],\"subject\":\"Alles\",\"message\":\"\"}}", "de", NOW));
        assertEquals(200, reply.status);
        JsonObjectAssert.created(store, 9000, "Alles Benachrichtigung", MAIL, 55);
        assertEquals("true", store.latest(9000, "Enabled").getValue());
        assertEquals("false", store.latest(9000, "HTML E-Mail").getValue());
        assertEquals("x@example.org", store.latest(9000, "Recipients").getValue());
        assertEquals(9000, body(reply).get("notification").get("id").asLong());
    }

    // ---------------------------------------------------------------- E7.1 create and delete

    @Test
    public void createRoundsTheTimeStampAndCreatesTheNotification() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.create(s,
                "{\"parentId\":3,\"name\":\"Gas\",\"scope\":\"SELECTED\",\"objects\":[{\"id\":43,\"attribute\":\"Value\"}],"
                        + "\"period\":\"QUARTER_HOURLY\",\"notification\":{\"recipientGroups\":[71],\"subject\":\"Gas\"}}",
                "de", NOW));
        assertEquals(201, reply.status);
        JsonObjectAssert.created(store, 9000, "Gas", CONFIG, 3);
        JsonObjectAssert.created(store, 9001, "Gas Benachrichtigung", MAIL, 9000);
        assertEquals("2026-11-16T10:00:00.000+01:00", store.latest(9000, "Time Stamp").getValue());
        assertEquals("true", store.latest(9000, "Enabled").getValue());
        assertEquals("SELECTED", store.latest(9000, "Alarm Scope").getValue());
        assertEquals("43:Value", store.latest(9000, "Alarm Objects").getValue());
        assertEquals("false", store.latest(9000, "Bind to Data").getValue());
        assertEquals("71:Value", store.latest(9001, "Recipient Groups").getValue());
        assertTrue(store.logs.contains("CREATE_OBJECT " + WebEdit.objectLog(9000, "Gas", "alarm-config-create")));
        JsonNode answer = body(reply);
        assertEquals("Gas", answer.get("name").asText());
        assertEquals(9001, answer.get("notification").get("id").asLong());

        /* DAILY: the start of the day in the site's time zone */
        reply = WebEdit.call(store, s -> ResourceWebEditAlarms.create(s, "{\"parentId\":3,\"name\":\"Wasser\"}", "de", NOW));
        assertEquals(201, reply.status);
        assertEquals("2026-11-16T00:00:00.000+01:00", store.latest(9002, "Time Stamp").getValue());
        assertEquals("COMPLETE", store.latest(9002, "Alarm Scope").getValue());
        assertEquals("DAILY", store.latest(9002, "Alarm Period").getValue());
    }

    @Test
    public void periodStartRounding() {
        assertEquals(new DateTime(2026, 11, 16, 10, 7, ZONE), ResourceWebEditAlarms.periodStart(NOW, "MINUTELY", ZONE));
        assertEquals(new DateTime(2026, 11, 16, 10, 0, ZONE), ResourceWebEditAlarms.periodStart(NOW, "HOURLY", ZONE));
        assertEquals(new DateTime(2026, 11, 16, 0, 0, ZONE), ResourceWebEditAlarms.periodStart(NOW, "WEEKLY", ZONE));
        assertEquals(new DateTime(2026, 11, 1, 0, 0, ZONE), ResourceWebEditAlarms.periodStart(NOW, "MONTHLY", ZONE));
        assertEquals(new DateTime(2026, 10, 1, 0, 0, ZONE), ResourceWebEditAlarms.periodStart(NOW, "QUARTERLY", ZONE));
        assertEquals(new DateTime(2026, 1, 1, 0, 0, ZONE), ResourceWebEditAlarms.periodStart(NOW, "YEARLY", ZONE));
    }

    @Test
    public void createValidationRightsAndRollback() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.create(s,
                "{\"parentId\":5,\"name\":\"Strom\",\"period\":\"CUSTOM\"}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(contains(messages(reply), "below a building"));
        assertTrue(contains(messages(reply), "CUSTOM"));
        reply = WebEdit.call(store, s -> ResourceWebEditAlarms.create(s, "{\"parentId\":3,\"name\":\"strom\"}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(contains(messages(reply), "already exists"));
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAlarms.create(s, "{\"parentId\":4,\"name\":\"X\"}", "de", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAlarms.create(s, "{\"parentId\":999,\"name\":\"X\"}", "de", NOW)).status);
        assertTrue(store.writes.isEmpty());

        /* the notification cannot be written: configuration and notification are removed again */
        store.failWritesOf.add("9001/Subject");
        try {
            WebEdit.call(store, s -> ResourceWebEditAlarms.create(s,
                    "{\"parentId\":3,\"name\":\"Gas\",\"notification\":{\"subject\":\"Gas\"}}", "de", NOW));
            fail("write error expected");
        } catch (IllegalStateException e) {
            assertEquals("write failed", e.getMessage());
        }
        assertTrue(store.deletedObjects.containsAll(Arrays.asList(9000L, 9001L)));
        assertNull(store.object(9000));
    }

    @Test
    public void deleteToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        String version = WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault());
        assertEquals(400, WebEdit.call(store, s -> ResourceWebEditAlarms.delete(s, 50, null, "de")).status);
        assertEquals(409, WebEdit.call(store, s -> ResourceWebEditAlarms.delete(s, 50, "2026-01-01T00:00:00.000+01:00", "de")).status);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditAlarms.delete(s, 52, "none", "de")).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditAlarms.delete(s, 3, version, "de")).status);
        assertEquals(204, WebEdit.call(store, s -> ResourceWebEditAlarms.delete(s, 50, version, "de")).status);
        assertEquals(Collections.singletonList(50L), store.recycled);
        assertTrue(store.logs.contains("MARK_AS_DELETE_OBJECT " + WebEdit.objectLog(50, "Strom", "alarm-config-delete")));
    }

    // ---------------------------------------------------------------- E7.2 GET

    @Test
    public void rowSettingsReadAndRawRowResolved() throws Exception {
        FakeEditStore store = store();
        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 40, "de"));
        assertEquals(41, settings.get("row").get("id").asLong());
        assertEquals(40, settings.get("row").get("rawId").asLong());
        assertEquals("Zähler Bezug", settings.get("row").get("rawName").asText());
        assertEquals("kWh", settings.get("row").get("unit").asText());
        assertTrue(settings.get("canWrite").asBoolean());
        assertEquals(WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault()), settings.get("version").asText());

        JsonNode steps = settings.get("limits").get("steps");
        assertTrue(settings.get("limits").get("enabled").asBoolean());
        assertEquals(2, steps.size());
        assertEquals(10.0, steps.get(0).get("min").asDouble(), 0);
        assertEquals(100.0, steps.get(0).get("max").asDouble(), 0);
        assertEquals("STATIC", steps.get(0).get("substitute").asText());
        assertEquals("DAY", steps.get(0).get("referenceperiod").asText());
        assertEquals("DEFAULT_VALUE", steps.get(1).get("substitute").asText());
        assertEquals(400.0, steps.get(1).get("defaultMax").asDouble(), 0);

        JsonNode rules = settings.get("alarms").get("rules");
        assertEquals(2, rules.size());
        assertEquals("STATIC", rules.get(0).get("kind").asText());
        assertEquals(50.0, rules.get(0).get("limit").asDouble(), 0);
        assertEquals("BIGGER", rules.get(0).get("operator").asText());
        assertEquals(5.0, rules.get(0).get("tolerance").asDouble(), 0);
        assertFalse(rules.get(0).get("silent").get("simple").asBoolean());
        assertTrue(rules.get(0).get("standby").get("simple").asBoolean());
        assertEquals("22:00", rules.get(0).get("standby").get("from").asText());
        assertEquals("06:00", rules.get(0).get("standby").get("to").asText());
        assertEquals("DYNAMIC", rules.get(1).get("kind").asText());
        assertEquals(43, rules.get(1).get("limitData").get("id").asLong());
        assertEquals("Einspeisung bereinigt", rules.get(1).get("limitData").get("name").asText());
        assertTrue(rules.get(1).get("silent").isNull());

        /* configuration 50 (enabled) selects the row: 55 (COMPLETE) skips rows with an own configuration */
        JsonNode configurations = settings.get("configurations");
        assertEquals(1, configurations.size());
        assertEquals(50, configurations.get(0).get("id").asLong());
        /* without an enabled own configuration the COMPLETE one covers the building's data directory */
        store.sample(50, "Enabled", T1.plusSeconds(1), "false", null);
        configurations = body(ResourceWebEditRowAlarms.settings(store, 40, "de")).get("configurations");
        assertEquals(2, configurations.size());
        assertEquals(55, configurations.get(0).get("id").asLong());
        assertFalse(configurations.get(0).get("enabled").asBoolean());
        assertEquals(50, configurations.get(1).get("id").asLong());

        assertEquals(404, ResourceWebEditRowAlarms.settings(store, 45, "de").status);
        assertEquals(404, ResourceWebEditRowAlarms.settings(store, 3, "de").status);
    }

    @Test
    public void scopeOfRows() throws Exception {
        FakeEditStore store = store();
        ResourceWebModules.Lookup lookup = store.lookup();
        assertTrue(ResourceWebEditAlarms.inCompleteScope(lookup, 55, store.object(43)));
        /* not a clean data row, or a configuration whose directory is not below the building */
        assertFalse(ResourceWebEditAlarms.inCompleteScope(lookup, 55, store.object(40)));
        store.add(obj(60, "Unterordner", DIRECTORY, 3), obj(61, "Tief", CONFIG, 60));
        assertFalse(ResourceWebEditAlarms.inCompleteScope(store.lookup(), 61, store.object(43)));
        /* WITHOUT_SELECTED: all but the selected rows */
        store.sample(55, "Alarm Scope", T1.plusSeconds(1), "WITHOUT_SELECTED", null)
                .sample(55, "Alarm Objects", T1, "41:Value", null);
        JsonNode settings43 = body(ResourceWebEditRowAlarms.settings(store, 43, "de"));
        JsonNode settings41 = body(ResourceWebEditRowAlarms.settings(store, 41, "de"));
        /* 50 selects 41 and 43 itself: 55 does not report them (no overlap) */
        assertEquals(1, settings43.get("configurations").size());
        assertEquals(1, settings41.get("configurations").size());
        store.sample(50, "Alarm Objects", T1.plusSeconds(1), "41:Value", null);
        settings43 = body(ResourceWebEditRowAlarms.settings(store, 43, "de"));
        assertEquals(55, settings43.get("configurations").get(0).get("id").asLong());
        assertEquals(1, settings43.get("configurations").size());
    }

    @Test
    public void rulesCopiedToOtherRows() throws Exception {
        FakeEditStore store = store();
        /* 43 has no rules; 40 is the raw row of the source itself; 45 is hidden; 50 is not a row */
        WebEdit.Reply reply = ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[43,40,45,50,43]}", NOW);
        assertEquals(200, reply.status);
        JsonNode answer = body(reply);
        assertEquals(1, answer.get("changed").size());
        assertEquals(43, answer.get("changed").get(0).get("id").asLong());
        List<String> reasons = new ArrayList<>();
        for (JsonNode s : answer.get("skipped")) reasons.add(s.get("id").asLong() + ":" + s.get("reason").asText());
        assertEquals(Arrays.asList("40:source", "45:notFound", "50:notFound"), reasons);
        JsonNode copied = body(ResourceWebEditRowAlarms.settings(store, 43, "de")).get("alarms");
        assertTrue(copied.get("enabled").asBoolean());
        assertEquals(2, copied.get("rules").size());
        assertEquals("BIGGER", copied.get("rules").get(0).get("operator").asText());

        /* added again: the ids 0 and 1 are taken, the copies get 2 and 3 */
        assertEquals(200, ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[43]}", NOW.plusSeconds(1)).status);
        JsonNode twice = body(ResourceWebEditRowAlarms.settings(store, 43, "de")).get("alarms").get("rules");
        assertEquals(4, twice.size());
        List<String> ids = new ArrayList<>();
        for (JsonNode r : twice) ids.add(r.get("id").asText());
        assertEquals(Arrays.asList("0", "1", "2", "3"), ids);
        /* replace: exactly the source's rules again */
        assertEquals(200, ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[43],\"replace\":true}", NOW.plusSeconds(2)).status);
        assertEquals(2, body(ResourceWebEditRowAlarms.settings(store, 43, "de")).get("alarms").get("rules").size());

        /* no write right on the target; a row without rules cannot be copied; bad input */
        store.write.remove(43L);
        assertEquals("noWrite", body(ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[43]}", NOW.plusSeconds(3)))
                .get("skipped").get(0).get("reason").asText());
        assertEquals(400, ResourceWebEditRowAlarms.copy(store(), 43, "{\"targets\":[41]}", NOW).status);
        assertEquals(404, ResourceWebEditRowAlarms.copy(store, 45, "{\"targets\":[41]}", NOW).status);
        assertEquals(400, ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[]}", NOW).status);
        assertEquals(400, ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[\"x\"]}", NOW).status);
        assertEquals(400, ResourceWebEditRowAlarms.copy(store, 41, "{\"targets\":[43],\"replace\":\"ja\"}", NOW).status);
    }

    // ---------------------------------------------------------------- E7.2 PUT

    private static ObjectNode rowPutBody(JsonNode settings) {
        ObjectNode body = AlarmFormats.MAPPER.createObjectNode();
        body.put("version", settings.get("version").asText());
        ObjectNode limits = body.putObject("limits");
        limits.set("enabled", settings.get("limits").get("enabled"));
        limits.set("steps", settings.get("limits").get("steps").deepCopy());
        ObjectNode alarms = body.putObject("alarms");
        alarms.set("enabled", settings.get("alarms").get("enabled"));
        alarms.set("rules", settings.get("alarms").get("rules").deepCopy());
        return body;
    }

    @Test
    public void rowSettingsReadAndWrittenBackUnchanged() throws Exception {
        FakeEditStore store = store();
        /* an unknown key in a step is kept as well */
        List<ObjectNode> steps = AlarmFormats.parseList(ccLimits());
        steps.get(0).put("customKey", "x");
        String limits = AlarmFormats.listText(steps);
        store.sample(41, "Limits Configuration", T1, limits, null);
        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 41, "de"));
        assertEquals("x", settings.get("limits").get("steps").get(0).get("customKey").asText());

        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41,
                rowPutBody(settings).toString(), "de", NOW));
        assertEquals(200, reply.status);
        assertTrue(store.writes.toString(), store.writes.isEmpty());
        assertEquals(limits, store.latest(41, "Limits Configuration").getValue());
        assertEquals(ccAlarms(), store.latest(41, "Alarm Config").getValue());
        assertFalse(body(reply).get("reprocessHint").asBoolean());
    }

    /** a row without "Limits Enabled"/"Alarm Enabled" samples: sending them back "off" writes nothing (local test 2026-11-18) */
    @Test
    public void missingEnabledFlagsCountAsOff() throws Exception {
        FakeEditStore store = store();
        store.samples.keySet().removeIf(k -> k.startsWith("41/Limits Enabled") || k.startsWith("41/Alarm Enabled"));
        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 41, "de"));
        assertFalse(settings.get("limits").get("enabled").asBoolean());
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41,
                rowPutBody(settings).toString(), "de", NOW));
        assertEquals(200, reply.status);
        assertTrue(store.writes.toString(), store.writes.isEmpty());
        assertFalse(body(reply).get("reprocessHint").asBoolean());
    }

    @Test
    public void rowSettingsChangesKeepUnknownKeysAndStoredTexts() throws Exception {
        FakeEditStore store = store();
        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 41, "de"));
        ObjectNode put = rowPutBody(settings);
        ObjectNode step1 = (ObjectNode) put.get("limits").get("steps").get(0);
        step1.put("max", 120);
        ArrayNode rules = (ArrayNode) put.get("alarms").get("rules");
        ObjectNode rule0 = (ObjectNode) rules.get(0);
        rule0.put("limit", 60);
        ObjectNode standby = rule0.putObject("standby");
        standby.put("simple", true);
        standby.putArray("days").add("SAT");
        standby.put("from", "08:00");
        standby.put("to", "12:00");
        rules.remove(1);
        ObjectNode added = rules.addObject();
        added.put("kind", "STATIC");
        added.put("limit", 1.5);
        added.put("operator", "SMALLER_EQUALS");
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, put.toString(), "de", NOW));
        assertEquals(messagesOrStatus(reply), 200, reply.status);
        assertTrue(body(reply).get("reprocessHint").asBoolean());
        assertEquals(2, store.writes.size());

        List<ObjectNode> storedSteps = AlarmFormats.parseList(store.latest(41, "Limits Configuration").getValue());
        assertEquals("10.0", storedSteps.get(0).get("min").asText());
        assertEquals("120.0", storedSteps.get(0).get("max").asText());
        assertEquals("DAY", storedSteps.get(0).get("referenceperiod").asText());
        /* the second step as stored, including the old spelling of its substitute */
        assertEquals("default value", storedSteps.get(1).get("typeOfSubstituteValue").asText());
        assertEquals(AlarmFormats.listText(storedSteps), store.latest(41, "Limits Configuration").getValue());

        List<ObjectNode> storedRules = AlarmFormats.parseList(store.latest(41, "Alarm Config").getValue());
        assertEquals(2, storedRules.size());
        assertEquals("60", storedRules.get(0).get("limit").asText());
        assertEquals("5", storedRules.get(0).get("tolerance").asText());
        assertEquals(AlarmFormats.MAPPER.valueToTree(complexSchedule()), storedRules.get(0).get("silentTime"));
        assertEquals("6", storedRules.get(0).get("standbyTime").get("rules").get(0).get("dayOfWeek").asText());
        assertEquals("Europe/Berlin", storedRules.get(0).get("standbyTime").get("timezone").asText());
        /* new static rule: id 0 is taken, so the next number; default name; JsonAlarmConfig keys */
        assertEquals("2", storedRules.get(1).get("id").asText());
        assertEquals("Statischer Alarm", storedRules.get(1).get("name").asText());
        assertEquals("1.5", storedRules.get(1).get("limit").asText());
        assertTrue(storedRules.get(1).get("limitData").isNull());
        assertTrue(storedRules.get(1).has("standbyTime"));
        assertTrue(store.logs.contains("CREATE_SAMPLE " + WebEdit.sampleLog(41, "Alarm Config", 1, "alarm-row-save")));

        /* the stored config is valid for JEAlarm's scheduler */
        SchedulerHandler.BuildScheduler(storedRules.get(0).get("standbyTime").toString());

        /* old version: 409 with the current settings */
        WebEdit.Reply conflict = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, put.toString(), "de", NOW.plusSeconds(5)));
        assertEquals(409, conflict.status);
        assertEquals(2, body(conflict).get("alarms").get("rules").size());
    }

    private static String messagesOrStatus(WebEdit.Reply reply) {
        return reply.status + " " + reply.body;
    }

    @Test
    public void rowSettingsValidation() throws Exception {
        FakeEditStore store = store();
        String version = WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault());
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{\"version\":\"" + version
                + "\",\"limits\":{\"enabled\":true,\"steps\":[{},{},{}]}}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(contains(messages(reply), "1 to 2 steps"));

        reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{\"version\":\"" + version
                + "\",\"limits\":{\"steps\":[{\"min\":5,\"max\":5},{\"substitute\":\"DEFAULT_VALUE\",\"defaultMin\":null}]}}", "de", NOW));
        assertEquals(400, reply.status);
        List<String> messages = messages(reply);
        assertTrue(messages.toString(), contains(messages, "min must be below max"));
        assertTrue(messages.toString(), contains(messages, "defaultMin and defaultMax"));

        reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{\"version\":\"" + version
                + "\",\"alarms\":{\"rules\":[{\"kind\":\"STATIC\",\"operator\":\"BIGGER\"},"
                + "{\"kind\":\"DYNAMIC\",\"operator\":\"GT\",\"limitData\":{\"id\":45}},"
                + "{\"kind\":\"STATIC\",\"limit\":1,\"operator\":\"EQUALS\",\"tolerance\":-1},"
                + "{\"kind\":\"STATIC\",\"limit\":1,\"operator\":\"EQUALS\",\"silent\":{\"simple\":true,\"days\":[\"XYZ\"],\"from\":\"1:00\",\"to\":\"2:00\"}}]}}",
                "de", NOW));
        assertEquals(400, reply.status);
        messages = messages(reply);
        assertTrue(messages.toString(), contains(messages, "rules[0].limit"));
        assertTrue(messages.toString(), contains(messages, "rules[1].limitData"));
        assertTrue(messages.toString(), contains(messages, "rules[1].operator"));
        assertTrue(messages.toString(), contains(messages, "rules[2].tolerance"));
        assertTrue(messages.toString(), contains(messages, "rules[3].silent"));

        String eleven = "[" + String.join(",", Collections.nCopies(11, "{\"kind\":\"STATIC\",\"limit\":1,\"operator\":\"EQUALS\"}")) + "]";
        reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{\"version\":\"" + version
                + "\",\"alarms\":{\"rules\":" + eleven + "}}", "de", NOW));
        assertEquals(400, reply.status);
        assertTrue(store.writes.isEmpty());

        assertEquals("version missing", WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{}", "de", NOW)).body);
        store.write.remove(41L);
        assertEquals(403, WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, "{\"version\":\"" + version + "\"}", "de", NOW)).status);
        assertEquals(404, WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 45, "{\"version\":\"" + version + "\"}", "de", NOW)).status);
    }

    @Test
    public void defaultConfigurationsAreAcceptedAndWrittenOnEnable() throws Exception {
        FakeEditStore store = store();
        /* row 43 has nothing: switching limits on writes the Control Center's default, via the raw row 42 */
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 42,
                "{\"version\":\"none\",\"limits\":{\"enabled\":true,\"steps\":[]},\"alarms\":{\"enabled\":true}}", "de", NOW));
        assertEquals(messagesOrStatus(reply), 200, reply.status);
        assertEquals("true", store.latest(43, "Limits Enabled").getValue());
        assertEquals("true", store.latest(43, "Alarm Enabled").getValue());
        List<ObjectNode> steps = AlarmFormats.parseList(store.latest(43, "Limits Configuration").getValue());
        assertEquals(2, steps.size());
        assertEquals("Stufe 1", steps.get(0).get("name").asText());
        assertEquals("Stufe 2", steps.get(1).get("name").asText());
        assertTrue(steps.get(0).get("min").isNull());
        /* exactly what LimitEditor.createDefaultConfig().toString() gives */
        JsonLimitsConfig default1 = new JsonLimitsConfig();
        default1.setName("Stufe 1");
        JsonLimitsConfig default2 = new JsonLimitsConfig();
        default2.setName("Stufe 2");
        assertEquals(Arrays.asList(default1, default2).toString(), store.latest(43, "Limits Configuration").getValue());
        assertTrue(body(reply).get("reprocessHint").asBoolean());
        assertEquals(43, body(reply).get("row").get("id").asLong());

        /* the Control Center's default alarm rules (no limit) are kept when sent back unchanged */
        store.sample(43, "Alarm Config", NOW.plusSeconds(1), ccDefaultAlarms(), null);
        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 43, "de"));
        assertFalse(settings.get("alarms").get("rules").get(0).get("complete").asBoolean());
        store.writes.clear();
        reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 43, rowPutBody(settings).toString(), "de", NOW.plusSeconds(2)));
        assertEquals(messagesOrStatus(reply), 200, reply.status);
        assertTrue(store.writes.toString(), store.writes.isEmpty());
        assertEquals(ccDefaultAlarms(), store.latest(43, "Alarm Config").getValue());
    }

    /**
     * Small assertions on objects created in a test.
     */
    static final class JsonObjectAssert {
        static void created(FakeEditStore store, long id, String name, String jevisClass, long parent) {
            org.jevis.commons.ws.json.JsonObject object = store.object(id);
            assertNotNull("object " + id, object);
            assertEquals(name, object.getName());
            assertEquals(jevisClass, object.getJevisClass());
            assertEquals(parent, object.getParent());
        }
    }
}
