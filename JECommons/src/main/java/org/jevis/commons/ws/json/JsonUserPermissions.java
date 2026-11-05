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
 * Answer of {@code GET permissions/user/{id}} (OpenJEVis #1211): the group memberships and roles of a user and,
 * if an object was given, his effective rights on it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonUserPermissions {

    private long userId;
    private String userName;
    private boolean sysAdmin;
    private boolean enabled;
    private List<JsonGroupRights> groups = new ArrayList<>();
    private List<JsonRoleRights> roles = new ArrayList<>();
    private Long objectId;
    private String objectName;
    private JsonEffectiveRights objectRights;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public boolean isSysAdmin() {
        return sysAdmin;
    }

    public void setSysAdmin(boolean sysAdmin) {
        this.sysAdmin = sysAdmin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<JsonGroupRights> getGroups() {
        return groups;
    }

    public void setGroups(List<JsonGroupRights> groups) {
        this.groups = groups;
    }

    public List<JsonRoleRights> getRoles() {
        return roles;
    }

    public void setRoles(List<JsonRoleRights> roles) {
        this.roles = roles;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public JsonEffectiveRights getObjectRights() {
        return objectRights;
    }

    public void setObjectRights(JsonEffectiveRights objectRights) {
        this.objectRights = objectRights;
    }
}
