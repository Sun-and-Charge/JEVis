/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;

/**
 * The stored JSON of the ISO module items (actions, nonconformities, obligations) as a tree, so a change only touches
 * the keys that are set and keeps everything else as the Control Center wrote it: unknown and legacy keys, the key
 * order, and {@code NaN}/{@code Infinity} unquoted (Gson's {@code serializeSpecialFloatingPointValues}).
 */
final class IsoJsonTree {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
            .disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
            .build();

    private IsoJsonTree() {
    }

    /**
     * @return the JSON object, null if the text is empty or not a JSON object
     */
    static ObjectNode parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            return node != null && node.isObject() ? (ObjectNode) node : null;
        } catch (IOException e) {
            return null;
        }
    }

    static ObjectNode object() {
        return MAPPER.createObjectNode();
    }

    /**
     * @return the tree pretty printed like the Control Center writes it (Gson {@code setPrettyPrinting})
     */
    static String write(ObjectNode root) throws IOException {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }

    /**
     * @return the date as the Control Center's date pickers store it: 00:00 in the time zone, Joda
     * {@code ISODateTimeFormat.dateTime()} ({@code yyyy-MM-dd'T'HH:mm:ss.SSSXXX})
     */
    static String date(LocalDate date, DateTimeZone zone) {
        return ISODateTimeFormat.dateTime().print(date.toDateTimeAtStartOfDay(zone));
    }
}
