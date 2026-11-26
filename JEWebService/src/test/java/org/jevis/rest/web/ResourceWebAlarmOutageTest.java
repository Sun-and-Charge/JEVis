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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.alarm.OutageEvent;
import org.jevis.commons.alarm.OutageState;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Data outage rules, validity time, moving mean, outage list and daily summary in the web (docs/ALARMS-OUTAGE.md). */
public class ResourceWebAlarmOutageTest {

    private static final DateTimeZone ZONE = FakeEditStore.ZONE;
    private static final DateTime T1 = new DateTime(2026, 11, 10, 9, 0, 0, ZONE);
    private static final DateTime NOW = new DateTime(2026, 11, 26, 10, 0, 0, ZONE);

    private static JsonNode body(WebEdit.Reply reply) throws Exception {
        return FakeEditStore.json(reply);
    }

    private static String version() {
        return WebEdit.iso(T1.getMillis(), DateTimeZone.getDefault());
    }

    @Test
    public void outageRuleValidTimeAndAverageAreStoredWithoutNullKeys() throws Exception {
        FakeEditStore store = ResourceWebEditAlarmsTest.store();
        store.sample(40, "Value", NOW.minusHours(3), "5", null);
        String put = "{\"version\":\"" + version() + "\",\"alarms\":{\"enabled\":true,\"rules\":["
                + "{\"kind\":\"STATIC\",\"name\":\"Tag\",\"limit\":40,\"operator\":\"BIGGER\",\"average\":60,"
                + "\"valid\":{\"simple\":true,\"days\":[\"MON\",\"TUE\",\"WED\",\"THU\",\"FRI\"],\"from\":\"06:00\",\"to\":\"20:00\"}},"
                + "{\"kind\":\"STATIC\",\"name\":\"Nacht\",\"limit\":10,\"operator\":\"BIGGER\"},"
                + "{\"kind\":\"NO_DATA\",\"noData\":120}]}}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, put, "de", NOW));
        assertEquals(reply.status + " " + reply.body, 200, reply.status);

        List<com.fasterxml.jackson.databind.node.ObjectNode> stored = AlarmFormats.parseList(store.latest(41, "Alarm Config").getValue());
        assertEquals(3, stored.size());
        assertEquals("60", stored.get(0).get("average").asText());
        assertTrue(stored.get(0).has("validTime"));
        /* older Control Centers reject unknown keys: rules without the new features must not carry them */
        assertFalse(stored.get(1).has("noData"));
        assertFalse(stored.get(1).has("validTime"));
        assertFalse(stored.get(1).has("average"));
        assertEquals("120", stored.get(2).get("noData").asText());
        assertEquals("Datenausfall", stored.get(2).get("name").asText());
        /* ids: 0 and 2 are taken by the two fixed rules, the outage rule gets the next number */
        assertEquals("3", stored.get(2).get("id").asText());
        assertTrue(stored.get(2).get("limit") == null || stored.get(2).get("limit").isNull());

        JsonNode settings = body(ResourceWebEditRowAlarms.settings(store, 41, "de"));
        JsonNode rules = settings.get("alarms").get("rules");
        assertEquals("NO_DATA", rules.get(2).get("kind").asText());
        assertEquals(120, rules.get(2).get("noData").asInt());
        assertTrue(rules.get(2).get("complete").asBoolean());
        assertEquals(60, rules.get(0).get("average").asInt());
        assertTrue(rules.get(0).get("valid").get("simple").asBoolean());
        assertEquals("06:00", rules.get(0).get("valid").get("from").asText());
        assertNotNull(settings.get("row").get("lastValue").asText(null));

        /* invalid minutes */
        String bad = "{\"version\":\"" + settings.get("version").asText() + "\",\"alarms\":{\"rules\":[{\"kind\":\"NO_DATA\",\"noData\":0}]}}";
        WebEdit.Reply invalid = WebEdit.call(store, s -> ResourceWebEditRowAlarms.update(s, 41, bad, "de", NOW.plusMinutes(1)));
        assertEquals(400, invalid.status);
    }

    @Test
    public void outagesAreListedOpenFirst() throws Exception {
        FakeEditStore store = ResourceWebEditAlarmsTest.store();
        OutageState state = new OutageState();
        state.getOpen().put(OutageState.rowKey(41), new OutageState.Open("ROW", 41, NOW.minusHours(5).toString(), NOW.minusHours(3).toString(), 120, null));
        store.sample(50, OutageState.ATTRIBUTE, NOW.minusHours(3), state.toJson(), null);
        OutageState.Open ended = new OutageState.Open("ROW", 43, NOW.minusDays(2).toString(), NOW.minusDays(2).plusHours(2).toString(), 120, null);
        store.sample(50, OutageEvent.ATTRIBUTE, NOW.minusDays(1), "[" + OutageEvent.end(ended, NOW.minusDays(1).toString(), true).toJson() + "]", null);

        ResourceWebAlarms.Store alarmStore = new ResourceWebAlarms.Store() {
            @Override
            public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) {
                return store.samples(id, attribute, from, to, limit);
            }

            @Override
            public JsonSample latest(long id, String attribute) {
                return store.latest(id, attribute);
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) {
                return null;
            }

            @Override
            public String unit(long id, String attribute) {
                return null;
            }
        };
        List<Map<String, Object>> outages = ResourceWebAlarms.outagesJson(store.lookup(),
                store.classes().withHeirs(ResourceWebNavigation.CLASS_BUILDING), alarmStore, NOW.minusDays(7), NOW, ZONE);
        assertEquals(2, outages.size());
        assertEquals(Boolean.TRUE, outages.get(0).get("open"));
        assertEquals("ROW", outages.get(0).get("kind"));
        @SuppressWarnings("unchecked") Map<String, Object> object = (Map<String, Object>) outages.get(0).get("object");
        assertEquals(41L, object.get("id"));
        assertEquals(Boolean.FALSE, outages.get(1).get("open"));
        assertEquals(Boolean.TRUE, outages.get(1).get("backfilled"));
        assertNotNull(outages.get(1).get("ended"));
    }

    @Test
    public void dailySummaryIsReadAndWritten() throws Exception {
        FakeEditStore store = ResourceWebEditAlarmsTest.store();
        int S = JEVisConstants.PrimitiveType.STRING;
        int B = JEVisConstants.PrimitiveType.BOOLEAN;
        store.type(ResourceWebEditAlarms.CLASS_CONFIGURATION, null, "Enabled", B, "Disable Link", B, "Include Limits In Notification", B,
                "Alarm Scope", S, "Alarm Objects", S, "Time Stamp", S, "Alarm Period", S, "Bind to Data", B,
                "Daily Summary", B, "Daily Summary Time", S);
        JsonNode settings = body(ResourceWebEditAlarms.settings(store, "de"));
        JsonNode strom = null;
        for (JsonNode c : settings.get("configurations")) if (c.get("id").asLong() == 50) strom = c;
        assertNotNull(strom);
        assertFalse(strom.get("dailySummary").asBoolean());
        String put = "{\"version\":\"" + strom.get("version").asText() + "\",\"dailySummary\":true,\"dailySummaryTime\":\"7:30\"}";
        WebEdit.Reply reply = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, put, "de", NOW));
        assertEquals(reply.status + " " + reply.body, 200, reply.status);
        assertEquals("true", store.latest(50, "Daily Summary").getValue());
        assertEquals("07:30", store.latest(50, "Daily Summary Time").getValue());

        JsonNode after = body(reply);
        String bad = "{\"version\":\"" + after.get("version").asText() + "\",\"dailySummaryTime\":\"25:00\"}";
        WebEdit.Reply invalid = WebEdit.call(store, s -> ResourceWebEditAlarms.update(s, 50, bad, "de", NOW.plusMinutes(1)));
        assertEquals(400, invalid.status);
        assertEquals(Collections.singletonList("07:30"), Collections.singletonList(store.latest(50, "Daily Summary Time").getValue()));
    }
}
