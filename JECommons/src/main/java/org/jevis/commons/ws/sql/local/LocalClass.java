/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.local;

import org.jevis.api.*;
import org.jevis.commons.ws.json.JsonClassRelationship;
import org.jevis.commons.ws.json.JsonJEVisClass;
import org.jevis.commons.ws.json.JsonType;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Read-only {@link JEVisClass} from the class definition files of the web service; behaves like
 * {@code JEVisClassWS}. The wrapped definition is shared by all requests and is never modified.
 */
public class LocalClass implements JEVisClass {

    private final LocalDataSource ds;
    private final JsonJEVisClass json;
    private final List<JEVisEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile List<JEVisType> types;
    private volatile List<JEVisClassRelationship> relationships;

    LocalClass(LocalDataSource ds, JsonJEVisClass json) {
        this.ds = ds;
        this.json = json;
    }

    JsonJEVisClass getJson() {
        return json;
    }

    @Override
    public String getName() {
        return json.getName();
    }

    @Override
    public String getDescription() {
        return json.getDescription();
    }

    /**
     * The icon file of the class below the class directory, {@code null} if there is none.
     */
    @Override
    public BufferedImage getIcon() {
        return ds.getClassIcon(getName());
    }

    /**
     * The types sorted by GUI position, like the client.
     */
    @Override
    public List<JEVisType> getTypes() {
        List<JEVisType> list = types;
        if (list == null) {
            list = new ArrayList<>();
            if (json.getTypes() != null) {
                for (JsonType type : json.getTypes()) {
                    list.add(new LocalType(ds, type, getName()));
                }
            }
            list.sort(Comparator.comparingInt(LocalClass::guiPosition));
            list = Collections.unmodifiableList(list);
            types = list;
        }
        return list;
    }

    private static int guiPosition(JEVisType type) {
        try {
            return type.getGUIPosition();
        } catch (JEVisException ex) {
            return 0;
        }
    }

    @Override
    public JEVisType getType(String typename) throws JEVisException {
        for (JEVisType type : getTypes()) {
            if (type.getName().equals(typename)) {
                return type;
            }
        }
        return null;
    }

    /**
     * The end of the INHERIT relationship starting at this class.
     */
    @Override
    public JEVisClass getInheritance() throws JEVisException {
        for (JEVisClassRelationship rel : getRelationships()) {
            if (rel.isType(JEVisConstants.ClassRelationship.INHERIT) && getName().equals(rel.getStartName())) {
                return rel.getEnd();
            }
        }
        return null;
    }

    /**
     * All classes inheriting from this class, transitively.
     */
    @Override
    public List<JEVisClass> getHeirs() throws JEVisException {
        List<JEVisClass> heirs = new LinkedList<>();
        collectHeirs(this, heirs, new HashSet<String>());
        return heirs;
    }

    private static void collectHeirs(JEVisClass jclass, List<JEVisClass> heirs, Set<String> visited) throws JEVisException {
        if (!visited.add(jclass.getName())) {
            return;
        }
        for (JEVisClassRelationship rel : jclass.getRelationships(JEVisConstants.ClassRelationship.INHERIT, JEVisConstants.Direction.BACKWARD)) {
            JEVisClass heir = rel.getStart();
            if (heir != null) {
                heirs.add(heir);
                collectHeirs(heir, heirs, visited);
            }
        }
    }

    /**
     * Valid parents of the super class plus the OK_PARENT ends of this class and their heirs, like the client.
     */
    @Override
    public List<JEVisClass> getValidParents() throws JEVisException {
        List<JEVisClass> validParents = new LinkedList<>();
        JEVisClass inheritance = getInheritance();
        if (inheritance != null) {
            validParents.addAll(inheritance.getValidParents());
        }
        for (JEVisClassRelationship rel : getRelationships()) {
            if (rel.isType(JEVisConstants.ClassRelationship.OK_PARENT) && getName().equals(rel.getStartName())) {
                JEVisClass other = rel.getEnd();
                if (other == null) {
                    continue;
                }
                if (!validParents.contains(other)) {
                    validParents.add(other);
                }
                validParents.addAll(other.getHeirs());
            }
        }
        Collections.sort(validParents);
        return validParents;
    }

    /**
     * The OK_PARENT starts ending at this class; heirs of Directory may also contain themselves (like the client).
     */
    @Override
    public List<JEVisClass> getValidChildren() throws JEVisException {
        List<JEVisClass> validChildren = new LinkedList<>();
        for (JEVisClassRelationship rel : getRelationships()) {
            if (rel.isType(JEVisConstants.ClassRelationship.OK_PARENT) && getName().equals(rel.getEndName())) {
                JEVisClass other = rel.getStart();
                if (other != null && !validChildren.contains(other)) {
                    validChildren.add(other);
                }
            }
        }
        JEVisClass directory = ds.getJEVisClass("Directory");
        if (directory != null && directory.getHeirs().contains(this) && !validChildren.contains(this)) {
            validChildren.add(this);
        }
        Collections.sort(validChildren);
        return validChildren;
    }

    @Override
    public boolean isAllowedUnder(JEVisClass jevisClass) throws JEVisException {
        for (JEVisClass parent : getValidParents()) {
            if (parent.getName().equals(jevisClass.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isUnique() {
        return json.getUnique();
    }

    @Override
    public List<JEVisClassRelationship> getRelationships() {
        List<JEVisClassRelationship> list = relationships;
        if (list == null) {
            list = new ArrayList<>();
            if (json.getRelationships() != null) {
                for (JsonClassRelationship rel : json.getRelationships()) {
                    LocalClassRelationship local = new LocalClassRelationship(ds, rel);
                    if (!list.contains(local)) {
                        list.add(local);
                    }
                }
            }
            list = Collections.unmodifiableList(list);
            relationships = list;
        }
        return list;
    }

    @Override
    public List<JEVisClassRelationship> getRelationships(int type) throws JEVisException {
        List<JEVisClassRelationship> list = new LinkedList<>();
        for (JEVisClassRelationship rel : getRelationships()) {
            if (rel.isType(type)) {
                list.add(rel);
            }
        }
        return list;
    }

    @Override
    public List<JEVisClassRelationship> getRelationships(int type, int direction) throws JEVisException {
        List<JEVisClassRelationship> list = new LinkedList<>();
        for (JEVisClassRelationship rel : getRelationships(type)) {
            if (direction == JEVisConstants.Direction.FORWARD && getName().equals(rel.getStartName())) {
                list.add(rel);
            } else if (direction == JEVisConstants.Direction.BACKWARD && getName().equals(rel.getEndName())) {
                list.add(rel);
            }
        }
        return list;
    }

    // ---------------------------------------------------------------- events (in memory only)

    @Override
    public void addEventListener(JEVisEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(JEVisEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void notifyListeners(JEVisEvent event) {
        for (JEVisEventListener listener : listeners) {
            listener.fireEvent(event);
        }
    }

    // ---------------------------------------------------------------- write operations

    @Override
    public void setName(String name) {
        throw LocalDataSource.readOnly("setName");
    }

    @Override
    public void setIcon(BufferedImage icon) {
        throw LocalDataSource.readOnly("setIcon");
    }

    @Override
    public void setIcon(File icon) {
        throw LocalDataSource.readOnly("setIcon");
    }

    @Override
    public void setDescription(String description) {
        throw LocalDataSource.readOnly("setDescription");
    }

    @Override
    public JEVisType buildType(String name) {
        throw LocalDataSource.readOnly("buildType");
    }

    @Override
    public void setUnique(boolean unique) {
        throw LocalDataSource.readOnly("setUnique");
    }

    @Override
    public boolean delete() {
        throw LocalDataSource.readOnly("delete");
    }

    @Override
    public boolean deleteType(String type) {
        throw LocalDataSource.readOnly("deleteType");
    }

    @Override
    public JEVisClassRelationship buildRelationship(JEVisClass jclass, int type, int direction) {
        throw LocalDataSource.readOnly("buildRelationship");
    }

    @Override
    public void deleteRelationship(JEVisClassRelationship rel) {
        throw LocalDataSource.readOnly("deleteRelationship");
    }

    @Override
    public void commit() {
        throw LocalDataSource.readOnly("commit");
    }

    @Override
    public void rollBack() {
        throw LocalDataSource.readOnly("rollBack");
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public int compareTo(JEVisClass o) {
        try {
            return getName().compareTo(o.getName());
        } catch (JEVisException ex) {
            return 1;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JEVisClass) {
            try {
                return getName() != null && getName().equals(((JEVisClass) o).getName());
            } catch (JEVisException ex) {
                return false;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getName() != null ? getName().hashCode() : 0;
    }

    @Override
    public String toString() {
        return "LocalClass [" + getName() + "]";
    }
}
