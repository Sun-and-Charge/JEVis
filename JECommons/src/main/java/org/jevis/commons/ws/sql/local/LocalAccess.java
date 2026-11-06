/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.local;

import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonRelationship;
import org.jevis.commons.ws.sql.UserRightManagerForWS;

import java.util.*;

/**
 * Read rights of the current user, evaluated with the rules of {@link UserRightManagerForWS}.
 * <p>
 * An object is readable if one of the following holds; this is exactly the rule of
 * {@link UserRightManagerForWS#filterList(List)} (used by {@code GET /objects}, i.e. the object list the desktop client
 * works with) and it contains every object {@link UserRightManagerForWS#canRead(JsonObject)} (used by
 * {@code GET /objects/{id}}, {@code /attributes} and {@code /samples}) accepts:
 * <ol>
 *     <li>the user is a sys admin,</li>
 *     <li>the object is public,</li>
 *     <li>the object is the user's own user object,</li>
 *     <li>the object is one of the groups the user may read (READ membership),</li>
 *     <li>the object has an OWNER relationship to a group the user may read.</li>
 * </ol>
 * A relationship is visible if both ends are readable, like {@link UserRightManagerForWS#filterRelationships(List)}
 * ({@code GET /relationships}); sys admins see all relationships.
 * <p>
 * The group IDs and the sys admin flag come from {@link UserRightManagerForWS}; the OWNER relationships are indexed
 * once per request, so a check costs O(1) instead of a scan over all relationships (filterList and canRead scan the
 * relationship list for every object, which is too slow for large trees).
 */
final class LocalAccess {

    private final boolean sysAdmin;
    private final long userID;
    private final Set<Long> readGroups;
    /**
     * object ID -> groups owning the object (OWNER relationship object -> group)
     */
    private final Map<Long, Set<Long>> ownerGroups = new HashMap<>();

    LocalAccess(boolean sysAdmin, long userID, Collection<Long> readGroups, Collection<JsonRelationship> allRelationships) {
        this.sysAdmin = sysAdmin;
        this.userID = userID;
        this.readGroups = new HashSet<>(readGroups);
        if (!sysAdmin) {
            for (JsonRelationship rel : allRelationships) {
                if (rel.getType() == JEVisConstants.ObjectRelationship.OWNER) {
                    Set<Long> owners = ownerGroups.get(rel.getFrom());
                    if (owners == null) {
                        owners = new HashSet<>();
                        ownerGroups.put(rel.getFrom(), owners);
                    }
                    owners.add(rel.getTo());
                }
            }
        }
    }

    /**
     * Builds the rule for the user of the given rights manager.
     *
     * @param um               rights manager of the request
     * @param userID           object ID of the current user
     * @param allRelationships all relationships of the database (unfiltered)
     */
    static LocalAccess of(UserRightManagerForWS um, long userID, Collection<JsonRelationship> allRelationships) {
        return new LocalAccess(um.isSysAdmin(), userID, um.getReadGIDS(), allRelationships);
    }

    boolean isSysAdmin() {
        return sysAdmin;
    }

    long getUserID() {
        return userID;
    }

    Set<Long> getReadGroups() {
        return Collections.unmodifiableSet(readGroups);
    }

    /**
     * @return {@code true} if the current user may read the object
     */
    boolean canRead(JsonObject object) {
        if (object == null) {
            return false;
        }
        if (sysAdmin || object.getisPublic()) {
            return true;
        }
        return isReadableID(object.getId());
    }

    /**
     * The ID based part of the rule (own user object, read group, owned by a read group). Public objects are not
     * covered, see {@link #canRead(JsonObject)}.
     */
    boolean isReadableID(long id) {
        if (sysAdmin || id == userID || readGroups.contains(id)) {
            return true;
        }
        Set<Long> owners = ownerGroups.get(id);
        if (owners != null) {
            for (Long owner : owners) {
                if (readGroups.contains(owner)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param rel     the relationship
     * @param objects all objects by ID, used for the public flag of both ends
     * @return {@code true} if the relationship is visible to the current user
     */
    boolean canRead(JsonRelationship rel, Map<Long, JsonObject> objects) {
        if (sysAdmin) {
            return true;
        }
        return isEndReadable(rel.getFrom(), objects) && isEndReadable(rel.getTo(), objects);
    }

    private boolean isEndReadable(long id, Map<Long, JsonObject> objects) {
        if (isReadableID(id)) {
            return true;
        }
        JsonObject object = objects.get(id);
        return object != null && object.getisPublic();
    }
}
