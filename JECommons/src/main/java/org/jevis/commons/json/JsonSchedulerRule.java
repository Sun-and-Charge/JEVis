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

package org.jevis.commons.json;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "JsonSchedulerRule")
public class JsonSchedulerRule {
    private String id;
    private String months;
    private String dayOfMonth;
    private String dayOfWeek;
    private String startTimeHours;
    private String startTimeMinutes;
    private String endTimeHours;
    private String endTimeMinutes;

    @XmlElement(name = "id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XmlElement(name = "months")
    public String getMonths() {
        return this.months;
    }

    public void setMonths(String months) {
        this.months = months;
    }

    @XmlElement(name = "dayOfMonth")
    public String getDayOfMonth() {
        return this.dayOfMonth;
    }

    public void setDayOfMonth(String dayOfMonth) {
        this.dayOfMonth = dayOfMonth;
    }

    @XmlElement(name = "dayOfWeek")
    public String getDayOfWeek() {
        return this.dayOfWeek;
    }

    public void setDayOfWeek(String dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    @XmlElement(name = "startTimeHours")
    public String getStartTimeHours() {
        return this.startTimeHours;
    }

    public void setStartTimeHours(String startTimeHours) {
        this.startTimeHours = startTimeHours;
    }

    @XmlElement(name = "startTimeMinutes")
    public String getStartTimeMinutes() {
        return this.startTimeMinutes;
    }

    public void setStartTimeMinutes(String startTimeMinutes) {
        this.startTimeMinutes = startTimeMinutes;
    }

    @XmlElement(name = "endTimeHours")
    public String getEndTimeHours() {
        return this.endTimeHours;
    }

    public void setEndTimeHours(String endTimeHours) {
        this.endTimeHours = endTimeHours;
    }

    @XmlElement(name = "endTimeMinutes")
    public String getEndTimeMinutes() {
        return this.endTimeMinutes;
    }

    public void setEndTimeMinutes(String endTimeMinutes) {
        this.endTimeMinutes = endTimeMinutes;
    }

    @Override
    public String toString() {
        try {
            return JsonTools.prettyObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }
}
