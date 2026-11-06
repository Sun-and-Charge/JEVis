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
 * Time frames of an analysis that can be resolved without user interaction, names as the Control Center's
 * {@code TimeFrame}. See {@link TimeFrameResolver}.
 */
public enum TimeFrame {
    CUSTOM, TODAY, YESTERDAY, LAST_7_DAYS, THIS_WEEK, LAST_WEEK, LAST_30_DAYS, THIS_MONTH, LAST_MONTH, THIS_YEAR,
    LAST_YEAR, THE_YEAR_BEFORE_LAST;

    /**
     * @return the time frame with this name or {@code null}
     */
    public static TimeFrame parse(String name) {
        if (name == null) return null;
        for (TimeFrame timeFrame : values()) {
            if (timeFrame.name().equals(name)) return timeFrame;
        }
        return null;
    }
}
