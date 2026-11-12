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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * In-memory bridge for tests: every request line is passed to a {@link Handler} that returns the response lines.
 */
class FakeTransport implements OpcBridge.Transport {

    static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String EOF = new String("<eof>");

    interface Handler {
        /**
         * @return the lines to answer with (none: no answer, a null element: end of stream)
         */
        List<String> handle(JsonNode request) throws Exception;
    }

    final List<JsonNode> requests;
    private final Handler handler;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private volatile boolean alive = true;

    FakeTransport(Handler handler, List<JsonNode> requests) {
        this.handler = handler;
        this.requests = requests;
    }

    /**
     * Factory that creates a new transport per start and records all requests in one list.
     */
    static class Factory implements OpcBridge.TransportFactory {
        final List<JsonNode> requests = Collections.synchronizedList(new ArrayList<JsonNode>());
        final Handler handler;
        int opened = 0;
        FakeTransport last;

        Factory(Handler handler) {
            this.handler = handler;
        }

        @Override
        public OpcBridge.Transport open() {
            opened++;
            last = new FakeTransport(handler, requests);
            return last;
        }

        List<String> commands() {
            List<String> commands = new ArrayList<>();
            synchronized (requests) {
                for (JsonNode request : requests) {
                    commands.add(request.path("cmd").asText());
                }
            }
            return commands;
        }

        JsonNode lastRequest(String cmd) {
            synchronized (requests) {
                for (int i = requests.size() - 1; i >= 0; i--) {
                    if (cmd.equals(requests.get(i).path("cmd").asText())) {
                        return requests.get(i);
                    }
                }
            }
            return null;
        }
    }

    @Override
    public void send(String line) throws IOException {
        if (!alive) {
            throw new IOException("closed");
        }
        JsonNode request = MAPPER.readTree(line);
        requests.add(request);
        List<String> answer;
        try {
            answer = handler.handle(request);
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        for (String response : answer) {
            lines.add(response == null ? EOF : response);
        }
    }

    @Override
    public String receive(long timeoutMs) throws InterruptedException, TimeoutException {
        String line = lines.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (line == null) {
            throw new TimeoutException();
        }
        if (line == EOF) {
            alive = false;
            return null;
        }
        return line;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void close() {
        alive = false;
    }

    /* ---- helpers to build responses ---- */

    static String ok(JsonNode request, JsonNode result) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("id", request.path("id").asLong());
        response.put("ok", true);
        response.set("result", result);
        return response.toString();
    }

    static String error(JsonNode request, String code, String status, String message) {
        ObjectNode response = MAPPER.createObjectNode();
        response.put("id", request.path("id").asLong());
        response.put("ok", false);
        ObjectNode error = response.putObject("error");
        error.put("code", code);
        if (status == null) {
            error.putNull("status");
        } else {
            error.put("status", status);
        }
        error.put("message", message);
        return response.toString();
    }

    static JsonNode json(String text) {
        try {
            return MAPPER.readTree(text.replace('\'', '"'));
        } catch (IOException ex) {
            throw new IllegalArgumentException(text, ex);
        }
    }
}
