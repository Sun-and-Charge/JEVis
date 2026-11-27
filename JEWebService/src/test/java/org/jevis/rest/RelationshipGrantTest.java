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
package org.jevis.rest;

import org.jevis.api.JEVisConstants.ObjectRelationship;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** E10.0: no rights can be granted that the granting user does not hold. */
public class RelationshipGrantTest {

    private static final long USER = 10, GROUP = 20, OTHER_GROUP = 21, ROLE = 30, OBJECT = 40;

    private static Map<Long, Set<Integer>> held(long group, Integer... types) {
        Map<Long, Set<Integer>> map = new HashMap<>();
        map.put(group, new HashSet<>(Arrays.asList(types)));
        return map;
    }

    @Test
    public void membershipNeedsTheSameMembership() {
        Map<Long, Set<Integer>> reader = held(GROUP, ObjectRelationship.MEMBER_READ, ObjectRelationship.MEMBER_WRITE);
        assertTrue(RelationshipGrant.mayGrant(false, ObjectRelationship.MEMBER_READ, USER, GROUP, reader, Collections.emptyMap()));
        assertTrue(RelationshipGrant.mayGrant(false, ObjectRelationship.MEMBER_WRITE, USER, GROUP, reader, Collections.emptyMap()));
        /* write on the group object alone was enough before */
        assertFalse(RelationshipGrant.mayGrant(false, ObjectRelationship.MEMBER_DELETE, USER, GROUP, reader, Collections.emptyMap()));
        assertFalse(RelationshipGrant.mayGrant(false, ObjectRelationship.MEMBER_READ, USER, OTHER_GROUP, reader, Collections.emptyMap()));
    }

    @Test
    public void siteManagersHoldAllRightsOfTheirGroups() {
        Map<Long, Set<Integer>> manager = held(GROUP, ObjectRelationship.MEMBER_READ, ObjectRelationship.MEMBER_WRITE,
                ObjectRelationship.MEMBER_EXECUTE, ObjectRelationship.MEMBER_CREATE, ObjectRelationship.MEMBER_DELETE);
        for (int type = ObjectRelationship.MEMBER_READ; type <= ObjectRelationship.MEMBER_DELETE; type++) {
            assertTrue(RelationshipGrant.mayGrant(false, type, USER, GROUP, manager, Collections.emptyMap()));
        }
    }

    @Test
    public void roleRightsAndRoleMembers() {
        Map<Long, Set<Integer>> reader = held(GROUP, ObjectRelationship.MEMBER_READ);
        assertEquals(ObjectRelationship.MEMBER_DELETE, RelationshipGrant.membershipFor(ObjectRelationship.ROLE_DELETE));
        assertTrue(RelationshipGrant.mayGrant(false, ObjectRelationship.ROLE_READ, ROLE, GROUP, reader, Collections.emptyMap()));
        assertFalse(RelationshipGrant.mayGrant(false, ObjectRelationship.ROLE_WRITE, ROLE, GROUP, reader, Collections.emptyMap()));

        Map<Long, Set<Integer>> roleReads = held(GROUP, ObjectRelationship.ROLE_READ);
        assertTrue(RelationshipGrant.mayGrant(false, ObjectRelationship.ROLE_MEMBER, ROLE, USER, reader, roleReads));
        Map<Long, Set<Integer>> roleWrites = held(GROUP, ObjectRelationship.ROLE_READ, ObjectRelationship.ROLE_WRITE);
        assertFalse(RelationshipGrant.mayGrant(false, ObjectRelationship.ROLE_MEMBER, ROLE, USER, reader, roleWrites));
    }

    @Test
    public void sysAdminsAndOtherRelationships() {
        Map<Long, Set<Integer>> none = Collections.emptyMap();
        assertTrue(RelationshipGrant.mayGrant(true, ObjectRelationship.MEMBER_DELETE, USER, GROUP, none, none));
        /* sharing an object with a group (owner) stays a matter of the write right */
        assertTrue(RelationshipGrant.mayGrant(false, ObjectRelationship.OWNER, OBJECT, GROUP, none, none));
    }
}
