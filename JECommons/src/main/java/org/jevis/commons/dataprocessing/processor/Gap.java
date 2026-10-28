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
package org.jevis.commons.dataprocessing.processor;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;


/**
 * @author broder
 */
public class Gap {

    private final List<DateTime> missingDateTimes = new ArrayList<>();
    private Double lastValue;
    private Double firstValue;
    private String startNote;

    public void addDateTime(DateTime missingDateTime) {
        missingDateTimes.add(missingDateTime);
    }

    public List<DateTime> getMissingDateTimes() {
        return missingDateTimes;
    }

    public Double getFirstValue() {
        return firstValue;
    }

    public void setFirstValue(Double firstValue) {
        this.firstValue = firstValue;
    }

    public Double getLastValue() {
        return lastValue;
    }

    public void setLastValue(Double lastValue) {
        this.lastValue = lastValue;
    }

    public String getStartNote() {
        return startNote;
    }

    public void setStartNote(String startNote) {
        this.startNote = startNote;
    }
}
