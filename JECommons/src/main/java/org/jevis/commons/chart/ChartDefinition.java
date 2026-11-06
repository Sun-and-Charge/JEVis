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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One chart of an analysis (Control Center: {@code ChartModel}). Defaults are those of the Control Center.
 */
public class ChartDefinition {
    public static final int DEFAULT_FRACTION_DIGITS = 2;
    private int id = -1;
    private String name = "";
    private AnalysisChartType type = AnalysisChartType.LINE;
    private int minFractionDigits = DEFAULT_FRACTION_DIGITS;
    private int maxFractionDigits = DEFAULT_FRACTION_DIGITS;
    private Boolean fractionDigitsAuto = null;
    private double height = -1;
    private String orientation = "HORIZONTAL";
    private boolean fixYAxisToZero = false;
    private String xAxisTitle = null;
    private String yAxisTitle = null;
    private double groupingInterval = 30;
    private String colorMapping = "GREEN_YELLOW_RED";
    private boolean filterEnabled = false;
    private boolean coloringEnabled = false;
    private boolean showColumnSums = false;
    private boolean showRowSums = false;
    private String dayStart = null;
    private String dayEnd = null;
    private Map<String, String> filterTexts = new LinkedHashMap<>();
    private List<RowDefinition> rows = new ArrayList<>();

    /**
     * @return true if the fraction digits are chosen automatically, like {@code ChartModel.isAutoFractionDigits()}
     */
    public boolean isAutoFractionDigits() {
        if (fractionDigitsAuto != null) return fractionDigitsAuto;
        return minFractionDigits == DEFAULT_FRACTION_DIGITS && maxFractionDigits == DEFAULT_FRACTION_DIGITS;
    }

    /**
     * @return the type of a row: its own type, or the chart type for {@link AnalysisChartType#DEFAULT}
     */
    public AnalysisChartType effectiveRowType(RowDefinition row) {
        if (row.getChartType() == null || row.getChartType() == AnalysisChartType.DEFAULT) return type;
        return row.getChartType();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public AnalysisChartType getType() {
        return type;
    }

    public void setType(AnalysisChartType type) {
        this.type = type;
    }

    public int getMinFractionDigits() {
        return minFractionDigits;
    }

    public void setMinFractionDigits(int minFractionDigits) {
        this.minFractionDigits = minFractionDigits;
    }

    public int getMaxFractionDigits() {
        return maxFractionDigits;
    }

    public void setMaxFractionDigits(int maxFractionDigits) {
        this.maxFractionDigits = maxFractionDigits;
    }

    public Boolean getFractionDigitsAuto() {
        return fractionDigitsAuto;
    }

    public void setFractionDigitsAuto(Boolean fractionDigitsAuto) {
        this.fractionDigitsAuto = fractionDigitsAuto;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    /**
     * @return {@code "HORIZONTAL"} or {@code "VERTICAL"}
     */
    public String getOrientation() {
        return orientation;
    }

    public void setOrientation(String orientation) {
        this.orientation = orientation;
    }

    public boolean isFixYAxisToZero() {
        return fixYAxisToZero;
    }

    public void setFixYAxisToZero(boolean fixYAxisToZero) {
        this.fixYAxisToZero = fixYAxisToZero;
    }

    public String getXAxisTitle() {
        return xAxisTitle;
    }

    public void setXAxisTitle(String xAxisTitle) {
        this.xAxisTitle = xAxisTitle;
    }

    public String getYAxisTitle() {
        return yAxisTitle;
    }

    public void setYAxisTitle(String yAxisTitle) {
        this.yAxisTitle = yAxisTitle;
    }

    public double getGroupingInterval() {
        return groupingInterval;
    }

    public void setGroupingInterval(double groupingInterval) {
        this.groupingInterval = groupingInterval;
    }

    /**
     * @return the name of the heat map color mapping ({@code eu.hansolo.fx.charts.tools.ColorMapping}), e.g. {@code "GREEN_YELLOW_RED"}
     */
    public String getColorMapping() {
        return colorMapping;
    }

    public void setColorMapping(String colorMapping) {
        this.colorMapping = colorMapping;
    }

    public boolean isFilterEnabled() {
        return filterEnabled;
    }

    public void setFilterEnabled(boolean filterEnabled) {
        this.filterEnabled = filterEnabled;
    }

    public boolean isColoringEnabled() {
        return coloringEnabled;
    }

    public void setColoringEnabled(boolean coloringEnabled) {
        this.coloringEnabled = coloringEnabled;
    }

    public boolean isShowColumnSums() {
        return showColumnSums;
    }

    public void setShowColumnSums(boolean showColumnSums) {
        this.showColumnSums = showColumnSums;
    }

    public boolean isShowRowSums() {
        return showRowSums;
    }

    public void setShowRowSums(boolean showRowSums) {
        this.showRowSums = showRowSums;
    }

    /**
     * @return start of day of a heat map as stored (Joda {@code LocalTime.toString()}, e.g. {@code "06:00:00.000"}) or {@code null}
     */
    public String getDayStart() {
        return dayStart;
    }

    public void setDayStart(String dayStart) {
        this.dayStart = dayStart;
    }

    public String getDayEnd() {
        return dayEnd;
    }

    public void setDayEnd(String dayEnd) {
        this.dayEnd = dayEnd;
    }

    public Map<String, String> getFilterTexts() {
        return filterTexts;
    }

    public void setFilterTexts(Map<String, String> filterTexts) {
        this.filterTexts = filterTexts != null ? filterTexts : new LinkedHashMap<>();
    }

    public List<RowDefinition> getRows() {
        return rows;
    }

    public void setRows(List<RowDefinition> rows) {
        this.rows = rows;
    }
}
