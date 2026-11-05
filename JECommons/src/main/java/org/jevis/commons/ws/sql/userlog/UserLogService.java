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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonUserLogEntry;
import org.jevis.commons.ws.sql.ConnectionFactory;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * User activity log (OpenJEVis #2341): writes into the table {@code user_log}, reads it together with the old
 * samples of the attribute {@code Activities} and deletes old entries. Without migration V14 the log is written
 * into the samples as before.
 */
public final class UserLogService {

    static final int CLEANUP_BATCH_SIZE = 5000;
    static final int CLEANUP_MAX_BATCHES = 10000;
    private static final Logger logger = LogManager.getLogger(UserLogService.class);
    private static ScheduledExecutorService cleanupExecutor;

    private UserLogService() {
    }

    /**
     * Writes a log entry for the user, asynchronously if the table exists. Errors are only logged.
     */
    public static void log(SQLDataSource ds, JEVisUserSQL user, String action, Long objectId, String details) {
        if (user == null) return;
        JsonUserLogEntry entry = new JsonUserLogEntry();
        entry.setUserId(user.getUserID());
        entry.setUserName(user.getAccountName());
        entry.setTimestamp(System.currentTimeMillis());
        entry.setAction(action);
        entry.setObjectId(objectId != null ? objectId : UserLogUtil.parseObjectId(details));
        entry.setDetails(details);
        try {
            if (new UserLogStore(ds.getConnection()).isSchemaAvailable()) {
                if (!UserLogWriter.getInstance().offer(entry)) {
                    logger.warn("User log queue is full, writing directly");
                    new UserLogStore(ds.getConnection()).insert(Collections.singletonList(entry));
                }
                return;
            }
            writeLegacySample(ds, entry);
        } catch (Exception ex) {
            logger.error("Error while logging event {}: {}", action, details, ex);
        }
    }

    private static void writeLegacySample(SQLDataSource ds, JsonUserLogEntry entry) throws Exception {
        JsonSample newSample = new JsonSample();
        newSample.setTs(JsonFactory.sampleDTF.print(new DateTime(entry.getTimestamp())));
        newSample.setValue(String.format("%s|%s|%s", entry.getUserName(), entry.getAction(), entry.getDetails()));
        ds.getSampleTable().insertSamples(entry.getUserId(), UserLogUtil.LEGACY_ATTRIBUTE, JEVisConstants.PrimitiveType.STRING,
                Collections.singletonList(newSample));
    }

    /**
     * Reads one page of the log, newest first, merged from the table and the old samples.
     *
     * @param userId user, or {@code null} for all users
     */
    public static List<JsonUserLogEntry> read(SQLDataSource ds, Long userId, Long from, Long until, int offset, int limit) throws SQLException {
        int window = offset + limit;
        UserLogStore store = new UserLogStore(ds.getConnection());
        List<JsonUserLogEntry> table = store.isSchemaAvailable()
                ? store.query(userId, from, until, window) : Collections.<JsonUserLogEntry>emptyList();
        List<JsonUserLogEntry> legacy = store.queryLegacy(userId, from, until, window);
        return UserLogUtil.mergePage(offset, limit, table, legacy);
    }

    /**
     * Deletes entries of the table and old samples that are older than the retention time, in batches.
     *
     * @return the number of deleted entries
     */
    public static long cleanup(Connection connection, int retentionDays, long nowMs) throws Exception {
        long cutoff = UserLogUtil.retentionCutoff(nowMs, retentionDays);
        if (cutoff < 0) {
            logger.info("User log retention is disabled, nothing to delete");
            return 0;
        }
        UserLogStore store = new UserLogStore(connection);
        long deleted = 0;
        if (store.isSchemaAvailable()) {
            deleted += UserLogUtil.deleteInBatches(size -> store.deleteOlderThan(cutoff, size), CLEANUP_BATCH_SIZE, CLEANUP_MAX_BATCHES);
        }
        long legacy = UserLogUtil.deleteInBatches(size -> store.deleteLegacyOlderThan(cutoff, size), CLEANUP_BATCH_SIZE, CLEANUP_MAX_BATCHES);
        if (legacy > 0) {
            store.refreshLegacyAttributeRange();
        }
        deleted += legacy;
        logger.info("User log cleanup: deleted {} entries older than {} days", deleted, retentionDays);
        return deleted;
    }

    /**
     * Starts the daily cleanup of the webservice (first run after five minutes).
     */
    public static synchronized void startDailyCleanup(int retentionDays) {
        if (cleanupExecutor != null) return;
        if (retentionDays <= 0) {
            logger.info("User log retention is disabled (webservice.userlog.retentiondays <= 0)");
            return;
        }
        logger.info("User log retention: {} days", retentionDays);
        cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "user-log-cleanup");
            thread.setDaemon(true);
            return thread;
        });
        cleanupExecutor.scheduleWithFixedDelay(() -> {
            try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                cleanup(connection, retentionDays, System.currentTimeMillis());
            } catch (Throwable t) {
                logger.error("User log cleanup failed", t);
            }
        }, 5, 24 * 60, TimeUnit.MINUTES);
    }

    /**
     * Stops the cleanup and writes the queued entries.
     */
    public static synchronized void shutdown() {
        if (cleanupExecutor != null) {
            cleanupExecutor.shutdownNow();
            cleanupExecutor = null;
        }
        UserLogWriter.getInstance().shutdown();
    }
}
