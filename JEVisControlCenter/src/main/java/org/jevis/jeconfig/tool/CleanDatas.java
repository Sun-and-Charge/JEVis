/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.tool;

import javafx.concurrent.Task;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.jevistree.JEVisTree;
import org.jevis.jeconfig.application.jevistree.JEVisTreeItem;
import org.jevis.jeconfig.application.jevistree.methods.CommonMethods;
import org.joda.time.DateTimeZone;

import java.util.TimeZone;

public class CleanDatas {

    private static final Logger logger = LogManager.getLogger(CleanDatas.class);


    public static void createTask(JEVisTree tree) {
        logger.debug("Setting default timezone to UTC");
        TimeZone defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        DateTimeZone defaultDateTimeZone = DateTimeZone.getDefault();
        DateTimeZone.setDefault(DateTimeZone.UTC);

        tree.getSelectionModel().getSelectedItems().forEach(o -> {

            JEVisTreeItem jeVisTreeItem = (JEVisTreeItem) o;

            try {
                JEVisObject obj = jeVisTreeItem.getValue().getJEVisObject();

                if (obj.getJEVisClassName().equals("Clean Data") || obj.getJEVisClassName().equals("Math Data")) {
                    recalculate(obj);
                } else if (obj.getJEVisClassName().equals("Data")) {
                    obj.getChildren().forEach(object -> {
                        try {
                            if (object.getJEVisClassName().equals("Clean Data")) {
                                recalculate(object);
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }


            } catch (Exception ex) {
                ex.printStackTrace();
            }

        });

        Task<Void> waitTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try {
                    CommonMethods.checkForActiveRecalculation(defaultTimeZone, defaultDateTimeZone);
                } catch (Exception e) {
                    failed();
                } finally {
                    succeeded();
                }

                return null;
            }
        };

        if (!JEConfig.getStatusBar().getTaskList().containsValue(CommonMethods.WAIT_FOR_TIMEZONE)) {
            JEConfig.getStatusBar().addTask(CommonMethods.WAIT_FOR_TIMEZONE, waitTask, JEConfig.getImage("1476369770_Sync.png"), true);
        }
    }

    public static void recalculate(JEVisObject cleanObject) {
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                try {

                    updateMessage("Recalculate " + cleanObject.getParents().get(0).getName());
                    org.jevis.commons.utils.CommonMethods.processCleanData(cleanObject);

                    this.succeeded();

                } catch (Exception ex) {
                    ex.printStackTrace();
                    this.failed();
                }
                return null;
            }
        };

        JEConfig.getStatusBar().addTask(CommonMethods.RECALCULATION, task, JEConfig.getImage("calc.png"), true);

    }
}
