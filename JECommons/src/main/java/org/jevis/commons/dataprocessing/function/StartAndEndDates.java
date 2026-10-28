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
package org.jevis.commons.dataprocessing.function;

import org.jevis.commons.dataprocessing.Process;
import org.jevis.commons.dataprocessing.ProcessOption;
import org.joda.time.DateTime;

public class StartAndEndDates {
    private final Process mainTask;
    private DateTime start = null;
    private DateTime end = null;

    public StartAndEndDates(Process mainTask) {
        this.mainTask = mainTask;
    }

    public DateTime getStart() {
        return start;
    }

    public DateTime getEnd() {
        return end;
    }

    public StartAndEndDates invoke() {
        for (Process process : mainTask.getSubProcesses()) {
            boolean foundDates = false;
            for (ProcessOption processOption : process.getOptions()) {
                foundDates = hasFoundDates(foundDates, processOption);
            }

            if (!foundDates) {
                for (Process subProcess : process.getSubProcesses()) {
                    for (ProcessOption processOption : subProcess.getOptions()) {
                        foundDates = hasFoundDates(foundDates, processOption);
                    }
                }
            }
        }
        return this;
    }

    private boolean hasFoundDates(boolean foundDates, ProcessOption processOption) {
        switch (processOption.getKey()) {
            case "date-start":
                start = new DateTime(processOption.getValue());
                break;
            case "date-end":
                end = new DateTime(processOption.getValue());
                break;
        }
        if (start != null && end != null) {
            foundDates = true;
        }
        return foundDates;
    }
}
