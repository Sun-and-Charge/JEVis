/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

/**
 * A browsed node with the path of its parent and, for variables, its value.
 */
public class PathReferenceDescription {

    private final OpcReference referenceDescription;
    private final String path;
    private final OpcValue dataValue;

    public PathReferenceDescription(OpcReference referenceDescription, String path, OpcValue dataValue) {
        this.referenceDescription = referenceDescription;
        this.path=path;
        this.dataValue = dataValue;
    }

    public OpcReference getReferenceDescription() {
        return referenceDescription;
    }

    public String getPath() {
        return path;
    }

    /**
     * @return the value of a variable, null for objects or if it could not be read
     */
    public OpcValue getDataValue() {
        return dataValue;
    }
}