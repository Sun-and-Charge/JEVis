/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Reads and writes the class definition files (one {@code <class name>.json} per class, in any
 * sub directory of the class directory) and the class icons next to them.
 *
 * <p>All changing operations of the web service run under {@link #LOCK}.
 */
public class ClassFileStore {

    /**
     * Lock for all changes of the class directory.
     */
    public static final Object LOCK = new Object();
    private static final Logger logger = LogManager.getLogger(ClassFileStore.class);
    private static final String[] ICON_EXTENSIONS = {".png", ".jpg", ".gif"};

    private final File classDir;

    public ClassFileStore(File classDir) {
        this.classDir = classDir;
    }

    private static void listFiles(File dir, List<File> result, String... extensions) {
        File[] entries = dir == null ? null : dir.listFiles();
        if (entries == null) {
            return;
        }
        Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                listFiles(entry, result, extensions);
            } else {
                String lower = entry.getName().toLowerCase(Locale.ROOT);
                for (String ext : extensions) {
                    if (lower.endsWith(ext)) {
                        result.add(entry);
                        break;
                    }
                }
            }
        }
    }

    private static String baseName(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    /**
     * @return the class definition files by class name (the name inside the file)
     */
    public Map<String, File> index() {
        List<File> files = new ArrayList<>();
        listFiles(classDir, files, ".json");
        Map<String, File> index = new LinkedHashMap<>();
        for (File file : files) {
            try {
                JsonJEVisClass jclass = read(file);
                if (jclass.getName() != null && !index.containsKey(jclass.getName())) {
                    index.put(jclass.getName(), file);
                }
            } catch (Exception ex) {
                logger.error("Could not read class file {}", file, ex);
            }
        }
        return index;
    }

    public JsonJEVisClass read(File file) throws IOException {
        return ClassDefinitions.parse(Files.readAllBytes(file.toPath()));
    }

    /**
     * Writes a class file (UTF-8 without BOM) via a temporary file in the same directory.
     */
    public void write(File file, JsonJEVisClass jclass) throws IOException {
        File dir = file.getAbsoluteFile().getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create directory " + dir);
        }
        File tmp = new File(dir, "." + file.getName() + ".tmp");
        Files.write(tmp.toPath(), (ClassDefinitions.toJson(jclass) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * @return the file for a new class: next to the file of its super class, otherwise in the
     * class directory
     */
    public File newClassFile(String className, File superClassFile) {
        File dir = superClassFile != null ? superClassFile.getAbsoluteFile().getParentFile() : classDir;
        return new File(dir, className + ".json");
    }

    /**
     * Removes the relationships of {@code className} which are not in {@code keep} from all other
     * class files.
     *
     * @return the names of the changed classes
     */
    public List<String> removeRelationshipsFromOtherFiles(Map<String, File> index, String className,
                                                         Set<JsonClassRelationship> keep) throws IOException {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, File> entry : index.entrySet()) {
            if (entry.getKey().equals(className)) {
                continue;
            }
            JsonJEVisClass other = read(entry.getValue());
            if (ClassDefinitions.removeRelationships(other, className, keep)) {
                write(entry.getValue(), other);
                changed.add(entry.getKey());
            }
        }
        return changed;
    }

    /**
     * @return all icon files of the class (file name without extension equals the class name,
     * ignoring case)
     */
    public List<File> iconFiles(String className) {
        List<File> files = new ArrayList<>();
        listFiles(classDir, files, ICON_EXTENSIONS);
        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (baseName(file).equalsIgnoreCase(className)) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * @return the file for a new icon of the class: next to its class file, otherwise in the
     * class directory
     */
    public File iconFile(String className, File classFile) {
        File dir = classFile != null ? classFile.getAbsoluteFile().getParentFile() : classDir;
        return new File(dir, className + ".png");
    }

    /**
     * Deletes all icon files of the class except {@code keep}.
     */
    public void deleteIcons(String className, File keep) {
        for (File icon : iconFiles(className)) {
            if (keep != null && icon.getAbsoluteFile().equals(keep.getAbsoluteFile())) {
                continue;
            }
            if (!icon.delete()) {
                logger.warn("Could not delete class icon {}", icon);
            }
        }
    }
}
