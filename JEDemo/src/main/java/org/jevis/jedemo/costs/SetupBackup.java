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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Backup of the objects the cost demo setup changes or deletes: per object a JSON file with name, class, parents,
 * children and every attribute with its units and all samples (time stamp, value, note); file samples are stored
 * next to it under {@code files/<id>/<attribute>/}. {@code manifest.json} lists the objects and why they were backed
 * up. Every write is checked, an exception aborts the setup before anything is changed.
 */
final class SetupBackup {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private final Path directory;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ArrayNode manifest = F.arrayNode();
    private final Set<Long> done = new HashSet<>();
    private int files;

    SetupBackup(Path directory) throws IOException {
        this.directory = directory;
        if (Files.exists(directory) && !Files.isDirectory(directory)) {
            throw new IOException("Backup path " + directory + " exists and is no directory");
        }
        Files.createDirectories(directory.resolve("objects"));
        if (!Files.isWritable(directory)) throw new IOException("Backup directory " + directory + " is not writable");
    }

    Path getDirectory() {
        return directory;
    }

    boolean contains(long id) {
        return done.contains(id);
    }

    int objectCount() {
        return done.size();
    }

    int fileCount() {
        return files;
    }

    /**
     * Backs up the object (and with {@code withChildren} all objects below it).
     */
    void backup(JEVisObject object, boolean withChildren, String reason) throws IOException, JEVisException {
        List<JEVisObject> todo = new ArrayList<>();
        todo.add(object);
        while (!todo.isEmpty()) {
            JEVisObject o = todo.remove(0);
            if (!done.add(o.getID())) continue;
            write(o);
            ObjectNode entry = manifest.addObject();
            entry.put("id", o.getID());
            entry.put("class", o.getJEVisClassName());
            entry.put("name", o.getName());
            entry.put("reason", o == object ? reason : "below " + object.getID() + ": " + reason);
            if (withChildren) todo.addAll(o.getChildren());
        }
        writeManifest();
    }

    private void write(JEVisObject o) throws IOException, JEVisException {
        ObjectNode node = F.objectNode();
        node.put("id", o.getID());
        node.put("name", o.getName());
        node.put("class", o.getJEVisClassName());
        ArrayNode parents = node.putArray("parents");
        for (JEVisObject p : o.getParents()) parents.add(p.getID());
        ArrayNode children = node.putArray("children");
        for (JEVisObject c : o.getChildren()) children.add(c.getID());
        ObjectNode attributes = node.putObject("attributes");
        List<JEVisAttribute> list = o.getAttributes();
        if (list != null) {
            for (JEVisAttribute att : list) {
                ObjectNode a = attributes.putObject(att.getName());
                a.put("inputUnit", label(att.getInputUnit()));
                a.put("displayUnit", label(att.getDisplayUnit()));
                boolean file = isFile(att);
                ArrayNode samples = a.putArray("samples");
                int i = 0;
                for (JEVisSample s : att.getAllSamples()) {
                    ObjectNode sample = samples.addObject();
                    sample.put("ts", s.getTimestamp() != null ? s.getTimestamp().toString() : null);
                    sample.put("note", s.getNote());
                    JEVisFile content = file ? s.getValueAsFile() : null;
                    if (content != null && content.getBytes() != null) {
                        String name = (i++) + "_" + safe(content.getFilename());
                        Path target = directory.resolve("files").resolve(String.valueOf(o.getID())).resolve(safe(att.getName())).resolve(name);
                        Files.createDirectories(target.getParent());
                        Files.write(target, content.getBytes());
                        files++;
                        sample.put("file", directory.relativize(target).toString().replace('\\', '/'));
                        sample.put("value", content.getFilename());
                    } else {
                        sample.put("value", s.getValueAsString());
                    }
                }
            }
        }
        Path file = directory.resolve("objects").resolve(o.getID() + ".json");
        Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(node));
        if (!Files.isRegularFile(file) || Files.size(file) == 0) throw new IOException("Backup of " + o.getID() + " not written");
    }

    private void writeManifest() throws IOException {
        Files.write(directory.resolve("manifest.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
    }

    static boolean isFile(JEVisAttribute att) {
        try {
            return att.getPrimitiveType() == JEVisConstants.PrimitiveType.FILE;
        } catch (Exception e) {
            return false;
        }
    }

    private static String label(JEVisUnit unit) {
        return unit != null ? unit.getLabel() : null;
    }

    static String safe(String name) {
        if (name == null || name.isEmpty()) return "file";
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
