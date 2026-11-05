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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The five group rights as bit mask. Bit i belongs to membership type {@code MEMBER_READ + i} and role right type
 * {@code ROLE_READ + i}.
 */
public final class Rights {

    public static final int READ = 1;
    public static final int WRITE = 2;
    public static final int EXECUTE = 4;
    public static final int CREATE = 8;
    public static final int DELETE = 16;
    public static final int ALL = READ | WRITE | EXECUTE | CREATE | DELETE;
    private static final int COUNT = 5;

    private Rights() {
    }

    public static boolean isMemberType(int type) {
        return type >= JEVisConstants.ObjectRelationship.MEMBER_READ && type <= JEVisConstants.ObjectRelationship.MEMBER_DELETE;
    }

    public static boolean isRoleRightType(int type) {
        return type >= JEVisConstants.ObjectRelationship.ROLE_READ && type <= JEVisConstants.ObjectRelationship.ROLE_DELETE;
    }

    public static int bitOfMemberType(int type) {
        return isMemberType(type) ? 1 << (type - JEVisConstants.ObjectRelationship.MEMBER_READ) : 0;
    }

    public static int bitOfRoleType(int type) {
        return isRoleRightType(type) ? 1 << (type - JEVisConstants.ObjectRelationship.ROLE_READ) : 0;
    }

    public static List<Integer> memberTypes(int mask) {
        List<Integer> types = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            if ((mask & (1 << i)) != 0) types.add(JEVisConstants.ObjectRelationship.MEMBER_READ + i);
        }
        return types;
    }

    public static List<Integer> roleTypes(int mask) {
        List<Integer> types = new ArrayList<>();
        for (int i = 0; i < COUNT; i++) {
            if ((mask & (1 << i)) != 0) types.add(JEVisConstants.ObjectRelationship.ROLE_READ + i);
        }
        return types;
    }

    /**
     * @return short form like "R W - - D"
     */
    public static String format(int mask) {
        String[] letters = {"R", "W", "X", "C", "D"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COUNT; i++) {
            if (i > 0) sb.append(' ');
            sb.append((mask & (1 << i)) != 0 ? letters[i] : "-");
        }
        return sb.toString();
    }

    /**
     * @return group id → rights of the given user (MEMBER_x relationships)
     */
    public static Map<Long, Integer> membershipsOf(RightsModel model, long userId) {
        Map<Long, Integer> result = new HashMap<>();
        for (RightsModel.Rel rel : model.getRelationshipsFrom(userId)) {
            if (isMemberType(rel.type)) {
                result.merge(rel.to, bitOfMemberType(rel.type), (a, b) -> a | b);
            }
        }
        return result;
    }

    /**
     * @return group id → rights the given role grants (ROLE_x relationships, role → group)
     */
    public static Map<Long, Integer> roleRightsOf(RightsModel model, long roleId) {
        Map<Long, Integer> result = new HashMap<>();
        for (RightsModel.Rel rel : model.getRelationshipsFrom(roleId)) {
            if (isRoleRightType(rel.type)) {
                result.merge(rel.to, bitOfRoleType(rel.type), (a, b) -> a | b);
            }
        }
        return result;
    }

    /**
     * @return ids of the roles the user is a member of (ROLE_MEMBER, role → user)
     */
    public static List<Long> rolesOf(RightsModel model, long userId) {
        List<Long> result = new ArrayList<>();
        for (RightsModel.Rel rel : model.getRelationshipsTo(userId, JEVisConstants.ObjectRelationship.ROLE_MEMBER)) {
            result.add(rel.from);
        }
        return result;
    }
}
