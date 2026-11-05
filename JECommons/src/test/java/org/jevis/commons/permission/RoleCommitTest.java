/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.permission;

import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;
import static org.jevis.commons.permission.PermissionConsistencyCheckTest.add;
import static org.junit.Assert.*;

public class RoleCommitTest {

    private RightsModel model;

    @Before
    public void setUp() {
        model = new RightsModel();
        add(model, 1, "System", "System", null);
        add(model, 10, "Groups", "Group Directory", 1L);
        add(model, 30, "Data", "Group", 10L);
        add(model, 31, "Reports", "Group", 10L);
        add(model, 32, "Unrelated", "Group", 10L);
        add(model, 11, "Users", "User Directory", 1L);
        add(model, 40, "Alice", "User", 11L);
        add(model, 41, "Bob", "User", 11L);
        add(model, 42, "Carol", "User", 11L);
        add(model, 12, "Roles", "User Role Directory", 1L);
        add(model, 50, "Viewer", "User Role", 12L);
        add(model, 51, "Editor", "User Role", 12L);

        /* Viewer: read on Data; Editor: read+write on Data, read on Reports */
        model.addRelationship(50, 30, ROLE_READ);
        model.addRelationship(51, 30, ROLE_READ);
        model.addRelationship(51, 30, ROLE_WRITE);
        model.addRelationship(51, 31, ROLE_READ);
        /* Alice in both roles, Bob only in Viewer */
        model.addRelationship(50, 40, ROLE_MEMBER);
        model.addRelationship(51, 40, ROLE_MEMBER);
        model.addRelationship(50, 41, ROLE_MEMBER);
        model.addRelationship(40, 30, MEMBER_READ);
        model.addRelationship(40, 30, MEMBER_WRITE);
        model.addRelationship(40, 31, MEMBER_READ);
        model.addRelationship(41, 30, MEMBER_READ);
        /* hand-made memberships outside the role */
        model.addRelationship(40, 32, MEMBER_READ);
        model.addRelationship(41, 32, MEMBER_DELETE);
        model.addRelationship(42, 32, MEMBER_READ);
    }

    private Map<Long, Integer> rights(long... pairs) {
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], (int) pairs[i + 1]);
        return map;
    }

    private Set<Long> members(Long... ids) {
        return new HashSet<>(Arrays.asList(ids));
    }

    @Test
    public void unchangedRolePlansNothing() {
        RightsPlan plan = RoleCommit.plan(model, 50, rights(30, Rights.READ), members(40L, 41L));
        assertTrue(plan.getActions().toString(), plan.isEmpty());
    }

    @Test
    public void membershipsInOtherGroupsAreNeverTouched() {
        RightsPlan plan = RoleCommit.plan(model, 50, rights(30, Rights.READ | Rights.EXECUTE), members(40L, 41L));
        PlanSimulator.apply(model, plan);
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 40).get(32L));
        assertEquals(Integer.valueOf(Rights.DELETE), Rights.membershipsOf(model, 41).get(32L));
        assertEquals("Editor role rights of Alice stay", Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 40).get(31L));
        assertEquals(Integer.valueOf(Rights.READ | Rights.WRITE | Rights.EXECUTE), Rights.membershipsOf(model, 40).get(30L));
        assertEquals(Integer.valueOf(Rights.READ | Rights.EXECUTE), Rights.membershipsOf(model, 41).get(30L));
        assertTrue(model.has(50, 30, ROLE_EXECUTE));
    }

    @Test
    public void removingMemberKeepsRightsOfOtherRole() {
        PlanSimulator.apply(model, RoleCommit.plan(model, 50, rights(30, Rights.READ), members(41L)));
        assertFalse(model.has(50, 40, ROLE_MEMBER));
        assertEquals("Editor still grants read+write on Data", Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 40).get(30L));
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 40).get(31L));
    }

    @Test
    public void removingMemberWithoutOtherRoleRemovesRoleGroupRightsOnly() {
        PlanSimulator.apply(model, RoleCommit.plan(model, 50, rights(30, Rights.READ), members(40L)));
        assertFalse(model.has(50, 41, ROLE_MEMBER));
        assertNull(Rights.membershipsOf(model, 41).get(30L));
        assertEquals(Integer.valueOf(Rights.DELETE), Rights.membershipsOf(model, 41).get(32L));
    }

    @Test
    public void removingGroupFromRoleKeepsRightOfOtherRole() {
        /* Viewer loses Data entirely */
        PlanSimulator.apply(model, RoleCommit.plan(model, 50, rights(), members(40L, 41L)));
        assertFalse(model.has(50, 30, ROLE_READ));
        assertEquals(Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 40).get(30L));
        assertNull(Rights.membershipsOf(model, 41).get(30L));
    }

    @Test
    public void addingMemberAndGroup() {
        RightsPlan plan = RoleCommit.plan(model, 50, rights(30, Rights.READ, 31, Rights.READ | Rights.WRITE), members(40L, 41L, 42L));
        PlanSimulator.apply(model, plan);
        assertTrue(model.has(50, 42, ROLE_MEMBER));
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 42).get(30L));
        assertEquals(Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 42).get(31L));
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 42).get(32L));
        assertEquals(Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 40).get(31L));
        assertTrue(RoleCommit.plan(model, 50, rights(30, Rights.READ, 31, Rights.READ | Rights.WRITE), members(40L, 41L, 42L)).isEmpty());
    }

    @Test
    public void usersOutsideTheRoleAreNotTouched() {
        model.addRelationship(42, 30, MEMBER_WRITE);
        PlanSimulator.apply(model, RoleCommit.plan(model, 50, rights(30, Rights.READ | Rights.WRITE), members(40L, 41L)));
        assertEquals(Integer.valueOf(Rights.WRITE), Rights.membershipsOf(model, 42).get(30L));
    }
}
