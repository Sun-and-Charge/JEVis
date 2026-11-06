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

import org.jevis.api.JEVisUnit;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.unit.UnitManager;

/**
 * One data row of a chart of an analysis, as stored in the analysis (Control Center: {@code ChartData}).
 * Defaults are those of the Control Center.
 */
public class RowDefinition {
    private String key;
    private long objectId = -1;
    private String attribute = "";
    private String name = "";
    private String color = WebColor.DEFAULT_ROW_COLOR;
    private int axis = 0;
    private AnalysisChartType chartType = AnalysisChartType.AREA;
    private String unitPrefix;
    private String unitFormula;
    private String unitLabel;
    private int decimalDigits = -1;
    private boolean calculation = false;
    private long calculationId = -1;
    private String bubbleType = "NONE";
    private boolean intervalEnabled = false;
    private String intervalStart;
    private String intervalEnd;
    private String css = "";
    /* stored by older Control Center versions per row; only dashboards use them (analyses take the tool bar) */
    private AggregationPeriod aggregation;
    private ManipulationMode manipulation;
    /* resolved when the analysis is read with a data source */
    private Boolean readable;
    private String objectName;

    public RowDefinition() {
        JEVisUnitImp one = new JEVisUnitImp(tech.units.indriya.AbstractUnit.ONE);
        this.unitPrefix = String.valueOf(one.getPrefix());
        this.unitFormula = one.getFormula();
        this.unitLabel = one.getLabel();
    }

    /**
     * @return the unit of the row like {@code ChartData.getUnit()}: {@code new JEVisUnitImp(formula, label, prefix)}
     */
    public JEVisUnit createUnit() {
        return new JEVisUnitImp(unitFormula, unitLabel, unitPrefix);
    }

    /**
     * Sets prefix, formula and label from a unit like {@code ChartData.setUnit(JEVisUnit)}.
     */
    public void setUnit(JEVisUnit unit) {
        this.unitPrefix = String.valueOf(unit.getPrefix());
        this.unitFormula = unit.getFormula();
        this.unitLabel = unit.getLabel();
    }

    /**
     * @return the unit as shown by the Control Center (label without "·")
     */
    public String getUnitDisplay() {
        return UnitManager.getInstance().format(createUnit());
    }

    /**
     * @return the stored name or, like {@code ChartDataRow.getName()}, the object name if the stored name is empty;
     * {@code null} if the object is not readable
     */
    public String getDisplayName() {
        if (Boolean.FALSE.equals(readable)) return null;
        if (name == null || name.isEmpty()) return objectName;
        return name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public String getAttribute() {
        return attribute;
    }

    public void setAttribute(String attribute) {
        this.attribute = attribute;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the color as {@code "#rrggbb"}
     */
    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getAxis() {
        return axis;
    }

    public void setAxis(int axis) {
        this.axis = axis;
    }

    public AnalysisChartType getChartType() {
        return chartType;
    }

    public void setChartType(AnalysisChartType chartType) {
        this.chartType = chartType;
    }

    public String getUnitPrefix() {
        return unitPrefix;
    }

    public void setUnitPrefix(String unitPrefix) {
        this.unitPrefix = unitPrefix;
    }

    public String getUnitFormula() {
        return unitFormula;
    }

    public void setUnitFormula(String unitFormula) {
        this.unitFormula = unitFormula;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public void setUnitLabel(String unitLabel) {
        this.unitLabel = unitLabel;
    }

    public int getDecimalDigits() {
        return decimalDigits;
    }

    public void setDecimalDigits(int decimalDigits) {
        this.decimalDigits = decimalDigits;
    }

    /**
     * @return the stored calculation flag; whether the row is evaluated as calculation is decided by
     * {@link RowDataLoader} (like the Control Center, only if a calculation targets the object)
     */
    public boolean isCalculation() {
        return calculation;
    }

    public void setCalculation(boolean calculation) {
        this.calculation = calculation;
    }

    public long getCalculationId() {
        return calculationId;
    }

    public void setCalculationId(long calculationId) {
        this.calculationId = calculationId;
    }

    public String getBubbleType() {
        return bubbleType;
    }

    public void setBubbleType(String bubbleType) {
        this.bubbleType = bubbleType;
    }

    public boolean isIntervalEnabled() {
        return intervalEnabled;
    }

    public void setIntervalEnabled(boolean intervalEnabled) {
        this.intervalEnabled = intervalEnabled;
    }

    /**
     * @return the row specific start (ISO date time) used instead of the time frame if {@link #isIntervalEnabled()}
     */
    public String getIntervalStart() {
        return intervalStart;
    }

    public void setIntervalStart(String intervalStart) {
        this.intervalStart = intervalStart;
    }

    public String getIntervalEnd() {
        return intervalEnd;
    }

    public void setIntervalEnd(String intervalEnd) {
        this.intervalEnd = intervalEnd;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    /**
     * @return the aggregation stored with the row ({@code aggregationPeriod}), {@code null} if none; analyses ignore
     * it (the tool bar decides), dashboard widgets use it like {@code DataModelDataHandler}
     */
    public AggregationPeriod getAggregation() {
        return aggregation;
    }

    public void setAggregation(AggregationPeriod aggregation) {
        this.aggregation = aggregation;
    }

    /**
     * @return the manipulation stored with the row ({@code manipulationMode}), {@code null} if none, see
     * {@link #getAggregation()}
     */
    public ManipulationMode getManipulation() {
        return manipulation;
    }

    public void setManipulation(ManipulationMode manipulation) {
        this.manipulation = manipulation;
    }

    /**
     * @return {@code true}/{@code false} if the object was resolved with a data source, {@code null} if not resolved
     */
    public Boolean getReadable() {
        return readable;
    }

    public void setReadable(Boolean readable) {
        this.readable = readable;
    }

    /**
     * @return the name of the JEVis object of the row (only set when resolved and readable)
     */
    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public String toString() {
        return "RowDefinition{key='" + key + "', objectId=" + objectId + ", name='" + name + "'}";
    }
}
