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
package org.jevis.commons.ws.sql;

import org.jevis.api.JEVisConstants.ObjectRelationship;
import org.jevis.commons.ws.json.*;
import org.junit.Test;

import java.util.*;
import java.util.function.Function;

import static org.junit.Assert.*;

public class PermissionExplainerTest {

    private static final long OBJECT = 500;
    private static final long USER = 10;
    private static final long OTHER_USER = 11;
    private static final long GROUP_A = 100;
    private static final long GROUP_B = 101;
    private static final long ROLE_X = 300;
    private static final long ROLE_Y = 301;

    private static final Map<Long, String> NAMES = new HashMap<>();

    static {
        NAMES.put(GROUP_A, "Group A");
        NAMES.put(GROUP_B, "Group B");
        NAMES.put(ROLE_X, "Role X");
        NAMES.put(ROLE_Y, "Role Y");
    }

    private static final Function<Long, String> NAME = NAMES::get;

    private static JsonRelationship rel(long from, long to, int type) {
        JsonRelationship rel = new JsonRelationship();
        rel.setFrom(from);
        rel.setTo(to);
        rel.setType(type);
        return rel;
    }

    private static PermissionExplainer.ObjectOwners ownedBy(boolean isPublic, long... groups) {
        List<JsonRelationship> rels = new ArrayList<>();
        for (long group : groups) rels.add(rel(OBJECT, group, ObjectRelationship.OWNER));
        return PermissionExplainer.ObjectOwners.of(OBJECT, isPublic, rels, rels);
    }

    private static JsonEffectiveRights explain(PermissionExplainer.ObjectOwners owners, List<JsonRelationship> memberships,
                                               List<JsonRelationship> roleRels) {
        return PermissionExplainer.explain(USER, "user", false, true, owners, memberships,
                PermissionExplainer.RoleIndex.of(roleRels), NAME);
    }

    @Test
    public void sysAdminHasAllRightsWithoutSources() {
        JsonEffectiveRights r = PermissionExplainer.explain(USER, "admin", true, true, ownedBy(false), Collections.emptyList(), null, NAME);
        assertTrue(r.isRead() && r.isWrite() && r.isCreate() && r.isDelete() && r.isExecute());
        assertTrue(r.isSysAdmin());
        assertTrue(r.getReadSources().isEmpty());
        assertTrue(r.getCreateViaExecuteClasses().isEmpty());
    }

    @Test
    public void noMembershipNoRights() {
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A), Collections.emptyList(), Collections.emptyList());
        assertFalse(r.hasAnyRight());
    }

    @Test
    public void publicObjectIsReadableOnly() {
        JsonEffectiveRights r = explain(ownedBy(true), Collections.emptyList(), Collections.emptyList());
        assertTrue(r.isRead());
        assertTrue(r.isPublicObject());
        assertTrue(r.getReadSources().isEmpty());
        assertFalse(r.isWrite() || r.isCreate() || r.isDelete() || r.isExecute());
    }

    @Test
    public void ownUserObjectIsReadableOnly() {
        PermissionExplainer.ObjectOwners owners = PermissionExplainer.ObjectOwners.of(USER, false, Collections.emptyList(), Collections.emptyList());
        JsonEffectiveRights r = PermissionExplainer.explain(USER, "user", false, true, owners, Collections.emptyList(), null, NAME);
        assertTrue(r.isRead());
        assertTrue(r.isOwnObject());
        assertFalse(r.isWrite());
    }

    @Test
    public void rightsComeFromMembershipsInOwnerGroups() {
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_READ),
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_WRITE),
                rel(USER, GROUP_B, ObjectRelationship.MEMBER_DELETE),
                rel(USER, GROUP_B, ObjectRelationship.MEMBER_READ));
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A), memberships, Collections.emptyList());
        assertTrue(r.isRead());
        assertTrue(r.isWrite());
        assertFalse("group B does not own the object", r.isDelete());
        assertFalse(r.isCreate());
        assertFalse(r.isExecute());
        assertEquals(1, r.getReadSources().size());
        assertEquals(GROUP_A, r.getReadSources().get(0).getGroupId());
        assertEquals("Group A", r.getReadSources().get(0).getGroupName());
        assertTrue(r.getReadSources().get(0).getRoles().isEmpty());
    }

    @Test
    public void everyOwnerGroupIsASource() {
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_READ),
                rel(USER, GROUP_B, ObjectRelationship.MEMBER_READ));
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A, GROUP_B), memberships, Collections.emptyList());
        assertEquals(2, r.getReadSources().size());
    }

    @Test
    public void duplicateAndForeignRelationshipsAreIgnored() {
        List<JsonRelationship> owner = Arrays.asList(
                rel(OBJECT, GROUP_A, ObjectRelationship.OWNER),
                rel(OBJECT, GROUP_A, ObjectRelationship.OWNER),
                rel(OBJECT, GROUP_B, ObjectRelationship.PARENT));
        PermissionExplainer.ObjectOwners owners = PermissionExplainer.ObjectOwners.of(OBJECT, false, owner, owner);
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_EXECUTE),
                rel(USER, GROUP_B, ObjectRelationship.MEMBER_EXECUTE));
        JsonEffectiveRights r = explain(owners, memberships, Collections.emptyList());
        assertEquals(1, r.getExecuteSources().size());
        assertEquals(GROUP_A, r.getExecuteSources().get(0).getGroupId());
    }

    @Test
    public void readAndOtherRightsUseTheirOwnOwnerLists() {
        /* canRead and canWrite query the owners differently, e.g. a group object that owns other objects */
        List<JsonRelationship> readOwners = Collections.emptyList();
        List<JsonRelationship> ownerRels = Collections.singletonList(rel(999, GROUP_A, ObjectRelationship.OWNER));
        PermissionExplainer.ObjectOwners owners = PermissionExplainer.ObjectOwners.of(GROUP_A, false, readOwners, ownerRels);
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_READ),
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_WRITE));
        JsonEffectiveRights r = explain(owners, memberships, Collections.emptyList());
        assertFalse(r.isRead());
        assertTrue(r.isWrite());
    }

    @Test
    public void membershipIsCoveredByMatchingRoleOfTheUser() {
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_READ),
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_WRITE));
        List<JsonRelationship> roles = Arrays.asList(
                rel(ROLE_X, USER, ObjectRelationship.ROLE_MEMBER),
                rel(ROLE_X, GROUP_A, ObjectRelationship.ROLE_READ),
                rel(ROLE_Y, OTHER_USER, ObjectRelationship.ROLE_MEMBER),
                rel(ROLE_Y, GROUP_A, ObjectRelationship.ROLE_WRITE));
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A), memberships, roles);
        assertEquals(1, r.getReadSources().get(0).getRoles().size());
        assertEquals(ROLE_X, r.getReadSources().get(0).getRoles().get(0).getId());
        assertEquals("Role X", r.getReadSources().get(0).getRoles().get(0).getName());
        assertTrue("role X grants no write, role Y belongs to another user", r.getWriteSources().get(0).getRoles().isEmpty());
    }

    @Test
    public void roleWithoutMembershipGrantsNothing() {
        /* roles are not evaluated by the server, only the copied memberships count */
        List<JsonRelationship> roles = Arrays.asList(
                rel(ROLE_X, USER, ObjectRelationship.ROLE_MEMBER),
                rel(ROLE_X, GROUP_A, ObjectRelationship.ROLE_READ),
                rel(ROLE_X, GROUP_A, ObjectRelationship.ROLE_CREATE));
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A), Collections.emptyList(), roles);
        assertFalse(r.hasAnyRight());
    }

    @Test
    public void executeAllowsCreatingNotes() {
        List<JsonRelationship> memberships = Collections.singletonList(rel(USER, GROUP_A, ObjectRelationship.MEMBER_EXECUTE));
        JsonEffectiveRights r = explain(ownedBy(false, GROUP_A), memberships, Collections.emptyList());
        assertFalse(r.isCreate());
        assertEquals(PermissionExplainer.CREATE_EXCEPTION_CLASSES, r.getCreateViaExecuteClasses());

        List<JsonRelationship> withCreate = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_EXECUTE),
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_CREATE));
        assertTrue(explain(ownedBy(false, GROUP_A), withCreate, Collections.emptyList()).getCreateViaExecuteClasses().isEmpty());
    }

    @Test
    public void createExceptionClassesMatchTheRightManager() {
        assertEquals(Arrays.asList("Data Notes", "User Data"), PermissionExplainer.CREATE_EXCEPTION_CLASSES);
    }

    @Test
    public void roleTypesMatchMembershipTypes() {
        assertEquals(ObjectRelationship.ROLE_READ, PermissionExplainer.roleTypeFor(ObjectRelationship.MEMBER_READ));
        assertEquals(ObjectRelationship.ROLE_WRITE, PermissionExplainer.roleTypeFor(ObjectRelationship.MEMBER_WRITE));
        assertEquals(ObjectRelationship.ROLE_CREATE, PermissionExplainer.roleTypeFor(ObjectRelationship.MEMBER_CREATE));
        assertEquals(ObjectRelationship.ROLE_DELETE, PermissionExplainer.roleTypeFor(ObjectRelationship.MEMBER_DELETE));
        assertEquals(ObjectRelationship.ROLE_EXECUTE, PermissionExplainer.roleTypeFor(ObjectRelationship.MEMBER_EXECUTE));
        assertEquals(-1, PermissionExplainer.roleTypeFor(ObjectRelationship.OWNER));
    }

    @Test
    public void groupRightsListMembershipsAndCoveringRoles() {
        List<JsonRelationship> memberships = Arrays.asList(
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_READ),
                rel(USER, GROUP_A, ObjectRelationship.MEMBER_DELETE),
                rel(USER, GROUP_B, ObjectRelationship.MEMBER_CREATE));
        List<JsonRelationship> roles = Arrays.asList(
                rel(ROLE_X, USER, ObjectRelationship.ROLE_MEMBER),
                rel(ROLE_X, GROUP_B, ObjectRelationship.ROLE_CREATE));
        List<JsonGroupRights> groups = PermissionExplainer.groupRights(USER, memberships,
                PermissionExplainer.RoleIndex.of(roles), NAME);
        assertEquals(2, groups.size());
        JsonGroupRights a = groups.get(0);
        assertEquals(GROUP_A, a.getGroupId());
        assertTrue(a.isRead() && a.isDelete());
        assertFalse(a.isWrite() || a.isCreate() || a.isExecute());
        assertTrue(a.getRoles().isEmpty());
        JsonGroupRights b = groups.get(1);
        assertTrue(b.isCreate());
        assertEquals(1, b.getRoles().size());
        assertEquals("Role X", b.getRoles().get(0).getName());
    }

    @Test
    public void roleRightsListTheGrantsOfEachRole() {
        List<JsonRelationship> roles = Arrays.asList(
                rel(ROLE_X, USER, ObjectRelationship.ROLE_MEMBER),
                rel(ROLE_X, GROUP_A, ObjectRelationship.ROLE_READ),
                rel(ROLE_X, GROUP_A, ObjectRelationship.ROLE_EXECUTE),
                rel(ROLE_X, GROUP_B, ObjectRelationship.ROLE_WRITE),
                rel(ROLE_Y, OTHER_USER, ObjectRelationship.ROLE_MEMBER));
        List<JsonRoleRights> result = PermissionExplainer.roleRights(USER, PermissionExplainer.RoleIndex.of(roles), NAME);
        assertEquals(1, result.size());
        JsonRoleRights role = result.get(0);
        assertEquals(ROLE_X, role.getRoleId());
        assertEquals(2, role.getGroups().size());
        assertTrue(role.getGroups().get(0).isRead() && role.getGroups().get(0).isExecute());
        assertFalse(role.getGroups().get(0).isWrite());
        assertTrue(role.getGroups().get(1).isWrite());
    }

    @Test
    public void missingNamesAreNull() {
        List<JsonRelationship> memberships = Collections.singletonList(rel(USER, 777, ObjectRelationship.MEMBER_READ));
        JsonEffectiveRights r = PermissionExplainer.explain(USER, "user", false, true, ownedBy(false, 777), memberships, null, null);
        assertTrue(r.isRead());
        assertNull(r.getReadSources().get(0).getGroupName());
    }
}
