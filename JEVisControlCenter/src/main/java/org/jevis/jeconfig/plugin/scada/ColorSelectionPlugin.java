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
package org.jevis.jeconfig.plugin.scada;

import javafx.scene.control.TreeTableColumn;
import org.jevis.jeconfig.application.jevistree.JEVisTree;
import org.jevis.jeconfig.application.jevistree.JEVisTreeRow;
import org.jevis.jeconfig.application.jevistree.TreePlugin;

import java.util.List;

public class ColorSelectionPlugin implements TreePlugin {
    @Override
    public void setTree(JEVisTree tree) {
//        FontSelectorDialog
    }

    @Override
    public List<TreeTableColumn<JEVisTreeRow, Long>> getColumns() {
        return null;
    }

    @Override
    public void selectionFinished() {

    }

    @Override
    public String getTitle() {
        return null;
    }
}
