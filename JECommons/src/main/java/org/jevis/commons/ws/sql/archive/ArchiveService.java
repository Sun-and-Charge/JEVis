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
package org.jevis.commons.ws.sql.archive;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.sql.ConnectionFactory;

import java.sql.Connection;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Daily run of the old data archive in the webservice (OpenJEVis #1440), only if {@code webservice.archive.enabled}
 * is set.
 */
public final class ArchiveService {

    private static final Logger logger = LogManager.getLogger(ArchiveService.class);
    private static ScheduledExecutorService executor;

    private ArchiveService() {
    }

    /**
     * Starts the daily archive run at the configured time of day (server time zone).
     */
    public static synchronized void startDailyArchive(ArchiveSettings settings) {
        if (executor != null) return;
        if (settings == null || !settings.isEnabled()) {
            logger.info("Old data archive is disabled (webservice.archive.enabled)");
            return;
        }
        if (settings.getDir() == null) {
            logger.error("Old data archive is enabled but no folder is configured (webservice.archive.dir)");
            return;
        }
        long delay = ArchiveUtil.delayUntil(ZonedDateTime.now(), settings.getTime());
        logger.info("Old data archive enabled ({}), next run in {} minutes", settings, delay / 60000);
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "sample-archive");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        executor.scheduleAtFixedRate(() -> run(settings), delay, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
    }

    private static void run(ArchiveSettings settings) {
        try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
            SampleArchiver.RunResult result = new SampleArchiver(connection, settings, null).archive(System.currentTimeMillis());
            if (result.error != null) {
                logger.error("Old data archive stopped with an error: {}", result.message);
            }
        } catch (Throwable t) {
            logger.error("Old data archive failed", t);
        }
    }

    /**
     * Stops the daily run; a running batch is interrupted after its commit.
     */
    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
