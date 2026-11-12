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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpcBridgeTest {

    @Test
    void requestCarriesIdCommandAndParametersAndReturnsResult() throws Exception {
        FakeTransport.Factory factory = new FakeTransport.Factory(request ->
                Collections.singletonList(FakeTransport.ok(request, FakeTransport.json("{'echo':'" + request.path("nodeId").asText() + "'}"))));
        OpcBridge bridge = new OpcBridge(factory);

        ObjectNode params = bridge.newParams();
        params.put("nodeId", "ns=2;s=Device1");
        JsonNode result = bridge.request("browse", params, 1000);

        assertEquals("ns=2;s=Device1", result.path("echo").asText());
        JsonNode sent = factory.requests.get(0);
        assertEquals("browse", sent.path("cmd").asText());
        assertEquals(1, sent.path("id").asLong());
        assertEquals("ns=2;s=Device1", sent.path("nodeId").asText());

        bridge.request("browse", params, 1000);
        assertEquals(2, factory.requests.get(1).path("id").asLong());
        assertEquals(1, factory.opened);
        assertEquals(1, bridge.getGeneration());
    }

    @Test
    void parametersCannotOverrideIdOrCommand() throws Exception {
        FakeTransport.Factory factory = new FakeTransport.Factory(request ->
                Collections.singletonList(FakeTransport.ok(request, FakeTransport.MAPPER.createObjectNode())));
        OpcBridge bridge = new OpcBridge(factory);
        ObjectNode params = bridge.newParams();
        params.put("id", 99);
        params.put("cmd", "other");
        bridge.request("read", params, 1000);
        assertEquals("read", factory.requests.get(0).path("cmd").asText());
        assertEquals(1, factory.requests.get(0).path("id").asLong());
    }

    @Test
    void errorResponseBecomesOpcException() {
        FakeTransport.Factory factory = new FakeTransport.Factory(request ->
                Collections.singletonList(FakeTransport.error(request, "opcua", "BadNodeIdUnknown", "node not found")));
        OpcBridge bridge = new OpcBridge(factory);

        OpcException ex = assertThrows(OpcException.class, () -> bridge.request("read", null, 1000));
        assertEquals(OpcException.OPCUA, ex.getCode());
        assertEquals("BadNodeIdUnknown", ex.getStatus());
        assertEquals("node not found", ex.getMessage());
        assertTrue(ex.isServerStatus());
    }

    @Test
    void errorWithoutStatus() {
        FakeTransport.Factory factory = new FakeTransport.Factory(request ->
                Collections.singletonList(FakeTransport.error(request, "not_connected", null, "connect first")));
        OpcBridge bridge = new OpcBridge(factory);

        OpcException ex = assertThrows(OpcException.class, () -> bridge.request("read", null, 1000));
        assertEquals(OpcException.NOT_CONNECTED, ex.getCode());
        assertNull(ex.getStatus());
        assertFalse(ex.isServerStatus());
    }

    @Test
    void timeoutStopsTheProcessAndTheNextRequestStartsANewOne() throws Exception {
        final boolean[] answer = {false};
        FakeTransport.Factory factory = new FakeTransport.Factory(request -> answer[0]
                ? Collections.singletonList(FakeTransport.ok(request, FakeTransport.MAPPER.createObjectNode()))
                : Collections.<String>emptyList());
        OpcBridge bridge = new OpcBridge(factory);

        long start = System.currentTimeMillis();
        OpcException ex = assertThrows(OpcException.class, () -> bridge.request("history-raw", null, 200));
        assertEquals(OpcException.TIMEOUT, ex.getCode());
        assertTrue(System.currentTimeMillis() - start < 5000);
        assertFalse(factory.last.isAlive());

        answer[0] = true;
        bridge.request("read", null, 1000);
        assertEquals(2, factory.opened);
        assertEquals(2, bridge.getGeneration());
    }

    @Test
    void endOfStreamIsABridgeError() {
        FakeTransport.Factory factory = new FakeTransport.Factory(request -> Collections.<String>singletonList(null));
        OpcBridge bridge = new OpcBridge(factory);

        OpcException ex = assertThrows(OpcException.class, () -> bridge.request("read", null, 1000));
        assertEquals(OpcException.BRIDGE, ex.getCode());
        assertTrue(ex.getMessage().contains("ended"));
    }

    @Test
    void staleAndInvalidLinesAreSkipped() throws Exception {
        FakeTransport.Factory factory = new FakeTransport.Factory(request -> Arrays.asList(
                "this is not json",
                "{\"id\":0,\"ok\":true,\"result\":\"old\"}",
                FakeTransport.ok(request, FakeTransport.json("'current'"))));
        OpcBridge bridge = new OpcBridge(factory);
        assertEquals("current", bridge.request("read", null, 1000).asText());
    }

    @Test
    void closeSendsCloseAndRejectsFurtherRequests() throws Exception {
        FakeTransport.Factory factory = new FakeTransport.Factory(request ->
                Collections.singletonList(FakeTransport.ok(request, FakeTransport.MAPPER.createObjectNode())));
        OpcBridge bridge = new OpcBridge(factory);
        bridge.request("endpoints", null, 1000);
        bridge.close();

        assertEquals(Arrays.asList("endpoints", "close"), factory.commands());
        assertFalse(factory.last.isAlive());
        OpcException ex = assertThrows(OpcException.class, () -> bridge.request("read", null, 1000));
        assertEquals(OpcException.BRIDGE, ex.getCode());
        bridge.close();
    }

    @Test
    void closeWithoutProcessDoesNotStartOne() {
        FakeTransport.Factory factory = new FakeTransport.Factory(request -> Collections.<String>emptyList());
        new OpcBridge(factory).close();
        assertEquals(0, factory.opened);
    }

    /* ---------------- program location ---------------- */

    @Test
    void programFromSystemPropertyWins(@TempDir File dir) throws Exception {
        File property = touch(new File(dir, "a/opcua-bridge"));
        File env = touch(new File(dir, "b/opcua-bridge"));
        assertEquals(property, OpcBridge.findProgram(property.getPath(), env.getPath(), Collections.<File>emptyList()));
        assertEquals(env, OpcBridge.findProgram(null, env.getPath(), Collections.<File>emptyList()));
        assertEquals(env, OpcBridge.findProgram("  ", env.getPath(), Collections.<File>emptyList()));
    }

    @Test
    void configuredProgramMustExist(@TempDir File dir) {
        OpcException ex = assertThrows(OpcException.class,
                () -> OpcBridge.findProgram(new File(dir, "missing").getPath(), null, Collections.<File>emptyList()));
        assertEquals(OpcException.BRIDGE, ex.getCode());
        assertTrue(ex.getMessage().contains("OPC UA bridge not found"));
        assertTrue(ex.getMessage().contains(OpcBridge.PROPERTY_PROGRAM));
    }

    @Test
    void programFoundInBinOfTheJevisHomeAboveTheServiceJar(@TempDir File home) throws Exception {
        File jar = touch(new File(home, "DataCollector/JEDataCollector/target/JEDataCollector.jar"));
        File program = touch(new File(home, "bin/opcua-bridge"));

        List<File> candidates = OpcBridge.defaultCandidates(null, jar, new File(home, "elsewhere"), false);
        assertEquals(program.getAbsoluteFile(), OpcBridge.findProgram(null, null, candidates));
        assertEquals(home.getAbsoluteFile(), OpcBridge.jevisHome(null, program, null).getAbsoluteFile());
    }

    @Test
    void controlCenterFindsTheProgramNextToItsJar(@TempDir File dir) throws Exception {
        File jar = touch(new File(dir, "JEVisControlCenter.jar"));
        File program = touch(new File(dir, "opcua-bridge.exe"));
        touch(new File(dir, "bin/opcua-bridge.exe"));

        List<File> candidates = OpcBridge.defaultCandidates(null, jar, null, true);
        assertEquals(program.getAbsoluteFile(), OpcBridge.findProgram(null, null, candidates));
        assertEquals(dir.getAbsoluteFile(), OpcBridge.jevisHome(null, program, null).getAbsoluteFile());
    }

    @Test
    void jevisHomeEnvironmentComesFirst(@TempDir File dir) throws Exception {
        File home = new File(dir, "home");
        File program = touch(new File(home, "bin/opcua-bridge"));
        touch(new File(dir, "work/bin/opcua-bridge"));

        List<File> candidates = OpcBridge.defaultCandidates(home.getPath(), null, new File(dir, "work"), false);
        assertEquals(program.getAbsoluteFile(), candidates.get(0));
        assertEquals(new File(dir, "work/bin/opcua-bridge").getAbsoluteFile(), candidates.get(candidates.size() - 1));
        assertEquals(home, OpcBridge.jevisHome(home.getPath(), null, null));
    }

    @Test
    void missingProgramListsTheCandidates(@TempDir File dir) {
        List<File> candidates = OpcBridge.defaultCandidates(null, null, dir, false);
        OpcException ex = assertThrows(OpcException.class, () -> OpcBridge.findProgram(null, null, candidates));
        assertTrue(ex.getMessage().contains("opcua-bridge"));
        assertTrue(ex.getMessage().contains("set jevis.opcua.bridge"));
    }

    @Test
    void certDirDefaultsToEtcOpcuaOfTheHome(@TempDir File dir) throws Exception {
        String old = System.getProperty(OpcBridge.PROPERTY_CERT_DIR);
        try {
            System.clearProperty(OpcBridge.PROPERTY_CERT_DIR);
            File program = touch(new File(dir, "bin/opcua-bridge"));
            if (System.getenv(OpcBridge.ENV_JEVIS_HOME) == null) {
                assertEquals(new File(dir, "etc/opcua").getAbsolutePath(), OpcBridge.certDir(program));
            }
            System.setProperty(OpcBridge.PROPERTY_CERT_DIR, "/custom/certs");
            assertEquals("/custom/certs", OpcBridge.certDir(program));
        } finally {
            if (old == null) {
                System.clearProperty(OpcBridge.PROPERTY_CERT_DIR);
            } else {
                System.setProperty(OpcBridge.PROPERTY_CERT_DIR, old);
            }
        }
    }

    private static File touch(File file) throws IOException {
        Files.createDirectories(file.getParentFile().toPath());
        Files.write(file.toPath(), new byte[0]);
        return file;
    }
}
