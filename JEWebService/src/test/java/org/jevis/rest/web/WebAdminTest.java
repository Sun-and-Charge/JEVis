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
package org.jevis.rest.web;

import org.jevis.api.JEVisConstants.ObjectRelationship;
import org.jevis.api.JEVisConstants.PrimitiveType;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** E10.1 web administration of users, groups and site sharing with a fake environment; neutral test data. */
public class WebAdminTest {

    static final long ORG = 10, HALL = 11, ADMIN_DIR = 12, GROUP_DIR = 13, USER_DIR = 14, OTHER_DIR = 30;
    static final long MANAGE = 20, VIEW = 21, FOREIGN = 22, EMPTY = 23;
    static final long ME = 100, ERIKA = 101, MAX = 102;

    static final class Fake implements WebAdmin.Env {
        long me = ME;
        boolean sysAdmin;
        boolean interactive = true;
        final Map<Long, JsonObject> objects = new LinkedHashMap<>();
        final List<JsonRelationship> relationships = new ArrayList<>();
        final Set<Long> unreadable = new HashSet<>();
        final Set<Long> writable = new HashSet<>();
        final Set<Long> creatable = new HashSet<>();
        final Set<Long> deletable = new HashSet<>();
        final Map<Long, Map<String, String>> attributes = new HashMap<>();
        final List<String> written = new ArrayList<>();
        final List<String> log = new ArrayList<>();
        final Set<Long> recycled = new HashSet<>();
        final Set<Integer> failingTypes = new HashSet<>();
        long nextId = 1000;

        JsonObject add(long id, String name, String jevisClass, Long parent) {
            JsonObject object = new JsonObject();
            object.setId(id);
            object.setName(name);
            object.setJevisClass(jevisClass);
            objects.put(id, object);
            if (parent != null) rel(id, parent, ObjectRelationship.PARENT);
            return object;
        }

        void rel(long from, long to, int type) {
            JsonRelationship rel = new JsonRelationship();
            rel.setFrom(from);
            rel.setTo(to);
            rel.setType(type);
            relationships.add(rel);
        }

        boolean has(long from, long to, int type) {
            for (JsonRelationship rel : relationships) {
                if (rel.getFrom() == from && rel.getTo() == to && rel.getType() == type) return true;
            }
            return false;
        }

        @Override
        public long userId() {
            return me;
        }

        @Override
        public boolean sysAdmin() {
            return sysAdmin;
        }

        @Override
        public boolean interactive() {
            return interactive;
        }

        @Override
        public Collection<JsonObject> objects() {
            return objects.values();
        }

        @Override
        public JsonObject object(long id) {
            return objects.get(id);
        }

        @Override
        public Long parent(long id) {
            for (JsonRelationship rel : relationships) {
                if (rel.getFrom() == id && rel.getType() == ObjectRelationship.PARENT) return rel.getTo();
            }
            return null;
        }

        @Override
        public boolean canRead(JsonObject object) {
            return sysAdmin || !unreadable.contains(object.getId());
        }

        @Override
        public boolean canWrite(JsonObject object) {
            return sysAdmin || writable.contains(object.getId());
        }

        @Override
        public boolean canCreate(JsonObject parent, String jevisClass) {
            return sysAdmin || creatable.contains(parent.getId());
        }

        @Override
        public boolean canDelete(JsonObject object) {
            return sysAdmin || deletable.contains(object.getId());
        }

        @Override
        public List<JsonRelationship> relationships() {
            return relationships;
        }

        @Override
        public Map<String, String> userAttributes(long id) {
            return new HashMap<>(attributes.getOrDefault(id, Collections.emptyMap()));
        }

        @Override
        public boolean hasMfa(long userId) {
            return userId == ERIKA;
        }

        @Override
        public void setAttribute(JsonObject object, String attribute, int primitiveType, String value) {
            written.add(object.getId() + ":" + attribute + ":" + primitiveType);
            if (!WebAdmin.ATTRIBUTE_PASSWORD.equals(attribute)) {
                attributes.computeIfAbsent(object.getId(), k -> new HashMap<>()).put(attribute, value);
            }
        }

        @Override
        public JsonObject create(long parent, String jevisClass, String name, Map<String, String> localNames) {
            long id = nextId++;
            JsonObject created = add(id, name, jevisClass, parent);
            /* owners of the parent are copied */
            if (writable.contains(parent)) writable.add(id);
            if (creatable.contains(parent)) creatable.add(id);
            return created;
        }

        @Override
        public void rename(JsonObject object, String name) {
            object.setName(name);
        }

        @Override
        public void recycle(JsonObject object) {
            recycled.add(object.getId());
        }

        @Override
        public void deleteObject(JsonObject object) {
            objects.remove(object.getId());
        }

        @Override
        public void addRelationship(long from, long to, int type) throws Exception {
            if (failingTypes.contains(type)) throw new Exception("database error");
            rel(from, to, type);
        }

        @Override
        public boolean removeRelationship(long from, long to, int type) {
            return relationships.removeIf(rel -> rel.getFrom() == from && rel.getTo() == to && rel.getType() == type);
        }

        @Override
        public void resetMfa(long userId) {
            written.add(userId + ":mfa-reset");
        }

        @Override
        public void log(SQLDataSource.LOG_EVENT event, String details) {
            log.add(event + " " + details);
        }
    }

    private Fake env;

    @Before
    public void setUp() {
        env = new Fake();
        env.add(ORG, "Musterwerk", "Organization", null);
        env.add(HALL, "Halle Nord", "Building", ORG);
        env.add(ADMIN_DIR, "Administration", "Administration Directory", ORG);
        env.add(GROUP_DIR, "Gruppen", "Group Directory", ADMIN_DIR);
        env.add(USER_DIR, "Benutzer", "User Directory", ADMIN_DIR);
        env.add(OTHER_DIR, "Andere Gruppen", "Group Directory", null);
        env.add(MANAGE, "Musterwerk – Verwalten", "Group", GROUP_DIR);
        env.add(VIEW, "Musterwerk – Ansehen", "Group", GROUP_DIR);
        env.add(EMPTY, "Leer", "Group", GROUP_DIR);
        env.add(FOREIGN, "Fremde Gruppe", "Group", OTHER_DIR);
        env.add(ME, "verwalter", "User", USER_DIR);
        env.add(ERIKA, "erika", "User", USER_DIR);
        env.add(MAX, "max", "User", USER_DIR);
        env.attributes.put(ME, new HashMap<>(Map.of("Enabled", "true", "First Name", "Vera", "Last Name", "Walter")));
        env.attributes.put(ERIKA, new HashMap<>(Map.of("Enabled", "true", "First Name", "Erika", "Last Name", "Muster", "E-Mail", "erika@example.com")));
        env.attributes.put(MAX, new HashMap<>(Map.of("Enabled", "true")));
        /* the caller manages the site: all rights on MANAGE, only read on VIEW */
        for (int type = ObjectRelationship.MEMBER_READ; type <= ObjectRelationship.MEMBER_DELETE; type++) env.rel(ME, MANAGE, type);
        env.rel(ME, VIEW, ObjectRelationship.MEMBER_READ);
        env.rel(ERIKA, VIEW, ObjectRelationship.MEMBER_READ);
        env.rel(ERIKA, FOREIGN, ObjectRelationship.MEMBER_READ);
        env.rel(ORG, MANAGE, ObjectRelationship.OWNER);
        env.writable.addAll(Arrays.asList(ORG, HALL, ADMIN_DIR, GROUP_DIR, USER_DIR, MANAGE, VIEW, EMPTY, ME, ERIKA, MAX));
        env.creatable.addAll(Arrays.asList(ORG, ADMIN_DIR, GROUP_DIR, USER_DIR));
        env.deletable.addAll(Arrays.asList(VIEW, EMPTY));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object body) {
        return (Map<String, Object>) body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object body) {
        return (List<Map<String, Object>>) body;
    }

    private static WebEdit.Reply call(WebEdit.Action action) throws Exception {
        return WebEdit.call(null, action);
    }

    private String version(long user) throws Exception {
        return WebAdmin.userVersion(env, env.object(user));
    }

    // ---------------------------------------------------------------- scope

    @Test
    public void scopeListsSitesWithDirectoriesAndManageFlag() {
        Map<String, Object> scope = WebAdmin.scope(env);
        assertEquals(false, scope.get("sysAdmin"));
        List<Map<String, Object>> sites = list(scope.get("sites"));
        assertEquals(2, sites.size());
        Map<String, Object> hall = sites.get(0);
        Map<String, Object> org = sites.get(1);
        assertEquals("Halle Nord", hall.get("name"));
        assertNull(hall.get("groupDirectory"));
        assertEquals(false, hall.get("canManage"));
        assertEquals(GROUP_DIR, map(org.get("groupDirectory")).get("id"));
        assertEquals(USER_DIR, map(org.get("userDirectory")).get("id"));
        assertEquals(true, org.get("canManage"));

        env.writable.remove(GROUP_DIR);
        env.creatable.remove(USER_DIR);
        Map<String, Object> reduced = list(WebAdmin.scope(env).get("sites")).get(1);
        assertEquals(false, reduced.get("canManage"));
        assertNull(reduced.get("userDirectory"));
    }

    @Test
    public void managesSomethingForNavigation() {
        assertTrue(WebAdmin.managesSomething(true, Collections.emptyList(), o -> false));
        assertTrue(WebAdmin.managesSomething(false, env.objects(), env::canWrite));
        assertFalse(WebAdmin.managesSomething(false, env.objects(), o -> o.getJevisClass().equals("Group")));
    }

    // ---------------------------------------------------------------- users

    @Test
    public void listsUsersWithGroupsAndFilters() throws Exception {
        env.unreadable.add(FOREIGN);
        List<Map<String, Object>> users = WebAdmin.users(env, "muster", null);
        assertEquals(1, users.size());
        Map<String, Object> erika = users.get(0);
        assertEquals("erika", erika.get("account"));
        assertEquals("erika@example.com", erika.get("email"));
        assertEquals(true, erika.get("mfa"));
        assertEquals(false, erika.get("sysAdmin"));
        List<Map<String, Object>> groups = list(erika.get("groups"));
        assertEquals(1, groups.size());
        assertEquals(Collections.singletonList("READ"), groups.get(0).get("rights"));
        assertNotNull(erika.get("version"));
        assertFalse(erika.containsKey("password"));
        assertEquals(3, WebAdmin.users(env, null, ORG).size());
        assertEquals(0, WebAdmin.users(env, null, HALL).size());
    }

    @Test
    public void createsUserWithHashedPasswordAndLog() throws Exception {
        String body = "{\"directory\":" + USER_DIR + ",\"account\":\"neu\",\"firstName\":\"Nora\",\"lastName\":\"Neu\","
                + "\"email\":\"nora@example.com\",\"enabled\":true,\"password\":\"geheim-12345\"}";
        WebEdit.Reply reply = call(s -> WebAdmin.createUser(env, body));
        assertEquals(201, reply.status);
        Map<String, Object> user = map(reply.body);
        assertEquals("neu", user.get("account"));
        assertEquals("Nora", user.get("firstName"));
        long id = (Long) user.get("id");
        assertEquals(USER_DIR, (long) env.parent(id));
        assertTrue(env.written.contains(id + ":Password:" + PrimitiveType.PASSWORD_PBKDF2));
        for (String entry : env.log) assertFalse(entry.contains("geheim"));
        assertTrue(env.log.stream().anyMatch(l -> l.contains("web:admin-user-create")));

        WebEdit.Reply exists = call(s -> WebAdmin.createUser(env, body.replace("\"neu\"", "\"ERIKA\"")));
        assertEquals(409, exists.status);
        assertEquals(WebAdmin.CODE_ACCOUNT_EXISTS, map(exists.body).get("code"));

        WebEdit.Reply weak = call(s -> WebAdmin.createUser(env, body.replace("geheim-12345", "kurz").replace("\"neu\"", "\"neu2\"")));
        assertEquals(400, weak.status);
        assertEquals(WebAdmin.CODE_PASSWORD, map(weak.body).get("code"));

        env.creatable.remove(USER_DIR);
        assertEquals(403, call(s -> WebAdmin.createUser(env, body.replace("\"neu\"", "\"neu3\""))).status);

        env.creatable.add(USER_DIR);
        env.interactive = false;
        assertEquals(403, call(s -> WebAdmin.createUser(env, body.replace("\"neu\"", "\"neu4\""))).status);
    }

    @Test
    public void editsUserAndChecksVersion() throws Exception {
        String body = "{\"version\":\"" + version(ERIKA) + "\",\"firstName\":\"Erika\",\"lastName\":\"Beispiel\",\"enabled\":false}";
        WebEdit.Reply reply = call(s -> WebAdmin.updateUser(env, ERIKA, body));
        assertEquals(200, reply.status);
        assertEquals("Beispiel", map(reply.body).get("lastName"));
        assertEquals(false, map(reply.body).get("enabled"));
        assertTrue(env.written.contains(ERIKA + ":Last Name:" + PrimitiveType.STRING));
        assertFalse(env.written.contains(ERIKA + ":First Name:" + PrimitiveType.STRING));

        /* the old version is outdated now */
        WebEdit.Reply conflict = call(s -> WebAdmin.updateUser(env, ERIKA, body));
        assertEquals(409, conflict.status);
        assertEquals("Beispiel", map(conflict.body).get("lastName"));
    }

    @Test
    public void nobodyDisablesOrDemotesHimselfAndOnlySysAdminsSetTheFlag() throws Exception {
        WebEdit.Reply self = call(s -> WebAdmin.updateUser(env, ME, "{\"version\":\"" + version(ME) + "\",\"enabled\":false}"));
        assertEquals(400, self.status);
        assertEquals(WebAdmin.CODE_SELF, map(self.body).get("code"));

        WebEdit.Reply flag = call(s -> WebAdmin.updateUser(env, ERIKA, "{\"version\":\"" + version(ERIKA) + "\",\"sysAdmin\":true}"));
        assertEquals(403, flag.status);
        assertEquals(WebAdmin.CODE_SYS_ADMIN, map(flag.body).get("code"));

        env.sysAdmin = true;
        env.attributes.get(ME).put("Sys Admin", "true");
        WebEdit.Reply demote = call(s -> WebAdmin.updateUser(env, ME, "{\"version\":\"" + version(ME) + "\",\"sysAdmin\":false}"));
        assertEquals(400, demote.status);
        WebEdit.Reply promote = call(s -> WebAdmin.updateUser(env, ERIKA, "{\"version\":\"" + version(ERIKA) + "\",\"sysAdmin\":true}"));
        assertEquals(200, promote.status);
        assertEquals(true, map(promote.body).get("sysAdmin"));

        /* unchanged flags of oneself are fine */
        env.sysAdmin = false;
        assertEquals(200, call(s -> WebAdmin.updateUser(env, ME, "{\"version\":\"" + version(ME) + "\",\"enabled\":true,\"lastName\":\"Walther\"}")).status);
    }

    @Test
    public void passwordAndMfaReset() throws Exception {
        assertEquals(204, call(s -> WebAdmin.setPassword(env, ERIKA, "{\"password\":\"neues-passwort\"}")).status);
        assertTrue(env.written.contains(ERIKA + ":Password:" + PrimitiveType.PASSWORD_PBKDF2));
        assertEquals(400, call(s -> WebAdmin.setPassword(env, ERIKA, "{\"password\":\"x\"}")).status);
        assertEquals(400, call(s -> WebAdmin.setPassword(env, ME, "{\"password\":\"neues-passwort\"}")).status);
        env.writable.remove(MAX);
        assertEquals(403, call(s -> WebAdmin.setPassword(env, MAX, "{\"password\":\"neues-passwort\"}")).status);

        assertEquals(403, call(s -> WebAdmin.resetMfa(env, ERIKA)).status);
        env.sysAdmin = true;
        assertEquals(204, call(s -> WebAdmin.resetMfa(env, ERIKA)).status);
        assertTrue(env.written.contains(ERIKA + ":mfa-reset"));
        assertTrue(env.log.stream().anyMatch(l -> l.contains("web:admin-mfa-reset")));
    }

    // ---------------------------------------------------------------- memberships

    @Test
    public void membershipDiffLeavesGroupsTheCallerMayNotWrite() throws Exception {
        String body = "{\"version\":\"" + version(ERIKA) + "\",\"groups\":["
                + "{\"id\":" + MANAGE + ",\"rights\":[\"READ\",\"WRITE\"]},"
                + "{\"id\":" + FOREIGN + ",\"rights\":[\"READ\",\"DELETE\"]}]}";
        WebEdit.Reply reply = call(s -> WebAdmin.setUserGroups(env, ERIKA, body));
        assertEquals(200, reply.status);
        assertTrue(env.has(ERIKA, MANAGE, ObjectRelationship.MEMBER_READ));
        assertTrue(env.has(ERIKA, MANAGE, ObjectRelationship.MEMBER_WRITE));
        assertFalse(env.has(ERIKA, MANAGE, ObjectRelationship.MEMBER_DELETE));
        /* removed from VIEW (writable), FOREIGN untouched (not writable) */
        assertFalse(env.has(ERIKA, VIEW, ObjectRelationship.MEMBER_READ));
        assertTrue(env.has(ERIKA, FOREIGN, ObjectRelationship.MEMBER_READ));
        assertFalse(env.has(ERIKA, FOREIGN, ObjectRelationship.MEMBER_DELETE));
        List<Map<String, Object>> unchanged = list(map(reply.body).get("unchanged"));
        assertEquals(1, unchanged.size());
        assertEquals(FOREIGN, unchanged.get(0).get("id"));
        assertEquals("erika", map(reply.body).get("account"));
        assertTrue(env.log.stream().anyMatch(l -> l.startsWith("DELETE_RELATIONSHIP " + ERIKA + "-" + VIEW + "-101|web:admin-membership")));
    }

    @Test
    public void rightsTheCallerDoesNotHoldCannotBeGranted() throws Exception {
        int before = env.relationships.size();
        String body = "{\"version\":\"" + version(MAX) + "\",\"groups\":["
                + "{\"id\":" + MANAGE + ",\"rights\":[\"READ\"]},"
                + "{\"id\":" + VIEW + ",\"rights\":[\"READ\",\"WRITE\"]}]}";
        WebEdit.Reply reply = call(s -> WebAdmin.setUserGroups(env, MAX, body));
        assertEquals(403, reply.status);
        assertEquals(WebAdmin.CODE_GRANT, map(reply.body).get("code"));
        assertEquals(Collections.singletonList("WRITE"), map(reply.body).get("rights"));
        assertEquals(before, env.relationships.size());

        /* Sys Admins grant everything */
        env.sysAdmin = true;
        assertEquals(200, call(s -> WebAdmin.setUserGroups(env, MAX, body)).status);
        assertTrue(env.has(MAX, VIEW, ObjectRelationship.MEMBER_WRITE));
    }

    @Test
    public void groupMembersFromTheGroupSide() throws Exception {
        String version = WebAdmin.groupVersion(env, env.object(VIEW));
        String body = "{\"version\":\"" + version + "\",\"members\":[{\"user\":" + MAX + ",\"rights\":[\"READ\"]},"
                + "{\"user\":{\"id\":" + ME + "},\"rights\":[\"READ\"]}]}";
        WebEdit.Reply reply = call(s -> WebAdmin.setMembers(env, VIEW, body));
        assertEquals(200, reply.status);
        assertTrue(env.has(MAX, VIEW, ObjectRelationship.MEMBER_READ));
        assertFalse(env.has(ERIKA, VIEW, ObjectRelationship.MEMBER_READ));
        assertEquals(2, list(map(reply.body).get("members")).size());
        assertEquals(409, call(s -> WebAdmin.setMembers(env, VIEW, body)).status);
    }

    // ---------------------------------------------------------------- groups

    @Test
    public void groupCreateRenameDelete() throws Exception {
        WebEdit.Reply created = call(s -> WebAdmin.createGroup(env, "{\"directory\":" + GROUP_DIR + ",\"name\":\"Technik\"}"));
        assertEquals(201, created.status);
        Map<String, Object> group = map(created.body);
        assertEquals("Musterwerk", map(group.get("site")).get("name"));
        long id = (Long) group.get("id");
        /* a site manager who creates a group holds all rights in it, so he can add others */
        for (int type = ObjectRelationship.MEMBER_READ; type <= ObjectRelationship.MEMBER_DELETE; type++) {
            assertTrue(env.has(ME, id, type));
        }

        WebEdit.Reply renamed = call(s -> WebAdmin.renameGroup(env, id, "{\"version\":\"" + group.get("version") + "\",\"name\":\"Technik Nord\"}"));
        assertEquals(200, renamed.status);
        assertEquals("Technik Nord", map(renamed.body).get("name"));
        assertEquals(409, call(s -> WebAdmin.renameGroup(env, id, "{\"version\":\"" + group.get("version") + "\",\"name\":\"X\"}")).status);

        WebEdit.Reply withMembers = call(s -> WebAdmin.deleteGroup(env, VIEW, null));
        assertEquals(409, withMembers.status);
        assertEquals(WebAdmin.CODE_HAS_MEMBERS, map(withMembers.body).get("code"));
        assertEquals(2, map(withMembers.body).get("members"));
        assertFalse(env.recycled.contains(VIEW));

        assertEquals(403, call(s -> WebAdmin.deleteGroup(env, MANAGE, null)).status);
        assertEquals(204, call(s -> WebAdmin.deleteGroup(env, EMPTY, WebAdmin.groupVersion(env, env.object(EMPTY)))).status);
        assertTrue(env.recycled.contains(EMPTY));
        assertTrue(env.log.stream().anyMatch(l -> l.contains("web:admin-group-delete")));
    }

    // ---------------------------------------------------------------- sharing

    @Test
    public void sharingShowsGroupsAndPresets() throws Exception {
        Map<String, Object> sharing = WebAdmin.sharing(env, ORG);
        Map<String, Object> groups = map(sharing.get("groups"));
        assertEquals(MANAGE, map(groups.get("MANAGE")).get("id"));
        assertEquals(VIEW, map(groups.get("VIEW")).get("id"));
        assertNull(groups.get("EDIT"));
        List<Map<String, Object>> users = list(sharing.get("users"));
        assertEquals(2, users.size());
        assertEquals("erika", users.get(0).get("account"));
        assertEquals("VIEW", users.get(0).get("preset"));
        assertEquals("MANAGE", users.get(1).get("preset"));
    }

    @Test
    public void sharingPreviewAndApplyListFailedActions() throws Exception {
        String body = "{\"assignments\":{\"" + MAX + "\":\"VIEW\",\"" + ERIKA + "\":\"EDIT\"},\"includeSubtree\":false}";
        Map<String, Object> preview = WebAdmin.preview(env, ORG, body);
        List<Map<String, Object>> planned = list(preview.get("actions"));
        assertFalse(planned.isEmpty());
        assertEquals(1, map(preview.get("counts")).get("createObjects"));
        /* the caller holds no rights on the new EDIT group, so the memberships there are refused */
        assertTrue(planned.stream().anyMatch(a -> Boolean.FALSE.equals(a.get("allowed")) && WebAdmin.CODE_GRANT.equals(a.get("reason"))));
        assertTrue(env.log.isEmpty());

        env.failingTypes.add(ObjectRelationship.ROOT);
        WebEdit.Reply reply = call(s -> WebAdmin.apply(env, ORG, body));
        assertEquals(200, reply.status);
        Map<String, Object> result = map(reply.body);
        List<Map<String, Object>> failed = list(result.get("failed"));
        assertTrue((Integer) result.get("done") > 0);
        assertTrue(failed.stream().anyMatch(f -> "database error".equals(f.get("error"))));
        assertTrue(failed.stream().anyMatch(f -> WebAdmin.CODE_GRANT.equals(f.get("error"))));
        /* the new EDIT group was created in the group directory, MAX became a VIEW member, ERIKA left VIEW */
        JsonObject edit = env.objects.values().stream().filter(o -> o.getName().endsWith("Bearbeiten")).findFirst().orElse(null);
        assertNotNull(edit);
        assertEquals(GROUP_DIR, (long) env.parent(edit.getId()));
        assertTrue(env.has(MAX, VIEW, ObjectRelationship.MEMBER_READ));
        assertFalse(env.has(ERIKA, VIEW, ObjectRelationship.MEMBER_READ));
        assertFalse(env.has(ERIKA, edit.getId(), ObjectRelationship.MEMBER_READ));
        assertTrue(env.has(ORG, edit.getId(), ObjectRelationship.OWNER));
        assertTrue(env.log.stream().anyMatch(l -> l.contains("web:admin-sharing")));

        env.interactive = false;
        assertEquals(403, call(s -> WebAdmin.apply(env, ORG, body)).status);
    }

    @Test
    public void sharingOfUnknownSite() throws Exception {
        assertEquals(404, call(s -> WebEdit.Reply.ok(WebAdmin.sharing(env, GROUP_DIR))).status);
        assertEquals(400, call(s -> WebEdit.Reply.ok(WebAdmin.preview(env, ORG, "{\"assignments\":{\"" + MAX + "\":\"ALL\"}}"))).status);
    }
}
