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
package org.jevis.rest;

import org.jevis.api.JEVisConstants.ObjectRelationship;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.SQLDataSource;

import java.util.*;

/**
 * E10.0: no rights can be granted that the granting user does not hold. Write right on both objects was enough to add
 * any membership or role relationship, so a user who may edit a group could give himself or others more rights on it.
 * <ul>
 *     <li>Sys Admins may grant everything.</li>
 *     <li>A membership {@code MEMBER_x} (user → group) needs the same membership of the granting user on that group.</li>
 *     <li>A role right {@code ROLE_x} (role → group) needs the membership {@code MEMBER_x} of the granting user on that
 *     group.</li>
 *     <li>A role assignment {@code ROLE_MEMBER} (role → user) needs every right the role carries.</li>
 *     <li>Other relationships (owner, parent, root …) and removing relationships are not restricted here.</li>
 * </ul>
 * Site managers ("Verwalten") keep managing the groups of their site, they hold all rights on them.
 */
public final class RelationshipGrant {

    private RelationshipGrant() {
    }

    /** the membership type that corresponds to a role right ({@code ROLE_READ} → {@code MEMBER_READ} …), else 0 */
    static int membershipFor(int roleRight) {
        if (roleRight >= ObjectRelationship.ROLE_READ && roleRight <= ObjectRelationship.ROLE_DELETE) {
            return roleRight - ObjectRelationship.ROLE_READ + ObjectRelationship.MEMBER_READ;
        }
        return 0;
    }

    static boolean isMembership(int type) {
        return type >= ObjectRelationship.MEMBER_READ && type <= ObjectRelationship.MEMBER_DELETE;
    }

    /**
     * @param held     group id → membership types the granting user holds on it
     * @param roleRights for {@code ROLE_MEMBER}: the rights of the role (group id → role right types), else ignored
     * @return whether the granting user may create the relationship
     */
    public static boolean mayGrant(boolean sysAdmin, int type, long from, long to, Map<Long, Set<Integer>> held,
                            Map<Long, Set<Integer>> roleRights) {
        if (sysAdmin) return true;
        if (isMembership(type)) {
            return held.getOrDefault(to, Collections.emptySet()).contains(type);
        }
        int membership = membershipFor(type);
        if (membership != 0) {
            return held.getOrDefault(to, Collections.emptySet()).contains(membership);
        }
        if (type == ObjectRelationship.ROLE_MEMBER) {
            for (Map.Entry<Long, Set<Integer>> e : roleRights.entrySet()) {
                for (int right : e.getValue()) {
                    int needed = membershipFor(right);
                    if (needed != 0 && !held.getOrDefault(e.getKey(), Collections.emptySet()).contains(needed)) return false;
                }
            }
            return true;
        }
        return true;
    }

    /** the check against the database for the current user */
    public static boolean mayGrant(SQLDataSource ds, JsonRelationship rel) {
        if (ds.getCurrentUser().isSysAdmin()) return true;
        int type = rel.getType();
        boolean needsCheck = isMembership(type) || membershipFor(type) != 0 || type == ObjectRelationship.ROLE_MEMBER;
        if (!needsCheck) return true;
        long userId = ds.getCurrentUser().getUserID();
        Map<Long, Set<Integer>> held = new HashMap<>();
        for (JsonRelationship r : ds.getRelationships(userId)) {
            if (r.getFrom() == userId && isMembership(r.getType())) {
                held.computeIfAbsent(r.getTo(), k -> new HashSet<>()).add(r.getType());
            }
        }
        Map<Long, Set<Integer>> roleRights = new HashMap<>();
        if (type == ObjectRelationship.ROLE_MEMBER) {
            for (JsonRelationship r : ds.getRelationships(rel.getFrom())) {
                if (r.getFrom() == rel.getFrom() && membershipFor(r.getType()) != 0) {
                    roleRights.computeIfAbsent(r.getTo(), k -> new HashSet<>()).add(r.getType());
                }
            }
        }
        return mayGrant(false, type, rel.getFrom(), rel.getTo(), held, roleRights);
    }
}
