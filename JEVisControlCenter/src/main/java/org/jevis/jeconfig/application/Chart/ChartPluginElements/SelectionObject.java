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
package org.jevis.jeconfig.application.Chart.ChartPluginElements;

import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import org.jevis.api.JEVisObject;

public class SelectionObject extends RecursiveTreeObject<SelectionObject> {

    private final SimpleStringProperty name;
    private final SimpleBooleanProperty selected;
    private final SimpleLongProperty id;
    private final SimpleLongProperty parent;
    private final JEVisObject object;

    SelectionObject(String name, Long id, Long parent, JEVisObject object) {
        this.name = new SimpleStringProperty(this, "name", name);
        this.selected = new SimpleBooleanProperty(this, "selected", false);
        this.id = new SimpleLongProperty(this, "id", id);
        this.parent = new SimpleLongProperty(this, "parent", parent);
        this.object = object;
    }

    public String getName() {
        return this.name.get();
    }

    public void setName(String firstName) {
        this.name.set(firstName);
    }

    public SimpleStringProperty nameProperty() {
        return name;
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

    public Long getId() {
        return this.id.get();
    }

    public void setId(long id) {
        this.id.set(id);
    }

    public void setId(Long id) {
        this.id.set(id);
    }

    public SimpleLongProperty idProperty() {
        return id;
    }

    public Long getParent() {
        return this.parent.get();
    }

    public void setParent(long parent) {
        this.parent.set(parent);
    }

    public void setParent(Long id) {
        this.parent.set(id);
    }

    public SimpleLongProperty parentProperty() {
        return parent;
    }

    public JEVisObject getObject() {
        return object;
    }

    @Override
    public String toString() {
        return name.get();
    }
}
