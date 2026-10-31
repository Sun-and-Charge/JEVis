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
package org.jevis.commons.ws.sql.auth;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

/**
 * Database access for the table {@code password_reset} of migration {@code V13__Password_Reset.sql}.
 * Timestamps use the clock of the webservice, like {@link AuthStore}.
 */
public class PasswordResetStore {

    private static final Logger logger = LogManager.getLogger(PasswordResetStore.class);
    private static final long SCHEMA_RECHECK_MS = 60_000;
    private static volatile boolean schemaAvailable = false;
    private static volatile long schemaCheckedAt = 0;
    private final Connection connection;

    public PasswordResetStore(Connection connection) {
        this.connection = connection;
    }

    /**
     * @return {@code true} if the table exists (migration V13 applied)
     */
    public boolean isSchemaAvailable() {
        if (schemaAvailable) return true;
        long nowMs = System.currentTimeMillis();
        if (nowMs - schemaCheckedAt < SCHEMA_RECHECK_MS) return false;
        schemaCheckedAt = nowMs;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'password_reset'");
             ResultSet rs = ps.executeQuery()) {
            schemaAvailable = rs.next() && rs.getInt(1) == 1;
        } catch (SQLException ex) {
            logger.error("Could not check the password_reset table", ex);
            schemaAvailable = false;
        }
        if (!schemaAvailable) {
            logger.error("Table password_reset is missing. Apply the database migration V13__Password_Reset.sql "
                    + "(mysqldb: mvn flyway:migrate). The password reset is disabled until then.");
        }
        return schemaAvailable;
    }

    public int countRequestsSince(long userId, long sinceMs) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM password_reset WHERE user_id = ? AND created >= ?")) {
            ps.setLong(1, userId);
            ps.setTimestamp(2, new Timestamp(sinceMs));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Invalidates all open codes of a user.
     */
    public void invalidateOpen(long userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE password_reset SET used = 1 WHERE user_id = ? AND used = 0")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    public void invalidate(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE password_reset SET used = 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * @return the id of the new row
     */
    public long insert(long userId, String codeHash, long createdMs, long expiresMs, String clientInfo) throws SQLException {
        String sql = "INSERT INTO password_reset (user_id, code_hash, created, expires, attempts, used, client_info) VALUES (?,?,?,?,0,0,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, codeHash);
            ps.setTimestamp(3, new Timestamp(createdMs));
            ps.setTimestamp(4, new Timestamp(expiresMs));
            ps.setString(5, clientInfo != null && clientInfo.length() > 255 ? clientInfo.substring(0, 255) : clientInfo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    /**
     * @return the newest code of the user that is not used yet, or {@code null}
     */
    public PasswordResetPolicy.Entry findOpen(long userId) throws SQLException {
        String sql = "SELECT id, user_id, code_hash, created, expires, attempts, used FROM password_reset "
                + "WHERE user_id = ? AND used = 0 ORDER BY created DESC, id DESC LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new PasswordResetPolicy.Entry(rs.getLong("id"), rs.getLong("user_id"), rs.getString("code_hash"),
                        rs.getTimestamp("created").getTime(), rs.getTimestamp("expires").getTime(),
                        rs.getInt("attempts"), rs.getBoolean("used"));
            }
        }
    }

    public void recordAttempt(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE password_reset SET attempts = attempts + 1 WHERE id = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Marks a code as used, atomically.
     *
     * @return {@code false} if another request used it first
     */
    public boolean markUsed(long id) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("UPDATE password_reset SET used = 1 WHERE id = ? AND used = 0")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Removes rows that are older than one day; they are not needed for the request limit anymore.
     */
    public int deleteOlderThan(long beforeMs) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM password_reset WHERE created < ?")) {
            ps.setTimestamp(1, new Timestamp(beforeMs));
            return ps.executeUpdate();
        }
    }
}
