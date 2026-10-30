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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level data model for a saved analysis in the ChartPlugin.
 * <p>
 * A {@code DataModel} aggregates all {@link ChartModel} instances that belong to one analysis
 * together with display-level settings that apply globally across charts:
 * <ul>
 *   <li>{@code autoSize} — whether charts should fill the available height automatically</li>
 *   <li>{@code chartsPerScreen} — how many charts are visible without scrolling</li>
 *   <li>{@code horizontalPies} / {@code horizontalTables} — columns in pie/table layouts</li>
 *   <li>{@code forcedInterval} — optional ISO-period string that overrides the time axis</li>
 *   <li>{@code dayFilterWeekdays} / {@code dayFilterHolidays} / {@code dayFilterDates} — day filter, see {@link DayFilter}</li>
 * </ul>
 * This class is serialised to/from JSON by {@link AnalysisHandler} and is also the target of
 * Jackson's {@code readerForUpdating} when loading a saved analysis file.
 *
 * @see ChartModel
 * @see AnalysisHandler
 */
public class DataModel {


    private final SimpleBooleanProperty autoSize = new SimpleBooleanProperty(this, "autoSize", true);
    private final SimpleIntegerProperty chartsPerScreen = new SimpleIntegerProperty(this, "chartsPerScreen", 2);
    private final SimpleIntegerProperty horizontalPies = new SimpleIntegerProperty(this, "horizontalPies", 3);
    private final SimpleIntegerProperty horizontalTables = new SimpleIntegerProperty(this, "horizontalTables", 3);
    private final SimpleStringProperty forcedInterval = new SimpleStringProperty(this, "forcedInterval", "");
    private final SimpleStringProperty dayFilterWeekdays = new SimpleStringProperty(this, "dayFilterWeekdays", "");
    private final SimpleBooleanProperty dayFilterHolidays = new SimpleBooleanProperty(this, "dayFilterHolidays", false);
    private final SimpleStringProperty dayFilterDates = new SimpleStringProperty(this, "dayFilterDates", "");
    private List<ChartModel> chartModels = new ArrayList<>();
    private List<UserLimitLine> limitLines = new ArrayList<>();

    /**
     * @return the ordered list of charts that make up this analysis
     */
    public List<ChartModel> getChartModels() {
        return chartModels;
    }

    public void setChartModels(List<ChartModel> chartModels) {
        this.chartModels = chartModels;
    }

    /**
     * @return user defined horizontal limit lines drawn in all XY charts of this analysis
     */
    public List<UserLimitLine> getLimitLines() {
        return limitLines;
    }

    public void setLimitLines(List<UserLimitLine> limitLines) {
        this.limitLines = limitLines == null ? new ArrayList<>() : limitLines;
    }

    public boolean isAutoSize() {
        return autoSize.get();
    }

    public void setAutoSize(boolean autoSize) {
        this.autoSize.set(autoSize);
    }

    public SimpleBooleanProperty autoSizeProperty() {
        return autoSize;
    }

    public int getChartsPerScreen() {
        return chartsPerScreen.get();
    }

    public void setChartsPerScreen(int chartsPerScreen) {
        this.chartsPerScreen.set(chartsPerScreen);
    }

    public SimpleIntegerProperty chartsPerScreenProperty() {
        return chartsPerScreen;
    }

    public int getHorizontalPies() {
        return horizontalPies.get();
    }

    public void setHorizontalPies(int horizontalPies) {
        this.horizontalPies.set(horizontalPies);
    }

    public SimpleIntegerProperty horizontalPiesProperty() {
        return horizontalPies;
    }

    public int getHorizontalTables() {
        return horizontalTables.get();
    }

    public void setHorizontalTables(int horizontalTables) {
        this.horizontalTables.set(horizontalTables);
    }

    public SimpleIntegerProperty horizontalTablesProperty() {
        return horizontalTables;
    }

    public String getForcedInterval() {
        return forcedInterval.get();
    }

    public void setForcedInterval(String forcedInterval) {
        this.forcedInterval.set(forcedInterval);
    }

    public SimpleStringProperty forcedIntervalProperty() {
        return forcedInterval;
    }

    /**
     * @return excluded ISO weekdays as comma separated numbers (1 = Monday ... 7 = Sunday)
     */
    public String getDayFilterWeekdays() {
        return dayFilterWeekdays.get();
    }

    public void setDayFilterWeekdays(String dayFilterWeekdays) {
        this.dayFilterWeekdays.set(dayFilterWeekdays == null ? "" : dayFilterWeekdays);
    }

    public SimpleStringProperty dayFilterWeekdaysProperty() {
        return dayFilterWeekdays;
    }

    public boolean isDayFilterHolidays() {
        return dayFilterHolidays.get();
    }

    public void setDayFilterHolidays(boolean dayFilterHolidays) {
        this.dayFilterHolidays.set(dayFilterHolidays);
    }

    public SimpleBooleanProperty dayFilterHolidaysProperty() {
        return dayFilterHolidays;
    }

    /**
     * @return excluded single dates as comma separated ISO dates (yyyy-MM-dd)
     */
    public String getDayFilterDates() {
        return dayFilterDates.get();
    }

    public void setDayFilterDates(String dayFilterDates) {
        this.dayFilterDates.set(dayFilterDates == null ? "" : dayFilterDates);
    }

    public SimpleStringProperty dayFilterDatesProperty() {
        return dayFilterDates;
    }

    /**
     * @return the day filter built from the current day filter settings
     */
    @JsonIgnore
    public DayFilter createDayFilter() {
        return DayFilter.of(getDayFilterWeekdays(), isDayFilterHolidays(), getDayFilterDates());
    }

    /**
     * Resets this model to its default state, clearing all chart models and
     * restoring all display settings to their defaults.
     * <p>Should be called before loading a new analysis to avoid stale state.</p>
     */
    public void reset() {
        autoSize.set(true);
        chartsPerScreen.set(2);
        horizontalPies.set(3);
        horizontalTables.set(3);
        forcedInterval.set("");
        dayFilterWeekdays.set("");
        dayFilterHolidays.set(false);
        dayFilterDates.set("");
        chartModels.clear();
        limitLines = new ArrayList<>();
    }
}
