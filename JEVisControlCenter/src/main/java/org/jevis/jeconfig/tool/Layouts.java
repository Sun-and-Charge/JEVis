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

import javafx.scene.Node;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Region;

public class Layouts {

    public static void setAnchor(Node node, double value) {
        AnchorPane.setTopAnchor(node, value);
        AnchorPane.setRightAnchor(node, value);
        AnchorPane.setBottomAnchor(node, value);
        AnchorPane.setLeftAnchor(node, value);
    }

    public static void setSize(Region node, double maxWidth, double maxHeight) {
        node.setMinSize(maxWidth, maxHeight);
        node.setMinSize(maxWidth, maxHeight);
        node.setPrefSize(maxWidth, maxHeight);
    }

}
