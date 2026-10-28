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

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import org.jevis.api.JEVisObject;

public class User {

    private final SimpleObjectProperty<JEVisObject> userObject;
    private final SimpleBooleanProperty isNew;
    private final SimpleBooleanProperty initMember;
    private final SimpleBooleanProperty member;
    private final SimpleStringProperty username;
    private final SimpleLongProperty id;


    public User(JEVisObject userObject) {
        this.userObject = new SimpleObjectProperty<>(userObject);
        this.username =new SimpleStringProperty(userObject.getName());
        this.id =new SimpleLongProperty(userObject.getID());
        this.isNew = new SimpleBooleanProperty(false);
        this.member = new SimpleBooleanProperty(false);
        this.initMember = new SimpleBooleanProperty(false);

    }

    public boolean getInitMember() {
        return initMember.get();
    }

    public SimpleBooleanProperty initMemberProperty() {
        return initMember;
    }

    public JEVisObject getUserObject() {
        return userObject.get();
    }

    public SimpleObjectProperty<JEVisObject> userObjectProperty() {
        return userObject;
    }


    public boolean getMember() {
        return member.get();
    }

    public SimpleBooleanProperty memberProperty() {
        return member;
    }

    public void setMember(boolean member) {
        this.member.set(member);
    }

    public boolean isIsNew() {
        return isNew.get();
    }

    public SimpleBooleanProperty isNewProperty() {
        return isNew;
    }

    public void setUserObject(JEVisObject userObject) {
        this.userObject.set(userObject);
    }

    public String getUsername() {
        return username.get();
    }

    public SimpleStringProperty usernameProperty() {
        return username;
    }

    public long getId() {
        return id.get();
    }

    public SimpleLongProperty idProperty() {
        return id;
    }
}
