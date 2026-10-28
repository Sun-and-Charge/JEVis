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

import java.util.List;

public interface Task {

    Long getID();

    Status getStatus();

    void setStatus(Status status);

    String getTaskName();

    void setTaskName(String name);

    DateTime getStartTime();

    String getErrors();

    Period getRunTime();

    Exception getException();

    void setException(Exception exception);

    List<TaskStep> getSteps();

    void addStep(TaskStep step);

    void addStep(String type, Object message);


    DateTime getEndTime();

    enum Status {UNKNOWN, IDLE, STOPPED, STARTED, SCHEDULED, RUNNING, FINISHED, FAILED}

}
