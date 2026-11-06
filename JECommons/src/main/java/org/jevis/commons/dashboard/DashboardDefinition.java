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

import java.util.ArrayList;
import java.util.List;

/**
 * A dashboard as stored by the Control Center (attribute "Data Model File" of a "Dashboard Analysis", see
 * {@code ConfigManager.loadDashboard} and {@code DashboardPojo}), normalised for the web app: colors are CSS colors,
 * the zoom is a name or a factor, the widgets are sorted by layer.
 */
public class DashboardDefinition {
    /**
     * Zoom values of the Control Center ({@code DashboardControl.fitToScreen/fitToWidth/fitToHeight}).
     */
    public static final double FIT_SCREEN = 99d;
    public static final double FIT_WIDTH = 98d;
    public static final double FIT_HEIGHT = 97d;

    private Long id;
    private String version = "1.0";
    private double width = 1863d;
    private double height = 886d;
    private double zoom = 1d;
    private String backgroundColor = "#f4f4f4";
    private String backgroundMode = "DEFAULT";
    private boolean backgroundImage = false;
    private int updateRate = 900;
    private String timeFrame = DashboardTimeFrames.DAY;
    private List<WidgetDefinition> widgets = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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

    /**
     * @return the stored zoom: a factor or {@link #FIT_SCREEN}, {@link #FIT_WIDTH}, {@link #FIT_HEIGHT}
     */
    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    /**
     * @return {@code FIT_SCREEN}, {@code FIT_WIDTH}, {@code FIT_HEIGHT} or the factor as number
     */
    public Object getZoomValue() {
        if (zoom == FIT_SCREEN) return "FIT_SCREEN";
        if (zoom == FIT_WIDTH) return "FIT_WIDTH";
        if (zoom == FIT_HEIGHT) return "FIT_HEIGHT";
        return zoom;
    }

    /**
     * @return the background color as CSS color
     */
    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    /**
     * @return {@code DEFAULT}, {@code REPEAT} or {@code STRETCH}
     */
    public String getBackgroundMode() {
        return backgroundMode;
    }

    public void setBackgroundMode(String backgroundMode) {
        this.backgroundMode = backgroundMode;
    }

    /**
     * @return {@code true} if the dashboard object has a background image (attribute "Background")
     */
    public boolean isBackgroundImage() {
        return backgroundImage;
    }

    public void setBackgroundImage(boolean backgroundImage) {
        this.backgroundImage = backgroundImage;
    }

    /**
     * @return the update rate in seconds
     */
    public int getUpdateRate() {
        return updateRate;
    }

    public void setUpdateRate(int updateRate) {
        this.updateRate = updateRate;
    }

    /**
     * @return the id of the time frame of the dashboard ("Data Period"), see {@link DashboardTimeFrames}
     */
    public String getTimeFrame() {
        return timeFrame;
    }

    public void setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
    }

    /**
     * @return the widgets sorted by layer (then by their order in the file)
     */
    public List<WidgetDefinition> getWidgets() {
        return widgets;
    }

    public void setWidgets(List<WidgetDefinition> widgets) {
        this.widgets = widgets;
    }

    /**
     * @return the widget with this uuid or {@code null}
     */
    public WidgetDefinition findWidget(int uuid) {
        for (WidgetDefinition widget : widgets) {
            if (widget.getUuid() == uuid) return widget;
        }
        return null;
    }
}
