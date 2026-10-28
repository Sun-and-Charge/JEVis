/**
 * Copyright (C) JEVis contributors
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

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jevis.api.JEVisObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Role {

    private final JEVisObject roleObject;
    private final ObservableList<Membership> memberShips = FXCollections.observableArrayList();
    private ObservableList<User> users = FXCollections.observableArrayList();

    public Role(JEVisObject roleObject) {
        this.roleObject = roleObject;
    }

    public ObservableList<Membership> getMemberShips() {
        return memberShips;
    }

    public ObservableList<User> getUsers() {
        return users;
    }

    public void setUsers(ObservableList<User> users) {
        this.users = users;
    }

    public JEVisObject getRoleObject() {
        return roleObject;
    }

    public boolean hasChanged() {
        AtomicBoolean userChanged = new AtomicBoolean(false);
        AtomicBoolean membershipChanged = new AtomicBoolean(false);

        users.forEach(user -> {
            if (user.getInitMember() != user.memberProperty().get()) userChanged.set(true);
        });
        membershipChanged.set(!getChangeMemberships().isEmpty());
        return (membershipChanged.get() || userChanged.get());
    }

    public List<Membership> getChangeMemberships() {
        List<Membership> changedList = new ArrayList<>();
        getMemberShips().forEach(membership -> {
            if (membership.hasChanged()) {
                changedList.add(membership);
            }
        });


        return changedList;
    }
}
