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
 * The effective rights of one user on one object, computed with the rules of {@code UserRightManagerForWS}
 * (OpenJEVis #1211). For every right the list of sources says which group memberships grant it. Read can also come from
 * {@link #isPublicObject()} or {@link #isOwnObject()} (a user can always read his own user object); a Sys Admin has all
 * rights without sources. {@link #getCreateViaExecuteClasses()} lists the classes the user may create under the object
 * because he has the execute right (special rule for notes), if he has no create right.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonEffectiveRights {

    private long userId;
    private String userName;
    private boolean sysAdmin;
    private boolean enabled;
    private boolean publicObject;
    private boolean ownObject;
    private boolean read;
    private boolean write;
    private boolean create;
    private boolean delete;
    private boolean execute;
    private List<JsonPermissionSource> readSources = new ArrayList<>();
    private List<JsonPermissionSource> writeSources = new ArrayList<>();
    private List<JsonPermissionSource> createSources = new ArrayList<>();
    private List<JsonPermissionSource> deleteSources = new ArrayList<>();
    private List<JsonPermissionSource> executeSources = new ArrayList<>();
    private List<String> createViaExecuteClasses = new ArrayList<>();

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

    public boolean isPublicObject() {
        return publicObject;
    }

    public void setPublicObject(boolean publicObject) {
        this.publicObject = publicObject;
    }

    public boolean isOwnObject() {
        return ownObject;
    }

    public void setOwnObject(boolean ownObject) {
        this.ownObject = ownObject;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isWrite() {
        return write;
    }

    public void setWrite(boolean write) {
        this.write = write;
    }

    public boolean isCreate() {
        return create;
    }

    public void setCreate(boolean create) {
        this.create = create;
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public boolean isExecute() {
        return execute;
    }

    public void setExecute(boolean execute) {
        this.execute = execute;
    }

    public List<JsonPermissionSource> getReadSources() {
        return readSources;
    }

    public void setReadSources(List<JsonPermissionSource> readSources) {
        this.readSources = readSources;
    }

    public List<JsonPermissionSource> getWriteSources() {
        return writeSources;
    }

    public void setWriteSources(List<JsonPermissionSource> writeSources) {
        this.writeSources = writeSources;
    }

    public List<JsonPermissionSource> getCreateSources() {
        return createSources;
    }

    public void setCreateSources(List<JsonPermissionSource> createSources) {
        this.createSources = createSources;
    }

    public List<JsonPermissionSource> getDeleteSources() {
        return deleteSources;
    }

    public void setDeleteSources(List<JsonPermissionSource> deleteSources) {
        this.deleteSources = deleteSources;
    }

    public List<JsonPermissionSource> getExecuteSources() {
        return executeSources;
    }

    public void setExecuteSources(List<JsonPermissionSource> executeSources) {
        this.executeSources = executeSources;
    }

    public List<String> getCreateViaExecuteClasses() {
        return createViaExecuteClasses;
    }

    public void setCreateViaExecuteClasses(List<String> createViaExecuteClasses) {
        this.createViaExecuteClasses = createViaExecuteClasses;
    }

    /**
     * @return {@code true} if the user has at least one right on the object
     */
    public boolean hasAnyRight() {
        return read || write || create || delete || execute;
    }
}
