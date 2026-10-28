/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEAlarm.
 * <p>
 * JEAlarm is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEAlarm is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAlarm. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAlarm is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jealarm.deprecated.limitalarm;

import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisUnit;

/**
 *
 * @author fs
 */
public class AlarmHelper {

    public static String formatValue(JEVisSample sample) {
        String strg = "";

        try {
            strg = JEAlarm.deci.format(sample.getValueAsDouble());

            try {
                if (sample.getUnit() != null && sample.getUnit().getLabel() != null && !sample.getUnit().getLabel().equals("null")) {
                    strg = strg + " " + sample.getUnit().getLabel();
                }

            } catch (Exception ex) {

            }

        } catch (Exception ex) {

        }
        return strg;
    }

    public static String formatValue(Double sample, JEVisUnit unit) {
        String strg = "";

        try {
            strg = JEAlarm.deci.format(sample);

            try {
                if (unit != null && unit.getLabel() != null && !unit.getLabel().equals("null")) {
                    strg = strg + " " + unit.getLabel();
                }

            } catch (Exception ex) {

            }

        } catch (Exception ex) {

        }
        return strg;
    }

}
