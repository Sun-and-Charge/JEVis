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
package org.jevis.rest.invoice;

import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;

import java.util.*;

/**
 * {@link InvoiceStore} in memory for the tests (no database).
 */
class MemoryInvoiceStore implements InvoiceStore {

    final Map<Long, JsonObject> objects = new LinkedHashMap<>();
    final Map<Long, Map<String, String>> localNames = new HashMap<>();
    /** "id/attribute" → samples by time stamp */
    final Map<String, TreeMap<DateTime, JsonSample>> samples = new HashMap<>();
    final Map<String, Integer> primitiveTypes = new HashMap<>();
    final Map<String, String> units = new HashMap<>();
    final Map<String, byte[]> files = new HashMap<>();
    final Set<Long> unreadable = new HashSet<>();
    final Set<Long> noCreate = new HashSet<>();
    final Set<Long> noWrite = new HashSet<>();
    int writes;
    private long nextId = 1000;

    JsonObject add(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        objects.put(id, object);
        return object;
    }

    private static String key(long id, String attribute) {
        return id + "/" + attribute;
    }

    JsonSample last(long id, String attribute) {
        TreeMap<DateTime, JsonSample> map = samples.get(key(id, attribute));
        return map == null || map.isEmpty() ? null : map.lastEntry().getValue();
    }

    List<JsonObject> ofClass(String jevisClass) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject object : objects.values()) {
            if (jevisClass.equals(object.getJevisClass())) result.add(object);
        }
        return result;
    }

    @Override
    public JsonObject object(long id) {
        JsonObject object = objects.get(id);
        if (object == null || unreadable.contains(id) || object.getDeleteTS() != null) return null;
        return object;
    }

    @Override
    public List<JsonObject> children(long id) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject object : objects.values()) {
            if (object.getParent() == id && object(object.getId()) != null) result.add(object);
        }
        return result;
    }

    @Override
    public String latest(long id, String attribute) {
        JsonSample sample = last(id, attribute);
        return sample != null ? sample.getValue() : null;
    }

    @Override
    public JsonSample sampleAt(long id, String attribute, DateTime timestamp) {
        TreeMap<DateTime, JsonSample> map = samples.get(key(id, attribute));
        if (map == null) return null;
        for (Map.Entry<DateTime, JsonSample> entry : map.entrySet()) {
            if (entry.getKey().isEqual(timestamp)) return entry.getValue();
        }
        return null;
    }

    @Override
    public boolean canCreate(JsonObject parent, String jevisClass) {
        return !noCreate.contains(parent.getId());
    }

    @Override
    public boolean canWrite(JsonObject object) {
        return !noWrite.contains(object.getId());
    }

    @Override
    public JsonObject create(JsonObject parent, String jevisClass, String name, Map<String, String> names) {
        writes++;
        JsonObject object = add(nextId++, name, jevisClass, parent.getId());
        localNames.put(object.getId(), new LinkedHashMap<>(names));
        return object;
    }

    @Override
    public void setSample(long id, String attribute, int primitiveType, DateTime timestamp, String value, String note) {
        if (attribute.equals(failOnAttribute)) throw new IllegalStateException("test failure");
        writes++;
        JsonSample sample = new JsonSample();
        sample.setTs(timestamp.toString());
        sample.setValue(value);
        sample.setNote(note);
        TreeMap<DateTime, JsonSample> map = samples.computeIfAbsent(key(id, attribute), k -> new TreeMap<>());
        map.keySet().removeIf(ts -> ts.isEqual(timestamp));
        map.put(timestamp, sample);
        primitiveTypes.put(key(id, attribute), primitiveType);
    }

    @Override
    public void setUnit(long id, String attribute, String unit) {
        writes++;
        units.put(key(id, attribute), unit);
    }

    @Override
    public String unit(long id, String attribute) {
        return units.get(key(id, attribute));
    }

    /** setSample throws for this attribute (to test the rollback) */
    String failOnAttribute;

    @Override
    public void deleteObject(long id) {
        objects.remove(id);
        localNames.remove(id);
        samples.keySet().removeIf(k -> k.startsWith(id + "/") || k.startsWith(id + ":"));
        files.keySet().removeIf(k -> k.startsWith(id + "/") || k.startsWith(id + ":"));
    }

    @Override
    public void deleteSample(long id, String attribute, DateTime timestamp) {
        TreeMap<DateTime, JsonSample> map = samples.get(key(id, attribute));
        if (map != null) map.keySet().removeIf(ts -> ts.isEqual(timestamp));
    }

    @Override
    public void storeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content, String note) {
        files.put(key(id, attribute) + "/" + fileName, content);
        setSample(id, attribute, 3, timestamp, fileName, note);
    }
}
