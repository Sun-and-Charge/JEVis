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
package org.jevis.jeconfig.taskmanager;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;

public class TaskWindow {

    public TaskWindow(ObservableList<Task> tasks) {

        TaskPane taskPane = new TaskPane(tasks);
        Notifications notification = Notifications.create()
                .title("Tasks")
                //.text("Tasks....")
                .graphic(taskPane)
                .hideAfter(Duration.minutes(5));

        SimpleBooleanProperty isShowing = new SimpleBooleanProperty(false);
        isShowing.addListener((observable, oldValue, newValue) -> {
            if(newValue){
                Platform.runLater(() -> {

                    /** TODO:check if allready shown**/
                    notification.show();
                });
            }
        });



        tasks.addListener(new ListChangeListener<Task>() {
            @Override
            public void onChanged(Change<? extends Task> c) {
                while (c.next()) {
                    System.out.println("Task List Changed");
                   if (c.wasPermutated()) {
                        for (int i = c.getFrom(); i < c.getTo(); ++i) {
                            //permutate
                        }
                    } else if (c.wasUpdated()) {
                        //update item
                    } else {
                       if( c.wasRemoved() || c.wasAdded()){
                           if(!isShowing.get()){
                               isShowing.setValue(true);
                           }
                       }

                    }
                }
            }
        });


        //notification.showInformation();

    }
}
