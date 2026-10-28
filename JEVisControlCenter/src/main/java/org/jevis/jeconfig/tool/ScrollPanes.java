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
import javafx.scene.control.ScrollPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScrollPanes {

    private static final Logger logger = LogManager.getLogger(ScrollPanes.class);

    /**
     * Resets the viewport of the first ScrollPane parent of the given Node
     *
     * @param node
     */
    public static void resetParentScrollView(Node node){
        try {
            if (node.getParent() != null) {
                if (node.getParent() instanceof ScrollPane) {
                    ScrollPane parentScroll = (ScrollPane) node.getParent();
                    parentScroll.setVvalue(0);
                    parentScroll.setHvalue(0);
                } else {
                    resetParentScrollView(node.getParent());
                }
            }
        }catch (Exception ex){
            logger.error(ex);
        }
    }
}
