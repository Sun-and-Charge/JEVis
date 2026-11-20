/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.plugin.action.ui.tab;

import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.controlsfx.control.CheckComboBox;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.plugin.action.data.ActionData;
import org.jevis.jeconfig.plugin.action.data.ActionPlanData;
import org.jevis.jeconfig.plugin.action.ui.CheckBoxData;
import org.jevis.jeconfig.plugin.action.ui.NumerFormating;
import org.joda.time.DateTime;

import java.time.LocalDate;
import java.util.Optional;

public class GeneralTab extends Tab {
    private static final Logger logger = LogManager.getLogger(GeneralTab.class);
    private final DatePicker f_plannedDate = new DatePicker();
    private final DatePicker f_doneDate = new DatePicker();
    //private ActionData names = new ActionData();
    private final Label l_Note = new Label();
    private final Label l_Description = new Label();
    private final Label l_ActionNr = new Label();
    private final Label l_Investment = new Label(I18n.getInstance().getString("actionform.editor.tab.general.investment"));
    private final Label l_changeKost = new Label(I18n.getInstance().getString("actionform.editor.tab.general.yearsaving"));
    private final Label l_Responsible = new Label();
    private final Label l_NoteBewertet = new Label();
    private final Label l_Attachment = new Label();
    private final Label l_Title = new Label();
    private final Label l_NoteEnergiefluss = new Label();
    private final Label l_doneDate = new Label();
    private final Label l_plannedDate = new Label();
    private final Label l_statusTags = new Label();
    private final Label l_fieldTags = new Label();
    private final Region col3Spacer = new Region();
    private final TextField f_savingYear = new TextField();
    private final TextField f_Investment = new TextField();
    private final TextField f_ActionNr = new TextField();
    private final TextField f_Title = new TextField();
    private final TextField f_Responsible = new TextField();
    private final TextArea f_Description = new TextArea();
    private final TextArea f_NoteBewertet = new TextArea();
    private final ComboBox<String> f_statusTags;
    private final CheckComboBox<String> f_fieldTags;
    //private JFXCheckComboBox f_fieldTags2;
    private final TextField f_Attachment = new TextField();
    private final TextArea f_Note = new TextArea();
    private final TextArea f_NoteEnergiefluss = new TextArea();
    private final Label l_seu = new Label(I18n.getInstance().getString("actionform.editor.tab.general.seu"));
    private final Label l_FromUser = new Label(I18n.getInstance().getString("plugin.action.fromuser"));
    private final TextField f_FromUser = new TextField();
    private final Label l_CreateDate = new Label(I18n.getInstance().getString("plugin.action.created"));  //
    private final DatePicker f_CreateDate = new DatePicker();
    private final Label l_distributor = new Label(I18n.getInstance().getString("plugin.action.distributor"));
    private final TextField f_distributor = new TextField();
    private ComboBox<String> f_sueTags = new ComboBox<>();

    {
        f_NoteBewertet.setWrapText(true);
        f_Description.setWrapText(true);
        f_Note.setWrapText(true);
        f_NoteEnergiefluss.setWrapText(true);
    }

    public GeneralTab(ActionData data) {
        super(I18n.getInstance().getString("actionform.editor.tab.general"));
        ActionPlanData actionPlan = data.getActionPlan();
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane(gridPane);
        gridPane.setVgap(10);
        gridPane.setHgap(15);

        col3Spacer.setMinWidth(25);

        /* Readable if the workaround is not needed */
        f_ActionNr.setText(actionPlan.noPrefixProperty().get() + data.noProperty().get());
        f_ActionNr.setEditable(false);
        f_ActionNr.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.isAltDown() && mouseEvent.isControlDown()) {
                TextInputDialog textInputDialog = new TextInputDialog(data.noProperty().get() + "");
                textInputDialog.setHeaderText("Set Nr");
                textInputDialog.setContentText("Set Nr");
                Optional<String> value = textInputDialog.showAndWait();
                try {
                    data.no.set(Integer.parseInt(value.get()));
                    f_ActionNr.setText(actionPlan.noPrefixProperty().get() + data.noProperty().get());
                } catch (Exception ex) {

                }
            }
        });
        /* allow editing NR */
        // StringConverter sdfs = new IntegerStringConverter();
        //Bindings.bindBidirectional(f_ActionNr.textProperty(), data.nr, sdfs);


        f_statusTags = new ComboBox<>(actionPlan.getStatustags());
        f_fieldTags = new CheckComboBox<>(actionPlan.getFieldsTags());
        f_sueTags = new ComboBox<>(actionPlan.significantEnergyUseTags());
        //f_mediaTags.setCellFactory();

        ObservableList<CheckBoxData> f_fieldTags2Data = FXCollections.observableArrayList();
        data.getActionPlan().getFieldsTags().forEach(s -> {
            f_fieldTags2Data.add(new CheckBoxData(s, false));
        });

        f_Title.widthProperty().addListener((observable, oldValue, newValue) -> {
            f_statusTags.setPrefWidth(newValue.doubleValue());
            f_fieldTags.setPrefWidth(newValue.doubleValue());
            //  f_fieldTags2.setPrefWidth(newValue.doubleValue());
            f_sueTags.setPrefWidth(newValue.doubleValue());
            //f_mediaTags.setPrefWidth(newValue.doubleValue());
            //f_Enpi.setPrefWidth(newValue.doubleValue());
        });

        f_Note.textProperty().bindBidirectional(data.noteProperty());
        f_Description.textProperty().bindBidirectional(data.descriptionProperty());
        f_Title.textProperty().bindBidirectional(data.titleProperty());
        f_NoteEnergiefluss.textProperty().bindBidirectional(data.noteEnergieflussProperty());
        f_NoteBewertet.textProperty().bindBidirectional(data.noteBewertetProperty());
        f_Responsible.textProperty().bindBidirectional(data.responsibleProperty());
        f_statusTags.getSelectionModel().select(data.statusTagsProperty().getValue());
        f_statusTags.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                //System.out.println("Status change: " + newValue + " = " + ActionPlanData.STATUS_DONE + " = " + newValue.equals(ActionPlanData.STATUS_DONE));
                data.statusTagsProperty().set(newValue);
                if (newValue.equals(ActionPlanData.STATUS_DONE)) {
                    data.doneDateProperty().set(new DateTime());
                }
            }
        });


        for (String s : data.fieldTagsProperty().getValue().split(";")) {
            f_fieldTags.getCheckModel().check(s);
        }
        f_fieldTags.getCheckModel().getCheckedItems().addListener(new ListChangeListener<String>() {
            public void onChanged(ListChangeListener.Change<? extends String> c) {
                while (c.next()) {
                    //do something with changes here
                    if (c.wasAdded() || c.wasRemoved()) {
                        data.fieldTagsProperty().set(ActionPlanData.listToString(f_fieldTags.getCheckModel().getCheckedItems()));
                    }
                }

            }
        });


        f_sueTags.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
//                System.out.println("Status change: " + newValue + " = " + ActionPlanData.STATUS_DONE + " = " + newValue.equals(ActionPlanData.STATUS_DONE));
                data.seuTagsProperty().set(newValue);
            }
        });


        if (data.doneDateProperty().getValue() != null) {
            DateTime end = data.doneDateProperty().get();
            f_doneDate.valueProperty().setValue(LocalDate.of(end.getYear(), end.getMonthOfYear(), end.getDayOfMonth()));
        }

        f_doneDate.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                data.doneDate.setValue(null);
            } else {
                data.doneDateProperty().set(new DateTime(newValue.getYear(), newValue.getMonthValue(), newValue.getDayOfMonth(), 0, 0));
            }

        });


        DateTime planDate = data.plannedDateProperty().get();
        if (planDate != null) {
            f_plannedDate.valueProperty().setValue(LocalDate.of(planDate.getYear(), planDate.getMonthOfYear(), planDate.getDayOfMonth()));
        }
        f_plannedDate.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                data.plannedDateProperty().set(null);
            } else {
                data.plannedDateProperty().set(new DateTime(newValue.getYear(), newValue.getMonthValue(), newValue.getDayOfMonth(), 0, 0));
            }
        });


        f_Investment.setTextFormatter(new TextFormatter<>(NumerFormating.getInstance().getDoubleConverter()));
        Bindings.bindBidirectional(f_Investment.textProperty(), data.npv.get().investment, NumerFormating.getInstance().getDoubleConverter());
        f_savingYear.setTextFormatter(new TextFormatter<>(NumerFormating.getInstance().getDoubleConverter()));
        Bindings.bindBidirectional(f_savingYear.textProperty(), data.npv.get().einsparung, NumerFormating.getInstance().getDoubleConverter());

        f_savingYear.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                f_savingYear.textProperty().set(NumerFormating.getInstance().getDoubleFormate().format(data.npv.get().einsparung.get()));
            }
        });

        f_Description.setWrapText(true);
        f_NoteBewertet.setWrapText(true);
        f_Note.setWrapText(true);
        f_NoteEnergiefluss.setWrapText(true);


        logger.debug("Investment: " + data.npv.get().investment.get());
        logger.debug("InvestText: " + f_Investment.textProperty().get());


        // f_savingYear.setTextFormatter(new TextFormatter(new UnitDoubleConverter()));
        TextField l_savingsUnitLabel = new TextField("€");
        l_savingsUnitLabel.setEditable(false);
        l_savingsUnitLabel.setPrefWidth(25);
        HBox savingsBox = new HBox(f_savingYear, l_savingsUnitLabel);
        HBox.setHgrow(f_savingYear, Priority.ALWAYS);
        f_savingYear.setAlignment(Pos.BASELINE_RIGHT);


        TextField l_investmentUnitLabel = new TextField("€");
        l_investmentUnitLabel.setEditable(false);
        l_investmentUnitLabel.setPrefWidth(25);
        HBox investBox = new HBox(f_Investment, l_investmentUnitLabel);
        HBox.setHgrow(f_Investment, Priority.ALWAYS);
        f_Investment.setAlignment(Pos.BASELINE_RIGHT);


        f_distributor.textProperty().bindBidirectional(data.distributorProperty());
        f_FromUser.textProperty().bindBidirectional(data.fromUserProperty());

        DateTime start = data.createDateProperty().get();
        if (start != null) {
            f_CreateDate.valueProperty().setValue(LocalDate.of(start.getYear(), start.getMonthOfYear(), start.getDayOfMonth()));
        }
        f_CreateDate.valueProperty().addListener((observable, oldValue, newValue) -> {
            logger.debug("newValue: " + newValue);
            if (newValue == null) {
                data.createDateProperty().set(null);
            } else {
                data.createDateProperty().set(new DateTime(newValue.getYear(), newValue.getMonthValue(), newValue.getDayOfMonth(), 0, 0));
            }

        });

        l_doneDate.setText(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.donedate", "Abgeschlossen am", "Done on"));
        l_plannedDate.setText(I18n.getInstance().getString("plugin.action.plandate"));
        l_Note.setText(I18n.getInstance().getString("plugin.action.note"));
        l_Description.setText(I18n.getInstance().getString("plugin.action.description"));
        l_ActionNr.setText(I18n.getInstance().getString("plugin.action.nr"));
        l_statusTags.setText("Status");
        l_fieldTags.setText("Bereich");
        l_Responsible.setText("Verantwortlichkeit");
        l_Title.setText(I18n.getInstance().getString("plugin.action.affectedprocess"));
        l_NoteBewertet.setText(I18n.getInstance().getString("plugin.action.noteBewertet"));
        l_NoteEnergiefluss.setText(I18n.getInstance().getString("plugin.action.measureDescription"));

        /* J25.5: sections, labels above the fields (docs/DESIGN-CC.md) */
        org.jevis.jeconfig.application.ui.FormLayout form = new org.jevis.jeconfig.application.ui.FormLayout();
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.form.action", "Maßnahme", "Action"))
                .add(l_ActionNr.getText(), f_ActionNr)
                .add(l_statusTags.getText(), f_statusTags)
                .addWide(l_Title.getText(), f_Title)
                .add(l_Responsible.getText(), f_Responsible)
                .add(l_FromUser.getText(), f_FromUser)
                .add(l_fieldTags.getText(), f_fieldTags)
                .add(l_seu.getText(), f_sueTags);
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.form.dates", "Termine", "Dates"))
                .add(l_CreateDate.getText(), f_CreateDate)
                .add(l_plannedDate.getText(), f_plannedDate)
                .add(l_doneDate.getText(), f_doneDate);
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.form.economics", "Wirtschaftlichkeit", "Economics"))
                .add(l_changeKost.getText(), savingsBox)
                .add(l_Investment.getText(), investBox);
        for (javafx.scene.control.TextArea area : new javafx.scene.control.TextArea[]{f_Description, f_NoteEnergiefluss, f_NoteBewertet, f_Note}) {
            area.setPrefRowCount(4);
            area.setWrapText(true);
        }
        form.section(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.action.form.description", "Beschreibung", "Description"))
                .addWide(l_Description.getText(), f_Description)
                .addWide(l_NoteEnergiefluss.getText(), f_NoteEnergiefluss)
                .addWide(l_NoteBewertet.getText(), f_NoteBewertet)
                .addWide(l_Note.getText(), f_Note);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(form);
        setContent(scrollPane);

    }

    private void add(GridPane pane, int column, int row, int colspan, int rowspan, Priority priority, Node node) {
        pane.add(node, column, row, colspan, rowspan);
        GridPane.setHgrow(node, priority);
    }
}
