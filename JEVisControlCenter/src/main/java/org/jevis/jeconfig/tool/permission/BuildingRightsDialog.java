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
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.permission.*;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.jevis.jeconfig.tool.permission.PermissionTexts.get;

/**
 * "Rechte nach Vorlage einrichten": brings existing buildings to the group and role structure of the building
 * template ({@link BuildingRightsTemplate}). Shows a preview first, optionally assigns the users of the building to
 * the template roles, then applies the changes. Nothing is removed unless the admin chooses to remove the old
 * memberships of migrated users.
 */
public class BuildingRightsDialog {

    private static final Logger logger = LogManager.getLogger(BuildingRightsDialog.class);
    private static final String MISSING_PLUGIN = "missing plugin: ";
    private final JEVisDataSource ds;
    private final JEVisObject start;
    private final Stage stage = new Stage();
    private final ObservableList<BuildingRow> buildings = FXCollections.observableArrayList(
            row -> new Observable[]{row.include, row.groupName});
    private final ObservableList<UserRow> users = FXCollections.observableArrayList(row -> new Observable[]{row.choice});
    private final ObservableList<RightsPlan.Action> preview = FXCollections.observableArrayList();
    private final CheckBox noEntryGroups = new CheckBox(get("permission.template.option.noentry"));
    private final CheckBox createDirectories = new CheckBox(get("permission.template.option.directories"));
    private final CheckBox propagate = new CheckBox(get("permission.template.option.propagate"));
    private final CheckBox serviceUser = new CheckBox(get("permission.template.option.serviceuser"));
    private final CheckBox removeOld = new CheckBox(get("permission.template.option.removeold"));
    private final Label summary = new Label();
    private final Label warnings = new Label();
    private final Label status = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Button previewButton = new Button(get("permission.template.preview.button"));
    private final Button apply = new Button(get("permission.template.apply"));
    private final BooleanProperty busy = new SimpleBooleanProperty(false);
    private final BooleanProperty previewValid = new SimpleBooleanProperty(false);
    private RightsModel model;
    private RightsPlan plan;

    /**
     * @param start a building, or an organisation (all buildings below it)
     */
    public BuildingRightsDialog(JEVisDataSource ds, JEVisObject start) {
        this.ds = ds;
        this.start = start;
    }

    public void show() {
        stage.setTitle(get("permission.template.title"));
        stage.initOwner(JEConfig.getStage());

        Label intro = new Label(get("permission.template.intro"));
        intro.setWrapText(true);

        TableView<BuildingRow> buildingTable = new TableView<>(buildings);
        buildingTable.setEditable(true);
        buildingTable.setPrefHeight(150);
        TableColumn<BuildingRow, Boolean> includeColumn = new TableColumn<>("");
        includeColumn.setCellValueFactory(p -> p.getValue().include);
        includeColumn.setCellFactory(CheckBoxTableCell.forTableColumn(includeColumn));
        includeColumn.setEditable(true);
        includeColumn.setPrefWidth(40);
        TableColumn<BuildingRow, String> buildingColumn = new TableColumn<>(get("permission.template.column.building"));
        buildingColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().path));
        buildingColumn.setPrefWidth(520);
        buildingColumn.setEditable(false);
        TableColumn<BuildingRow, String> nameColumn = new TableColumn<>(get("permission.template.groupname"));
        nameColumn.setCellValueFactory(p -> p.getValue().groupName);
        nameColumn.setCellFactory(TextFieldTableCell.forTableColumn());
        nameColumn.setEditable(true);
        nameColumn.setPrefWidth(250);
        buildingTable.getColumns().addAll(includeColumn, buildingColumn, nameColumn);

        createDirectories.setSelected(true);
        propagate.setSelected(true);
        FlowPane options = new FlowPane(16, 6, noEntryGroups, createDirectories, propagate, serviceUser);

        Label usersIntro = new Label(get("permission.template.users.intro"));
        usersIntro.setWrapText(true);
        TableView<UserRow> userTable = new TableView<>(users);
        userTable.setEditable(true);
        userTable.setPrefHeight(180);
        userTable.setPlaceholder(new Label("-"));
        TableColumn<UserRow, String> userBuilding = new TableColumn<>(get("permission.template.column.building"));
        userBuilding.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().building.groupName.get()));
        userBuilding.setPrefWidth(160);
        TableColumn<UserRow, String> userName = new TableColumn<>(get("permission.template.column.user"));
        userName.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().candidate.getUserName() + " [" + p.getValue().candidate.getUserId() + "]"));
        userName.setPrefWidth(200);
        TableColumn<UserRow, String> userRights = new TableColumn<>(get("permission.template.column.rights"));
        userRights.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.level(p.getValue().candidate.getLevel())
                + " (" + PermissionTexts.rights(p.getValue().candidate.getRights()) + ")"));
        userRights.setPrefWidth(300);
        TableColumn<UserRow, String> userCurrent = new TableColumn<>(get("permission.template.column.current"));
        userCurrent.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().candidate.getCurrentRole() == BuildingRightsTemplate.RoleChoice.SKIP
                ? "-" : PermissionTexts.role(p.getValue().candidate.getCurrentRole())));
        userCurrent.setPrefWidth(150);
        TableColumn<UserRow, BuildingRightsTemplate.RoleChoice> userChoice = new TableColumn<>(get("permission.template.column.role"));
        userChoice.setCellValueFactory(p -> p.getValue().choice);
        userChoice.setCellFactory(ComboBoxTableCell.forTableColumn(new StringConverter<BuildingRightsTemplate.RoleChoice>() {
            @Override
            public String toString(BuildingRightsTemplate.RoleChoice choice) {
                return choice == null ? "" : PermissionTexts.role(choice);
            }

            @Override
            public BuildingRightsTemplate.RoleChoice fromString(String string) {
                return null;
            }
        }, BuildingRightsTemplate.RoleChoice.values()));
        userChoice.setEditable(true);
        userChoice.setPrefWidth(190);
        userTable.getColumns().addAll(userBuilding, userName, userRights, userCurrent, userChoice);

        TableView<RightsPlan.Action> previewTable = new TableView<>(preview);
        previewTable.setPlaceholder(new Label(get("permission.template.nothing")));
        TableColumn<RightsPlan.Action, String> kindColumn = new TableColumn<>(get("permission.template.column.kind"));
        kindColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionKind(p.getValue())));
        kindColumn.setPrefWidth(110);
        TableColumn<RightsPlan.Action, String> whatColumn = new TableColumn<>(get("permission.template.column.what"));
        whatColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionWhat(p.getValue())));
        whatColumn.setPrefWidth(220);
        TableColumn<RightsPlan.Action, String> changeColumn = new TableColumn<>(get("permission.template.column.change"));
        changeColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.actionText(p.getValue())));
        changeColumn.setPrefWidth(620);
        TableColumn<RightsPlan.Action, String> resultColumn = new TableColumn<>(get("permission.template.column.result"));
        resultColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getError() != null
                ? p.getValue().getError() : (p.getValue().isDone() ? "✓" : "")));
        resultColumn.setPrefWidth(160);
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
        buildings.addListener((javafx.collections.ListChangeListener<BuildingRow>) c -> previewValid.set(false));
        users.addListener((javafx.collections.ListChangeListener<UserRow>) c -> previewValid.set(false));
        for (CheckBox box : new CheckBox[]{noEntryGroups, createDirectories, propagate, serviceUser, removeOld}) {
            box.selectedProperty().addListener((o, a, b) -> previewValid.set(false));
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(8, progress, status, spacer, previewButton, apply, close);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(8,
                intro,
                title(get("permission.template.buildings")), buildingTable, options,
                title(get("permission.template.users")), usersIntro, userTable, removeOld,
                title(get("permission.template.preview")), summary, warnings, previewTable);
        content.setPadding(new Insets(12));
        /* wrapped texts keep their full height, the preview table takes the remaining space */
        for (Label label : new Label[]{intro, usersIntro, summary, warnings}) {
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        BorderPane root = new BorderPane(content);
        root.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(0, 12, 12, 12));

        Scene scene = new Scene(root, 1150, 900);
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

    private String displayName(long id, RightsModel.Node node) {
        try {
            JEVisObject object = ds.getObject(id);
            if (object != null) {
                String local = object.getLocalName(I18n.getInstance().getLocale().getLanguage());
                if (local != null && !local.trim().isEmpty()) return local.trim();
            }
        } catch (Exception e) {
            logger.debug("No local name for {}", id, e);
        }
        return node.name;
    }

    private void load() {
        busy.set(true);
        status.setText(get("permission.check.loading"));
        Task<List<BuildingRow>> task = new Task<List<BuildingRow>>() {
            @Override
            protected List<BuildingRow> call() throws Exception {
                model = RightsModel.fromDataSource(ds);
                List<BuildingRow> rows = new ArrayList<>();
                List<RightsModel.Node> found = new ArrayList<>();
                RightsModel.Node startNode = model.get(start.getID());
                if (startNode != null && RightsModel.CLASS_BUILDING.equals(startNode.className)) {
                    found.add(startNode);
                } else {
                    for (RightsModel.Node node : model.getDescendants(start.getID())) {
                        if (RightsModel.CLASS_BUILDING.equals(node.className)) found.add(node);
                    }
                }
                for (RightsModel.Node node : found) {
                    BuildingRow row = new BuildingRow(node.id, model.getPath(node.id), displayName(node.id, node));
                    row.candidates.addAll(new BuildingRightsTemplate(model, node.id, row.groupName.get()).findUserCandidates());
                    rows.add(row);
                }
                return rows;
            }
        };
        task.setOnSucceeded(e -> {
            buildings.setAll(task.getValue());
            List<UserRow> userRows = new ArrayList<>();
            for (BuildingRow row : buildings) {
                for (BuildingRightsTemplate.UserCandidate candidate : row.candidates) {
                    userRows.add(new UserRow(row, candidate));
                }
            }
            users.setAll(userRows);
            busy.set(false);
            status.setText("");
            computePreview();
        });
        task.setOnFailed(e -> {
            logger.error("Loading rights failed", task.getException());
            busy.set(false);
            status.setText(get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "building-rights-load").start();
    }

    private BuildingRightsTemplate.Options options(BuildingRow building) {
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.withNoEntryPointGroups = noEntryGroups.isSelected();
        options.createMissingDirectories = createDirectories.isSelected();
        options.propagateOwnersToSubtree = propagate.isSelected();
        options.createServiceUser = serviceUser.isSelected();
        options.removeOldMemberships = removeOld.isSelected();
        for (UserRow row : users) {
            if (row.building == building && row.choice.get() != null) {
                options.userChoices.put(row.candidate.getUserId(), row.choice.get());
            }
        }
        return options;
    }

    private void computePreview() {
        if (model == null) return;
        busy.set(true);
        List<Object[]> inputs = new ArrayList<>();
        for (BuildingRow row : buildings) {
            if (row.include.get()) inputs.add(new Object[]{row, options(row), row.groupName.get()});
        }
        List<String> warningTexts = new ArrayList<>();
        Task<RightsPlan> task = new Task<RightsPlan>() {
            @Override
            protected RightsPlan call() {
                RightsPlan combined = new RightsPlan();
                /* plugin objects are global, a missing one is reported once and not per building */
                Set<String> missingPlugins = new LinkedHashSet<>();
                for (Object[] input : inputs) {
                    BuildingRow row = (BuildingRow) input[0];
                    RightsPlan single = new BuildingRightsTemplate(model, row.id, (String) input[2]).plan((BuildingRightsTemplate.Options) input[1]);
                    for (String warning : single.getWarnings()) {
                        if (warning.startsWith(MISSING_PLUGIN)) {
                            missingPlugins.add(warning.substring(MISSING_PLUGIN.length()));
                        } else {
                            warningTexts.add(input[2] + ": " + PermissionTexts.warning(warning));
                        }
                    }
                    combined.addAll(single);
                }
                if (!missingPlugins.isEmpty()) {
                    warningTexts.add(0, PermissionTexts.warning(MISSING_PLUGIN + String.join(", ", missingPlugins)));
                }
                return combined;
            }
        };
        task.setOnSucceeded(e -> {
            plan = task.getValue();
            preview.setAll(plan.getActions());
            summary.setText(plan.isEmpty() ? get("permission.template.nothing") : get("permission.template.summary",
                    plan.count(RightsPlan.ActionType.CREATE_OBJECT, null),
                    plan.count(RightsPlan.ActionType.ADD_RELATIONSHIP, null),
                    plan.count(RightsPlan.ActionType.REMOVE_RELATIONSHIP, null)));
            warnings.setText(String.join("\n", warningTexts));
            busy.set(false);
            previewValid.set(!plan.isEmpty());
        });
        task.setOnFailed(e -> {
            logger.error("Preview failed", task.getException());
            busy.set(false);
            status.setText(get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "building-rights-preview").start();
    }

    private void applyPlan() {
        if (plan == null || plan.isEmpty()) return;
        RightsPlan toApply = plan;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, get("permission.template.apply.confirm", toApply.getActions().size()), ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText(get("permission.template.title"));
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
            logger.error("Applying building rights failed", task.getException());
            finishApply(null, toApply);
        });
        new Thread(task, "building-rights-apply").start();
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
        info.setHeaderText(get("permission.template.title"));
        info.showAndWait();
        load();
    }

    private static class BuildingRow {
        final long id;
        final String path;
        final BooleanProperty include = new SimpleBooleanProperty(true);
        final StringProperty groupName = new SimpleStringProperty();
        final List<BuildingRightsTemplate.UserCandidate> candidates = new ArrayList<>();

        BuildingRow(long id, String path, String groupName) {
            this.id = id;
            this.path = path;
            this.groupName.set(groupName);
        }
    }

    private static class UserRow {
        final BuildingRow building;
        final BuildingRightsTemplate.UserCandidate candidate;
        final ObjectProperty<BuildingRightsTemplate.RoleChoice> choice = new SimpleObjectProperty<>();

        UserRow(BuildingRow building, BuildingRightsTemplate.UserCandidate candidate) {
            this.building = building;
            this.candidate = candidate;
            this.choice.set(candidate.getSuggestion());
        }
    }
}
