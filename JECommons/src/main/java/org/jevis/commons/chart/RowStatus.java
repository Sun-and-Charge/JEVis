/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

/**
 * Result status of a data row.
 */
public enum RowStatus {
    /**
     * samples were loaded
     */
    OK,
    /**
     * no samples in the time frame
     */
    NO_DATA,
    /**
     * the object of the row is not readable for the user (or does not exist any more)
     */
    NO_PERMISSION,
    /**
     * the data could not be loaded, see the message
     */
    ERROR
}
