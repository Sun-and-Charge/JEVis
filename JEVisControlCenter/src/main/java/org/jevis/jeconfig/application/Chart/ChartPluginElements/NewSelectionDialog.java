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
package org.jevis.jeconfig.application.Chart.ChartPluginElements;

import javafx.scene.control.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs.ChartTab;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs.CommonSettingTab;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs.DayFilterTab;
import org.jevis.jeconfig.application.Chart.data.ChartModel;
import org.jevis.jeconfig.application.Chart.data.DataModel;
import org.jevis.jeconfig.application.navigation.PluginSidebar;
import org.jevis.jeconfig.application.ui.SucDialog;
import org.jevis.jeconfig.dialog.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Rows, charts, common settings and day filter of an analysis (J25.5 stage 5: SucDialog, size L, one primary button).
 * The model is edited in place, as before.
 */
public class NewSelectionDialog extends SucDialog<ButtonType> {
    private static final Logger logger = LogManager.getLogger(NewSelectionDialog.class);
    private final JEVisDataSource ds;
    private final DataModel dataModel;
    private Response response = Response.CANCEL;

    public NewSelectionDialog(JEVisDataSource ds, DataModel dataModel) {
        this(ds, dataModel, false);
    }

    /**
     * @param newAnalysis true for "Neue Analyse": the primary button is "Weiter" (the save dialog follows),
     *                    otherwise "Übernehmen"
     */
    public NewSelectionDialog(JEVisDataSource ds, DataModel dataModel, boolean newAnalysis) {
        super(newAnalysis
                        ? PluginSidebar.text("plugin.graph.selection.new.title", "Neue Analyse", "New analysis")
                        : PluginSidebar.text("plugin.graph.selection.edit.title", "Analyse bearbeiten", "Edit analysis"),
                PluginSidebar.text("plugin.graph.selection.description",
                        "Datenreihen je Diagramm wählen. + fügt ein Diagramm hinzu.",
                        "Choose the rows per chart. + adds a chart."), Size.L);

        this.ds = ds;
        this.dataModel = dataModel;

        TabPane tabPane = new TabPane();

        if (this.dataModel.getChartModels().isEmpty()) {
            ChartModel chartModel = new ChartModel();
            chartModel.setChartId(0);
            chartModel.setChartName(I18n.getInstance().getString("graph.title"));
            this.dataModel.getChartModels().add(chartModel);
        }

        CommonSettingTab commonSettingTab = new CommonSettingTab(this.dataModel);
        DayFilterTab dayFilterTab = new DayFilterTab(this.dataModel);

        for (ChartModel chartModel : this.dataModel.getChartModels()) {
            ChartTab chartTab = new ChartTab(ds, chartModel);
            chartTab.setClosable(true);

            chartTab.setOnClosed(event -> this.dataModel.getChartModels().remove(chartModel));

            tabPane.getTabs().add(chartTab);
        }

        Tab addTab = new Tab();
        addTab.setGraphic(JEConfig.getSVGImage(Icon.PLUS, 12, 12));
        addTab.setClosable(false);
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldTab, newTab) -> {
            if (newTab == addTab) {
                ChartModel chartModel = new ChartModel();
                chartModel.setChartName(getNextChartName());
                chartModel.setChartId(getNextChartId());
                this.dataModel.getChartModels().add(chartModel);
                ChartTab newChartTab = new ChartTab(ds, chartModel);
                newChartTab.setOnClosed(event -> this.dataModel.getChartModels().remove(chartModel));

                tabPane.getTabs().add(tabPane.getTabs().indexOf(addTab), newChartTab);
                tabPane.getSelectionModel().select(newChartTab);
            }
        });

        tabPane.getTabs().add(addTab);
        tabPane.getTabs().add(commonSettingTab);
        tabPane.getTabs().add(dayFilterTab);

        ButtonType okType = new ButtonType(newAnalysis
                ? PluginSidebar.text("plugin.graph.selection.next", "Weiter", "Next")
                : PluginSidebar.text("plugin.graph.selection.apply", "Übernehmen", "Apply"), ButtonBar.ButtonData.OK_DONE);
        addPrimary(okType);
        addCancel();
        setResultConverter(type -> {
            response = type == okType ? Response.OK : Response.CANCEL;
            return type;
        });
        tabPane.setPrefHeight(560);

        this.getDialogPane().setContent(tabPane);
    }

    private int getNextChartId() {
        int id = 0;
        for (ChartModel chartModel : dataModel.getChartModels()) {
            id = Math.max(id, chartModel.getChartId());
        }

        id++;

        return id;
    }


    private String getNextChartName() {
        List<String> oldNames = new ArrayList<>();
        for (ChartModel chartModel : dataModel.getChartModels()) {
            oldNames.add(chartModel.getChartName());
        }

        String newName = I18n.getInstance().getString("graph.title");
        int i = 1;

        while (oldNames.contains(newName)) {
            for (String s : oldNames) {
                if (s.equals(newName)) {
                    i++;

                    if (newName.contains(" ")) {
                        newName = newName.substring(0, newName.lastIndexOf(" "));
                    }
                    newName += " " + i;
                }
            }

        }

        return newName;
    }

    public Response getResponse() {
        return response;
    }
}
