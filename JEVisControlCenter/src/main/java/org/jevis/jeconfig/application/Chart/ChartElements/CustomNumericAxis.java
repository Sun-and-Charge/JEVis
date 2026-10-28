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

import de.gsi.chart.axes.spi.AxisRange;
import de.gsi.chart.axes.spi.DefaultNumericAxis;

public class CustomNumericAxis extends DefaultNumericAxis {

    public CustomNumericAxis() {
        super();
    }


    @Override
    protected AxisRange autoRange(final double minValue, final double maxValue, final double length,
                                  final double labelSize) {
        double min = minValue > 0 && isForceZeroInRange() ? 0 : minValue;
        if (isLogAxis && minValue <= 0) {
            min = DefaultNumericAxis.DEFAULT_LOG_MIN_VALUE;
            isUpdating = true;
            // TODO: check w.r.t. inverted axis (lower <-> upper bound exchange)
            setMin(DefaultNumericAxis.DEFAULT_LOG_MIN_VALUE);
            isUpdating = false;
        }
        final double max = maxValue < 0 && isForceZeroInRange() ? 0 : maxValue;
        final double padding = DefaultNumericAxis.getEffectiveRange(min, max) * getAutoRangePadding();
        final double paddingScale = 1.0 + getAutoRangePadding();
        double paddedMin = isLogAxis ? minValue / paddingScale
                : DefaultNumericAxis.clampBoundToZero(min - padding, min);
        if (paddedMin < 0) paddedMin *= 1.10;
        final double paddedMax = isLogAxis ? maxValue * paddingScale
                : DefaultNumericAxis.clampBoundToZero(max + padding, max) * 1.10;

        return computeRange(paddedMin, paddedMax, length, labelSize);
    }
}
