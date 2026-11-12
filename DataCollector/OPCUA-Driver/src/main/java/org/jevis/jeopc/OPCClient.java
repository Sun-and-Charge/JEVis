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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * OPC UA client of JEVis. Talks to the server through the external program {@code opcua-bridge}
 * (see docs/OPCUA-BRIDGE.md); one client uses one bridge process and one session.
 */
public class OPCClient implements AutoCloseable {

    /** NodeId of the RootFolder. */
    public static final String ROOT_FOLDER = "i=84";
    /** Security policy/mode value that lets the bridge choose the endpoint. */
    public static final String SECURITY_AUTO = "auto";

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(OPCClient.class);
    private static final List<String> BROWSE_CLASSES = Collections.unmodifiableList(
            java.util.Arrays.asList(OpcReference.NODE_CLASS_OBJECT, OpcReference.NODE_CLASS_VARIABLE));

    private static final int MAX_DEPTH = 32;

    private final String endpointURL;
    private final OpcBridge bridge;
    private final boolean defaultBridge;
    private OpcEndpoint endpointDescription;
    private String user;
    private String password;
    private String applicationName = "JEVis DataCollector";
    private String applicationUri = "urn:jevis:datacollector";
    private long connectTimeoutMs = 30000;
    private long requestTimeoutMs = 60000;
    private long historyTimeoutMs = 600000;
    private boolean connected = false;
    private int connectedGeneration = -1;

    /**
     * Crete an new client for the endpoint
     *
     * @param url - for example: opc.tcp://10.1.2.128:4840
     */
    public OPCClient(String url) {
        this(url, new OpcBridge(), true);
    }

    /**
     * Client with its own bridge, e.g. with an in-memory transport for tests.
     */
    public OPCClient(String url, OpcBridge bridge) {
        this(url, bridge, false);
    }

    private OPCClient(String url, OpcBridge bridge, boolean defaultBridge) {
        logger.info("Init OPC Client with url: {}", url);
        this.endpointURL = url;
        this.bridge = bridge;
        this.defaultBridge = defaultBridge;
    }

    /**
     * Use user name and password for the session; anonymous if user is null or empty.
     */
    public void setIdentification(String user, String password) {
        this.user = user;
        this.password = password;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public void setApplicationUri(String applicationUri) {
        this.applicationUri = applicationUri;
    }

    /**
     * Timeout of the connect request (and of the bridge's own connect).
     */
    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    /**
     * Timeout of browse, read and write requests.
     */
    public void setRequestTimeoutMs(long requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }

    /**
     * Timeout of one history request (all continuation points).
     */
    public void setHistoryTimeoutMs(long historyTimeoutMs) {
        this.historyTimeoutMs = historyTimeoutMs;
    }

    public String getURL() {
        return endpointURL;
    }

    public static String printEP(OpcEndpoint endpointDescription) {
        logger.debug("---------Endpoint----------");
        logger.debug(endpointDescription.getUrl());
        logger.debug("UserIdentityTokens: {}", endpointDescription.getUserTokens());
        logger.debug("SecurityMode: {}", endpointDescription.getSecurityMode());
        logger.debug("SecurityPolicyUri: {}", endpointDescription.getSecurityPolicyUri());
        logger.debug("SecurityLevel: {}", endpointDescription.getSecurityLevel());
        return "";
    }

    /**
     * Close the session and stop the bridge.
     */
    @Override
    public void close() {
        connected = false;
        try {
            bridge.close();
        } catch (Exception ex) {
            logger.warn("Error while closing the OPC UA connection", ex);
        }
    }

    /**
     * The the list of endpoints the device supports.
     */
    public List<OpcEndpoint> getEndpoints() throws OpcException {
        logger.info("Discover Endpoint for: {}", endpointURL);
        ObjectNode params = bridge.newParams();
        params.put("url", endpointURL);
        JsonNode result = bridge.request("endpoints", params, connectTimeoutMs);
        List<OpcEndpoint> endpoints = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                OpcEndpoint endpoint = OpcEndpoint.fromJson(node);
                logger.debug("Endpoint: {}", endpoint);
                endpoints.add(endpoint);
            }
        }
        return endpoints;
    }

    /**
     * Selects the endpoint without security for the configured URL, else the first one.
     * <p>
     * Without {@link #setEndpoints(OpcEndpoint)}, {@link #connect()} lets the bridge choose ({@value #SECURITY_AUTO}).
     */
    public OpcEndpoint autoSelectEndpoint() throws OpcException {
        List<OpcEndpoint> endpoints = getEndpoints();
        if (endpoints.isEmpty()) {
            throw new OpcException(OpcException.CONNECT_FAILED, null, "Server has no endpoints: " + endpointURL);
        }
        for (OpcEndpoint endpoint : endpoints) {
            if (endpoint.isSecurityNone() && endpoint.getUrl() != null && endpoint.getUrl().contains(endpointURL)) {
                logger.info("Found supported endpoint: {}", endpoint);
                return endpoint;
            }
        }

        logger.info("Did not found supported endpoint using first: {}", endpoints.get(0));
        return endpoints.get(0);
    }

    /**
     * Set the endpoint to use. This defines the security settings. Null lets the bridge choose.
     */
    public void setEndpoints(OpcEndpoint endpoint) {
        endpointDescription = endpoint;
    }

    public OpcEndpoint getEndpoint() {
        return endpointDescription;
    }

    /**
     * Connect to the enpoint and open the session.
     */
    public void connect() throws OpcException {
        ObjectNode params = bridge.newParams();
        params.put("url", endpointURL);
        if (endpointDescription != null && endpointDescription.getSecurityPolicy() != null
                && endpointDescription.getSecurityMode() != null) {
            params.put("securityPolicy", endpointDescription.getSecurityPolicy());
            params.put("securityMode", endpointDescription.getSecurityMode());
        } else {
            params.put("securityPolicy", SECURITY_AUTO);
            params.put("securityMode", SECURITY_AUTO);
        }
        if (user != null && !user.isEmpty()) {
            params.put("user", user);
            params.put("password", password != null ? password : "");
        } else {
            params.putNull("user");
            params.putNull("password");
        }
        params.putNull("certFile");
        params.putNull("keyFile");
        params.put("certDir", certDir());
        params.put("applicationName", applicationName);
        params.put("applicationUri", applicationUri);
        params.put("trustServerCertificate", true);
        params.put("timeoutMs", connectTimeoutMs);

        logger.info("Connect to {} security: {}/{} user: {}", endpointURL, params.get("securityPolicy").asText(),
                params.get("securityMode").asText(), user != null && !user.isEmpty() ? user : "anonymous");

        JsonNode result = bridge.request("connect", params, connectTimeoutMs + 5000);
        connected = true;
        /* the request may have started the process, so take the generation afterwards */
        connectedGeneration = bridge.getGeneration();
        if (result.has("endpoint")) {
            logger.debug("Connected endpoint: {}", OpcEndpoint.fromJson(result.get("endpoint")));
        }
        logger.debug("Done Connect");
    }

    private String certDir() {
        File program = null;
        if (defaultBridge) {
            try {
                program = OpcBridge.findProgram();
            } catch (OpcException ex) {
                logger.debug("Bridge not found for cert dir: {}", ex.getMessage());
            }
        }
        return OpcBridge.certDir(program);
    }

    /**
     * Sends a request of the session; connects again if the bridge was restarted since the last connect.
     */
    private JsonNode sessionRequest(String cmd, ObjectNode params, long timeoutMs) throws OpcException {
        if (connected && bridge.start() != connectedGeneration) {
            logger.warn("OPC UA bridge was restarted, connecting again");
            connected = false;
            connect();
        }
        return bridge.request(cmd, params, timeoutMs);
    }

    /**
     * Browses one level below the node: Objects and Variables of the hierarchical references.
     *
     * @param nodeId parent node
     */
    public List<OpcReference> browse(String nodeId) throws OpcException {
        ObjectNode params = bridge.newParams();
        params.put("nodeId", nodeId);
        ArrayNode classes = params.putArray("nodeClasses");
        for (String nodeClass : BROWSE_CLASSES) {
            classes.add(nodeClass);
        }
        params.put("references", "Hierarchical");
        JsonNode result = sessionRequest("browse", params, requestTimeoutMs);
        List<OpcReference> references = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                references.add(OpcReference.fromJson(node));
            }
        }
        return references;
    }

    /**
     * Browse the tree like opc structure.
     *
     * @return map of path (browse names below the RootFolder, e.g. "/Objects/Server") to reference
     */
    public HashMap<String, OpcReference> browse() {
        HashMap<String, OpcReference> map = new HashMap<>();
        browseTree(map, "", ROOT_FOLDER, 0);
        return map;
    }

    private void browseTree(HashMap<String, OpcReference> hashMap, String xpathParent, String browseRoot, int depth) {
        if (depth > MAX_DEPTH) {
            logger.warn("Browse depth limit reached at {}", xpathParent);
            return;
        }
        try {
            for (OpcReference rd : browse(browseRoot)) {
                logger.debug("rd: {}", rd);
                String xpath = xpathParent + "/" + rd.getName();
                if (hashMap.containsKey(xpath) && rd.getNodeId().equals(hashMap.get(xpath).getNodeId())) {
                    continue;
                }
                hashMap.put(xpath, rd);
                browseTree(hashMap, xpath, rd.getNodeId(), depth + 1);
            }
        } catch (OpcException ex) {
            logger.error("Browse of {} failed: {}", browseRoot, ex.getMessage());
        }
    }

    /**
     * Get the history of a channel: processed (aggregate) if the channel has a function node, else raw.
     *
     * @return the values, empty if the server has no data (bad status) for the period
     */
    public List<OpcValue> getHistory(OPCUAChannel channel, DateTime from, DateTime until) throws OpcException {
        logger.debug("getHistory from: {} until: {}", from, until);
        String nodeId = channel.getOPCNodeId();
        ObjectNode params = bridge.newParams();
        params.put("nodeId", nodeId);
        params.put("from", formatTimestamp(from));
        params.put("to", formatTimestamp(until));

        String cmd;
        /* Use Aggregated request if function node is not empty */
        if (channel.getFunctionNode() != null && !channel.getFunctionNode().trim().isEmpty()) {
            cmd = "history-processed";
            params.put("aggregate", channel.getFunctionNode().trim());
            double interval = channel.getFunctionInterval();
            if (interval == Math.rint(interval)) {
                params.put("intervalMs", (long) interval);
            } else {
                params.put("intervalMs", interval);
            }
            params.put("useServerCapabilitiesDefaults", true);
            params.put("treatUncertainAsBad", false);
            params.put("percentDataBad", 0);
            params.put("percentDataGood", 0);
            params.put("useSlopedExtrapolation", false);
            logger.debug("Aggregation Node: {}", channel.getFunctionNode());
        } else {
            /* use raw data request */
            cmd = "history-raw";
            params.put("maxValues", 0);
            params.put("returnBounds", true);
            params.put("timestamps", "Source");
        }

        JsonNode result;
        try {
            result = sessionRequest(cmd, params, historyTimeoutMs);
        } catch (OpcException ex) {
            if (ex.isServerStatus()) {
                /* e.g. BadNoData for the period, as before: no values */
                logger.warn("History of {} from {} until {}: {} {}", nodeId, from, until, ex.getStatus(), ex.getMessage());
                return new ArrayList<>();
            }
            throw ex;
        }

        List<OpcValue> values = new ArrayList<>();
        JsonNode list = result.path("values");
        if (list.isArray()) {
            for (JsonNode node : list) {
                values.add(OpcValue.fromHistoryJson(nodeId, node));
            }
        }
        if (result.path("truncated").asBoolean(false)) {
            logger.warn("History of {} was truncated", nodeId);
        }
        logger.debug("Data Size: {}", values.size());
        return values;
    }

    static String formatTimestamp(DateTime dateTime) {
        return dateTime.toDateTime(DateTimeZone.UTC).toString();
    }

    /**
     * Searches the node of a path such as "/Objects/Loytec ROOT/Trend", starting at the RootFolder.
     *
     * @return the references of the path from the RootFolder, empty if the first segment is not found
     */
    private List<OpcReference> resolvePath(String rootFolder) throws OpcException {
        List<OpcReference> path = new ArrayList<>();
        String nodeId = ROOT_FOLDER;
        for (String segment : rootFolder.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            OpcReference found = null;
            for (OpcReference reference : browse(nodeId)) {
                if (reference.getName().equals(segment)) {
                    found = reference;
                    break;
                }
            }
            if (found == null) {
                logger.warn("OPC UA path segment '{}' of '{}' not found", segment, rootFolder);
                break;
            }
            path.add(found);
            nodeId = found.getNodeId();
        }
        return path;
    }

    /**
     * Adds the node of the path (with path "") and then all nodes below it, depth first, to the list. The path of
     * a node is the path of its parent, starting with "/" + the name of the root folder node.
     *
     * @param list       of the OPC-UA Nodes
     * @param rootFolder of OPC-UA, e.g. "/Objects/Loytec ROOT/Trend"
     */
    public void browse(List<PathReferenceDescription> list, String rootFolder) {
        String[] segments = rootFolder.split("/");
        String last = segments.length > 0 ? segments[segments.length - 1] : "";
        try {
            List<OpcReference> path = resolvePath(rootFolder);
            String nodeId = ROOT_FOLDER;
            if (!path.isEmpty()) {
                OpcReference target = path.get(path.size() - 1);
                nodeId = target.getNodeId();
                if (target.getName().equals(last)) {
                    list.add(new PathReferenceDescription(target, "", null));
                }
            }
            logger.debug("Browse root: {}", nodeId);
            browseTree(list, "/" + last, nodeId, 0);
        } catch (OpcException ex) {
            logger.error("Browse of {} failed: {}", rootFolder, ex.getMessage());
        }
    }

    /**
     * @return the node of the path with path "/" + its name, or null if the path is not found completely
     */
    public PathReferenceDescription getRoot(String rootFolder) {
        try {
            List<OpcReference> path = resolvePath(rootFolder);
            int segments = 0;
            for (String segment : rootFolder.split("/")) {
                if (!segment.isEmpty()) {
                    segments++;
                }
            }
            if (path.isEmpty() || path.size() < segments) {
                return null;
            }
            OpcReference reference = path.get(path.size() - 1);
            PathReferenceDescription pathReferenceDescription = new PathReferenceDescription(reference, "/" + reference.getName(), null);
            logger.debug("Root: {}", reference);
            return pathReferenceDescription;
        } catch (OpcException ex) {
            logger.error("Root {} not found: {}", rootFolder, ex.getMessage());
            return null;
        }
    }

    private void browseTree(List<PathReferenceDescription> list, String xpath, String browseRoot, int depth) {
        if (depth > MAX_DEPTH) {
            logger.warn("Browse depth limit reached at {}", xpath);
            return;
        }
        try {
            List<OpcReference> references = browse(browseRoot);

            /* values of all variables of this level in one request */
            List<String> variableIds = new ArrayList<>();
            for (OpcReference rd : references) {
                if (rd.isVariable()) {
                    variableIds.add(rd.getNodeId());
                }
            }
            HashMap<String, OpcValue> values = new HashMap<>();
            if (!variableIds.isEmpty()) {
                try {
                    for (OpcValue value : readValues(variableIds)) {
                        if (value.getNodeId() != null && value.isGood()) {
                            values.put(value.getNodeId(), value);
                        }
                    }
                } catch (OpcException ex) {
                    logger.warn("Reading values below {} failed: {}", xpath, ex.getMessage());
                }
            }

            for (OpcReference rd : references) {
                PathReferenceDescription pathReferenceDescription = new PathReferenceDescription(rd, xpath,
                        rd.isVariable() ? values.get(rd.getNodeId()) : null);
                logger.debug("Add to list: {}-{}", xpath, rd);
                if (rd.isObject() || rd.isVariable()) {
                    list.add(pathReferenceDescription);
                }
                browseTree(list, xpath + "/" + rd.getName(), rd.getNodeId(), depth + 1);
            }
        } catch (OpcException ex) {
            logger.error("Browse of {} failed: {}", browseRoot, ex.getMessage());
        }
    }

    /**
     * Reads the values of several nodes in one request.
     */
    public List<OpcValue> readValues(List<String> nodeIds) throws OpcException {
        return readAttribute(nodeIds, "Value");
    }

    private List<OpcValue> readAttribute(List<String> nodeIds, String attribute) throws OpcException {
        ObjectNode params = bridge.newParams();
        ArrayNode ids = params.putArray("nodeIds");
        for (String nodeId : nodeIds) {
            ids.add(nodeId);
        }
        params.put("attribute", attribute);
        JsonNode result = sessionRequest("read", params, requestTimeoutMs);
        List<OpcValue> values = new ArrayList<>();
        if (result.isArray()) {
            for (JsonNode node : result) {
                values.add(OpcValue.fromReadJson(node));
            }
        }
        return values;
    }

    /**
     * @param nodeId of Node
     * @return the value, or null if its status is not good
     */
    public OpcValue readValue(String nodeId) throws OpcException {
        List<OpcValue> values = readValues(Collections.singletonList(nodeId));
        if (values.isEmpty()) {
            return null;
        }
        OpcValue value = values.get(0);
        if (value.isGood()) {
            logger.debug("{}: {}", nodeId, value);
            return value;
        } else {
            logger.info("Data of {} is bad: {}", nodeId, value.getStatus());
            return null;
        }
    }

    /**
     * Writes a value.
     *
     * @param value    Number, String, Boolean or null
     * @param nodeId   target node
     * @param dataType OPC UA data type name such as {@code Double}, or {@code auto} to use the node's type
     */
    public void writeValue(Object value, String nodeId, String dataType) throws OpcException {
        ObjectNode params = bridge.newParams();
        params.put("nodeId", nodeId);
        if (value == null) {
            params.putNull("value");
        } else if (value instanceof Double || value instanceof Float) {
            params.put("value", ((Number) value).doubleValue());
        } else if (value instanceof Number) {
            params.put("value", ((Number) value).longValue());
        } else if (value instanceof Boolean) {
            params.put("value", (Boolean) value);
        } else {
            params.put("value", value.toString());
        }
        params.put("dataType", dataType != null ? dataType : "auto");
        logger.info("Write {} to {} as {}", value, nodeId, dataType);
        sessionRequest("write", params, requestTimeoutMs);
    }

    /**
     * @param value to be written into Node (Double)
     */
    public void writeValue(Double value, String nodeId) throws OpcException {
        writeValue(value, nodeId, "Double");
    }

    /**
     * @param value to be written into Node (Int32)
     */
    public void writeValue(Integer value, String nodeId) throws OpcException {
        writeValue(value, nodeId, "Int32");
    }

    /**
     * @param value to be written int Node (String)
     */
    public void writeValue(String value, String nodeId) throws OpcException {
        writeValue(value, nodeId, "String");
    }

    /**
     * @param value to be written int Node (Bool)
     */
    public void writeValue(Boolean value, String nodeId) throws OpcException {
        writeValue(value, nodeId, "Boolean");
    }

    /**
     * @return the OPC UA data type name of the variable, e.g. {@code Double}, {@code Boolean}, {@code String}
     * (the NodeId for non-standard types)
     */
    public String getDataType(String nodeId) throws OpcException {
        List<OpcValue> values = readAttribute(Collections.singletonList(nodeId), "DataType");
        if (values.isEmpty() || values.get(0).getValue() == null) {
            throw new OpcException(OpcException.OPCUA, values.isEmpty() ? null : values.get(0).getStatus(),
                    "No data type for " + nodeId);
        }
        return values.get(0).getValue().toString();
    }

    /**
     * @return true for the numeric OPC UA data types
     */
    public static boolean isNumericType(String dataType) {
        return isIntegerType(dataType) || "Float".equals(dataType) || "Double".equals(dataType);
    }

    /**
     * @return true for the integer OPC UA data types
     */
    public static boolean isIntegerType(String dataType) {
        if (dataType == null) {
            return false;
        }
        switch (dataType) {
            case "SByte":
            case "Byte":
            case "Int16":
            case "UInt16":
            case "Int32":
            case "UInt32":
            case "Int64":
            case "UInt64":
                return true;
            default:
                return false;
        }
    }
}
