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

import java.util.ArrayList;
import java.util.List;

/**
 * Data of one row of an analysis for a time frame.
 */
public class RowResult {
    private String key;
    private RowStatus status = RowStatus.OK;
    private String message;
    private String unit;
    private boolean calculation;
    private boolean stringData;
    private List<ChartSample> samples = new ArrayList<>();
    private RowStatistics stats;
    private Double pieValue;

    public RowResult() {
    }

    public RowResult(String key, RowStatus status, String message) {
        this.key = key;
        this.status = status;
        this.message = message;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public RowStatus getStatus() {
        return status;
    }

    public void setStatus(RowStatus status) {
        this.status = status;
    }

    /**
     * @return a technical message for {@link RowStatus#ERROR} (and some {@link RowStatus#NO_DATA} cases), else {@code null}
     */
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return the unit of the values as shown by the Control Center; for aggregated rows of XY charts the sum unit
     * (e.g. kWh instead of kW); {@code null} without permission
     */
    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    /**
     * @return true if the row was evaluated as a calculation
     */
    public boolean isCalculation() {
        return calculation;
    }

    public void setCalculation(boolean calculation) {
        this.calculation = calculation;
    }

    /**
     * @return true for text data rows ("String Data", "Data Notes"): values are strings
     */
    public boolean isStringData() {
        return stringData;
    }

    public void setStringData(boolean stringData) {
        this.stringData = stringData;
    }

    public List<ChartSample> getSamples() {
        return samples;
    }

    public void setSamples(List<ChartSample> samples) {
        this.samples = samples;
    }

    /**
     * @return statistics, {@code null} for rows without samples and for text rows
     */
    public RowStatistics getStats() {
        return stats;
    }

    public void setStats(RowStatistics stats) {
        this.stats = stats;
    }

    /**
     * @return for rows of pie charts the value of the pie piece as {@code PieChart} computes it, else {@code null}
     */
    public Double getPieValue() {
        return pieValue;
    }

    public void setPieValue(Double pieValue) {
        this.pieValue = pieValue;
    }
}
