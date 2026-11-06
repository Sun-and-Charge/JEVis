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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.ws.json.JsonI18n;
import org.jevis.commons.ws.json.JsonObject;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Read-only {@link JEVisObject} of a {@link LocalDataSource}; behaves like {@code JEVisObjectWS}. Parents and children
 * come from the visible PARENT relationships, so objects the user may not read are skipped. All setters and write
 * operations throw {@link UnsupportedOperationException}.
 */
public class LocalObject implements JEVisObject {

    private static final Logger logger = LogManager.getLogger(LocalObject.class);
    private static final DateTimeFormatter DELETED_DTF = ISODateTimeFormat.dateTime();

    private final LocalDataSource ds;
    private final JsonObject json;
    private final DateTime deleteTS;
    private final List<JEVisEventListener> listeners = new CopyOnWriteArrayList<>();
    private volatile Map<String, JEVisAttribute> attributeByName = null;

    LocalObject(LocalDataSource ds, JsonObject json) {
        this.ds = ds;
        this.json = json;
        this.deleteTS = parseDeleteTS(json);
    }

    /**
     * Same as the client: the precise {@code deleted} timestamp, else the day-only {@code deleteTS}.
     */
    static DateTime parseDeleteTS(JsonObject json) {
        try {
            if (json.getDeleted() != null && !json.getDeleted().isEmpty()) {
                return DELETED_DTF.parseDateTime(json.getDeleted());
            }
            if (json.getDeleteTS() != null && !json.getDeleteTS().isEmpty()) {
                return ISODateTimeFormat.dateTimeParser().parseDateTime(json.getDeleteTS().trim().replace(' ', 'T'));
            }
        } catch (IllegalArgumentException ex) {
            logger.warn("Unreadable delete timestamp of object {}: {}", json.getId(), ex.getMessage());
            return new DateTime(0);
        }
        return null;
    }

    JsonObject getJson() {
        return json;
    }

    private boolean isLink() {
        return "Link".equals(json.getJevisClass());
    }

    // ---------------------------------------------------------------- names

    /**
     * The name in the language of the data source ({@link LocalDataSource#setLanguage(String)}), else the
     * untranslated name.
     */
    @Override
    public String getName() {
        String language = ds.getLanguage();
        if (language == null) {
            return json.getName();
        }
        return getLocalName(language);
    }

    @Override
    public String getLocalName(String key) {
        if (key == null || key.equalsIgnoreCase("default")) {
            return json.getName();
        }
        for (JsonI18n i18n : json.getI18n()) {
            if (key.equals(i18n.getKey())) {
                return i18n.getValue();
            }
        }
        return json.getName();
    }

    @Override
    public Map<String, String> getLocalNameList() {
        Map<String, String> names = new HashMap<>();
        for (JsonI18n i18n : json.getI18n()) {
            names.put(i18n.getKey(), i18n.getValue());
        }
        return names;
    }

    @Override
    public void setLocalName(String key, String name) {
        throw LocalDataSource.readOnly("setLocalName");
    }

    @Override
    public void setLocalNames(Map<String, String> translation) {
        throw LocalDataSource.readOnly("setLocalNames");
    }

    @Override
    public void setName(String name) {
        throw LocalDataSource.readOnly("setName");
    }

    @Override
    public Long getID() {
        return json.getId();
    }

    // ---------------------------------------------------------------- class

    /**
     * The class of the object; for a link the class of the linked object (like the client).
     */
    @Override
    public JEVisClass getJEVisClass() {
        if (isLink()) {
            JEVisObject linked = getLinkedObject();
            if (linked != null) {
                try {
                    return linked.getJEVisClass();
                } catch (JEVisException ex) {
                    logger.debug("Could not get class of linked object", ex);
                }
            }
        }
        return ds.getJEVisClass(json.getJevisClass());
    }

    @Override
    public String getJEVisClassName() {
        return json.getJevisClass();
    }

    // ---------------------------------------------------------------- tree

    /**
     * Ends of the visible PARENT relationships starting at this object.
     */
    @Override
    public List<JEVisObject> getParents() {
        List<JEVisObject> parents = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships()) {
            if (rel.getStartID() == getID() && isType(rel, JEVisConstants.ObjectRelationship.PARENT)) {
                JEVisObject parent = ds.getObject(rel.getEndID());
                if (parent != null && !parents.contains(parent)) {
                    parents.add(parent);
                }
            }
        }
        return parents;
    }

    @Override
    public JEVisObject getParent() {
        List<JEVisObject> parents = getParents();
        return parents.isEmpty() ? null : parents.get(0);
    }

    /**
     * Starts of the visible PARENT relationships ending at this object.
     */
    @Override
    public List<JEVisObject> getChildren() {
        List<JEVisObject> children = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships()) {
            if (rel.getEndID() == getID() && isType(rel, JEVisConstants.ObjectRelationship.PARENT)) {
                JEVisObject child = ds.getObject(rel.getStartID());
                if (child != null && !children.contains(child)) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    /**
     * Children whose class is {@code jclass} or inherits from it. Like the client the {@code inherit} flag is not
     * evaluated: children of subclasses are always included.
     */
    @Override
    public List<JEVisObject> getChildren(JEVisClass jclass, boolean inherit) {
        List<JEVisObject> filtered = new ArrayList<>();
        if (jclass == null) {
            return filtered;
        }
        for (JEVisObject child : getChildren()) {
            try {
                JEVisClass childClass = child.getJEVisClass();
                if (childClass == null) {
                    continue;
                }
                if (childClass.equals(jclass) || inheritanceClasses(childClass).contains(jclass)) {
                    filtered.add(child);
                }
            } catch (JEVisException ex) {
                logger.error("Could not check class of {}", child.getID(), ex);
            }
        }
        return filtered;
    }

    /**
     * All super classes of the class (transitively).
     */
    static Set<JEVisClass> inheritanceClasses(JEVisClass jclass) throws JEVisException {
        Set<JEVisClass> classes = new LinkedHashSet<>();
        JEVisClass current = jclass.getInheritance();
        while (current != null && classes.add(current)) {
            current = current.getInheritance();
        }
        return classes;
    }

    private static boolean isType(JEVisRelationship rel, int type) {
        try {
            return rel.isType(type);
        } catch (JEVisException ex) {
            return false;
        }
    }

    // ---------------------------------------------------------------- attributes

    /**
     * The attributes of the object; for a link the attributes of the linked object (like the client).
     */
    @Override
    public List<JEVisAttribute> getAttributes() {
        if (isLink()) {
            JEVisObject linked = getLinkedObject();
            if (linked != null) {
                return ds.getAttributes(linked.getID());
            }
        }
        return ds.getAttributes(getID());
    }

    @Override
    public JEVisAttribute getAttribute(JEVisType type) throws JEVisException {
        if (type == null) {
            return null;
        }
        for (JEVisAttribute att : getAttributes()) {
            if (att.getName().equals(type.getName())) {
                return att;
            }
        }
        return null;
    }

    /**
     * Case-insensitive like the client.
     */
    @Override
    public JEVisAttribute getAttribute(String type) {
        if (type == null) {
            return null;
        }
        Map<String, JEVisAttribute> map = attributeByName;
        if (map == null) {
            List<JEVisAttribute> atts = getAttributes();
            map = new HashMap<>(atts.size() * 2);
            synchronized (atts) {
                for (JEVisAttribute att : atts) {
                    map.put(att.getName().toLowerCase(Locale.ROOT), att);
                }
            }
            attributeByName = map;
        }
        return map.get(type.toLowerCase(Locale.ROOT));
    }

    // ---------------------------------------------------------------- relationships

    /**
     * The end of the first visible LINK relationship starting at this object.
     */
    @Override
    public JEVisObject getLinkedObject() {
        for (JEVisRelationship rel : getRelationships(JEVisConstants.ObjectRelationship.LINK, JEVisConstants.Direction.FORWARD)) {
            JEVisObject original = ds.getObject(rel.getEndID());
            if (original != null) {
                return original;
            }
        }
        return null;
    }

    @Override
    public List<JEVisRelationship> getRelationships() {
        return ds.getRelationships(getID());
    }

    @Override
    public List<JEVisRelationship> getRelationships(int type) {
        List<JEVisRelationship> list = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships()) {
            if (isType(rel, type)) {
                list.add(rel);
            }
        }
        return list;
    }

    @Override
    public List<JEVisRelationship> getRelationships(int type, int direction) {
        List<JEVisRelationship> list = new ArrayList<>();
        for (JEVisRelationship rel : getRelationships(type)) {
            if (direction == JEVisConstants.Direction.FORWARD && rel.getStartID() == getID()) {
                list.add(rel);
            } else if (direction == JEVisConstants.Direction.BACKWARD && rel.getEndID() == getID()) {
                list.add(rel);
            }
        }
        return list;
    }

    /**
     * Valid child classes of the class; unique classes only while no such child exists (like the client).
     */
    @Override
    public List<JEVisClass> getAllowedChildrenClasses() throws JEVisException {
        List<JEVisClass> allowed = new ArrayList<>();
        JEVisClass jclass = getJEVisClass();
        if (jclass == null) {
            return allowed;
        }
        for (JEVisClass child : jclass.getValidChildren()) {
            if (!child.isUnique() || getChildren(child, false).isEmpty()) {
                allowed.add(child);
            }
        }
        return allowed;
    }

    /**
     * Same rule as the client: the class must be allowed under the class of the other object; a unique class only
     * once below the same parent, except a directory below a directory of the same class.
     */
    @Override
    public boolean isAllowedUnder(JEVisObject otherObject) throws JEVisException {
        JEVisClass jclass = getJEVisClass();
        if (jclass == null || otherObject == null || otherObject.getJEVisClass() == null) {
            return false;
        }
        if (!jclass.isAllowedUnder(otherObject.getJEVisClass())) {
            return false;
        }
        if (!jclass.isUnique()) {
            return true;
        }
        boolean isAlreadyUnderParent = false;
        for (JEVisObject child : otherObject.getChildren()) {
            if (getJEVisClassName().equals(child.getJEVisClassName())) {
                isAlreadyUnderParent = true;
            }
        }
        if (!isAlreadyUnderParent) {
            return true;
        }
        JEVisClass directory = ds.getJEVisClass("Directory");
        boolean isDirectory = directory != null && directory.getHeirs().contains(jclass);
        return isDirectory && getJEVisClassName().equals(otherObject.getJEVisClassName());
    }

    @Override
    public boolean isPublic() {
        return json.getisPublic();
    }

    @Override
    public void setIsPublic(boolean ispublic) {
        throw LocalDataSource.readOnly("setIsPublic");
    }

    // ---------------------------------------------------------------- recycle bin

    @Override
    public DateTime getDeleteTS() {
        return deleteTS;
    }

    @Override
    public void setDeleteTS(DateTime ts) {
        throw LocalDataSource.readOnly("setDeleteTS");
    }

    /**
     * Name of the user who deleted the object, if it is in the recycle bin and the user object is readable.
     */
    @Override
    public String getDeletedBy() {
        if (deleteTS == null) {
            return null;
        }
        if (json.getDeletedByName() != null) {
            return json.getDeletedByName();
        }
        if (json.getDeletedBy() != null) {
            JEVisObject deletedBy = ds.getObject(json.getDeletedBy());
            return deletedBy != null ? deletedBy.getName() : null;
        }
        return null;
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
    public JEVisEventListener[] getEventListener() {
        return listeners.toArray(new JEVisEventListener[0]);
    }

    @Override
    public void notifyListeners(JEVisEvent event) {
        for (JEVisEventListener listener : listeners) {
            listener.fireEvent(event);
        }
    }

    // ---------------------------------------------------------------- write operations

    @Override
    public boolean delete() {
        throw LocalDataSource.readOnly("delete");
    }

    @Override
    public JEVisObject buildObject(String name, JEVisClass type) {
        throw LocalDataSource.readOnly("buildObject");
    }

    @Override
    public JEVisRelationship buildRelationship(JEVisObject obj, int type, int direction) {
        throw LocalDataSource.readOnly("buildRelationship");
    }

    @Override
    public void deleteRelationship(JEVisRelationship rel) {
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
    public int compareTo(JEVisObject o) {
        return getID().compareTo(o.getID());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof JEVisObject) {
            Long id = ((JEVisObject) o).getID();
            return id != null && id.equals(getID());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getID().hashCode();
    }

    @Override
    public String toString() {
        return "LocalObject [ id: '" + getID() + "' name: '" + json.getName() + "' jclass: '" + getJEVisClassName() + "']";
    }
}
