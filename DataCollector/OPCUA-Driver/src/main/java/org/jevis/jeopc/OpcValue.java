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
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * A value with timestamp and status, as returned by the bridge commands {@code read}, {@code history-raw} and
 * {@code history-processed}.
 */
public class OpcValue {

    private static final DateTimeFormatter TS_PARSER = ISODateTimeFormat.dateTimeParser().withOffsetParsed();
    private static final long SEVERITY_MASK = 0xC0000000L;
    private static final long SEVERITY_UNCERTAIN = 0x40000000L;
    private static final long SEVERITY_BAD = 0x80000000L;

    private final String nodeId;
    private final DateTime timestamp;
    private final DateTime serverTimestamp;
    private final Object value;
    private final String dataType;
    private final String status;
    private final long statusCode;

    public OpcValue(String nodeId, DateTime timestamp, DateTime serverTimestamp, Object value, String dataType,
                    String status, long statusCode) {
        this.nodeId = nodeId;
        this.timestamp = timestamp;
        this.serverTimestamp = serverTimestamp;
        this.value = value;
        this.dataType = dataType;
        this.status = status;
        this.statusCode = statusCode;
    }

    /**
     * Value of a {@code read} result (timestamps {@code sourceTimestamp}/{@code serverTimestamp}).
     */
    static OpcValue fromReadJson(JsonNode node) {
        return new OpcValue(
                OpcBridge.text(node, "nodeId"),
                parseTimestamp(OpcBridge.text(node, "sourceTimestamp")),
                parseTimestamp(OpcBridge.text(node, "serverTimestamp")),
                toJava(node.get("value")),
                OpcBridge.text(node, "dataType"),
                OpcBridge.text(node, "status"),
                statusCode(node));
    }

    /**
     * Value of a history result (timestamp {@code ts}).
     */
    static OpcValue fromHistoryJson(String nodeId, JsonNode node) {
        return new OpcValue(
                nodeId,
                parseTimestamp(OpcBridge.text(node, "ts")),
                null,
                toJava(node.get("value")),
                OpcBridge.text(node, "dataType"),
                OpcBridge.text(node, "status"),
                statusCode(node));
    }

    private static long statusCode(JsonNode node) {
        JsonNode code = node.get("statusCode");
        if (code != null && code.isNumber()) {
            return code.asLong() & 0xFFFFFFFFL;
        }
        String status = OpcBridge.text(node, "status");
        if (status == null || status.startsWith("Good")) {
            return 0L;
        } else if (status.startsWith("Uncertain")) {
            return SEVERITY_UNCERTAIN;
        } else {
            return SEVERITY_BAD;
        }
    }

    static DateTime parseTimestamp(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return TS_PARSER.parseDateTime(text);
    }

    /**
     * Converts a JSON value of the protocol into Java: numbers to Long or Double, booleans, strings, arrays to
     * lists, objects to maps, null to null.
     */
    static Object toJava(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isIntegralNumber()) {
            return node.canConvertToLong() ? (Object) node.longValue() : (Object) node.bigIntegerValue();
        } else if (node.isNumber()) {
            return node.doubleValue();
        } else if (node.isTextual()) {
            return node.textValue();
        } else if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : node) {
                list.add(toJava(element));
            }
            return list;
        } else if (node.isObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                map.put(field.getKey(), toJava(field.getValue()));
            }
            return map;
        }
        return node.asText();
    }

    public String getNodeId() {
        return nodeId;
    }

    /**
     * @return the source timestamp (history: the timestamp the server returned), may be null
     */
    public DateTime getTimestamp() {
        return timestamp;
    }

    public DateTime getServerTimestamp() {
        return serverTimestamp;
    }

    /**
     * @return Long, Double, Boolean, String, List or null
     */
    public Object getValue() {
        return value;
    }

    /**
     * @return the OPC UA data type name such as {@code Double}, if the bridge sent it
     */
    public String getDataType() {
        return dataType;
    }

    /**
     * @return the status name, e.g. {@code Good}, {@code BadNoData}
     */
    public String getStatus() {
        return status;
    }

    public long getStatusCode() {
        return statusCode;
    }

    public boolean isGood() {
        return (statusCode & SEVERITY_MASK) == 0;
    }

    public boolean isUncertain() {
        return (statusCode & SEVERITY_MASK) == SEVERITY_UNCERTAIN;
    }

    public boolean isBad() {
        return (statusCode & SEVERITY_BAD) != 0;
    }

    /**
     * The value as JEVis sample value: a Double if the value is a number or its text is a number, else its text.
     *
     * @return Double, String or null if there is no value
     */
    public Object toSampleValue() {
        if (value == null) {
            return null;
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String text = value.toString();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return text;
        }
    }

    @Override
    public String toString() {
        return "OpcValue{" + (nodeId != null ? nodeId + ", " : "") + "ts=" + timestamp + ", value=" + value
                + ", status=" + status + "}";
    }
}
