/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class ShutdownHookThread extends Thread {
    public static final String THREAD_NAME = "Shutdown Hook";
    private static final Logger logger = LogManager.getLogger(ShutdownHookThread.class);

    public static final int GRACE_PERIOD = 0;
    public static final TimeUnit GRACE_PERIOD_TIME_UNIT = TimeUnit.SECONDS;

    private Thread thread;

    /**
     * @param service The Thread to shut down
     */
    public ShutdownHookThread(Thread service) {
        this.thread = service;
        setName(THREAD_NAME);
    }

    @Override
    public void run() {
        logger.info("Interrupting current Thread");

        logger.info("Waiting for thread to shut down... Grace period is " + GRACE_PERIOD + GRACE_PERIOD_TIME_UNIT);
        thread.interrupt();

        logger.info("Thread stopped.");
    }
}
