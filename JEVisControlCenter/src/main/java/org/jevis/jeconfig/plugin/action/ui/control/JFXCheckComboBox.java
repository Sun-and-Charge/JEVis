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
package org.jevis.jeconfig.plugin.action.ui.control;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventDispatcher;
import javafx.geometry.Side;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import org.jevis.jeconfig.plugin.action.data.ActionPlanData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JFXCheckComboBox extends JFXTextField {

    private List<JFXCheckBox> boxes = new ArrayList<>();
    private SimpleStringProperty text = new SimpleStringProperty("");

    public JFXCheckComboBox(ObservableList<String> items, String selected) {
        //super(ActionPlanData.listToString(items).replaceAll(";", ", "));
        super(selected.replaceAll(";", ", "));
        setEditable(false);

        EventDispatcher ed = getEventDispatcher();
        this.setOnMouseClicked(event -> {
            getContextMenu().show(this, Side.BOTTOM, 0, 0);
            event.consume();
        });


        ContextMenu cm = new ContextMenu();
        cm.setOnHidden(ev -> {
        });

        List<String> selectedField = Arrays.asList(selected.split(";"));

        for (String s : items) {
            JFXCheckBox cb = new JFXCheckBox(s);
            cb.setSelected(selectedField.contains(s.trim()));

            boxes.add(cb);
            cb.setOnAction(event -> {
                updateValue();
                cm.hide();
            });
            CustomMenuItem cmi = new CustomMenuItem(cb);
            cm.getItems().add(cmi);
        }
        setContextMenu(cm);


    }

    private void updateValue() {
        ObservableList tmptext = FXCollections.observableArrayList();
        for (JFXCheckBox jfxCheckBox : boxes) {
            if (jfxCheckBox.isSelected()) tmptext.add(jfxCheckBox.getText());
        }

        text.set(ActionPlanData.listToString(tmptext).replaceAll(";", ", "));
        setText(text.getValue());
    }

    public ObservableList getValue() {
        ObservableList tmptext = FXCollections.observableArrayList();
        for (JFXCheckBox jfxCheckBox : boxes) {
            if (jfxCheckBox.isSelected()) tmptext.add(jfxCheckBox.getText());
        }
        return tmptext;
    }

}
