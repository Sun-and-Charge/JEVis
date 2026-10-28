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

import org.joda.time.DateTime;

import java.time.LocalDate;
import java.time.LocalTime;

public class BaseLoadSetting {
    private DateTime baseLoadStart;
    private DateTime baseLoadEnd;
    private int repeatType = 0;
    private DateTime resultStart;
    private DateTime resultEnd;

    public BaseLoadSetting() {

    }

    public void setBaseLoadStart(LocalDate baseLoadStartDate, LocalTime baseLoadStartTime) {
        this.baseLoadStart = new DateTime(baseLoadStartDate.getYear(), baseLoadStartDate.getMonthValue(), baseLoadStartDate.getDayOfMonth(),
                baseLoadStartTime.getHour(), baseLoadStartTime.getMinute(), baseLoadStartTime.getSecond(), 0);
    }

    public void setBaseLoadEnd(LocalDate baseLoadEndDate, LocalTime baseLoadEndTime) {
        this.baseLoadEnd = new DateTime(baseLoadEndDate.getYear(), baseLoadEndDate.getMonthValue(), baseLoadEndDate.getDayOfMonth(),
                baseLoadEndTime.getHour(), baseLoadEndTime.getMinute(), baseLoadEndTime.getSecond(), 999);
    }

    public void setResultStart(LocalDate resultStartDate, LocalTime resultStartTime) {
        this.resultStart = new DateTime(resultStartDate.getYear(), resultStartDate.getMonthValue(), resultStartDate.getDayOfMonth(),
                resultStartTime.getHour(), resultStartTime.getMinute(), resultStartTime.getSecond(), 0);
    }

    public void setResultEnd(LocalDate resultEndDate, LocalTime resultEndTime) {
        this.resultEnd = new DateTime(resultEndDate.getYear(), resultEndDate.getMonthValue(), resultEndDate.getDayOfMonth(),
                resultEndTime.getHour(), resultEndTime.getMinute(), resultEndTime.getSecond(), 999);
    }

    public DateTime getBaseLoadStart() {
        return baseLoadStart;
    }

    public DateTime getBaseLoadEnd() {
        return baseLoadEnd;
    }

    public int getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(int repeatType) {
        this.repeatType = repeatType;
    }

    public DateTime getResultStart() {
        return resultStart;
    }

    public DateTime getResultEnd() {
        return resultEnd;
    }
}
