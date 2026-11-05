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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.json.JsonUserLogEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Database access for the table {@code user_log} of migration {@code V14__User_Log.sql} and for the old samples of
 * the attribute {@code Activities}.
 */
public class UserLogStore {

    private static final Logger logger = LogManager.getLogger(UserLogStore.class);
    private static final long SCHEMA_RECHECK_MS = 60_000;
    private static volatile boolean schemaAvailable = false;
    private static volatile long schemaCheckedAt = 0;
    private final Connection connection;

    public UserLogStore(Connection connection) {
        this.connection = connection;
    }

    /**
     * @return {@code true} if the table exists (migration V14 applied); a missing table is checked again after a minute
     */
    public boolean isSchemaAvailable() {
        if (schemaAvailable) return true;
        long nowMs = System.currentTimeMillis();
        if (nowMs - schemaCheckedAt < SCHEMA_RECHECK_MS) return false;
        schemaCheckedAt = nowMs;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_log'");
             ResultSet rs = ps.executeQuery()) {
            schemaAvailable = rs.next() && rs.getInt(1) == 1;
        } catch (SQLException ex) {
            logger.error("Could not check the user_log table", ex);
            schemaAvailable = false;
        }
        if (!schemaAvailable) {
            logger.warn("Table user_log is missing. Apply the database migration V14__User_Log.sql "
                    + "(mysqldb: mvn flyway:migrate). The user log is written into the samples of the attribute Activities until then.");
        }
        return schemaAvailable;
    }

    /**
     * Marks the table as missing, e.g. after an insert failed because it was dropped.
     */
    static void markSchemaUnknown() {
        schemaAvailable = false;
        schemaCheckedAt = 0;
    }

    /**
     * Inserts the entries with one multi-row statement.
     *
     * @return the number of inserted rows
     */
    public int insert(List<JsonUserLogEntry> entries) throws SQLException {
        if (entries == null || entries.isEmpty()) return 0;
        StringBuilder sql = new StringBuilder("INSERT INTO user_log (user_id, user_name, ts, action, object_id, details) VALUES ");
        for (int i = 0; i < entries.size(); i++) {
            sql.append(i == 0 ? "(?,?,?,?,?,?)" : ",(?,?,?,?,?,?)");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int p = 0;
            for (JsonUserLogEntry entry : entries) {
                ps.setLong(++p, entry.getUserId());
                ps.setString(++p, UserLogUtil.truncate(entry.getUserName(), UserLogUtil.MAX_USER_NAME_LENGTH));
                ps.setTimestamp(++p, new Timestamp(entry.getTimestamp()));
                ps.setString(++p, UserLogUtil.truncate(entry.getAction(), UserLogUtil.MAX_ACTION_LENGTH));
                if (entry.getObjectId() != null) {
                    ps.setLong(++p, entry.getObjectId());
                } else {
                    ps.setNull(++p, Types.BIGINT);
                }
                ps.setString(++p, UserLogUtil.truncate(entry.getDetails(), UserLogUtil.MAX_DETAILS_LENGTH));
            }
            return ps.executeUpdate();
        }
    }

    /**
     * @param userId  user, or {@code null} for all users
     * @param from    inclusive start in ms, or {@code null}
     * @param until   inclusive end in ms, or {@code null}
     * @param maxRows maximum number of rows, newest first
     */
    public List<JsonUserLogEntry> query(Long userId, Long from, Long until, int maxRows) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT id, user_id, user_name, ts, action, object_id, details FROM user_log WHERE 1=1");
        if (userId != null) sql.append(" AND user_id = ?");
        if (from != null) sql.append(" AND ts >= ?");
        if (until != null) sql.append(" AND ts <= ?");
        sql.append(" ORDER BY ts DESC, id DESC LIMIT ?");
        List<JsonUserLogEntry> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int p = bindRange(ps, userId, from, until);
            ps.setInt(++p, maxRows);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonUserLogEntry entry = new JsonUserLogEntry();
                    entry.setId(rs.getLong("id"));
                    entry.setUserId(rs.getLong("user_id"));
                    entry.setUserName(rs.getString("user_name"));
                    entry.setTimestamp(rs.getTimestamp("ts").getTime());
                    entry.setAction(rs.getString("action"));
                    long objectId = rs.getLong("object_id");
                    entry.setObjectId(rs.wasNull() ? null : objectId);
                    entry.setDetails(rs.getString("details"));
                    entry.setSource(JsonUserLogEntry.SOURCE_LOG);
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /**
     * Reads the old samples of the attribute {@code Activities} of user objects, newest first.
     */
    public List<JsonUserLogEntry> queryLegacy(Long userId, Long from, Long until, int maxRows) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT s.object, s.timestamp, s.value, o.name FROM sample s "
                + "JOIN object o ON o.id = s.object AND o.type = 'User' WHERE s.attribute = '" + UserLogUtil.LEGACY_ATTRIBUTE + "'");
        if (userId != null) sql.append(" AND s.object = ?");
        if (from != null) sql.append(" AND s.timestamp >= ?");
        if (until != null) sql.append(" AND s.timestamp <= ?");
        sql.append(" ORDER BY s.timestamp DESC LIMIT ?");
        List<JsonUserLogEntry> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            int p = bindRange(ps, userId, from, until);
            ps.setInt(++p, maxRows);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(UserLogUtil.fromLegacySample(rs.getLong("object"), rs.getString("name"),
                            rs.getTimestamp("timestamp").getTime(), rs.getString("value")));
                }
            }
        }
        return result;
    }

    private static int bindRange(PreparedStatement ps, Long userId, Long from, Long until) throws SQLException {
        int p = 0;
        if (userId != null) ps.setLong(++p, userId);
        if (from != null) ps.setTimestamp(++p, new Timestamp(from));
        if (until != null) ps.setTimestamp(++p, new Timestamp(until));
        return p;
    }

    /**
     * Deletes at most {@code batchSize} rows older than the cutoff.
     */
    public int deleteOlderThan(long cutoffMs, int batchSize) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM user_log WHERE ts < ? ORDER BY ts LIMIT ?")) {
            ps.setTimestamp(1, new Timestamp(cutoffMs));
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    /**
     * Deletes at most {@code batchSize} old {@code Activities} samples of users older than the cutoff.
     */
    public int deleteLegacyOlderThan(long cutoffMs, int batchSize) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM sample WHERE attribute = '"
                + UserLogUtil.LEGACY_ATTRIBUTE + "' AND timestamp < ? AND object IN (SELECT id FROM object WHERE type = 'User') LIMIT ?")) {
            ps.setTimestamp(1, new Timestamp(cutoffMs));
            ps.setInt(2, batchSize);
            return ps.executeUpdate();
        }
    }

    /**
     * Updates first/last timestamp of the {@code Activities} attributes of all users after old samples were deleted.
     */
    public void refreshLegacyAttributeRange() throws SQLException {
        String sql = "UPDATE attribute a JOIN object o ON o.id = a.object AND o.type = 'User' "
                + "SET a.mints = (SELECT MIN(s.timestamp) FROM sample s WHERE s.object = a.object AND s.attribute = a.name), "
                + "a.maxts = (SELECT MAX(s.timestamp) FROM sample s WHERE s.object = a.object AND s.attribute = a.name), "
                + "a.samplecount = (SELECT IF(COUNT(*) > 0, 1, 0) FROM sample s WHERE s.object = a.object AND s.attribute = a.name) "
                + "WHERE a.name = '" + UserLogUtil.LEGACY_ATTRIBUTE + "'";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }
}
