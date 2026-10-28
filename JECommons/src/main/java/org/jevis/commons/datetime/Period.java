/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.datetime;

public enum Period {
    MINUTELY, QUARTER_HOURLY, HOURLY, DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, CUSTOM, CUSTOM2, NONE;

    public static Period parsePeriod(String aggregation) {
        switch (aggregation) {
            case "Minute":
            case "MINUTE":
            case "Minutely":
            case "MINUTELY":
                return MINUTELY;
            case "Quarter Hour":
            case "Quarter Hourly":
            case "Quarterly Hour":
            case "Quarterly Hourly":
            case "QUARTER_HOUR":
            case "QUARTER_HOURLY":
            case "QUARTERLY_HOURLY":
                return QUARTER_HOURLY;
            case "HOUR":
            case "HOURLY":
            case "Hour":
            case "Hourly":
                return HOURLY;
            case "DAY":
            case "DAILY":
            case "Day":
            case "Daily":
                return DAILY;
            case "WEEK":
            case "WEEKLY":
            case "Week":
            case "Weekly":
                return WEEKLY;
            case "MONTH":
            case "MONTHLY":
            case "Month":
            case "Monthly":
                return MONTHLY;
            case "QUARTER":
            case "QUARTERLY":
            case "Quarter":
            case "Quarterly":
                return QUARTERLY;
            case "YEAR":
            case "YEARLY":
            case "Year":
            case "Yearly":
                return YEARLY;
            case "CUSTOM":
            case "Custom":
                return CUSTOM;
            case "CUSTOM2":
            case "Custom2":
                return CUSTOM2;
        }
        return NONE;
    }
}