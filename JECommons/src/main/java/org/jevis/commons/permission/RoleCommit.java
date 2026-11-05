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

import java.util.*;

import static org.jevis.api.JEVisConstants.ObjectRelationship.ROLE_MEMBER;

/**
 * Plans the changes when a role is saved in the role editor: role rights (role → group), role members
 * (role → user) and the memberships of the role members (user → group), which are copies of the role rights.
 * <p>
 * Only the groups the role refers to (before or after saving) are touched, and only for users that are or were
 * members of the role. On these groups each such user gets the union of the rights of all his roles (the same rule
 * as the re-apply of {@link PermissionConsistencyCheck}): missing rights are added, other rights are removed unless
 * another role of the user grants them. Memberships in other groups are never changed.
 */
public final class RoleCommit {

    private RoleCommit() {
    }

    /**
     * @param model      snapshot before saving
     * @param roleId     the saved role
     * @param newRights  group id → rights the role grants after saving (missing or 0: no rights)
     * @param newMembers ids of the users that are members of the role after saving
     */
    public static RightsPlan plan(RightsModel model, long roleId, Map<Long, Integer> newRights, Set<Long> newMembers) {
        RightsPlan plan = new RightsPlan();
        RightsModel.Node roleNode = model.get(roleId);
        if (roleNode == null) throw new IllegalArgumentException("role not in model: " + roleId);
        Map<Long, RightsPlan.Ref> refs = new HashMap<>();
        RightsPlan.Ref role = plan.existing(roleNode);
        refs.put(roleId, role);

        /* role rights */
        Map<Long, Integer> oldRights = Rights.roleRightsOf(model, roleId);
        Set<Long> roleGroups = new TreeSet<>();
        for (Map.Entry<Long, Integer> entry : oldRights.entrySet()) if (entry.getValue() != 0) roleGroups.add(entry.getKey());
        for (Map.Entry<Long, Integer> entry : newRights.entrySet()) if (entry.getValue() != null && entry.getValue() != 0) roleGroups.add(entry.getKey());
        for (Long groupId : new ArrayList<>(roleGroups)) {
            RightsModel.Node group = model.get(groupId);
            if (group == null) {
                roleGroups.remove(groupId);
                continue;
            }
            RightsPlan.Ref groupRef = refs.computeIfAbsent(groupId, id -> plan.existing(group));
            int before = oldRights.getOrDefault(groupId, 0);
            Integer afterMask = newRights.get(groupId);
            int after = afterMask == null ? 0 : afterMask;
            for (Integer type : Rights.roleTypes(after & ~before)) {
                plan.addRelationship(role, groupRef, type, RightsPlan.Category.ROLE_RIGHT);
            }
            for (Integer type : Rights.roleTypes(before & ~after)) {
                plan.removeRelationship(role, groupRef, type, RightsPlan.Category.ROLE_RIGHT);
            }
        }

        /* role members */
        Set<Long> oldMembers = new TreeSet<>();
        for (RightsModel.Rel rel : model.getRelationshipsFrom(roleId, ROLE_MEMBER)) oldMembers.add(rel.to);
        Set<Long> affected = new TreeSet<>(oldMembers);
        affected.addAll(newMembers);
        for (Long userId : affected) {
            RightsModel.Node user = model.get(userId);
            if (user == null) continue;
            RightsPlan.Ref userRef = refs.computeIfAbsent(userId, id -> plan.existing(user));
            boolean member = newMembers.contains(userId);
            if (member && !oldMembers.contains(userId)) {
                plan.addRelationship(role, userRef, ROLE_MEMBER, RightsPlan.Category.ROLE_MEMBER);
            } else if (!member && oldMembers.contains(userId)) {
                plan.removeRelationship(role, userRef, ROLE_MEMBER, RightsPlan.Category.ROLE_MEMBER);
            }
            if (user.isDeleted()) continue;

            /* memberships on the role's groups: union of all roles of the user after saving */
            Map<Long, Integer> desired = new HashMap<>();
            if (member) {
                newRights.forEach((group, mask) -> {
                    if (mask != null) desired.merge(group, mask, (a, b) -> a | b);
                });
            }
            for (Long otherRole : Rights.rolesOf(model, userId)) {
                if (otherRole == roleId) continue;
                RightsModel.Node other = model.get(otherRole);
                if (other == null || other.isDeleted()) continue;
                Rights.roleRightsOf(model, otherRole).forEach((group, mask) -> desired.merge(group, mask, (a, b) -> a | b));
            }
            Map<Long, Integer> has = Rights.membershipsOf(model, userId);
            for (Long groupId : roleGroups) {
                RightsModel.Node group = model.get(groupId);
                if (group.isDeleted()) continue;
                RightsPlan.Ref groupRef = refs.computeIfAbsent(groupId, id -> plan.existing(group));
                int want = desired.getOrDefault(groupId, 0);
                int actual = has.getOrDefault(groupId, 0);
                for (Integer type : Rights.memberTypes(want & ~actual)) {
                    plan.addRelationship(userRef, groupRef, type, RightsPlan.Category.MEMBERSHIP);
                }
                for (Integer type : Rights.memberTypes(actual & ~want)) {
                    plan.removeRelationship(userRef, groupRef, type, RightsPlan.Category.MEMBERSHIP);
                }
            }
        }
        return plan;
    }
}
