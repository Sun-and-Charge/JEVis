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
package org.jevis.commons.dataprocessing.processor.workflow;

import org.joda.time.DateTime;
import org.joda.time.Period;

public class PeriodRule {
    private final DateTime startOfPeriod;
    private final Period period;

    public PeriodRule(DateTime startOfPeriod, Period period) {
        this.startOfPeriod = startOfPeriod;
        this.period = period;
    }

    public DateTime getStartOfPeriod() {
        return startOfPeriod;
    }

    public Period getPeriod() {
        return period;
    }
}
