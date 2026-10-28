/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEAPI.
 * <p>
 * JEAPI is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEAPI is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAPI. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAPI is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.api;

/**
 *
 * @author fs
 */
public interface JEVisUser {

    boolean isEnabled();

    String getFirstName();

    String getLastName();

    boolean isSysAdmin();

    long getUserID();

    JEVisObject getUserObject();

    String getAccountName();

    boolean canRead(long objectID);

    boolean canWrite(long objectID);

    boolean canCreate(long objectID);

    boolean canCreate(long objectID, String jevisClass);

    boolean canExecute(long objectID);

    boolean canDelete(long objectID);

    void reload();


//    public boolean canDeleteClass(String jclass);
}
