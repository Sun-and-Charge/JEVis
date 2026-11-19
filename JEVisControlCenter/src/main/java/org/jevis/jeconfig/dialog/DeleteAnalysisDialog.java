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
package org.jevis.jeconfig.dialog;

import org.jevis.jeconfig.application.navigation.PluginSidebar;
import org.jevis.jeconfig.application.ui.Confirm;
import org.jevis.jeconfig.application.ui.Notify;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.AnalysesComboBox;
import org.jevis.jeconfig.application.Chart.data.AnalysisHandler;
import org.jevis.jeconfig.plugin.charts.DataSettings;

public class DeleteAnalysisDialog {
    private static final Logger logger = LogManager.getLogger(DeleteAnalysisDialog.class);
    private final JEVisDataSource ds;
    private final DataSettings dataSettings;
    private final AnalysesComboBox analysesComboBox;

    public DeleteAnalysisDialog(JEVisDataSource ds, DataSettings dataSettings, AnalysesComboBox analysesComboBox) {
        this.ds = ds;
        this.dataSettings = dataSettings;
        this.analysesComboBox = analysesComboBox;

        deleteCurrentAnalysis();
    }

    private void deleteCurrentAnalysis() {
        /* J25.5: question naming the analysis, consequence, red "Löschen" (docs/DESIGN-CC.md) */
        String name = dataSettings.getCurrentAnalysis() == null ? "" : dataSettings.getCurrentAnalysis().getName();
        boolean temporary = false;
        try {
            temporary = dataSettings.getCurrentAnalysis().getJEVisClassName().equals("User");
        } catch (Exception ignored) {
        }
        boolean confirmed = Confirm.ask(
                PluginSidebar.text("plugin.graph.dialog.delete.question", "Analyse „%s“ löschen?", "Delete analysis “%s”?").replace("%s", name),
                temporary
                        ? PluginSidebar.text("plugin.graph.dialog.delete.temporary", "Die zuletzt gespeicherte Ad-hoc-Analyse wird entfernt.", "The last saved ad hoc analysis is removed.")
                        : PluginSidebar.text("plugin.graph.dialog.delete.consequence", "Die Analyse kommt in den Papierkorb und lässt sich von dort wiederherstellen.",
                        "The analysis moves to the recycle bin and can be restored from there."),
                PluginSidebar.text("dialog.ui.delete", "Löschen", "Delete"), true);
        {
            if (confirmed) {
                try {
                    if (dataSettings.getCurrentAnalysis().getJEVisClassName().equals("User")) {
                        JEVisAttribute analysisFileAttribute = ds.getCurrentUser().getUserObject().getAttribute(AnalysisHandler.ANALYSIS_FILE_ATTRIBUTE_NAME);
                        if (analysisFileAttribute.hasSample()) {
                            int currentIndex = analysesComboBox.getItems().indexOf(dataSettings.getCurrentAnalysis());
                            analysisFileAttribute.deleteSamplesBetween(analysisFileAttribute.getTimestampOfLastSample(), analysisFileAttribute.getTimestampOfLastSample());

                            updateAnalysisBox(currentIndex);
                        }
                    } else if (ds.getCurrentUser().canDelete(dataSettings.getCurrentAnalysis().getID())) {
                        int currentIndex = analysesComboBox.getItems().indexOf(dataSettings.getCurrentAnalysis());
                        ds.deleteObject(dataSettings.getCurrentAnalysis().getID(), false);

                        updateAnalysisBox(currentIndex);
                    } else {
                        Notify.error(I18n.getInstance().getString("plugin.graph.dialog.delete.error"));
                    }
                } catch (Exception e) {
                    logger.error("Error: could not delete current analysis", e);
                    Notify.error(PluginSidebar.text("plugin.graph.dialog.delete.failed", "Die Analyse konnte nicht gelöscht werden. Verbindung prüfen und erneut versuchen.",
                            "The analysis could not be deleted. Check the connection and try again."));
                }
            }
        }

    }

    private void updateAnalysisBox(int currentIndex) {
        analysesComboBox.updateListAnalyses();
        analysesComboBox.getSelectionModel().select(currentIndex - 1);
    }
}
