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

import com.fasterxml.jackson.databind.JsonNode;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.permission.Rights;
import org.jevis.commons.permission.RightsModel;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.SiteSharing;
import org.jevis.commons.ws.json.JsonI18n;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.commons.ws.sql.auth.PasswordResetPolicy;
import org.jevis.rest.RelationshipGrant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Stage E10.1 (docs/WEB-API.md "E10.1 users, groups, rights"): the logic of the web administration of users, groups
 * and site sharing, on an {@link Env} so it can be tested without a database.
 * <p>
 * Rights are those of the generic API: creating needs the create right on the directory, changing a user or group the
 * write right on it, a membership change the write right on the user and on the group (as {@code POST /relationships})
 * plus the E10.0 rule {@link RelationshipGrant} for new memberships, deleting the delete right. Sys Admins may do
 * everything. Nobody disables or demotes himself; the Sys Admin flag is set by Sys Admins only.
 */
final class WebAdmin {

    static final String CLASS_USER = "User";
    static final String CLASS_GROUP = "Group";
    static final String CLASS_USER_DIRECTORY = "User Directory";
    static final String CLASS_GROUP_DIRECTORY = "Group Directory";
    static final List<String> SITE_CLASSES = Collections.unmodifiableList(Arrays.asList("Organization", "Building"));

    static final String ATTRIBUTE_FIRST_NAME = "First Name";
    static final String ATTRIBUTE_LAST_NAME = "Last Name";
    static final String ATTRIBUTE_EMAIL = "E-Mail";
    static final String ATTRIBUTE_ENABLED = "Enabled";
    static final String ATTRIBUTE_SYS_ADMIN = "Sys Admin";
    static final String ATTRIBUTE_PASSWORD = "Password";
    /** the user attributes read by the administration (never the password) */
    static final List<String> USER_ATTRIBUTES = Collections.unmodifiableList(Arrays.asList(
            ATTRIBUTE_FIRST_NAME, ATTRIBUTE_LAST_NAME, ATTRIBUTE_EMAIL, ATTRIBUTE_ENABLED, ATTRIBUTE_SYS_ADMIN));

    /** right names of the API, index = bit of {@link Rights} = {@code MEMBER_READ + index} */
    static final List<String> RIGHT_NAMES = Collections.unmodifiableList(Arrays.asList("READ", "WRITE", "EXECUTE", "CREATE", "DELETE"));

    static final String CODE_ACCOUNT_EXISTS = "ACCOUNT_EXISTS";
    static final String CODE_SELF = "SELF";
    static final String CODE_GRANT = "GRANT";
    static final String CODE_HAS_MEMBERS = "HAS_MEMBERS";
    static final String CODE_PASSWORD = "PASSWORD";
    static final String CODE_SYS_ADMIN = "SYS_ADMIN";
    static final String CODE_RIGHTS = "RIGHTS";

    private WebAdmin() {
    }

    // ---------------------------------------------------------------- environment

    /**
     * Data access and rights of one request (production: {@link WebAdminSql}).
     */
    interface Env {
        long userId();

        boolean sysAdmin();

        /** false for API key logins: they must not manage accounts */
        boolean interactive();

        /** all objects, readable or not (for parents and the account check) */
        Collection<JsonObject> objects();

        /** the object or null */
        JsonObject object(long id);

        Long parent(long id);

        boolean canRead(JsonObject object);

        boolean canWrite(JsonObject object);

        boolean canCreate(JsonObject parent, String jevisClass);

        boolean canDelete(JsonObject object);

        /** all relationships, kept up to date with the changes of this request */
        List<JsonRelationship> relationships();

        /** latest values of {@link #USER_ATTRIBUTES} of a user (missing if not set) */
        Map<String, String> userAttributes(long id) throws Exception;

        boolean hasMfa(long userId);

        /** writes a sample "now"; the password is hashed by the data source */
        void setAttribute(JsonObject object, String attribute, int primitiveType, String value) throws Exception;

        JsonObject create(long parent, String jevisClass, String name, Map<String, String> localNames) throws Exception;

        void rename(JsonObject object, String name) throws Exception;

        void recycle(JsonObject object) throws Exception;

        /** removes an object created in this request for good (rollback) */
        void deleteObject(JsonObject object) throws Exception;

        void addRelationship(long from, long to, int type) throws Exception;

        boolean removeRelationship(long from, long to, int type) throws Exception;

        /** ends the two-factor authentication and all sessions of the user */
        void resetMfa(long userId) throws Exception;

        void log(SQLDataSource.LOG_EVENT event, String details);
    }

    // ---------------------------------------------------------------- answers

    static WebEdit.Reply error(int status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        return new WebEdit.Reply(status, body);
    }

    static WebEdit.Stop stop(int status, String code, String message) {
        return new WebEdit.Stop(error(status, code, message));
    }

    private static void requireInteractive(Env env) throws WebEdit.Stop {
        if (!env.interactive()) throw new WebEdit.Stop(WebEdit.Reply.forbidden("Not allowed for API keys"));
    }

    // ---------------------------------------------------------------- helpers

    static boolean deleted(JsonObject object) {
        return object == null || ResourceWebIso.isDeleted(object);
    }

    /** the object if it exists, is readable, not deleted and of the class, else null */
    static JsonObject readable(Env env, long id, String jevisClass) {
        JsonObject object = env.object(id);
        if (deleted(object) || !env.canRead(object)) return null;
        return jevisClass == null || jevisClass.equals(object.getJevisClass()) ? object : null;
    }

    /** the nearest organisation or building at or above the object, null if none */
    static JsonObject site(Env env, long id) {
        Set<Long> seen = new HashSet<>();
        Long current = id;
        while (current != null && seen.add(current)) {
            JsonObject object = env.object(current);
            if (object == null) return null;
            if (SITE_CLASSES.contains(object.getJevisClass())) return object;
            current = env.parent(current);
        }
        return null;
    }

    /** whether the object lies below (or is) the ancestor */
    static boolean below(Env env, long id, long ancestor) {
        Set<Long> seen = new HashSet<>();
        Long current = id;
        while (current != null && seen.add(current)) {
            if (current == ancestor) return true;
            current = env.parent(current);
        }
        return false;
    }

    private static int depth(Env env, long id) {
        int depth = 0;
        Set<Long> seen = new HashSet<>();
        Long current = env.parent(id);
        while (current != null && seen.add(current)) {
            depth++;
            current = env.parent(current);
        }
        return depth;
    }

    static Map<String, Object> ref(JsonObject object) {
        if (object == null) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        return json;
    }

    static List<String> rightNames(int mask) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < RIGHT_NAMES.size(); i++) {
            if ((mask & (1 << i)) != 0) names.add(RIGHT_NAMES.get(i));
        }
        return names;
    }

    /**
     * @throws WebEdit.Stop {@code 400} for unknown names
     */
    static int rightMask(JsonNode rights) throws WebEdit.Stop {
        if (rights == null || rights.isNull()) return 0;
        if (!rights.isArray()) throw WebEdit.Stop.badRequest("Invalid rights");
        int mask = 0;
        for (JsonNode right : rights) {
            int index = right.isTextual() ? RIGHT_NAMES.indexOf(right.asText().trim().toUpperCase(Locale.ROOT)) : -1;
            if (index < 0) throw WebEdit.Stop.badRequest("Invalid right " + right);
            mask |= 1 << index;
        }
        return mask;
    }

    /** user id → group id → rights of all memberships */
    static Map<Long, Map<Long, Integer>> memberships(Env env) {
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (JsonRelationship rel : env.relationships()) {
            if (Rights.isMemberType(rel.getType())) {
                result.computeIfAbsent(rel.getFrom(), k -> new HashMap<>()).merge(rel.getTo(), Rights.bitOfMemberType(rel.getType()), (a, b) -> a | b);
            }
        }
        return result;
    }

    static Map<Long, Integer> groupsOf(Env env, long userId) {
        return memberships(env).getOrDefault(userId, Collections.emptyMap());
    }

    /** user id → rights on the group */
    static Map<Long, Integer> membersOf(Env env, long groupId) {
        Map<Long, Integer> result = new HashMap<>();
        for (JsonRelationship rel : env.relationships()) {
            if (rel.getTo() == groupId && Rights.isMemberType(rel.getType())) {
                result.merge(rel.getFrom(), Rights.bitOfMemberType(rel.getType()), (a, b) -> a | b);
            }
        }
        return result;
    }

    /** group id → membership types the calling user holds (for {@link RelationshipGrant#mayGrant}) */
    static Map<Long, Set<Integer>> held(Env env) {
        Map<Long, Set<Integer>> held = new HashMap<>();
        long me = env.userId();
        for (JsonRelationship rel : env.relationships()) {
            if (rel.getFrom() == me && Rights.isMemberType(rel.getType())) {
                held.computeIfAbsent(rel.getTo(), k -> new HashSet<>()).add(rel.getType());
            }
        }
        return held;
    }

    static String version(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String part : parts) {
                digest.update(String.valueOf(part).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            StringBuilder hex = new StringBuilder();
            byte[] hash = digest.digest();
            for (int i = 0; i < 8; i++) hex.append(String.format("%02x", hash[i]));
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String membershipKey(Map<Long, Integer> rights) {
        StringBuilder key = new StringBuilder();
        for (Long id : new TreeMap<>(rights).keySet()) key.append(id).append(':').append(rights.get(id)).append(';');
        return key.toString();
    }

    static boolean bool(String value) {
        return value != null && ("1".equals(value.trim()) || "true".equalsIgnoreCase(value.trim()));
    }

    private static String version(JsonNode body) throws WebEdit.Stop {
        String version = WebEdit.text(body, "version");
        if (version == null || version.trim().isEmpty()) throw WebEdit.Stop.badRequest("version missing");
        return version.trim();
    }

    /** an id given as number or as object with {@code id} */
    private static Long id(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) node = node.get("id");
        return node != null && node.canConvertToLong() ? node.asLong() : null;
    }

    // ---------------------------------------------------------------- scope

    /**
     * {@code GET /web/admin/scope}: the readable organisations and buildings with the directories new users and groups
     * go into and whether the caller manages the site.
     */
    static Map<String, Object> scope(Env env) {
        List<JsonObject> sites = new ArrayList<>();
        List<JsonObject> groupDirs = new ArrayList<>();
        List<JsonObject> userDirs = new ArrayList<>();
        for (JsonObject object : env.objects()) {
            if (deleted(object) || !env.canRead(object)) continue;
            if (SITE_CLASSES.contains(object.getJevisClass())) sites.add(object);
            else if (CLASS_GROUP_DIRECTORY.equals(object.getJevisClass())) groupDirs.add(object);
            else if (CLASS_USER_DIRECTORY.equals(object.getJevisClass())) userDirs.add(object);
        }
        sites.sort(Comparator.comparing((JsonObject o) -> String.valueOf(o.getName()).toLowerCase(Locale.ROOT)).thenComparing(JsonObject::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject site : sites) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", site.getId());
            json.put("name", site.getName());
            json.put("className", site.getJevisClass());
            json.put("groupDirectory", ref(directory(env, site, groupDirs, CLASS_GROUP)));
            json.put("userDirectory", ref(directory(env, site, userDirs, CLASS_USER)));
            boolean canManage = false;
            for (JsonObject dir : groupDirs) {
                JsonObject dirSite = site(env, dir.getId());
                if (dirSite != null && dirSite.getId() == site.getId() && env.canWrite(dir)) {
                    canManage = true;
                    break;
                }
            }
            json.put("canManage", canManage);
            list.add(json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sysAdmin", env.sysAdmin());
        result.put("sites", list);
        return result;
    }

    /** the topmost directory of the site (nearest site) in which the caller may create the class, null if none */
    static JsonObject directory(Env env, JsonObject site, List<JsonObject> directories, String jevisClass) {
        JsonObject best = null;
        int bestDepth = Integer.MAX_VALUE;
        for (JsonObject dir : directories) {
            JsonObject dirSite = site(env, dir.getId());
            if (dirSite == null || dirSite.getId() != site.getId() || !env.canCreate(dir, jevisClass)) continue;
            int depth = depth(env, dir.getId());
            if (depth < bestDepth) {
                best = dir;
                bestDepth = depth;
            }
        }
        return best;
    }

    /**
     * For the navigation: whether the caller may see "Verwaltung" (Sys Admin or write right on a group or user
     * directory).
     */
    static boolean managesSomething(boolean sysAdmin, Collection<JsonObject> readable, java.util.function.Predicate<JsonObject> canWrite) {
        if (sysAdmin) return true;
        for (JsonObject object : readable) {
            if ((CLASS_GROUP_DIRECTORY.equals(object.getJevisClass()) || CLASS_USER_DIRECTORY.equals(object.getJevisClass()))
                    && !deleted(object) && canWrite.test(object)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- users

    static Map<String, Object> user(Env env, JsonObject user) throws Exception {
        return user(env, user, memberships(env).getOrDefault(user.getId(), Collections.emptyMap()));
    }

    static Map<String, Object> user(Env env, JsonObject user, Map<Long, Integer> groups) throws Exception {
        Map<String, String> attributes = env.userAttributes(user.getId());
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("account", user.getName());
        json.put("firstName", attributes.get(ATTRIBUTE_FIRST_NAME));
        json.put("lastName", attributes.get(ATTRIBUTE_LAST_NAME));
        json.put("email", attributes.get(ATTRIBUTE_EMAIL));
        boolean enabled = bool(attributes.get(ATTRIBUTE_ENABLED));
        boolean sysAdmin = bool(attributes.get(ATTRIBUTE_SYS_ADMIN));
        json.put("enabled", enabled);
        json.put("sysAdmin", sysAdmin);
        json.put("mfa", env.hasMfa(user.getId()));
        json.put("self", user.getId() == env.userId());
        Long parentId = env.parent(user.getId());
        JsonObject parent = parentId != null ? env.object(parentId) : null;
        if (parent != null && env.canRead(parent)) {
            Map<String, Object> directory = ref(parent);
            directory.put("site", ref(site(env, parent.getId())));
            json.put("directory", directory);
        } else {
            json.put("directory", null);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : groups.entrySet()) {
            JsonObject group = readable(env, entry.getKey(), CLASS_GROUP);
            if (group == null) continue;
            Map<String, Object> g = ref(group);
            g.put("rights", rightNames(entry.getValue()));
            g.put("canWrite", env.canWrite(group));
            list.add(g);
        }
        list.sort(Comparator.comparing(g -> String.valueOf(g.get("name")).toLowerCase(Locale.ROOT)));
        json.put("groups", list);
        json.put("canWrite", env.canWrite(user));
        json.put("version", userVersion(user, attributes, groups));
        return json;
    }

    static String userVersion(JsonObject user, Map<String, String> attributes, Map<Long, Integer> groups) {
        return version(user.getName(), attributes.get(ATTRIBUTE_FIRST_NAME), attributes.get(ATTRIBUTE_LAST_NAME),
                attributes.get(ATTRIBUTE_EMAIL), String.valueOf(bool(attributes.get(ATTRIBUTE_ENABLED))),
                String.valueOf(bool(attributes.get(ATTRIBUTE_SYS_ADMIN))), membershipKey(groups));
    }

    static String userVersion(Env env, JsonObject user) throws Exception {
        return userVersion(user, env.userAttributes(user.getId()), groupsOf(env, user.getId()));
    }

    /** {@code GET /web/admin/users?q=&site=} */
    static List<Map<String, Object>> users(Env env, String q, Long site) throws Exception {
        Map<Long, Map<Long, Integer>> memberships = memberships(env);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonObject object : env.objects()) {
            if (!CLASS_USER.equals(object.getJevisClass()) || deleted(object) || !env.canRead(object)) continue;
            Map<Long, Integer> groups = memberships.getOrDefault(object.getId(), Collections.emptyMap());
            if (site != null && !inSite(env, object, groups, site)) continue;
            Map<String, Object> json = user(env, object, groups);
            if (!query.isEmpty() && !matches(query, json.get("account"), json.get("firstName"), json.get("lastName"), json.get("email"))) continue;
            result.add(json);
        }
        result.sort(Comparator.comparing(u -> String.valueOf(u.get("account")).toLowerCase(Locale.ROOT)));
        return result;
    }

    private static boolean inSite(Env env, JsonObject user, Map<Long, Integer> groups, long site) {
        if (below(env, user.getId(), site)) return true;
        for (Long group : groups.keySet()) {
            if (below(env, group, site)) return true;
        }
        return false;
    }

    private static boolean matches(String query, Object... values) {
        for (Object value : values) {
            if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    static boolean accountExists(Env env, String account) {
        for (JsonObject object : env.objects()) {
            if (CLASS_USER.equals(object.getJevisClass()) && !deleted(object) && account.equalsIgnoreCase(String.valueOf(object.getName()).trim())) {
                return true;
            }
        }
        return false;
    }

    private static void checkPassword(String password) throws WebEdit.Stop {
        if (!PasswordResetPolicy.isValidPassword(password)) {
            throw stop(400, CODE_PASSWORD, "The password does not meet the password policy (at least "
                    + PasswordResetPolicy.MIN_PASSWORD_LENGTH + " characters)");
        }
    }

    /** {@code POST /web/admin/users} {@code {directory, account, firstName, lastName, email, enabled, password}} */
    static WebEdit.Reply createUser(Env env, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        Long directoryId = id(json.get("directory"));
        if (directoryId == null) throw WebEdit.Stop.badRequest("directory missing");
        JsonObject directory = readable(env, directoryId, CLASS_USER_DIRECTORY);
        if (directory == null) throw WebEdit.Stop.notFound("User directory not found");
        if (!env.canCreate(directory, CLASS_USER)) throw stop(403, CODE_RIGHTS, "No right to create users in this directory");
        String account = WebEdit.text(json, "account");
        if (account == null || account.trim().isEmpty()) throw WebEdit.Stop.badRequest("account missing");
        account = account.trim();
        if (account.length() > 128) throw WebEdit.Stop.badRequest("account too long");
        if (accountExists(env, account)) throw stop(409, CODE_ACCOUNT_EXISTS, "The account exists already");
        String password = WebEdit.text(json, "password");
        checkPassword(password);
        boolean enabled = !json.hasNonNull("enabled") || WebEdit.bool(json, "enabled");
        String firstName = trimmed(WebEdit.text(json, "firstName"));
        String lastName = trimmed(WebEdit.text(json, "lastName"));
        String email = trimmed(WebEdit.text(json, "email"));

        JsonObject user = env.create(directory.getId(), CLASS_USER, account, Collections.emptyMap());
        try {
            env.setAttribute(user, ATTRIBUTE_PASSWORD, JEVisConstants.PrimitiveType.PASSWORD_PBKDF2, password);
        } catch (Exception e) {
            env.deleteObject(user);
            throw e;
        }
        env.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(user.getId(), account, "admin-user-create"));
        setLogged(env, user, ATTRIBUTE_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN, enabled ? "true" : "false", "admin-user-create");
        if (firstName != null) setLogged(env, user, ATTRIBUTE_FIRST_NAME, JEVisConstants.PrimitiveType.STRING, firstName, "admin-user-create");
        if (lastName != null) setLogged(env, user, ATTRIBUTE_LAST_NAME, JEVisConstants.PrimitiveType.STRING, lastName, "admin-user-create");
        if (email != null) setLogged(env, user, ATTRIBUTE_EMAIL, JEVisConstants.PrimitiveType.STRING, email, "admin-user-create");
        return WebEdit.Reply.created(user(env, user));
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static void setLogged(Env env, JsonObject object, String attribute, int primitiveType, String value, String action) throws Exception {
        env.setAttribute(object, attribute, primitiveType, value);
        env.log(SQLDataSource.LOG_EVENT.CREATE_SAMPLE, WebEdit.sampleLog(object.getId(), attribute, 1, action));
    }

    /** the user with write right, else 404/403 */
    private static JsonObject writableUser(Env env, long id) throws WebEdit.Stop {
        JsonObject user = readable(env, id, CLASS_USER);
        if (user == null) throw WebEdit.Stop.notFound("User not found");
        if (!env.canWrite(user)) throw stop(403, CODE_RIGHTS, "No write right on this user");
        return user;
    }

    /** {@code PUT /web/admin/users/{id}} {@code {version, firstName, lastName, email, enabled, sysAdmin}} */
    static WebEdit.Reply updateUser(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        JsonObject user = writableUser(env, id);
        String version = version(json);
        Map<String, String> attributes = env.userAttributes(id);
        if (!version.equals(userVersion(user, attributes, groupsOf(env, id)))) return WebEdit.Reply.conflict(user(env, user));

        boolean self = id == env.userId();
        boolean enabled = bool(attributes.get(ATTRIBUTE_ENABLED));
        boolean sysAdmin = bool(attributes.get(ATTRIBUTE_SYS_ADMIN));
        Boolean newEnabled = json.hasNonNull("enabled") ? WebEdit.bool(json, "enabled") : null;
        Boolean newSysAdmin = json.hasNonNull("sysAdmin") ? WebEdit.bool(json, "sysAdmin") : null;
        boolean enabledChanged = newEnabled != null && newEnabled != enabled;
        boolean sysAdminChanged = newSysAdmin != null && newSysAdmin != sysAdmin;
        if (self && (enabledChanged || sysAdminChanged)) {
            throw stop(400, CODE_SELF, "You cannot disable yourself or change your own Sys Admin flag");
        }
        if (sysAdminChanged && !env.sysAdmin()) throw stop(403, CODE_SYS_ADMIN, "Only Sys Admins can change the Sys Admin flag");

        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(ATTRIBUTE_FIRST_NAME, "firstName");
        texts.put(ATTRIBUTE_LAST_NAME, "lastName");
        texts.put(ATTRIBUTE_EMAIL, "email");
        Map<String, String> changes = new LinkedHashMap<>();
        for (Map.Entry<String, String> field : texts.entrySet()) {
            if (!json.has(field.getValue())) continue;
            String value = trimmed(WebEdit.text(json, field.getValue()));
            String current = attributes.get(field.getKey());
            if (!Objects.equals(value == null ? "" : value, current == null ? "" : current)) changes.put(field.getKey(), value == null ? "" : value);
        }
        for (Map.Entry<String, String> change : changes.entrySet()) {
            setLogged(env, user, change.getKey(), JEVisConstants.PrimitiveType.STRING, change.getValue(), "admin-user");
        }
        if (enabledChanged) setLogged(env, user, ATTRIBUTE_ENABLED, JEVisConstants.PrimitiveType.BOOLEAN, String.valueOf(newEnabled), "admin-user");
        if (sysAdminChanged) setLogged(env, user, ATTRIBUTE_SYS_ADMIN, JEVisConstants.PrimitiveType.BOOLEAN, String.valueOf(newSysAdmin), "admin-user");
        return WebEdit.Reply.ok(user(env, user));
    }

    /** {@code POST /web/admin/users/{id}/password} {@code {password}}: all sessions of the user end */
    static WebEdit.Reply setPassword(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        JsonObject user = writableUser(env, id);
        if (id == env.userId()) throw stop(400, CODE_SELF, "Change your own password in your account settings");
        String password = WebEdit.text(json, "password");
        checkPassword(password);
        setLogged(env, user, ATTRIBUTE_PASSWORD, JEVisConstants.PrimitiveType.PASSWORD_PBKDF2, password, "admin-password");
        return WebEdit.Reply.noContent();
    }

    /** {@code POST /web/admin/users/{id}/mfa-reset}: Sys Admins with an interactive login only */
    static WebEdit.Reply resetMfa(Env env, long id) throws Exception {
        if (!env.interactive() || !env.sysAdmin()) throw stop(403, CODE_SYS_ADMIN, "Only Sys Admins can reset the two-factor authentication");
        JsonObject user = readable(env, id, CLASS_USER);
        if (user == null) throw WebEdit.Stop.notFound("User not found");
        env.resetMfa(id);
        env.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, user.getName(), "admin-mfa-reset"));
        return WebEdit.Reply.noContent();
    }

    // ---------------------------------------------------------------- memberships

    /**
     * One membership change: what is wanted, and the user and group of it.
     */
    static final class Change {
        final JsonObject user;
        final JsonObject group;
        final int current;
        final int wanted;

        Change(JsonObject user, JsonObject group, int current, int wanted) {
            this.user = user;
            this.group = group;
            this.current = current;
            this.wanted = wanted;
        }
    }

    /**
     * Applies the membership changes: the ones the caller may not make (no write right on user or group) stay
     * untouched and are returned; a right the caller does not hold himself makes the whole request fail with
     * {@code 403 GRANT} before anything is changed.
     */
    static List<Change> applyMemberships(Env env, List<Change> changes) throws Exception {
        List<Change> allowed = new ArrayList<>();
        List<Change> unchanged = new ArrayList<>();
        for (Change change : changes) {
            if (change.current == change.wanted) continue;
            if (env.canWrite(change.user) && env.canWrite(change.group)) allowed.add(change);
            else unchanged.add(change);
        }
        Map<Long, Set<Integer>> held = held(env);
        for (Change change : allowed) {
            List<String> refused = new ArrayList<>();
            for (int type : Rights.memberTypes(change.wanted & ~change.current)) {
                if (!RelationshipGrant.mayGrant(env.sysAdmin(), type, change.user.getId(), change.group.getId(), held, Collections.emptyMap())) {
                    refused.add(RIGHT_NAMES.get(type - JEVisConstants.ObjectRelationship.MEMBER_READ));
                }
            }
            if (!refused.isEmpty()) {
                WebEdit.Reply reply = error(403, CODE_GRANT, "Only rights you hold yourself can be granted");
                @SuppressWarnings("unchecked") Map<String, Object> details = (Map<String, Object>) reply.body;
                details.put("group", ref(change.group));
                details.put("user", ref(change.user));
                details.put("rights", refused);
                throw new WebEdit.Stop(reply);
            }
        }
        for (Change change : allowed) {
            for (int type : Rights.memberTypes(change.current & ~change.wanted)) {
                if (env.removeRelationship(change.user.getId(), change.group.getId(), type)) {
                    env.log(SQLDataSource.LOG_EVENT.DELETE_RELATIONSHIP, relationshipLog(change.user.getId(), change.group.getId(), type, "admin-membership"));
                }
            }
            for (int type : Rights.memberTypes(change.wanted & ~change.current)) {
                env.addRelationship(change.user.getId(), change.group.getId(), type);
                env.log(SQLDataSource.LOG_EVENT.CREATE_RELATIONSHIP, relationshipLog(change.user.getId(), change.group.getId(), type, "admin-membership"));
            }
        }
        return unchanged;
    }

    /** {@code <from>-<to>-<type>|web:<action>}, the format of {@code ResourceRelationship.delete} */
    static String relationshipLog(long from, long to, int type, String action) {
        return String.format("%s-%s-%s|%s%s", from, to, type, WebEdit.ACTION_PREFIX, action);
    }

    /** {@code PUT /web/admin/users/{id}/groups} {@code {version, groups: [{id, rights}]}} */
    static WebEdit.Reply setUserGroups(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        JsonObject user = writableUser(env, id);
        String version = version(json);
        if (!version.equals(userVersion(env, user))) return WebEdit.Reply.conflict(user(env, user));
        JsonNode list = json.get("groups");
        if (list == null || !list.isArray()) throw WebEdit.Stop.badRequest("groups missing");
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (JsonNode entry : list) {
            Long groupId = id(entry);
            if (groupId == null) throw WebEdit.Stop.badRequest("Invalid group");
            if (readable(env, groupId, CLASS_GROUP) == null) throw WebEdit.Stop.notFound("Group not found: " + groupId);
            wanted.merge(groupId, rightMask(entry.get("rights")), (a, b) -> a | b);
        }
        Map<Long, Integer> current = groupsOf(env, id);
        Set<Long> ids = new LinkedHashSet<>(current.keySet());
        ids.addAll(wanted.keySet());
        List<Change> changes = new ArrayList<>();
        for (Long groupId : ids) {
            /* groups the caller cannot read are not shown to him and stay as they are */
            JsonObject group = readable(env, groupId, CLASS_GROUP);
            if (group == null) continue;
            changes.add(new Change(user, group, current.getOrDefault(groupId, 0), wanted.getOrDefault(groupId, 0)));
        }
        List<Change> unchanged = applyMemberships(env, changes);
        /* the user as in GET users plus the groups that stayed unchanged */
        Map<String, Object> answer = user(env, user);
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Change change : unchanged) refs.add(ref(change.group));
        answer.put("unchanged", refs);
        return WebEdit.Reply.ok(answer);
    }

    // ---------------------------------------------------------------- groups

    static String groupVersion(Env env, JsonObject group) {
        return version(group.getName(), membershipKey(membersOf(env, group.getId())));
    }

    static int memberCount(Env env, long groupId) {
        int count = 0;
        for (Long userId : membersOf(env, groupId).keySet()) {
            JsonObject user = env.object(userId);
            if (!deleted(user) && CLASS_USER.equals(user.getJevisClass())) count++;
        }
        return count;
    }

    static String path(Env env, long id) {
        LinkedList<String> parts = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        Long current = env.parent(id);
        while (current != null && seen.add(current)) {
            JsonObject object = env.object(current);
            if (object == null || !env.canRead(object)) break;
            parts.addFirst(object.getName());
            current = env.parent(current);
        }
        return String.join(" / ", parts);
    }

    static Map<String, Object> group(Env env, JsonObject group) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", group.getId());
        json.put("name", group.getName());
        json.put("site", ref(site(env, group.getId())));
        json.put("path", path(env, group.getId()));
        json.put("members", memberCount(env, group.getId()));
        json.put("canWrite", env.canWrite(group));
        json.put("canDelete", env.canDelete(group));
        json.put("version", groupVersion(env, group));
        return json;
    }

    /** {@code GET /web/admin/groups?q=&site=} */
    static List<Map<String, Object>> groups(Env env, String q, Long site) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonObject object : env.objects()) {
            if (!CLASS_GROUP.equals(object.getJevisClass()) || deleted(object) || !env.canRead(object)) continue;
            if (site != null && !below(env, object.getId(), site)) continue;
            if (!query.isEmpty() && !matches(query, object.getName())) continue;
            result.add(group(env, object));
        }
        result.sort(Comparator.comparing(g -> String.valueOf(g.get("name")).toLowerCase(Locale.ROOT)));
        return result;
    }

    /** {@code POST /web/admin/groups} {@code {directory, name}} */
    static WebEdit.Reply createGroup(Env env, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        Long directoryId = id(json.get("directory"));
        if (directoryId == null) throw WebEdit.Stop.badRequest("directory missing");
        JsonObject directory = readable(env, directoryId, CLASS_GROUP_DIRECTORY);
        if (directory == null) throw WebEdit.Stop.notFound("Group directory not found");
        if (!env.canCreate(directory, CLASS_GROUP)) throw stop(403, CODE_RIGHTS, "No right to create groups in this directory");
        String name = trimmed(WebEdit.text(json, "name"));
        if (name == null || name.isEmpty()) throw WebEdit.Stop.badRequest("name missing");
        JsonObject group = env.create(directory.getId(), CLASS_GROUP, name, Collections.emptyMap());
        env.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(group.getId(), name, "admin-group-create"));
        if (!env.sysAdmin()) {
            /* the creator holds all rights in his new group, so that he can give them to others (E10.0 grant rule) */
            for (int type = JEVisConstants.ObjectRelationship.MEMBER_READ; type <= JEVisConstants.ObjectRelationship.MEMBER_DELETE; type++) {
                env.addRelationship(env.userId(), group.getId(), type);
            }
            env.log(SQLDataSource.LOG_EVENT.CREATE_RELATIONSHIP, WebEdit.objectLog(group.getId(), name, "admin-group-creator-member"));
        }
        return WebEdit.Reply.created(group(env, group));
    }

    private static JsonObject readableGroup(Env env, long id) throws WebEdit.Stop {
        JsonObject group = readable(env, id, CLASS_GROUP);
        if (group == null) throw WebEdit.Stop.notFound("Group not found");
        return group;
    }

    /** {@code PUT /web/admin/groups/{id}} {@code {version, name}} */
    static WebEdit.Reply renameGroup(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        JsonObject group = readableGroup(env, id);
        if (!env.canWrite(group)) throw stop(403, CODE_RIGHTS, "No write right on this group");
        if (!version(json).equals(groupVersion(env, group))) return WebEdit.Reply.conflict(group(env, group));
        String name = trimmed(WebEdit.text(json, "name"));
        if (name == null || name.isEmpty()) throw WebEdit.Stop.badRequest("name missing");
        if (!name.equals(group.getName())) {
            env.rename(group, name);
            group.setName(name);
            env.log(SQLDataSource.LOG_EVENT.UPDATE_OBJECT, WebEdit.objectLog(id, name, "admin-group-rename"));
        }
        return WebEdit.Reply.ok(group(env, group));
    }

    /** {@code DELETE /web/admin/groups/{id}[?version=]}: into the recycle bin, only without members */
    static WebEdit.Reply deleteGroup(Env env, long id, String version) throws Exception {
        requireInteractive(env);
        JsonObject group = readableGroup(env, id);
        if (!env.canDelete(group)) throw stop(403, CODE_RIGHTS, "No delete right on this group");
        /* the version is optional here: deleting is refused anyway while the group has members */
        if (version != null && !version.trim().isEmpty() && !version.trim().equals(groupVersion(env, group))) {
            return WebEdit.Reply.conflict(group(env, group));
        }
        int members = memberCount(env, id);
        if (members > 0) {
            WebEdit.Reply reply = error(409, CODE_HAS_MEMBERS, "The group still has members");
            @SuppressWarnings("unchecked") Map<String, Object> details = (Map<String, Object>) reply.body;
            details.put("members", members);
            return reply;
        }
        env.recycle(group);
        env.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT, WebEdit.objectLog(id, group.getName(), "admin-group-delete"));
        return WebEdit.Reply.noContent();
    }

    /** the display name of a user: first and last name, else the account */
    static String displayName(Env env, JsonObject user) throws Exception {
        Map<String, String> attributes = env.userAttributes(user.getId());
        String first = attributes.get(ATTRIBUTE_FIRST_NAME);
        String last = attributes.get(ATTRIBUTE_LAST_NAME);
        String name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return name.isEmpty() ? user.getName() : name;
    }

    static Map<String, Object> userRef(Env env, JsonObject user) throws Exception {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", user.getId());
        json.put("account", user.getName());
        json.put("name", displayName(env, user));
        return json;
    }

    /**
     * {@code GET /web/admin/groups/{id}/members}: the members the caller may read (all members if he may write the
     * group)
     */
    static List<Map<String, Object>> members(Env env, long id) throws Exception {
        JsonObject group = readableGroup(env, id);
        boolean all = env.canWrite(group);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : membersOf(env, id).entrySet()) {
            JsonObject user = env.object(entry.getKey());
            if (deleted(user) || !CLASS_USER.equals(user.getJevisClass()) || (!all && !env.canRead(user))) continue;
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("user", userRef(env, user));
            json.put("rights", rightNames(entry.getValue()));
            json.put("canWrite", env.canWrite(user));
            result.add(json);
        }
        result.sort(Comparator.comparing(m -> String.valueOf(((Map<?, ?>) m.get("user")).get("account")).toLowerCase(Locale.ROOT)));
        return result;
    }

    /** {@code PUT /web/admin/groups/{id}/members} {@code {version, members: [{user, rights}]}} */
    static WebEdit.Reply setMembers(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        JsonNode json = WebEdit.body(body);
        JsonObject group = readableGroup(env, id);
        if (!env.canWrite(group)) throw stop(403, CODE_RIGHTS, "No write right on this group");
        if (!version(json).equals(groupVersion(env, group))) return WebEdit.Reply.conflict(group(env, group));
        JsonNode list = json.get("members");
        if (list == null || !list.isArray()) throw WebEdit.Stop.badRequest("members missing");
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (JsonNode entry : list) {
            Long userId = id(entry.get("user"));
            if (userId == null) throw WebEdit.Stop.badRequest("Invalid user");
            JsonObject user = env.object(userId);
            if (deleted(user) || !CLASS_USER.equals(user.getJevisClass())) throw WebEdit.Stop.notFound("User not found: " + userId);
            wanted.merge(userId, rightMask(entry.get("rights")), (a, b) -> a | b);
        }
        Map<Long, Integer> current = membersOf(env, id);
        Set<Long> ids = new LinkedHashSet<>(current.keySet());
        ids.addAll(wanted.keySet());
        List<Change> changes = new ArrayList<>();
        for (Long userId : ids) {
            JsonObject user = env.object(userId);
            if (deleted(user) || !CLASS_USER.equals(user.getJevisClass())) continue;
            changes.add(new Change(user, group, current.getOrDefault(userId, 0), wanted.getOrDefault(userId, 0)));
        }
        List<Change> unchanged = applyMemberships(env, changes);
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("group", group(env, group));
        answer.put("members", members(env, id));
        List<Map<String, Object>> refs = new ArrayList<>();
        for (Change change : unchanged) refs.add(userRef(env, change.user));
        answer.put("unchanged", refs);
        return WebEdit.Reply.ok(answer);
    }

    // ---------------------------------------------------------------- site sharing

    /** the snapshot for {@link SiteSharing}: the readable objects and the relationships between them */
    static RightsModel model(Env env) {
        RightsModel model = new RightsModel();
        Set<Long> ids = new HashSet<>();
        for (JsonObject object : env.objects()) {
            if (object == null || !env.canRead(object)) continue;
            RightsModel.Node node = model.addObject(object.getId(), object.getName(), object.getJevisClass());
            for (JsonI18n i18n : object.getI18n()) {
                if (i18n.getKey() != null && i18n.getValue() != null) node.localName(i18n.getKey(), i18n.getValue());
            }
            node.setDeleted(deleted(object));
            ids.add(object.getId());
        }
        for (JsonRelationship rel : env.relationships()) {
            if (ids.contains(rel.getFrom()) && ids.contains(rel.getTo())) model.addRelationship(rel.getFrom(), rel.getTo(), rel.getType());
        }
        return model;
    }

    private static JsonObject readableSite(Env env, long id) throws WebEdit.Stop {
        JsonObject site = env.object(id);
        if (deleted(site) || !env.canRead(site) || !SITE_CLASSES.contains(site.getJevisClass())) {
            throw WebEdit.Stop.notFound("Site not found");
        }
        return site;
    }

    /** {@code GET /web/admin/sites/{id}/sharing} */
    static Map<String, Object> sharing(Env env, long id) throws Exception {
        JsonObject site = readableSite(env, id);
        SiteSharing sharing = new SiteSharing(model(env), id, null);
        Map<String, Object> groups = new LinkedHashMap<>();
        for (SiteSharing.Preset preset : SiteSharing.Preset.GROUP_PRESETS) {
            RightsModel.Node group = sharing.getGroup(preset);
            if (group == null) {
                groups.put(preset.name(), null);
            } else {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("id", group.id);
                ref.put("name", group.name);
                groups.put(preset.name(), ref);
            }
        }
        List<Map<String, Object>> users = new ArrayList<>();
        for (Map.Entry<Long, SiteSharing.Preset> entry : sharing.currentPresets().entrySet()) {
            JsonObject user = env.object(entry.getKey());
            if (user == null) continue;
            Map<String, Object> json = userRef(env, user);
            json.put("preset", entry.getValue().name());
            users.add(json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("site", ref(site));
        result.put("groups", groups);
        result.put("users", users);
        result.put("canManage", env.canWrite(site));
        return result;
    }

    /** the plan of a preview or apply body {@code {assignments: {userId: preset|null}, includeSubtree}} */
    static RightsPlan plan(Env env, long id, String body) throws Exception {
        readableSite(env, id);
        JsonNode json = WebEdit.body(body);
        JsonNode assignments = json.get("assignments");
        if (assignments == null || !assignments.isObject()) throw WebEdit.Stop.badRequest("assignments missing");
        Map<Long, SiteSharing.Preset> map = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = assignments.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            long userId;
            try {
                userId = Long.parseLong(field.getKey().trim());
            } catch (NumberFormatException e) {
                throw WebEdit.Stop.badRequest("Invalid user " + field.getKey());
            }
            SiteSharing.Preset preset;
            if (field.getValue() == null || field.getValue().isNull()) {
                preset = SiteSharing.Preset.NONE;
            } else {
                try {
                    preset = SiteSharing.Preset.valueOf(field.getValue().asText().trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    throw WebEdit.Stop.badRequest("Invalid preset " + field.getValue());
                }
            }
            map.put(userId, preset);
        }
        return new SiteSharing(model(env), id, null).plan(map, WebEdit.optionalBool(json, "includeSubtree"));
    }

    /** {@code POST /web/admin/sites/{id}/sharing/preview}: the planned changes, each with whether the caller may make it */
    static Map<String, Object> preview(Env env, long id, String body) throws Exception {
        RightsPlan plan = plan(env, id, body);
        Executor executor = new Executor(env);
        List<Map<String, Object>> actions = new ArrayList<>();
        for (RightsPlan.Action action : plan.getActions()) {
            Map<String, Object> json = actionJson(action);
            String refusal = executor.check(action);
            json.put("allowed", refusal == null);
            if (refusal != null) json.put("reason", refusal);
            actions.add(json);
        }
        return planAnswer(plan, actions);
    }

    /** {@code POST /web/admin/sites/{id}/sharing/apply}: runs the plan with the caller's rights */
    static WebEdit.Reply apply(Env env, long id, String body) throws Exception {
        requireInteractive(env);
        RightsPlan plan = plan(env, id, body);
        Executor executor = new Executor(env);
        List<Map<String, Object>> actions = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        int done = 0;
        for (RightsPlan.Action action : plan.getActions()) {
            String error = executor.execute(action);
            Map<String, Object> json = actionJson(action);
            if (action.getType() == RightsPlan.ActionType.CREATE_OBJECT) {
                Map<String, Object> object = refJson(action.getObject());
                object.put("id", executor.id(action.getObject()));
                json.put("object", object);
            }
            json.put("done", error == null);
            if (error != null) {
                json.put("error", error);
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("action", json);
                failure.put("text", json.get("text"));
                failure.put("error", error);
                failed.add(failure);
            } else {
                done++;
            }
            actions.add(json);
        }
        Map<String, Object> answer = planAnswer(plan, actions);
        answer.put("done", done);
        answer.put("failed", failed);
        return WebEdit.Reply.ok(answer);
    }

    private static Map<String, Object> planAnswer(RightsPlan plan, List<Map<String, Object>> actions) {
        Map<String, Object> answer = new LinkedHashMap<>();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("createObjects", plan.count(RightsPlan.ActionType.CREATE_OBJECT, null));
        counts.put("addRelationships", plan.count(RightsPlan.ActionType.ADD_RELATIONSHIP, null));
        counts.put("removeRelationships", plan.count(RightsPlan.ActionType.REMOVE_RELATIONSHIP, null));
        answer.put("counts", counts);
        answer.put("actions", actions);
        answer.put("warnings", new ArrayList<>(plan.getWarnings()));
        return answer;
    }

    static Map<String, Object> actionJson(RightsPlan.Action action) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("type", action.getType().name());
        json.put("category", action.getCategory() != null ? action.getCategory().name() : null);
        json.put("text", action.toString());
        if (action.getType() == RightsPlan.ActionType.CREATE_OBJECT) {
            json.put("object", refJson(action.getObject()));
            json.put("name", action.getObject().getName());
            json.put("className", action.getObject().getClassName());
            json.put("parent", refJson(action.getFrom()));
        } else {
            json.put("from", refJson(action.getFrom()));
            json.put("to", refJson(action.getTo()));
            json.put("relType", action.getRelType());
            if (Rights.isMemberType(action.getRelType())) {
                json.put("right", RIGHT_NAMES.get(action.getRelType() - JEVisConstants.ObjectRelationship.MEMBER_READ));
            }
        }
        return json;
    }

    static Map<String, Object> refJson(RightsPlan.Ref ref) {
        if (ref == null) return null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", ref.getId());
        json.put("name", ref.getName());
        json.put("className", ref.getClassName());
        json.put("planned", ref.isPlanned());
        return json;
    }

    /**
     * Runs the actions of a {@link RightsPlan} on the server with the rights of the caller; every action is checked
     * like a single request ({@code POST /objects}, {@code POST} and {@code DELETE /relationships} with the E10.0 grant
     * rule) and logged. An object created by the plan has the owners of its parent, so its rights are those of the
     * nearest existing ancestor (the rights cache of the request does not know it yet).
     */
    static final class Executor {
        private final Env env;
        private final Map<RightsPlan.Ref, Long> created = new IdentityHashMap<>();
        /** created object id → existing object whose rights it has */
        private final Map<Long, Long> rightsOf = new HashMap<>();
        private final Map<Long, Set<Integer>> held;

        Executor(Env env) {
            this.env = env;
            this.held = held(env);
        }

        Long id(RightsPlan.Ref ref) {
            if (ref == null) return null;
            return ref.getId() != null ? ref.getId() : created.get(ref);
        }

        private JsonObject rightsObject(long id) {
            return env.object(rightsOf.getOrDefault(id, id));
        }

        private boolean canWrite(long id) {
            JsonObject object = rightsObject(id);
            return object != null && !deleted(object) && env.canWrite(object);
        }

        /**
         * @return why the caller may not make the change, null if he may (objects still to be created are judged
         * by their planned parent)
         */
        String check(RightsPlan.Action action) {
            switch (action.getType()) {
                case CREATE_OBJECT: {
                    RightsPlan.Ref parent = action.getFrom();
                    while (parent != null && id(parent) == null) parent = parent.getParent();
                    Long parentId = id(parent);
                    JsonObject object = parentId != null ? rightsObject(parentId) : null;
                    if (object == null || deleted(object)) return "parent not found";
                    return env.canCreate(object, action.getObject().getClassName()) ? null : "no create right";
                }
                case ADD_RELATIONSHIP:
                case REMOVE_RELATIONSHIP: {
                    Long from = known(action.getFrom());
                    Long to = known(action.getTo());
                    if (from == null || to == null) return "object not found";
                    if (!canWrite(from) || !canWrite(to)) return "no write right";
                    if (action.getType() == RightsPlan.ActionType.ADD_RELATIONSHIP
                            && !RelationshipGrant.mayGrant(env.sysAdmin(), action.getRelType(), from, to, held, Collections.emptyMap())) {
                        return CODE_GRANT;
                    }
                    return null;
                }
                default:
                    return "unknown action";
            }
        }

        /** the id of the object, for planned objects the id of the nearest existing ancestor (preview) */
        private Long known(RightsPlan.Ref ref) {
            RightsPlan.Ref current = ref;
            while (current != null && id(current) == null) current = current.getParent();
            return id(current);
        }

        /**
         * @return the error, null if done
         */
        String execute(RightsPlan.Action action) {
            try {
                switch (action.getType()) {
                    case CREATE_OBJECT: {
                        Long parentId = id(action.getFrom());
                        if (parentId == null) return "parent was not created";
                        String refusal = check(action);
                        if (refusal != null) return refusal;
                        RightsPlan.Ref ref = action.getObject();
                        JsonObject object = env.create(parentId, ref.getClassName(), ref.getName(), ref.getLocalNames());
                        created.put(ref, object.getId());
                        rightsOf.put(object.getId(), rightsOf.getOrDefault(parentId, parentId));
                        env.log(SQLDataSource.LOG_EVENT.CREATE_OBJECT, WebEdit.objectLog(object.getId(), object.getName(), "admin-sharing"));
                        return null;
                    }
                    case ADD_RELATIONSHIP:
                    case REMOVE_RELATIONSHIP: {
                        Long from = id(action.getFrom());
                        Long to = id(action.getTo());
                        if (from == null || to == null) return "object was not created";
                        String refusal = check(action);
                        if (refusal != null) return refusal;
                        if (action.getType() == RightsPlan.ActionType.ADD_RELATIONSHIP) {
                            env.addRelationship(from, to, action.getRelType());
                            env.log(SQLDataSource.LOG_EVENT.CREATE_RELATIONSHIP, relationshipLog(from, to, action.getRelType(), "admin-sharing"));
                        } else {
                            if (!env.removeRelationship(from, to, action.getRelType())) return "relationship was not removed";
                            env.log(SQLDataSource.LOG_EVENT.DELETE_RELATIONSHIP, relationshipLog(from, to, action.getRelType(), "admin-sharing"));
                        }
                        return null;
                    }
                    default:
                        return "unknown action";
                }
            } catch (Exception e) {
                return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
    }
}
