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

package org.jevis.commons.datatype.scheduler;

import org.jevis.commons.datatype.scheduler.cron.CronTime;
import org.joda.time.DateTime;

import java.security.InvalidParameterException;
import java.time.DayOfWeek;
import java.time.Month;

public interface SchedulerRule {
    void setDayOfWeek(DayOfWeek var1, boolean var2);

    boolean isDayOfWeekEnabled(DayOfWeek var1);

    String getDayOfWeekArray();

    void setMonth(Month var1, boolean var2);

    void setAllMonths(boolean var1);

    boolean isMonthEnabled(Month var1);

    String getMonthArray();

    void setDayOfMonths(String var1) throws InvalidParameterException;

    boolean isDayOfMonthEnabled(DateTime var1);

    String getDayOfMonth() throws InvalidParameterException;

    CronTime getStartTime();

    CronTime getEndTime();

    String getStartHour();

    void setStartHour(String var1);

    String getEndHour();

    void setEndHour(String var1);

    String getStartMinute();

    void setStartMinute(String var1);

    String getEndMinute();

    void setEndMinute(String var1);
}
