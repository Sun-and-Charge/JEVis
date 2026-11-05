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
package org.jevis.jeconfig.application.Chart.data;

import com.fasterxml.jackson.annotation.JsonIgnore;
import eu.hansolo.fx.charts.tools.ColorMapping;
import javafx.beans.property.*;
import javafx.geometry.Orientation;
import org.jevis.commons.object.ValueFormat;
import org.jevis.jeconfig.application.Chart.ChartType;
import org.joda.time.LocalTime;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for a single chart within an analysis.
 * <p>
 * A {@code ChartModel} carries all settings that are persisted per chart:
 * <ul>
 *   <li>Identity — {@code chartId}, {@code chartName}</li>
 *   <li>Rendering — {@code chartType}, {@code orientation}, {@code colorMapping},
 *       {@code height}</li>
 *   <li>Numeric formatting — {@code minFractionDigits}, {@code maxFractionDigits},
 *       {@code fractionDigitsAuto}, {@code groupingInterval}</li>
 *   <li>Flags — {@code filterEnabled}, {@code fixYAxisToZero}, {@code coloringEnabled},
 *       {@code showColumnSums}, {@code showRowSums}</li>
 *   <li>Heat-map time window — {@code dayStart}, {@code dayEnd}</li>
 *   <li>Axis labels — {@code xAxisTitle}, {@code yAxisTitle}</li>
 * </ul>
 * Each field is backed by a JavaFX property so that UI components can bind to it directly.
 * The model is serialised to JSON by {@link AnalysisHandler} and deserialised via Jackson's
 * {@code readerForUpdating}.
 * <p>
 * Decimal places of the values of a data row (upstream issue #1194), see {@link #getNumberFormat(ChartData)}:
 * <ol>
 *   <li>the decimal digits of the row in this chart ({@link ChartData#getDecimalDigits()}), if set</li>
 *   <li>the fraction digits of the chart, if they are explicit ({@link #isAutoFractionDigits()} is false)</li>
 *   <li>otherwise the "Decimal Places" of the data row ({@link ChartDataRow#dataRowDecimals()})</li>
 * </ol>
 * The fraction digits of a chart are explicit when {@code fractionDigitsAuto} is false. Analyses saved before this
 * setting existed have none; their fraction digits count as explicit only if they differ from the default 2/2.
 *
 * @see DataModel
 * @see ChartData
 */
public class ChartModel {

    public static final int DEFAULT_FRACTION_DIGITS = 2;

    private final SimpleIntegerProperty chartId = new SimpleIntegerProperty(this, "chartId", -1);
    private final SimpleStringProperty chartName = new SimpleStringProperty(this, "chartName", "");
    private final SimpleObjectProperty<ChartType> chartType = new SimpleObjectProperty<>(this, "chartType", ChartType.LINE);
    private final SimpleDoubleProperty height = new SimpleDoubleProperty(this, "height", -1);
    private final SimpleObjectProperty<ColorMapping> colorMapping = new SimpleObjectProperty<>(this, "colorMapping", ColorMapping.GREEN_YELLOW_RED);
    private final SimpleDoubleProperty groupingInterval = new SimpleDoubleProperty(this, "groupingInterval", 30);
    private final SimpleObjectProperty<Orientation> orientation = new SimpleObjectProperty<>(this, "orientation", Orientation.HORIZONTAL);
    private final SimpleIntegerProperty minFractionDigits = new SimpleIntegerProperty(this, "minFractionDigits", 2);
    private final SimpleIntegerProperty maxFractionDigits = new SimpleIntegerProperty(this, "maxFractionDigits", 2);
    /**
     * true: the decimal places of the data rows are used, false: the fraction digits of the chart, null: not set
     */
    private final SimpleObjectProperty<Boolean> fractionDigitsAuto = new SimpleObjectProperty<>(this, "fractionDigitsAuto", null);
    private final SimpleBooleanProperty filterEnabled = new SimpleBooleanProperty(this, "filterEnabled", false);
    private final SimpleBooleanProperty fixYAxisToZero = new SimpleBooleanProperty(this, "fixYAxisToZero", false);
    private final SimpleBooleanProperty coloringEnabled = new SimpleBooleanProperty(this, "coloringEnabled", false);
    private final SimpleBooleanProperty showColumnSums = new SimpleBooleanProperty(this, "showColumnSums", false);
    private final SimpleBooleanProperty showRowSums = new SimpleBooleanProperty(this, "showRowSums", false);
    private final SimpleObjectProperty<LocalTime> dayStart = new SimpleObjectProperty<>(this, "dayStart", null);
    private final SimpleObjectProperty<LocalTime> dayEnd = new SimpleObjectProperty<>(this, "dayEnd", null);
    private final StringProperty xAxisTitle = new SimpleStringProperty(this, "xAxisTitle", null);
    private final StringProperty yAxisTitle = new SimpleStringProperty(this, "yAxisTitle", null);

    private List<ChartData> chartData = new ArrayList<>();
    private Map<String, String> filterTexts = new HashMap<>();
    private boolean customWorkDay = true;

    public List<ChartData> getChartData() {
        return chartData;
    }

    public void setChartData(List<ChartData> chartData) {
        this.chartData = chartData;
    }

    public int getChartId() {
        return chartId.get();
    }

    public void setChartId(int chartId) {
        this.chartId.set(chartId);
    }

    public SimpleIntegerProperty chartIdProperty() {
        return chartId;
    }

    public String getChartName() {
        return chartName.get();
    }

    public void setChartName(String chartName) {
        this.chartName.set(chartName);
    }

    public SimpleStringProperty chartNameProperty() {
        return chartName;
    }

    public ChartType getChartType() {
        return chartType.get();
    }

    public void setChartType(ChartType chartType) {
        this.chartType.set(chartType);
    }

    public SimpleObjectProperty<ChartType> chartTypeProperty() {
        return chartType;
    }

    public double getHeight() {
        return height.get();
    }

    public void setHeight(double height) {
        this.height.set(height);
    }

    public SimpleDoubleProperty heightProperty() {
        return height;
    }

    public ColorMapping getColorMapping() {
        return colorMapping.get();
    }

    public void setColorMapping(ColorMapping colorMapping) {
        this.colorMapping.set(colorMapping);
    }

    public SimpleObjectProperty<ColorMapping> colorMappingProperty() {
        return colorMapping;
    }

    public double getGroupingInterval() {
        return groupingInterval.get();
    }

    public void setGroupingInterval(double groupingInterval) {
        this.groupingInterval.set(groupingInterval);
    }

    public SimpleDoubleProperty groupingIntervalProperty() {
        return groupingInterval;
    }

    public Orientation getOrientation() {
        return orientation.get();
    }

    public void setOrientation(Orientation orientation) {
        this.orientation.set(orientation);
    }

    public SimpleObjectProperty<Orientation> orientationProperty() {
        return orientation;
    }

    public int getMinFractionDigits() {
        return minFractionDigits.get();
    }

    public void setMinFractionDigits(int minFractionDigits) {
        this.minFractionDigits.set(minFractionDigits);
    }

    public SimpleIntegerProperty minFractionDigitsProperty() {
        return minFractionDigits;
    }

    public int getMaxFractionDigits() {
        return maxFractionDigits.get();
    }

    public void setMaxFractionDigits(int maxFractionDigits) {
        this.maxFractionDigits.set(maxFractionDigits);
    }

    public SimpleIntegerProperty maxFractionDigitsProperty() {
        return maxFractionDigits;
    }

    public Boolean getFractionDigitsAuto() {
        return fractionDigitsAuto.get();
    }

    public void setFractionDigitsAuto(Boolean fractionDigitsAuto) {
        this.fractionDigitsAuto.set(fractionDigitsAuto);
    }

    public SimpleObjectProperty<Boolean> fractionDigitsAutoProperty() {
        return fractionDigitsAuto;
    }

    /**
     * @return true if the values of each data row are shown with the decimal places of the data row instead of the
     * fraction digits of the chart; without a stored setting, only charts with the default fraction digits 2/2 do so
     */
    @JsonIgnore
    public boolean isAutoFractionDigits() {
        Boolean auto = fractionDigitsAuto.get();
        if (auto != null) {
            return auto;
        }
        return getMinFractionDigits() == DEFAULT_FRACTION_DIGITS && getMaxFractionDigits() == DEFAULT_FRACTION_DIGITS;
    }

    /**
     * @return the format for the values of a data row in this chart, see the class description
     */
    @JsonIgnore
    public NumberFormat getNumberFormat(ChartData row) {
        if (row != null && row.getDecimalDigits() > -1) {
            return ValueFormat.numberFormat(row.getDecimalDigits());
        }
        if (row instanceof ChartDataRow && isAutoFractionDigits()) {
            return ValueFormat.numberFormat(((ChartDataRow) row).dataRowDecimals());
        }
        NumberFormat nf = ValueFormat.numberFormat(getMaxFractionDigits());
        nf.setMinimumFractionDigits(Math.min(getMinFractionDigits(), getMaxFractionDigits()));
        return nf;
    }

    /**
     * Sets the fraction digits for the values of a data row in this chart (see {@link #getNumberFormat(ChartData)})
     * on a number format of the charts.
     */
    public void applyFractionDigits(com.ibm.icu.text.NumberFormat nf, ChartData row) {
        NumberFormat format = getNumberFormat(row);
        nf.setMinimumFractionDigits(format.getMinimumFractionDigits());
        nf.setMaximumFractionDigits(format.getMaximumFractionDigits());
    }

    public boolean isFilterEnabled() {
        return filterEnabled.get();
    }

    public void setFilterEnabled(boolean filterEnabled) {
        this.filterEnabled.set(filterEnabled);
    }

    public SimpleBooleanProperty filterEnabledProperty() {
        return filterEnabled;
    }

    public boolean isFixYAxisToZero() {
        return fixYAxisToZero.get();
    }

    public void setFixYAxisToZero(boolean fixYAxisToZero) {
        this.fixYAxisToZero.set(fixYAxisToZero);
    }

    public SimpleBooleanProperty fixYAxisToZeroProperty() {
        return fixYAxisToZero;
    }

    public boolean isShowColumnSums() {
        return showColumnSums.get();
    }

    public void setShowColumnSums(boolean showColumnSums) {
        this.showColumnSums.set(showColumnSums);
    }

    public SimpleBooleanProperty showColumnSumsProperty() {
        return showColumnSums;
    }

    public boolean isShowRowSums() {
        return showRowSums.get();
    }

    public void setShowRowSums(boolean showRowSums) {
        this.showRowSums.set(showRowSums);
    }

    public SimpleBooleanProperty showRowSumsProperty() {
        return showRowSums;
    }

    public LocalTime getDayStart() {
        return dayStart.get();
    }

    public void setDayStart(LocalTime dayStart) {
        this.dayStart.set(dayStart);
    }

    public SimpleObjectProperty<LocalTime> dayStartProperty() {
        return dayStart;
    }

    public LocalTime getDayEnd() {
        return dayEnd.get();
    }

    public void setDayEnd(LocalTime dayEnd) {
        this.dayEnd.set(dayEnd);
    }

    public SimpleObjectProperty<LocalTime> dayEndProperty() {
        return dayEnd;
    }

    public String getxAxisTitle() {
        return xAxisTitle.get();
    }

    public void setxAxisTitle(String title) {
        this.xAxisTitle.set(title);
    }

    public StringProperty xAxisTitleProperty() {
        return xAxisTitle;
    }

    public String getyAxisTitle() {
        return yAxisTitle.get();
    }

    public void setyAxisTitle(String title) {
        this.yAxisTitle.set(title);
    }

    public StringProperty yAxisTitleProperty() {
        return yAxisTitle;
    }

    public boolean isColoringEnabled() {
        return coloringEnabled.get();
    }

    public void setColoringEnabled(Boolean coloringEnabled) {
        this.coloringEnabled.set(coloringEnabled);
    }

    public SimpleBooleanProperty coloringEnabledProperty() {
        return coloringEnabled;
    }

    /**
     * Filter texts of a vertical table chart, keyed by column title.
     */
    public Map<String, String> getFilterTexts() {
        return filterTexts;
    }

    public void setFilterTexts(Map<String, String> filterTexts) {
        this.filterTexts = filterTexts != null ? filterTexts : new HashMap<>();
    }

    /**
     * Runtime state of the custom workday toolbar toggle, not persisted with the analysis.
     */
    @JsonIgnore
    public boolean isCustomWorkDay() {
        return customWorkDay;
    }

    @JsonIgnore
    public void setCustomWorkDay(boolean customWorkDay) {
        this.customWorkDay = customWorkDay;
    }
}
