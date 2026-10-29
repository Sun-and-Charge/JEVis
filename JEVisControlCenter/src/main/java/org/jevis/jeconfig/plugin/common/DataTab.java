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
package org.jevis.jeconfig.plugin.common;

import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.controlsfx.control.NotificationPane;

/**
 * Tab of an editor form that shows one data object of type T inside a NotificationPane.
 */
public abstract class DataTab<T> extends Tab {
    protected final NotificationPane notificationPane = new NotificationPane();

    public DataTab(String s) {
        super(s);
    }

    public DataTab() {
    }

    public DataTab(String s, Node node) {
        super(s, node);
    }

    public abstract void initTab(T data);

    public abstract void updateView(T data);

    protected void add(GridPane pane, int column, int row, int colspan, int rowspan, Priority priority, Node node) {
        pane.add(node, column, row, colspan, rowspan);
        GridPane.setHgrow(node, priority);
    }

    public void showNotification(String text, Node icon) {
        notificationPane.show(text, icon);
        notificationPane.setOnHiding(event -> this.getTabPane().getTabs().forEach(tab -> {
            if (tab.getContent() instanceof NotificationPane) {
                ((NotificationPane) tab.getContent()).hide();
            }
        }));
    }
}
