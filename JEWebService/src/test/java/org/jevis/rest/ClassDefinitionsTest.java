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

import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.JEVisClassHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.*;

public class ClassDefinitionsTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static JsonClassRelationship rel(String start, String end, int type) {
        JsonClassRelationship rel = new JsonClassRelationship();
        rel.setStart(start);
        rel.setEnd(end);
        rel.setType(type);
        return rel;
    }

    private static JsonType type(String name, String jclass, int primitiveType, int position) {
        JsonType type = new JsonType();
        type.setName(name);
        type.setJevisclass(jclass);
        type.setPrimitiveType(primitiveType);
        type.setGuiType("Text");
        type.setGUIPosition(position);
        return type;
    }

    private static JsonJEVisClass jclass(String name, List<JsonClassRelationship> rels, JsonType... types) {
        JsonJEVisClass jclass = new JsonJEVisClass();
        jclass.setName(name);
        jclass.setDescription("Description of " + name);
        jclass.setRelationships(new ArrayList<>(rels));
        jclass.setTypes(new ArrayList<>(Arrays.asList(types)));
        return jclass;
    }

    /**
     * Base (types A, B) &lt;- Child (type C), Child valid under Folder; the relationships are stored in the
     * files as the web service has them on disk.
     */
    private Map<String, JsonJEVisClass> storedClasses() {
        Map<String, JsonJEVisClass> stored = new LinkedHashMap<>();
        stored.put("Base", jclass("Base", Collections.emptyList(),
                type("A", "Base", JEVisConstants.PrimitiveType.STRING, 1),
                type("B", "Base", JEVisConstants.PrimitiveType.DOUBLE, 2)));
        stored.put("Child", jclass("Child", Arrays.asList(
                        rel("Child", "Base", JEVisConstants.ClassRelationship.INHERIT),
                        rel("Child", "Folder", JEVisConstants.ClassRelationship.OK_PARENT)),
                type("C", "Child", JEVisConstants.PrimitiveType.LONG, 3)));
        stored.put("Folder", jclass("Folder", Collections.singletonList(
                rel("Child", "Folder", JEVisConstants.ClassRelationship.OK_PARENT))));
        return stored;
    }

    /**
     * @return the classes as the web service serves them (completed, deep copies of the stored ones)
     */
    private Map<String, JsonJEVisClass> completed(Map<String, JsonJEVisClass> stored) throws Exception {
        Map<String, JsonJEVisClass> map = new LinkedHashMap<>();
        for (JsonJEVisClass jclass : stored.values()) {
            JsonJEVisClass copy = ClassDefinitions.parse(ClassDefinitions.toJson(jclass).getBytes(StandardCharsets.UTF_8));
            map.put(copy.getName(), copy);
        }
        JEVisClassHelper.completeClasses(map);
        return map;
    }

    @Test
    public void jsonRoundTripKeepsAllFields() throws Exception {
        JsonType t = type("Value", "Data", JEVisConstants.PrimitiveType.DOUBLE, 4);
        t.setValidity(JEVisConstants.Validity.AT_DATE);
        t.setDescription("Ä value");
        JsonJEVisClass c = jclass("Data", Collections.singletonList(rel("Data", "Data Directory", 3)), t);
        c.setUnique(true);

        String json = ClassDefinitions.toJson(c);
        assertTrue(json.startsWith("{" + System.lineSeparator() + "  \"name\": \"Data\","));
        assertFalse(json.contains("null"));

        JsonJEVisClass back = ClassDefinitions.parse(("﻿" + json).getBytes(StandardCharsets.UTF_8));
        assertEquals("Data", back.getName());
        assertEquals("Description of Data", back.getDescription());
        assertTrue(back.getUnique());
        assertEquals(Collections.singletonList(rel("Data", "Data Directory", 3)), back.getRelationships());
        JsonType bt = back.getTypes().get(0);
        assertEquals("Value", bt.getName());
        assertEquals(JEVisConstants.PrimitiveType.DOUBLE, bt.getPrimitiveType());
        assertEquals("Text", bt.getGuiType());
        assertEquals(4, bt.getGUIPosition());
        assertEquals(JEVisConstants.Validity.AT_DATE, bt.getValidity());
        assertEquals("Ä value", bt.getDescription());
        assertEquals("Data", bt.getJevisClass());
    }

    @Test
    public void classNameValidation() {
        assertNull(ClassDefinitions.checkClassName("Clean Data"));
        assertNull(ClassDefinitions.checkClassName("Data (Ä-1)"));
        assertNotNull(ClassDefinitions.checkClassName(""));
        assertNotNull(ClassDefinitions.checkClassName(" Data"));
        assertNotNull(ClassDefinitions.checkClassName("../Data"));
        assertNotNull(ClassDefinitions.checkClassName("Data/Test"));
        assertNotNull(ClassDefinitions.checkClassName("Data?"));
        assertNotNull(ClassDefinitions.checkClassName(".hidden"));
    }

    @Test
    public void mergeDropsInheritedTypesAndKeepsOwnTypes() throws Exception {
        Map<String, JsonJEVisClass> stored = storedClasses();
        Map<String, JsonJEVisClass> known = completed(stored);

        JsonJEVisClass incoming = known.get("Child");
        assertEquals(3, incoming.getTypes().size());
        incoming.getTypes().add(type("D", null, JEVisConstants.PrimitiveType.BOOLEAN, 4));

        ClassDefinitions.MergeResult result = ClassDefinitions.merge(incoming, stored.get("Child"), known);
        List<String> names = new ArrayList<>();
        for (JsonType t : result.getPersisted().getTypes()) {
            names.add(t.getName());
            assertEquals("Child", t.getJevisClass());
        }
        assertEquals(Arrays.asList("C", "D"), names);
        assertEquals("Base", result.getSuperClass());
        assertTrue(result.getRemovedTypes().isEmpty());
        assertEquals(new HashSet<>(Arrays.asList(
                        rel("Child", "Base", 0), rel("Child", "Folder", 3))),
                new HashSet<>(result.getPersisted().getRelationships()));
    }

    @Test
    public void mergeReportsRemovedAndChangedTypes() throws Exception {
        Map<String, JsonJEVisClass> stored = storedClasses();
        Map<String, JsonJEVisClass> known = completed(stored);

        JsonJEVisClass incoming = known.get("Base");
        incoming.getTypes().removeIf(t -> t.getName().equals("A"));
        incoming.getTypes().get(0).setPrimitiveType(JEVisConstants.PrimitiveType.STRING);

        ClassDefinitions.MergeResult result = ClassDefinitions.merge(incoming, stored.get("Base"), known);
        assertEquals(Collections.singleton("A"), result.getRemovedTypes());
        assertEquals(Collections.singleton("B"), result.getChangedPrimitiveTypes());
        /* the INHERIT relationship of the heir is a relationship of Base too */
        assertTrue(result.getInvolvingRelationships().contains(rel("Child", "Base", 0)));
    }

    @Test
    public void removedRelationshipIsAlsoRemovedFromOtherFile() throws Exception {
        Map<String, JsonJEVisClass> stored = storedClasses();
        Map<String, JsonJEVisClass> known = completed(stored);

        JsonJEVisClass incoming = known.get("Child");
        incoming.getRelationships().removeIf(r -> r.getType() == JEVisConstants.ClassRelationship.OK_PARENT);
        ClassDefinitions.MergeResult result = ClassDefinitions.merge(incoming, stored.get("Child"), known);

        assertEquals(Collections.singletonList(rel("Child", "Base", 0)), result.getPersisted().getRelationships());
        JsonJEVisClass folder = stored.get("Folder");
        assertTrue(ClassDefinitions.removeRelationships(folder, "Child", result.getInvolvingRelationships()));
        assertTrue(folder.getRelationships().isEmpty());
        assertFalse(ClassDefinitions.removeRelationships(stored.get("Base"), "Child", result.getInvolvingRelationships()));
    }

    @Test
    public void invalidDefinitionsAreRefused() throws Exception {
        Map<String, JsonJEVisClass> stored = storedClasses();
        Map<String, JsonJEVisClass> known = completed(stored);

        /* duplicate type */
        JsonJEVisClass dup = known.get("Base");
        dup.getTypes().add(type("A", "Base", 0, 9));
        assertStatus(400, dup, stored.get("Base"), known);

        /* inheritance cycle: Base inherits from its heir */
        known = completed(stored);
        JsonJEVisClass cycle = known.get("Base");
        cycle.getRelationships().add(rel("Base", "Child", 0));
        assertStatus(400, cycle, stored.get("Base"), known);

        /* two super classes */
        known = completed(stored);
        JsonJEVisClass two = known.get("Child");
        two.getRelationships().add(rel("Child", "Folder", 0));
        assertStatus(400, two, stored.get("Child"), known);

        /* unknown class in a new relationship */
        known = completed(stored);
        JsonJEVisClass unknown = known.get("Child");
        unknown.getRelationships().add(rel("Child", "Nowhere", 3));
        assertStatus(400, unknown, stored.get("Child"), known);

        /* unknown primitive type */
        known = completed(stored);
        JsonJEVisClass prim = known.get("Folder");
        prim.getTypes().add(type("X", "Folder", 99, 1));
        assertStatus(400, prim, stored.get("Folder"), known);
    }

    @Test
    public void newClassGetsOnlyItsOwnContent() throws Exception {
        Map<String, JsonJEVisClass> stored = storedClasses();
        Map<String, JsonJEVisClass> known = completed(stored);

        JsonJEVisClass incoming = jclass("Grandchild", Collections.singletonList(rel("Grandchild", "Child", 0)),
                type("A", "Base", 0, 1), type("E", null, 0, 5));
        ClassDefinitions.MergeResult result = ClassDefinitions.merge(incoming, null, known);
        assertEquals(1, result.getPersisted().getTypes().size());
        assertEquals("E", result.getPersisted().getTypes().get(0).getName());
        assertEquals("Child", result.getSuperClass());
    }

    @Test
    public void fileStoreWritesReadsAndCleansUp() throws Exception {
        File root = folder.newFolder("classes");
        File sub = new File(root, "Sub");
        assertTrue(sub.mkdirs());
        ClassFileStore store = new ClassFileStore(root);
        Map<String, JsonJEVisClass> stored = storedClasses();
        store.write(new File(sub, "Base.json"), stored.get("Base"));
        store.write(new File(sub, "Child.json"), stored.get("Child"));
        store.write(new File(root, "Folder.json"), stored.get("Folder"));
        Files.write(new File(sub, "Child.png").toPath(), new byte[]{1});
        Files.write(new File(root, "child.gif").toPath(), new byte[]{1});

        Map<String, File> index = store.index();
        assertEquals(new HashSet<>(Arrays.asList("Base", "Child", "Folder")), index.keySet());
        assertEquals(new File(sub, "Grandchild.json"), store.newClassFile("Grandchild", index.get("Child")));
        assertEquals(2, store.iconFiles("Child").size());

        List<String> changed = store.removeRelationshipsFromOtherFiles(index, "Child", Collections.emptySet());
        assertEquals(Collections.singletonList("Folder"), changed);
        assertTrue(store.read(index.get("Folder")).getRelationships().isEmpty());

        File icon = store.iconFile("Child", index.get("Child"));
        store.deleteIcons("Child", icon);
        assertEquals(Collections.singletonList(icon), store.iconFiles("Child"));
        assertFalse(new File(sub, ".Child.json.tmp").exists());
    }

    private static void assertStatus(int status, JsonJEVisClass incoming, JsonJEVisClass stored, Map<String, JsonJEVisClass> known) {
        try {
            ClassDefinitions.merge(incoming, stored, known);
            fail("Expected error " + status);
        } catch (ClassDefinitions.ClassDefinitionException ex) {
            assertEquals(status, ex.getStatus());
        }
    }
}
