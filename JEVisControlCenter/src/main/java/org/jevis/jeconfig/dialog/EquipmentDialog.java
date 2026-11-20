/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.dialog;

import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisClassRelationship;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.application.jevistree.filter.JEVisTreeFilter;
import org.jevis.jeconfig.application.navigation.PluginSidebar;
import org.jevis.jeconfig.application.ui.Notify;

import java.util.ArrayList;
import java.util.List;

/**
 * J25.5: "Neue Anlage" as a short form (folder and name, docs/DESIGN-CC.md). The object is created only with
 * "Anlegen"; its attributes are filled afterwards in the side panel or in the table. Before, choosing the folder
 * already created an object "Neu" that stayed behind when the dialog was closed.
 */
public class EquipmentDialog extends org.jevis.jeconfig.application.ui.SucDialog<ButtonType> {

    private static final Logger logger = LogManager.getLogger(EquipmentDialog.class);
    private final List<JEVisClass> possibleParents = new ArrayList<>();
    private JEVisObject parent;
    private JEVisObject created;

    public EquipmentDialog(JEVisDataSource ds, JEVisClass jeVisClass) {
        super(PluginSidebar.text("plugin.equipment.new.title", "Neue Anlage", "New equipment"),
                PluginSidebar.text("plugin.equipment.new.description", "Ordner und Name wählen; die Angaben füllen Sie danach im Editor.",
                        "Choose folder and name; fill in the details afterwards in the editor."), Size.M);

        try {
            for (JEVisClass aClass : ds.getJEVisClasses()) {
                for (JEVisClassRelationship rel : aClass.getRelationships(3)) {
                    if (rel.getStart() != null && rel.getEnd() != null && rel.getStart().equals(jeVisClass) && rel.getEnd().equals(aClass)) {
                        possibleParents.add(aClass);
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e);
        }
        possibleParents.remove(jeVisClass);

        Label folderLabel = new Label(PluginSidebar.text("plugin.equipment.new.folder", "Ordner", "Folder"));
        folderLabel.getStyleClass().add("suc-form-label");
        Label folderName = new Label(PluginSidebar.text("plugin.equipment.new.nofolder", "Kein Ordner gewählt", "No folder chosen"));
        folderName.getStyleClass().add("suc-form-empty");
        Button folderButton = new Button(PluginSidebar.text("plugin.equipment.new.choose", "Ordner wählen", "Choose folder"),
                JEConfig.getSVGImage(Icon.FOLDER_OPEN, 16, 16));
        HBox folderRow = new HBox(10, folderButton, folderName);
        folderRow.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(PluginSidebar.text("plugin.equipment.new.name", "Name", "Name"));
        nameLabel.getStyleClass().add("suc-form-label");
        TextField nameField = new TextField();
        nameField.setPromptText(PluginSidebar.text("plugin.equipment.new.nameprompt", "z. B. Lüftung 4", "e.g. Ventilation 4"));

        VBox body = new VBox(6, folderLabel, folderRow, new Label(), nameLabel, nameField);
        setBody(body);

        folderButton.setOnAction(event -> {
            List<JEVisTreeFilter> allFilter = new ArrayList<>();
            List<JEVisClass> others = new ArrayList<>(possibleParents);
            JEVisClass first = others.isEmpty() ? jeVisClass : others.remove(0);
            JEVisTreeFilter filter = SelectTargetDialog.buildMultiClassFilter(first, others);
            allFilter.add(filter);
            SelectTargetDialog select = new SelectTargetDialog(allFilter, filter, null, SelectionMode.SINGLE, ds, new ArrayList<>());
            select.setOnCloseRequest(e -> {
                if (select.getResponse() == SelectTargetDialog.Response.OK && !select.getUserSelection().isEmpty()) {
                    UserSelection us = select.getUserSelection().get(0);
                    parent = us.getSelectedObject();
                    folderName.setText(parent.getName());
                    folderName.getStyleClass().remove("suc-form-empty");
                    dirtyProperty().set(true);
                }
            });
            select.show();
        });
        nameField.textProperty().addListener((o, a, b) -> dirtyProperty().set(true));

        ButtonType create = addFormPrimary(PluginSidebar.text("plugin.equipment.new.create", "Anlegen", "Create"));
        addCancel();
        Button createButton = (Button) getDialogPane().lookupButton(create);
        createButton.addEventFilter(ActionEvent.ACTION, event -> {
            String name = nameField.getText() == null ? "" : nameField.getText().trim();
            if (parent == null || name.isEmpty()) {
                Notify.error(PluginSidebar.text("plugin.equipment.new.missing", "Bitte Ordner und Name angeben.", "Please choose a folder and enter a name."));
                event.consume();
                return;
            }
            try {
                JEVisObject object = parent.buildObject(name, jeVisClass);
                object.commit();
                created = object;
                dirtyProperty().set(false);
            } catch (Exception e) {
                logger.error("Could not create the equipment", e);
                Notify.error(PluginSidebar.text("dialog.ui.error.generic", "Das hat nicht geklappt. Verbindung prüfen und erneut versuchen.", "That did not work. Check the connection and try again."));
                event.consume();
            }
        });
    }

    /** the new object after "Anlegen", else null */
    public JEVisObject getCreated() {
        return created;
    }
}
