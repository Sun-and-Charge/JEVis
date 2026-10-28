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

public enum Weekday {

    MONDAY(I18n.getInstance().getString("graph.datehelper.weekday.monday")), TUESDAY(I18n.getInstance().getString("graph.datehelper.weekday.tuesday")),
    WEDNESDAY(I18n.getInstance().getString("graph.datehelper.weekday.wednesday")), THURSDAY(I18n.getInstance().getString("graph.datehelper.weekday.thursday")),
    FRIDAY(I18n.getInstance().getString("graph.datehelper.weekday.friday")), SATURDAY(I18n.getInstance().getString("graph.datehelper.weekday.saturday")),
    SUNDAY(I18n.getInstance().getString("graph.datehelper.weekday.sunday"));

    private final String weekdayName;

    Weekday(String weekdayName) {
        this.weekdayName = weekdayName;
    }

    public String getWeekdayName() {
        return weekdayName;
    }
}