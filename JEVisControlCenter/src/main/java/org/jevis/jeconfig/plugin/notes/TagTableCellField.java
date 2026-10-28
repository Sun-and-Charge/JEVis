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
import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;

import java.util.HashMap;
import java.util.List;

public class TagTableCellField {

    HashMap<String, BooleanProperty> activeTags;

    public TagTableCellField(HashMap<String, BooleanProperty> activeTags) {
        List<NoteTag> noteTagList = NoteTag.getAllTags();
        ContextMenu cm = new ContextMenu();
        cm.setOnHidden(ev -> {
            //System.out.println("Hide");
        });

        activeTags.forEach((tagKey, tagActive) -> {
            JFXCheckBox cb = new JFXCheckBox(tagKey);
            cb.selectedProperty().bindBidirectional(tagActive);
            cb.setOnAction(event -> {
                tagActive.set(cb.isSelected());
            });
            CustomMenuItem cmi = new CustomMenuItem(cb);
            cm.getItems().add(cmi);
        });

        JFXButton tagButton = new JFXButton("Tags");
        tagButton.setContextMenu(cm);
        tagButton.setOnAction(event -> {
            cm.show(tagButton, Side.BOTTOM, 0, 0);
        });
        Button button = new Button();
        button.setOnAction(event -> {
            cm.show(button, Side.BOTTOM, 0, 0);
        });

    }

    public HashMap<String, BooleanProperty> getActiveTags() {
        return activeTags;
    }
}
