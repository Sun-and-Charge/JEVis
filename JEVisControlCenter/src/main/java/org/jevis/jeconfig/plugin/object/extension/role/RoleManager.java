/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.object.extension.role;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.permission.RightsModel;
import org.jevis.commons.permission.RightsPlan;
import org.jevis.commons.permission.RightsPlanExecutor;
import org.jevis.commons.permission.RoleCommit;
import org.jevis.commons.permission.Rights;

import java.util.*;

import static org.jevis.api.JEVisConstants.ObjectRelationship.*;

public class RoleManager {

    private static final Logger logger = LogManager.getLogger(RoleManager.class);
    final Role role;
    final JEVisObject roleObject;
    final JEVisDataSource ds;
    final List<Integer> REL_IDS = Arrays.asList(
            ROLE_READ, ROLE_WRITE,
            ROLE_EXECUTE, ROLE_CREATE,
            JEVisConstants.ObjectRelationship.ROLE_DELETE
    );
    private final Map<Long, Membership> allMemberships = new HashMap<>();
    private final Map<Long, User> allUsers = new HashMap<>();


    public RoleManager(JEVisObject roleObject) throws JEVisException {
        this.roleObject = roleObject;
        this.ds = roleObject.getDataSource();
        this.role = new Role(roleObject);

        getAllMemberships();
        getAllUser();
        parsRelationships(roleObject);
        this.role.getMemberShips().addAll(new ArrayList<>(allMemberships.values()));
        this.role.getUsers().addAll(new ArrayList<>(allUsers.values()));
    }


    public Role getRole() {
        return role;
    }


    /**
     * Saves the role: role rights, role members and the memberships of the role members. Only the groups the role
     * refers to (before or after saving) are changed, and a right on such a group stays if another role of the user
     * grants it. Memberships in other groups are never touched (see {@link RoleCommit}).
     */
    public void commit() {
        logger.info("Commit role {}", roleObject.getID());
        try {
            RightsModel model = RightsModel.fromDataSource(ds, false);

            /* start from the saved state, so groups and users the editor does not list stay as they are */
            Map<Long, Integer> newRights = new HashMap<>(Rights.roleRightsOf(model, roleObject.getID()));
            for (Membership membership : role.getMemberShips()) {
                newRights.put(membership.getGroupObject().getID(), maskOf(membership));
            }
            Set<Long> newMembers = new HashSet<>();
            for (RightsModel.Rel rel : model.getRelationshipsFrom(roleObject.getID(), ROLE_MEMBER)) {
                newMembers.add(rel.to);
            }
            for (User user : role.getUsers()) {
                if (user.memberProperty().get()) {
                    newMembers.add(user.getId());
                } else {
                    newMembers.remove(user.getId());
                }
            }

            RightsPlan plan = RoleCommit.plan(model, roleObject.getID(), newRights, newMembers);
            plan.getActions().forEach(action -> logger.info("Role commit: {}", action));
            RightsPlanExecutor.Result result = new RightsPlanExecutor(ds).execute(plan, null);
            if (result.getFailed() > 0) {
                logger.error("Role commit: {} of {} changes failed", result.getFailed(), plan.getActions().size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Role commit interrupted", e);
        } catch (Exception e) {
            logger.error("Role commit failed", e);
        }

        role.getMemberShips().forEach(Membership::createInitHash);
        role.getUsers().forEach(user -> user.initMemberProperty().set(user.memberProperty().get()));
    }

    private static int maskOf(Membership membership) {
        int mask = 0;
        if (membership.isRead()) mask |= Rights.READ;
        if (membership.isWrite()) mask |= Rights.WRITE;
        if (membership.isExecute()) mask |= Rights.EXECUTE;
        if (membership.isCreate()) mask |= Rights.CREATE;
        if (membership.isDelete()) mask |= Rights.DELETE;
        return mask;
    }


    private void getAllUser() throws JEVisException {
        List<JEVisObject> allUsers = ds.getObjects(ds.getJEVisClass("User"), true);

        for (JEVisObject userObj : allUsers) {
            User user = new User(userObj);
            this.allUsers.put(user.getId(), user);
        }
    }

    private void getAllMemberships() throws JEVisException {
        List<JEVisObject> allGroups = ds.getObjects(ds.getJEVisClass("Group"), true);
        for (JEVisObject group : allGroups) {
            Membership membership = new Membership(group, false, false, false, false, false);
            allMemberships.put(group.getID(), membership);
        }
    }

    private void parsRelationships(JEVisObject role) {
        try {

            role.getRelationships().forEach(jeVisRelationship -> {
                try {
                    if (REL_IDS.contains(jeVisRelationship.getType())) {
                        JEVisObject groupObj = jeVisRelationship.getEndObject();
                        Membership membership = allMemberships.getOrDefault(groupObj.getID(),
                                new Membership(groupObj, false, false, false, false, false));

                        switch (jeVisRelationship.getType()) {
                            case ROLE_READ:
                                membership.setRead(true);
                                break;
                            case ROLE_WRITE:
                                membership.setWrite(true);
                                break;
                            case ROLE_EXECUTE:
                                membership.setExecute(true);
                                break;
                            case ROLE_CREATE:
                                membership.setCreate(true);
                                break;
                            case ROLE_DELETE:
                                membership.setDelete(true);
                                break;
                        }
                        allMemberships.put(groupObj.getID(), membership);
                    } else if (ROLE_MEMBER == jeVisRelationship.getType()) {
                        JEVisObject userObj = jeVisRelationship.getEndObject();
                        User user = allUsers.getOrDefault(userObj.getID(), new User(userObj));
                        user.memberProperty().set(true);
                        user.initMemberProperty().set(true);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            allMemberships.forEach((aLong, membership) -> {
                membership.createInitHash();
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
