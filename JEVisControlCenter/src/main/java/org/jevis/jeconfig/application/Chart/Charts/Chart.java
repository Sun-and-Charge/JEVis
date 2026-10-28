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

import javafx.collections.ObservableList;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.application.Chart.ChartElements.TableEntry;
import org.jevis.jeconfig.application.Chart.ChartElements.XYChartSerie;
import org.jevis.jeconfig.application.Chart.ChartType;
import org.jevis.jeconfig.application.Chart.data.ChartDataRow;
import org.jevis.jeconfig.application.Chart.data.ChartModel;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.List;

public interface Chart extends Comparable<Chart> {

    String getChartName();

    void setTitle(String s);

    Integer getChartId();

    void updateTable(MouseEvent mouseEvent, DateTime valueForDisplay);

    void updateTableZoom(double lowerBound, double upperBound);

    void applyColors();

    de.gsi.chart.Chart getChart();

    void setChart(de.gsi.chart.Chart chart);

    ChartType getChartType();

    Region getRegion();

    ObservableList<TableEntry> getTableData();

    Period getPeriod();

    void setPeriod(Period period);

    void setRegion(Region region);

    List<ChartDataRow> getChartDataRows();

    ChartModel getChartModel();

    List<XYChartSerie> getXyChartSerieList();

    AlphanumComparator alphanumComparator = new AlphanumComparator();

    @Override
    default int compareTo(@NotNull Chart o) {
        return alphanumComparator.compare(this.getChartName(), o.getChartName());
    }
}

