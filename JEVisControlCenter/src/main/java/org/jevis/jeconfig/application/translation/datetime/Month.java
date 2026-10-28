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
package org.jevis.jeconfig.application.translation.datetime;

import org.jevis.commons.i18n.I18n;

public enum Month {

    JANUARY(I18n.getInstance().getString("graph.datehelper.months.january")), FEBRUARY(I18n.getInstance().getString("graph.datehelper.months.february")),
    MARCH(I18n.getInstance().getString("graph.datehelper.months.march")), APRIL(I18n.getInstance().getString("graph.datehelper.months.april")),
    MAY(I18n.getInstance().getString("graph.datehelper.months.may")), JUNE(I18n.getInstance().getString("graph.datehelper.months.june")),
    JULY(I18n.getInstance().getString("graph.datehelper.months.july")), AUGUST(I18n.getInstance().getString("graph.datehelper.months.august")),
    SEPTEMBER(I18n.getInstance().getString("graph.datehelper.months.september")), OCTOBER(I18n.getInstance().getString("graph.datehelper.months.october")),
    NOVEMBER(I18n.getInstance().getString("graph.datehelper.months.november")), DECEMBER(I18n.getInstance().getString("graph.datehelper.months.december"));

    private final String monthName;

    Month(String monthName) {
        this.monthName = monthName;
    }

    public String getMonthName() {
        return monthName;
    }
}
