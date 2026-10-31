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
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.AppointmentLogic;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.GlobalToolBar;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.Plugin;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.table.TableColumnStateStore;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Module "Appointments" (OpenJEVis #1855): appointments with due date, recurrence and reminder mails. The mails are
 * sent by the JENotifier service.
 */
public class AppointmentPlugin extends AbstractEntryPlugin {

    public static final String PLUGIN_NAME = Appointment.PLUGIN_CLASS_NAME;
    private static final Logger logger = LogManager.getLogger(AppointmentPlugin.class);
    private static final double ICON_SIZE = 20;

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final FilteredList<Row> filteredRows = new FilteredList<>(rows);
    private final TableView<Row> table = new TableView<>();
    private final TextField searchField = new TextField();
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ToolBar toolBar = new ToolBar();
    private final EntryStore.NameCache names;

    public static class Row {
        final JEVisObject object;
        final Appointment appointment;
        final MailState state;

        Row(JEVisObject object, Appointment appointment, MailState state) {
            this.object = object;
            this.appointment = appointment;
            this.state = state;
        }
    }

    public AppointmentPlugin(JEVisDataSource ds, String name) {
        super(ds, name, "Appointments");
        this.names = new EntryStore.NameCache(ds);
        buildToolbar();
        buildContent();
    }

    static String recurrenceText(Appointment.Recurrence recurrence) {
        return I18n.getInstance().getString("plugin.appointments.recurrence." + recurrence.name().toLowerCase());
    }

    static String statusText(Appointment.Status status) {
        return I18n.getInstance().getString("plugin.appointments.status." + status.name().toLowerCase());
    }

    static String mailStateText(MailState state) {
        if (state == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        if (state.getLastMail() != null) {
            text.append(EntryFormat.display(EntryFormat.parse(state.getLastMail())));
            if (state.getLastMailKind() != null) {
                text.append(" (").append(I18n.getInstance().getString("plugin.scheduledentries.mailkind." + state.getLastMailKind().toLowerCase())).append(')');
            }
        }
        if (state.getLastError() != null) {
            if (text.length() > 0) text.append(" - ");
            text.append(I18n.getInstance().getString("plugin.scheduledentries.mailerror")).append(": ").append(state.getLastError());
        }
        return text.toString();
    }

    private String rowStatus(Row row) {
        LocalDate today = new LocalDate();
        if (AppointmentLogic.isOverdue(row.appointment, today)) {
            return I18n.getInstance().getString("plugin.appointments.status.overdue");
        } else if (AppointmentLogic.isDueSoon(row.appointment, today)) {
            return I18n.getInstance().getString("plugin.appointments.status.duesoon");
        }
        return statusText(row.appointment.getStatus());
    }

    private void buildToolbar() {
        ToggleButton reload = toolbarButton(Icon.REFRESH, "plugin.scheduledentries.reload");
        ToggleButton add = toolbarButton(Icon.PLUS, "plugin.appointments.new");
        ToggleButton edit = toolbarButton(Icon.EDIT, "plugin.appointments.edit");
        ToggleButton done = toolbarButton(Icon.CHECK, "plugin.appointments.markdone");
        ToggleButton delete = toolbarButton(Icon.DELETE, "plugin.appointments.delete");
        reload.setOnAction(event -> reload());
        add.setOnAction(event -> createAppointment());
        edit.setOnAction(event -> editSelected());
        done.setOnAction(event -> markSelectedDone());
        delete.setOnAction(event -> deleteSelected());
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        done.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        delete.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        toolBar.getItems().setAll(reload, new Separator(), add, edit, done, delete);
    }

    public static ToggleButton toolbarButton(String icon, String tooltipKey) {
        ToggleButton button = new ToggleButton("", JEConfig.getSVGImage(icon, ICON_SIZE, ICON_SIZE));
        button.setTooltip(new Tooltip(I18n.getInstance().getString(tooltipKey)));
        button.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) button.setSelected(false);
        });
        GlobalToolBar.changeBackgroundOnHoverUsingBinding(button);
        return button;
    }

    private void buildContent() {
        TableColumn<Row, String> titleColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.title"));
        titleColumn.setId("title");
        titleColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(param.getValue().appointment.getTitle()));
        titleColumn.setPrefWidth(240);

        TableColumn<Row, LocalDateTime> dueColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.due"));
        dueColumn.setId("due");
        dueColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().appointment.getDueDate()));
        dueColumn.setCellFactory(param -> new TableCell<Row, LocalDateTime>() {
            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : EntryFormat.display(item));
            }
        });
        dueColumn.setPrefWidth(130);

        TableColumn<Row, String> recurrenceColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.recurrence"));
        recurrenceColumn.setId("recurrence");
        recurrenceColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(recurrenceText(param.getValue().appointment.getRecurrence())));

        TableColumn<Row, Integer> reminderColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.reminderdays"));
        reminderColumn.setId("reminder");
        reminderColumn.setCellValueFactory(param -> new ReadOnlyObjectWrapper<>(param.getValue().appointment.getReminderDays()));

        TableColumn<Row, String> statusColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.status"));
        statusColumn.setId("status");
        statusColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(rowStatus(param.getValue())));

        TableColumn<Row, String> responsibleColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.responsible"));
        responsibleColumn.setId("responsible");
        responsibleColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(names.names(param.getValue().appointment.getRecipients())));
        responsibleColumn.setPrefWidth(200);

        TableColumn<Row, String> linkedColumn = new TableColumn<>(I18n.getInstance().getString("plugin.appointments.linked"));
        linkedColumn.setId("linked");
        linkedColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(names.name(param.getValue().appointment.getLinkedObject())));
        linkedColumn.setPrefWidth(160);

        TableColumn<Row, String> mailColumn = new TableColumn<>(I18n.getInstance().getString("plugin.scheduledentries.lastmail"));
        mailColumn.setId("lastmail");
        mailColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(mailStateText(param.getValue().state)));
        mailColumn.setPrefWidth(200);

        TableColumn<Row, String> directoryColumn = new TableColumn<>(I18n.getInstance().getString("plugin.scheduledentries.directory"));
        directoryColumn.setId("directory");
        directoryColumn.setCellValueFactory(param -> new ReadOnlyStringWrapper(EntryStore.directoryLabel(EntryStore.parentOf(param.getValue().object))));
        directoryColumn.setPrefWidth(180);

        table.getColumns().addAll(titleColumn, dueColumn, recurrenceColumn, reminderColumn, statusColumn,
                responsibleColumn, linkedColumn, mailColumn, directoryColumn);
        SortedList<Row> sortedRows = new SortedList<>(filteredRows);
        sortedRows.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedRows);
        dueColumn.setSortType(TableColumn.SortType.ASCENDING);
        table.getSortOrder().add(dueColumn);
        table.setPlaceholder(new Label(I18n.getInstance().getString("plugin.appointments.empty")));
        TableColumnStateStore.bind(table, "appointments");

        table.setRowFactory(param -> {
            TableRow<Row> row = new TableRow<Row>() {
                @Override
                protected void updateItem(Row item, boolean empty) {
                    super.updateItem(item, empty);
                    if (item != null && !empty && AppointmentLogic.isOverdue(item.appointment, new LocalDate())) {
                        setStyle("-fx-text-background-color: #c62828;");
                    } else if (item != null && !empty && item.appointment.getStatus() == Appointment.Status.DONE) {
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
                I18n.getInstance().getString("plugin.appointments.filter.open"),
                I18n.getInstance().getString("plugin.appointments.filter.duesoon"),
                I18n.getInstance().getString("plugin.appointments.filter.overdue"),
                I18n.getInstance().getString("plugin.appointments.filter.done"),
                I18n.getInstance().getString("plugin.scheduledentries.filter.all"));
        statusFilter.getSelectionModel().selectFirst();
        searchField.setPromptText(I18n.getInstance().getString("plugin.scheduledentries.search"));
        searchField.textProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> updateFilter());
        updateFilter();

        HBox filterBar = new HBox(8, new Label(I18n.getInstance().getString("plugin.scheduledentries.filter")), statusFilter, searchField);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label(I18n.getInstance().getString("plugin.appointments.hint"));
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        VBox top = new VBox(6, filterBar);
        top.setPadding(new Insets(8));
        rootPane.setTop(top);
        rootPane.setCenter(table);
        HBox bottom = new HBox(hint);
        bottom.setPadding(new Insets(6, 8, 6, 8));
        rootPane.setBottom(bottom);
    }

    private void updateFilter() {
        int status = statusFilter.getSelectionModel().getSelectedIndex();
        String search = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        LocalDate today = new LocalDate();
        filteredRows.setPredicate(row -> {
            Appointment a = row.appointment;
            boolean statusMatch;
            switch (status) {
                case 0:
                    statusMatch = a.getStatus() == Appointment.Status.OPEN;
                    break;
                case 1:
                    statusMatch = AppointmentLogic.isDueSoon(a, today);
                    break;
                case 2:
                    statusMatch = AppointmentLogic.isOverdue(a, today);
                    break;
                case 3:
                    statusMatch = a.getStatus() == Appointment.Status.DONE;
                    break;
                default:
                    statusMatch = true;
            }
            if (!statusMatch) return false;
            if (search.isEmpty()) return true;
            return a.getTitle().toLowerCase().contains(search)
                    || a.getDescription().toLowerCase().contains(search)
                    || names.names(a.getRecipients()).toLowerCase().contains(search)
                    || names.name(a.getLinkedObject()).toLowerCase().contains(search);
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
                for (JEVisObject object : EntryStore.load(ds, Appointment.CLASS_NAME)) {
                    Appointment appointment = EntryFormat.fromJson(EntryStore.read(object, EntryFormat.ATTRIBUTE_DATA), Appointment.class);
                    if (appointment == null) {
                        appointment = new Appointment();
                        appointment.setTitle(object.getName());
                    }
                    MailState state = EntryFormat.fromJson(EntryStore.read(object, EntryFormat.ATTRIBUTE_MAIL_STATE), MailState.class);
                    loaded.add(new Row(object, appointment, state));
                }
                return loaded;
            }
        };
        task.setOnSucceeded(event -> {
            names.clear();
            rows.setAll(task.getValue());
            updateFilter();
        });
        task.setOnFailed(event -> logger.error("Could not load appointments", task.getException()));
        Thread thread = new Thread(task, "Load appointments");
        thread.setDaemon(true);
        thread.start();
    }

    private void createAppointment() {
        JEVisObject directory = EntryStore.chooseDirectory(ds, Appointment.DIRECTORY_CLASS_NAME);
        if (directory == null) {
            return;
        }
        Appointment appointment = new Appointment();
        appointment.setCreated(EntryFormat.formatTimestamp(new LocalDateTime()));
        appointment.setCreatedBy(EntryStore.currentUserName(ds));
        AppointmentDialog dialog = new AppointmentDialog(ds, appointment, null, true);
        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }
        LocalDateTime next = dialog.apply(appointment);
        try {
            JEVisObject object = EntryStore.create(directory, Appointment.CLASS_NAME, appointment.getTitle());
            EntryStore.write(object, EntryFormat.ATTRIBUTE_DATA, EntryFormat.toJson(appointment));
            showNextDue(next);
            reload();
        } catch (Exception e) {
            EntryStore.showError(I18n.getInstance().getString("plugin.scheduledentries.error.save"), e);
        }
    }

    private void editSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        Appointment appointment = EntryFormat.fromJson(EntryFormat.toJson(row.appointment), Appointment.class);
        AppointmentDialog dialog = new AppointmentDialog(ds, appointment, row.state, false);
        Optional<ButtonType> result = dialog.showAndWait();
        if (!result.isPresent() || result.get().getButtonData() != ButtonBar.ButtonData.OK_DONE) {
            return;
        }
        LocalDateTime next = dialog.apply(appointment);
        save(row, appointment, next);
    }

    private void markSelectedDone() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null || row.appointment.getStatus() == Appointment.Status.DONE) return;
        Appointment appointment = EntryFormat.fromJson(EntryFormat.toJson(row.appointment), Appointment.class);
        LocalDateTime next = AppointmentLogic.complete(appointment, new LocalDateTime());
        save(row, appointment, next);
    }

    private void save(Row row, Appointment appointment, LocalDateTime next) {
        try {
            EntryStore.write(row.object, EntryFormat.ATTRIBUTE_DATA, EntryFormat.toJson(appointment));
            EntryStore.rename(row.object, appointment.getTitle());
            showNextDue(next);
            reload();
        } catch (Exception e) {
            EntryStore.showError(I18n.getInstance().getString("plugin.scheduledentries.error.save"), e);
        }
    }

    private void showNextDue(LocalDateTime next) {
        if (next == null) return;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
        alert.setHeaderText(null);
        alert.setContentText(String.format(I18n.getInstance().getString("plugin.appointments.nextdue"), EntryFormat.display(next)));
        alert.showAndWait();
    }

    private void deleteSelected() {
        Row row = table.getSelectionModel().getSelectedItem();
        if (row == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(alert.getDialogPane().getScene());
        alert.setHeaderText(I18n.getInstance().getString("plugin.appointments.delete"));
        alert.setContentText(row.appointment.getTitle());
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
        return "Appointment Plugin";
    }

    @Override
    public Region getIcon() {
        return JEConfig.getSVGImage(Icon.ALARM, Plugin.IconSize, Plugin.IconSize, Icon.CSS_PLUGIN);
    }

    @Override
    public int getPrefTapPos() {
        return 8;
    }
}
