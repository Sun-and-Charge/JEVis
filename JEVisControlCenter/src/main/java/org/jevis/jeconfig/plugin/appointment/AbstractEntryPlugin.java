/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.plugin.appointment;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import org.jevis.api.JEVisDataSource;
import org.jevis.jeconfig.Plugin;

/**
 * Common plugin plumbing of the appointment and maintenance modules: a toolbar and a content pane that is loaded on
 * first focus.
 */
public abstract class AbstractEntryPlugin implements Plugin {

    protected final BorderPane rootPane = new BorderPane();
    private final StringProperty nameProperty = new SimpleStringProperty();
    private final StringProperty uuidProperty;
    protected JEVisDataSource ds;
    private boolean loaded = false;

    protected AbstractEntryPlugin(JEVisDataSource ds, String name, String uuid) {
        this.ds = ds;
        this.nameProperty.set(name);
        this.uuidProperty = new SimpleStringProperty(uuid);
    }

    protected abstract ToolBar toolbar();

    protected abstract void reload();

    @Override
    public String getName() {
        return nameProperty.get();
    }

    @Override
    public void setName(String name) {
        nameProperty.set(name);
    }

    @Override
    public StringProperty nameProperty() {
        return nameProperty;
    }

    @Override
    public String getUUID() {
        return uuidProperty.get();
    }

    @Override
    public void setUUID(String id) {
        uuidProperty.set(id);
    }

    @Override
    public String getToolTip() {
        return nameProperty.get();
    }

    @Override
    public StringProperty uuidProperty() {
        return uuidProperty;
    }

    @Override
    public Node getMenu() {
        return new Pane();
    }

    @Override
    public boolean supportsRequest(int cmdType) {
        return false;
    }

    @Override
    public Node getToolbar() {
        return toolbar();
    }

    @Override
    public void updateToolbar() {
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public void setDataSource(JEVisDataSource ds) {
        this.ds = ds;
    }

    @Override
    public void handleRequest(int cmdType) {
    }

    @Override
    public Node getContentNode() {
        return rootPane;
    }

    @Override
    public void fireCloseEvent() {
    }

    @Override
    public void setHasFocus() {
        if (!loaded) {
            loaded = true;
            reload();
        }
    }

    @Override
    public void lostFocus() {
    }

    @Override
    public void openObject(Object object) {
    }
}
