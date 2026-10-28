/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEStatus.
 * <p>
 * JEStatus is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEStatus is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEStatus. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEStatus is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jestatus;

import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

public class DataServerLine {
    private final StringBuilder lineString = new StringBuilder();
    private String name;
    private Long id;
    private String nameTarget;
    private String organizationName;
    private String buildingName;
    private JEVisObject targetObject;
    private DateTime lastTimeStamp = new DateTime();

    public DataServerLine() {
    }

    public StringBuilder getLineString() {
        return lineString;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameTarget() {
        return nameTarget;
    }

    public void setNameTarget(String nameTarget) {
        this.nameTarget = nameTarget;
    }

    public String getOrganizationName() {
        return organizationName;
    }

    public void setOrganizationName(String organizationName) {
        this.organizationName = organizationName;
    }

    public String getBuildingName() {
        return buildingName;
    }

    public void setBuildingName(String buildingName) {
        this.buildingName = buildingName;
    }

    public JEVisObject getTargetObject() {
        return targetObject;
    }

    public void setTargetObject(JEVisObject targetObject) {
        this.targetObject = targetObject;
    }

    public DateTime getLastTimeStamp() {
        return lastTimeStamp;
    }

    public void setLastTimeStamp(DateTime lastTimeStamp) {
        this.lastTimeStamp = lastTimeStamp;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}