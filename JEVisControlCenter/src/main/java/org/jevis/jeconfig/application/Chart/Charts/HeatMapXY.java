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
package org.jevis.jeconfig.application.Chart.Charts;

import org.jevis.commons.dataprocessing.AggregationPeriod;

public class HeatMapXY {
    private Long X;
    private String X_FORMAT;
    private Long Y;
    private String Y_FORMAT;
    private String Y2_FORMAT;
    private AggregationPeriod aggregationPeriod;

    public HeatMapXY(Long x, String x_Format, Long y, String y_FORMAT, String y2_FORMAT, AggregationPeriod aggregationPeriod) {
        this.X = x;
        this.X_FORMAT = x_Format;
        this.Y = y;
        this.Y_FORMAT = y_FORMAT;
        this.Y2_FORMAT = y2_FORMAT;
        this.aggregationPeriod = aggregationPeriod;
    }

    public Long getX() {
        return X;
    }

    public String getX_FORMAT() {
        return X_FORMAT;
    }

    public Long getY() {
        return Y;
    }

    public String getY_FORMAT() {
        return Y_FORMAT;
    }

    public String getY2_FORMAT() {
        return Y2_FORMAT;
    }

    public AggregationPeriod getAggregationPeriod() {
        return aggregationPeriod;
    }
}
