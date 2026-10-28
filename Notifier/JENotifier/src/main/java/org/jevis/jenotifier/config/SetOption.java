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
package org.jevis.jenotifier.config;

import org.jevis.jenotifier.JENotifierHelper;
import org.apache.commons.cli.Option;
import org.jevis.commons.cli.JEVisCommandLine;

/**
 *
 * @author gf
 */
public class SetOption {

    /**
     * To create the special command line for JENotifier. And return the
     * variable of type JEVisCommandLine.
     *
     * @return
     */
    public static JEVisCommandLine JENotifierCommandLine() {
        JEVisCommandLine cmd = JEVisCommandLine.getInstance();

        cmd.addOption(new Option("shm", JENotifierHelper.DB_SCHEMA, true, JENotifierHelper.DB_SCHEMA_EXP));
        cmd.addOption(new Option("jeus", JENotifierHelper.JEVIS_USER_NAME, true, JENotifierHelper.JEVIS_USER_NAME_EXP));
        cmd.addOption(new Option("jeuspw", JENotifierHelper.JEVIS_USER_PW, true, JENotifierHelper.JEVIS_USER_PW_EXP));
        cmd.addOption(new Option("md", JENotifierHelper.MODE, true, JENotifierHelper.MODE_EXP));
//        cmd.addOption(new Option("intv", JENotifierHelper.INTERVAL, true, JENotifierHelper.INTERVAL_EXP));
        cmd.addOption(new Option("nids", JENotifierHelper.NOTI_IDS, true, JENotifierHelper.NOTI_IDS_EXP));
        cmd.addOption(new Option("ndids", JENotifierHelper.NOTI_DRI_IDS, true, JENotifierHelper.NOTI_DRI_IDS_EXP));
        cmd.addOption(new Option("pathn", JENotifierHelper.PATH_NOTIFIER_JAR, true, JENotifierHelper.PATH_NOTIFIER_JAR_EXP));
        cmd.addOption(new Option("nnj", JENotifierHelper.NAME_NOTIFIER_JAR, true, JENotifierHelper.NAME_NOTIFIER_JAR_EXP));
        cmd.addOption(new Option("nnc", JENotifierHelper.NAME_NOTI_CLASS, true, JENotifierHelper.NAME_NOTI_CLASS_EXP));
        cmd.addOption(new Option("ndc", JENotifierHelper.NAME_Driver_CLASS, true, JENotifierHelper.NAME_Driver_CLASS_EXP));
        cmd.addOption(new Option("nthr", JENotifierHelper.NUMBER_THREAD, true, JENotifierHelper.NUMBER_THREAD_EXP));
        cmd.addOption(new Option("sp", JENotifierHelper.SERVICE_PORT, true, JENotifierHelper.SERVICE_PORT_EXP));

        return cmd;
    }
}
