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
package org.jevis.jeconfig.application.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CalendarRow {
    private String countryCode;
    private String countryName;
    private String stateCode;
    private String stateName;

    public CalendarRow(String countryCode, String countryName, String stateCode, String stateName) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.stateCode = stateCode;
        this.stateName = stateName;
    }

    public CalendarRow(String countryCode, String countryName) {
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.stateCode = "";
        this.stateName = "";
    }

    public CalendarRow(String value) {
        List<String> tempList = new ArrayList<>(Arrays.asList(value.split(",")));
        if (tempList.size() == 2) {
            this.countryCode = tempList.get(0);
            this.stateCode = tempList.get(1);
        }
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getCountryName() {
        return countryName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getStateName() {
        return stateName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CalendarRow) {
            CalendarRow otherObject = (CalendarRow) obj;
            if (this.getCountryCode().equals(otherObject.getCountryCode()) && this.getStateCode() == null && otherObject.getStateCode() == null) {
                return true;
            } else {
                return (this.getCountryCode().equals(otherObject.getCountryCode()) && this.getStateCode().equals(otherObject.getStateCode()));
            }
        } else return false;
    }
}
