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
package org.jevis.jeconfig.plugin.dashboard.common;

import com.jfoenix.controls.JFXButton;
import javafx.scene.control.Alert;
import javafx.scene.control.Tooltip;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.AnalysisRequest;
import org.jevis.jeconfig.plugin.charts.ChartPlugin;
import org.jevis.jeconfig.plugin.dashboard.DashboardControl;
import org.jevis.jeconfig.plugin.dashboard.config.GraphAnalysisLinkerNode;
import org.jevis.jeconfig.plugin.dashboard.timeframe.LastPeriod;
import org.jevis.jeconfig.plugin.dashboard.timeframe.TimeFrame;
import org.joda.time.Interval;

public class GraphAnalysisLinker {
    private static final Logger logger = LogManager.getLogger(GraphAnalysisLinker.class);

    public static String ANALYSIS_LINKER_NODE = "analysisLinker";
    private final JEVisDataSource dataSource;
    private GraphAnalysisLinkerNode node = new GraphAnalysisLinkerNode();
    private AggregationPeriod aggregationPeriod;
    private ManipulationMode manipulationMode;
    private Interval interval;
    boolean targetIsreachable = false;
    JFXButton button = new JFXButton("", JEConfig.getImage("1415314386_Graph.png", 20, 20));

    public GraphAnalysisLinker(JEVisDataSource dataSource) {
        button.setStyle("-fx-background-color: transparent;");

        this.dataSource = dataSource;
        button.setOnAction(event -> {
            openLink();
        });


    }

    public JFXButton getLinkerButton() {
        return button;
    }

    public void openLink() {
        try {
            if (targetIsreachable && interval != null) {
                AnalysisRequest analysisRequest = new AnalysisRequest(
                        this.dataSource.getObject(this.node.getGraphAnalysisObject())
                        , aggregationPeriod, manipulationMode
                        , interval.getStart(), interval.getEnd());

                JEConfig.openObjectInPlugin(ChartPlugin.PLUGIN_NAME, analysisRequest);
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setContentText(I18n.getInstance().getString("plugin.dashboard.linker.error.message"));

                alert.showAndWait();
            }


        } catch (Exception ex) {
            logger.error("Failed to open linked graph analysis", ex);
        }
    }

    /**
     * @return the time frame with the given ID (dashboard time frames or last periods), or {@code null} if the ID is
     * empty or unknown
     */
    public static TimeFrame findTimeFrame(DashboardControl control, String timeFrameID) {
        if (timeFrameID == null || timeFrameID.isEmpty()) {
            return null;
        }
        try {
            for (TimeFrame timeFrame : control.getAllTimeFrames().getAll()) {
                if (timeFrameID.equals(timeFrame.getID())) {
                    return timeFrame;
                }
            }
            return new LastPeriod(timeFrameID);
        } catch (Exception ex) {
            logger.warn("Unknown time frame '{}'", timeFrameID);
            return null;
        }
    }

    /**
     * @return the interval of the given time frame relative to the end of the dashboard interval, or the dashboard
     * interval if no time frame is set
     */
    public static Interval resolveInterval(DashboardControl control, String timeFrameID, Interval dashboardInterval, boolean fixed) {
        TimeFrame timeFrame = findTimeFrame(control, timeFrameID);
        if (timeFrame == null || dashboardInterval == null) {
            return dashboardInterval;
        }
        try {
            Interval forced = timeFrame.getInterval(dashboardInterval.getEnd(), fixed);
            if (forced != null && forced.getEndMillis() > forced.getStartMillis()) {
                return forced;
            }
        } catch (Exception ex) {
            logger.warn("Could not calculate interval of time frame '{}'", timeFrameID, ex);
        }
        return dashboardInterval;
    }

    public void applyNode(GraphAnalysisLinkerNode node) {
        try {
            this.node = node;
            targetIsreachable = false;
            if (this.node.getGraphAnalysisObject() != null && this.node.getGraphAnalysisObject() > 0) {
                JEVisObject jeVisObject = this.dataSource.getObject(this.node.getGraphAnalysisObject());
                if (jeVisObject != null) {
                    Tooltip tooltip = new Tooltip(I18n.getInstance().getString("plugin.dashboard.linker.open") + " " + jeVisObject.getName());
                    button.setTooltip(tooltip);
                    targetIsreachable = true;
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to apply graph analysis linker node", ex);
        }
    }

    public void applyConfig(AggregationPeriod aggregationPeriod, ManipulationMode manipulationMode, Interval interval) {
//        System.out.println("ApplyConfig: " + aggregationPeriod + "  man: " + manipulationMode + "  int: " + interval);
        this.aggregationPeriod = aggregationPeriod;
        this.manipulationMode = manipulationMode;
        this.interval = interval;

    }

//    public void applyConfig(JFXButton button, List<ChartDataModel> dataModels, Interval interval) {
//        button.setOnAction(event -> {
//            try {
//                AggregationPeriod aggregationPeriod = AggregationPeriod.HOURLY;
//                ManipulationMode manipulationMode = ManipulationMode.NONE;
//                for (ChartDataModel dataModel : dataModels) {
//                    aggregationPeriod = dataModel.getAggregationPeriod();
//                    manipulationMode = dataModel.getManipulationMode();
//                    break;
//                }
//                AnalysisTimeFrame analysisTimeFrame = new AnalysisTimeFrame(TimeFrame.CUSTOM);
//                AnalysisRequest analysisRequest = new AnalysisRequest(
//                        this.dataSource.getObject(this.node.getGraphAnalysisObject())
//                        , aggregationPeriod, manipulationMode, analysisTimeFrame
//                        , interval.getStart(), interval.getEnd());
//                JEConfig.openObjectInPlugin(GraphPluginView.PLUGIN_NAME, analysisRequest);
//            } catch (Exception ex) {
//                ex.printStackTrace();
//            }
//        });
//        try {
//            JEVisObject jeVisObject = this.dataSource.getObject(this.node.getGraphAnalysisObject());
//            if (jeVisObject != null) {
//                Tooltip tooltip = new  Tooltip(I18n.getInstance().getString("plugin.dashboard.linker.open") + " " + jeVisObject.getName());
//                button.setTooltip(tooltip);
//            }
//
//        } catch (JEVisException e) {
//            e.printStackTrace();
//        }
//
//    }

}
