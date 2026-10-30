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

import com.sun.javafx.charts.Legend;
import javafx.geometry.Pos;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.Chart.ChartType;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.jevis.jeconfig.plugin.AnalysisRequest;
import org.jevis.jeconfig.plugin.charts.ChartPlugin;
import org.jevis.jeconfig.plugin.object.ObjectPlugin;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.lang.reflect.Field;

public class WidgetLegend extends Legend {
    private static final Logger logger = LogManager.getLogger(WidgetLegend.class);


    /**
     * Optional interval used when a legend entry is opened in the analysis; set by the widget on each update.
     */
    private Interval interval;

    public void setInterval(Interval interval) {
        this.interval = interval;
    }

    public Legend.LegendItem buildHorizontalLegendItem(String name, Color color, Color fontcolor, double fontSize, JEVisObject obj, boolean isAlert, String alertText, boolean wrapText) {
        return buildHorizontalLegendItem(name, color, fontcolor, fontSize, obj, isAlert, alertText, wrapText, null);
    }

    /**
     * @param row optional data row of this entry; if set, the entry gets a context menu to open the row in the analysis
     */
    public Legend.LegendItem buildHorizontalLegendItem(String name, Color color, Color fontcolor, double fontSize, JEVisObject obj, boolean isAlert, String alertText, boolean wrapText, ChartDataRow row) {
        Rectangle r = new Rectangle();
        r.setX(0);
        r.setY(0);
        r.setWidth(12);
        r.setHeight(12);
        r.setArcWidth(20);
        r.setArcHeight(20);
        r.setStroke(color);
        r.setFill(color);

        Label alertLabel = new Label();
        alertLabel.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        ImageView alertImage = JEConfig.getImage("Warning-icon.png", 12d, 12d);
        alertLabel.setGraphic(alertImage);
        alertLabel.setTooltip(new Tooltip(alertText));

        GridPane gridPane = new GridPane();
        gridPane.add(r, 0, 0);
        gridPane.setAlignment(Pos.BASELINE_LEFT);
        gridPane.setHgap(5);
        if (isAlert) {
            gridPane.add(alertLabel, 1, 0);
        }


        /**
         * TODO: replace this hack with an own implementation of an legend
         */
        Legend.LegendItem item = new Legend.LegendItem(name, gridPane);
        try {
            Field privateStringField = Legend.LegendItem.class.
                    getDeclaredField("label");
            privateStringField.setAccessible(true);
            Label label = (Label) privateStringField.get(item);
            label.setWrapText(wrapText);
//            label.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            label.setTextFill(fontcolor);
            label.setFont(new Font(fontSize));
//            label.setMaxWidth(180);
            //label.setMaxWidth(140);
            label.setMinWidth(140);

            installActions(label, obj, row);

        } catch (Exception ex) {
            logger.error("Failed to configure legend item label", ex);
        }

        return item;
    }


    public Legend.LegendItem buildVerticalLegendItem(String name, Color color, Color fontcolor, double fontSize, JEVisObject obj, boolean isAlert, String alertText, boolean wrapText) {
        return buildVerticalLegendItem(name, color, fontcolor, fontSize, obj, isAlert, alertText, wrapText, null);
    }

    /**
     * @param row optional data row of this entry; if set, the entry gets a context menu to open the row in the analysis
     */
    public Legend.LegendItem buildVerticalLegendItem(String name, Color color, Color fontcolor, double fontSize, JEVisObject obj, boolean isAlert, String alertText, boolean wrapText, ChartDataRow row) {

        Rectangle r = new Rectangle();
        r.setX(0);
        r.setY(0);
        r.setWidth(12);
        r.setHeight(12);
        r.setArcWidth(20);
        r.setArcHeight(20);
        r.setStroke(color);
        r.setFill(color);

        Label alertLabel = new Label();
        alertLabel.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        ImageView alertImage = JEConfig.getImage("Warning-icon.png", 12d, 12d);
        alertLabel.setGraphic(alertImage);
        alertLabel.setTooltip(new Tooltip(alertText));

        GridPane gridPane = new GridPane();
        gridPane.add(r, 0, 0);
        gridPane.setAlignment(Pos.BASELINE_LEFT);
        gridPane.setHgap(5);
        if (isAlert) {
            gridPane.add(alertLabel, 1, 0);
        }


        /**
         * TODO: replace this hack with an own implementation of an legend
         */
        Legend.LegendItem item = new Legend.LegendItem(name, gridPane);
        try {
            Field privateStringField = Legend.LegendItem.class.
                    getDeclaredField("label");
            privateStringField.setAccessible(true);
            Label label = (Label) privateStringField.get(item);
            label.setWrapText(wrapText);
//            label.setTextOverrun(OverrunStyle.CENTER_ELLIPSIS);
            label.setTextFill(fontcolor);
            label.setFont(new Font(fontSize));
//            label.setMaxWidth(180);
            label.setMaxWidth(140);
            label.setMinWidth(140);

            installActions(label, obj, row);

        } catch (Exception ex) {
            logger.error("Failed to configure vertical legend item label", ex);
        }

        return item;
    }

    private void installActions(Label label, JEVisObject obj, ChartDataRow row) {
        try {
            label.setOnMouseClicked(event -> {
                if (event.isShiftDown() && event.getClickCount() == 2) {
                    JEConfig.openObjectInPlugin(ObjectPlugin.PLUGIN_NAME, obj);
                }
            });
        } catch (Exception ex) {
            logger.debug("Could not install legend click handler", ex);
        }

        if (row == null) {
            return;
        }

        try {
            Menu openAs = new Menu(I18n.getInstance().getString("plugin.dashboard.legend.openinanalysisas"));
            for (ChartType chartType : new ChartType[]{ChartType.LINE, ChartType.AREA, ChartType.COLUMN, ChartType.BAR, ChartType.TABLE}) {
                MenuItem item = new MenuItem(getChartTypeName(chartType));
                item.setOnAction(event -> openInAnalysis(row, obj, chartType));
                openAs.getItems().add(item);
            }
            label.setContextMenu(new ContextMenu(openAs));
        } catch (Exception ex) {
            logger.error("Could not install legend context menu", ex);
        }
    }

    private void openInAnalysis(ChartDataRow row, JEVisObject fallbackObject, ChartType chartType) {
        try {
            /* keep the clean data object if the row uses one, otherwise the raw data object */
            JEVisObject target = row.getDataProcessor() != null ? row.getDataProcessor() : row.getObject();
            if (target == null) {
                target = fallbackObject;
            }
            if (target == null) {
                return;
            }

            DateTime start;
            DateTime end;
            if (interval != null) {
                start = interval.getStart();
                end = interval.getEnd();
            } else {
                start = row.getSelectedStart();
                end = row.getSelectedEnd();
            }

            AggregationPeriod aggregationPeriod = row.getAggregationPeriod() != null ? row.getAggregationPeriod() : AggregationPeriod.NONE;
            ManipulationMode manipulationMode = row.getManipulationMode() != null ? row.getManipulationMode() : ManipulationMode.NONE;

            AnalysisRequest analysisRequest = new AnalysisRequest(target, aggregationPeriod, manipulationMode, start, end);
            analysisRequest.setChartType(chartType);
            JEConfig.openObjectInPlugin(ChartPlugin.PLUGIN_NAME, analysisRequest);
        } catch (Exception ex) {
            logger.error("Could not open legend entry in analysis", ex);
        }
    }

    private static String getChartTypeName(ChartType chartType) {
        switch (chartType) {
            case LINE:
                return I18n.getInstance().getString("plugin.graph.charttype.line.name");
            case AREA:
                return I18n.getInstance().getString("plugin.graph.charttype.area.name");
            case COLUMN:
                return I18n.getInstance().getString("plugin.graph.charttype.column.name");
            case BAR:
                return I18n.getInstance().getString("plugin.graph.charttype.bar.name");
            case TABLE:
                return I18n.getInstance().getString("plugin.graph.charttype.table.name");
            default:
                return chartType.toString();
        }
    }
}
