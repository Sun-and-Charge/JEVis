/**
 * Copyright (C) JEVis contributors
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

import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

public class PathReferenceDescription {

    private final  ReferenceDescription referenceDescription;
    private final String path;
    private final DataValue dataValue;

    public PathReferenceDescription(ReferenceDescription referenceDescription, String path, DataValue dataValue) {
        this.referenceDescription = referenceDescription;
        this.path=path;
        this.dataValue = dataValue;
    }

    public ReferenceDescription getReferenceDescription() {
        return referenceDescription;
    }

    public String getPath() {
        return path;
    }

    public DataValue getDataValue() {
        return dataValue;
    }
}