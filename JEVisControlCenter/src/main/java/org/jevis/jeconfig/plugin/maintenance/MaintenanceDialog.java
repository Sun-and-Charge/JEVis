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

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.maintenance.MaintenanceEntry;
import org.jevis.commons.maintenance.MaintenanceLogic;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.plugin.appointment.DateTimeInput;
import org.jevis.jeconfig.plugin.appointment.ObjectListEditor;
import org.joda.time.LocalDateTime;

import java.time.LocalTime;

/**
 * Edit dialog of a maintenance message (#2663).
 */
public class MaintenanceDialog extends Dialog<ButtonType> {

    private final ComboBox<MaintenanceEntry.Type> typeBox = new ComboBox<>();
    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final DateTimeInput startInput;
    private final Spinner<Integer> hoursSpinner = new Spinner<>(0, 999, 0);
    private final Spinner<Integer> minutesSpinner = new Spinner<>(0, 59, 0, 5);
    private final TextArea impactArea = new TextArea();
    private final ComboBox<MaintenanceEntry.Status> statusBox = new ComboBox<>();
    private final ObjectListEditor recipientEditor;
    private final CheckBox informBox = new CheckBox(I18n.getInstance().getString("plugin.maintenance.informaftersave"));

    public MaintenanceDialog(JEVisDataSource ds, MaintenanceEntry entry, MailState state, boolean isNew) {
        initOwner(JEConfig.getStage());
        TopMenu.applyActiveTheme(getDialogPane().getScene());
        setResizable(true);
        setTitle(I18n.getInstance().getString(isNew ? "plugin.maintenance.new" : "plugin.maintenance.edit"));
        setHeaderText(null);

        typeBox.getItems().addAll(MaintenanceEntry.Type.values());
        typeBox.setConverter(typeConverter());
        typeBox.getSelectionModel().select(entry.getType());
        titleField.setText(entry.getTitle());
        titleField.setPrefWidth(420);
        descriptionArea.setText(entry.getDescription());
        descriptionArea.setPrefRowCount(4);
        descriptionArea.setWrapText(true);
        startInput = new DateTimeInput(entry.getStartDate(), LocalTime.of(18, 0));
        int duration = entry.getDurationMinutes() != null ? entry.getDurationMinutes() : 0;
        hoursSpinner.getValueFactory().setValue(duration / 60);
        minutesSpinner.getValueFactory().setValue(duration % 60);
        hoursSpinner.setEditable(true);
        minutesSpinner.setEditable(true);
        hoursSpinner.setPrefWidth(80);
        minutesSpinner.setPrefWidth(80);
        HBox durationBox = new HBox(6, hoursSpinner, new Label("h"), minutesSpinner, new Label("min"));
        durationBox.setAlignment(Pos.CENTER_LEFT);
        impactArea.setText(entry.getImpact());
        impactArea.setPrefRowCount(3);
        impactArea.setWrapText(true);
        statusBox.getItems().addAll(MaintenanceEntry.Status.values());
        statusBox.setConverter(new StringConverter<MaintenanceEntry.Status>() {
            @Override
            public String toString(MaintenanceEntry.Status object) {
                return object != null ? MaintenancePlugin.statusText(object) : "";
            }

            @Override
            public MaintenanceEntry.Status fromString(String string) {
                return null;
            }
        });
        statusBox.getSelectionModel().select(entry.getStatus());
        recipientEditor = new ObjectListEditor(ds, entry.getRecipients());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        int row = 0;
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.type")), typeBox);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.title")), titleField);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.description")), descriptionArea);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.start")), startInput);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.duration")), durationBox);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.impact")), impactArea);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.status")), statusBox);
        grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.maintenance.recipients")), recipientEditor);
        if (state != null && (state.getLastMail() != null || state.getLastError() != null)) {
            grid.addRow(row++, new Label(I18n.getInstance().getString("plugin.scheduledentries.lastmail")), new Label(MaintenancePlugin.mailText(entry, state)));
        }
        grid.add(informBox, 1, row++);
        Label hint = new Label(I18n.getInstance().getString("plugin.maintenance.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(560);
        grid.add(hint, 0, row, 2, 1);
        GridPane.setHgrow(titleField, Priority.ALWAYS);

        getDialogPane().setContent(grid);
        ButtonType ok = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);
        getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
                event.consume();
                Alert alert = new Alert(Alert.AlertType.WARNING, I18n.getInstance().getString("plugin.scheduledentries.error.title"));
                alert.initOwner(getDialogPane().getScene().getWindow());
                alert.setHeaderText(null);
                alert.showAndWait();
            }
        });
    }

    private static StringConverter<MaintenanceEntry.Type> typeConverter() {
        return new StringConverter<MaintenanceEntry.Type>() {
            @Override
            public String toString(MaintenanceEntry.Type object) {
                return object != null ? MaintenancePlugin.typeText(object) : "";
            }

            @Override
            public MaintenanceEntry.Type fromString(String string) {
                return null;
            }
        };
    }

    /**
     * Writes the dialog values into the entry.
     *
     * @return true if "inform after saving" is checked
     */
    public boolean apply(MaintenanceEntry entry) {
        LocalDateTime now = new LocalDateTime();
        entry.setType(typeBox.getValue());
        entry.setTitle(titleField.getText().trim());
        entry.setDescription(descriptionArea.getText());
        entry.setStartDate(startInput.getValue());
        Integer hours = hoursSpinner.getValue();
        Integer minutes = minutesSpinner.getValue();
        int duration = (hours != null ? hours : 0) * 60 + (minutes != null ? minutes : 0);
        entry.setDurationMinutes(duration > 0 ? duration : null);
        entry.setImpact(impactArea.getText());
        entry.setRecipients(recipientEditor.getIds());
        MaintenanceLogic.changeStatus(entry, statusBox.getValue(), now);
        if (informBox.isSelected()) {
            MaintenanceLogic.requestInform(entry, now);
            return true;
        }
        return false;
    }
}
