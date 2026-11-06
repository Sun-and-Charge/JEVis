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
 * Statistics of a data row as shown in the table below a chart of the Control Center
 * ({@code XYChartSerie.updateTableEntry}). Numbers only.
 */
public class RowStatistics {
    private Double min;
    private Double max;
    private Double avg;
    private Double sum;
    private String sumUnit;
    private Double enpi;
    private int count;

    public Double getMin() {
        return min;
    }

    public void setMin(Double min) {
        this.min = min;
    }

    public Double getMax() {
        return max;
    }

    public void setMax(Double max) {
        this.max = max;
    }

    /**
     * @return the arithmetic mean; for calculated rows the result of the calculation over the whole time frame
     * (Control Center column "average" of a calculation); {@code null} if not available
     */
    public Double getAvg() {
        return avg;
    }

    public void setAvg(Double avg) {
        this.avg = avg;
    }

    /**
     * @return the sum for quantities, the converted sum for power units (e.g. kW to kWh, see {@link #getSumUnit()}),
     * otherwise {@code null} (the Control Center shows "-")
     */
    public Double getSum() {
        return sum;
    }

    public void setSum(Double sum) {
        this.sum = sum;
    }

    /**
     * @return the unit of the sum (differs from the row unit for power units), {@code null} without sum
     */
    public String getSumUnit() {
        return sumUnit;
    }

    public void setSumUnit(String sumUnit) {
        this.sumUnit = sumUnit;
    }

    /**
     * @return for calculated rows the arithmetic mean of the samples (Control Center column "EnPI"), else {@code null}
     */
    public Double getEnpi() {
        return enpi;
    }

    public void setEnpi(Double enpi) {
        this.enpi = enpi;
    }

    /**
     * @return number of samples
     */
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }
}
