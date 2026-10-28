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
package org.jevis.jeconfig.plugin.object.classes;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jevis.api.JEVisObject;

public class SelectableObject {

    private final SimpleObjectProperty<JEVisObject> object = new SimpleObjectProperty<>(this, "object", null);
    private final SimpleBooleanProperty selected = new SimpleBooleanProperty(this, "selected", true);
    private final SimpleBooleanProperty calculation = new SimpleBooleanProperty(this, "calculation", true);

    public SelectableObject(JEVisObject object, Boolean selected, Boolean calculation) {
        setObject(object);
        setSelected(selected);
        setCalculation(calculation);
    }

    public JEVisObject getObject() {
        return object.get();
    }

    public void setObject(JEVisObject object) {
        this.object.set(object);
    }

    public SimpleObjectProperty<JEVisObject> objectProperty() {
        return object;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public SimpleBooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isCalculation() {
        return calculation.get();
    }

    public void setCalculation(boolean calculation) {
        this.calculation.set(calculation);
    }

    public SimpleBooleanProperty calculationProperty() {
        return calculation;
    }
}
