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
package org.jevis.jeconfig.plugin;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisType;
import org.jevis.commons.datetime.WorkDays;

import java.util.Map;

public class RegisterTableRow {
    private final String name;
    private JEVisObject object;
    private final WorkDays workDays;
    private boolean isMultiSite = false;
    private Map<JEVisType, JEVisAttribute> attributeMap;
    private String fullName;

    public RegisterTableRow(Map<JEVisType, JEVisAttribute> attributeMap, JEVisObject object, Boolean isMultiSite) {
        this.object = object;
        this.workDays = new WorkDays(object);
        this.attributeMap = attributeMap;
        this.isMultiSite = isMultiSite;
        this.name = object.getName();
    }

    public JEVisObject getObject() {
        return object;
    }

    public void setObject(JEVisObject object) {
        this.object = object;
    }

    public Map<JEVisType, JEVisAttribute> getAttributeMap() {
        return attributeMap;
    }

    public void setAttributeMap(Map<JEVisType, JEVisAttribute> attributeMap) {
        this.attributeMap = attributeMap;
    }

    public String getName() {
        return name;
    }

    public boolean isMultiSite() {
        return isMultiSite;
    }

    public WorkDays getWorkDays() {
        return workDays;
    }
}
