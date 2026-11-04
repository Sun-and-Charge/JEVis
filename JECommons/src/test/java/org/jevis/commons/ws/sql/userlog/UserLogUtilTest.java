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
package org.jevis.commons.ws.sql.userlog;

import org.jevis.commons.ws.json.JsonUserLogEntry;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class UserLogUtilTest {

    private static JsonUserLogEntry entry(Long id, long ts, String source) {
        JsonUserLogEntry e = new JsonUserLogEntry();
        e.setId(id);
        e.setTimestamp(ts);
        e.setSource(source);
        e.setUserId(1L);
        e.setAction("UPDATE_OBJECT");
        return e;
    }

    @Test
    public void parsesWebserviceSample() {
        JsonUserLogEntry e = UserLogUtil.fromLegacySample(7, "user object", 1000, "account|UPDATE_ATTRIBUTE|4711:Value");
        assertEquals("account", e.getUserName());
        assertEquals("UPDATE_ATTRIBUTE", e.getAction());
        assertEquals("4711:Value", e.getDetails());
        assertEquals(Long.valueOf(4711), e.getObjectId());
        assertEquals(Long.valueOf(7), e.getUserId());
        assertEquals(JsonUserLogEntry.SOURCE_LEGACY, e.getSource());
        assertNull(e.getId());
    }

    @Test
    public void keepsPipesInDetails() {
        JsonUserLogEntry e = UserLogUtil.fromLegacySample(7, "u", 1000, "account|DELETE_SAMPLE|12:Value|2026 -> 2027");
        assertEquals("DELETE_SAMPLE", e.getAction());
        assertEquals("12:Value|2026 -> 2027", e.getDetails());
        assertEquals(Long.valueOf(12), e.getObjectId());
    }

    @Test
    public void parsesControlCenterAndUnknownSamples() {
        JsonUserLogEntry login = UserLogUtil.fromLegacySample(7, "user object", 1000, "Login: JEVis Control Center Version: 1");
        assertEquals(UserLogUtil.ACTION_LOGIN, login.getAction());
        assertEquals("user object", login.getUserName());
        assertNull(login.getObjectId());
        assertEquals(UserLogUtil.ACTION_LOGOUT, UserLogUtil.fromLegacySample(7, "u", 1, "Logout: x").getAction());
        JsonUserLogEntry other = UserLogUtil.fromLegacySample(7, "u", 1, "a|NOT_AN_EVENT|b");
        assertEquals(UserLogUtil.ACTION_LEGACY, other.getAction());
        assertEquals("a|NOT_AN_EVENT|b", other.getDetails());
        assertEquals(UserLogUtil.ACTION_LEGACY, UserLogUtil.fromLegacySample(7, "u", 1, null).getAction());
    }

    @Test
    public void allServerEventsAreKnown() {
        for (SQLDataSource.LOG_EVENT event : SQLDataSource.LOG_EVENT.values()) {
            assertTrue(event.name(), UserLogUtil.KNOWN_EVENTS.contains(event.name()));
        }
    }

    @Test
    public void parsesObjectId() {
        assertEquals(Long.valueOf(5), UserLogUtil.parseObjectId("5:name"));
        assertNull(UserLogUtil.parseObjectId("restore 5:name"));
        assertNull(UserLogUtil.parseObjectId("5-6-1"));
        assertNull(UserLogUtil.parseObjectId(null));
        assertNull(UserLogUtil.parseObjectId("1234567890123456789:x"));
    }

    @Test
    public void retentionCutoff() {
        long now = 1_000L * UserLogUtil.DAY_MS;
        assertEquals(now - 365 * UserLogUtil.DAY_MS, UserLogUtil.retentionCutoff(now, 365));
        assertEquals(now - UserLogUtil.DAY_MS, UserLogUtil.retentionCutoff(now, 1));
        assertEquals(-1, UserLogUtil.retentionCutoff(now, 0));
        assertEquals(-1, UserLogUtil.retentionCutoff(now, -5));
        /* no int overflow for long retention */
        assertEquals(now - 100_000L * UserLogUtil.DAY_MS, UserLogUtil.retentionCutoff(now, 100_000));
    }

    @Test
    public void deletesInBatchesUntilSmallBatch() throws Exception {
        AtomicInteger remaining = new AtomicInteger(12);
        AtomicInteger calls = new AtomicInteger();
        long total = UserLogUtil.deleteInBatches(size -> {
            calls.incrementAndGet();
            int d = Math.min(size, remaining.get());
            remaining.addAndGet(-d);
            return d;
        }, 5, 100);
        assertEquals(12, total);
        assertEquals(3, calls.get());

        /* exact multiple needs one more empty batch */
        remaining.set(10);
        calls.set(0);
        assertEquals(10, UserLogUtil.deleteInBatches(size -> {
            calls.incrementAndGet();
            int d = Math.min(size, remaining.get());
            remaining.addAndGet(-d);
            return d;
        }, 5, 100));
        assertEquals(3, calls.get());
    }

    @Test
    public void deleteStopsAtMaxBatches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertEquals(15, UserLogUtil.deleteInBatches(size -> {
            calls.incrementAndGet();
            return size;
        }, 5, 3));
        assertEquals(3, calls.get());
    }

    @Test
    public void mergesNewestFirstAndPages() {
        List<JsonUserLogEntry> table = Arrays.asList(entry(2L, 500, JsonUserLogEntry.SOURCE_LOG),
                entry(1L, 100, JsonUserLogEntry.SOURCE_LOG), entry(3L, 300, JsonUserLogEntry.SOURCE_LOG));
        List<JsonUserLogEntry> legacy = Arrays.asList(entry(null, 400, JsonUserLogEntry.SOURCE_LEGACY),
                entry(null, 300, JsonUserLogEntry.SOURCE_LEGACY));

        List<JsonUserLogEntry> all = UserLogUtil.mergePage(0, 10, table, legacy);
        assertEquals(5, all.size());
        long[] expected = {500, 400, 300, 300, 100};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], (long) all.get(i).getTimestamp());
        }
        /* same timestamp: table entry before the old sample */
        assertEquals(JsonUserLogEntry.SOURCE_LOG, all.get(2).getSource());
        assertEquals(JsonUserLogEntry.SOURCE_LEGACY, all.get(3).getSource());

        List<JsonUserLogEntry> page2 = UserLogUtil.mergePage(2, 2, table, legacy);
        assertEquals(2, page2.size());
        assertEquals(300, (long) page2.get(0).getTimestamp());
        assertTrue(UserLogUtil.mergePage(5, 2, table, legacy).isEmpty());
        assertTrue(UserLogUtil.mergePage(0, 5, Collections.<JsonUserLogEntry>emptyList(), null).isEmpty());
    }

    @Test
    public void sameTimestampOrderedByIdDescending() {
        List<JsonUserLogEntry> table = new ArrayList<>(Arrays.asList(entry(1L, 100, JsonUserLogEntry.SOURCE_LOG),
                entry(2L, 100, JsonUserLogEntry.SOURCE_LOG)));
        List<JsonUserLogEntry> page = UserLogUtil.mergePage(0, 2, table);
        assertEquals(Long.valueOf(2), page.get(0).getId());
    }

    @Test
    public void clampsPaging() {
        assertEquals(UserLogUtil.DEFAULT_LIMIT, UserLogUtil.clampLimit(null));
        assertEquals(UserLogUtil.DEFAULT_LIMIT, UserLogUtil.clampLimit(0));
        assertEquals(UserLogUtil.MAX_LIMIT, UserLogUtil.clampLimit(1_000_000));
        assertEquals(0, UserLogUtil.clampOffset(-3, 100));
        assertEquals(UserLogUtil.MAX_WINDOW - 100, UserLogUtil.clampOffset(Integer.MAX_VALUE, 100));
        assertEquals(400, UserLogUtil.clampOffset(400, 100));
    }

    @Test
    public void truncates() {
        assertNull(UserLogUtil.truncate(null, 3));
        assertEquals("abc", UserLogUtil.truncate("abcdef", 3));
        assertEquals("ab", UserLogUtil.truncate("ab", 3));
    }
}
