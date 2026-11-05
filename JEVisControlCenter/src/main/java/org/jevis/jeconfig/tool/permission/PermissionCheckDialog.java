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

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.permission.PermissionConsistencyCheck;
import org.jevis.commons.permission.RightsModel;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.RightsPlanExecutor;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.plugin.object.ObjectPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.jevis.jeconfig.tool.permission.PermissionTexts.get;

/**
 * Sys Admin tool "Berechtigungen prüfen" (OpenJEVis #1211, concept step 3): lists objects without owner group, users
 * without membership, empty groups, groups that own nothing and role members whose memberships no longer match their
 * roles. Role drift can be fixed per user or for all users.
 */
public class PermissionCheckDialog {

    private static final Logger logger = LogManager.getLogger(PermissionCheckDialog.class);
    private final JEVisDataSource ds;
    private final Long scopeId;
    private final String scopeName;
    private final Stage stage = new Stage();
    private final ObservableList<PermissionConsistencyCheck.Finding> findings = FXCollections.observableArrayList();
    private final FilteredList<PermissionConsistencyCheck.Finding> filtered = new FilteredList<>(findings, f -> true);
    private final TableView<PermissionConsistencyCheck.Finding> table = new TableView<>(filtered);
    private final Label status = new Label();
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button reapply = new Button(get("permission.check.reapply"));
    private final Button reapplyAll = new Button(get("permission.check.reapplyall"));
    private final Button refresh = new Button(get("permission.check.refresh"));
    private RightsModel model;

    /**
     * @param scope object whose subtree is checked, or null for all organisations and buildings
     */
    public PermissionCheckDialog(JEVisDataSource ds, JEVisObject scope) {
        this.ds = ds;
        this.scopeId = scope == null ? null : scope.getID();
        this.scopeName = scope == null ? get("permission.check.scope.all") : scope.getName() + " [" + scope.getID() + "]";
    }

    public void show() {
        stage.setTitle(get("permission.check.title"));
        stage.initOwner(JEConfig.getStage());

        Label scopeLabel = new Label(get("permission.check.scope", scopeName));

        ComboBox<PermissionConsistencyCheck.FindingType> filter = new ComboBox<>();
        filter.getItems().add(null);
        filter.getItems().addAll(PermissionConsistencyCheck.FindingType.values());
        filter.setConverter(new StringConverter<PermissionConsistencyCheck.FindingType>() {
            @Override
            public String toString(PermissionConsistencyCheck.FindingType type) {
                return type == null ? get("permission.check.filter.all") : PermissionTexts.findingType(type);
            }

            @Override
            public PermissionConsistencyCheck.FindingType fromString(String string) {
                return null;
            }
        });
        /* JavaFX 8 shows a null value as the prompt text, not through the converter */
        filter.setPromptText(get("permission.check.filter.all"));
        filter.setCellFactory(list -> new ListCell<PermissionConsistencyCheck.FindingType>() {
            @Override
            protected void updateItem(PermissionConsistencyCheck.FindingType type, boolean empty) {
                super.updateItem(type, empty);
                setText(empty ? null : filter.getConverter().toString(type));
            }
        });
        filter.getSelectionModel().selectFirst();
        filter.valueProperty().addListener((o, a, type) -> {
            filtered.setPredicate(f -> type == null || f.getType() == type);
            updateStatus();
        });

        TableColumn<PermissionConsistencyCheck.Finding, String> typeColumn = new TableColumn<>(get("permission.check.column.type"));
        typeColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(PermissionTexts.findingType(p.getValue().getType())));
        typeColumn.setPrefWidth(230);
        TableColumn<PermissionConsistencyCheck.Finding, Long> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().getObjectId()));
        idColumn.setPrefWidth(70);
        TableColumn<PermissionConsistencyCheck.Finding, String> objectColumn = new TableColumn<>(get("permission.check.column.object"));
        objectColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getObjectName()));
        objectColumn.setPrefWidth(180);
        TableColumn<PermissionConsistencyCheck.Finding, String> classColumn = new TableColumn<>(get("permission.check.column.class"));
        classColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getClassName()));
        classColumn.setPrefWidth(130);
        TableColumn<PermissionConsistencyCheck.Finding, String> detailColumn = new TableColumn<>(get("permission.check.column.detail"));
        detailColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(detail(p.getValue())));
        detailColumn.setPrefWidth(300);
        TableColumn<PermissionConsistencyCheck.Finding, String> pathColumn = new TableColumn<>(get("permission.check.column.path"));
        pathColumn.setCellValueFactory(p -> new ReadOnlyStringWrapper(p.getValue().getPath()));
        pathColumn.setPrefWidth(400);
        table.getColumns().addAll(typeColumn, idColumn, objectColumn, classColumn, detailColumn, pathColumn);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setPlaceholder(new Label(get("permission.check.loading")));
        table.setRowFactory(tv -> {
            TableRow<PermissionConsistencyCheck.Finding> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) goTo(row.getItem());
            });
            return row;
        });

        Button goTo = new Button(get("permission.check.goto"));
        goTo.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        goTo.setOnAction(e -> goTo(table.getSelectionModel().getSelectedItem()));

        table.getSelectionModel().getSelectedItems().addListener((javafx.collections.ListChangeListener<PermissionConsistencyCheck.Finding>) c -> updateButtons());
        reapply.setOnAction(e -> reapplyRoles(new ArrayList<>(table.getSelectionModel().getSelectedItems())));
        reapplyAll.setOnAction(e -> reapplyRoles(new ArrayList<>(findings)));

        Button copy = new Button(get("permission.check.copy"));
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(toText("\t"));
            Clipboard.getSystemClipboard().setContent(content);
        });
        Button export = new Button(get("permission.check.export"));
        export.setOnAction(e -> exportCsv());
        refresh.setOnAction(e -> load());
        Button close = new Button(get("permission.common.close"));
        close.setOnAction(e -> stage.close());

        progress.setPrefSize(18, 18);
        progress.setVisible(false);

        HBox top = new HBox(12, scopeLabel, new Region(), filter);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);
        top.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, goTo, reapply, reapplyAll, copy, export, spacer, progress, status, refresh, close);
        actions.setAlignment(Pos.CENTER_LEFT);

        BorderPane root = new BorderPane(table);
        root.setTop(top);
        root.setBottom(actions);
        BorderPane.setMargin(top, new Insets(0, 0, 8, 0));
        BorderPane.setMargin(actions, new Insets(8, 0, 0, 0));
        root.setPadding(new Insets(12));

        Scene scene = new Scene(root, 1250, 650);
        TopMenu.applyActiveTheme(scene);
        stage.setScene(scene);
        stage.show();
        load();
    }

    private String detail(PermissionConsistencyCheck.Finding finding) {
        if (finding.getType() == PermissionConsistencyCheck.FindingType.USER_WITHOUT_MEMBERSHIP && "role".equals(finding.getDetail())) {
            return get("permission.check.detail.rolenotapplied");
        }
        return finding.getDetail();
    }

    private void updateStatus() {
        if (findings.isEmpty()) {
            status.setText(model == null ? "" : get("permission.check.none"));
        } else {
            status.setText(get("permission.check.count", filtered.size(), findings.size()));
        }
    }

    private void updateButtons() {
        boolean selectedDrift = table.getSelectionModel().getSelectedItems().stream().anyMatch(f -> f != null && f.getRoleDrift() != null);
        boolean anyDrift = findings.stream().anyMatch(f -> f.getRoleDrift() != null);
        reapply.setDisable(!selectedDrift || progress.isVisible());
        reapplyAll.setDisable(!anyDrift || progress.isVisible());
        refresh.setDisable(progress.isVisible());
    }

    private void setBusy(boolean busy, String text) {
        progress.setVisible(busy);
        if (text != null) status.setText(text);
        updateButtons();
    }

    private void load() {
        findings.clear();
        setBusy(true, get("permission.check.loading"));
        Task<List<PermissionConsistencyCheck.Finding>> task = new Task<List<PermissionConsistencyCheck.Finding>>() {
            @Override
            protected List<PermissionConsistencyCheck.Finding> call() throws Exception {
                model = RightsModel.fromDataSource(ds);
                return new PermissionConsistencyCheck(model).run(scopeId);
            }
        };
        task.setOnSucceeded(e -> {
            findings.setAll(task.getValue());
            table.setPlaceholder(new Label(get("permission.check.none")));
            setBusy(false, null);
            updateStatus();
        });
        task.setOnFailed(e -> {
            logger.error("Permission check failed", task.getException());
            setBusy(false, get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "permission-check").start();
    }

    private void goTo(PermissionConsistencyCheck.Finding finding) {
        if (finding == null) return;
        try {
            JEVisObject object = ds.getObject(finding.getObjectId());
            if (object != null) JEConfig.openObjectInPlugin(ObjectPlugin.PLUGIN_NAME, object);
        } catch (Exception ex) {
            logger.error("Could not open object {}", finding.getObjectId(), ex);
        }
    }

    private void reapplyRoles(List<PermissionConsistencyCheck.Finding> selection) {
        RightsPlan plan = new RightsPlan();
        for (PermissionConsistencyCheck.Finding finding : selection) {
            if (finding != null && finding.getRoleDrift() != null) {
                plan.addAll(finding.getRoleDrift().toFixPlan(model));
            }
        }
        int count = plan.getActions().size();
        if (count == 0) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, get("permission.check.reapply.confirm", count), ButtonType.OK, ButtonType.CANCEL);
        confirm.initOwner(stage);
        confirm.setHeaderText(get("permission.check.reapply"));
        confirm.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        setBusy(true, get("permission.template.running"));
        Task<RightsPlanExecutor.Result> task = new Task<RightsPlanExecutor.Result>() {
            @Override
            protected RightsPlanExecutor.Result call() throws Exception {
                return new RightsPlanExecutor(ds).execute(plan, (done, total, action) -> updateMessage(done + " / " + total));
            }
        };
        task.messageProperty().addListener((o, a, msg) -> status.setText(msg));
        task.setOnSucceeded(e -> {
            RightsPlanExecutor.Result result = task.getValue();
            setBusy(false, null);
            Alert info = new Alert(result.getFailed() == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING,
                    get("permission.common.done", result.getDone(), result.getFailed()), ButtonType.OK);
            info.initOwner(stage);
            info.setHeaderText(get("permission.check.reapply"));
            info.showAndWait();
            load();
        });
        task.setOnFailed(e -> {
            logger.error("Re-applying roles failed", task.getException());
            setBusy(false, get("permission.common.error", String.valueOf(task.getException())));
        });
        new Thread(task, "permission-reapply").start();
    }

    private String toText(String separator) {
        StringBuilder sb = new StringBuilder();
        String[] header = {get("permission.check.column.type"), "ID", get("permission.check.column.object"),
                get("permission.check.column.class"), get("permission.check.column.detail"), get("permission.check.column.path")};
        sb.append(String.join(separator, csvRow(header, separator))).append(System.lineSeparator());
        for (PermissionConsistencyCheck.Finding f : filtered) {
            String[] row = {PermissionTexts.findingType(f.getType()), String.valueOf(f.getObjectId()), f.getObjectName(),
                    f.getClassName(), detail(f), f.getPath()};
            sb.append(String.join(separator, csvRow(row, separator))).append(System.lineSeparator());
        }
        return sb.toString();
    }

    private String[] csvRow(String[] values, String separator) {
        if (!";".equals(separator)) return values;
        String[] result = new String[values.length];
        for (int i = 0; i < values.length; i++) result[i] = PermissionTexts.csv(values[i]);
        return result;
    }

    private void exportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(get("permission.check.export"));
        chooser.setInitialFileName("permission-check.csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        try {
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
            byte[] text = toText(";").getBytes(StandardCharsets.UTF_8);
            byte[] all = new byte[bom.length + text.length];
            System.arraycopy(bom, 0, all, 0, bom.length);
            System.arraycopy(text, 0, all, bom.length, text.length);
            Files.write(file.toPath(), all);
        } catch (Exception ex) {
            logger.error("CSV export failed", ex);
            status.setText(get("permission.common.error", ex.getMessage()));
        }
    }
}
