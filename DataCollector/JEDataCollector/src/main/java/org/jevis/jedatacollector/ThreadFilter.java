/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEDataCollector.
 * <p>
 * JEDataCollector is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEDataCollector is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDataCollector. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDataCollector is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedatacollector;

import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggingEvent;

/**
 *
 * @author bf
 */
public class ThreadFilter extends Filter {

    private String _value = "";

    public ThreadFilter(String key) {
        _value = key;
    }

    @Override
    public int decide(LoggingEvent le) {
        String mdc = (String) le.getMDC(Launcher.KEY);
//        logger.info("MDC: " + mdc);
//        logger.info("key: " + _value);
        if (mdc != null && mdc.equals(_value)) {
            return Filter.ACCEPT;
        } else {
            return Filter.DENY;
        }
//        return Filter.ACCEPT;
    }
}
