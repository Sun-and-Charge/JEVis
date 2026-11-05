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

import org.jevis.api.*;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;
import static org.jevis.commons.permission.PermissionConsistencyCheck.FindingType.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PermissionConsistencyCheckTest {

    private RightsModel model;

    static void add(RightsModel model, long id, String name, String className, Long parent) {
        model.addObject(id, name, className);
        if (parent != null) model.addRelationship(id, parent, PARENT);
    }

    @Before
    public void setUp() {
        model = new RightsModel();
        add(model, 1, "System", "System", null);
        add(model, 2, "Classes", "Directory", 1L);
        add(model, 10, "Example Org", "Organization", 1L);
        add(model, 11, "Administration", "Administration Directory", 10L);
        add(model, 12, "Users", "User Directory", 11L);
        add(model, 13, "Groups", "Group Directory", 11L);
        add(model, 14, "Roles", "User Role Directory", 11L);
        add(model, 20, "Site A", "Building", 10L);
        add(model, 21, "Data", "Data Directory", 20L);
        add(model, 22, "Meter 1", "Data", 21L);
        add(model, 23, "Meter 2", "Data", 21L);

        add(model, 30, "Readers", "Group", 13L);
        add(model, 31, "Editors", "Group", 13L);
        add(model, 32, "Empty", "Group", 13L);
        add(model, 33, "Unused", "Group", 13L);

        add(model, 40, "Alice", "User", 12L);
        add(model, 41, "Bob", "User", 12L);
        add(model, 42, "Carol", "User", 12L);
        add(model, 43, "Admin", "User", 12L);
        model.get(43).setSysAdmin(true);

        add(model, 50, "Viewer Role", "User Role", 14L);

        model.addRelationship(10, 30, OWNER);
        model.addRelationship(20, 30, OWNER);
        model.addRelationship(21, 31, OWNER);
        model.addRelationship(22, 31, OWNER);
        model.addRelationship(32, 32, OWNER);
        // Meter 2 (23) has no owner
        model.addRelationship(40, 30, MEMBER_READ);
        model.addRelationship(41, 31, MEMBER_READ);
        model.addRelationship(41, 31, MEMBER_WRITE);
        model.addRelationship(42, 33, MEMBER_READ);
    }

    private List<PermissionConsistencyCheck.Finding> of(List<PermissionConsistencyCheck.Finding> findings, PermissionConsistencyCheck.FindingType type) {
        return findings.stream().filter(f -> f.getType() == type).collect(Collectors.toList());
    }

    @Test
    public void reportsObjectWithoutOwnerOnlyBelowOrganisations() {
        List<PermissionConsistencyCheck.Finding> findings = new PermissionConsistencyCheck(model).run(null);
        List<Long> ids = of(findings, OBJECT_WITHOUT_OWNER).stream().map(PermissionConsistencyCheck.Finding::getObjectId).collect(Collectors.toList());
        assertEquals(Collections.singletonList(23L), ids);
        assertEquals("System / Example Org / Site A / Data / Meter 2", of(findings, OBJECT_WITHOUT_OWNER).get(0).getPath());
    }

    @Test
    public void reportsUsersWithoutMembershipButNotSysAdmins() {
        List<PermissionConsistencyCheck.Finding> findings = new PermissionConsistencyCheck(model).run(null);
        assertTrue(of(findings, USER_WITHOUT_MEMBERSHIP).isEmpty());

        add(model, 44, "Dave", "User", 12L);
        findings = new PermissionConsistencyCheck(model).run(null);
        List<Long> ids = of(findings, USER_WITHOUT_MEMBERSHIP).stream().map(PermissionConsistencyCheck.Finding::getObjectId).collect(Collectors.toList());
        assertEquals(Collections.singletonList(44L), ids);
    }

    @Test
    public void reportsEmptyGroupsAndGroupsOwningNothing() {
        List<PermissionConsistencyCheck.Finding> findings = new PermissionConsistencyCheck(model).run(null);
        Set<Long> empty = of(findings, GROUP_WITHOUT_MEMBERS).stream().map(PermissionConsistencyCheck.Finding::getObjectId).collect(Collectors.toSet());
        Set<Long> ownsNothing = of(findings, GROUP_OWNS_NOTHING).stream().map(PermissionConsistencyCheck.Finding::getObjectId).collect(Collectors.toSet());
        assertEquals(new HashSet<>(Collections.singletonList(32L)), empty);
        assertEquals(new HashSet<>(Collections.singletonList(33L)), ownsNothing);
    }

    @Test
    public void deletedObjectsAreIgnored() {
        model.get(23).setDeleted(true);
        model.get(33).setDeleted(true);
        List<PermissionConsistencyCheck.Finding> findings = new PermissionConsistencyCheck(model).run(null);
        assertTrue(of(findings, OBJECT_WITHOUT_OWNER).isEmpty());
        assertTrue(of(findings, GROUP_OWNS_NOTHING).isEmpty());
    }

    @Test
    public void scopeLimitsTheCheck() {
        List<PermissionConsistencyCheck.Finding> findings = new PermissionConsistencyCheck(model).run(21L);
        assertEquals(1, of(findings, OBJECT_WITHOUT_OWNER).size());
        assertTrue(of(findings, GROUP_WITHOUT_MEMBERS).isEmpty());
        assertTrue(of(findings, GROUP_OWNS_NOTHING).isEmpty());
    }

    @Test
    public void reportsRoleDriftAndBuildsFixPlan() {
        // role grants read+write on Editors and read on Readers
        model.addRelationship(50, 31, ROLE_READ);
        model.addRelationship(50, 31, ROLE_WRITE);
        model.addRelationship(50, 30, ROLE_READ);
        model.addRelationship(50, 41, ROLE_MEMBER);
        model.addRelationship(41, 31, MEMBER_DELETE);   // extra
        model.addRelationship(41, 33, MEMBER_READ);     // group outside the role: ignored
        // missing: read on Readers

        List<PermissionConsistencyCheck.Finding> drift = of(new PermissionConsistencyCheck(model).run(null), ROLE_MEMBER_MISMATCH);
        assertEquals(1, drift.size());
        PermissionConsistencyCheck.RoleDrift roleDrift = drift.get(0).getRoleDrift();
        assertEquals(41L, roleDrift.getUserId());
        assertEquals(2, roleDrift.getGroups().size());

        RightsPlan fix = roleDrift.toFixPlan(model);
        List<String> actions = fix.getActions().stream().map(RightsPlan.Action::toString).collect(Collectors.toList());
        assertEquals(2, actions.size());
        assertTrue(actions.contains("add Bob [41] -" + MEMBER_READ + "-> Readers [30]"));
        assertTrue(actions.contains("remove Bob [41] -" + MEMBER_DELETE + "-> Editors [31]"));

        PlanSimulator.apply(model, fix);
        assertTrue(of(new PermissionConsistencyCheck(model).run(null), ROLE_MEMBER_MISMATCH).isEmpty());
        assertTrue("membership outside the role stays", model.has(41, 33, MEMBER_READ));
    }

    @Test
    public void unionOfTwoRolesIsExpected() {
        add(model, 51, "Editor Role", "User Role", 14L);
        model.addRelationship(50, 31, ROLE_READ);
        model.addRelationship(51, 31, ROLE_WRITE);
        model.addRelationship(50, 41, ROLE_MEMBER);
        model.addRelationship(51, 41, ROLE_MEMBER);
        assertTrue(of(new PermissionConsistencyCheck(model).run(null), ROLE_MEMBER_MISMATCH).isEmpty());
    }

    @Test
    public void modelFromDataSource() throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisClass userClass = mock(JEVisClass.class);
        when(userClass.getName()).thenReturn("User");
        when(ds.getJEVisClasses()).thenReturn(Collections.singletonList(userClass));

        JEVisObject dir = mock(JEVisObject.class);
        when(dir.getID()).thenReturn(5L);
        when(dir.getName()).thenReturn("Users");
        when(dir.getJEVisClassName()).thenReturn("User Directory");
        when(dir.getLocalNameList()).thenReturn(Collections.singletonMap("de", "Benutzer"));

        JEVisObject user = mock(JEVisObject.class);
        when(user.getID()).thenReturn(6L);
        when(user.getName()).thenReturn("Eve");
        when(user.getJEVisClassName()).thenReturn("User");
        JEVisAttribute sysAdmin = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsBoolean()).thenReturn(true);
        when(sysAdmin.getLatestSample()).thenReturn(sample);
        when(user.getAttribute("Sys Admin")).thenReturn(sysAdmin);
        when(ds.getObjects()).thenReturn(Arrays.asList(dir, user));

        JEVisRelationship parent = mock(JEVisRelationship.class);
        when(parent.getStartID()).thenReturn(6L);
        when(parent.getEndID()).thenReturn(5L);
        when(parent.getType()).thenReturn(PARENT);
        when(ds.getRelationships()).thenReturn(Collections.singletonList(parent));

        RightsModel fromDs = RightsModel.fromDataSource(ds);
        assertTrue(fromDs.get(6).isSysAdmin());
        assertEquals(Long.valueOf(5), fromDs.get(6).getParentId());
        assertTrue(fromDs.get(5).matchesName(Collections.singletonList("benutzer")));
        assertTrue(fromDs.hasClass("User"));
        assertFalse(fromDs.hasClass("Group"));
        assertEquals("Users / Eve", fromDs.getPath(6));
    }
}
