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

//import org.apache.log4j.Appender;
//import org.apache.log4j.FileAppender;
//import org.apache.log4j.PatternLayout;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//import org.jevis.commons.cli.JEVisCommandLine;

/**
 * @author gf
 */
public class Main {

    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) {
        JENotifierOption option = new JENotifierOption();

//        JENotifierHelper.initializeLogger(Level.FATAL);
//        Appender appender = Logger.getRootLogger().getAppender("FILE");
        logger.info("-------Start JENotifier-------");
//        Helper.initializeLogger(JEVisCommandLine.getInstance().getDebugLevel());

        option.runJENotifier(args);

        logger.info("-------End JENotifier-------");
    }
//    private void initNewAppender(String NameForAppender, String Name4LogFile) {
////        logger = Logger.getLogger(NameForAppender); //NOT DEFAULT BY "logger = Logger.getLogger(TestJob.class);"
//
//        FileAppender appender = new FileAppender();
//        appender.setLayout(new PatternLayout("[%d{dd MMM yyyy HH:mm:ss}][%c{2}]: %-10m%n"));
//        appender.setFile(Name4LogFile);
//        appender.setAppend(true);
//        appender.setImmediateFlush(true);
//        appender.activateOptions();
//        appender.setName(NameForAppender);
//        appender.addFilter(new ThreadFilter(NameForAppender));
//        logger.setAdditivity(false);    //<--do not use default root logger
//        logger.addAppender(appender);
//    }
}
