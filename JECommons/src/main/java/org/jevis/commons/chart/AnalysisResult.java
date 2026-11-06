/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

/**
 * Data of all charts of an analysis for a time frame.
 */
public class AnalysisResult {
    private DateTime from;
    private DateTime to;
    private List<ChartResult> charts = new ArrayList<>();

    /**
     * @return start of the resolved time frame (in the requested zone)
     */
    public DateTime getFrom() {
        return from;
    }

    public void setFrom(DateTime from) {
        this.from = from;
    }

    public DateTime getTo() {
        return to;
    }

    public void setTo(DateTime to) {
        this.to = to;
    }

    public List<ChartResult> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartResult> charts) {
        this.charts = charts;
    }
}
