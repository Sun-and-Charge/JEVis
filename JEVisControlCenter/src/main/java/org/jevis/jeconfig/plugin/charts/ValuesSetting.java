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
package org.jevis.jeconfig.plugin.charts;

import org.jevis.jeconfig.sample.DaySchedule;
import org.joda.time.DateTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class ValuesSetting {
    private DateTime resultStart;
    private DateTime resultEnd;

    private boolean inside;

    private Map<Integer, DaySchedule> dayScheduleMap = new HashMap<>();

    public ValuesSetting() {
    }

    public void setResultStart(LocalDate resultStartDate, LocalTime resultStartTime) {
        this.resultStart = new DateTime(resultStartDate.getYear(), resultStartDate.getMonthValue(), resultStartDate.getDayOfMonth(),
                resultStartTime.getHour(), resultStartTime.getMinute(), resultStartTime.getSecond(), 0);
    }

    public void setResultEnd(LocalDate resultEndDate, LocalTime resultEndTime) {
        this.resultEnd = new DateTime(resultEndDate.getYear(), resultEndDate.getMonthValue(), resultEndDate.getDayOfMonth(),
                resultEndTime.getHour(), resultEndTime.getMinute(), resultEndTime.getSecond(), 999);
    }

    public DateTime getResultStart() {
        return resultStart;
    }

    public DateTime getResultEnd() {
        return resultEnd;
    }

    public Map<Integer, DaySchedule> getDaySchedule() {
        return dayScheduleMap;
    }

    public void setDaySchedule(Map<Integer, DaySchedule> dayScheduleMap) {
        this.dayScheduleMap = dayScheduleMap;
    }

    public boolean isInside() {
        return inside;
    }

    public void setInside(boolean inside) {
        this.inside = inside;
    }
}
