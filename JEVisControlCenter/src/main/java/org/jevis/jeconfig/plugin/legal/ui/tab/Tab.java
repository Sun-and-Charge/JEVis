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
package org.jevis.jeconfig.plugin.legal.ui.tab;


import javafx.scene.Node;

import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.controlsfx.control.NotificationPane;
import org.jevis.jeconfig.plugin.legal.data.ObligationData;

public abstract class Tab extends javafx.scene.control.Tab {
    protected NotificationPane notificationPane = new NotificationPane();

    public Tab(String s) {
        super(s);
    }

    public Tab() {
    }

    public Tab(String s, Node node) {
        super(s, node);
    }

    public abstract void initTab(ObligationData data);

    public abstract void updateView(ObligationData data);

    void add(GridPane pane, int column, int row, int colspan, int rowspan, Priority priority, Node node) {
        pane.add(node, column, row, colspan, rowspan);
        GridPane.setHgrow(node, priority);
    }
}
