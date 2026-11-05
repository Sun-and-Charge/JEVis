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

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Pure logic for the file based JEVis class definitions (one JSON file per class in the class
 * directory of the web service).
 *
 * <p>The web service keeps the classes "completed" in memory: relationships from all files are
 * copied into both classes of a relationship and inherited types are copied into the heirs. A
 * class that a client sends back therefore contains more than its own file. {@link #merge}
 * reduces such a class to what belongs into its own file, and {@link #removeRelationships}
 * cleans up relationships that are also stored in the files of other classes.
 */
public final class ClassDefinitions {

    /**
     * Characters that are not allowed in class names, because the name is used as file name
     * and as part of the REST path.
     */
    private static final String FORBIDDEN_NAME_CHARS = "/\\:*?\"<>|#%";
    private static final int MAX_NAME_LENGTH = 128;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ClassDefinitions() {
    }

    /**
     * Error in a class definition, carries the HTTP status that should be answered.
     */
    public static class ClassDefinitionException extends Exception {
        private final int status;

        public ClassDefinitionException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    /**
     * Result of {@link #merge}.
     */
    public static final class MergeResult {
        private final JsonJEVisClass persisted;
        private final Set<JsonClassRelationship> involvingRelationships;
        private final Set<String> removedTypes;
        private final Set<String> changedPrimitiveTypes;
        private final String superClass;

        MergeResult(JsonJEVisClass persisted, Set<JsonClassRelationship> involvingRelationships,
                    Set<String> removedTypes, Set<String> changedPrimitiveTypes, String superClass) {
            this.persisted = persisted;
            this.involvingRelationships = involvingRelationships;
            this.removedTypes = removedTypes;
            this.changedPrimitiveTypes = changedPrimitiveTypes;
            this.superClass = superClass;
        }

        /** @return the class as it has to be written into its own file */
        public JsonJEVisClass getPersisted() {
            return persisted;
        }

        /** @return all relationships of the class (as start or end) after the change */
        public Set<JsonClassRelationship> getInvolvingRelationships() {
            return involvingRelationships;
        }

        /** @return own types of the stored file which are no longer part of the class */
        public Set<String> getRemovedTypes() {
            return removedTypes;
        }

        /** @return own types whose primitive type was changed */
        public Set<String> getChangedPrimitiveTypes() {
            return changedPrimitiveTypes;
        }

        /** @return the name of the super class or {@code null} */
        public String getSuperClass() {
            return superClass;
        }
    }

    /**
     * Checks a class name.
     *
     * @return an error message or {@code null} if the name is valid
     */
    public static String checkClassName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "The class name must not be empty.";
        }
        if (!name.equals(name.trim())) {
            return "The class name must not start or end with a blank.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "The class name must not be longer than " + MAX_NAME_LENGTH + " characters.";
        }
        if (name.startsWith(".")) {
            return "The class name must not start with a dot.";
        }
        for (char c : name.toCharArray()) {
            if (c < 32 || FORBIDDEN_NAME_CHARS.indexOf(c) >= 0) {
                return "The class name must not contain any of these characters: " + FORBIDDEN_NAME_CHARS;
            }
        }
        return null;
    }

    /**
     * @return {@code true} if the relationship starts or ends at the class
     */
    public static boolean involves(JsonClassRelationship rel, String className) {
        return rel != null && (className.equals(rel.getStart()) || className.equals(rel.getEnd()));
    }

    /**
     * @return the super class of the class (end of its outgoing INHERIT relationship) or {@code null}
     */
    public static String findSuperClass(JsonJEVisClass jclass) {
        if (jclass == null || jclass.getRelationships() == null) {
            return null;
        }
        for (JsonClassRelationship rel : jclass.getRelationships()) {
            if (rel != null && rel.getType() == JEVisConstants.ClassRelationship.INHERIT
                    && jclass.getName().equals(rel.getStart()) && !jclass.getName().equals(rel.getEnd())) {
                return rel.getEnd();
            }
        }
        return null;
    }

    /**
     * @return the names of all classes that inherit directly from the class
     */
    public static Set<String> findDirectHeirs(String className, Collection<JsonJEVisClass> classes) {
        Set<String> heirs = new TreeSet<>();
        for (JsonJEVisClass jclass : classes) {
            if (jclass == null || jclass.getRelationships() == null || className.equals(jclass.getName())) {
                continue;
            }
            for (JsonClassRelationship rel : jclass.getRelationships()) {
                if (rel != null && rel.getType() == JEVisConstants.ClassRelationship.INHERIT
                        && className.equals(rel.getEnd()) && jclass.getName().equals(rel.getStart())) {
                    heirs.add(jclass.getName());
                }
            }
        }
        return heirs;
    }

    private static Map<String, JsonType> typeMap(JsonJEVisClass jclass) {
        Map<String, JsonType> map = new LinkedHashMap<>();
        if (jclass != null && jclass.getTypes() != null) {
            for (JsonType type : jclass.getTypes()) {
                if (type != null && type.getName() != null) {
                    map.put(type.getName(), type);
                }
            }
        }
        return map;
    }

    private static boolean isValidRelationshipType(int type) {
        return type == JEVisConstants.ClassRelationship.INHERIT
                || type == JEVisConstants.ClassRelationship.NESTED
                || type == JEVisConstants.ClassRelationship.OK_PARENT;
    }

    /**
     * Reduces a class sent by a client to the content of its own class file and validates it.
     *
     * @param incoming the class as sent by the client (may contain inherited types and
     *                 relationships which are stored in other files)
     * @param stored   the content of the existing class file or {@code null} for a new class
     * @param known    all known classes (completed, as served by the web service), by name
     * @return the merge result
     * @throws ClassDefinitionException if the class is invalid (status 400)
     */
    public static MergeResult merge(JsonJEVisClass incoming, JsonJEVisClass stored,
                                    Map<String, JsonJEVisClass> known) throws ClassDefinitionException {
        if (incoming == null) {
            throw new ClassDefinitionException(400, "No class definition was sent.");
        }
        String name = incoming.getName();
        String nameError = checkClassName(name);
        if (nameError != null) {
            throw new ClassDefinitionException(400, nameError);
        }

        /* relationships */
        Set<JsonClassRelationship> storedRels = new LinkedHashSet<>();
        if (stored != null && stored.getRelationships() != null) {
            for (JsonClassRelationship rel : stored.getRelationships()) {
                if (rel != null && rel.getStart() != null && rel.getEnd() != null) {
                    storedRels.add(rel);
                }
            }
        }

        Set<JsonClassRelationship> involving = new LinkedHashSet<>();
        String superClass = null;
        if (incoming.getRelationships() != null) {
            for (JsonClassRelationship rel : incoming.getRelationships()) {
                if (rel == null || !involves(rel, name)) {
                    continue;
                }
                if (rel.getStart() == null || rel.getEnd() == null) {
                    throw new ClassDefinitionException(400, "A class relationship has no start or end class.");
                }
                JsonClassRelationship copy = copy(rel);
                if (!involving.add(copy)) {
                    continue;
                }
                boolean isNew = !storedRels.contains(copy);
                String other = name.equals(copy.getStart()) ? copy.getEnd() : copy.getStart();
                if (isNew) {
                    if (!isValidRelationshipType(copy.getType())) {
                        throw new ClassDefinitionException(400, "Unknown class relationship type: " + copy.getType());
                    }
                    if (!other.equals(name) && (known == null || !known.containsKey(other))) {
                        throw new ClassDefinitionException(400, "The class '" + other + "' does not exist.");
                    }
                }
                if (copy.getType() == JEVisConstants.ClassRelationship.INHERIT) {
                    if (copy.getStart().equals(copy.getEnd())) {
                        throw new ClassDefinitionException(400, "A class cannot inherit from itself.");
                    }
                    if (name.equals(copy.getStart())) {
                        if (superClass != null) {
                            throw new ClassDefinitionException(400, "A class can only inherit from one class.");
                        }
                        superClass = copy.getEnd();
                    }
                }
            }
        }

        if (superClass != null && known != null) {
            Set<String> visited = new HashSet<>();
            String current = superClass;
            while (current != null && visited.add(current)) {
                if (current.equals(name)) {
                    throw new ClassDefinitionException(400, "The inheritance of '" + name + "' would create a cycle.");
                }
                current = findSuperClass(known.get(current));
            }
        }

        List<JsonClassRelationship> persistedRels = new ArrayList<>(involving);
        for (JsonClassRelationship rel : storedRels) {
            if (!involves(rel, name)) {
                persistedRels.add(rel);
            }
        }

        /* types */
        Map<String, JsonType> storedTypes = typeMap(stored);
        Set<String> inheritedNames = new HashSet<>();
        if (superClass != null && known != null && known.get(superClass) != null) {
            inheritedNames.addAll(typeMap(known.get(superClass)).keySet());
        }

        List<JsonType> persistedTypes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (incoming.getTypes() != null) {
            for (JsonType type : incoming.getTypes()) {
                if (type == null) {
                    continue;
                }
                String typeName = type.getName();
                if (typeName == null || typeName.trim().isEmpty()) {
                    throw new ClassDefinitionException(400, "A type name must not be empty.");
                }
                if (!seen.add(typeName)) {
                    throw new ClassDefinitionException(400, "The type '" + typeName + "' exists more than once.");
                }
                boolean storedOwn = storedTypes.containsKey(typeName);
                if (inheritedNames.contains(typeName) && !storedOwn) {
                    /* inherited from the super class, not part of this file */
                    continue;
                }
                if (!storedOwn) {
                    if (!typeName.equals(typeName.trim())) {
                        throw new ClassDefinitionException(400, "The type name '" + typeName + "' must not start or end with a blank.");
                    }
                    if (typeName.length() > MAX_NAME_LENGTH) {
                        throw new ClassDefinitionException(400, "The type name '" + typeName + "' is too long.");
                    }
                }
                if (type.getPrimitiveType() < JEVisConstants.PrimitiveType.STRING
                        || type.getPrimitiveType() > JEVisConstants.PrimitiveType.PASSWORD_PBKDF2) {
                    throw new ClassDefinitionException(400, "Unknown primitive type " + type.getPrimitiveType() + " of type '" + typeName + "'.");
                }
                if (type.getValidity() != JEVisConstants.Validity.LAST && type.getValidity() != JEVisConstants.Validity.AT_DATE) {
                    throw new ClassDefinitionException(400, "Unknown validity " + type.getValidity() + " of type '" + typeName + "'.");
                }

                JsonType copy = new JsonType();
                copy.setName(typeName);
                copy.setPrimitiveType(type.getPrimitiveType());
                copy.setGuiType(type.getGuiType());
                copy.setGUIPosition(type.getGUIPosition());
                copy.setValidity(type.getValidity());
                copy.setInherited(storedOwn && type.getInherited());
                copy.setJevisclass(name);
                copy.setDescription(emptyToNull(type.getDescription()));
                persistedTypes.add(copy);
            }
        }

        Set<String> persistedNames = new HashSet<>();
        for (JsonType type : persistedTypes) {
            persistedNames.add(type.getName());
        }
        Set<String> removedTypes = new TreeSet<>();
        Set<String> changedPrimitive = new TreeSet<>();
        for (JsonType storedType : storedTypes.values()) {
            if (!persistedNames.contains(storedType.getName())) {
                removedTypes.add(storedType.getName());
            }
        }
        for (JsonType type : persistedTypes) {
            JsonType storedType = storedTypes.get(type.getName());
            if (storedType != null && storedType.getPrimitiveType() != type.getPrimitiveType()) {
                changedPrimitive.add(type.getName());
            }
        }

        JsonJEVisClass persisted = new JsonJEVisClass();
        persisted.setName(name);
        persisted.setDescription(emptyToNull(incoming.getDescription()));
        persisted.setUnique(incoming.getUnique());
        persisted.setRelationships(persistedRels);
        persisted.setTypes(persistedTypes);

        return new MergeResult(persisted, involving, removedTypes, changedPrimitive, superClass);
    }

    /**
     * Removes all relationships of {@code className} from another class file which are not in
     * {@code keep}.
     *
     * @param other     content of another class file, changed in place
     * @param className the class whose relationships are cleaned up
     * @param keep      relationships which still exist
     * @return {@code true} if {@code other} was changed and has to be written
     */
    public static boolean removeRelationships(JsonJEVisClass other, String className, Set<JsonClassRelationship> keep) {
        if (other == null || other.getRelationships() == null || className.equals(other.getName())) {
            return false;
        }
        boolean changed = false;
        Iterator<JsonClassRelationship> it = other.getRelationships().iterator();
        while (it.hasNext()) {
            JsonClassRelationship rel = it.next();
            if (rel != null && rel.getStart() != null && rel.getEnd() != null
                    && involves(rel, className) && !keep.contains(rel)) {
                it.remove();
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Parses a class file (an optional UTF-8 BOM is ignored).
     */
    public static JsonJEVisClass parse(byte[] content) throws IOException {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        return MAPPER.readValue(text, JsonJEVisClass.class);
    }

    /**
     * Writes a class in the format of the class files in {@code var/classes}: fixed field
     * order, two blanks indentation, no {@code null} values.
     */
    public static String toJson(JsonJEVisClass jclass) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("name", jclass.getName());
        if (jclass.getDescription() != null) {
            root.put("description", jclass.getDescription());
        }
        root.put("unique", jclass.getUnique());
        ArrayNode rels = root.putArray("relationships");
        if (jclass.getRelationships() != null) {
            for (JsonClassRelationship rel : jclass.getRelationships()) {
                ObjectNode node = rels.addObject();
                node.put("start", rel.getStart());
                node.put("end", rel.getEnd());
                node.put("type", rel.getType());
            }
        }
        ArrayNode types = root.putArray("types");
        if (jclass.getTypes() != null) {
            for (JsonType type : jclass.getTypes()) {
                ObjectNode node = types.addObject();
                node.put("name", type.getName());
                node.put("primitiveType", type.getPrimitiveType());
                if (type.getGuiType() != null) {
                    node.put("guiType", type.getGuiType());
                }
                node.put("guiposition", type.getGUIPosition());
                node.put("validity", type.getValidity());
                node.put("inherited", type.getInherited());
                if (type.getJevisClass() != null) {
                    node.put("jevisClass", type.getJevisClass());
                }
                if (type.getDescription() != null) {
                    node.put("description", type.getDescription());
                }
            }
        }

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER));
        printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        return MAPPER.writer(printer).writeValueAsString(root);
    }

    private static JsonClassRelationship copy(JsonClassRelationship rel) {
        JsonClassRelationship copy = new JsonClassRelationship();
        copy.setStart(rel.getStart());
        copy.setEnd(rel.getEnd());
        copy.setType(rel.getType());
        return copy;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
