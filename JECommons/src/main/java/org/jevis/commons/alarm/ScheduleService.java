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

import org.joda.time.DateTime;

import java.util.List;

/**
 * @author Artur Iablokov
 */
public class ScheduleService {

    private static final int LOG_NORMAL = 1;
    private static final int LOG_SILENT = 2;
    private static final int LOG_STANDBY = 4;

    public static Integer getValueForLog(DateTime dt, List<UsageSchedule> up) {
        int res = 0;

        for (UsageSchedule us : up) {
            if (us.contains(dt)) {
                if (us.getType().equals(UsageScheduleType.SILENT)) {
                    res = res + LOG_SILENT;
                } else if (us.getType().equals(UsageScheduleType.STANDBY)) {
                    res = res + LOG_STANDBY;
                }
            }
        }

        if (res == 0) {
            res = LOG_NORMAL;
        }
        return res;
    }
}