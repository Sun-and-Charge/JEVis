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

import org.jevis.api.JEVisException;

/**
 *
 * @author ai
 */
public interface ILimitAlarm {
    
    /**
     * Sets the parameters for the Alarm object from the JEVis class
     * (Static / Dynamic Limit Alarm).
     * 
     */
    void init();
    
    /**
     * Checks the data since the last checking in accord specific
     * conditions for a given alarm, and writes the result to the log.
     * 
     * @throws JEVisException 
     */
    void checkAlarm() throws JEVisException;
}
