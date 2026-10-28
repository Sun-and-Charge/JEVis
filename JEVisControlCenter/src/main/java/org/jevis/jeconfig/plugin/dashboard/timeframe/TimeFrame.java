/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.dashboard.timeframe;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Interval;

public interface TimeFrame {
    Logger logger = LogManager.getLogger(TimeFrame.class);

    String getListName();


    /**
     * @param addAmount
     * @return
     */
    Interval nextPeriod(Interval interval, int addAmount);

    Interval previousPeriod(Interval interval, int addAmount);

    String format(Interval interval);

    Interval getInterval(DateTime dateTime, Boolean fixed);

    String getID();

    boolean hasNextPeriod(Interval interval);

    boolean hasPreviousPeriod(Interval interval);

    default boolean timeFrameEqual(Object other) {
        try {
            if (other instanceof TimeFrame) {
                return getID().equals(((TimeFrame) other).getID());
            }
        } catch (Exception ex) {
            logger.error("Failed to compare TimeFrame IDs", ex);
        }
        return false;
    }

}

