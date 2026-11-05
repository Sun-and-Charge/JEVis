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
import static org.jevis.commons.permission.PermissionConsistencyCheckTest.add;
import static org.jevis.commons.permission.RightsPlan.ActionType.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BuildingRightsTemplateTest {

    private static final long ORG = 10, BUILDING = 20;
    private RightsModel model;

    @Before
    public void setUp() {
        model = new RightsModel();
        add(model, 1, "System", "System", null);
        add(model, 2, "Plugins", "Directory", 1L);
        long id = 50;
        for (BuildingRightsTemplate.PluginSlot slot : BuildingRightsTemplate.PLUGIN_SLOTS) {
            add(model, id++, slot.pluginClass, slot.pluginClass, 2L);
        }
        add(model, ORG, "Example Org", "Organization", 1L);
        add(model, 11, "Org Users", "User Directory", ORG);
        add(model, BUILDING, "Building", "Building", ORG);
        model.get(BUILDING).localName("de", "Site A");
    }

    private RightsPlan planNew() {
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.createServiceUser = true;
        return new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options);
    }

    private RightsModel.Node child(long parent, String name) {
        return model.getChildren(parent).stream().filter(n -> n.name.equals(name)).findFirst().orElse(null);
    }

    private RightsModel.Node byName(String name) {
        return model.getObjects().stream().filter(n -> n.name.equals(name)).findFirst().orElse(null);
    }

    @Test
    public void newBuildingGetsTheCompleteTemplate() {
        RightsPlan plan = planNew();
        // Administration, Users, Groups, Roles, with entry points, Plugins + 14 data directories
        assertEquals(20, plan.count(CREATE_OBJECT, RightsPlan.Category.DIRECTORY));
        // building groups 2 + 14 data groups + 11 plugin groups
        assertEquals(27, plan.count(CREATE_OBJECT, RightsPlan.Category.GROUP));
        assertEquals(2, plan.count(CREATE_OBJECT, RightsPlan.Category.ROLE));
        assertEquals(1, plan.count(CREATE_OBJECT, RightsPlan.Category.USER));
        assertEquals(0, plan.count(REMOVE_RELATIONSHIP, null));
        // 2 building + 14 directories + 11 plugins
        assertEquals(27, plan.count(ADD_RELATIONSHIP, RightsPlan.Category.OWNER));
        assertEquals(15, plan.count(ADD_RELATIONSHIP, RightsPlan.Category.ENTRY_POINT));
        assertTrue(plan.getWarnings().isEmpty());

        PlanSimulator.apply(model, plan);

        RightsModel.Node admin = child(BUILDING, "Administration");
        assertNotNull(admin);
        RightsModel.Node groups = child(admin.id, "Groups");
        RightsModel.Node data = child(BUILDING, "Data");
        RightsModel.Node dataX = byName("Site A Data X");
        assertEquals("Site A Daten X", dataX.localNames.get("de"));
        assertEquals("بيانات X Site A", dataX.localNames.get("ar"));
        assertTrue(model.isInSubtree(dataX.id, groups.id));
        assertTrue(model.has(data.id, dataX.id, OWNER));
        assertTrue(model.has(dataX.id, data.id, ROOT));
        RightsModel.Node buildingX = byName("Site A X");
        assertTrue(model.has(BUILDING, buildingX.id, OWNER));
        assertTrue(model.has(buildingX.id, BUILDING, ROOT));
        assertNotNull(byName("Site A Dashboard Plugin"));
        assertEquals("Site A Dashboard Modul", byName("Site A Dashboard Plugin").localNames.get("de"));

        RightsModel.Node userRole = byName("User Role");
        RightsModel.Node adminRole = byName("Admin Role");
        assertEquals(Rights.READ | Rights.EXECUTE, (int) Rights.roleRightsOf(model, userRole.id).get(dataX.id));
        assertEquals(Rights.READ, (int) Rights.roleRightsOf(model, userRole.id).get(byName("Site A Reports X").id));
        assertEquals(Rights.ALL, (int) Rights.roleRightsOf(model, adminRole.id).get(dataX.id));
        assertNull("objects plugin is not part of the user role", Rights.roleRightsOf(model, userRole.id).get(byName("Site A Objects Plugin").id));
        assertEquals(Rights.READ, (int) Rights.roleRightsOf(model, adminRole.id).get(byName("Site A Objects Plugin").id));

        RightsModel.Node jsc = byName("JSC@Site A");
        assertTrue(model.has(adminRole.id, jsc.id, ROLE_MEMBER));
        assertEquals(Rights.ALL, (int) Rights.membershipsOf(model, jsc.id).get(dataX.id));
        PermissionConsistencyCheck.RoleDrift drift = new PermissionConsistencyCheck(model).computeRoleDrift(jsc.id);
        assertTrue(drift.getGroups().isEmpty());
    }

    @Test
    public void planIsIdempotent() {
        PlanSimulator.apply(model, planNew());
        RightsPlan second = planNew();
        assertTrue(second.getActions().toString(), second.isEmpty());
    }

    @Test
    public void optionalGroupsWithoutEntryPoint() {
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.withNoEntryPointGroups = true;
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options);
        assertEquals(27 + 14, plan.count(CREATE_OBJECT, RightsPlan.Category.GROUP));
        PlanSimulator.apply(model, plan);
        RightsModel.Node data = child(BUILDING, "Data");
        RightsModel.Node noEntry = byName("Site A Data");
        assertTrue(model.has(data.id, noEntry.id, OWNER));
        assertFalse(model.has(noEntry.id, data.id, ROOT));
        assertTrue(new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options).isEmpty());
    }

    @Test
    public void missingPluginAndClassGiveWarningsInsteadOfGroups() {
        model = new RightsModel();
        model.addClass("Building");
        model.addClass("Data Directory");
        add(model, ORG, "Example Org", "Organization", null);
        add(model, BUILDING, "Site A", "Building", ORG);
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, null).plan(new BuildingRightsTemplate.Options());
        // building groups + Data X only
        assertEquals(3, plan.count(CREATE_OBJECT, RightsPlan.Category.GROUP));
        assertEquals(13 + 11, plan.getWarnings().size());
    }

    /**
     * Older structure: German names only, groups with entry point without " X", a "Module" directory, an
     * organisation-wide group and a few users.
     */
    private void buildOldStructure() {
        add(model, 100, "Administration", "Administration Directory", BUILDING);
        add(model, 101, "Gruppen", "Group Directory", 100L);
        add(model, 102, "Gruppen mit Einstiegspunkt", "Group Directory", 101L);
        add(model, 103, "Benutzerrollen", "User Role Directory", 100L);
        add(model, 110, "Site A", "Group", 101L);
        add(model, 111, "Site A Daten", "Group", 102L);
        add(model, 112, "Site A Berichte", "Group", 102L);
        add(model, 113, "Site A Sonstiges", "Group", 102L);
        add(model, 120, "Benutzer Rolle", "User Role", 103L);
        add(model, 130, "Site A Objekte Plugin", "Group", 101L);

        add(model, 200, "Daten", "Data Directory", BUILDING);
        add(model, 201, "Zähler", "Data", 200L);
        add(model, 202, "Wert", "Clean Data", 201L);
        add(model, 210, "Berichte", "Report Directory", BUILDING);

        add(model, 300, "Org Groups", "Group Directory", ORG);
        add(model, 301, "All sites", "Group", 300L);
        add(model, 302, "Site A - Rechte", "Group", 300L);

        model.addRelationship(BUILDING, 110, OWNER);
        model.addRelationship(BUILDING, 301, OWNER);
        model.addRelationship(BUILDING, 302, OWNER);
        model.addRelationship(ORG, 301, OWNER);
        model.addRelationship(200, 111, OWNER);
        model.addRelationship(111, 200, ROOT);
        model.addRelationship(201, 111, OWNER);   // 202 lacks the owner
        model.addRelationship(210, 112, OWNER);
        model.addRelationship(112, 210, ROOT);
        model.addRelationship(120, 111, ROLE_READ);

        add(model, 400, "reader", "User", 11L);
        add(model, 401, "writer", "User", 11L);
        add(model, 402, "outsider", "User", 11L);
        add(model, 403, "admin", "User", 11L);
        model.get(403).setSysAdmin(true);
        model.addRelationship(400, 111, MEMBER_READ);
        model.addRelationship(400, 113, MEMBER_READ);
        model.addRelationship(400, 301, MEMBER_READ);
        model.addRelationship(401, 111, MEMBER_READ);
        model.addRelationship(401, 111, MEMBER_WRITE);
        model.addRelationship(401, 302, MEMBER_WRITE);
        model.addRelationship(402, 301, MEMBER_READ);
        model.addRelationship(403, 111, MEMBER_READ);
    }

    @Test
    public void existingStructureIsReusedAndNothingIsRemoved() {
        buildOldStructure();
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, "Site A").plan(new BuildingRightsTemplate.Options());
        List<RightsPlan.Action> actions = plan.getActions();

        assertEquals(0, plan.count(REMOVE_RELATIONSHIP, null));
        Set<String> created = actions.stream().filter(a -> a.getType() == CREATE_OBJECT).map(a -> a.getObject().getName()).collect(Collectors.toSet());
        assertFalse("data group found by structure", created.contains("Site A Data X"));
        assertFalse("report group found by structure", created.contains("Site A Reports X"));
        assertFalse(created.contains("Site A"));
        assertFalse(created.contains("Data"));
        assertFalse(created.contains("Administration"));
        assertFalse(created.contains("Groups"));
        assertFalse(created.contains("Roles"));
        assertFalse("user role found by German name", created.contains("User Role"));
        assertTrue(created.contains("Admin Role"));
        assertTrue(created.contains("Site A X"));
        assertTrue(created.contains("Site A Alarms X"));
        assertTrue(created.contains("Alarms"));
        assertTrue(created.contains("Plugins"));
        assertFalse("old plugin group found by its older name", created.contains("Site A Objects Plugin"));
        assertTrue(created.contains("Site A Dashboard Plugin"));

        // the object below the data directory gets the data group as owner, the one that has it keeps it
        assertTrue(actions.stream().anyMatch(a -> a.getType() == ADD_RELATIONSHIP && a.getRelType() == OWNER
                && a.getFrom().getId() != null && a.getFrom().getId() == 202 && a.getTo().getId() == 111));
        assertFalse(actions.stream().anyMatch(a -> a.getType() == ADD_RELATIONSHIP && a.getRelType() == OWNER
                && a.getFrom().getId() != null && a.getFrom().getId() == 201));

        PlanSimulator.apply(model, plan);
        assertTrue(new BuildingRightsTemplate(model, BUILDING, "Site A").plan(new BuildingRightsTemplate.Options()).isEmpty());
        // nothing that existed before is gone
        assertTrue(model.has(400, 113, MEMBER_READ));
        assertTrue(model.has(120, 111, ROLE_READ));
        assertTrue(model.has(BUILDING, 302, OWNER));
    }

    @Test
    public void noSubtreePropagationIfDisabled() {
        buildOldStructure();
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.propagateOwnersToSubtree = false;
        options.createMissingDirectories = false;
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options);
        assertFalse(plan.getActions().stream().anyMatch(a -> a.getFrom().getId() != null && a.getFrom().getId() == 202));
        Set<String> created = plan.getActions().stream().filter(a -> a.getType() == CREATE_OBJECT).map(a -> a.getObject().getName()).collect(Collectors.toSet());
        assertFalse(created.contains("Alarms"));
        assertFalse(created.contains("Site A Alarms X"));
    }

    @Test
    public void userCandidatesAndSuggestions() {
        buildOldStructure();
        List<BuildingRightsTemplate.UserCandidate> candidates = new BuildingRightsTemplate(model, BUILDING, "Site A").findUserCandidates();
        Map<String, BuildingRightsTemplate.UserCandidate> byUser = candidates.stream()
                .collect(Collectors.toMap(BuildingRightsTemplate.UserCandidate::getUserName, c -> c));
        assertEquals(new HashSet<>(Arrays.asList("reader", "writer")), byUser.keySet());
        assertEquals(BuildingRightsTemplate.RightsLevel.READ, byUser.get("reader").getLevel());
        assertEquals(BuildingRightsTemplate.RoleChoice.USER_ROLE, byUser.get("reader").getSuggestion());
        assertEquals(BuildingRightsTemplate.RightsLevel.WRITE, byUser.get("writer").getLevel());
        assertEquals(BuildingRightsTemplate.RoleChoice.ADMIN_ROLE, byUser.get("writer").getSuggestion());
        // "All sites" also owns the organisation: not a group of this building
        assertFalse(byUser.get("reader").getGroupIds().contains(301L));
        // "Site A - Rechte" owns only the building: counts
        assertTrue(byUser.get("writer").getGroupIds().contains(302L));

        model.addRelationship(120, 400, ROLE_MEMBER);
        byUser = new BuildingRightsTemplate(model, BUILDING, "Site A").findUserCandidates().stream()
                .collect(Collectors.toMap(BuildingRightsTemplate.UserCandidate::getUserName, c -> c));
        assertEquals(BuildingRightsTemplate.RoleChoice.USER_ROLE, byUser.get("reader").getCurrentRole());
        assertEquals(BuildingRightsTemplate.RoleChoice.SKIP, byUser.get("reader").getSuggestion());
    }

    @Test
    public void migrationAssignsRolesAndKeepsOldMembershipsByDefault() {
        buildOldStructure();
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.userChoices.put(400L, BuildingRightsTemplate.RoleChoice.USER_ROLE);
        options.userChoices.put(401L, BuildingRightsTemplate.RoleChoice.ADMIN_ROLE);
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options);
        assertEquals(0, plan.count(REMOVE_RELATIONSHIP, null));
        assertEquals(2, plan.count(ADD_RELATIONSHIP, RightsPlan.Category.ROLE_MEMBER));
        PlanSimulator.apply(model, plan);

        assertTrue(model.has(120, 400, ROLE_MEMBER));
        RightsModel.Node adminRole = byName("Admin Role");
        assertTrue(model.has(adminRole.id, 401, ROLE_MEMBER));
        assertEquals(Rights.ALL, (int) Rights.membershipsOf(model, 401).get(111L));
        assertTrue(model.has(400, byName("Site A X").id, MEMBER_READ) || model.has(400, 110, MEMBER_READ));
        assertTrue(model.has(400, 113, MEMBER_READ));
        assertTrue(model.has(401, 302, MEMBER_WRITE));
        assertTrue(new PermissionConsistencyCheck(model).computeRoleDrift(401).getGroups().isEmpty());
    }

    @Test
    public void migrationCanRemoveOldMembershipsOfThisBuildingOnly() {
        buildOldStructure();
        BuildingRightsTemplate.Options options = new BuildingRightsTemplate.Options();
        options.removeOldMemberships = true;
        options.userChoices.put(400L, BuildingRightsTemplate.RoleChoice.USER_ROLE);
        options.userChoices.put(401L, BuildingRightsTemplate.RoleChoice.SKIP);
        RightsPlan plan = new BuildingRightsTemplate(model, BUILDING, "Site A").plan(options);
        PlanSimulator.apply(model, plan);

        assertFalse("old group of the building", model.has(400, 113, MEMBER_READ));
        assertTrue("group of the role stays", model.has(400, 111, MEMBER_READ));
        assertTrue("organisation-wide group stays", model.has(400, 301, MEMBER_READ));
        assertTrue("skipped user unchanged", model.has(401, 302, MEMBER_WRITE));
    }

    @Test
    public void executorCreatesObjectsBeforeRelationships() throws Exception {
        RightsModel small = new RightsModel();
        add(small, 1, "Site", "Building", null);
        add(small, 2, "Readers", "Group", 1L);
        RightsPlan plan = new RightsPlan();
        RightsPlan.Ref site = plan.existing(small.get(1));
        RightsPlan.Ref readers = plan.existing(small.get(2));
        plan.addRelationship(site, readers, OWNER, RightsPlan.Category.OWNER);
        RightsPlan.Ref group = plan.create(site, "New", "Group", Collections.singletonMap("de", "Neu"), RightsPlan.Category.GROUP);
        plan.addRelationship(site, group, OWNER, RightsPlan.Category.OWNER);
        plan.removeRelationship(site, readers, MEMBER_READ, RightsPlan.Category.MEMBERSHIP);

        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisObject siteObj = mock(JEVisObject.class);
        JEVisObject created = mock(JEVisObject.class);
        JEVisClass groupClass = mock(JEVisClass.class);
        when(ds.getObject(1L)).thenReturn(siteObj);
        when(ds.getJEVisClass("Group")).thenReturn(groupClass);
        when(siteObj.buildObject("New", groupClass)).thenReturn(created);
        when(created.getID()).thenReturn(77L);
        when(ds.buildRelationship(anyLong(), anyLong(), anyInt())).thenReturn(mock(JEVisRelationship.class));
        when(ds.deleteRelationship(anyLong(), anyLong(), anyInt())).thenReturn(true);

        List<String> progress = new ArrayList<>();
        RightsPlanExecutor.Result result = new RightsPlanExecutor(ds).setObjectDelayMs(0)
                .execute(plan, (done, total, action) -> progress.add(done + "/" + total));

        assertEquals(4, result.getDone());
        assertEquals(0, result.getFailed());
        assertEquals(Arrays.asList("1/4", "2/4", "3/4", "4/4"), progress);
        verify(created).setLocalNames(Collections.singletonMap("de", "Neu"));
        verify(created).commit();
        verify(ds).buildRelationship(1L, 2L, OWNER);
        verify(ds).buildRelationship(1L, 77L, OWNER);
        verify(ds).deleteRelationship(1L, 2L, MEMBER_READ);
    }

    @Test
    public void executorReportsFailures() throws Exception {
        RightsModel small = new RightsModel();
        add(small, 1, "Site", "Building", null);
        RightsPlan plan = new RightsPlan();
        RightsPlan.Ref site = plan.existing(small.get(1));
        RightsPlan.Ref group = plan.create(site, "New", "Group", null, RightsPlan.Category.GROUP);
        plan.addRelationship(site, group, OWNER, RightsPlan.Category.OWNER);
        JEVisDataSource ds = mock(JEVisDataSource.class);
        when(ds.getObject(1L)).thenReturn(null);

        RightsPlanExecutor.Result result = new RightsPlanExecutor(ds).setObjectDelayMs(0).execute(plan, null);
        assertEquals(2, result.getFailed());
        assertNotNull(plan.getActions().get(0).getError());
        verify(ds, never()).buildRelationship(anyLong(), anyLong(), anyInt());
    }
}
