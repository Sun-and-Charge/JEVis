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
package org.jevis.commons.dataprocessing;

import org.jevis.api.JEVisSample;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;

public class FunctionalInterval {
    final Interval interval;
    List<JEVisSample> samples = new ArrayList<>();

    public FunctionalInterval(DateTime startDate, DateTime endDate) {
        this.interval = new Interval(startDate, endDate);
    }

    public List<JEVisSample> getSamples() {
        return samples;
    }

    public void setSamples(List<JEVisSample> samples) {
        this.samples = samples;
    }

    public Interval getInterval() {
        return interval;
    }
}
