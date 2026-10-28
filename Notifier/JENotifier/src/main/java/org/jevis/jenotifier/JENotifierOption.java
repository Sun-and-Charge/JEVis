/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.jenotifier.config.JENotifierConfig;
import org.jevis.jenotifier.mode.Service;
import org.jevis.jenotifier.mode.Single;
import org.jevis.jenotifier.mode.SingleAll;

/**
 * @author gf
 */
public class JENotifierOption {
    private static final Logger logger = LogManager.getLogger(JENotifierOption.class);

    public void runJENotifier(String[] commands) {

        JENotifierConfig config = new JENotifierConfig();
        config.parser(commands);

//        JENotifierHelper.initializeLogger(Level.INFO);
//        logger.info(config.getDebugLevel());

        if (config.getMode().equalsIgnoreCase("single")) {
            logger.info("-------Operation Mode: Single-------");
            Single single = new Single(config);
            single.start();
        } else if (config.getMode().equalsIgnoreCase("singleAll")) {
            logger.info("-------Operation Mode: SingleAll-------");
            SingleAll singleall = new SingleAll(config);
            singleall.start();
        } else if (config.getMode().equalsIgnoreCase("Service")) {
            logger.info("-------Operation Mode: Service-------");
            Service service = new Service(config);
            service.start();
        } else {
            logger.info("-------The Operation Mode: " + config.getMode() + " doesn't exist!-------");
        }

    }
}
