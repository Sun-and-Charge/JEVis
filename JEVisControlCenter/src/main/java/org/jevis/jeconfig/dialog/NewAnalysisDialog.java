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
import org.jevis.api.JEVisDataSource;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.NewSelectionDialog;
import org.jevis.jeconfig.application.Chart.data.DataModel;
import org.jevis.jeconfig.application.tools.JEVisHelp;
import org.jevis.jeconfig.plugin.charts.ChartPlugin;

public class NewAnalysisDialog {
    private final JEVisDataSource ds;
    private final DataModel model;
    private final ChartPlugin chartPlugin;
    private final Boolean changed;

    public NewAnalysisDialog(JEVisDataSource ds, DataModel model, ChartPlugin chartPlugin, Boolean changed) {
        this.ds = ds;
        this.model = model;
        this.chartPlugin = chartPlugin;
        this.changed = changed;

        newAnalysis();
    }

    private void newAnalysis() {

        model.reset();

        NewSelectionDialog newSelectionDialog = new NewSelectionDialog(ds, model);
        newSelectionDialog.setOnCloseRequest(event -> {
            if (newSelectionDialog.getResponse() == Response.OK) {

                /* the current analysis still points to the previously opened one, so do not prefill its name */
                SaveAnalysisDialog saveAnalysisDialog = new SaveAnalysisDialog(ds, chartPlugin.getDataSettings(), chartPlugin, chartPlugin.getToolBarView(), true);
                saveAnalysisDialog.show();
                Platform.runLater(() -> chartPlugin.getToolBarView().setDisableToolBarIcons(false));
            }
            JEVisHelp.getInstance().deactivatePluginModule();
        });

        newSelectionDialog.show();
    }
}
