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
package org.jevis.jeconfig.plugin.maintenance;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.maintenance.MaintenanceLogic;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.Plugin;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.table.TableColumnStateStore;
import org.jevis.jeconfig.plugin.appointment.AbstractEntryPlugin;
import org.jevis.jeconfig.plugin.appointment.AppointmentPlugin;
import org.jevis.jeconfig.plugin.appointment.EntryStore;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Module "Maintenance &amp; Known Issues" (OpenJEVis #2663): planned maintenance and known issues that users are
 * informed about by mail. "Inform" and the status "done" only mark the entry; the JENotifier service sends the mails
 * on its next cycle, because the Control Center has no SMTP credentials.
 */
public class MaintenancePlugin extends AbstractEntryPlugin {

    public static final String PLUGIN_NAME = MaintenanceEntry.PLUGIN_CLASS_NAME;
    private static final Logger logger = LogManager.getLogger(MaintenancePlugin.class);

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filteredRows = new FilteredList<>(rows);
    private final TableView<Row> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final ToolBar toolBar = new ToolBar();
    private final EntryStore.NameCache names;

    public static class Row {
        final JEVisObject object;
        final MaintenanceEntry entry;
        final MailState state;

        Row(JEVisObject object, MaintenanceEntry entry, MailState state) {
            this.object = object;
            this.entry = entry;
            this.state = state;
        }
    }

    public MaintenancePlugin(JEVisDataSource ds, String name) {
        super(ds, name, "Maintenance");
        this.names = new EntryStore.NameCache(ds);
        buildToolbar();
        buildContent();
    }

    static String typeText(MaintenanceEntry.Type type) {
        return I18n.getInstance().getString("plugin.maintenance.type." + type.name().toLowerCase());
    }

    static String statusText(MaintenanceEntry.Status status) {
        return I18n.getInstance().getString("plugin.maintenance.status." + status.name().toLowerCase());
    }

    /**
     * @return what was mailed and what is pending
     */
    static String mailText(MaintenanceEntry entry, MailState state) {
        StringBuilder text = new StringBuilder();
        MaintenanceLogic.MailKind pending = MaintenanceLogic.pendingMail(entry, state);
        if (pending != null) {
            text.append(I18n.getInstance().getString(pending == MaintenanceLogic.MailKind.DONE
                    ? "plugin.maintenance.mail.donepending" : "plugin.maintenance.mail.informpending"));
        }
        if (state != null && state.getLastMail() != null) {
            if (text.length() > 0) text.append(" - ");
            text.append(String.format(I18n.getInstance().getString("plugin.maintenance.mail.sent"),
                    EntryFormat.display(EntryFormat.parse(state.getLastMail()))));
            if (state.getLastMailKind() != null) {
                text.append(" (").append(I18n.getInstance().getString("plugin.scheduledentries.mailkind." + state.getLastMailKind().toLowerCase())).append(')');
            }
        }
        if (state != null && state.getLastError() != null) {
            if (text.length() > 0) text.append(" - ");
            text.append(I18n.getInstance().getString("plugin.scheduledentries.mailerror")).append(": ").append(state.getLastError());
        }
        return text.toString();
    }

    private void buildToolbar() {
        ToggleButton reload = AppointmentPlugin.toolbarButton(Icon.REFRESH, "plugin.scheduledentries.reload");
        ToggleButton add = AppointmentPlugin.toolbarButton(Icon.PLUS, "plugin.maintenance.new");
        ToggleButton edit = AppointmentPlugin.toolbarButton(Icon.EDIT, "plugin.maintenance.edit");
        ToggleButton delete = AppointmentPlugin.toolbarButton(Icon.DELETE, "plugin.maintenance.delete");
        Button inform = new Button(I18n.getInstance().getString("plugin.maintenance.inform"),
                JEConfig.getSVGImage(Icon.ALARM, 18, 18));
        inform.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.maintenance.inform.tip")));
        reload.setOnAction(event -> reload());
        add.setOnAction(event -> createEntry());
        edit.setOnAction(event -> editSelected());
        delete.setOnAction(event -> deleteSelected());
        inform.setOnAction(event -> informSelected());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        inform.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        toolBar.getItems().setAll(reload, new Separator(), add, edit, delete, new Separator(), inform);
    }

    private void buildContent() {
        TableColumn<Row, String> typeColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.type"));
        typeColumn.setId("type");
        typeColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(typeText(param.getValue().entry.getType())));
        typeColumn.setPrefWidth(130);

        TableColumn<Row, String> titleColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.title"));
        titleColumn.setId("title");
        titleColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().entry.getTitle()));
        titleColumn.setPrefWidth(240);

        TableColumn<Row, LocalDateTime> startColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.start"));
        startColumn.setId("start");
        startColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().entry.getStartDate()));
        startColumn.setCellFactory(param -> new TableCell<Row, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : EntryFormat.display(item));
            }
        });
        startColumn.setPrefWidth(130);

        TableColumn<Row, String> durationColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.duration"));
        durationColumn.setId("duration");
        durationColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(EntryFormat.displayDuration(param.getValue().entry.getDurationMinutes())));

        TableColumn<Row, String> statusColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.status"));
        statusColumn.setId("status");
        statusColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(statusText(param.getValue().entry.getStatus())));

        TableColumn<Row, String> impactColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.impact"));
        impactColumn.setId("impact");
        impactColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().entry.getImpact().replaceAll("\\s+", " ")));
        impactColumn.setPrefWidth(220);

        TableColumn<Row, String> recipientColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.recipients"));
        recipientColumn.setId("recipients");
        recipientColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(names.names(param.getValue().entry.getRecipients())));
        recipientColumn.setPrefWidth(180);

        TableColumn<Row, String> mailColumn = new TableColumn<>(I18n.getInstance().getString("plugin.maintenance.mail"));
        mailColumn.setId("mail");
        mailColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(mailText(param.getValue().entry, param.getValue().state)));
        mailColumn.setPrefWidth(260);

        TableColumn<Row, String> directoryColumn = new TableColumn<>(I18n.getInstance().getString("plugin.scheduledentries.directory"));
        directoryColumn.setId("directory");
        directoryColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(EntryStore.directoryLabel(EntryStore.parentOf(param.getValue().object))));
        directoryColumn.setPrefWidth(180);

        table.getColumns().addAll(typeColumn, titleColumn, startColumn, durationColumn, statusColumn, impactColumn,
                recipientColumn, mailColumn, directoryColumn);
        SortedList<Row> sortedRows = new SortedList<>(filteredRows);
        sortedRows.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedRows);
        startColumn.setSortType(TableColumn.SortType.DESCENDING);
        table.getSortOrder().add(startColumn);
        table.setPlaceholder(new Label(I18n.getInstance().getString("plugin.maintenance.empty")));
        TableColumnStateStore.bind(table, "maintenance");
        table.setRowFactory(param -> {
            TableRow<Row> row = new TableRow<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && !empty && item.entry.getStatus() == MaintenanceEntry.Status.DONE) {
                        setStyle("-fx-text-background-color: #808080;");
                    } else {
                        setStyle("");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    editSelected();
                }
            });
            return row;
        });

        statusFilter.getItems().addAll(
                I18n.getInstance().getString("plugin.maintenance.filter.active"),
                I18n.getInstance().getString("plugin.maintenance.status.done"),
                I18n.getInstance().getString("plugin.scheduledentries.filter.all"));
        statusFilter.getSelectionModel().selectFirst();
        typeFilter.getItems().addAll(
                I18n.getInstance().getString("plugin.scheduledentries.filter.all"),
                typeText(MaintenanceEntry.Type.MAINTENANCE),
                typeText(MaintenanceEntry.Type.KNOWN_ISSUE));
        typeFilter.getSelectionModel().selectFirst();
        searchField.setPromptText(I18n.getInstance().getString("plugin.scheduledentries.search"));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        typeFilter.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        updateFilter();

        HBox filterBar = new HBox(8, new Label(I18n.getInstance().getString("plugin.scheduledentries.filter")),
                statusFilter, typeFilter, searchField);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(8));
        Label hint = new Label(I18n.getInstance().getString("plugin.maintenance.hint"));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        HBox bottom = new HBox(hint);
        bottom.setPadding(new Insets(6, 8, 6, 8));
        rootPane.setTop(filterBar);
        rootPane.setCenter(table);
        rootPane.setBottom(bottom);
    }

    private void updateFilter() {
        int status = statusFilter.getSelectionModel().getSelectedIndex();
        int type = typeFilter.getSelectionModel().getSelectedIndex();
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredRows.setPredicate(row -> {
            MaintenanceEntry e = row.entry;
            boolean done = e.getStatus() == MaintenanceEntry.Status.DONE;
            if (status == 0 && done || status == 1 && !done) return false;
            if (type == 1 && e.getType() != MaintenanceEntry.Type.MAINTENANCE) return false;
            if (type == 2 && e.getType() != MaintenanceEntry.Type.KNOWN_ISSUE) return false;
            if (search.isEmpty()) return true;
            return e.getTitle().toLowerCase().contains(search)
                    || e.getDescription().toLowerCase().contains(search)
                    || e.getImpact().toLowerCase().contains(search);
        });
    }

    @Override
    protected ToolBar toolbar() {
        return toolBar;
    }

    @Override
    protected void reload() {
        Task<List<Row>> task = new Task<List<Row>>() {
            @Override
            protected List<Row> call() throws Exception {
                List<Row> loaded = new ArrayList<>();
                for (JEVisObject object : EntryStore.load(ds, MaintenanceEntry.CLASS_NAME)) {
                    MaintenanceEntry entry = EntryFormat.fromJson(EntryStore.read(object, EntryFormat.ATTRIBUTE_DATA), MaintenanceEntry.class);
                    if (entry == null) {
                        entry = new MaintenanceEntry();
                        entry.setTitle(object.getName());
                    }
                    MailState state = EntryFormat.fromJson(EntryStore.read(object, EntryFormat.ATTRIBUTE_MAIL_STATE), MailState.class);
                    loaded.add(new Row(object, entry, state));
                }
                return loaded;
            }
        };
        task.setOnSucceeded(event -> {
            names.clear();
            rows.setAll(task.getValue());
            updateFilter();
        });
        task.setOnFailed(event -> logger.error("Could not load maintenance messages", task.getException()));
        Thread thread = new Thread(task, "Load maintenance messages");
        thread.setDaemon(true);
        thread.start();
    }

    private void createEntry() {
        JEVisObject directory = EntryStore.chooseDirectory(ds, MaintenanceEntry.DIRECTORY_CLASS_NAME);
        if (directory == null) {
            return;
        }
        MaintenanceEntry entry = new MaintenanceEntry();
        entry.setCreated(EntryFormat.formatTimestamp(new LocalDateTime()));
        entry.setCreatedBy(EntryStore.currentUserName(ds));
        MaintenanceDialog dialog = new MaintenanceDialog(ds, entry, null, true);
        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }
        boolean inform = dialog.apply(entry);
        try {
            JEVisObject object = EntryStore.create(directory, MaintenanceEntry.CLASS_NAME, entry.getTitle());
            EntryStore.write(object, EntryFormat.ATTRIBUTE_DATA, EntryFormat.toJson(entry));
            if (inform) showInformQueued();
            reload();
        } catch (Exception e) {
            EntryStore.showError(I18n.getInstance().getString("plugin.scheduledentries.error.save"), e);
        }
    }

    private void editSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        MaintenanceEntry entry = EntryFormat.fromJson(EntryFormat.toJson(row.entry), MaintenanceEntry.class);
        MaintenanceDialog dialog = new MaintenanceDialog(ds, entry, row.state, false);
        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }
        boolean inform = dialog.apply(entry);
        boolean doneMail = MaintenanceLogic.pendingMail(entry, row.state) == MaintenanceLogic.MailKind.DONE
                && MaintenanceLogic.pendingMail(row.entry, row.state) != MaintenanceLogic.MailKind.DONE;
        if (save(row, entry) && (inform || doneMail)) {
            showInformQueued();
        }
    }

    private void informSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        if (row.entry.getRecipients().isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING, I18n.getInstance().getString("plugin.maintenance.norecipients"));
            alert.initOwner(JEConfig.getStage());
            TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(confirm.getDialogPane().getScene());
        confirm.setHeaderText(String.format(I18n.getInstance().getString("plugin.maintenance.inform.confirm"),
                row.entry.getTitle(), names.names(row.entry.getRecipients())));
        confirm.setContentText(I18n.getInstance().getString("plugin.maintenance.hint"));
        Optional<ButtonType> result = confirm.showAndWait();
        if (!result.isPresent() || result.get() != ButtonType.OK) {
            return;
        }
        MaintenanceEntry entry = EntryFormat.fromJson(EntryFormat.toJson(row.entry), MaintenanceEntry.class);
        MaintenanceLogic.requestInform(entry, new LocalDateTime());
        if (save(row, entry)) {
            showInformQueued();
        }
    }

    private boolean save(Row row, MaintenanceEntry entry) {
        try {
            EntryStore.write(row.object, EntryFormat.ATTRIBUTE_DATA, EntryFormat.toJson(entry));
            EntryStore.rename(row.object, entry.getTitle());
            reload();
            return true;
        } catch (Exception e) {
            EntryStore.showError(I18n.getInstance().getString("plugin.scheduledentries.error.save"), e);
            return false;
        }
    }

    private void showInformQueued() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
        alert.setHeaderText(null);
        alert.setContentText(I18n.getInstance().getString("plugin.maintenance.inform.queued"));
        alert.showAndWait();
    }

    private void deleteSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
        alert.setHeaderText(I18n.getInstance().getString("plugin.maintenance.delete"));
        alert.setContentText(row.entry.getTitle());
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                ds.deleteObject(row.object.getID(), false);
                reload();
            } catch (Exception e) {
                EntryStore.showError(I18n.getInstance().getString("plugin.scheduledentries.error.delete"), e);
            }
        }
    }

    @Override
    public String getClassName() {
        return "Maintenance Plugin";
    }

    @Override
    public Region getIcon() {
        return JEConfig.getSVGImage(Icon.TUNE, Plugin.IconSize, Plugin.IconSize, Icon.CSS_PLUGIN);
    }

    @Override
    public int getPrefTapPos() {
        return 9;
    }
}
