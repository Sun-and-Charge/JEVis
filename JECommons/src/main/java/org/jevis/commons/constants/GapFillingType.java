/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.constants;

public enum GapFillingType {
    NONE,
    STATIC,
    INTERPOLATION,
    DEFAULT_VALUE,
    MINIMUM,
    MAXIMUM,
    MEDIAN,
    AVERAGE,
    DELETE,
    /**
     * Linear regression over the reference samples (forecast only).
     */
    REGRESSION;

    public static GapFillingType parse(String type) {
        if (type != null) {
            switch (type.toUpperCase()) {
                case ("STATIC"):
                    return STATIC;
                case ("INTERPOLATION"):
                    return INTERPOLATION;
                case ("DEFAULT VALUE"):
                case ("DEFAULT_VALUE"):
                    return DEFAULT_VALUE;
                case ("MINIMUM"):
                    return MINIMUM;
                case ("MAXIMUM"):
                    return MAXIMUM;
                case ("MEDIAN"):
                    return MEDIAN;
                case ("AVERAGE"):
                    return AVERAGE;
                case ("DELETE"):
                    return DELETE;
                case ("REGRESSION"):
                    return REGRESSION;
                case ("NONE"):
                default:
                    return NONE;
            }
        } else {
            return NONE;
        }
    }
}
