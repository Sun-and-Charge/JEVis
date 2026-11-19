/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.dialog;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.AnalysesComboBox;
import org.jevis.jeconfig.application.Chart.ChartTools;
import org.jevis.jeconfig.application.navigation.PluginSidebar;
import org.jevis.jeconfig.application.ui.Confirm;
import org.jevis.jeconfig.application.ui.Notify;
import org.jevis.jeconfig.application.ui.SucDialog;
import org.jevis.jeconfig.plugin.charts.ChartPlugin;
import org.jevis.jeconfig.plugin.charts.DataSettings;
import org.jevis.jeconfig.plugin.charts.ToolBarView;

import java.util.ArrayList;
import java.util.List;

/**
 * Saves the current analysis (J25.5 stage 5, docs/DESIGN-CC.md): folder and name, one primary button "Speichern".
 * A name that already exists in the folder is shown below the field; saving then asks „Analyse „…“ ersetzen?“.
 * Missing rights and failures are reported as notifications; the dialog stays open so the user can correct the input.
 */
public class SaveAnalysisDialog extends SucDialog<ButtonType> {
    private static final Logger logger = LogManager.getLogger(SaveAnalysisDialog.class);
    private final JEVisDataSource ds;
    private final ChartPlugin chartPlugin;
    private final DataSettings dataSettings;
    private final ToolBarView toolBarView;
    private final AnalysesComboBox analysesComboBox;
    private final ComboBox<JEVisObject> folders = new ComboBox<>();
    private final TextField name = new TextField();
    private final Label exists = new Label();
    private Response response = Response.CANCEL;

    public SaveAnalysisDialog(JEVisDataSource ds, DataSettings dataSettings, ChartPlugin chartPlugin, ToolBarView toolBarView) {
        this(ds, dataSettings, chartPlugin, toolBarView, false);
    }

    /**
     * @param newAnalysis true if the analysis was just created; the name field then starts empty instead of
     *                    showing the name of the previously opened analysis, its directory is kept
     */
    public SaveAnalysisDialog(JEVisDataSource ds, DataSettings dataSettings, ChartPlugin chartPlugin, ToolBarView toolBarView, boolean newAnalysis) {
        super(text("plugin.graph.save.title", "Analyse speichern", "Save analysis"),
                text("plugin.graph.save.description", "Datenreihen und Diagramme werden gespeichert, der Zeitraum nicht.",
                        "Rows and charts are saved, the time frame is not."), Size.M);
        this.ds = ds;
        this.chartPlugin = chartPlugin;
        this.dataSettings = dataSettings;
        this.toolBarView = toolBarView;
        this.analysesComboBox = toolBarView.getAnalysesComboBox();

        List<JEVisObject> directories = new ArrayList<>();
        try {
            directories.addAll(ds.getObjects(ds.getJEVisClass("Analyses Directory"), false));
        } catch (Exception e) {
            logger.error("Could not load the analyses directories", e);
        }
        folders.setItems(FXCollections.observableArrayList(directories));
        ObjectRelations objectRelations = new ObjectRelations(ds);
        Callback<ListView<JEVisObject>, ListCell<JEVisObject>> cellFactory = view -> new ListCell<JEVisObject>() {
            @Override
            protected void updateItem(JEVisObject obj, boolean empty) {
                super.updateItem(obj, empty);
                if (empty || obj == null || obj.getName() == null) {
                    setText("");
                } else {
                    String prefix = "";
                    if (ChartTools.isMultiSite(ds)) prefix += objectRelations.getObjectPath(obj);
                    if (ChartTools.isMultiDir(ds, obj)) prefix += objectRelations.getRelativePath(obj);
                    setText(prefix + obj.getName());
                }
            }
        };
        folders.setCellFactory(cellFactory);
        folders.setButtonCell(cellFactory.call(null));
        folders.setMaxWidth(Double.MAX_VALUE);

        folders.getSelectionModel().selectFirst();
        JEVisObject current = dataSettings.getCurrentAnalysis();
        if (current != null) {
            try {
                if (current.getParents() != null && !current.getParents().isEmpty()) {
                    folders.getSelectionModel().select(current.getParents().get(0));
                }
            } catch (Exception e) {
                logger.error("Couldn't select current Analysis Directory", e);
            }
            if (!newAnalysis && current.getName() != null) name.setText(current.getName());
        }

        name.setPromptText(text("plugin.graph.save.name.prompt", "z. B. Strom Hauptzähler 2026", "e.g. Electricity main meter 2026"));
        exists.getStyleClass().add("suc-field-hint");
        exists.setWrapText(true);

        Label folderLabel = new Label(text("plugin.graph.save.folder", "Ordner", "Folder"));
        folderLabel.setLabelFor(folders);
        Label nameLabel = new Label(text("plugin.graph.save.name", "Name", "Name"));
        nameLabel.setLabelFor(name);
        Region gap = new Region();
        gap.setMinHeight(6);
        VBox form = new VBox(6, folderLabel, folders, gap, nameLabel, name, exists);
        form.getStyleClass().add("suc-form");
        getDialogPane().setContent(form);

        ButtonType saveType = new ButtonType(text("dialog.ui.save", "Speichern", "Save"), ButtonBar.ButtonData.OK_DONE);
        Button save = addPrimary(saveType);
        addCancel();

        name.textProperty().addListener((observable, oldValue, newValue) -> updateHint(save));
        folders.valueProperty().addListener((observable, oldValue, newValue) -> updateHint(save));
        updateHint(save);

        /* save on click; on a missing right, a declined replacement or a failure the dialog stays open */
        save.addEventFilter(ActionEvent.ACTION, event -> {
            if (!save()) event.consume();
        });
        setOnShown(event -> Platform.runLater(() -> {
            name.requestFocus();
            name.selectAll();
        }));
    }

    private static String text(String key, String german, String english) {
        return PluginSidebar.text(key, german, english);
    }

    private String enteredName() {
        return name.getText() == null ? "" : name.getText().trim();
    }

    /** the analysis with the entered name in the chosen folder, or null */
    private JEVisObject existing() {
        JEVisObject folder = folders.getValue();
        if (folder == null || enteredName().isEmpty()) return null;
        try {
            for (JEVisObject child : folder.getChildren()) {
                if (enteredName().equals(child.getName())) return child;
            }
        } catch (Exception e) {
            logger.error("Error in current analysis directory", e);
        }
        return null;
    }

    private void updateHint(Button save) {
        save.setDisable(folders.getValue() == null || enteredName().isEmpty());
        /* saving the open analysis under its own name is a normal save, no replacement of another analysis */
        boolean replaces = existing() != null && !isCurrent(existing());
        exists.setText(replaces ? text("plugin.graph.save.exists",
                "In diesem Ordner gibt es schon eine Analyse mit diesem Namen. Speichern ersetzt sie.",
                "This folder already has an analysis with this name. Saving replaces it.") : "");
        exists.setVisible(replaces);
        exists.setManaged(replaces);
    }

    private boolean isCurrent(JEVisObject analysis) {
        return dataSettings.getCurrentAnalysis() != null && analysis.getID().equals(dataSettings.getCurrentAnalysis().getID());
    }

    /** @return true when saved and the dialog may close */
    private boolean save() {
        JEVisObject folder = folders.getValue();
        String analysisName = enteredName();
        JEVisObject target = existing();
        try {
            if (target == null) {
                if (!ds.getCurrentUser().canCreate(folder.getID())) {
                    Notify.error(text("plugin.graph.save.nocreate",
                            "Sie dürfen in diesem Ordner keine Analysen anlegen. Anderen Ordner wählen.",
                            "You may not create analyses in this folder. Choose another folder."));
                    return false;
                }
                target = folder.buildObject(analysisName, ds.getJEVisClass("Analysis"));
                target.commit();
            } else {
                if (!ds.getCurrentUser().canWrite(target.getID())) {
                    Notify.error(text("plugin.graph.save.nowrite",
                            "Sie dürfen diese Analyse nicht ändern. Unter einem neuen Namen speichern.",
                            "You may not change this analysis. Save it under a new name."));
                    return false;
                }
                if (!isCurrent(target) && !Confirm.ask(
                        String.format(text("plugin.graph.save.replace.question", "Analyse „%s“ ersetzen?", "Replace analysis “%s”?"), analysisName),
                        text("plugin.graph.save.replace.consequence", "Die gespeicherte Analyse wird durch die aktuelle ersetzt.",
                                "The saved analysis is replaced by the current one."),
                        text("plugin.graph.save.replace.action", "Ersetzen", "Replace"), true)) {
                    return false;
                }
            }
        } catch (Exception e) {
            logger.error("Could not create the analysis {}", analysisName, e);
            Notify.error(text("plugin.graph.save.failed", "Die Analyse konnte nicht gespeichert werden. Verbindung prüfen und erneut versuchen.",
                    "The analysis could not be saved. Check the connection and try again."));
            return false;
        }

        if (!chartPlugin.getAnalysisHandler().saveDataModel(target, chartPlugin.getDataModel())) {
            Notify.error(text("plugin.graph.save.failed", "Die Analyse konnte nicht gespeichert werden. Verbindung prüfen und erneut versuchen.",
                    "The analysis could not be saved. Check the connection and try again."));
            return false;
        }
        toolBarView.setChanged(false);
        analysesComboBox.updateListAnalyses();
        dataSettings.setCurrentAnalysis(target);
        toolBarView.updateLayout();
        response = Response.OK;
        Notify.success(String.format(text("plugin.graph.save.done", "Analyse „%s“ gespeichert", "Analysis “%s” saved"), analysisName));
        return true;
    }

    public Response getResponse() {
        return response;
    }
}
