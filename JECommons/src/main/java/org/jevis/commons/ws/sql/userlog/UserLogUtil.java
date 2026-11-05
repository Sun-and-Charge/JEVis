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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure helper logic of the user activity log (OpenJEVis #2341), shared by the webservice and the Control Center:
 * parsing of the old {@code Activities} samples, merging and paging, retention and batch deletion.
 */
public final class UserLogUtil {

    public static final String ACTION_LOGIN = "USER_LOGIN";
    public static final String ACTION_LOGOUT = "USER_LOGOUT";
    public static final String ACTION_LEGACY = "LEGACY";
    /**
     * Name of the old log attribute of the class {@code User}.
     */
    public static final String LEGACY_ATTRIBUTE = "Activities";
    public static final int DEFAULT_RETENTION_DAYS = 365;
    public static final int MAX_LIMIT = 1000;
    public static final int DEFAULT_LIMIT = 200;
    /**
     * Upper bound of {@code offset + limit}, every source is read up to this number of rows.
     */
    public static final int MAX_WINDOW = 20000;
    public static final int MAX_ACTION_LENGTH = 64;
    public static final int MAX_USER_NAME_LENGTH = 255;
    public static final int MAX_DETAILS_LENGTH = 20000;
    public static final long DAY_MS = 24L * 60 * 60 * 1000;
    /**
     * Events written by the webservice into the old samples, see {@code V14__User_Log.sql}.
     */
    public static final Set<String> KNOWN_EVENTS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "USER_LOGIN", "USER_LOGOUT", "DELETE_OBJECT", "MARK_AS_DELETE_OBJECT", "DELETE_SAMPLE",
            "DELETE_RELATIONSHIP", "CREATE_OBJECT", "CREATE_SAMPLE", "CREATE_RELATIONSHIP", "UPDATE_OBJECT",
            "UPDATE_ATTRIBUTE")));
    /**
     * Newest first; with the same timestamp entries of the table before old samples, then the higher id first.
     */
    public static final Comparator<JsonUserLogEntry> NEWEST_FIRST = (a, b) -> {
        int c = Long.compare(ts(b), ts(a));
        if (c != 0) return c;
        c = Integer.compare(sourceRank(a), sourceRank(b));
        if (c != 0) return c;
        return Long.compare(b.getId() != null ? b.getId() : Long.MIN_VALUE, a.getId() != null ? a.getId() : Long.MIN_VALUE);
    };
    private static final Pattern OBJECT_ID = Pattern.compile("^([0-9]{1,18}):");

    private UserLogUtil() {
    }

    private static long ts(JsonUserLogEntry entry) {
        return entry.getTimestamp() != null ? entry.getTimestamp() : Long.MIN_VALUE;
    }

    private static int sourceRank(JsonUserLogEntry entry) {
        return JsonUserLogEntry.SOURCE_LEGACY.equals(entry.getSource()) ? 1 : 0;
    }

    /**
     * @return the object id at the start of the details ({@code "<id>:..."}), or {@code null}
     */
    public static Long parseObjectId(String details) {
        if (details == null) return null;
        Matcher m = OBJECT_ID.matcher(details);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    /**
     * Converts an old sample of the attribute {@code Activities}, same rules as the migration {@code V14}.
     *
     * @param userName name of the user object, used if the value does not contain the account
     */
    public static JsonUserLogEntry fromLegacySample(long userId, String userName, long timestamp, String value) {
        JsonUserLogEntry entry = new JsonUserLogEntry();
        entry.setUserId(userId);
        entry.setTimestamp(timestamp);
        entry.setSource(JsonUserLogEntry.SOURCE_LEGACY);
        entry.setUserName(userName);
        String v = value != null ? value : "";
        int first = v.indexOf('|');
        int second = first >= 0 ? v.indexOf('|', first + 1) : -1;
        if (second > 0 && KNOWN_EVENTS.contains(v.substring(first + 1, second))) {
            entry.setUserName(v.substring(0, first));
            entry.setAction(v.substring(first + 1, second));
            entry.setDetails(v.substring(second + 1));
            entry.setObjectId(parseObjectId(entry.getDetails()));
        } else if (v.startsWith("Login:")) {
            entry.setAction(ACTION_LOGIN);
            entry.setDetails(v);
        } else if (v.startsWith("Logout:")) {
            entry.setAction(ACTION_LOGOUT);
            entry.setDetails(v);
        } else {
            entry.setAction(ACTION_LEGACY);
            entry.setDetails(v);
        }
        return entry;
    }

    /**
     * @return {@code limit} limited to 1..{@value #MAX_LIMIT}, {@value #DEFAULT_LIMIT} if not set
     */
    public static int clampLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * @return {@code offset} limited to 0..({@value #MAX_WINDOW} - limit)
     */
    public static int clampOffset(Integer offset, int limit) {
        if (offset == null || offset < 0) return 0;
        return Math.min(offset, Math.max(0, MAX_WINDOW - limit));
    }

    /**
     * Merges entries of several sources (each may be unsorted) newest first and returns one page.
     * Every source has to contain at least its newest {@code offset + limit} entries of the requested range.
     */
    @SafeVarargs
    public static List<JsonUserLogEntry> mergePage(int offset, int limit, List<JsonUserLogEntry>... sources) {
        List<JsonUserLogEntry> all = new ArrayList<>();
        for (List<JsonUserLogEntry> source : sources) {
            if (source != null) all.addAll(source);
        }
        all.sort(NEWEST_FIRST);
        if (offset >= all.size() || limit <= 0) return new ArrayList<>();
        return new ArrayList<>(all.subList(Math.max(0, offset), Math.min(all.size(), offset + limit)));
    }

    /**
     * @return the time before which entries are deleted, or {@code -1} if the log is kept forever ({@code days <= 0})
     */
    public static long retentionCutoff(long nowMs, int retentionDays) {
        if (retentionDays <= 0) return -1;
        return nowMs - retentionDays * DAY_MS;
    }

    /**
     * Calls the deleter until a batch deletes less than {@code batchSize} rows or {@code maxBatches} is reached.
     *
     * @return the number of deleted rows
     */
    public static long deleteInBatches(BatchDeleter deleter, int batchSize, int maxBatches) throws Exception {
        long total = 0;
        for (int i = 0; i < maxBatches; i++) {
            int deleted = deleter.deleteBatch(batchSize);
            total += Math.max(0, deleted);
            if (deleted < batchSize) break;
        }
        return total;
    }

    /**
     * @return the text cut to {@code max} characters, {@code null} stays {@code null}
     */
    public static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max);
    }

    @FunctionalInterface
    public interface BatchDeleter {
        /**
         * Deletes at most {@code batchSize} rows.
         *
         * @return the number of deleted rows
         */
        int deleteBatch(int batchSize) throws Exception;
    }
}
