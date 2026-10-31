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

import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.dialog.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * List of users and groups (mail recipients) with buttons to add them from the object tree and to remove them.
 */
public class ObjectListEditor extends VBox {

    private static final Logger logger = LogManager.getLogger(ObjectListEditor.class);
    private final JEVisDataSource ds;
    private final ListView<JEVisObject> listView = new ListView<>();

    public ObjectListEditor(JEVisDataSource ds, List<Long> ids) {
        super(4);
        this.ds = ds;
        listView.setPrefHeight(110);
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setCellFactory(param -> new ListCell<JEVisObject>() {
            @Override
            protected void updateItem(JEVisObject item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                } else {
                    String className = "";
                    try {
                        className = item.getJEVisClassName();
                    } catch (Exception ignored) {
                    }
                    setText(item.getName() + ("Group".equals(className)
                            ? " (" + I18n.getInstance().getString("plugin.scheduledentries.group") + ")" : ""));
                }
            }
        });
        for (Long id : ids) {
            try {
                JEVisObject object = ds.getObject(id);
                if (object != null) {
                    listView.getItems().add(object);
                }
            } catch (Exception e) {
                logger.warn("Recipient {} not readable", id, e);
            }
        }

        Button add = new Button(I18n.getInstance().getString("plugin.scheduledentries.recipients.add"));
        Button remove = new Button(I18n.getInstance().getString("plugin.scheduledentries.recipients.remove"));
        remove.disableProperty().bind(listView.getSelectionModel().selectedItemProperty().isNull());
        add.setOnAction(event -> addRecipients());
        remove.setOnAction(event -> listView.getItems().removeAll(new ArrayList<>(listView.getSelectionModel().getSelectedItems())));

        HBox buttons = new HBox(6, add, remove);
        VBox.setVgrow(listView, Priority.ALWAYS);
        getChildren().addAll(listView, buttons);
    }

    private void addRecipients() {
        try {
            List<JEVisClass> classes = new ArrayList<>();
            for (String name : new String[]{"User", "Group"}) {
                JEVisClass jeVisClass = ds.getJEVisClass(name);
                if (jeVisClass != null) {
                    classes.add(jeVisClass);
                }
            }
            TreeSelectionDialog dialog = new TreeSelectionDialog(ds, classes, SelectionMode.MULTIPLE);
            dialog.showAndWait();
            if (dialog.getResponse() == Response.OK) {
                for (JEVisObject object : dialog.getTreeView().getSelectedObjects()) {
                    if (object != null && listView.getItems().stream().noneMatch(o -> o.getID().equals(object.getID()))) {
                        listView.getItems().add(object);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not select recipients", e);
        }
    }

    public List<Long> getIds() {
        List<Long> ids = new ArrayList<>();
        for (JEVisObject object : listView.getItems()) {
            ids.add(object.getID());
        }
        return ids;
    }
}
