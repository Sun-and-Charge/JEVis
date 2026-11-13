/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.Objects;

/**
 * Reference to a JEVis object: an object id, or (in the setup plan of the demo cost cases, before the objects exist) a
 * symbolic key. In JSON an id is a number ({@code 23510}) and a key a string starting with {@code @}
 * ({@code "@B.supplier"}); a string with digits only is read as an id.
 */
@JsonSerialize(using = Ref.Serializer.class)
@JsonDeserialize(using = Ref.Deserializer.class)
public final class Ref {
    private final Long id;
    private final String key;

    private Ref(Long id, String key) {
        this.id = id;
        this.key = key;
    }

    public static Ref id(long id) {
        return new Ref(id, null);
    }

    /**
     * @param key symbolic key without the leading {@code @}
     */
    public static Ref key(String key) {
        return new Ref(null, Objects.requireNonNull(key));
    }

    /**
     * @return the reference of a JSON text: digits = id, {@code @key} = key, else null
     */
    public static Ref parse(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        if (t.startsWith("@")) return t.length() > 1 ? key(t.substring(1)) : null;
        try {
            return id(Long.parseLong(t));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the object id, null for an unresolved key
     */
    public Long getId() {
        return id;
    }

    /**
     * @return the symbolic key (without {@code @}), null for an id
     */
    public String getKey() {
        return key;
    }

    public boolean isResolved() {
        return id != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ref)) return false;
        Ref ref = (Ref) o;
        return Objects.equals(id, ref.id) && Objects.equals(key, ref.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, key);
    }

    @Override
    public String toString() {
        return id != null ? String.valueOf(id) : "@" + key;
    }

    static final class Serializer extends JsonSerializer<Ref> {
        @Override
        public void serialize(Ref value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            if (value.id != null) {
                gen.writeNumber(value.id);
            } else {
                gen.writeString("@" + value.key);
            }
        }
    }

    static final class Deserializer extends JsonDeserializer<Ref> {
        @Override
        public Ref deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonToken token = p.currentToken();
            if (token == JsonToken.VALUE_NUMBER_INT) return id(p.getLongValue());
            if (token == JsonToken.VALUE_STRING) {
                Ref ref = parse(p.getText());
                if (ref == null) throw ctxt.weirdStringException(p.getText(), Ref.class, "object id or @key expected");
                return ref;
            }
            if (token == JsonToken.VALUE_NULL) return null;
            return (Ref) ctxt.handleUnexpectedToken(Ref.class, p);
        }
    }
}
