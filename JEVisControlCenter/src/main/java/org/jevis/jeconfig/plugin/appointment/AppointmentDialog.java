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

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.appointment.Appointment;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.dialog.Response;
import org.joda.time.LocalDateTime;

import java.time.LocalTime;
import java.util.ArrayList;

/**
 * Edit dialog of an appointment (#1855).
 */
public class AppointmentDialog extends Dialog<ButtonType> {

    private static final Logger logger = LogManager.getLogger(AppointmentDialog.class);

    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final DateTimeInput dueInput;
    private final ComboBox<Appointment.Recurrence> recurrenceBox = new ComboBox<>();
    private final Spinner<Integer> reminderSpinner = new Spinner<>(0, 3650, 7);
    private final ComboBox<Appointment.Status> statusBox = new ComboBox<>();
    private final ObjectListEditor recipientEditor;
    private final Label linkedLabel = new Label();
    private Long linkedObject;

    public AppointmentDialog(JEVisDataSource ds, Appointment appointment, MailState state, boolean isNew) {
        initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(getDialogPane().getScene());
        setResizable(true);
        setTitle(I18n.getInstance().getString(isNew ? "plugin.appointments.new" : "plugin.appointments.edit"));
        setHeaderText(null);

        titleField.setText(appointment.getTitle());
        descriptionArea.setText(appointment.getDescription());
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);
        dueInput = new DateTimeInput(appointment.getDueDate(), LocalTime.of(9, 0));

        recurrenceBox.getItems().addAll(Appointment.Recurrence.values());
        recurrenceBox.setConverter(converter(AppointmentPlugin::recurrenceText));
        recurrenceBox.getSelectionModel().select(appointment.getRecurrence());

        reminderSpinner.setEditable(true);
        reminderSpinner.getValueFactory().setValue(isNew ? 7 : appointment.getReminderDays());
        reminderSpinner.setPrefWidth(100);

        statusBox.getItems().addAll(Appointment.Status.values());
        statusBox.setConverter(converter(AppointmentPlugin::statusText));
        statusBox.getSelectionModel().select(appointment.getStatus());

        recipientEditor = new ObjectListEditor(ds, appointment.getRecipients());

        linkedObject = appointment.getLinkedObject();
        updateLinkedLabel(ds);
        Button chooseLinked = new Button(I18n.getInstance().getString("plugin.appointments.linked.choose"));
        Button clearLinked = new Button(I18n.getInstance().getString("plugin.appointments.linked.clear"));
        chooseLinked.setOnAction(event -> chooseLinkedObject(ds));
        clearLinked.setOnAction(event -> {
            linkedObject = null;
            updateLinkedLabel(ds);
        });
        HBox linkedBox = new HBox(6, linkedLabel, chooseLinked, clearLinked);
        linkedLabel.setMaxWidth(260);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        int row = 0;
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.title")), titleField);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.description")), descriptionArea);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.due")), dueInput);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.recurrence")), recurrenceBox);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.reminderdays")), reminderSpinner);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.responsible")), recipientEditor);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.linked")), linkedBox);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.appointments.status")), statusBox);
        if (state != null && (state.getLastMail() != null || state.getLastError() != null)) {
            grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.scheduledentries.lastmail")), new Label(AppointmentPlugin.mailStateText(state)));
        }
        Label hint = new Label(I18n.getInstance().getString("plugin.appointments.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, row, 2, 1);
        GridPane.setHgrow(titleField, Priority.ALWAYS);
        titleField.setPrefWidth(420);

        getDialogPane().setContent(grid);
        ButtonType ok = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);
        getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            String error = null;
            if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
                error = I18n.getInstance().getString("plugin.scheduledentries.error.title");
            } else if (dueInput.getValue() == null) {
                error = I18n.getInstance().getString("plugin.appointments.error.due");
            }
            if (error != null) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING, error);
                alert.initOwner(getDialogPane().getScene().getWindow());
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
    }

    static <T> StringConverter<T> converter(java.util.function.Function<T, String> text) {
        return new StringConverter<T>() {
            @Override
            public String toString(T object) {
                return object != null ? text.apply(object) : "";
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    private void chooseLinkedObject(JEVisDataSource ds) {
        try {
            TreeSelectionDialog dialog = new TreeSelectionDialog(ds, new ArrayList<>(), SelectionMode.SINGLE);
            dialog.showAndWait();
            if (dialog.getResponse() == Response.OK && !dialog.getTreeView().getSelectedObjects().isEmpty()) {
                JEVisObject object = dialog.getTreeView().getSelectedObjects().get(0);
                if (object != null) {
                    linkedObject = object.getID();
                    updateLinkedLabel(ds);
                }
            }
        } catch (Exception e) {
            logger.error("Could not select linked object", e);
        }
    }

    private void updateLinkedLabel(JEVisDataSource ds) {
        if (linkedObject == null) {
            linkedLabel.setText(I18n.getInstance().getString("plugin.appointments.linked.none"));
        } else {
            linkedLabel.setText(new EntryStore.NameCache(ds).name(linkedObject));
        }
    }

    /**
     * Writes the dialog values into the appointment. Setting an open appointment to done completes it; a recurring
     * appointment then moves to its next due date.
     *
     * @return the new due date if a recurring appointment was completed, else null
     */
    public LocalDateTime apply(Appointment appointment) {
        Appointment.Status oldStatus = appointment.getStatus();
        LocalDateTime oldDue = appointment.getDueDate();
        appointment.setTitle(titleField.getText().trim());
        appointment.setDescription(descriptionArea.getText());
        LocalDateTime due = dueInput.getValue();
        appointment.setDueDate(due);
        if (oldDue == null || due == null || oldDue.getDayOfMonth() != due.getDayOfMonth()
                || appointment.getRecurrence() != recurrenceBox.getValue()) {
            appointment.setAnchorDay(due != null ? due.getDayOfMonth() : null);
        }
        appointment.setRecurrence(recurrenceBox.getValue());
        Integer reminder = reminderSpinner.getValue();
        appointment.setReminderDays(reminder != null ? reminder : 0);
        appointment.setRecipients(recipientEditor.getIds());
        appointment.setLinkedObject(linkedObject);
        Appointment.Status status = statusBox.getValue();
        if (status == Appointment.Status.DONE && oldStatus != Appointment.Status.DONE) {
            return org.jevis.commons.appointment.AppointmentLogic.complete(appointment, new LocalDateTime());
        }
        appointment.setStatus(status);
        return null;
    }

    static String display(LocalDateTime time) {
        return EntryFormat.display(time);
    }
}
