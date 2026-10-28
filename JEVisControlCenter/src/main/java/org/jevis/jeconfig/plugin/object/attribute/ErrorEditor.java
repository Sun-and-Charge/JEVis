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
package org.jevis.jeconfig.plugin.object.attribute;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.jevis.api.JEVisAttribute;

/**
 * @author fs
 */
public class ErrorEditor implements AttributeEditor {

    private BooleanProperty ValueChangedProperty = new SimpleBooleanProperty(false);
    private Label label = new Label();

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void commit() {
    }

    @Override
    public void update() {

    }

    @Override
    public Node getEditor() {
        label.setText("Error while loading values");
        return label;
    }

    @Override
    public BooleanProperty getValueChangedProperty() {
        return ValueChangedProperty;
    }

    @Override
    public void setReadOnly(boolean canRead) {
    }

    public void setException(Exception ex) {
        label.setText("Error: " + ex.toString());
    }

    @Override
    public JEVisAttribute getAttribute() {
        return null;
    }

    @Override
    public boolean isValid() {
        //TODO: implement validation
        return true;
    }

}
