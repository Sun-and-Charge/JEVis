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

import java.util.List;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisSample;

/**
 *
 * @author Florian Simon
 */
public class SampleStringBuilder {

    public static String toString(List<JEVisSample> samples) throws JEVisException {
        if (samples == null || samples.isEmpty()) {
            return " -No Samples- ";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Sample count: ");
        sb.append(samples.size());
        sb.append("\n");
        sb.append("Sample between: ");
        sb.append(samples.get(0).getTimestamp());
        sb.append(" - ");
        sb.append(samples.get(samples.size() - 1).getTimestamp());
        sb.append("\n");

        for (JEVisSample sample : samples) {
            sb.append("[");
            sb.append(sample.getTimestamp());
            sb.append("] ");
            sb.append(sample.getValueAsString());
            sb.append("\n");
        }
        sb.append("-----------");

        return sb.toString();
    }
}
