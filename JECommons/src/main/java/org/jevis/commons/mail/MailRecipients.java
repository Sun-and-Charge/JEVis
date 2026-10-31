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
package org.jevis.commons.mail;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.classes.JC;

import java.util.*;

/**
 * Resolves users and groups to e-mail addresses at the time a mail is sent (upstream issue #1437).
 * <p>
 * A user contributes the value of its "E-Mail" attribute (several addresses may be separated by ';' or ','). A group
 * contributes the addresses of all its members (users with a membership relationship to the group). Disabled users are
 * skipped.
 */
public class MailRecipients {

    private static final Logger logger = LogManager.getLogger(MailRecipients.class);
    private static final String USER_CLASS = "User";
    private static final String GROUP_CLASS = "Group";
    private static final int[] MEMBERSHIPS = {
            JEVisConstants.ObjectRelationship.MEMBER_READ,
            JEVisConstants.ObjectRelationship.MEMBER_WRITE,
            JEVisConstants.ObjectRelationship.MEMBER_EXECUTE,
            JEVisConstants.ObjectRelationship.MEMBER_CREATE,
            JEVisConstants.ObjectRelationship.MEMBER_DELETE};

    /**
     * Splits an address list on ';', ',' and whitespace and drops empty entries.
     */
    public static List<String> parseAddresses(String addresses) {
        List<String> result = new ArrayList<>();
        if (addresses == null) {
            return result;
        }
        for (String part : addresses.split("[;,\\s]+")) {
            String address = part.trim();
            if (!address.isEmpty() && !result.contains(address)) {
                result.add(address);
            }
        }
        return result;
    }

    /**
     * @param usersOrGroups User and Group objects
     * @return the e-mail addresses, without duplicates, in the order of the objects
     */
    public static Set<String> resolve(JEVisDataSource ds, Collection<JEVisObject> usersOrGroups) {
        Set<String> addresses = new LinkedHashSet<>();
        for (JEVisObject object : usersOrGroups) {
            if (object == null) {
                continue;
            }
            try {
                String className = object.getJEVisClassName();
                if (USER_CLASS.equals(className)) {
                    addresses.addAll(addressesOfUser(object));
                } else if (GROUP_CLASS.equals(className)) {
                    for (JEVisObject member : membersOf(ds, object)) {
                        addresses.addAll(addressesOfUser(member));
                    }
                } else {
                    logger.warn("{} is neither a user nor a group, ignored as mail recipient", object);
                }
            } catch (Exception e) {
                logger.error("Could not resolve mail recipient {}", object, e);
            }
        }
        return addresses;
    }

    /**
     * Resolves a list of object ids as stored in an attribute (e.g. "12;15" or "12:Value").
     */
    public static Set<String> resolveIds(JEVisDataSource ds, String ids) {
        List<JEVisObject> objects = new ArrayList<>();
        for (Long id : parseIds(ids)) {
            try {
                JEVisObject object = ds.getObject(id);
                if (object != null) {
                    objects.add(object);
                } else {
                    logger.warn("Mail recipient object {} not found or not readable", id);
                }
            } catch (Exception e) {
                logger.error("Could not load mail recipient object {}", id, e);
            }
        }
        return resolve(ds, objects);
    }

    static List<Long> parseIds(String ids) {
        List<Long> result = new ArrayList<>();
        if (ids == null) {
            return result;
        }
        for (String part : ids.split("[;,\\s]+")) {
            String id = part.split(":")[0].trim();
            if (id.isEmpty()) {
                continue;
            }
            try {
                Long value = Long.parseLong(id);
                if (!result.contains(value)) {
                    result.add(value);
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid object id '{}' in mail recipients", part);
            }
        }
        return result;
    }

    static List<String> addressesOfUser(JEVisObject user) throws JEVisException {
        JEVisAttribute enabled = user.getAttribute(JC.User.a_Enabled);
        if (enabled != null && enabled.hasSample() && !enabled.getLatestSample().getValueAsBoolean()) {
            return Collections.emptyList();
        }
        JEVisAttribute email = user.getAttribute(JC.User.a_EMail);
        if (email == null || !email.hasSample()) {
            logger.info("User {} has no e-mail address", user.getName());
            return Collections.emptyList();
        }
        return parseAddresses(email.getLatestSample().getValueAsString());
    }

    static List<JEVisObject> membersOf(JEVisDataSource ds, JEVisObject group) throws JEVisException {
        Map<Long, JEVisObject> members = new LinkedHashMap<>();
        for (int type : MEMBERSHIPS) {
            for (JEVisRelationship relationship : ds.getRelationships(type)) {
                if (relationship.getEndID() == group.getID() && !members.containsKey(relationship.getStartID())) {
                    JEVisObject user = relationship.getStartObject();
                    if (user != null && USER_CLASS.equals(user.getJEVisClassName())) {
                        members.put(user.getID(), user);
                    }
                }
            }
        }
        return new ArrayList<>(members.values());
    }
}
