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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.PermissionExplainer;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.*;

/**
 * In-memory {@link WebEdit.Store} for the tests of the write endpoints: objects, rights per object id, samples and a
 * record of all writes, deletes and user action log entries.
 */
class FakeEditStore implements WebEdit.Store {

    static final ObjectMapper MAPPER = new ObjectMapper();
    static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");
    static final String ACCOUNT = "demo.user";

    final List<JsonObject> objects = new ArrayList<>();
    final Set<Long> hidden = new HashSet<>();
    final Map<String, JsonJEVisClass> classMap = new HashMap<>();
    final Set<Long> write = new HashSet<>();
    final Set<Long> execute = new HashSet<>();
    final Set<Long> delete = new HashSet<>();
    final Set<Long> create = new HashSet<>();
    final Map<String, TreeMap<Long, JsonSample>> samples = new HashMap<>();
    final List<String> writes = new ArrayList<>();
    final List<String> deletes = new ArrayList<>();
    final List<String> logs = new ArrayList<>();
    final List<String> renames = new ArrayList<>();
    final List<Long> dependents = new ArrayList<>();
    final Map<String, byte[]> files = new HashMap<>();
    final List<Long> deletedObjects = new ArrayList<>();
    final List<Long> recycled = new ArrayList<>();
    final List<DateTime> waits = new ArrayList<>();
    String userName = "Demo User";
    boolean failFileWrites = false;
    WebEdit.WorkDay workDay = WebEdit.WorkDay.standard(ZONE);
    /**
     * Stage E8.2: the logged-in user and its groups
     */
    final Set<Long> userAndGroups = new HashSet<>();
    private ResourceWebModules.Lookup lookup;
    private long nextId = 9000;

    // ---------------------------------------------------------------- setup

    static JsonObject obj(long id, String name, String jevisClass, long parent) {
        JsonObject object = new JsonObject();
        object.setId(id);
        object.setName(name);
        object.setJevisClass(jevisClass);
        object.setParent(parent);
        return object;
    }

    FakeEditStore add(JsonObject... added) {
        objects.addAll(Arrays.asList(added));
        lookup = null;
        return this;
    }

    FakeEditStore type(String jevisClass, String inherits, Object... nameAndType) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(jevisClass);
        List<JsonClassRelationship> relationships = new ArrayList<>();
        if (inherits != null) {
            JsonClassRelationship rel = new JsonClassRelationship();
            rel.setStart(jevisClass);
            rel.setEnd(inherits);
            rel.setType(JEVisConstants.ClassRelationship.INHERIT);
            relationships.add(rel);
        }
        jclass.setRelationships(relationships);
        List<JsonType> types = new ArrayList<>();
        for (int i = 0; i + 1 < nameAndType.length; i += 2) {
            JsonType type = new JsonType();
            type.setJevisclass(jevisClass);
            type.setName((String) nameAndType[i]);
            type.setPrimitiveType((Integer) nameAndType[i + 1]);
            types.add(type);
        }
        jclass.setTypes(types);
        classMap.put(jevisClass, jclass);
        return this;
    }

    static String ts(DateTime time) {
        return WebEdit.sampleTs(time);
    }

    FakeEditStore sample(long id, String attribute, DateTime time, String value, String note) {
        JsonSample sample = new JsonSample();
        sample.setTs(ts(time));
        sample.setValue(value);
        sample.setNote(note);
        samples.computeIfAbsent(id + "/" + attribute, k -> new TreeMap<>()).put(time.getMillis(), sample);
        return this;
    }

    JsonSample at(long id, String attribute, DateTime time) {
        TreeMap<Long, JsonSample> map = samples.get(id + "/" + attribute);
        return map != null ? map.get(time.getMillis()) : null;
    }

    int count(long id, String attribute) {
        TreeMap<Long, JsonSample> map = samples.get(id + "/" + attribute);
        return map != null ? map.size() : 0;
    }

    /**
     * Adds a FILE sample with its stored content
     */
    FakeEditStore file(long id, String attribute, DateTime time, String fileName, String content) {
        sample(id, attribute, time, fileName, null);
        files.put(id + "/" + attribute + "/" + time.getMillis(), content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return this;
    }

    /**
     * @return the content of the latest file of the attribute, null if none
     */
    String latestFile(long id, String attribute) {
        JsonSample latest = latest(id, attribute);
        byte[] bytes = latest != null ? file(id, attribute, latest) : null;
        return bytes != null ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    JsonObject object(long id) {
        for (JsonObject o : objects) {
            if (o.getId() == id) return o;
        }
        return null;
    }

    static JsonNode json(WebEdit.Reply reply) throws Exception {
        return MAPPER.readTree(MAPPER.writeValueAsString(reply.body));
    }

    // ---------------------------------------------------------------- store

    @Override
    public ResourceWebModules.Lookup lookup() {
        if (lookup == null) {
            List<JsonObject> readable = new ArrayList<>();
            Map<Long, Long> parents = new HashMap<>();
            for (JsonObject o : objects) {
                if (!hidden.contains(o.getId())) readable.add(o);
                parents.put(o.getId(), o.getParent());
            }
            lookup = new ResourceWebModules.Lookup(objects, readable, parents);
        }
        return lookup;
    }

    @Override
    public ResourceWebRegisters.Classes classes() {
        return new ResourceWebRegisters.Classes(classMap);
    }

    @Override
    public WebEdit.Rights rights() {
        return new WebEdit.Rights() {
            @Override
            public boolean canWrite(JsonObject object) {
                return write.contains(object.getId());
            }

            @Override
            public boolean canExecute(JsonObject object) {
                return execute.contains(object.getId());
            }

            @Override
            public boolean canDelete(JsonObject object) {
                return delete.contains(object.getId());
            }

            @Override
            public boolean canCreate(JsonObject parent, String jevisClass) {
                return create.contains(parent.getId())
                        || (PermissionExplainer.CREATE_EXCEPTION_CLASSES.contains(jevisClass) && execute.contains(parent.getId()));
            }
        };
    }

    @Override
    public String account() {
        return ACCOUNT;
    }

    @Override
    public List<JsonSample> samples(long id, String attribute, DateTime from, DateTime to, long limit) {
        List<JsonSample> result = new ArrayList<>();
        TreeMap<Long, JsonSample> map = samples.get(id + "/" + attribute);
        if (map == null) return result;
        for (JsonSample sample : map.subMap(from.getMillis(), true, to.getMillis(), true).values()) {
            if (result.size() >= limit) break;
            result.add(sample);
        }
        return result;
    }

    @Override
    public JsonSample latest(long id, String attribute) {
        TreeMap<Long, JsonSample> map = samples.get(id + "/" + attribute);
        return map != null && !map.isEmpty() ? map.lastEntry().getValue() : null;
    }

    @Override
    public int write(long id, String attribute, int primitiveType, List<JsonSample> written) {
        for (JsonSample sample : written) {
            long millis = ResourceWebNotes.millis(sample.getTs());
            JsonSample copy = new JsonSample();
            copy.setTs(sample.getTs());
            copy.setValue(sample.getValue());
            copy.setNote(sample.getNote());
            samples.computeIfAbsent(id + "/" + attribute, k -> new TreeMap<>()).put(millis, copy);
            writes.add(id + "/" + attribute + " " + primitiveType + " " + sample.getValue() + " | " + sample.getNote());
        }
        return written.size();
    }

    @Override
    public void delete(long id, String attribute, DateTime from, DateTime to) {
        TreeMap<Long, JsonSample> map = samples.get(id + "/" + attribute);
        if (map != null) map.subMap(from.getMillis(), true, to.getMillis(), true).clear();
        deletes.add(id + "/" + attribute + " " + from.getMillis() + "-" + to.getMillis());
    }

    @Override
    public JsonObject createObject(String name, String jevisClass, long parent) {
        JsonObject object = obj(nextId++, name, jevisClass, parent);
        objects.add(object);
        /* the new object gets the owners (rights) of the parent */
        for (Set<Long> set : Arrays.asList(write, execute, delete, create)) {
            if (set.contains(parent)) set.add(object.getId());
        }
        return object;
    }

    @Override
    public void rename(JsonObject object, String name) {
        renames.add(object.getId() + " " + name);
    }

    @Override
    public void log(SQLDataSource.LOG_EVENT event, String details) {
        logs.add(event + " " + details);
    }

    @Override
    public String unit(long id, String attribute) {
        return "kWh";
    }

    @Override
    public Set<Long> userAndGroups() {
        return userAndGroups;
    }

    @Override
    public WebEdit.WorkDay workDay(JsonObject object) {
        return workDay;
    }

    @Override
    public List<Long> dependents(JsonObject row) {
        return dependents;
    }

    @Override
    public String userName() {
        return userName;
    }

    @Override
    public byte[] file(long id, String attribute, JsonSample sample) {
        if (sample == null) return null;
        return files.get(id + "/" + attribute + "/" + ResourceWebNotes.millis(sample.getTs()));
    }

    @Override
    public int writeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content) throws java.io.IOException {
        if (failFileWrites) throw new java.io.IOException("disk full");
        files.put(id + "/" + attribute + "/" + timestamp.getMillis(), content);
        return write(id, attribute, JEVisConstants.PrimitiveType.FILE,
                new ArrayList<>(Collections.singletonList(WebEdit.sample(timestamp, fileName, null))));
    }

    @Override
    public void deleteObject(JsonObject object) {
        objects.removeIf(o -> o.getId() == object.getId());
        deletedObjects.add(object.getId());
        lookup = null;
    }

    @Override
    public void recycle(JsonObject object) {
        object.setDeleteTS("2026-11-12T10:30:15.000+01:00");
        recycled.add(object.getId());
        lookup = null;
    }

    @Override
    public void waitUntil(DateTime time) {
        waits.add(time);
    }

    /**
     * Input unit labels by {@code <id>/<attribute>} (stage E5)
     */
    final Map<String, String> inputUnits = new HashMap<>();

    @Override
    public ResourceWebRegisters.Data registerData() {
        return new ResourceWebRegisters.Data() {
            @Override
            public Map<String, JsonAttribute> attributes(long id) {
                Map<String, JsonAttribute> attributes = new HashMap<>();
                JsonObject object = object(id);
                if (object == null) return attributes;
                for (JsonType type : classes().types(object.getJevisClass())) {
                    JsonAttribute attribute = new JsonAttribute();
                    attribute.setType(type.getName());
                    attribute.setLatestValue(latest(id, type.getName()));
                    String label = inputUnits.get(id + "/" + type.getName());
                    if (label != null) {
                        JsonUnit unit = new JsonUnit();
                        unit.setLabel(label);
                        attribute.setInputUnit(unit);
                        attribute.setDisplayUnit(unit);
                    }
                    attributes.put(type.getName(), attribute);
                }
                return attributes;
            }

            @Override
            public Integer decimals(long id, Map<Long, Integer> meterDecimalsByTarget) {
                return meterDecimalsByTarget.get(id);
            }
        };
    }
}
