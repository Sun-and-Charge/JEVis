/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.server.HttpServer;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GrizzlyServerShutdownHookThread extends Thread {
    private static final Logger logger = LogManager.getLogger(GrizzlyServerShutdownHookThread.class);
    public static final String THREAD_NAME = "Grizzly Server Shutdown Hook";

    public static final int GRACE_PERIOD = 0;
    public static final TimeUnit GRACE_PERIOD_TIME_UNIT = TimeUnit.SECONDS;

    private final HttpServer server;

    /**
     * @param server The server to shut down
     */
    public GrizzlyServerShutdownHookThread(HttpServer server) {
        this.server = server;
        setName(THREAD_NAME);
    }

    @Override
    public void run() {
        logger.info("Running Grizzly Server Shutdown Hook.");
        logger.info("Shutting down server.");
        //GrizzlyFuture<HttpServer> future = server.shutdown(GRACE_PERIOD, GRACE_PERIOD_TIME_UNIT);
        GrizzlyFuture<HttpServer> future = server.shutdown();

        try {
            logger.info("Waiting for server to shut down... Grace period is {} {}", GRACE_PERIOD, GRACE_PERIOD_TIME_UNIT);
            future.get();
        } catch (InterruptedException | ExecutionException e) {
            logger.info("Error while shutting down server. ", e);
        }

        logger.info("Server stopped.");
    }
}
