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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * {@link InvoiceStore} on the database with the rights of the logged-in user ({@link SQLDataSource}). File samples
 * are stored like {@code ResourceSample}: {@code <file dir>/<id>/<attribute>/<UTC yyyyMMddHHmmss>_<file name>}.
 */
public class SqlInvoiceStore implements InvoiceStore {

    private static final Logger logger = LogManager.getLogger(SqlInvoiceStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SQLDataSource ds;
    private Map<Long, JsonObject> readable;
    private Map<Long, List<JsonObject>> children;

    public SqlInvoiceStore(SQLDataSource ds) {
        this.ds = ds;
    }

    private void load() throws Exception {
        if (readable != null) return;
        readable = new HashMap<>();
        children = new HashMap<>();
        Map<Long, Long> parents = new HashMap<>();
        for (JsonRelationship rel : ds.getRelationships(JEVisConstants.ObjectRelationship.PARENT)) {
            parents.put(rel.getFrom(), rel.getTo());
        }
        for (JsonObject object : ds.getUserManager().filterList(ds.getObjects())) {
            if (object.getDeleteTS() != null) continue;
            readable.put(object.getId(), object);
            Long parent = parents.get(object.getId());
            if (parent != null) children.computeIfAbsent(parent, k -> new ArrayList<>()).add(object);
        }
    }

    @Override
    public JsonObject object(long id) throws Exception {
        load();
        return readable.get(id);
    }

    @Override
    public List<JsonObject> children(long id) throws Exception {
        load();
        return new ArrayList<>(children.getOrDefault(id, Collections.emptyList()));
    }

    @Override
    public String latest(long id, String attribute) throws Exception {
        JsonSample sample = ds.getLastSample(id, attribute);
        return sample != null ? sample.getValue() : null;
    }

    @Override
    public JsonSample sampleAt(long id, String attribute, DateTime timestamp) throws Exception {
        List<JsonSample> samples = ds.getSamples(id, attribute, timestamp, timestamp, 1);
        return samples.isEmpty() ? null : samples.get(0);
    }

    @Override
    public boolean canCreate(JsonObject parent, String jevisClass) {
        return ds.getUserManager().canCreateWOE(parent, jevisClass);
    }

    @Override
    public boolean canWrite(JsonObject object) {
        return ds.getUserManager().canWriteWOE(object);
    }

    @Override
    public JsonObject create(JsonObject parent, String jevisClass, String name, Map<String, String> localNames) throws Exception {
        load();
        JsonObject request = new JsonObject();
        request.setName(name);
        request.setJevisClass(jevisClass);
        request.setisPublic(false);
        String i18n = null;
        if (!localNames.isEmpty()) {
            List<JsonI18n> list = new ArrayList<>();
            for (Map.Entry<String, String> entry : localNames.entrySet()) {
                JsonI18n jsonI18n = new JsonI18n();
                jsonI18n.setKey(entry.getKey());
                jsonI18n.setValue(entry.getValue());
                list.add(jsonI18n);
            }
            i18n = MAPPER.writeValueAsString(list);
        }
        JsonObject created = ds.buildObject(request, parent.getId(), i18n);
        ds.logUserAction(SQLDataSource.LOG_EVENT.CREATE_OBJECT, String.format("%s:%s", created.getId(), created.getName()));
        readable.put(created.getId(), created);
        children.computeIfAbsent(parent.getId(), k -> new ArrayList<>()).add(created);
        return created;
    }

    @Override
    public void setSample(long id, String attribute, int primitiveType, DateTime timestamp, String value, String note) throws Exception {
        JsonSample sample = new JsonSample();
        sample.setTs(JsonFactory.sampleDTF.print(timestamp));
        sample.setValue(value);
        sample.setNote(note);
        ds.setSamples(id, attribute, primitiveType, Collections.singletonList(sample));
    }

    @Override
    public void setUnit(long id, String attribute, String unit) throws Exception {
        JsonUnit json = unitJson(unit);
        JsonAttribute att = new JsonAttribute();
        att.setType(attribute);
        att.setInputUnit(json);
        att.setDisplayUnit(json);
        ds.setAttribute(id, att);
    }

    /**
     * The unit as the Control Center's unit chooser stores it. ChartUnits.parseUnit is not used: it fails on the
     * current units library ("The parent unit: L is not an unscaled SI unit").
     */
    static JsonUnit unitJson(String unit) {
        JsonUnit json = new JsonUnit();
        json.setPrefix("NONE");
        json.setLabel(unit);
        switch (unit) {
            case "kWh":
                json.setFormula("kW·h");
                break;
            case "m³":
                json.setFormula("㎥");
                break;
            default:
                json.setFormula("one");
        }
        return json;
    }

    @Override
    public void deleteObject(long id) throws Exception {
        JsonObject object = ds.getObject(id);
        if (object == null) return;
        ds.deleteObject(object);
        readable.remove(id);
        children.values().forEach(list -> list.removeIf(child -> child.getId() == id));
        File files = new File(Config.getFileDir().getAbsolutePath() + File.separator + id);
        if (files.isDirectory()) {
            try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(files.toPath())) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }

    @Override
    public void deleteSample(long id, String attribute, DateTime timestamp) {
        ds.deleteSamplesBetween(id, attribute, timestamp, timestamp);
    }

    @Override
    public String unit(long id, String attribute) {
        JsonAttribute att = ds.getAttribute(id, attribute);
        if (att == null || att.getInputUnit() == null) return null;
        String label = att.getInputUnit().getLabel();
        if (label == null || label.trim().isEmpty() || label.equals("1") || label.equalsIgnoreCase("one")) return null;
        return label.trim();
    }

    @Override
    public void storeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content, String note) throws Exception {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new IOException("Invalid file name");
        }
        File file = new File(Config.getFileDir().getAbsolutePath() + File.separator + id + File.separator + attribute
                + File.separator + DateTimeFormat.forPattern("yyyyMMddHHmmss").withZoneUTC().print(timestamp)
                + "_" + fileName);
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Could not create directory " + file.getParentFile());
        }
        /* only add the sample if the file exists, otherwise the sample points to a missing file */
        Files.write(file.toPath(), content);
        logger.debug("Stored invoice file {}", file);
        setSample(id, attribute, JEVisConstants.PrimitiveType.FILE, timestamp, fileName, note);
    }
}
