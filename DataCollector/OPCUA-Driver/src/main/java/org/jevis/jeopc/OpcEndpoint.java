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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An endpoint of an OPC UA server, as returned by the bridge command {@code endpoints}.
 */
public class OpcEndpoint {

    public static final String SECURITY_NONE = "None";

    private final String url;
    private final String securityPolicyUri;
    private final String securityPolicy;
    private final String securityMode;
    private final int securityLevel;
    private final List<String> userTokens;
    private final String serverCertificate;

    public OpcEndpoint(String url, String securityPolicyUri, String securityPolicy, String securityMode,
                       int securityLevel, List<String> userTokens, String serverCertificate) {
        this.url = url;
        this.securityPolicyUri = securityPolicyUri;
        this.securityPolicy = securityPolicy;
        this.securityMode = securityMode;
        this.securityLevel = securityLevel;
        this.userTokens = userTokens == null ? Collections.<String>emptyList() : Collections.unmodifiableList(userTokens);
        this.serverCertificate = serverCertificate;
    }

    static OpcEndpoint fromJson(JsonNode node) {
        List<String> tokens = new ArrayList<>();
        JsonNode tokenNode = node.path("userTokens");
        if (tokenNode.isArray()) {
            for (JsonNode token : tokenNode) {
                tokens.add(token.asText());
            }
        }
        return new OpcEndpoint(
                OpcBridge.text(node, "url"),
                OpcBridge.text(node, "securityPolicyUri"),
                OpcBridge.text(node, "securityPolicy"),
                OpcBridge.text(node, "securityMode"),
                node.path("securityLevel").asInt(0),
                tokens,
                OpcBridge.text(node, "serverCertificate"));
    }

    public String getUrl() {
        return url;
    }

    public String getSecurityPolicyUri() {
        return securityPolicyUri;
    }

    /**
     * @return short name such as {@code None} or {@code Basic256Sha256}
     */
    public String getSecurityPolicy() {
        return securityPolicy;
    }

    /**
     * @return {@code None}, {@code Sign} or {@code SignAndEncrypt}
     */
    public String getSecurityMode() {
        return securityMode;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    /**
     * @return the user token types, e.g. {@code Anonymous}, {@code UserName}
     */
    public List<String> getUserTokens() {
        return userTokens;
    }

    /**
     * @return base64 DER server certificate or null
     */
    public String getServerCertificate() {
        return serverCertificate;
    }

    public boolean isSecurityNone() {
        if (securityPolicy != null) {
            return SECURITY_NONE.equals(securityPolicy);
        }
        return securityPolicyUri != null && securityPolicyUri.endsWith("#None");
    }

    @Override
    public String toString() {
        return "OpcEndpoint{" + url + ", policy=" + securityPolicy + ", mode=" + securityMode
                + ", level=" + securityLevel + ", userTokens=" + userTokens + "}";
    }
}
