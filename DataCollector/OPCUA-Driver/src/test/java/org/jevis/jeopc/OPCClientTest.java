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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OPCClient against an in-memory bridge that simulates a small server.
 */
class OPCClientTest {

    private static final String URL = "opc.tcp://device.example:4840";

    /**
     * Simulated address space: RootFolder / Objects / Device ROOT / {Trend / {Trend_A / {LogHandle, Configuration}}, Info}.
     */
    static class FakeServer implements FakeTransport.Handler {
        final Map<String, List<OpcReference>> children = new HashMap<>();
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, String> dataTypes = new HashMap<>();
        String historyError = null;
        String historyStatus = null;
        boolean failRead = false;

        FakeServer() {
            add("i=84", ref("i=85", "0:Objects", "Object"));
            add("i=84", ref("i=86", "0:Types", "Object"));
            add("i=85", ref("i=2253", "0:Server", "Object"));
            add("i=85", ref("ns=1;i=100", "1:Device ROOT", "Object"));
            add("ns=1;i=100", ref("ns=1;i=200", "1:Trend", "Object"));
            add("ns=1;i=100", ref("ns=1;s=Info", "1:Info", "Variable"));
            add("ns=1;i=200", ref("ns=1;i=210", "1:Trend_A", "Object"));
            add("ns=1;i=210", ref("ns=1;i=211", "1:LogHandle", "Variable"));
            add("ns=1;i=210", ref("ns=1;i=212", "1:Configuration", "Variable"));
            values.put("ns=1;s=Info", "info text");
            values.put("ns=1;i=211", "x.trend-42-a");
            values.put("ns=1;i=212", 12.5);
            dataTypes.put("ns=1;i=212", "Double");
        }

        void add(String parent, OpcReference reference) {
            if (!children.containsKey(parent)) {
                children.put(parent, new ArrayList<OpcReference>());
            }
            children.get(parent).add(reference);
        }

        static OpcReference ref(String nodeId, String browseName, String nodeClass) {
            return new OpcReference(nodeId, browseName, OpcReference.nameOf(browseName), nodeClass, "i=58", "i=35");
        }

        @Override
        public List<String> handle(JsonNode request) {
            String cmd = request.path("cmd").asText();
            ObjectNode empty = FakeTransport.MAPPER.createObjectNode();
            switch (cmd) {
                case "endpoints": {
                    ArrayNode result = FakeTransport.MAPPER.createArrayNode();
                    result.add(FakeTransport.json("{'url':'opc.tcp://device.example:4840','securityPolicyUri':'http://opcfoundation.org/UA/SecurityPolicy#Basic256Sha256',"
                            + "'securityPolicy':'Basic256Sha256','securityMode':'SignAndEncrypt','securityLevel':3,'userTokens':['Anonymous','UserName'],'serverCertificate':'AAEC'}"));
                    result.add(FakeTransport.json("{'url':'opc.tcp://device.example:4840','securityPolicyUri':'http://opcfoundation.org/UA/SecurityPolicy#None',"
                            + "'securityPolicy':'None','securityMode':'None','securityLevel':0,'userTokens':['Anonymous'],'serverCertificate':null}"));
                    return Collections.singletonList(FakeTransport.ok(request, result));
                }
                case "connect": {
                    ObjectNode result = FakeTransport.MAPPER.createObjectNode();
                    result.put("sessionTimeoutMs", 1200000);
                    return Collections.singletonList(FakeTransport.ok(request, result));
                }
                case "browse": {
                    ArrayNode result = FakeTransport.MAPPER.createArrayNode();
                    List<OpcReference> list = children.get(request.path("nodeId").asText());
                    if (list != null) {
                        for (OpcReference reference : list) {
                            ObjectNode node = result.addObject();
                            node.put("nodeId", reference.getNodeId());
                            node.put("browseName", reference.getBrowseName());
                            node.put("displayName", reference.getDisplayName());
                            node.put("nodeClass", reference.getNodeClass());
                            node.put("typeDefinition", reference.getTypeDefinition());
                            node.put("referenceType", reference.getReferenceType());
                        }
                    }
                    return Collections.singletonList(FakeTransport.ok(request, result));
                }
                case "read": {
                    if (failRead) {
                        return Collections.singletonList(FakeTransport.error(request, "opcua", "BadTooManyOperations", "too many"));
                    }
                    ArrayNode result = FakeTransport.MAPPER.createArrayNode();
                    for (JsonNode id : request.path("nodeIds")) {
                        ObjectNode node = result.addObject();
                        node.put("nodeId", id.asText());
                        if ("DataType".equals(request.path("attribute").asText())) {
                            node.put("value", dataTypes.containsKey(id.asText()) ? dataTypes.get(id.asText()) : "String");
                        } else {
                            node.putPOJO("value", values.get(id.asText()));
                        }
                        node.put("sourceTimestamp", "2026-11-12T08:15:00.000Z");
                        node.put("serverTimestamp", "2026-11-12T08:15:00.120Z");
                        boolean known = values.containsKey(id.asText());
                        node.put("status", known ? "Good" : "BadNodeIdUnknown");
                        node.put("statusCode", known ? 0L : 0x80340000L);
                    }
                    return Collections.singletonList(FakeTransport.ok(request, result));
                }
                case "write":
                    return Collections.singletonList(FakeTransport.ok(request, FakeTransport.json("{'status':'Good','statusCode':0}")));
                case "history-raw":
                case "history-processed": {
                    if (historyError != null) {
                        return Collections.singletonList(FakeTransport.error(request, historyError, historyStatus, "history failed"));
                    }
                    return Collections.singletonList(FakeTransport.ok(request, FakeTransport.json("{'values':["
                            + "{'ts':'2026-11-01T00:00:00.000Z','value':1.5,'status':'Good','statusCode':0},"
                            + "{'ts':'2026-11-01T00:15:00.000Z','value':null,'status':'BadNoData','statusCode':2157641728}"
                            + "],'truncated':false}")));
                }
                case "close":
                    return Collections.singletonList(FakeTransport.ok(request, empty));
                default:
                    return Collections.singletonList(FakeTransport.error(request, "bad_request", null, "unknown " + cmd));
            }
        }
    }

    private final FakeServer server = new FakeServer();
    private final FakeTransport.Factory factory = new FakeTransport.Factory(server);

    private OPCClient connectedClient() throws OpcException {
        OPCClient client = new OPCClient(URL, new OpcBridge(factory));
        client.connect();
        return client;
    }

    @Test
    void endpointsAreMapped() throws Exception {
        OPCClient client = new OPCClient(URL, new OpcBridge(factory));
        List<OpcEndpoint> endpoints = client.getEndpoints();
        assertEquals(2, endpoints.size());
        OpcEndpoint secure = endpoints.get(0);
        assertEquals("Basic256Sha256", secure.getSecurityPolicy());
        assertEquals("SignAndEncrypt", secure.getSecurityMode());
        assertEquals(3, secure.getSecurityLevel());
        assertEquals(Arrays.asList("Anonymous", "UserName"), secure.getUserTokens());
        assertEquals("AAEC", secure.getServerCertificate());
        assertFalse(secure.isSecurityNone());
        assertTrue(endpoints.get(1).isSecurityNone());
        assertNull(endpoints.get(1).getServerCertificate());
        assertEquals(URL, factory.lastRequest("endpoints").path("url").asText());
    }

    @Test
    void autoSelectPrefersTheEndpointWithoutSecurity() throws Exception {
        OPCClient client = new OPCClient(URL, new OpcBridge(factory));
        OpcEndpoint endpoint = client.autoSelectEndpoint();
        assertTrue(endpoint.isSecurityNone());

        client.setEndpoints(endpoint);
        client.setIdentification("operator", "secret");
        client.connect();
        JsonNode connect = factory.lastRequest("connect");
        assertEquals(URL, connect.path("url").asText());
        assertEquals("None", connect.path("securityPolicy").asText());
        assertEquals("None", connect.path("securityMode").asText());
        assertEquals("operator", connect.path("user").asText());
        assertEquals("secret", connect.path("password").asText());
        assertTrue(connect.path("trustServerCertificate").asBoolean());
        assertTrue(connect.path("certDir").asText().endsWith("opcua"));
        assertEquals("JEVis DataCollector", connect.path("applicationName").asText());
    }

    @Test
    void connectWithoutEndpointLetsTheBridgeChoose() throws Exception {
        OPCClient client = new OPCClient(URL, new OpcBridge(factory));
        client.setConnectTimeoutMs(1234);
        client.connect();
        JsonNode connect = factory.lastRequest("connect");
        assertEquals("auto", connect.path("securityPolicy").asText());
        assertEquals("auto", connect.path("securityMode").asText());
        assertTrue(connect.path("user").isNull());
        assertTrue(connect.path("password").isNull());
        assertEquals(1234, connect.path("timeoutMs").asLong());
    }

    @Test
    void rawHistoryWithoutFunctionNode() throws Exception {
        OPCClient client = connectedClient();
        OPCUAChannel channel = new OPCUAChannel(" ns=1;s=Meter.Energy ", null, 0);
        DateTime from = new DateTime(2026, 11, 1, 1, 0, 0, DateTimeZone.forID("Europe/Berlin"));
        DateTime until = new DateTime(2026, 11, 2, 1, 0, 0, DateTimeZone.forID("Europe/Berlin"));

        List<OpcValue> values = client.getHistory(channel, from, until);

        JsonNode request = factory.lastRequest("history-raw");
        assertNotNull(request);
        assertNull(factory.lastRequest("history-processed"));
        assertEquals("ns=1;s=Meter.Energy", request.path("nodeId").asText());
        assertEquals("2026-11-01T00:00:00.000Z", request.path("from").asText());
        assertEquals("2026-11-02T00:00:00.000Z", request.path("to").asText());
        assertTrue(request.path("returnBounds").asBoolean());
        assertEquals(0, request.path("maxValues").asInt());
        assertEquals("Source", request.path("timestamps").asText());

        assertEquals(2, values.size());
        assertEquals(1.5, values.get(0).getValue());
        assertTrue(values.get(0).isGood());
        assertEquals(new DateTime(2026, 11, 1, 0, 0, DateTimeZone.UTC).getMillis(), values.get(0).getTimestamp().getMillis());
        assertTrue(values.get(1).isBad());
        assertEquals("BadNoData", values.get(1).getStatus());
        assertEquals(0x809B0000L, values.get(1).getStatusCode());
    }

    @Test
    void processedHistoryWithFunctionNode() throws Exception {
        OPCClient client = connectedClient();
        OPCUAChannel channel = new OPCUAChannel("ns=1;s=Meter.Energy", "i=2342", 900000);

        client.getHistory(channel, new DateTime(2026, 11, 1, 0, 0, DateTimeZone.UTC), new DateTime(2026, 11, 2, 0, 0, DateTimeZone.UTC));

        JsonNode request = factory.lastRequest("history-processed");
        assertNotNull(request);
        assertNull(factory.lastRequest("history-raw"));
        assertEquals("i=2342", request.path("aggregate").asText());
        assertEquals(900000, request.path("intervalMs").asLong());
        assertTrue(request.path("intervalMs").isIntegralNumber());
        assertTrue(request.path("useServerCapabilitiesDefaults").asBoolean());
        assertFalse(request.path("treatUncertainAsBad").asBoolean());
        assertEquals(0, request.path("percentDataBad").asInt());
        assertEquals(0, request.path("percentDataGood").asInt());
        assertFalse(request.path("useSlopedExtrapolation").asBoolean());
    }

    @Test
    void historyWithBadServerStatusIsEmpty() throws Exception {
        OPCClient client = connectedClient();
        server.historyError = "opcua";
        server.historyStatus = "BadNoData";
        List<OpcValue> values = client.getHistory(new OPCUAChannel("ns=1;s=X", "", 0), DateTime.now().minusDays(1), DateTime.now());
        assertTrue(values.isEmpty());
    }

    @Test
    void historyConnectionErrorsAreThrown() throws Exception {
        OPCClient client = connectedClient();
        server.historyError = "not_connected";
        OpcException ex = assertThrows(OpcException.class,
                () -> client.getHistory(new OPCUAChannel("ns=1;s=X", "", 0), DateTime.now().minusDays(1), DateTime.now()));
        assertEquals(OpcException.NOT_CONNECTED, ex.getCode());
    }

    @Test
    void browseOneLevelAsksForObjectsAndVariables() throws Exception {
        OPCClient client = connectedClient();
        List<OpcReference> references = client.browse("i=85");
        assertEquals(2, references.size());
        assertEquals("Device ROOT", references.get(1).getName());
        assertEquals("1:Device ROOT", references.get(1).getBrowseName());
        assertTrue(references.get(1).isObject());

        JsonNode request = factory.lastRequest("browse");
        assertEquals("i=85", request.path("nodeId").asText());
        assertEquals("Hierarchical", request.path("references").asText());
        assertEquals("[\"Object\",\"Variable\"]", request.path("nodeClasses").toString());
    }

    @Test
    void browseWholeTreeByPath() {
        OPCClient client = new OPCClient(URL, new OpcBridge(factory));
        HashMap<String, OpcReference> map = client.browse();
        assertEquals("ns=1;i=100", map.get("/Objects/Device ROOT").getNodeId());
        assertEquals("ns=1;i=211", map.get("/Objects/Device ROOT/Trend/Trend_A/LogHandle").getNodeId());
        assertEquals(9, map.size());
    }

    @Test
    void getRootResolvesThePath() throws Exception {
        OPCClient client = connectedClient();
        PathReferenceDescription root = client.getRoot("/Objects/Device ROOT");
        assertEquals("ns=1;i=100", root.getReferenceDescription().getNodeId());
        assertEquals("/Device ROOT", root.getPath());
        assertNull(root.getDataValue());

        assertNull(client.getRoot("/Objects/Missing"));
    }

    @Test
    void browseBelowRootFolderKeepsTheTreeOrderAndPaths() throws Exception {
        OPCClient client = connectedClient();
        List<PathReferenceDescription> list = new ArrayList<>();
        client.browse(list, "/Objects/Device ROOT/Trend");

        List<String> entries = new ArrayList<>();
        for (PathReferenceDescription description : list) {
            entries.add(description.getPath() + "|" + description.getReferenceDescription().getName());
        }
        assertEquals(Arrays.asList(
                "|Trend",
                "/Trend|Trend_A",
                "/Trend/Trend_A|LogHandle",
                "/Trend/Trend_A|Configuration"), entries);

        assertNull(list.get(0).getDataValue());
        assertNull(list.get(1).getDataValue());
        assertEquals("x.trend-42-a", list.get(2).getDataValue().getValue());
        assertEquals(12.5, list.get(3).getDataValue().getValue());
        assertEquals("ns=1;i=212", list.get(3).getDataValue().getNodeId());

        /* the variables of one level are read in one request */
        JsonNode read = factory.lastRequest("read");
        assertEquals("[\"ns=1;i=211\",\"ns=1;i=212\"]", read.path("nodeIds").toString());
    }

    @Test
    void browseContinuesWhenValuesCannotBeRead() throws Exception {
        OPCClient client = connectedClient();
        server.failRead = true;
        List<PathReferenceDescription> list = new ArrayList<>();
        client.browse(list, "/Objects/Device ROOT/Trend");
        assertEquals(4, list.size());
        assertNull(list.get(2).getDataValue());
    }

    @Test
    void readValueReturnsNullForBadStatus() throws Exception {
        OPCClient client = connectedClient();
        OpcValue value = client.readValue("ns=1;i=212");
        assertEquals(12.5, value.getValue());
        assertEquals("Value", factory.lastRequest("read").path("attribute").asText());
        assertEquals(new DateTime(2026, 11, 12, 8, 15, DateTimeZone.UTC).getMillis(), value.getTimestamp().getMillis());
        assertNull(client.readValue("ns=1;i=999"));
    }

    @Test
    void dataTypeAndWrite() throws Exception {
        OPCClient client = connectedClient();
        assertEquals("Double", client.getDataType("ns=1;i=212"));
        assertEquals("DataType", factory.lastRequest("read").path("attribute").asText());

        client.writeValue(21.5, "ns=1;i=212");
        JsonNode write = factory.lastRequest("write");
        assertEquals("ns=1;i=212", write.path("nodeId").asText());
        assertEquals(21.5, write.path("value").asDouble());
        assertEquals("Double", write.path("dataType").asText());

        client.writeValue(Boolean.TRUE, "ns=1;i=1");
        assertTrue(factory.lastRequest("write").path("value").isBoolean());
        assertEquals("Boolean", factory.lastRequest("write").path("dataType").asText());

        client.writeValue(7, "ns=1;i=1");
        assertEquals("Int32", factory.lastRequest("write").path("dataType").asText());
        assertTrue(factory.lastRequest("write").path("value").isIntegralNumber());

        client.writeValue(3L, "ns=1;i=1", "UInt16");
        assertEquals("UInt16", factory.lastRequest("write").path("dataType").asText());
        assertEquals(3, factory.lastRequest("write").path("value").asInt());

        client.writeValue("text", "ns=1;i=1");
        assertEquals("text", factory.lastRequest("write").path("value").asText());

        client.writeValue(null, "ns=1;i=1", null);
        assertTrue(factory.lastRequest("write").path("value").isNull());
        assertEquals("auto", factory.lastRequest("write").path("dataType").asText());
    }

    @Test
    void reconnectsAfterTheBridgeWasRestarted() throws Exception {
        OPCClient client = connectedClient();
        client.readValue("ns=1;i=212");
        /* simulate a crash of the process */
        factory.last.close();

        client.readValue("ns=1;i=212");

        assertEquals(Arrays.asList("connect", "read", "connect", "read"), factory.commands());
        assertEquals(2, factory.opened);
    }

    @Test
    void integerTypes() {
        assertTrue(OPCClient.isIntegerType("UInt32"));
        assertTrue(OPCClient.isNumericType("Float"));
        assertFalse(OPCClient.isNumericType("String"));
        assertFalse(OPCClient.isIntegerType(null));
    }

    @Test
    void closeStopsTheBridge() throws Exception {
        OPCClient client = connectedClient();
        client.close();
        assertEquals(Arrays.asList("connect", "close"), factory.commands());
    }
}
