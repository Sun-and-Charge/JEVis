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
package org.jevis.jeconfig.tool.permission;

import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.permission.RightsModel;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.RightsPlanExecutor;
import org.jevis.commons.permission.SiteSharing;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.dialog.Response;

import java.util.*;

import static org.jevis.jeconfig.tool.permission.PermissionTexts.get;

/**
 * "Zugriff einrichten …": the sharing wizard for small sites (OpenJEVis #1211, concept step 2). The admin picks
 * users and gives each a preset (Nur ansehen, Bearbeiten, Verwalten); {@link SiteSharing} plans one group per preset
 * for the location, the owner and entry point relationships and the memberships. Preview and apply work like the
 * building rights dialog.
 */
public class SiteSharingDialog {

    private static final Logger logger = LogManager.getLogger(SiteSharingDialog.class);
    private static final List<SiteSharing.Preset> CHOICES = SiteSharing.Preset.GROUP_PRESETS;
    private final JEVisDataSource ds;
    private final JEVisObject location;
    private final Stage stage = new Stage();
    private final ObservableList<UserRow> users = FXCollections.observableArrayList(row -> new Observable[]{row.preset});
    private final ObservableList<RightsPlan.Action> preview = FXCollections.observableArrayList();
    private final CheckBox includeSubtree = new CheckBox(get("permission.share.option.subtree"));
    private final Label groupsInfo = new Label();
    private final Label summary = new Label();
    private final Label warnings = new Label();
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Button addUsers = new Button(get("permission.share.adduser"));
    private final Button previewButton = new Button(get("permission.template.preview.button"));
    private final Button apply = new Button(get("permission.template.apply"));
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty previewValid = new SimpleBooleanProperty(false);
    private RightsModel model;
    private SiteSharing sharing;
    private String locationName;
    private Map<Long, SiteSharing.Preset> current = new LinkedHashMap<>();
    private RightsPlan plan;

    public SiteSharingDialog(JEVisDataSource ds, JEVisObject location) {
        this.ds = ds;
        this.location = location;
    }

    public void show() {
        stage.setTitle(get("permission.share.title"));
        stage.initOwner(JEConfig.getStage());

        Label intro = new Label(get("permission.share.intro"));
        intro.setWrapText(true);
        Label presets = new Label(get("permission.share.presets"));
        presets.setWrapText(true);
        groupsInfo.setWrapText(true);
        includeSubtree.setSelected(true);

        TableView<UserRow> userTable = new TableView<>(users);
        userTable.setEditable(true);
        userTable.setPrefHeight(220);
        userTable.setPlaceholder(new Label(get("permission.share.nousers")));
        TableColumn<UserRow, String> userName = new TableColumn<>(get("permission.template.column.user"));
        userName.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().name));
        userName.setPrefWidth(420);
        userName.setEditable(false);
        TableColumn<UserRow, String> userCurrent = new TableColumn<>(get("permission.share.column.current"));
        userCurrent.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.preset(current.get(p.getValue().userId))));
        userCurrent.setPrefWidth(150);
        userCurrent.setEditable(false);
        TableColumn<UserRow, SiteSharing.Preset> userPreset = new TableColumn<>(get("permission.share.column.preset"));
        userPreset.setCellValueFactory(p -> p.getValue().preset);
        userPreset.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<SiteSharing.Preset>() {
            @Override
            public String toString(SiteSharing.Preset preset) {
                return preset == null ? "" : PermissionTexts.preset(preset);
            }

            @Override
            public SiteSharing.Preset fromString(String string) {
                return null;
            }
        }, CHOICES.toArray(new SiteSharing.Preset[0])));
        userPreset.setEditable(true);
        userPreset.setPrefWidth(180);
        TableColumn<UserRow, UserRow> userRemove = new TableColumn<>("");
        userRemove.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue()));
        userRemove.setCellFactory(column -> new TableCell<UserRow, UserRow>() {
            private final Button remove = new Button(get("permission.share.remove"));

            @Override
            protected void updateItem(UserRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setGraphic(null);
                } else {
                    remove.setOnAction(e -> users.remove(row));
                    remove.disableProperty().bind(busy);
                    setGraphic(remove);
                }
            }
        });
        userRemove.setPrefWidth(120);
        userRemove.setSortable(false);
        userTable.getColumns().addAll(userName, userCurrent, userPreset, userRemove);

        addUsers.setOnAction(e -> chooseUsers());
        addUsers.disableProperty().bind(busy);
        Label removeHint = new Label(get("permission.share.removehint"));
        removeHint.setWrapText(true);

        TableView<RightsPlan.Action> previewTable = new TableView<>(preview);
        previewTable.setPlaceholder(new Label(get("permission.share.nothing")));
        TableColumn<RightsPlan.Action, String> kindColumn = new TableColumn<>(get("permission.template.column.kind"));
        kindColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionKind(p.getValue())));
        kindColumn.setPrefWidth(110);
        TableColumn<RightsPlan.Action, String> whatColumn = new TableColumn<>(get("permission.template.column.what"));
        whatColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionWhat(p.getValue())));
        whatColumn.setPrefWidth(220);
        TableColumn<RightsPlan.Action, String> changeColumn = new TableColumn<>(get("permission.template.column.change"));
        changeColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionText(p.getValue())));
        changeColumn.setPrefWidth(560);
        TableColumn<RightsPlan.Action, String> resultColumn = new TableColumn<>(get("permission.template.column.result"));
        resultColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getError() != null
                ? p.getValue().getError() : (p.getValue().isDone() ? "✓" : "")));
        resultColumn.setPrefWidth(140);
        previewTable.getColumns().addAll(kindColumn, whatColumn, changeColumn, resultColumn);
        VBox.setVgrow(previewTable, Priority.ALWAYS);

        warnings.setWrapText(true);
        warnings.setStyle("-fx-text-fill: #b36b00;");

        previewButton.setOnAction(e -> computePreview());
        previewButton.disableProperty().bind(busy);
        apply.disableProperty().bind(busy.or(previewValid.not()));
        apply.setDefaultButton(true);
        apply.setOnAction(e -> applyPlan());
        Button close = new Button(get("permission.common.close"));
        close.setOnAction(e -> stage.close());
        progress.setPrefWidth(200);
        progress.visibleProperty().bind(busy);

        /* any change invalidates the preview */
        users.addListener((ListChangeListener<UserRow>) c -> previewValid.set(false));
        includeSubtree.selectedProperty().addListener((o, a, b) -> previewValid.set(false));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, progress, status, spacer, previewButton, apply, close);
        bottom.setAlignment(Pos.CENTER_LEFT);
        HBox userButtons = new HBox(8, addUsers);

        VBox content = new VBox(8,
                intro, presets, groupsInfo, includeSubtree,
                title(get("permission.share.users")), userButtons, userTable, removeHint,
                title(get("permission.template.preview")), summary, warnings, previewTable);
        content.setPadding(new Insets(12));
        /* wrapped texts keep their full height, the preview table takes the remaining space */
        for (Label label : new Label[]{intro, presets, groupsInfo, removeHint, summary, warnings}) {
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        BorderPane root = new BorderPane(content);
        root.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(0, 12, 12, 12));

        Scene scene = new Scene(root, 1050, 860);
        TopMenu.applyActiveTheme(scene);
        stage.setScene(scene);
        stage.show();
        load();
    }

    private Label title(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }

    private String localName(JEVisObject object, String fallback) {
        try {
            String local = object.getLocalName(I18n.getInstance().getLocale().getLanguage());
            if (local != null && !local.trim().isEmpty()) return local.trim();
        } catch (Exception e) {
            logger.debug("No local name for {}", object, e);
        }
        return fallback;
    }

    private String userLabel(long userId) {
        RightsModel.Node node = model == null ? null : model.get(userId);
        if (node == null) return String.valueOf(userId);
        return model.getPath(userId) + " [" + userId + "]";
    }

    private void load() {
        busy.set(true);
        status.setText(get("permission.check.loading"));
        Task<SiteSharing> task = new Task<SiteSharing>() {
            @Override
            protected SiteSharing call() throws Exception {
                model = RightsModel.fromDataSource(ds, false);
                locationName = localName(location, location.getName());
                return new SiteSharing(model, location.getID(), locationName);
            }
        };
        task.setOnSucceeded(e -> {
            sharing = task.getValue();
            current = sharing.currentPresets();
            /* keep the admin's choices for users already in the list, add the current members */
            Map<Long, SiteSharing.Preset> keep = new LinkedHashMap<>();
            for (UserRow row : users) keep.put(row.userId, row.preset.get());
            List<UserRow> rows = new ArrayList<>();
            Set<Long> listed = new HashSet<>();
            for (Map.Entry<Long, SiteSharing.Preset> entry : current.entrySet()) {
                SiteSharing.Preset preset = keep.containsKey(entry.getKey()) ? keep.get(entry.getKey()) : entry.getValue();
                rows.add(new UserRow(entry.getKey(), userLabel(entry.getKey()), preset));
                listed.add(entry.getKey());
            }
            for (Map.Entry<Long, SiteSharing.Preset> entry : keep.entrySet()) {
                if (listed.add(entry.getKey())) rows.add(new UserRow(entry.getKey(), userLabel(entry.getKey()), entry.getValue()));
            }
            users.setAll(rows);
            updateGroupsInfo();
            busy.set(false);
            status.setText("");
            computePreview();
        });
        task.setOnFailed(e -> {
            logger.error("Loading rights failed", task.getException());
            busy.set(false);
            status.setText(get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "site-sharing-load").start();
    }

    private void updateGroupsInfo() {
        StringBuilder text = new StringBuilder(get("permission.share.location", model.getPath(location.getID())));
        for (SiteSharing.Preset preset : CHOICES) {
            RightsModel.Node group = sharing.getGroup(preset);
            text.append("\n").append(PermissionTexts.preset(preset)).append(": ");
            if (group != null) {
                text.append(group.name).append(" [").append(group.id).append("]");
            } else {
                text.append(get("permission.share.group.new", SiteSharing.groupName(locationName, preset)));
            }
        }
        groupsInfo.setText(text.toString());
    }

    private void chooseUsers() {
        try {
            JEVisClass userClass = ds.getJEVisClass(RightsModel.CLASS_USER);
            List<JEVisClass> filter = new ArrayList<>();
            if (userClass != null) filter.add(userClass);
            TreeSelectionDialog dialog = new TreeSelectionDialog(ds, filter, SelectionMode.MULTIPLE);
            dialog.showAndWait();
            if (dialog.getResponse() != Response.OK) return;
            Set<Long> listed = new HashSet<>();
            for (UserRow row : users) listed.add(row.userId);
            for (JEVisObject object : dialog.getTreeView().getSelectedObjects()) {
                if (object == null || !RightsModel.CLASS_USER.equals(object.getJEVisClassName())) continue;
                if (listed.add(object.getID())) {
                    users.add(new UserRow(object.getID(), userLabel(object.getID()), SiteSharing.Preset.VIEW));
                }
            }
        } catch (Exception e) {
            logger.error("Could not choose users", e);
            status.setText(get("permission.common.error", String.valueOf(e)));
        }
    }

    /**
     * @return the choices of the table; current members removed from the table lose their access (NONE)
     */
    private Map<Long, SiteSharing.Preset> assignments() {
        Map<Long, SiteSharing.Preset> result = new LinkedHashMap<>();
        for (UserRow row : users) {
            result.put(row.userId, row.preset.get() == null ? SiteSharing.Preset.VIEW : row.preset.get());
        }
        for (Long userId : current.keySet()) {
            result.putIfAbsent(userId, SiteSharing.Preset.NONE);
        }
        return result;
    }

    private void computePreview() {
        if (sharing == null) return;
        busy.set(true);
        Map<Long, SiteSharing.Preset> input = assignments();
        boolean subtree = includeSubtree.isSelected();
        SiteSharing planner = sharing;
        Task<RightsPlan> task = new Task<RightsPlan>() {
            @Override
            protected RightsPlan call() {
                return planner.plan(input, subtree);
            }
        };
        task.setOnSucceeded(e -> {
            plan = task.getValue();
            preview.setAll(plan.getActions());
            summary.setText(plan.isEmpty() ? get("permission.share.nothing") : get("permission.template.summary",
                    plan.count(RightsPlan.ActionType.CREATE_OBJECT, null),
                    plan.count(RightsPlan.ActionType.ADD_RELATIONSHIP, null),
                    plan.count(RightsPlan.ActionType.REMOVE_RELATIONSHIP, null)));
            List<String> warningTexts = new ArrayList<>();
            for (String warning : plan.getWarnings()) warningTexts.add(PermissionTexts.warning(warning));
            warnings.setText(String.join("\n", warningTexts));
            busy.set(false);
            previewValid.set(!plan.isEmpty());
        });
        task.setOnFailed(e -> {
            logger.error("Preview failed", task.getException());
            busy.set(false);
            status.setText(get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "site-sharing-preview").start();
    }

    private void applyPlan() {
        if (plan == null || plan.isEmpty()) return;
        RightsPlan toApply = plan;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, get("permission.template.apply.confirm", toApply.getActions().size()), ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText(get("permission.share.title"));
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        busy.set(true);
        status.setText(get("permission.template.running"));
        Task<RightsPlanExecutor.Result> task = new Task<RightsPlanExecutor.Result>() {
            @Override
            protected RightsPlanExecutor.Result call() throws Exception {
                return new RightsPlanExecutor(ds).setObjectDelayMs(200).execute(toApply, (done, total, action) -> {
                    updateProgress(done, total);
                    updateMessage(done + " / " + total);
                });
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        status.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> finishApply(task.getValue(), toApply));
        task.setOnFailed(e -> {
            logger.error("Applying site sharing failed", task.getException());
            finishApply(null, toApply);
        });
        new Thread(task, "site-sharing-apply").start();
    }

    private void finishApply(RightsPlanExecutor.Result result, RightsPlan applied) {
        progress.progressProperty().unbind();
        status.textProperty().unbind();
        busy.set(false);
        previewValid.set(false);
        preview.setAll(applied.getActions());
        String text = result == null ? get("permission.common.error", "-")
                : get("permission.common.done", result.getDone(), result.getFailed());
        status.setText(text);
        Alert info = new Alert(result != null && result.getFailed() == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                text + "\n" + get("permission.template.after"), ButtonType.OK);
        info.initOwner(stage);
        info.setHeaderText(get("permission.share.title"));
        info.showAndWait();
        load();
    }

    private static class UserRow {
        final long userId;
        final String name;
        final ObjectProperty<SiteSharing.Preset> preset = new SimpleObjectProperty<>();

        UserRow(long userId, String name, SiteSharing.Preset preset) {
            this.userId = userId;
            this.name = name;
            this.preset.set(preset);
        }
    }
}
