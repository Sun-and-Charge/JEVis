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
package org.jevis.commons.driver.dwd;

public enum Aggregation {
    TEN_MINUTES("10_minutes"),
    ONE_MINUTE("1_minute"),
    FIVE_MINUTES("5_minutes"),
    ANNUAL("annual"),
    DAILY("daily"),
    HOURLY("hourly"),
    MONTHLY("monthly"),
    MULTI_ANNUAL("multi_annual"),
    SUBDAILY("subdaily");

    private final String value;

    Aggregation(String value) {
        this.value = value;
    }

    public static Aggregation getEnum(String value) {
        for (Aggregation v : values())
            if (v.getValue().equalsIgnoreCase(value)) return v;
        throw new IllegalArgumentException();
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return this.getValue();
    }
}
