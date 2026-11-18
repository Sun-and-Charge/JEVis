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

import org.jevis.jeconfig.application.Chart.ChartElements.ChartLegend;
import javafx.collections.ObservableList;
import javafx.scene.chart.PieChart;

public class PieChartExtended extends PieChart {

    /* own legend instead of the internal JavaFX one (J25); filled by the chart with names and colours */
    private final ChartLegend legend = new ChartLegend();

    public PieChartExtended() {
        super();
        setLegend(this.legend);
    }

    public PieChartExtended(ObservableList<Data> data) {
        super(data);
        setLegend(this.legend);
    }

    public ChartLegend getPieLegend() {
        return this.legend;
    }
}
