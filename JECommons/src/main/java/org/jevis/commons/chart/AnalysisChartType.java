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

/**
 * Chart types of an analysis, same names and parsing as the Control Center's {@code ChartType}
 * (org.jevis.jeconfig.application.Chart.ChartType) so the values can be passed through as strings.
 */
public enum AnalysisChartType {
    AREA, LOGICAL, LINE, BAR, COLUMN, BUBBLE, SCATTER, PIE, TABLE, HEAT_MAP, DEFAULT, TABLE_V, STACKED_AREA, STACKED_COLUMN, TABLE_H;

    /**
     * Same as {@code ChartType.parseChartType(String)}: unknown or {@code null} values become {@link #LINE}.
     */
    public static AnalysisChartType parse(String chartType) {
        if (chartType == null) return LINE;
        for (AnalysisChartType type : values()) {
            if (type.name().equals(chartType)) return type;
        }
        return LINE;
    }

    /**
     * Same as {@code ChartType.parseChartType(Integer)}: unknown indices become {@link #LINE}.
     */
    public static AnalysisChartType parse(Integer index) {
        if (index == null) return LINE;
        switch (index) {
            case 0:
                return AREA;
            case 1:
                return LOGICAL;
            case 3:
                return BAR;
            case 4:
                return COLUMN;
            case 5:
                return BUBBLE;
            case 6:
                return SCATTER;
            case 7:
                return PIE;
            case 8:
                return TABLE;
            case 9:
                return HEAT_MAP;
            case 10:
                return DEFAULT;
            case 11:
                return TABLE_V;
            case 12:
                return STACKED_AREA;
            case 13:
                return STACKED_COLUMN;
            case 14:
                return TABLE_H;
            case 2:
            default:
                return LINE;
        }
    }

    /**
     * @return true if the Control Center draws a chart of this type with {@code XYChart}/{@code XYChartSerie},
     * i.e. aggregated rows get the sum unit and the table below the chart shows min/max/avg/sum
     */
    public boolean isXYChart() {
        switch (this) {
            case PIE:
            case BAR:
            case BUBBLE:
            case HEAT_MAP:
                return false;
            default:
                return true;
        }
    }
}
