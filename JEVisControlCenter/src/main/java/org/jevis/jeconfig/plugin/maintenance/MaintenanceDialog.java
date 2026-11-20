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
import org.jevis.jeconfig.application.ui.FormLayout;
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
public class MaintenanceDialog extends org.jevis.jeconfig.application.ui.SucDialog<ButtonType> {

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

    private final FormLayout form = new FormLayout();

    public MaintenanceDialog(JEVisDataSource ds, MaintenanceEntry entry, MailState state, boolean isNew) {
        super(I18n.getInstance().getString(isNew ? "plugin.maintenance.new" : "plugin.maintenance.edit"), null, Size.M);
        TopMenu.applyActiveTheme(getDialogPane().getScene());
        setResizable(true);

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

        /* J25.5: sections, labels above the fields; shown in the side panel (docs/DESIGN-CC.md) */
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.maintenance.form.entry", "Meldung", "Notice"))
                .add(I18n.getInstance().getString("plugin.maintenance.type"), typeBox)
                .add(I18n.getInstance().getString("plugin.maintenance.status"), statusBox)
                .addWide(I18n.getInstance().getString("plugin.maintenance.title"), titleField)
                .addWide(I18n.getInstance().getString("plugin.maintenance.description"), descriptionArea);
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.maintenance.form.time", "Zeitraum und Auswirkungen", "Time and impact"))
                .add(I18n.getInstance().getString("plugin.maintenance.start"), startInput)
                .add(I18n.getInstance().getString("plugin.maintenance.duration"), durationBox)
                .addWide(I18n.getInstance().getString("plugin.maintenance.impact"), impactArea);
        FormLayout.Section people = form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.maintenance.form.notification", "Benachrichtigung", "Notification"))
                .addWide(I18n.getInstance().getString("plugin.maintenance.recipients"), recipientEditor);
        if (state != null && (state.getLastMail() != null || state.getLastError() != null)) {
            people.addWide(I18n.getInstance().getString("plugin.scheduledentries.lastmail"), new Label(MaintenancePlugin.mailText(entry, state)));
        }
        people.addWide("", informBox);
        Label hint = new Label(I18n.getInstance().getString("plugin.maintenance.hint"));
        hint.setWrapText(true);
        hint.getStyleClass().add("suc-form-empty");
        form.getChildren().add(hint);

        getDialogPane().setContent(form);
        ButtonType ok = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(I18n.getInstance().getString("plugin.scheduledentries.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(ok, cancel);
        getDialogPane().lookupButton(ok).addEventFilter(ActionEvent.ACTION, event -> {
            if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
                event.consume();
                org.jevis.jeconfig.application.ui.Notify.error(I18n.getInstance().getString("plugin.scheduledentries.error.title"));
            }
        });
    }

    /** the form for the side panel */
    public FormLayout getForm() {
        return form;
    }

    /** the first missing required value, null when the form can be saved */
    public String validate() {
        if (titleField.getText() == null || titleField.getText().trim().isEmpty()) {
            return I18n.getInstance().getString("plugin.scheduledentries.error.title");
        }
        return null;
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
