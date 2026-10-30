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
import javafx.scene.paint.Color;

/**
 * A user defined horizontal limit line of an analysis (upstream issues #2233 and #1036).
 * <p>
 * The line is drawn in every XY chart of the analysis as a dashed line with an optional label.
 * Optionally the area above or below the line is filled semi-transparently.
 * Persisted as part of the analysis JSON by {@link AnalysisHandler}.
 */
public class UserLimitLine {

    public static final String FILL_NONE = "NONE";
    public static final String FILL_ABOVE = "ABOVE";
    public static final String FILL_BELOW = "BELOW";

    private double value = 0d;
    private String label = "";
    private String color = "#FF0000";
    /**
     * 0 = left (y1) axis, 1 = right (y2) axis.
     */
    private int axis = 0;
    private String fill = FILL_NONE;

    public UserLimitLine() {
    }

    public UserLimitLine(UserLimitLine other) {
        this.value = other.value;
        this.label = other.label;
        this.color = other.color;
        this.axis = other.axis;
        this.fill = other.fill;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

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

    public String getFill() {
        return fill;
    }

    public void setFill(String fill) {
        this.fill = fill;
    }

    @JsonIgnore
    public Color getFxColor() {
        try {
            if (color != null && !color.isEmpty()) {
                return Color.web(color);
            }
        } catch (Exception ignored) {
        }
        return Color.RED;
    }

    @JsonIgnore
    public void setFxColor(Color fxColor) {
        if (fxColor == null) {
            this.color = "#FF0000";
        } else {
            this.color = String.format("#%02X%02X%02X",
                    (int) Math.round(fxColor.getRed() * 255),
                    (int) Math.round(fxColor.getGreen() * 255),
                    (int) Math.round(fxColor.getBlue() * 255));
        }
    }
}
