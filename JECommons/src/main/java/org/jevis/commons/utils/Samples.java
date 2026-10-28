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
package org.jevis.commons.utils;

import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.List;

public class Samples {

    /**
     * Returns the period of an sample List.
     * <p>
     * TODO: support non sorted lists
     *
     * @param samples
     * @return
     */
    public static Period getDuration(List<JsonSample> samples) {
        try {
            if (samples.isEmpty()) {
                return Period.ZERO;
            }

            if (samples.size() == 1) {
                return new Period(DateTime.parse(samples.get(0).getTs()), DateTime.parse(samples.get(0).getTs()));
            }

            return new Period(DateTime.parse(samples.get(0).getTs()), DateTime.parse(samples.get(samples.size() - 1).getTs()));

        } catch (Exception ex) {
            return Period.ZERO;
        }
    }


}
