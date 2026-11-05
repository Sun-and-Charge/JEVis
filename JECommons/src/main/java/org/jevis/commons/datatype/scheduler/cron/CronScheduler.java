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
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;

public class CronScheduler {
    private final List<SchedulerRule> rules = new ArrayList();
    private DateTimeZone datetTimeZone;

    public CronScheduler() {
    }

    public void addRule(SchedulerRule scheduler) {
        this.rules.add(scheduler);
    }

    public void removeRule(SchedulerRule scheduler) {
        this.rules.remove(scheduler);
    }

    public List<SchedulerRule> getAllRules() {
        return this.rules;
    }

    public DateTimeZone getDatetTimeZone() {
        return this.datetTimeZone;
    }

    public void setDatetTimeZone(DateTimeZone datetTimeZone) {
        this.datetTimeZone = datetTimeZone;
    }
}
