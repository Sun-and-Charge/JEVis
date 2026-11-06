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
package org.jevis.commons.dashboard;

import org.jevis.commons.chart.AnalysisChartType;
import org.jevis.commons.chart.AnalysisDefinition;
import org.jevis.commons.chart.ChartDefinition;
import org.jevis.commons.chart.RowDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One widget of a dashboard (Control Center: {@code WidgetPojo} plus the type specific nodes and the data handler).
 * Defaults are those of {@code WidgetPojo}.
 */
public class WidgetDefinition {
    public static final String TYPE_TITLE = "Title";
    public static final String TYPE_VALUE = "Value";
    public static final String TYPE_STRING_VALUE = "String Value";
    public static final String TYPE_TABLE = "Table";
    public static final String TYPE_PIE = "Pie";
    public static final String TYPE_CHART = "Chart";
    public static final String TYPE_GAUGE = "Gauge";
    public static final String TYPE_LINEAR_GAUGE = "LinearGauge";
    public static final String TYPE_BATTERY = "Battery";
    public static final String TYPE_SHAPE = "Shape";
    public static final String TYPE_ARROW = "Arrow";
    public static final String TYPE_NET_GRAPH = "NetGraph";
    public static final String TYPE_SANKEY = "Sankey";
    public static final String TYPE_TIME_FRAME = "Time Frame";
    public static final String TYPE_ALARM_COUNT = "Alarm Count";
    public static final String TYPE_IMAGE = "Image";
    public static final String TYPE_ANALYSIS_LINK = "Analyse-Link";
    public static final String TYPE_DASHBOARD_LINK = "Dashboard-Link";
    public static final String TYPE_REPORT_LINK = "Report-Link";
    public static final String TYPE_VALUE_EDITOR = "Value Editor";
    public static final String TYPE_TOGGLE_SWITCH = "Toggle Switch";
    public static final String TYPE_SLIDER = "Slider";
    public static final String TYPE_PLUS_MINUS = "PlusMinus";

    private int uuid;
    private String type = "";
    private double x = 100d;
    private double y = 100d;
    private double width = 250d;
    private double height = 150d;
    private int layer = 1;
    private String title = "Title";
    private String tooltip = "";
    private String background = "#ffffff";
    private String fontColor = "#000000";
    private double fontSize = 13d;
    private String fontWeight = "NORMAL";
    private boolean italic = false;
    private boolean underline = false;
    private String titlePosition = "CENTER";
    private double borderSize = 0.2d;
    private String borderColor = "#000000";
    private double cornerRadius = 0d;
    private boolean shadow = true;
    private int decimals = 2;
    private boolean decimalsAuto = true;
    private boolean showValue = true;
    private boolean fixedTimeFrame = false;
    private String timeFrame;
    private AnalysisDefinition data = new AnalysisDefinition();
    private Map<String, Object> config = new LinkedHashMap<>();

    /**
     * @return {@code true} for the types whose value(s) the Control Center shows in absolute mode (one value per row)
     */
    public static boolean isValueType(String type) {
        return TYPE_VALUE.equals(type) || TYPE_TABLE.equals(type) || TYPE_PIE.equals(type) || TYPE_GAUGE.equals(type)
                || TYPE_LINEAR_GAUGE.equals(type) || TYPE_BATTERY.equals(type) || TYPE_SHAPE.equals(type)
                || TYPE_NET_GRAPH.equals(type) || TYPE_SANKEY.equals(type) || TYPE_TIME_FRAME.equals(type);
    }

    /**
     * @return {@code true} for the widgets that write values in the Control Center (shown read-only in the web)
     */
    public static boolean isInputType(String type) {
        return TYPE_VALUE_EDITOR.equals(type) || TYPE_TOGGLE_SWITCH.equals(type) || TYPE_SLIDER.equals(type)
                || TYPE_PLUS_MINUS.equals(type);
    }

    /**
     * @return all data rows of the widget (of all chart models of its data handler, in their order)
     */
    public List<RowDefinition> getRows() {
        List<RowDefinition> rows = new ArrayList<>();
        if (data != null) {
            for (ChartDefinition chart : data.getCharts()) rows.addAll(chart.getRows());
        }
        return rows;
    }

    /**
     * @return the first chart model of the data handler (the one the Control Center's widgets show), or {@code null}
     */
    public ChartDefinition getChart() {
        return data != null && !data.getCharts().isEmpty() ? data.getCharts().get(0) : null;
    }

    /**
     * @return the chart type of the first chart model (Control Center default {@code LINE})
     */
    public AnalysisChartType getChartType() {
        ChartDefinition chart = getChart();
        return chart != null && chart.getType() != null ? chart.getType() : AnalysisChartType.LINE;
    }

    public int getUuid() {
        return uuid;
    }

    public void setUuid(int uuid) {
        this.uuid = uuid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    public int getLayer() {
        return layer;
    }

    public void setLayer(int layer) {
        this.layer = layer;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }

    /**
     * @return the background color (CSS)
     */
    public String getBackground() {
        return background;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public String getFontColor() {
        return fontColor;
    }

    public void setFontColor(String fontColor) {
        this.fontColor = fontColor;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    /**
     * @return a JavaFX {@code FontWeight} name, e.g. {@code NORMAL}, {@code BOLD}
     */
    public String getFontWeight() {
        return fontWeight;
    }

    public void setFontWeight(String fontWeight) {
        this.fontWeight = fontWeight;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    /**
     * @return a JavaFX {@code Pos} name
     */
    public String getTitlePosition() {
        return titlePosition;
    }

    public void setTitlePosition(String titlePosition) {
        this.titlePosition = titlePosition;
    }

    public double getBorderSize() {
        return borderSize;
    }

    public void setBorderSize(double borderSize) {
        this.borderSize = borderSize;
    }

    public String getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(String borderColor) {
        this.borderColor = borderColor;
    }

    public double getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(double cornerRadius) {
        this.cornerRadius = cornerRadius;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public int getDecimals() {
        return decimals;
    }

    public void setDecimals(int decimals) {
        this.decimals = decimals;
    }

    /**
     * @return {@code true} if the decimal places of the data rows are used ({@code WidgetPojo.isDecimalsAuto()})
     */
    public boolean isDecimalsAuto() {
        return decimalsAuto;
    }

    public void setDecimalsAuto(boolean decimalsAuto) {
        this.decimalsAuto = decimalsAuto;
    }

    public boolean isShowValue() {
        return showValue;
    }

    public void setShowValue(boolean showValue) {
        this.showValue = showValue;
    }

    /**
     * @return the "fixed time frame" switch of the widget: custom periods are resolved for now instead of the date of
     * the dashboard
     */
    public boolean isFixedTimeFrame() {
        return fixedTimeFrame;
    }

    public void setFixedTimeFrame(boolean fixedTimeFrame) {
        this.fixedTimeFrame = fixedTimeFrame;
    }

    /**
     * @return the own time frame of the widget ({@code forcedInterval} of its data handler), {@code null} for the
     * time frame of the dashboard
     */
    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    /**
     * @return the data handler as analysis (chart models with their rows); never {@code null}
     */
    public AnalysisDefinition getData() {
        return data;
    }

    public void setData(AnalysisDefinition data) {
        this.data = data != null ? data : new AnalysisDefinition();
    }

    /**
     * @return the type specific configuration, normalised (see docs/WEB-API.md)
     */
    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }

    @Override
    public String toString() {
        return "WidgetDefinition{uuid=" + uuid + ", type='" + type + "'}";
    }
}
