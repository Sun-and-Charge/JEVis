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

import com.jfoenix.controls.JFXTextField;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class TextFieldWithUnit extends HBox {

    private JFXTextField jfxTextField = new JFXTextField();
    private JFXTextField unitField = new JFXTextField();

    public TextFieldWithUnit() {
        super();
        setSpacing(2);

        getChildren().addAll(jfxTextField, unitField);
        HBox.setHgrow(jfxTextField, Priority.ALWAYS);
        HBox.setHgrow(unitField, Priority.NEVER);

        unitField.setPrefWidth(50);
        jfxTextField.setAlignment(Pos.BASELINE_RIGHT);
        unitField.setAlignment(Pos.BASELINE_LEFT);
    }

    public void setEditable(boolean edible) {
        jfxTextField.setEditable(edible);
        unitField.setEditable(edible);
    }

    public JFXTextField getTextField() {
        return jfxTextField;
    }

    public JFXTextField getUnitField() {
        return unitField;
    }
}
