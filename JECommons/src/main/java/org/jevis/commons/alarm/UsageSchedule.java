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
package org.jevis.commons.alarm;


import org.jevis.commons.datatype.scheduler.cron.CronScheduler;
import org.jevis.commons.datatype.scheduler.handler.SchedulerContainsHandler;
import org.joda.time.DateTime;

public class UsageSchedule {

    private final CronScheduler scheduler;
    private final UsageScheduleType type;
    private final SchedulerContainsHandler handler = new SchedulerContainsHandler();

    public UsageSchedule(CronScheduler scheduler, UsageScheduleType type) {
        this.scheduler = scheduler;
        this.type = type;
    }

    public boolean contains(DateTime dt) {
        return handler.contains(dt, scheduler);
    }

    public UsageScheduleType getType() {
        return type;
    }
}
