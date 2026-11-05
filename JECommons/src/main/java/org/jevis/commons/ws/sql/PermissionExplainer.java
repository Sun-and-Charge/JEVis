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

import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.*;

import java.util.*;
import java.util.function.Function;

/**
 * Explains the effective rights of a user on an object (OpenJEVis #1211, "Wer darf was?").
 * <p>
 * The rules are the same as in {@link UserRightManagerForWS}, which decides every request:
 * <ul>
 *     <li>Sys Admins may do everything.</li>
 *     <li>Read: the object is public, it is the user's own user object, or one of its owner groups (OWNER
 *     relationships as returned by {@link SQLDataSource#getGroupOwnerRelationships(long)}) is a group the user has a
 *     read membership in.</li>
 *     <li>Write, create, delete, execute: one of the owner groups (OWNER relationships as returned by
 *     {@link SQLDataSource#getRelationships(long, int)}) is a group the user has the matching membership in.</li>
 *     <li>Users with execute right may create the classes in {@link #CREATE_EXCEPTION_CLASSES} (notes).</li>
 * </ul>
 * Roles are not evaluated by the server: the Control Center copies the group rights of a role into memberships of its
 * members. This class only says whether a membership that grants a right is covered by a role of the user.
 * <p>
 * The class has no database access, the caller passes the relationships.
 */
public final class PermissionExplainer {

    /**
     * Classes users with execute right may create without create right, see
     * {@link UserRightManagerForWS#canCreate(org.jevis.commons.ws.json.JsonObject, String)}.
     */
    public static final List<String> CREATE_EXCEPTION_CLASSES = Collections.unmodifiableList(Arrays.asList("Data Notes", "User Data"));

    /**
     * The membership relationship types in the order read, write, create, delete, execute.
     */
    public static final int[] MEMBER_TYPES = {
            JEVisConstants.ObjectRelationship.MEMBER_READ,
            JEVisConstants.ObjectRelationship.MEMBER_WRITE,
            JEVisConstants.ObjectRelationship.MEMBER_CREATE,
            JEVisConstants.ObjectRelationship.MEMBER_DELETE,
            JEVisConstants.ObjectRelationship.MEMBER_EXECUTE};

    private PermissionExplainer() {
    }

    /**
     * Maps a membership type (101-105) to the role type (201-205) that grants the same right.
     *
     * @return the role type, or -1 for other types
     */
    public static int roleTypeFor(int memberType) {
        switch (memberType) {
            case JEVisConstants.ObjectRelationship.MEMBER_READ:
                return JEVisConstants.ObjectRelationship.ROLE_READ;
            case JEVisConstants.ObjectRelationship.MEMBER_WRITE:
                return JEVisConstants.ObjectRelationship.ROLE_WRITE;
            case JEVisConstants.ObjectRelationship.MEMBER_CREATE:
                return JEVisConstants.ObjectRelationship.ROLE_CREATE;
            case JEVisConstants.ObjectRelationship.MEMBER_DELETE:
                return JEVisConstants.ObjectRelationship.ROLE_DELETE;
            case JEVisConstants.ObjectRelationship.MEMBER_EXECUTE:
                return JEVisConstants.ObjectRelationship.ROLE_EXECUTE;
            default:
                return -1;
        }
    }

    /**
     * The owner groups of one object, collected once and used for all users.
     */
    public static final class ObjectOwners {
        private final long objectId;
        private final boolean publicObject;
        private final Set<Long> readGroups;
        private final Set<Long> ownerGroups;

        private ObjectOwners(long objectId, boolean publicObject, Set<Long> readGroups, Set<Long> ownerGroups) {
            this.objectId = objectId;
            this.publicObject = publicObject;
            this.readGroups = readGroups;
            this.ownerGroups = ownerGroups;
        }

        /**
         * @param readOwnerRels the relationships {@code canRead} checks (the end of every OWNER relationship counts)
         * @param ownerRels     the relationships {@code canWrite/canCreate/canDelete/canExecute} check
         */
        public static ObjectOwners of(long objectId, boolean publicObject,
                                      Collection<JsonRelationship> readOwnerRels, Collection<JsonRelationship> ownerRels) {
            return new ObjectOwners(objectId, publicObject, ownerEnds(readOwnerRels), ownerEnds(ownerRels));
        }

        private static Set<Long> ownerEnds(Collection<JsonRelationship> rels) {
            Set<Long> groups = new LinkedHashSet<>();
            if (rels == null) return groups;
            for (JsonRelationship rel : rels) {
                if (rel != null && rel.getType() == JEVisConstants.ObjectRelationship.OWNER) {
                    groups.add(rel.getTo());
                }
            }
            return groups;
        }

        public long getObjectId() {
            return objectId;
        }

        public boolean isPublicObject() {
            return publicObject;
        }

        /**
         * @return the groups that grant read, in order
         */
        public Set<Long> getReadGroups() {
            return readGroups;
        }

        /**
         * @return the groups that grant write, create, delete and execute, in order
         */
        public Set<Long> getOwnerGroups() {
            return ownerGroups;
        }

        /**
         * @return the owner groups as shown to the user (read and other owner groups together)
         */
        public Set<Long> getAllGroups() {
            Set<Long> all = new LinkedHashSet<>(readGroups);
            all.addAll(ownerGroups);
            return all;
        }
    }

    /**
     * Index of the role relationships: ROLE_MEMBER (role to user) and ROLE_READ..ROLE_DELETE (role to group).
     */
    public static final class RoleIndex {
        private final Map<Long, Set<Long>> rolesByUser = new HashMap<>();
        private final Map<Long, Map<Integer, Set<Long>>> groupsByRole = new HashMap<>();

        /**
         * @param relationships any relationships; other types are ignored
         */
        public static RoleIndex of(Collection<JsonRelationship> relationships) {
            RoleIndex index = new RoleIndex();
            if (relationships == null) return index;
            for (JsonRelationship rel : relationships) {
                if (rel == null) continue;
                switch (rel.getType()) {
                    case JEVisConstants.ObjectRelationship.ROLE_MEMBER:
                        index.rolesByUser.computeIfAbsent(rel.getTo(), k -> new TreeSet<>()).add(rel.getFrom());
                        break;
                    case JEVisConstants.ObjectRelationship.ROLE_READ:
                    case JEVisConstants.ObjectRelationship.ROLE_WRITE:
                    case JEVisConstants.ObjectRelationship.ROLE_CREATE:
                    case JEVisConstants.ObjectRelationship.ROLE_DELETE:
                    case JEVisConstants.ObjectRelationship.ROLE_EXECUTE:
                        index.groupsByRole.computeIfAbsent(rel.getFrom(), k -> new HashMap<>())
                                .computeIfAbsent(rel.getType(), k -> new LinkedHashSet<>()).add(rel.getTo());
                        break;
                    default:
                        break;
                }
            }
            return index;
        }

        /**
         * @return the roles the user is a member of, sorted by id
         */
        public Set<Long> rolesOf(long userId) {
            return rolesByUser.getOrDefault(userId, Collections.emptySet());
        }

        /**
         * @param roleType ROLE_READ..ROLE_DELETE
         * @return the groups the role grants this right on
         */
        public Set<Long> groupsOf(long roleId, int roleType) {
            return groupsByRole.getOrDefault(roleId, Collections.emptyMap()).getOrDefault(roleType, Collections.emptySet());
        }

        /**
         * @return all groups the role grants at least one right on, in order
         */
        public Set<Long> groupsOf(long roleId) {
            Set<Long> groups = new LinkedHashSet<>();
            for (int memberType : MEMBER_TYPES) {
                groups.addAll(groupsOf(roleId, roleTypeFor(memberType)));
            }
            return groups;
        }

        /**
         * @param memberType MEMBER_READ..MEMBER_DELETE
         * @return the roles of the user that grant this right on the group
         */
        public List<Long> rolesGranting(long userId, long groupId, int memberType) {
            int roleType = roleTypeFor(memberType);
            List<Long> roles = new ArrayList<>();
            for (Long roleId : rolesOf(userId)) {
                if (groupsOf(roleId, roleType).contains(groupId)) {
                    roles.add(roleId);
                }
            }
            return roles;
        }
    }

    /**
     * Groups the memberships of a user by type.
     *
     * @return membership type (101-105) to the groups, in order
     */
    public static Map<Integer, Set<Long>> membershipsByType(Collection<JsonRelationship> memberships) {
        Map<Integer, Set<Long>> byType = new HashMap<>();
        for (int type : MEMBER_TYPES) {
            byType.put(type, new LinkedHashSet<>());
        }
        if (memberships == null) return byType;
        for (JsonRelationship rel : memberships) {
            if (rel != null && byType.containsKey(rel.getType())) {
                byType.get(rel.getType()).add(rel.getTo());
            }
        }
        return byType;
    }

    /**
     * Explains the effective rights of one user on one object.
     *
     * @param user        the user (id, account name, Sys Admin and enabled flags are used)
     * @param owners      the owner groups of the object
     * @param memberships the memberships of the user (other relationship types are ignored)
     * @param roles       the role index, or {@code null}
     * @param names       resolves group and role names, may return {@code null}
     */
    public static JsonEffectiveRights explain(JEVisUserSQL user, ObjectOwners owners, Collection<JsonRelationship> memberships,
                                              RoleIndex roles, Function<Long, String> names) {
        return explain(user.getUserID(), user.getAccountName(), user.isSysAdmin(), user.isEnabled(), owners, memberships, roles, names);
    }

    /**
     * Explains the effective rights of one user on one object.
     */
    public static JsonEffectiveRights explain(long userId, String userName, boolean sysAdmin, boolean enabled,
                                              ObjectOwners owners, Collection<JsonRelationship> memberships,
                                              RoleIndex roles, Function<Long, String> names) {
        RoleIndex roleIndex = roles != null ? roles : RoleIndex.of(null);
        Function<Long, String> nameOf = names != null ? names : id -> null;

        JsonEffectiveRights rights = new JsonEffectiveRights();
        rights.setUserId(userId);
        rights.setUserName(userName);
        rights.setSysAdmin(sysAdmin);
        rights.setEnabled(enabled);
        rights.setPublicObject(owners.isPublicObject());
        rights.setOwnObject(userId == owners.getObjectId());

        if (sysAdmin) {
            rights.setRead(true);
            rights.setWrite(true);
            rights.setCreate(true);
            rights.setDelete(true);
            rights.setExecute(true);
            return rights;
        }

        Map<Integer, Set<Long>> byType = membershipsByType(memberships);

        rights.setReadSources(sources(userId, owners.getReadGroups(), byType.get(JEVisConstants.ObjectRelationship.MEMBER_READ),
                JEVisConstants.ObjectRelationship.MEMBER_READ, roleIndex, nameOf));
        rights.setWriteSources(sources(userId, owners.getOwnerGroups(), byType.get(JEVisConstants.ObjectRelationship.MEMBER_WRITE),
                JEVisConstants.ObjectRelationship.MEMBER_WRITE, roleIndex, nameOf));
        rights.setCreateSources(sources(userId, owners.getOwnerGroups(), byType.get(JEVisConstants.ObjectRelationship.MEMBER_CREATE),
                JEVisConstants.ObjectRelationship.MEMBER_CREATE, roleIndex, nameOf));
        rights.setDeleteSources(sources(userId, owners.getOwnerGroups(), byType.get(JEVisConstants.ObjectRelationship.MEMBER_DELETE),
                JEVisConstants.ObjectRelationship.MEMBER_DELETE, roleIndex, nameOf));
        rights.setExecuteSources(sources(userId, owners.getOwnerGroups(), byType.get(JEVisConstants.ObjectRelationship.MEMBER_EXECUTE),
                JEVisConstants.ObjectRelationship.MEMBER_EXECUTE, roleIndex, nameOf));

        rights.setRead(rights.isPublicObject() || rights.isOwnObject() || !rights.getReadSources().isEmpty());
        rights.setWrite(!rights.getWriteSources().isEmpty());
        rights.setCreate(!rights.getCreateSources().isEmpty());
        rights.setDelete(!rights.getDeleteSources().isEmpty());
        rights.setExecute(!rights.getExecuteSources().isEmpty());
        if (rights.isExecute() && !rights.isCreate()) {
            rights.setCreateViaExecuteClasses(new ArrayList<>(CREATE_EXCEPTION_CLASSES));
        }
        return rights;
    }

    private static List<JsonPermissionSource> sources(long userId, Set<Long> ownerGroups, Set<Long> memberGroups, int memberType,
                                                      RoleIndex roles, Function<Long, String> names) {
        List<JsonPermissionSource> sources = new ArrayList<>();
        for (Long groupId : ownerGroups) {
            if (!memberGroups.contains(groupId)) continue;
            JsonPermissionSource source = new JsonPermissionSource();
            source.setGroupId(groupId);
            source.setGroupName(names.apply(groupId));
            for (Long roleId : roles.rolesGranting(userId, groupId, memberType)) {
                source.getRoles().add(new JsonPermissionRef(roleId, names.apply(roleId)));
            }
            sources.add(source);
        }
        return sources;
    }

    /**
     * Lists the group memberships of a user with the rights per group and the roles covering them.
     *
     * @return one entry per group the user has at least one membership in, in order of the memberships
     */
    public static List<JsonGroupRights> groupRights(long userId, Collection<JsonRelationship> memberships, RoleIndex roles,
                                                    Function<Long, String> names) {
        RoleIndex roleIndex = roles != null ? roles : RoleIndex.of(null);
        Function<Long, String> nameOf = names != null ? names : id -> null;
        Map<Long, JsonGroupRights> groups = new LinkedHashMap<>();
        if (memberships != null) {
            for (JsonRelationship rel : memberships) {
                if (rel == null || roleTypeFor(rel.getType()) < 0) continue;
                JsonGroupRights group = groups.computeIfAbsent(rel.getTo(), id -> newGroupRights(id, nameOf));
                setRight(group, rel.getType());
            }
        }
        for (JsonGroupRights group : groups.values()) {
            for (Long roleId : roleIndex.rolesOf(userId)) {
                if (roleIndex.groupsOf(roleId).contains(group.getGroupId())) {
                    group.getRoles().add(new JsonPermissionRef(roleId, nameOf.apply(roleId)));
                }
            }
        }
        return new ArrayList<>(groups.values());
    }

    /**
     * Lists the roles of a user with the group rights each role grants.
     */
    public static List<JsonRoleRights> roleRights(long userId, RoleIndex roles, Function<Long, String> names) {
        RoleIndex roleIndex = roles != null ? roles : RoleIndex.of(null);
        Function<Long, String> nameOf = names != null ? names : id -> null;
        List<JsonRoleRights> result = new ArrayList<>();
        for (Long roleId : roleIndex.rolesOf(userId)) {
            JsonRoleRights role = new JsonRoleRights();
            role.setRoleId(roleId);
            role.setRoleName(nameOf.apply(roleId));
            for (Long groupId : roleIndex.groupsOf(roleId)) {
                JsonGroupRights group = newGroupRights(groupId, nameOf);
                for (int memberType : MEMBER_TYPES) {
                    if (roleIndex.groupsOf(roleId, roleTypeFor(memberType)).contains(groupId)) {
                        setRight(group, memberType);
                    }
                }
                role.getGroups().add(group);
            }
            result.add(role);
        }
        return result;
    }

    private static JsonGroupRights newGroupRights(long groupId, Function<Long, String> names) {
        JsonGroupRights group = new JsonGroupRights();
        group.setGroupId(groupId);
        group.setGroupName(names.apply(groupId));
        return group;
    }

    private static void setRight(JsonGroupRights group, int memberType) {
        switch (memberType) {
            case JEVisConstants.ObjectRelationship.MEMBER_READ:
                group.setRead(true);
                break;
            case JEVisConstants.ObjectRelationship.MEMBER_WRITE:
                group.setWrite(true);
                break;
            case JEVisConstants.ObjectRelationship.MEMBER_CREATE:
                group.setCreate(true);
                break;
            case JEVisConstants.ObjectRelationship.MEMBER_DELETE:
                group.setDelete(true);
                break;
            case JEVisConstants.ObjectRelationship.MEMBER_EXECUTE:
                group.setExecute(true);
                break;
            default:
                break;
        }
    }
}
