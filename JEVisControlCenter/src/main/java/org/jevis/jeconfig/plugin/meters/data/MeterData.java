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
package org.jevis.jeconfig.plugin.meters.data;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;

import java.util.HashMap;
import java.util.Map;

public class MeterData {
    JEVisObject jeVisObject;
    Map<JEVisTypeWrapper, SampleData> jeVisAttributeJEVisSampleMap = new HashMap<>();

    private JEVisClass jeVisClass;

    private String jEVisClassName;
    private String name;

    public MeterData(JEVisObject jeVisObject) {
        this.jeVisObject = jeVisObject;
        load();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void load() {
        try {
            name = jeVisObject.getName();
            jeVisClass = jeVisObject.getJEVisClass();
            jEVisClassName = jeVisClass.getName();
            for (JEVisAttribute jeVisAttribute : jeVisObject.getAttributes()) {
                if (jeVisAttribute.hasSample()) {
                    jeVisAttributeJEVisSampleMap.put(new JEVisTypeWrapper(jeVisAttribute.getType()), new SampleData(jeVisAttribute));
                } else {
                    jeVisAttributeJEVisSampleMap.put(new JEVisTypeWrapper(jeVisAttribute.getType()), new SampleData(jeVisAttribute));
                }

            }
        } catch (Exception e) {

        }
    }


    public Map<JEVisTypeWrapper, SampleData> getJeVisAttributeJEVisSampleMap() {
        return jeVisAttributeJEVisSampleMap;
    }

    public void setJeVisAttributeJEVisSampleMap(Map<JEVisTypeWrapper, SampleData> jeVisAttributeJEVisSampleMap) {
        this.jeVisAttributeJEVisSampleMap = jeVisAttributeJEVisSampleMap;
    }

    public JEVisClass getJeVisClass() {
        return jeVisClass;
    }

    public void setJeVisClass(JEVisClass jeVisClass) {
        this.jeVisClass = jeVisClass;
    }

    public JEVisObject getJeVisObject() {
        return jeVisObject;
    }

    public void setJeVisObject(JEVisObject jeVisObject) {
        this.jeVisObject = jeVisObject;
    }

    public String getjEVisClassName() {
        return jEVisClassName;
    }

    @Override
    public String toString() {
        return "MeterData{" +
                "jeVisObject=" + jeVisObject +
                ", name='" + name + '\'' +
                '}';
    }
}
