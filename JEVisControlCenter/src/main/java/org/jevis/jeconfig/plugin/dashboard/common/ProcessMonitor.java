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
package org.jevis.jeconfig.plugin.dashboard.common;

import javafx.collections.ListChangeListener;
import javafx.concurrent.Task;
import javafx.stage.Popup;
import org.controlsfx.control.TaskProgressView;
import org.jevis.jeconfig.JEConfig;

public class ProcessMonitor {

    TaskProgressView view = new TaskProgressView<>();
    final Popup popup = new Popup();

    public ProcessMonitor() {
        popup.setAutoFix(true);
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        popup.getContent().add(view);


        view.getTasks().addListener(new ListChangeListener() {
            @Override
            public void onChanged(Change c) {
                if (c.next()) {
                    if (c.wasAdded() && !popup.isShowing()) {
                        popup.show(JEConfig.getStage());
                    }
                }
            }
        });
    }

    public void addTask(Task task) {
        view.getTasks().add(task);

        task.setOnSucceeded(event -> {
//            boolean someArerunning = false;
//            for (Task otherTask : (List<Task>) view.getTasks()) {
//                if (((Task) otherTask).isDone()) {
//                    someArerunning = true;
//                }
//            }
//            if (someArerunning = true) {
//                Platform.runLater(() -> {
//                    popup.hide();
//                });
//            }

        });


//        if (task.ge) {
//
//        }
//        Platform.runLater(() -> {
//            popup.hide();
//        });
//        task.setOnSucceeded(Platform.runLater(() -> {
//        }));
    }

    public void showAndWait() {


    }
}
