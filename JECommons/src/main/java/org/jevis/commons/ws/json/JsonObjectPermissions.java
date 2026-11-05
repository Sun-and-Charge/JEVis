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
package org.jevis.commons.ws.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * Answer of {@code GET permissions/object/{id}} (OpenJEVis #1211): the owner groups of the object and the effective
 * rights of the users. {@link #isComplete()} is {@code false} if the caller may only see his own row.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonObjectPermissions {

    private long objectId;
    private String objectName;
    private String jevisClass;
    private boolean publicObject;
    private boolean complete;
    private List<JsonPermissionRef> ownerGroups = new ArrayList<>();
    private List<JsonEffectiveRights> users = new ArrayList<>();

    public long getObjectId() {
        return objectId;
    }

    public void setObjectId(long objectId) {
        this.objectId = objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getJevisClass() {
        return jevisClass;
    }

    public void setJevisClass(String jevisClass) {
        this.jevisClass = jevisClass;
    }

    public boolean isPublicObject() {
        return publicObject;
    }

    public void setPublicObject(boolean publicObject) {
        this.publicObject = publicObject;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public List<JsonPermissionRef> getOwnerGroups() {
        return ownerGroups;
    }

    public void setOwnerGroups(List<JsonPermissionRef> ownerGroups) {
        this.ownerGroups = ownerGroups;
    }

    public List<JsonEffectiveRights> getUsers() {
        return users;
    }

    public void setUsers(List<JsonEffectiveRights> users) {
        this.users = users;
    }
}
