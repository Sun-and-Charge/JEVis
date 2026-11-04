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
import org.jevis.commons.ws.sql.ConnectionFactory;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Writes user log entries asynchronously in batches into {@code user_log}, so a request does not wait for the log.
 * The queue is bounded; if it is full the caller writes the entry itself. Entries that are still queued when the
 * webservice is killed are lost, {@link #shutdown()} writes them on a normal stop.
 */
public final class UserLogWriter {

    static final int QUEUE_CAPACITY = 10000;
    static final int BATCH_SIZE = 500;
    private static final long FLUSH_INTERVAL_MS = 1000;
    private static final Logger logger = LogManager.getLogger(UserLogWriter.class);
    private static final UserLogWriter INSTANCE = new UserLogWriter();

    private final BlockingQueue<JsonUserLogEntry> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final Object flushLock = new Object();
    private volatile ScheduledExecutorService executor;

    private UserLogWriter() {
    }

    public static UserLogWriter getInstance() {
        return INSTANCE;
    }

    /**
     * Queues an entry.
     *
     * @return {@code false} if the queue is full
     */
    public boolean offer(JsonUserLogEntry entry) {
        ensureStarted();
        return queue.offer(entry);
    }

    private void ensureStarted() {
        if (executor != null) return;
        synchronized (this) {
            if (executor != null) return;
            ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "user-log-writer");
                thread.setDaemon(true);
                return thread;
            });
            ex.scheduleWithFixedDelay(this::flushSafe, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
            executor = ex;
        }
    }

    private void flushSafe() {
        try {
            flush();
        } catch (Throwable t) {
            logger.error("Error while writing the user log", t);
        }
    }

    /**
     * Writes all queued entries in batches of {@value #BATCH_SIZE}.
     */
    public void flush() {
        synchronized (flushLock) {
            while (!queue.isEmpty()) {
                List<JsonUserLogEntry> batch = new ArrayList<>(BATCH_SIZE);
                queue.drainTo(batch, BATCH_SIZE);
                if (batch.isEmpty()) return;
                try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                    new UserLogStore(connection).insert(batch);
                } catch (Exception ex) {
                    UserLogStore.markSchemaUnknown();
                    logger.error("Could not write {} user log entries, they are lost", batch.size(), ex);
                    return;
                }
            }
        }
    }

    /**
     * Stops the writer and writes the queued entries.
     */
    public void shutdown() {
        ScheduledExecutorService ex = executor;
        if (ex != null) {
            ex.shutdown();
            try {
                ex.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        flushSafe();
    }
}
