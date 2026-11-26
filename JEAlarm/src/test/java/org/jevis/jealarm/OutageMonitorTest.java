/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm;

import org.jevis.api.*;
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.alarm.OutageEvent;
import org.jevis.commons.alarm.OutageState;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Data outage check and daily summary of a configuration with mocked objects; neutral names. */
public class OutageMonitorTest {

    /** the mocked configuration has no building, so its time zone is UTC */
    private static final DateTimeZone Z = DateTimeZone.UTC;
    /** "id/attribute" → samples by time */
    private final Map<String, TreeMap<Long, Object>> values = new HashMap<>();
    private final List<String[]> mails = new ArrayList<>();
    private JEVisDataSource ds;
    private AlarmConfiguration configuration;
    private JEVisObject cfg;

    private static DateTime t(int day, int hour, int minute) {
        return new DateTime(2026, 11, day, hour, minute, Z);
    }

    private JEVisClass jclass(String name, JEVisClass parent) throws Exception {
        JEVisClass c = mock(JEVisClass.class);
        when(c.getName()).thenReturn(name);
        when(c.getInheritance()).thenReturn(parent);
        return c;
    }

    private JEVisObject object(long id, String name, String className, JEVisClass jclass, JEVisObject parent, List<JEVisObject> children) throws Exception {
        JEVisObject o = mock(JEVisObject.class);
        when(o.getID()).thenReturn(id);
        when(o.getName()).thenReturn(name);
        when(o.getJEVisClassName()).thenReturn(className);
        when(o.getJEVisClass()).thenReturn(jclass);
        when(o.getParents()).thenReturn(parent != null ? Collections.singletonList(parent) : Collections.emptyList());
        when(o.getChildren()).thenReturn(children);
        when(o.getDataSource()).thenReturn(ds);
        when(o.getAttribute(any(String.class))).thenAnswer(inv -> attribute(id + "/" + inv.getArgument(0)));
        when(ds.getObject(id)).thenReturn(o);
        return o;
    }

    private JEVisSample sample(long ts, Object v) throws Exception {
        JEVisSample s = mock(JEVisSample.class);
        when(s.getTimestamp()).thenReturn(new DateTime(ts, Z));
        when(s.getValueAsString()).thenReturn(v == null ? null : String.valueOf(v));
        when(s.getValueAsBoolean()).thenAnswer(inv -> v instanceof Boolean ? v : Boolean.parseBoolean(String.valueOf(v)));
        when(s.getValueAsLong()).thenAnswer(inv -> v instanceof Number ? ((Number) v).longValue() : null);
        when(s.getValue()).thenReturn(v);
        return s;
    }

    private JEVisAttribute attribute(String key) throws Exception {
        JEVisAttribute a = mock(JEVisAttribute.class);
        when(a.getLatestSample()).thenAnswer(inv -> {
            TreeMap<Long, Object> m = values.get(key);
            return m == null || m.isEmpty() ? null : sample(m.lastKey(), m.lastEntry().getValue());
        });
        when(a.getTimestampOfLastSample()).thenAnswer(inv -> {
            TreeMap<Long, Object> m = values.get(key);
            return m == null || m.isEmpty() ? null : new DateTime(m.lastKey(), Z);
        });
        when(a.getSamples(any(DateTime.class), any(DateTime.class))).thenAnswer(inv -> {
            List<JEVisSample> list = new ArrayList<>();
            TreeMap<Long, Object> m = values.get(key);
            if (m == null) return list;
            for (Map.Entry<Long, Object> e : m.subMap(((DateTime) inv.getArgument(0)).getMillis(), true, ((DateTime) inv.getArgument(1)).getMillis(), true).entrySet()) {
                list.add(sample(e.getKey(), e.getValue()));
            }
            return list;
        });
        when(a.buildSample(any(DateTime.class), any())).thenAnswer(inv -> {
            JEVisSample s = mock(JEVisSample.class);
            DateTime ts = inv.getArgument(0);
            Object v = inv.getArgument(1);
            org.mockito.Mockito.doAnswer(c -> {
                values.computeIfAbsent(key, k -> new TreeMap<>()).put(ts.getMillis(), v);
                return null;
            }).when(s).commit();
            return s;
        });
        return a;
    }

    private void put(long id, String attribute, DateTime ts, Object value) {
        values.computeIfAbsent(id + "/" + attribute, k -> new TreeMap<>()).put(ts.getMillis(), value);
    }

    private JEVisObject raw;
    private JEVisObject row;
    private JEVisObject source;

    @BeforeEach
    public void setUp() throws Exception {
        ds = mock(JEVisDataSource.class);
        JEVisClass plain = jclass("Directory", null);
        cfg = object(1, "Werk Alarme", "Alarm Configuration", plain, null, Collections.emptyList());
        put(1, OutageState.ATTRIBUTE, t(1, 0, 0), "");
        put(1, OutageEvent.ATTRIBUTE, t(1, 0, 0), "[]");
        configuration = new AlarmConfiguration(ds, cfg);

        raw = object(10, "Strom - Halle 3", "Data", jclass("Data", null), null, new ArrayList<>());
        row = object(11, "Bereinigte Daten", "Clean Data", jclass("Clean Data", null), raw, Collections.emptyList());
        put(11, "Alarm Config", t(1, 0, 0), "[{\"name\":\"Ausfall\",\"noData\":\"120\"},{\"name\":\"Spitze\",\"limit\":\"40\",\"operator\":\"BIGGER\"}]");

        JEVisClass channelBase = jclass("Channel", null);
        JEVisClass sourceBase = jclass("Data Source", null);
        JEVisObject channel = object(31, "Kanal 1", "Muster Channel", jclass("Muster Channel", channelBase), null, Collections.emptyList());
        put(31, "Target ID", t(1, 0, 0), "10:Value");
        when(ds.getObject(10L)).thenReturn(raw);
        source = object(30, "Gateway Halle 3", "Muster Server", jclass("Muster Server", sourceBase), null, Collections.singletonList(channel));
        put(30, "Enabled", t(1, 0, 0), true);
    }

    private OutageMonitor monitor() {
        return new OutageMonitor(ds, configuration, (subject, html) -> {
            mails.add(new String[]{subject, html});
            return true;
        });
    }

    private List<OutageEvent> logged() {
        List<OutageEvent> all = new ArrayList<>();
        for (Object v : values.get("1/" + OutageEvent.ATTRIBUTE).values()) all.addAll(OutageMonitor.events(String.valueOf(v)));
        return all;
    }

    @Test
    public void rowOutageStartsOnceAndEndsWithLateDelivery() {
        put(10, "Value", t(26, 9, 0), 5.0);
        Set<Long> inOutage = monitor().run(Collections.singletonList(row), Collections.emptyList(), t(26, 12, 0));
        assertEquals(Collections.singleton(11L), inOutage);
        assertEquals(1, mails.size());
        assertTrue(mails.get(0)[0].startsWith("Datenausfall: Werk Alarme (1)"));
        assertTrue(mails.get(0)[1].contains("Strom - Halle 3 / Bereinigte Daten"));
        assertTrue(mails.get(0)[1].contains("2 Stunden"));

        monitor().run(Collections.singletonList(row), Collections.emptyList(), t(26, 12, 15));
        assertEquals(1, mails.size(), "no second mail for the same outage");

        /* the gateway delivers the missing quarter hours late */
        put(10, "Value", t(26, 9, 15), 5.0);
        put(10, "Value", t(26, 11, 45), 5.0);
        Set<Long> after = monitor().run(Collections.singletonList(row), Collections.emptyList(), t(26, 13, 0));
        assertTrue(after.isEmpty());
        List<OutageEvent> events = logged();
        assertEquals(2, events.size());
        assertEquals(OutageEvent.START, events.get(0).event);
        assertEquals(OutageEvent.END, events.get(1).event);
        assertTrue(events.get(1).backfilled);
        assertTrue(OutageState.fromJson(String.valueOf(values.get("1/" + OutageState.ATTRIBUTE).lastEntry().getValue())).getOpen().isEmpty());
    }

    @Test
    public void silentGatewayIsNamedAsCauseAndRemovedRuleEndsTheOutage() {
        put(10, "Value", t(25, 6, 0), 5.0);
        put(31, "Last Readout", t(1, 0, 0), t(25, 6, 0).toString());
        monitor().run(Collections.singletonList(row), Collections.singletonList(source), t(26, 12, 0));
        assertEquals(1, mails.size());
        assertTrue(mails.get(0)[0].endsWith("(2)"));
        assertTrue(mails.get(0)[1].contains("Übertragungsstörung"));
        assertTrue(mails.get(0)[1].contains("Datenquelle Gateway Halle 3"));
        OutageState state = OutageState.fromJson(String.valueOf(values.get("1/" + OutageState.ATTRIBUTE).lastEntry().getValue()));
        assertEquals(Long.valueOf(30), state.getOpen().get("row:11").cause);

        /* the outage rule is removed: the row outage ends without a value, the source stays open */
        put(11, "Alarm Config", t(26, 12, 30), "[{\"name\":\"Spitze\",\"limit\":\"40\",\"operator\":\"BIGGER\"}]");
        monitor().run(Collections.singletonList(row), Collections.singletonList(source), t(26, 13, 0));
        state = OutageState.fromJson(String.valueOf(values.get("1/" + OutageState.ATTRIBUTE).lastEntry().getValue()));
        assertFalse(state.getOpen().containsKey("row:11"));
        assertTrue(state.getOpen().containsKey("source:30"));
    }

    @Test
    public void dailySummaryOnceAfterItsTimeAndOnlyWithContent() {
        put(1, OutageMonitor.ATTRIBUTE_DAILY_SUMMARY, t(1, 0, 0), true);
        put(1, OutageMonitor.ATTRIBUTE_DAILY_SUMMARY_TIME, t(1, 0, 0), "07:00");

        /* nothing to report: no mail, but marked as done */
        monitor().dailySummary(t(26, 7, 5));
        assertTrue(mails.isEmpty());

        put(10, "Value", t(26, 9, 0), 5.0);
        monitor().run(Collections.singletonList(row), Collections.emptyList(), t(26, 12, 0));
        mails.clear();
        monitor().dailySummary(t(26, 18, 0));
        assertTrue(mails.isEmpty(), "today's summary was already done at 07:05");
        monitor().dailySummary(t(27, 7, 1));
        assertEquals(1, mails.size());
        assertTrue(mails.get(0)[0].startsWith("Tageszusammenfassung Alarme"));
        assertTrue(mails.get(0)[1].contains("Noch offene Ausfälle (1)"));
        monitor().dailySummary(t(27, 9, 0));
        assertEquals(1, mails.size());
    }
}
