/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.driver;

import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;

import java.util.Map;

/**
 * Finds the driver registered for a JEVis class. If the class itself has no driver, its parent classes are checked, so a
 * subclass of e.g. "HTTP Server" uses the HTTP driver.
 */
public class DriverLookup {

    private DriverLookup() {
    }

    public static Class find(Map<String, Class> drivers, JEVisClass jevisClass) throws JEVisException {
        for (JEVisClass current = jevisClass; current != null; current = current.getInheritance()) {
            Class driver = drivers.get(current.getName());
            if (driver != null) {
                return driver;
            }
        }
        return null;
    }
}
