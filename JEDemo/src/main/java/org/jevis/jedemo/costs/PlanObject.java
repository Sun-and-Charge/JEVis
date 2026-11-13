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
package org.jevis.jedemo.costs;

import org.jevis.jedemo.Ref;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An object of the setup plan of the demo cost cases: an existing object that gets new samples ({@code id} set) or a
 * new one (created below its {@link Parent}). Referenced by its symbolic key ({@code @key}) in the templates,
 * configurations and invoice cases until step 3 has created it.
 */
public final class PlanObject {

    final String key;
    final Long id;
    final String jevisClass;
    final String name;
    final Parent parent;
    final Map<String, Attr> attributes = new LinkedHashMap<>();
    /**
     * history profile of {@link History} for the "Value" of a new data row
     */
    String history;
    /**
     * copy the attribute settings of this existing object first (e.g. a clean data row with the wanted aggregation)
     */
    Long copyAttributesFrom;
    String note;

    private PlanObject(String key, Long id, String jevisClass, String name, Parent parent) {
        this.key = key;
        this.id = id;
        this.jevisClass = jevisClass;
        this.name = name;
        this.parent = parent;
    }

    /**
     * An object to create.
     */
    static PlanObject create(String key, String jevisClass, String name, Parent parent) {
        return new PlanObject(key, null, jevisClass, name, parent);
    }

    /**
     * An existing object that gets new samples.
     */
    static PlanObject update(String key, long id, String jevisClass, String name) {
        return new PlanObject(key, id, jevisClass, name, null);
    }

    public String getKey() {
        return key;
    }

    public Long getId() {
        return id;
    }

    public String getJevisClass() {
        return jevisClass;
    }

    public String getName() {
        return name;
    }

    public Parent getParent() {
        return parent;
    }

    public Map<String, Attr> getAttributes() {
        return attributes;
    }

    public String getHistory() {
        return history;
    }

    /**
     * @return the reference of the object: its id if it exists, else its key
     */
    public Ref ref() {
        return id != null ? Ref.id(id) : Ref.key(key);
    }

    PlanObject unit(String attribute, String unit) {
        attr(attribute).unit = unit;
        return this;
    }

    /**
     * a setting written once at setup time
     */
    PlanObject set(String attribute, Object value) {
        attr(attribute).value = value;
        return this;
    }

    PlanObject sample(String attribute, ZonedDateTime ts, Object value) {
        attr(attribute).samples.add(new Sample(ts, value));
        return this;
    }

    PlanObject history(String profile) {
        this.history = profile;
        return this;
    }

    PlanObject copyAttributesFrom(long id) {
        this.copyAttributesFrom = id;
        return this;
    }

    PlanObject note(String note) {
        this.note = note;
        return this;
    }

    Attr attr(String attribute) {
        return attributes.computeIfAbsent(attribute, a -> new Attr());
    }

    /**
     * Values of one attribute: a unit, a setting written at setup time and/or samples with time stamps.
     */
    public static final class Attr {
        String unit;
        Object value;
        final List<Sample> samples = new ArrayList<>();

        public String getUnit() {
            return unit;
        }

        public Object getValue() {
            return value;
        }

        public List<Sample> getSamples() {
            return samples;
        }
    }

    public static final class Sample {
        final ZonedDateTime ts;
        final Object value;

        Sample(ZonedDateTime ts, Object value) {
            this.ts = ts;
            this.value = value;
        }

        public ZonedDateTime getTs() {
            return ts;
        }

        public Object getValue() {
            return value;
        }
    }

    /**
     * Where a new object is created: below an object found by path (names from the organisation, " / " separated),
     * below a plan object (key), below an object id, or next to an object (same parent).
     */
    public static final class Parent {
        final String kind;
        final Object value;

        private Parent(String kind, Object value) {
            this.kind = kind;
            this.value = value;
        }

        static Parent path(String path) {
            return new Parent("path", path);
        }

        static Parent key(String key) {
            return new Parent("key", key);
        }

        static Parent id(long id) {
            return new Parent("id", id);
        }

        static Parent parentOf(long id) {
            return new Parent("parentOf", id);
        }

        public String getKind() {
            return kind;
        }

        public Object getValue() {
            return value;
        }
    }
}
