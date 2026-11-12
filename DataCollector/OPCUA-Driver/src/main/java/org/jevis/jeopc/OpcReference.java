/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A reference found by the bridge command {@code browse}: the target node with its names and node class.
 */
public class OpcReference {

    public static final String NODE_CLASS_OBJECT = "Object";
    public static final String NODE_CLASS_VARIABLE = "Variable";

    private final String nodeId;
    private final String browseName;
    private final String displayName;
    private final String nodeClass;
    private final String typeDefinition;
    private final String referenceType;

    public OpcReference(String nodeId, String browseName, String displayName, String nodeClass,
                        String typeDefinition, String referenceType) {
        this.nodeId = nodeId;
        this.browseName = browseName;
        this.displayName = displayName;
        this.nodeClass = nodeClass;
        this.typeDefinition = typeDefinition;
        this.referenceType = referenceType;
    }

    static OpcReference fromJson(JsonNode node) {
        return new OpcReference(
                OpcBridge.text(node, "nodeId"),
                OpcBridge.text(node, "browseName"),
                OpcBridge.text(node, "displayName"),
                OpcBridge.text(node, "nodeClass"),
                OpcBridge.text(node, "typeDefinition"),
                OpcBridge.text(node, "referenceType"));
    }

    /**
     * @return the node id in OPC UA string form, e.g. {@code ns=2;s=Device1}
     */
    public String getNodeId() {
        return nodeId;
    }

    /**
     * @return the browse name as {@code ns:name}, e.g. {@code 2:Device1}
     */
    public String getBrowseName() {
        return browseName;
    }

    /**
     * @return the name part of the browse name without the namespace index
     */
    public String getName() {
        return nameOf(browseName);
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return {@code Object}, {@code Variable}, ...
     */
    public String getNodeClass() {
        return nodeClass;
    }

    public String getTypeDefinition() {
        return typeDefinition;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public boolean isObject() {
        return NODE_CLASS_OBJECT.equals(nodeClass);
    }

    public boolean isVariable() {
        return NODE_CLASS_VARIABLE.equals(nodeClass);
    }

    /**
     * Strips the namespace index of a qualified name in the form {@code ns:name}.
     */
    static String nameOf(String qualifiedName) {
        if (qualifiedName == null) {
            return "";
        }
        int colon = qualifiedName.indexOf(':');
        if (colon > 0) {
            boolean digits = true;
            for (int i = 0; i < colon; i++) {
                if (!Character.isDigit(qualifiedName.charAt(i))) {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                return qualifiedName.substring(colon + 1);
            }
        }
        return qualifiedName;
    }

    @Override
    public String toString() {
        return "OpcReference{nodeId=" + nodeId + ", browseName=" + browseName + ", displayName=" + displayName
                + ", nodeClass=" + nodeClass + ", typeDefinition=" + typeDefinition + "}";
    }
}
