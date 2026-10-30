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
package org.jevis.jeconfig.plugin.dashboard.config2;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs.ChartTab;
import org.jevis.jeconfig.application.Chart.data.ChartData;
import org.jevis.jeconfig.plugin.dashboard.datahandler.DataModelDataHandler;
import org.jevis.jeconfig.plugin.dashboard.widget.ChartWidget;
import org.jevis.jeconfig.plugin.dashboard.widget.GenericConfigNode;
import org.jevis.jeconfig.plugin.dashboard.widget.ValueWidget;
import org.jevis.jeconfig.plugin.dashboard.widget.Widget;
import org.jevis.jeconfig.tool.ScreenSize;

public class WidgetConfigDialog extends Alert {

    private static final Logger logger = LogManager.getLogger(WidgetConfigDialog.class);
    private final TabPane tabPane = new TabPane();
    //private WidgetTreePlugin widgetTreePlugin;
    private final Widget widget;
    private DataModelDataHandler dataModelDataHandler;

    /**
     * Create a new Widget Config Dialog.
     */
    public WidgetConfigDialog(Widget widget) {
        super(AlertType.INFORMATION);

        this.widget = widget;
        setTitle(I18n.getInstance().getString("dashboard.widget.editor.title"));
        setHeaderText(I18n.getInstance().getString("dashboard.widget.editor.header"));
        setResizable(true);
        initOwner(JEConfig.getStage());
        initModality(Modality.APPLICATION_MODAL);

        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(this.tabPane);
        borderPane.setPrefWidth(ScreenSize.fitScreenWidth(1500));
        borderPane.setPrefHeight(650d);

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        getDialogPane().setContent(borderPane);

        Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
        TopMenu.applyActiveTheme(stage.getScene());
        /* no setAlwaysOnTop: the dialog is owned by the main stage and modal, always-on-top would hide file choosers behind it */
    }


    public void requestFirstTabFocus() {
        Platform.runLater(() -> tabPane.getTabs().get(0).getContent().requestFocus());
    }

    private void addGeneralTab(DataModelDataHandler dataModelDataHandler) {
        GenericConfigNode genericConfigNode = new GenericConfigNode(widget.getDataSource(), widget, dataModelDataHandler);
        addTab(genericConfigNode);
    }

    public void addTab(Tab tab) {
        this.tabPane.getTabs().add(tab);
    }

    public void addGeneralTabsDataModel(DataModelDataHandler dataModelDataHandler) {
        addGeneralTab(dataModelDataHandler);

        if (dataModelDataHandler != null) {
            this.dataModelDataHandler = dataModelDataHandler;

            ChartTab chartTab = new ChartTab(dataModelDataHandler.getJeVisDataSource(), dataModelDataHandler.getDataModel().getChartModels().get(0));
            chartTab.setText(I18n.getInstance().getString("plugin.dashboard.widget.config.tab.datamodel"));
            chartTab.setClosable(false);
            chartTab.setMenuVisible(true);
            chartTab.setIntervalColumnVisible(false);
            chartTab.setShowChartSettings(false);

            if (widget instanceof ValueWidget) {
                chartTab.setCommonChartSettingsVisible(false);
                chartTab.setColorColumnVisible(false);
                chartTab.setAxisColumnVisible(false);
                chartTab.setChartTypeColumnVisible(false);
                chartTab.setCssColumnVisible(false);
                chartTab.setNameColumnVisible(false);
                chartTab.setAggregationPeriodColumnVisible(true);
                chartTab.setManipulationModeColumnVisible(true);
            } else if (widget instanceof ChartWidget) {
                chartTab.setShowChartSettings(true);
            }

            addTab(chartTab);
        }

    }

    public ObservableList<ChartData> addGeneralTabsDataModelNetGraph(DataModelDataHandler dataModelDataHandler) {
        addGeneralTab(dataModelDataHandler);

        if (dataModelDataHandler != null) {
            this.dataModelDataHandler = dataModelDataHandler;

            ChartTab chartTab = new ChartTab(dataModelDataHandler.getJeVisDataSource(), dataModelDataHandler.getDataModel().getChartModels().get(0));

            chartTab.setText(I18n.getInstance().getString("plugin.dashboard.widget.config.tab.datamodel"));
            chartTab.setClosable(false);
            chartTab.setMenuVisible(false);
            chartTab.setIntervalColumnVisible(false);

            if (widget instanceof ValueWidget) {
                chartTab.setCommonChartSettingsVisible(false);
                chartTab.setColorColumnVisible(false);
                chartTab.setAxisColumnVisible(false);
                chartTab.setChartTypeColumnVisible(false);
                chartTab.setCssColumnVisible(false);
                chartTab.setNameColumnVisible(false);
            }

            addTab(chartTab);
            chartTab.getChartTable().getItems().addListener(new ListChangeListener<ChartData>() {
                @Override
                public void onChanged(Change<? extends ChartData> c) {
                    logger.debug("table changed");
                    logger.debug(c);
                }
            });
            return chartTab.getChartTable().getItems();
        }
        return null;

    }


    public void commitSettings() {
        this.tabPane.getTabs().forEach(tab -> {
            try {
                if (tab instanceof ConfigTab) {
                    ((ConfigTab) tab).commitChanges();
                }
//                else if (tab instanceof ChartTab) {
//                    ((ChartTab) tab).commitChanges();
//                }
            } catch (Exception ex) {
                logger.error("Failed to commit widget config tab changes", ex);
            }
        });
        logger.debug("done wiget config commit for: {}", getTitle());
    }
}
