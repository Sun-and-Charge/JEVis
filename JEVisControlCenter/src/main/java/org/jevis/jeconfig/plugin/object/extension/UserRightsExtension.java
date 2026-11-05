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
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisUser;
import org.jevis.commons.ws.json.*;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.dialog.Response;
import org.jevis.jeconfig.plugin.object.ObjectEditorExtension;

import java.util.ArrayList;
import java.util.List;

import org.jevis.jeconfig.plugin.object.permission.EffectiveRightsText;

import static org.jevis.jeconfig.plugin.object.permission.EffectiveRightsText.*;

/**
 * Object editor tab "Rechte des Benutzers" on User objects (OpenJEVis #1211): the group memberships and roles of the
 * user and, for an object chosen in the tree, his effective rights on it, as computed by the web service. Read-only.
 * Shown to Sys Admins and to the user himself.
 */
public class UserRightsExtension implements ObjectEditorExtension {

    private static final Logger logger = LogManager.getLogger(UserRightsExtension.class);
    private static final String TITLE = s("plugin.object.userrights.title");

    private final JEVisObject obj;
    private final BorderPane view = new BorderPane();
    private final BooleanProperty changed = new SimpleBooleanProperty(false);
    private final ObservableList<JsonGroupRights> groupRows = FXCollections.observableArrayList();
    private final ObservableList<RoleRow> roleRows = FXCollections.observableArrayList();
    private final Label info = new Label();
    private final Label objectLabel = new Label();
    private final VBox objectRights = new VBox(4);
    private JEVisObject selectedObject;
    private boolean built = false;

    public UserRightsExtension(JEVisObject obj) {
        this.obj = obj;
    }

    @Override
    public boolean isForObject(JEVisObject obj) {
        try {
            if (obj == null || obj.getID() == null || !"User".equals(obj.getJEVisClassName())) return false;
            JEVisDataSource ds = obj.getDataSource();
            if (!(ds instanceof JEVisDataSourceWS)) return false;
            JEVisUser user = ds.getCurrentUser();
            return user != null && (user.isSysAdmin() || user.getUserID() == obj.getID());
        } catch (JEVisException ex) {
            logger.debug("Could not check the rights of the current user", ex);
            return false;
        }
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
        Button reload = new Button(s("plugin.object.effectiverights.reload"));
        reload.setOnAction(event -> reload());
        HBox top = new HBox(8, reload, info);
        top.setAlignment(Pos.CENTER_LEFT);
        info.setWrapText(true);

        TableView<JsonGroupRights> groups = new TableView<>(groupRows);
        groups.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        groups.setPlaceholder(new Label(s("plugin.object.userrights.nogroups")));
        groups.setPrefHeight(220);
        List<TableColumn<JsonGroupRights, ?>> groupColumns = new ArrayList<>();
        groupColumns.add(EffectiveRightsText.<JsonGroupRights>textColumn(s("plugin.object.userrights.group"), g -> name(g.getGroupName(), g.getGroupId()), 180));
        for (int right = READ; right <= EXECUTE; right++) {
            final int current = right;
            groupColumns.add(EffectiveRightsText.<JsonGroupRights>tickColumn(rightName(current), g -> has(g, current), null));
        }
        groupColumns.add(EffectiveRightsText.<JsonGroupRights>textColumn(s("plugin.object.userrights.roles"), g -> roleNames(g.getRoles()), 180));
        groups.getColumns().addAll(groupColumns);

        TableView<RoleRow> roles = new TableView<>(roleRows);
        roles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        roles.setPlaceholder(new Label(s("plugin.object.userrights.noroles")));
        roles.setPrefHeight(160);
        List<TableColumn<RoleRow, ?>> roleColumns = new ArrayList<>();
        roleColumns.add(EffectiveRightsText.<RoleRow>textColumn(s("plugin.object.userrights.role"), r -> r.role, 160));
        roleColumns.add(EffectiveRightsText.<RoleRow>textColumn(s("plugin.object.userrights.group"), r -> name(r.group.getGroupName(), r.group.getGroupId()), 160));
        for (int right = READ; right <= EXECUTE; right++) {
            final int current = right;
            roleColumns.add(EffectiveRightsText.<RoleRow>tickColumn(rightName(current), r -> has(r.group, current), null));
        }
        roles.getColumns().addAll(roleColumns);

        Button choose = new Button(s("plugin.object.userrights.selectobject"));
        choose.setOnAction(event -> chooseObject());
        objectLabel.setText(s("plugin.object.userrights.noobject"));
        HBox objectBar = new HBox(8, choose, objectLabel);
        objectBar.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8,
                top,
                title(s("plugin.object.userrights.groups")), groups,
                title(s("plugin.object.userrights.roles")), roles,
                title(s("plugin.object.userrights.object")), objectBar, objectRights);
        box.setPadding(new Insets(8));
        view.setCenter(box);
    }

    private static Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private static String roleNames(List<JsonPermissionRef> roles) {
        if (roles == null || roles.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (JsonPermissionRef role : roles) names.add(name(role.getName(), role.getId()));
        return String.join(", ", names);
    }

    private void chooseObject() {
        try {
            TreeSelectionDialog dialog = new TreeSelectionDialog(obj.getDataSource(), new ArrayList<>(), SelectionMode.SINGLE,
                    new ArrayList<>(), false);
            dialog.showAndWait();
            if (dialog.getResponse() != Response.OK || dialog.getTreeView().getSelectedObjects().isEmpty()) return;
            selectedObject = dialog.getTreeView().getSelectedObjects().get(0);
            reload();
        } catch (Exception ex) {
            logger.warn("Could not select an object", ex);
        }
    }

    private void reload() {
        info.setText(s("plugin.object.effectiverights.loading"));
        final Long objectId = selectedObject != null ? selectedObject.getID() : null;
        Task<JsonUserPermissions> task = new Task<JsonUserPermissions>() {
            @Override
            protected JsonUserPermissions call() throws Exception {
                return ((JEVisDataSourceWS) obj.getDataSource()).getUserPermissions(obj.getID(), objectId);
            }
        };
        task.setOnSucceeded(event -> show(task.getValue()));
        task.setOnFailed(event -> {
            Throwable ex = task.getException();
            logger.warn("Could not load the rights of user {}", obj.getID(), ex);
            info.setText(s("plugin.object.effectiverights.error", ex != null ? ex.getMessage() : ""));
        });
        Thread thread = new Thread(task, "user-rights-" + obj.getID());
        thread.setDaemon(true);
        thread.start();
    }

    private void show(JsonUserPermissions permissions) {
        objectRights.getChildren().clear();
        if (permissions == null) {
            groupRows.clear();
            roleRows.clear();
            info.setText(s("plugin.object.effectiverights.unsupported"));
            return;
        }
        info.setText(userLabel(permissions.getUserName(), permissions.getUserId(), permissions.isSysAdmin(), permissions.isEnabled())
                + (permissions.isSysAdmin() ? "\n" + s("plugin.object.effectiverights.sysadmin") : ""));
        groupRows.setAll(permissions.getGroups());
        List<RoleRow> rows = new ArrayList<>();
        for (JsonRoleRights role : permissions.getRoles()) {
            String roleName = name(role.getRoleName(), role.getRoleId());
            for (JsonGroupRights group : role.getGroups()) rows.add(new RoleRow(roleName, group));
        }
        roleRows.setAll(rows);

        JsonEffectiveRights rights = permissions.getObjectRights();
        if (rights == null) {
            objectLabel.setText(s("plugin.object.userrights.noobject"));
            return;
        }
        objectLabel.setText(s("plugin.object.userrights.objectrights",
                name(permissions.getObjectName(), permissions.getObjectId() != null ? permissions.getObjectId() : 0L)));
        for (int right = READ; right <= EXECUTE; right++) {
            Label line = new Label((has(rights, right) ? TICK + " " : "✗ ") + rightExplanation(rights, right).replace(":\n", ": ").replace("\n", "; "));
            line.setWrapText(true);
            objectRights.getChildren().add(line);
        }
    }

    /**
     * One group right granted by one role.
     */
    private static final class RoleRow {
        private final String role;
        private final JsonGroupRights group;

        private RoleRow(String role, JsonGroupRights group) {
            this.role = role;
            this.group = group;
        }
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
