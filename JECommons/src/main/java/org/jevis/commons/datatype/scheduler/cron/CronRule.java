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

package org.jevis.commons.datatype.scheduler.cron;

import org.jevis.commons.datatype.scheduler.SchedulerRule;
import org.joda.time.DateTime;

import java.security.InvalidParameterException;
import java.time.DayOfWeek;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;

public class CronRule implements SchedulerRule {
    private final CronTime start = new CronTime();
    private final CronTime end = new CronTime();
    private final List<DayOfWeek> weekDays = new ArrayList();
    private final List<Month> months = new ArrayList();
    private CronDayOfMonth monthDays = null;

    public CronRule() {
    }

    public void setDayOfWeek(DayOfWeek day, boolean enable) {
        if (enable) {
            this.weekDays.add(day);
        } else {
            this.weekDays.remove(day);
        }

    }

    public boolean isDayOfWeekEnabled(DayOfWeek day) {
        return this.weekDays.contains(day);
    }

    public void setMonth(Month month, boolean enable) {
        if (enable) {
            this.months.add(month);
        } else {
            this.months.remove(month);
        }

    }

    public void setAllMonths(boolean enable) {
        Month[] var2 = Month.values();
        int var3 = var2.length;

        for (int var4 = 0; var4 < var3; ++var4) {
            Month m = var2[var4];
            this.setMonth(m, enable);
        }

    }

    public boolean isMonthEnabled(Month day) {
        return this.months.contains(day);
    }

    public void setDayOfMonths(String dayOfMonth) throws InvalidParameterException {
        this.monthDays = new CronDayOfMonth();
        this.monthDays.init(dayOfMonth);
    }

    public boolean isDayOfMonthEnabled(DateTime dt) {
        if (dt != null) {
            if (this.monthDays.isAllDays()) {
                return true;
            } else {
                if (this.monthDays.isLastDay()) {
                    int lastDayOfMonth = dt.dayOfMonth().withMaximumValue().getDayOfMonth();
                    if (dt.getDayOfMonth() == lastDayOfMonth) {
                        return true;
                    }
                }

                return this.monthDays.isDayEnabled(dt.getDayOfMonth());
            }
        } else {
            throw new InvalidParameterException("datetime value is null or corrupt");
        }
    }

    public String getDayOfMonth() {
        return this.monthDays.getDayOfMonthValue();
    }

    public String getStartHour() {
        return this.start.getHour().getValue();
    }

    public void setStartHour(String h) {
        this.start.setHour(h);
    }

    public String getEndHour() {
        return this.end.getHour().getValue();
    }

    public void setEndHour(String h) {
        this.end.setHour(h);
    }

    public String getStartMinute() {
        return this.start.getMin().getValue();
    }

    public void setStartMinute(String m) {
        this.start.setMin(m);
    }

    public String getEndMinute() {
        return this.end.getMin().getValue();
    }

    public void setEndMinute(String m) {
        this.end.setMin(m);
    }

    public String getDayOfWeekArray() {
        List<Integer> ar = new ArrayList();
        this.weekDays.forEach((d) -> {
            ar.add(d.getValue());
        });
        return (listToString(ar));
    }

    public String getMonthArray() {
        List<Integer> ar = new ArrayList();
        this.months.forEach((m) -> {
            ar.add(m.getValue());
        });
        return listToString(ar);
    }

    private String listToString(List<Integer> listString) {
        if (listString != null) {
            StringBuilder sb = new StringBuilder();
            if (listString.size() > 1) {
                for (Integer i : listString) {
                    int index = listString.indexOf(i);
                    sb.append(i.toString());
                    if (index < listString.size() - 1) sb.append(", ");
                }
            } else if (listString.size() == 1) sb.append(listString.get(0));
            return sb.toString();
        } else return "";
    }

    public CronTime getStartTime() {
        return this.start;
    }

    public CronTime getEndTime() {
        return this.end;
    }
}
