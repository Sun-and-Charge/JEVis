/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.local;

import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.JEVisUserSQL;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.UserRightManagerForWS;
import org.jevis.commons.ws.sql.tables.ObjectTable;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.jevis.commons.ws.sql.local.LocalDataSourceTest.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Compares {@link LocalAccess} with the real {@link UserRightManagerForWS} on the fixture of
 * {@link LocalDataSourceTest}: the readable objects must be exactly those of {@code filterList}, everything
 * {@code canRead} accepts must be readable, and the visible relationships must be those of
 * {@code filterRelationships}.
 */
public class LocalAccessTest {

    private List<JsonObject> objects;
    private List<JsonRelationship> relationships;
    private UserRightManagerForWS um;

    @Before
    public void setUp() throws Exception {
        objects = fixtureObjects();
        relationships = fixtureRelationships();
        SQLDataSource sql = mock(SQLDataSource.class);
        ObjectTable objectTable = mock(ObjectTable.class);
        List<JsonObject> publicObjects = new ArrayList<>();
        for (JsonObject obj : objects) {
            if (obj.getisPublic()) {
                publicObjects.add(obj);
            }
        }
        when(objectTable.getAllPublicObjects()).thenReturn(publicObjects);
        when(sql.getObjectTable()).thenReturn(objectTable);
        when(sql.getCurrentUser()).thenReturn(new JEVisUserSQL(sql, "user", USER, false, true));
        when(sql.getRelationships()).thenReturn(relationships);
        // with the relationship cache loaded, as in GET /objects
        when(sql.getGroupOwnerRelationships(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            List<JsonRelationship> list = new ArrayList<>();
            for (JsonRelationship rel : relationships) {
                if (rel.getFrom() == id || rel.getTo() == id) {
                    list.add(rel);
                }
            }
            return list;
        });

        um = new UserRightManagerForWS(sql, false);
        // memberships normally come from CachedAccessControl, which needs a database
        um.getReadGIDS().clear();
        um.getReadGIDS().add(GROUP_READ);
    }

    private Map<Long, JsonObject> objectMap() {
        Map<Long, JsonObject> map = new HashMap<>();
        for (JsonObject obj : objects) {
            map.put(obj.getId(), obj);
        }
        return map;
    }

    @Test
    public void readableObjectsEqualFilterList() {
        LocalAccess access = LocalAccess.of(um, USER, relationships);
        Set<Long> expected = new TreeSet<>();
        for (JsonObject obj : um.filterList(objects)) {
            expected.add(obj.getId());
        }
        Set<Long> actual = new TreeSet<>();
        for (JsonObject obj : objects) {
            if (access.canRead(obj)) {
                actual.add(obj.getId());
            }
        }
        assertEquals(expected, actual);
        assertFalse(actual.contains(4L));
        assertFalse(actual.contains(GROUP_OTHER));
    }

    @Test
    public void everythingCanReadAcceptsIsReadable() {
        LocalAccess access = LocalAccess.of(um, USER, relationships);
        for (JsonObject obj : objects) {
            boolean canRead;
            try {
                canRead = um.canRead(obj);
            } catch (Exception ex) {
                canRead = false;
            }
            if (canRead) {
                assertTrue("object " + obj.getId(), access.canRead(obj));
            }
        }
    }

    @Test
    public void visibleRelationshipsEqualFilterRelationships() {
        LocalAccess access = LocalAccess.of(um, USER, relationships);
        Map<Long, JsonObject> map = objectMap();
        Set<String> expected = new TreeSet<>();
        for (JsonRelationship rel : um.filterRelationships(relationships)) {
            expected.add(rel.toString());
        }
        Set<String> actual = new TreeSet<>();
        for (JsonRelationship rel : relationships) {
            if (access.canRead(rel, map)) {
                actual.add(rel.toString());
            }
        }
        assertEquals(expected, actual);
    }

    @Test
    public void sysAdminReadsAll() {
        LocalAccess access = new LocalAccess(true, USER, Collections.<Long>emptyList(), relationships);
        for (JsonObject obj : objects) {
            assertTrue(access.canRead(obj));
        }
        for (JsonRelationship rel : relationships) {
            assertTrue(access.canRead(rel, objectMap()));
        }
    }

    @Test
    public void nullIsNotReadable() {
        assertFalse(LocalAccess.of(um, USER, relationships).canRead((JsonObject) null));
    }
}
