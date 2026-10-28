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
package org.jevis.commons.task;

import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.List;

public class CommonTask implements Task {

    private final Long taskID;
    private Status status = Status.UNKNOWN;
    private DateTime startDate;
    private DateTime endDate;
    private String name = "";
    private String error;
    private Exception exception;
    private final List<TaskStep> steps = new ArrayList<>();

    public CommonTask(Long id) {
        taskID = id;
    }

    @Override
    public Status getStatus() {
        return status;
    }

    @Override
    public void setStatus(Status status) {
        this.status = status;

        if (status == Status.STARTED) {
            startDate = new DateTime();
        } else if (status == Status.FINISHED || status == Status.FAILED || status == Status.STOPPED) {
            endDate = new DateTime();
        }


    }

    @Override
    public Long getID() {
        return taskID;
    }

    @Override
    public Period getRunTime() {
        try {
            if (startDate == null) {
                return Period.ZERO;
            } else if (endDate == null) {
                Period period = new Period(startDate, (new DateTime()));
                return period;
            } else {
                Period period = new Period(startDate, endDate);
                return period;
            }
        } catch (Exception ex) {
            return Period.ZERO;
        }


    }

    @Override
    public String getTaskName() {
        return name;
    }

    @Override
    public void setTaskName(String name) {
        this.name = name;
    }

    @Override
    public DateTime getStartTime() {
        return startDate;
    }

    @Override
    public String getErrors() {
        return error;
    }

    @Override
    public Exception getException() {
        //setStatus(Status.FAILED);
        return exception;
    }

    @Override
    public void setException(Exception exception) {
        this.exception = exception;
    }

    @Override
    public List<TaskStep> getSteps() {
        return steps;
    }

    @Override
    public void addStep(TaskStep step) {
        steps.add(step);
    }

    @Override
    public void addStep(String type, Object message) {
        steps.add(new TaskStep(type, message));
    }

    @Override
    public DateTime getEndTime() {
        return endDate;
    }
}
