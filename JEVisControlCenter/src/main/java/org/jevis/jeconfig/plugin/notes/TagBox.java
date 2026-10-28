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
package org.jevis.jeconfig.plugin.notes;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;

import java.awt.*;
import java.util.HashMap;

public class TagBox extends Menu {

    javafx.scene.control.MenuItem selectAllMenuItem = new javafx.scene.control.MenuItem("Alle Auswählen");
    javafx.scene.control.MenuItem deselectAllMenuItem = new javafx.scene.control.MenuItem("Alle Abwählen");
    JFXButton tagButton = new JFXButton("Tags");

    public TagBox(ObservableList<String> allTags, HashMap<String, BooleanProperty> aktiveTags) throws HeadlessException {
     

    }


    private void createMenuItems() {

    }

}
