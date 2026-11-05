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

import java.util.LinkedHashMap;
import java.util.Map;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;
import static org.jevis.commons.permission.PermissionConsistencyCheckTest.add;
import static org.junit.Assert.*;

public class SiteSharingTest {

    private RightsModel model;

    @Before
    public void setUp() {
        model = new RightsModel();
        add(model, 1, "System", "System", null);
        add(model, 10, "Example Org", "Organization", 1L);
        add(model, 11, "Administration", "Administration Directory", 10L);
        add(model, 12, "Users", "User Directory", 11L);
        add(model, 40, "Alice", "User", 12L);
        add(model, 41, "Bob", "User", 12L);
        add(model, 42, "Carol", "User", 12L);
        add(model, 20, "Site A", "Building", 10L);
        add(model, 21, "Data", "Data Directory", 20L);
        add(model, 22, "Meter 1", "Data", 21L);
        add(model, 30, "Other Group", "Group", 12L);
        model.addRelationship(40, 30, MEMBER_READ);
        model.addRelationship(40, 30, MEMBER_WRITE);
    }

    private Map<Long, SiteSharing.Preset> assign(Object... pairs) {
        Map<Long, SiteSharing.Preset> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(((Number) pairs[i]).longValue(), (SiteSharing.Preset) pairs[i + 1]);
        return map;
    }

    private RightsModel.Node group(SiteSharing.Preset preset) {
        return new SiteSharing(model, 20, null).getGroup(preset);
    }

    @Test
    public void createsDirectoriesGroupsOwnerEntryPointAndMemberships() {
        SiteSharing sharing = new SiteSharing(model, 20, null);
        RightsPlan plan = sharing.plan(assign(40, SiteSharing.Preset.VIEW, 41, SiteSharing.Preset.MANAGE), true);
        PlanSimulator.apply(model, plan);

        RightsModel.Node admin = model.getChildren(20, "Administration Directory").get(0);
        RightsModel.Node groupDir = model.getChildren(admin.id, "Group Directory").get(0);
        RightsModel.Node view = group(SiteSharing.Preset.VIEW);
        RightsModel.Node manage = group(SiteSharing.Preset.MANAGE);
        assertNotNull(view);
        assertNotNull(manage);
        assertNull("no group for an unused preset", group(SiteSharing.Preset.EDIT));
        assertEquals("Site A – Ansehen", view.name);
        assertEquals(groupDir.id, (long) view.getParentId());
        for (RightsModel.Node group : new RightsModel.Node[]{view, manage}) {
            assertTrue(model.has(20, group.id, OWNER));
            assertTrue(model.has(group.id, 20, ROOT));
            assertTrue(model.has(21, group.id, OWNER));
            assertTrue(model.has(22, group.id, OWNER));
            assertFalse("administration stays private", model.has(admin.id, group.id, OWNER));
        }
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 40).get(view.id));
        assertNull(Rights.membershipsOf(model, 40).get(manage.id));
        assertEquals(Integer.valueOf(Rights.ALL), Rights.membershipsOf(model, 41).get(manage.id));
        assertEquals("other memberships untouched", Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 40).get(30L));
        assertTrue(Rights.membershipsOf(model, 42).isEmpty());
    }

    @Test
    public void secondRunPlansNothing() {
        Map<Long, SiteSharing.Preset> choice = assign(40, SiteSharing.Preset.VIEW, 41, SiteSharing.Preset.EDIT);
        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(choice, true));
        RightsPlan second = new SiteSharing(model, 20, null).plan(choice, true);
        assertTrue(second.getActions().toString(), second.isEmpty());
    }

    @Test
    public void currentPresetsAreReadBack() {
        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.VIEW, 41, SiteSharing.Preset.EDIT), false));
        Map<Long, SiteSharing.Preset> current = new SiteSharing(model, 20, null).currentPresets();
        assertEquals(2, current.size());
        assertEquals(SiteSharing.Preset.VIEW, current.get(40L));
        assertEquals(SiteSharing.Preset.EDIT, current.get(41L));
    }

    @Test
    public void changingPresetMovesUserBetweenGroups() {
        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.VIEW), false));
        RightsPlan change = new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.EDIT), false);
        PlanSimulator.apply(model, change);
        RightsModel.Node view = group(SiteSharing.Preset.VIEW);
        RightsModel.Node edit = group(SiteSharing.Preset.EDIT);
        assertNull(Rights.membershipsOf(model, 40).get(view.id));
        assertEquals(Integer.valueOf(SiteSharing.Preset.EDIT.mask), Rights.membershipsOf(model, 40).get(edit.id));
        assertEquals(1, change.count(RightsPlan.ActionType.REMOVE_RELATIONSHIP, RightsPlan.Category.MEMBERSHIP));

        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.NONE), false));
        assertNull(Rights.membershipsOf(model, 40).get(edit.id));
        assertEquals(Integer.valueOf(Rights.READ | Rights.WRITE), Rights.membershipsOf(model, 40).get(30L));
        assertTrue(new SiteSharing(model, 20, null).currentPresets().isEmpty());
    }

    @Test
    public void reusesExistingGroupsAndDirectories() {
        add(model, 50, "Administration", "Administration Directory", 20L);
        add(model, 51, "Groups", "Group Directory", 50L);
        add(model, 52, "Site A - View", "Group", 51L);
        add(model, 53, "Renamed – Bearbeiten", "Group", 51L);
        model.addRelationship(20, 53, OWNER);
        RightsPlan plan = new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.VIEW, 41, SiteSharing.Preset.EDIT), false);
        assertEquals(0, plan.count(RightsPlan.ActionType.CREATE_OBJECT, null));
        PlanSimulator.apply(model, plan);
        assertEquals(Integer.valueOf(Rights.READ), Rights.membershipsOf(model, 40).get(52L));
        assertEquals(Integer.valueOf(SiteSharing.Preset.EDIT.mask), Rights.membershipsOf(model, 41).get(53L));
        assertTrue(model.has(52, 20, ROOT));
    }

    @Test
    public void keepsRightsGrantedByRole() {
        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.MANAGE), false));
        RightsModel.Node manage = group(SiteSharing.Preset.MANAGE);
        add(model, 60, "Admins", "User Role", 12L);
        model.addRelationship(60, manage.id, ROLE_READ);
        model.addRelationship(60, manage.id, ROLE_DELETE);
        model.addRelationship(60, 40, ROLE_MEMBER);
        PlanSimulator.apply(model, new SiteSharing(model, 20, null).plan(assign(40, SiteSharing.Preset.VIEW), false));
        assertEquals(Integer.valueOf(Rights.READ | Rights.DELETE), Rights.membershipsOf(model, 40).get(manage.id));
    }

    @Test
    public void presetOfMask() {
        assertEquals(SiteSharing.Preset.VIEW, SiteSharing.Preset.ofMask(Rights.READ));
        assertEquals(SiteSharing.Preset.EDIT, SiteSharing.Preset.ofMask(Rights.READ | Rights.WRITE | Rights.EXECUTE | Rights.CREATE));
        assertEquals(SiteSharing.Preset.MANAGE, SiteSharing.Preset.ofMask(Rights.ALL));
        assertEquals(SiteSharing.Preset.NONE, SiteSharing.Preset.ofMask(0));
        assertNull(SiteSharing.Preset.ofMask(Rights.READ | Rights.DELETE));
    }
}
