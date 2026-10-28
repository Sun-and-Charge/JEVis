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

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.structured.ReferenceDescription;

public class Nodes {

    public static NodeId toNodeID(ReferenceDescription referenceDescription) {
        NodeId nodeId = new NodeId(referenceDescription.getNodeId().getNamespaceIndex(), (UInteger) referenceDescription.getNodeId().getIdentifier());

        return nodeId;

    }
}
