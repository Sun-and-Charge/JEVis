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

import org.jevis.api.JEVisConstants;

import java.util.*;

/**
 * Consistency check of the permission setup (OpenJEVis #1211, concept step 3). Pure analysis on a
 * {@link RightsModel}; nothing is changed.
 * <p>
 * Scope: without a scope object, all objects below organisations and buildings are checked (system directories and
 * the class/driver tree are fine without owner). With a scope object, only that subtree is checked. Objects inside an
 * "Administration Directory" (users, groups, roles) are not reported as "without owner": they are meant to be visible
 * to Sys Admins only.
 */
public class PermissionConsistencyCheck {

    public static final List<String> OWNER_SCOPE_CLASSES = Arrays.asList(RightsModel.CLASS_ORGANIZATION, RightsModel.CLASS_BUILDING);

    private final RightsModel model;

    public PermissionConsistencyCheck(RightsModel model) {
        this.model = model;
    }

    /**
     * @param scopeId root of the checked subtree, or null for everything below organisations and buildings
     */
    public List<Finding> run(Long scopeId) {
        Set<Long> scope = buildScope(scopeId);
        List<Finding> findings = new ArrayList<>();
        findings.addAll(findObjectsWithoutOwner(scope));
        findings.addAll(findUsersWithoutMembership(scopeId));
        findings.addAll(findGroupProblems(scopeId));
        findings.addAll(findRoleDrift(scopeId));
        return findings;
    }

    /**
     * @return ids of the objects in which "without owner" is checked
     */
    Set<Long> buildScope(Long scopeId) {
        Set<Long> scope = new LinkedHashSet<>();
        List<Long> roots = new ArrayList<>();
        if (scopeId != null) {
            roots.add(scopeId);
        } else {
            for (RightsModel.Node node : model.getObjects()) {
                if (!node.isDeleted() && OWNER_SCOPE_CLASSES.contains(node.className)) roots.add(node.id);
            }
        }
        for (Long root : roots) {
            RightsModel.Node rootNode = model.get(root);
            if (rootNode == null || rootNode.isDeleted()) continue;
            if (!isInsideAdministration(rootNode.id)) scope.add(rootNode.id);
            collect(rootNode.id, scope);
        }
        return scope;
    }

    private void collect(long id, Set<Long> scope) {
        for (RightsModel.Node child : model.getChildren(id)) {
            if (RightsModel.CLASS_ADMINISTRATION_DIRECTORY.equals(child.className)) continue;
            if (scope.add(child.id)) collect(child.id, scope);
        }
    }

    private boolean isInsideAdministration(long id) {
        Set<Long> seen = new HashSet<>();
        RightsModel.Node node = model.get(id);
        while (node != null && seen.add(node.id)) {
            if (RightsModel.CLASS_ADMINISTRATION_DIRECTORY.equals(node.className)) return true;
            node = model.getParent(node.id);
        }
        return false;
    }

    private boolean inScope(long id, Long scopeId) {
        return scopeId == null || model.isInSubtree(id, scopeId);
    }

    List<Finding> findObjectsWithoutOwner(Set<Long> scope) {
        List<Finding> result = new ArrayList<>();
        for (Long id : scope) {
            RightsModel.Node node = model.get(id);
            if (node == null || node.isDeleted()) continue;
            if (model.getRelationshipsFrom(id, JEVisConstants.ObjectRelationship.OWNER).isEmpty()) {
                result.add(new Finding(FindingType.OBJECT_WITHOUT_OWNER, node, model.getPath(id), ""));
            }
        }
        return result;
    }

    List<Finding> findUsersWithoutMembership(Long scopeId) {
        List<Finding> result = new ArrayList<>();
        for (RightsModel.Node user : model.getObjectsOfClass(RightsModel.CLASS_USER)) {
            if (!inScope(user.id, scopeId) || user.isSysAdmin()) continue;
            if (Rights.membershipsOf(model, user.id).isEmpty()) {
                String detail = Rights.rolesOf(model, user.id).isEmpty() ? "" : "role";
                result.add(new Finding(FindingType.USER_WITHOUT_MEMBERSHIP, user, model.getPath(user.id), detail));
            }
        }
        return result;
    }

    List<Finding> findGroupProblems(Long scopeId) {
        List<Finding> result = new ArrayList<>();
        for (RightsModel.Node group : model.getObjectsOfClass(RightsModel.CLASS_GROUP)) {
            if (!inScope(group.id, scopeId)) continue;
            boolean hasMember = false;
            for (RightsModel.Rel rel : model.getRelationshipsTo(group.id)) {
                if (Rights.isMemberType(rel.type) && rel.from != group.id) {
                    RightsModel.Node member = model.get(rel.from);
                    if (member == null || !member.isDeleted()) {
                        hasMember = true;
                        break;
                    }
                }
            }
            if (!hasMember) {
                result.add(new Finding(FindingType.GROUP_WITHOUT_MEMBERS, group, model.getPath(group.id), ""));
            }
            boolean ownsSomething = false;
            for (RightsModel.Rel rel : model.getRelationshipsTo(group.id, JEVisConstants.ObjectRelationship.OWNER)) {
                RightsModel.Node owned = model.get(rel.from);
                if (owned == null || !owned.isDeleted()) {
                    ownsSomething = true;
                    break;
                }
            }
            if (!ownsSomething) {
                result.add(new Finding(FindingType.GROUP_OWNS_NOTHING, group, model.getPath(group.id), ""));
            }
        }
        return result;
    }

    List<Finding> findRoleDrift(Long scopeId) {
        List<Finding> result = new ArrayList<>();
        for (RightsModel.Node user : model.getObjectsOfClass(RightsModel.CLASS_USER)) {
            if (!inScope(user.id, scopeId)) continue;
            RoleDrift drift = computeRoleDrift(user.id);
            if (drift != null && !drift.getGroups().isEmpty()) {
                StringBuilder detail = new StringBuilder();
                for (GroupDrift groupDrift : drift.getGroups()) {
                    if (detail.length() > 0) detail.append("; ");
                    RightsModel.Node group = model.get(groupDrift.groupId);
                    detail.append(group == null ? String.valueOf(groupDrift.groupId) : group.name);
                    if (groupDrift.missing != 0) detail.append(" +").append(Rights.format(groupDrift.missing));
                    if (groupDrift.extra != 0) detail.append(" -").append(Rights.format(groupDrift.extra));
                }
                Finding finding = new Finding(FindingType.ROLE_MEMBER_MISMATCH, user, model.getPath(user.id), detail.toString());
                finding.roleDrift = drift;
                result.add(finding);
            }
        }
        return result;
    }

    /**
     * Compares the memberships of a user with the rights of all roles the user is a member of. Only groups that one
     * of these roles refers to are compared; memberships in other groups are not part of a role and are ignored.
     *
     * @return the drift, or null if the user is in no role
     */
    public RoleDrift computeRoleDrift(long userId) {
        List<Long> roles = new ArrayList<>();
        for (Long roleId : Rights.rolesOf(model, userId)) {
            RightsModel.Node role = model.get(roleId);
            if (role != null && !role.isDeleted()) roles.add(roleId);
        }
        if (roles.isEmpty()) return null;
        Map<Long, Integer> expected = new TreeMap<>();
        for (Long roleId : roles) {
            Rights.roleRightsOf(model, roleId).forEach((group, mask) -> expected.merge(group, mask, (a, b) -> a | b));
        }
        Map<Long, Integer> actual = Rights.membershipsOf(model, userId);
        RoleDrift drift = new RoleDrift(userId, roles);
        for (Map.Entry<Long, Integer> entry : expected.entrySet()) {
            RightsModel.Node group = model.get(entry.getKey());
            if (group != null && group.isDeleted()) continue;
            int has = actual.getOrDefault(entry.getKey(), 0);
            int missing = entry.getValue() & ~has;
            int extra = has & ~entry.getValue();
            if (missing != 0 || extra != 0) {
                drift.groups.add(new GroupDrift(entry.getKey(), missing, extra));
            }
        }
        return drift;
    }

    public enum FindingType {
        OBJECT_WITHOUT_OWNER, USER_WITHOUT_MEMBERSHIP, GROUP_WITHOUT_MEMBERS, GROUP_OWNS_NOTHING, ROLE_MEMBER_MISMATCH
    }

    /**
     * One problem found by the check.
     */
    public static class Finding {
        private final FindingType type;
        private final long objectId;
        private final String objectName;
        private final String className;
        private final String path;
        private final String detail;
        private RoleDrift roleDrift;

        public Finding(FindingType type, RightsModel.Node node, String path, String detail) {
            this.type = type;
            this.objectId = node.id;
            this.objectName = node.name;
            this.className = node.className;
            this.path = path;
            this.detail = detail;
        }

        public FindingType getType() {
            return type;
        }

        public long getObjectId() {
            return objectId;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getClassName() {
            return className;
        }

        public String getPath() {
            return path;
        }

        /**
         * @return for ROLE_MEMBER_MISMATCH a list "group +missing -extra"; for USER_WITHOUT_MEMBERSHIP "role" if the
         * user is in a role (the role was never applied); otherwise empty
         */
        public String getDetail() {
            return detail;
        }

        public RoleDrift getRoleDrift() {
            return roleDrift;
        }
    }

    /**
     * Differences between the memberships of a user and the rights of the user's roles.
     */
    public static class RoleDrift {
        private final long userId;
        private final List<Long> roleIds;
        private final List<GroupDrift> groups = new ArrayList<>();

        RoleDrift(long userId, List<Long> roleIds) {
            this.userId = userId;
            this.roleIds = roleIds;
        }

        public long getUserId() {
            return userId;
        }

        public List<Long> getRoleIds() {
            return roleIds;
        }

        public List<GroupDrift> getGroups() {
            return groups;
        }

        /**
         * @return the changes that bring the memberships back in line with the roles: add missing rights, remove
         * rights on role groups that no role of the user grants. Memberships in groups outside the roles stay.
         */
        public RightsPlan toFixPlan(RightsModel model) {
            RightsPlan plan = new RightsPlan();
            RightsPlan.Ref user = plan.existing(model.get(userId));
            for (GroupDrift groupDrift : groups) {
                RightsPlan.Ref group = plan.existing(model.get(groupDrift.groupId));
                for (Integer type : Rights.memberTypes(groupDrift.missing)) {
                    plan.addRelationship(user, group, type, RightsPlan.Category.MEMBERSHIP);
                }
                for (Integer type : Rights.memberTypes(groupDrift.extra)) {
                    plan.removeRelationship(user, group, type, RightsPlan.Category.MEMBERSHIP);
                }
            }
            return plan;
        }
    }

    /**
     * Drift for one group: rights the roles grant but the user lacks (missing), and rights the user has but no role
     * grants (extra).
     */
    public static class GroupDrift {
        public final long groupId;
        public final int missing;
        public final int extra;

        GroupDrift(long groupId, int missing, int extra) {
            this.groupId = groupId;
            this.missing = missing;
            this.extra = extra;
        }
    }
}
