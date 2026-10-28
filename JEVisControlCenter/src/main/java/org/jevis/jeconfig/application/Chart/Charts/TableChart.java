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
package org.jevis.jeconfig.application.Chart.Charts;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.layout.HBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.Chart.ChartElements.TableHeaderTable;
import org.jevis.jeconfig.application.Chart.ChartElements.TableSerie;
import org.jevis.jeconfig.application.Chart.ChartElements.XYChartSerie;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TableTopDatePicker;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.jevis.jeconfig.application.Chart.data.ChartModel;
import org.jevis.jeconfig.plugin.charts.DataSettings;
import org.jevis.jeconfig.plugin.charts.ToolBarSettings;

import java.util.Comparator;
import java.util.List;

public class TableChart extends XYChart {
    private static final Logger logger = LogManager.getLogger(TableChart.class);
    private ChartDataRow singleRow;
    private final TableTopDatePicker tableTopDatePicker = new TableTopDatePicker();
    private TableHeaderTable tableHeader;

    public TableChart(JEVisDataSource ds, ChartModel chartModel) {
        super(ds, chartModel);
    }

    @Override
    public void createChart(List<ChartDataRow> dataRows, ToolBarSettings toolBarSettings, DataSettings dataSettings, boolean instant) {
        this.chartDataRows = dataRows;

        if (!instant) {

            Task task = new Task() {
                @Override
                protected Object call() throws Exception {
                    try {
                        buildChart(toolBarSettings, dataSettings);

                        tableTopDatePicker.initialize(singleRow, timeStampOfLastSample.get());
                    } catch (Exception e) {
                        this.failed();
                        logger.error("Could not build chart {}", chartModel.getChartName(), e);
                    } finally {
                        succeeded();
                    }
                    return null;
                }
            };

            JEConfig.getStatusBar().addTask(TableChart.class.getName(), task, taskImage, true);
        } else {
            buildChart(toolBarSettings, dataSettings);
        }
    }

    @Override
    public XYChartSerie generateSerie(Boolean[] changedBoth, ChartDataRow singleRow) throws JEVisException {
        this.singleRow = singleRow;
        TableSerie serie = new TableSerie(chartModel, singleRow, showIcons);

        getHexColors().add(singleRow.getColor());

        /**
         * check if timestamps are in serie
         */

        if (serie.getTimeStampOfFirstSample().isBefore(timeStampOfFirstSample.get())) {
            timeStampOfFirstSample.set(serie.getTimeStampOfFirstSample());
            changedBoth[0] = true;
        }

        if (serie.getTimeStampOfLastSample().isAfter(timeStampOfLastSample.get())) {
            timeStampOfLastSample.set(serie.getTimeStampOfLastSample());
            changedBoth[1] = true;
        }

        return serie;
    }

    @Override
    public void addSeriesToChart() {
        xyChartSerieList.sort(Comparator.comparingDouble(XYChartSerie::getSortCriteria));


        for (XYChartSerie xyChartSerie : xyChartSerieList) {
            tableData.add(xyChartSerie.getTableEntry());
        }

        AlphanumComparator ac = new AlphanumComparator();

        Platform.runLater(() -> tableData.sort((o1, o2) -> ac.compare(o1.getName(), o2.getName())));
    }

    public HBox getTopPicker() {
        return tableTopDatePicker;
    }

    @Override
    public void generateYAxis() {
    }

    public TableTopDatePicker getTableTopDatePicker() {
        return tableTopDatePicker;
    }

    public ChartDataRow getSingleRow() {
        return singleRow;
    }

    private boolean blockDatePickerEvent = false;

    public boolean isBlockDatePickerEvent() {
        return blockDatePickerEvent;
    }

    public void setBlockDatePickerEvent(boolean blockDatePickerEvent) {
        this.blockDatePickerEvent = blockDatePickerEvent;
    }

    public TableHeaderTable getTableHeader() {
        return tableHeader;
    }

    public void setTableHeader(TableHeaderTable tableHeader) {
        this.tableHeader = tableHeader;
    }

    @Override
    public ChartModel getChartModel() {
        return chartModel;
    }
}
