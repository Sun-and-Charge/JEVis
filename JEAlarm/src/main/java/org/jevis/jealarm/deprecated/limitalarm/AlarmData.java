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

import org.joda.time.DateTime;

/**
 *
 * @author fs
 */
public class AlarmData {

    private DateTime ts;
    private String message;
    private boolean isAlarm = true;

    public AlarmData(DateTime ts, boolean isAlarm, String message) {
        this.ts = ts;
        this.message = message;
        this.isAlarm = isAlarm;
    }

    public DateTime getTime() {
        return ts;
    }

    public String getMessage() {
        return message;
    }

    public boolean isAlarm() {
        return isAlarm;
    }
}
