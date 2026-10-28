/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.dimpex;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DimpexObject {

    private String uid = UUID.randomUUID().toString();
    private String name = "NO NAME";
    private String jclass = "";
    private String action = Action.ADD.name();
    private boolean isPublic = false;
    private List<DimpexObject> children = new ArrayList<>();
    private List<DimpexAttribute> attributes = new ArrayList<>();

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJclass() {
        return jclass;
    }

    public void setJclass(String jclass) {
        this.jclass = jclass;
    }

    public List<DimpexObject> getChildren() {
        return children;
    }

    public void setChildren(List<DimpexObject> children) {
        this.children = children;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public List<DimpexAttribute> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<DimpexAttribute> attributes) {
        this.attributes = attributes;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public void setPublic(boolean aPublic) {
        isPublic = aPublic;
    }

    public enum Action {
        DELETE, ADD, UPDATE
    }
}
