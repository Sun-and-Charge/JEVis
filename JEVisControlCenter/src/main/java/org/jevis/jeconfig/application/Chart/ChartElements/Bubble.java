/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.application.Chart.ChartElements;

import org.jevis.api.JEVisSample;

import java.util.List;

public class Bubble {
    private Double x;
    private Double y;
    private Double size;
    private final List<Boolean> visibleSamples;

    private final List<JEVisSample> xSamples;
    private final List<JEVisSample> ySamples;

    public Bubble(List<JEVisSample> xSamples, List<JEVisSample> ySamples, Double x, Double y, Double size, List<Boolean> visibleSamples) {
        this.xSamples = xSamples;
        this.ySamples = ySamples;
        this.visibleSamples = visibleSamples;
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public Double getX() {
        return x;
    }

    public void setX(Double x) {
        this.x = x;
    }

    public Double getY() {
        return y;
    }

    public void setY(Double y) {
        this.y = y;
    }

    public Double getSize() {
        return size;
    }

    public void setSize(Double size) {
        this.size = size;
    }

    public List<Boolean> getVisibleSamples() {
        return visibleSamples;
    }

    public List<JEVisSample> getXSamples() {
        return xSamples;
    }

    public List<JEVisSample> getYSamples() {
        return ySamples;
    }
}
