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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Connection to one {@code opcua-bridge} process (see docs/OPCUA-BRIDGE.md): JSON lines over stdin/stdout, one
 * response per request, in order.
 * <p>
 * The process is started on the first request. If it ends or a request times out, the process is stopped and a new
 * one is started on the next request; {@link #getGeneration()} then changes, so the caller knows that the session
 * is gone and has to connect again.
 */
public class OpcBridge implements AutoCloseable {

    /** System property with the path of the bridge program. */
    public static final String PROPERTY_PROGRAM = "jevis.opcua.bridge";
    /** Environment variable with the path of the bridge program. */
    public static final String ENV_PROGRAM = "JEVIS_OPCUA_BRIDGE";
    /** System property with the directory of the client certificate. */
    public static final String PROPERTY_CERT_DIR = "jevis.opcua.certdir";
    /** Environment variable with the JEVis home directory (as in /etc/jevis/jevis.env). */
    public static final String ENV_JEVIS_HOME = "JEVIS_HOME";
    public static final String PROGRAM_NAME = "opcua-bridge";

    private static final Logger logger = LogManager.getLogger(OpcBridge.class);
    private static final Logger processLogger = LogManager.getLogger(OpcBridge.class.getName() + ".process");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long CLOSE_TIMEOUT_MS = 5000;

    private final TransportFactory factory;
    private Transport transport;
    private long lastId = 0;
    private int generation = 0;
    private boolean closed = false;

    /**
     * A bridge that starts the program found by {@link #findProgram()}.
     */
    public OpcBridge() {
        this(new TransportFactory() {
            @Override
            public Transport open() throws OpcException {
                File program = findProgram();
                return ProcessTransport.start(Collections.singletonList(program.getAbsolutePath()));
            }
        });
    }

    /**
     * A bridge with its own transport, e.g. in-memory for tests.
     */
    public OpcBridge(TransportFactory factory) {
        this.factory = factory;
    }

    static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * @return a new empty object for request parameters
     */
    public ObjectNode newParams() {
        return MAPPER.createObjectNode();
    }

    /**
     * Number of processes started so far. Changes when the process was restarted, i.e. the session was lost.
     */
    public synchronized int getGeneration() {
        return generation;
    }

    /**
     * Starts the process if it is not running.
     *
     * @return the generation, see {@link #getGeneration()}
     */
    public synchronized int start() throws OpcException {
        if (closed) {
            throw new OpcException(OpcException.BRIDGE, null, "OPC UA bridge is closed");
        }
        ensureTransport();
        return generation;
    }

    /**
     * Sends one request and waits for its response.
     *
     * @param cmd       the command, e.g. {@code browse}
     * @param params    parameters of the command, may be null
     * @param timeoutMs time to wait for the response
     * @return the {@code result} of the response (may be a null node)
     * @throws OpcException error response, timeout or bridge problem
     */
    public synchronized JsonNode request(String cmd, ObjectNode params, long timeoutMs) throws OpcException {
        if (closed) {
            throw new OpcException(OpcException.BRIDGE, null, "OPC UA bridge is closed");
        }
        Transport current = ensureTransport();

        long id = ++lastId;
        ObjectNode request = MAPPER.createObjectNode();
        request.put("id", id);
        request.put("cmd", cmd);
        if (params != null) {
            request.setAll(params);
            request.put("id", id);
            request.put("cmd", cmd);
        }

        String line;
        try {
            line = MAPPER.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            throw new OpcException(OpcException.BAD_REQUEST, "Cannot write request " + cmd, ex);
        }
        logger.debug("-> {} #{}", cmd, id);

        try {
            current.send(line);
        } catch (IOException ex) {
            stopTransport();
            throw new OpcException(OpcException.BRIDGE, "OPC UA bridge does not accept requests: " + ex.getMessage(), ex);
        }

        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            long remaining = deadline - System.currentTimeMillis();
            String responseLine;
            try {
                if (remaining <= 0) {
                    throw new TimeoutException();
                }
                responseLine = current.receive(remaining);
            } catch (TimeoutException ex) {
                logger.warn("OPC UA bridge request {} #{} timed out after {} ms, restarting the bridge", cmd, id, timeoutMs);
                stopTransport();
                throw new OpcException(OpcException.TIMEOUT, null, "OPC UA request '" + cmd + "' timed out after " + timeoutMs + " ms");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                stopTransport();
                throw new OpcException(OpcException.BRIDGE, "Interrupted while waiting for the OPC UA bridge", ex);
            } catch (IOException ex) {
                stopTransport();
                throw new OpcException(OpcException.BRIDGE, "Cannot read from the OPC UA bridge: " + ex.getMessage(), ex);
            }

            if (responseLine == null) {
                stopTransport();
                throw new OpcException(OpcException.BRIDGE, null, "OPC UA bridge ended unexpectedly during '" + cmd + "'");
            }

            JsonNode response;
            try {
                response = MAPPER.readTree(responseLine);
            } catch (IOException ex) {
                logger.warn("OPC UA bridge sent an invalid line, ignored: {}", abbreviate(responseLine));
                continue;
            }
            if (response == null || !response.isObject()) {
                logger.warn("OPC UA bridge sent an invalid line, ignored: {}", abbreviate(responseLine));
                continue;
            }

            long responseId = response.path("id").asLong(-1);
            if (responseId != id) {
                /* late answer to a request that timed out before */
                logger.debug("Ignoring response #{} while waiting for #{}", responseId, id);
                continue;
            }

            logger.debug("<- {} #{} ok={}", cmd, id, response.path("ok").asBoolean(false));
            if (response.path("ok").asBoolean(false)) {
                JsonNode result = response.get("result");
                return result != null ? result : MAPPER.nullNode();
            }

            JsonNode error = response.path("error");
            throw new OpcException(
                    text(error, "code"),
                    text(error, "status"),
                    error.hasNonNull("message") ? error.get("message").asText() : "OPC UA request '" + cmd + "' failed");
        }
    }

    private Transport ensureTransport() throws OpcException {
        if (transport != null && transport.isAlive()) {
            return transport;
        }
        if (transport != null) {
            logger.warn("OPC UA bridge process ended, starting a new one");
            stopTransport();
        }
        try {
            transport = factory.open();
        } catch (IOException ex) {
            throw new OpcException(OpcException.BRIDGE, "Cannot start the OPC UA bridge: " + ex.getMessage(), ex);
        }
        generation++;
        return transport;
    }

    private void stopTransport() {
        if (transport != null) {
            try {
                transport.close();
            } catch (Exception ex) {
                logger.debug("Error while stopping the OPC UA bridge", ex);
            }
            transport = null;
        }
    }

    /**
     * Sends {@code close} and stops the process.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (transport != null && transport.isAlive()) {
            try {
                request("close", null, CLOSE_TIMEOUT_MS);
            } catch (Exception ex) {
                logger.debug("OPC UA bridge close: {}", ex.getMessage());
            }
        }
        stopTransport();
        closed = true;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText();
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /* ---------------------------------------------------------------------------------------------------------- */
    /* Program and directories                                                                                    */
    /* ---------------------------------------------------------------------------------------------------------- */

    /**
     * Finds the bridge program: system property {@value #PROPERTY_PROGRAM}, environment variable
     * {@value #ENV_PROGRAM}, else {@code bin/opcua-bridge} ({@code .exe} on Windows) in the JEVis home.
     *
     * @throws OpcException if it is not found
     */
    public static File findProgram() throws OpcException {
        return findProgram(System.getProperty(PROPERTY_PROGRAM), System.getenv(ENV_PROGRAM),
                defaultCandidates(System.getenv(ENV_JEVIS_HOME), codeLocation(), new File(System.getProperty("user.dir")), isWindows()));
    }

    static File findProgram(String property, String env, List<File> candidates) throws OpcException {
        if (property != null && !property.trim().isEmpty()) {
            return existing(new File(property.trim()), "system property " + PROPERTY_PROGRAM);
        }
        if (env != null && !env.trim().isEmpty()) {
            return existing(new File(env.trim()), "environment variable " + ENV_PROGRAM);
        }
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                logger.debug("Using OPC UA bridge {}", candidate);
                return candidate;
            }
        }
        throw new OpcException(OpcException.BRIDGE, null, "OPC UA bridge not found: " + candidates
                + ", set " + PROPERTY_PROGRAM);
    }

    private static File existing(File file, String source) throws OpcException {
        if (!file.isFile()) {
            throw new OpcException(OpcException.BRIDGE, null, "OPC UA bridge not found: " + file + " (" + source
                    + "), set " + PROPERTY_PROGRAM);
        }
        return file;
    }

    /**
     * The places to look for the program, in this order: {@code $JEVIS_HOME/bin}, the directory of the jar itself
     * (Control Center), then {@code bin/} in the jar directory and its parents (the services run from
     * {@code <home>/<module>/target/*.jar}), then {@code bin/} in the working directory.
     */
    static List<File> defaultCandidates(String jevisHome, File codeLocation, File workingDir, boolean windows) {
        String name = PROGRAM_NAME + (windows ? ".exe" : "");
        List<File> candidates = new ArrayList<>();
        if (jevisHome != null && !jevisHome.trim().isEmpty()) {
            candidates.add(new File(new File(jevisHome.trim(), "bin"), name));
        }
        if (codeLocation != null) {
            File dir = codeLocation.isFile() ? codeLocation.getParentFile() : codeLocation;
            if (dir != null) {
                candidates.add(new File(dir, name));
                for (int i = 0; i < 4 && dir != null; i++) {
                    candidates.add(new File(new File(dir, "bin"), name));
                    dir = dir.getParentFile();
                }
            }
        }
        if (workingDir != null) {
            candidates.add(new File(new File(workingDir, "bin"), name));
        }
        List<File> unique = new ArrayList<>();
        for (File candidate : candidates) {
            File normalized = candidate.getAbsoluteFile();
            if (!unique.contains(normalized)) {
                unique.add(normalized);
            }
        }
        return unique;
    }

    /**
     * Directory for the client certificate: system property {@value #PROPERTY_CERT_DIR}, else
     * {@code <jevis home>/etc/opcua}; the JEVis home is {@code $JEVIS_HOME}, else the parent of the {@code bin}
     * directory the program was found in, else the working directory.
     */
    public static String certDir(File program) {
        String property = System.getProperty(PROPERTY_CERT_DIR);
        if (property != null && !property.trim().isEmpty()) {
            return property.trim();
        }
        return new File(new File(jevisHome(System.getenv(ENV_JEVIS_HOME), program,
                new File(System.getProperty("user.dir"))), "etc"), "opcua").getAbsolutePath();
    }

    static File jevisHome(String jevisHomeEnv, File program, File workingDir) {
        if (jevisHomeEnv != null && !jevisHomeEnv.trim().isEmpty()) {
            return new File(jevisHomeEnv.trim());
        }
        if (program != null) {
            File dir = program.getAbsoluteFile().getParentFile();
            if (dir != null && "bin".equals(dir.getName()) && dir.getParentFile() != null) {
                return dir.getParentFile();
            }
            if (dir != null) {
                return dir;
            }
        }
        return workingDir;
    }

    private static File codeLocation() {
        try {
            CodeSource source = OpcBridge.class.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) {
                return new File(source.getLocation().toURI());
            }
        } catch (URISyntaxException | SecurityException | IllegalArgumentException ex) {
            logger.debug("No code location: {}", ex.getMessage());
        }
        return null;
    }

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /* ---------------------------------------------------------------------------------------------------------- */
    /* Transport                                                                                                  */
    /* ---------------------------------------------------------------------------------------------------------- */

    /**
     * Line transport to a bridge.
     */
    public interface Transport extends Closeable {

        /**
         * Sends one request line (without line break).
         */
        void send(String line) throws IOException;

        /**
         * Waits for the next response line.
         *
         * @return the line, or null at the end of the stream
         * @throws TimeoutException if no line arrived in time
         */
        String receive(long timeoutMs) throws IOException, InterruptedException, TimeoutException;

        boolean isAlive();
    }

    /**
     * Creates the transport when the bridge needs one (first request, after a crash or timeout).
     */
    public interface TransportFactory {
        Transport open() throws IOException, OpcException;
    }

    /**
     * Transport to a child process; stderr goes to the logger {@code org.jevis.jeopc.OpcBridge.process}.
     */
    public static class ProcessTransport implements Transport {

        private static final String EOF = new String("<eof>");
        private final Process process;
        private final BufferedWriter writer;
        private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
        private volatile boolean eof = false;

        private ProcessTransport(Process process) {
            this.process = process;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            startThread("opcua-bridge-stdout", new Runnable() {
                @Override
                public void run() {
                    readStdout(process.getInputStream());
                }
            });
            startThread("opcua-bridge-stderr", new Runnable() {
                @Override
                public void run() {
                    drainStderr(process.getErrorStream());
                }
            });
        }

        /**
         * Starts the program with the given command line.
         */
        public static ProcessTransport start(List<String> command) throws OpcException {
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.redirectErrorStream(false);
                logger.info("Starting OPC UA bridge {}", command.get(0));
                return new ProcessTransport(builder.start());
            } catch (IOException ex) {
                throw new OpcException(OpcException.BRIDGE, "Cannot start the OPC UA bridge " + command.get(0)
                        + ": " + ex.getMessage(), ex);
            }
        }

        private static void startThread(String name, Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            thread.start();
        }

        private void readStdout(InputStream in) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        lines.add(line);
                    }
                }
            } catch (IOException ex) {
                logger.debug("OPC UA bridge stdout closed: {}", ex.getMessage());
            } finally {
                eof = true;
                lines.add(EOF);
            }
        }

        private static void drainStderr(InputStream in) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    processLogger.info("opcua-bridge: {}", line);
                }
            } catch (IOException ex) {
                logger.debug("OPC UA bridge stderr closed: {}", ex.getMessage());
            }
        }

        @Override
        public synchronized void send(String line) throws IOException {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        }

        @Override
        public String receive(long timeoutMs) throws InterruptedException, TimeoutException {
            String line = lines.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (line == null) {
                throw new TimeoutException();
            }
            if (line == EOF) {
                lines.add(EOF);
                return null;
            }
            return line;
        }

        @Override
        public boolean isAlive() {
            return !eof && process.isAlive();
        }

        @Override
        public void close() {
            try {
                writer.close();
            } catch (IOException ex) {
                logger.debug("Closing OPC UA bridge stdin: {}", ex.getMessage());
            }
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }
}
