/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.tools;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import javafx.scene.paint.Color;

import java.io.IOException;

/**
 * Jackson (de)serializer for {@link Color} as text ({@code 0xrrggbbaa}, as {@code Color.toString()} and
 * {@code Color.valueOf} use it). Java 9+ does not let Jackson look into the JavaFX class itself (J25).
 */
public final class FxColorJson {
    private FxColorJson() {
    }

    public static class Serializer extends JsonSerializer<Color> {
        @Override
        public void serialize(Color value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(value.toString());
        }
    }

    public static class Deserializer extends JsonDeserializer<Color> {
        @Override
        public Color deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            JsonNode node = parser.getCodec().readTree(parser);
            if (node == null || node.isNull()) return null;
            if (node.isTextual()) {
                String text = node.asText().trim();
                return text.isEmpty() ? null : Color.valueOf(text);
            }
            if (node.isObject() && node.has("red")) {
                /* written by bean serialization of the Color getters */
                return new Color(node.path("red").asDouble(), node.path("green").asDouble(),
                        node.path("blue").asDouble(), node.path("opacity").asDouble(1));
            }
            return null;
        }
    }
}
