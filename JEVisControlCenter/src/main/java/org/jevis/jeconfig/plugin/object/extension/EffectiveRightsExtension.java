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
package org.jevis.jeconfig.plugin.object.extension;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisUser;
import org.jevis.commons.ws.json.JsonEffectiveRights;
import org.jevis.commons.ws.json.JsonObjectPermissions;
import org.jevis.commons.ws.json.JsonPermissionRef;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jeconfig.plugin.object.ObjectEditorExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jevis.jeconfig.plugin.object.permission.EffectiveRightsText;

import static org.jevis.jeconfig.plugin.object.permission.EffectiveRightsText.*;

/**
 * Object editor tab "Wer darf was?" (OpenJEVis #1211): the effective rights of all users on the object, as computed by
 * the web service, with the groups and roles they come from. Read-only. Shown to Sys Admins and users with write
 * right on the object; the web service decides which users the caller may see.
 */
public class EffectiveRightsExtension implements ObjectEditorExtension {

    private static final Logger logger = LogManager.getLogger(EffectiveRightsExtension.class);
    private static final String TITLE = s("plugin.object.effectiverights.title");

    private final JEVisObject obj;
    private final BorderPane view = new BorderPane();
    private final BooleanProperty changed = new SimpleBooleanProperty(false);
    private final ObservableList<JsonEffectiveRights> rows = FXCollections.observableArrayList();
    private final FilteredList<JsonEffectiveRights> filteredRows = new FilteredList<>(rows, r -> true);
    private final Label info = new Label();
    private final TextField filter = new TextField();
    private boolean built = false;

    public EffectiveRightsExtension(JEVisObject obj) {
        this.obj = obj;
    }

    /**
     * @return {@code true} if the current user is a Sys Admin or may write the object
     */
    static boolean isAllowed(JEVisObject obj) {
        try {
            JEVisDataSource ds = obj.getDataSource();
            if (!(ds instanceof JEVisDataSourceWS)) return false;
            JEVisUser user = ds.getCurrentUser();
            return user != null && (user.isSysAdmin() || user.canWrite(obj.getID()));
        } catch (JEVisException ex) {
            logger.debug("Could not check the rights of the current user", ex);
            return false;
        }
    }

    @Override
    public boolean isForObject(JEVisObject obj) {
        return obj != null && obj.getID() != null && isAllowed(obj);
    }

    @Override
    public Node getView() {
        return view;
    }

    @Override
    public void setVisible() {
        Platform.runLater(() -> {
            if (!built) {
                build();
                built = true;
            }
            reload();
        });
    }

    private void build() {
        filter.setPromptText(s("plugin.object.effectiverights.filter"));
        filter.textProperty().addListener((observable, oldValue, newValue) -> {
            String text = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filteredRows.setPredicate(r -> text.isEmpty()
                    || (r.getUserName() != null && r.getUserName().toLowerCase(Locale.ROOT).contains(text))
                    || summary(r).toLowerCase(Locale.ROOT).contains(text));
        });
        HBox.setHgrow(filter, Priority.ALWAYS);

        Button reload = new Button(s("plugin.object.effectiverights.reload"));
        reload.setOnAction(event -> reload());

        HBox bar = new HBox(8, filter, reload);
        bar.setAlignment(Pos.CENTER_LEFT);

        TableView<JsonEffectiveRights> table = new TableView<>(filteredRows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(s("plugin.object.effectiverights.empty")));
        table.setPrefHeight(420);

        List<TableColumn<JsonEffectiveRights, ?>> columns = new ArrayList<>();
        columns.add(EffectiveRightsText.<JsonEffectiveRights>textColumn(s("plugin.object.effectiverights.user"),
                r -> userLabel(r.getUserName(), r.getUserId(), r.isSysAdmin(), r.isEnabled()), 180));
        for (int right = READ; right <= EXECUTE; right++) {
            final int current = right;
            columns.add(EffectiveRightsText.<JsonEffectiveRights>tickColumn(rightName(current), r -> has(r, current), r -> rightExplanation(r, current)));
        }
        columns.add(EffectiveRightsText.<JsonEffectiveRights>textColumn(s("plugin.object.effectiverights.sources"), r -> summary(r), 320));
        table.getColumns().addAll(columns);

        info.setWrapText(true);

        VBox box = new VBox(8, info, bar, table);
        box.setPadding(new Insets(8));
        VBox.setVgrow(table, Priority.ALWAYS);
        view.setCenter(box);
    }

    private void reload() {
        info.setText(s("plugin.object.effectiverights.loading"));
        Task<JsonObjectPermissions> task = new Task<JsonObjectPermissions>() {
            @Override
            protected JsonObjectPermissions call() throws Exception {
                return ((JEVisDataSourceWS) obj.getDataSource()).getObjectPermissions(obj.getID());
            }
        };
        task.setOnSucceeded(event -> show(task.getValue()));
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            logger.warn("Could not load the effective rights of object {}", obj.getID(), ex);
            rows.clear();
            info.setText(s("plugin.object.effectiverights.error", ex != null ? ex.getMessage() : ""));
        });
        Thread thread = new Thread(task, "effective-rights-" + obj.getID());
        thread.setDaemon(true);
        thread.start();
    }

    private void show(JsonObjectPermissions permissions) {
        if (permissions == null) {
            rows.clear();
            info.setText(s("plugin.object.effectiverights.unsupported"));
            return;
        }
        List<String> lines = new ArrayList<>();
        if (permissions.getOwnerGroups() == null || permissions.getOwnerGroups().isEmpty()) {
            lines.add(s("plugin.object.effectiverights.noowners"));
        } else {
            List<String> owners = new ArrayList<>();
            for (JsonPermissionRef group : permissions.getOwnerGroups()) owners.add(name(group.getName(), group.getId()));
            lines.add(s("plugin.object.effectiverights.owners", String.join(", ", owners)));
        }
        if (permissions.isPublicObject()) lines.add(s("plugin.object.effectiverights.public"));
        if (!permissions.isComplete()) lines.add(s("plugin.object.effectiverights.incomplete"));
        info.setText(String.join("\n", lines));
        rows.setAll(permissions.getUsers());
    }

    @Override
    public String getTitle() {
        return TITLE;
    }

    @Override
    public boolean needSave() {
        return false;
    }

    @Override
    public void dismissChanges() {
        changed.setValue(false);
    }

    @Override
    public boolean save() {
        return true;
    }

    @Override
    public BooleanProperty getValueChangedProperty() {
        return changed;
    }

    @Override
    public void showHelp(boolean show) {
    }
}
