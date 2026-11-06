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

import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;

import java.util.ArrayList;
import java.util.List;

/**
 * A stored analysis (Control Center: {@code DataModel}), read by {@link AnalysisReader} from either storage format.
 * Defaults are those of the Control Center ({@code DataModel.reset()}).
 */
public class AnalysisDefinition {
    /**
     * Time frame the Control Center uses when an analysis is opened; analyses do not store one that is used.
     */
    public static final TimeFrame DEFAULT_TIME_FRAME = TimeFrame.TODAY;
    public static final AggregationPeriod DEFAULT_AGGREGATION = AggregationPeriod.NONE;
    public static final ManipulationMode DEFAULT_MANIPULATION = ManipulationMode.NONE;

    private Long id;
    private Format format = Format.NONE;
    private boolean autoSize = true;
    private int chartsPerScreen = 2;
    private int horizontalPies = 3;
    private int horizontalTables = 3;
    private String forcedInterval = "";
    private String dayFilterWeekdays = "";
    private boolean dayFilterHolidays = false;
    private String dayFilterDates = "";
    private String legacyTimeFrame;
    private List<LimitLineDefinition> limitLines = new ArrayList<>();
    private List<ChartDefinition> charts = new ArrayList<>();

    /**
     * @return the day filter of the analysis, see {@link DayFilter}
     */
    public DayFilter createDayFilter() {
        return DayFilter.of(dayFilterWeekdays, dayFilterHolidays, dayFilterDates);
    }

    /**
     * @return the row with the given key or {@code null}
     */
    public RowDefinition findRow(String key) {
        for (ChartDefinition chart : charts) {
            for (RowDefinition row : chart.getRows()) {
                if (row.getKey() != null && row.getKey().equals(key)) return row;
            }
        }
        return null;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the storage format the analysis was read from
     */
    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public boolean isAutoSize() {
        return autoSize;
    }

    public void setAutoSize(boolean autoSize) {
        this.autoSize = autoSize;
    }

    public int getChartsPerScreen() {
        return chartsPerScreen;
    }

    public void setChartsPerScreen(int chartsPerScreen) {
        this.chartsPerScreen = chartsPerScreen;
    }

    public int getHorizontalPies() {
        return horizontalPies;
    }

    public void setHorizontalPies(int horizontalPies) {
        this.horizontalPies = horizontalPies;
    }

    public int getHorizontalTables() {
        return horizontalTables;
    }

    public void setHorizontalTables(int horizontalTables) {
        this.horizontalTables = horizontalTables;
    }

    public String getForcedInterval() {
        return forcedInterval;
    }

    public void setForcedInterval(String forcedInterval) {
        this.forcedInterval = forcedInterval == null ? "" : forcedInterval;
    }

    /**
     * @return excluded ISO weekdays as comma separated numbers (1 = Monday ... 7 = Sunday)
     */
    public String getDayFilterWeekdays() {
        return dayFilterWeekdays;
    }

    public void setDayFilterWeekdays(String dayFilterWeekdays) {
        this.dayFilterWeekdays = dayFilterWeekdays == null ? "" : dayFilterWeekdays;
    }

    public boolean isDayFilterHolidays() {
        return dayFilterHolidays;
    }

    public void setDayFilterHolidays(boolean dayFilterHolidays) {
        this.dayFilterHolidays = dayFilterHolidays;
    }

    /**
     * @return excluded single dates as comma separated ISO dates (yyyy-MM-dd)
     */
    public String getDayFilterDates() {
        return dayFilterDates;
    }

    public void setDayFilterDates(String dayFilterDates) {
        this.dayFilterDates = dayFilterDates == null ? "" : dayFilterDates;
    }

    /**
     * @return the time frame stored in the legacy chart settings (e.g. {@code "today"}, {@code "last7Days"}) or
     * {@code null}; informational only, the Control Center ignores it and opens every analysis with {@link #DEFAULT_TIME_FRAME}
     */
    public String getLegacyTimeFrame() {
        return legacyTimeFrame;
    }

    public void setLegacyTimeFrame(String legacyTimeFrame) {
        this.legacyTimeFrame = legacyTimeFrame;
    }

    public List<LimitLineDefinition> getLimitLines() {
        return limitLines;
    }

    public void setLimitLines(List<LimitLineDefinition> limitLines) {
        this.limitLines = limitLines == null ? new ArrayList<>() : limitLines;
    }

    /**
     * @return the charts in the order the Control Center shows them (sorted by name, alphanumeric)
     */
    public List<ChartDefinition> getCharts() {
        return charts;
    }

    public void setCharts(List<ChartDefinition> charts) {
        this.charts = charts;
    }

    public enum Format {
        /**
         * JSON file in the attribute "Analysis File"
         */
        ANALYSIS_FILE,
        /**
         * JSON in the attributes "Data Model" and "Charts"
         */
        LEGACY,
        /**
         * nothing stored
         */
        NONE
    }
}
