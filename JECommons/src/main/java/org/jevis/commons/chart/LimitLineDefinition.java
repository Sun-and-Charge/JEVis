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
 * User defined horizontal limit line drawn in all XY charts of an analysis (Control Center: {@code UserLimitLine}).
 */
public class LimitLineDefinition {
    private double value = 0d;
    private String label = "";
    private String color = "#FF0000";
    private int axis = 0;
    private String fill = "NONE";

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

    /**
     * @return the color as stored (normally {@code "#RRGGBB"})
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

    /**
     * @return fill mode as stored ({@code NONE}, {@code ABOVE}, {@code BELOW})
     */
    public String getFill() {
        return fill;
    }

    public void setFill(String fill) {
        this.fill = fill;
    }
}
