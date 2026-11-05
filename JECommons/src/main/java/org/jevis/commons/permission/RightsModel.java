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
package org.jevis.commons.permission;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;

import java.util.*;

/**
 * Read-only snapshot of objects and relationships that the permission tools (consistency check, building rights
 * template) work on. The analysis code only uses this model, so it can be tested without a server: tests fill it
 * with {@link #addObject} and {@link #addRelationship}; the Control Center fills it with
 * {@link #fromDataSource(JEVisDataSource)}.
 */
public class RightsModel {

    public static final String CLASS_USER = "User";
    public static final String CLASS_GROUP = "Group";
    public static final String CLASS_ROLE = "User Role";
    public static final String CLASS_BUILDING = "Building";
    public static final String CLASS_ORGANIZATION = "Organization";
    public static final String CLASS_ADMINISTRATION_DIRECTORY = "Administration Directory";
    private static final Logger logger = LogManager.getLogger(RightsModel.class);
    private final Map<Long, Node> nodes = new LinkedHashMap<>();
    private final Map<Long, List<Rel>> relsFrom = new HashMap<>();
    private final Map<Long, List<Rel>> relsTo = new HashMap<>();
    private final Map<Long, List<Long>> children = new HashMap<>();
    private final Set<String> classNames = new HashSet<>();

    /**
     * Reads all objects and relationships the current user can see. For users, the "Sys Admin" flag is read too.
     */
    public static RightsModel fromDataSource(JEVisDataSource ds) throws JEVisException {
        return fromDataSource(ds, true);
    }

    /**
     * @param readUserFlags read the "Sys Admin" attribute of each user (needed by the consistency check and the user
     *                      migration, not by the building template for a new building)
     */
    public static RightsModel fromDataSource(JEVisDataSource ds, boolean readUserFlags) throws JEVisException {
        RightsModel model = new RightsModel();
        for (JEVisClass jClass : ds.getJEVisClasses()) {
            try {
                model.addClass(jClass.getName());
            } catch (Exception e) {
                logger.debug("Could not read class name", e);
            }
        }
        for (JEVisObject object : ds.getObjects()) {
            try {
                String className = object.getJEVisClassName();
                Node node = model.addObject(object.getID(), object.getName(), className);
                Map<String, String> localNames = object.getLocalNameList();
                if (localNames != null) node.localNames.putAll(localNames);
                node.deleted = object.getDeleteTS() != null;
                if (readUserFlags && CLASS_USER.equals(className)) {
                    node.sysAdmin = readSysAdmin(object);
                }
            } catch (Exception e) {
                logger.warn("Could not read object {}", object, e);
            }
        }
        for (JEVisRelationship rel : ds.getRelationships()) {
            try {
                model.addRelationship(rel.getStartID(), rel.getEndID(), rel.getType());
            } catch (Exception e) {
                logger.debug("Could not read relationship", e);
            }
        }
        return model;
    }

    private static boolean readSysAdmin(JEVisObject user) {
        try {
            JEVisAttribute attribute = user.getAttribute("Sys Admin");
            if (attribute != null && attribute.getLatestSample() != null) {
                Boolean value = attribute.getLatestSample().getValueAsBoolean();
                return value != null && value;
            }
        } catch (Exception e) {
            logger.debug("Could not read Sys Admin flag of {}", user.getID(), e);
        }
        return false;
    }

    public void addClass(String className) {
        classNames.add(className);
    }

    /**
     * @return true if the class exists. A model without any class information (tests) accepts every class.
     */
    public boolean hasClass(String className) {
        return classNames.isEmpty() || classNames.contains(className);
    }

    public Node addObject(long id, String name, String className) {
        Node node = new Node(id, name, className);
        nodes.put(id, node);
        return node;
    }

    public void addRelationship(long from, long to, int type) {
        if (has(from, to, type)) return;
        Rel rel = new Rel(from, to, type);
        relsFrom.computeIfAbsent(from, k -> new ArrayList<>()).add(rel);
        relsTo.computeIfAbsent(to, k -> new ArrayList<>()).add(rel);
        if (type == JEVisConstants.ObjectRelationship.PARENT) {
            Node child = nodes.get(from);
            if (child != null && child.parentId == null) {
                child.parentId = to;
            }
            children.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
        }
    }

    public void removeRelationship(long from, long to, int type) {
        relsFrom.getOrDefault(from, new ArrayList<>()).removeIf(rel -> rel.to == to && rel.type == type);
        relsTo.getOrDefault(to, new ArrayList<>()).removeIf(rel -> rel.from == from && rel.type == type);
    }

    public Node get(long id) {
        return nodes.get(id);
    }

    public Collection<Node> getObjects() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<Node> getObjectsOfClass(String className) {
        List<Node> result = new ArrayList<>();
        for (Node node : nodes.values()) {
            if (!node.deleted && className.equals(node.className)) result.add(node);
        }
        return result;
    }

    public Node getParent(long id) {
        Node node = nodes.get(id);
        if (node == null || node.parentId == null) return null;
        return nodes.get(node.parentId);
    }

    public List<Node> getChildren(long id) {
        List<Node> result = new ArrayList<>();
        for (Long childId : children.getOrDefault(id, Collections.emptyList())) {
            Node child = nodes.get(childId);
            if (child != null && !child.deleted) result.add(child);
        }
        result.sort(Comparator.comparingLong(n -> n.id));
        return result;
    }

    public List<Node> getChildren(long id, String className) {
        List<Node> result = new ArrayList<>();
        for (Node child : getChildren(id)) {
            if (className.equals(child.className)) result.add(child);
        }
        return result;
    }

    /**
     * @return all descendants (not the object itself), depth first; loops in the parent structure are ignored
     */
    public List<Node> getDescendants(long id) {
        List<Node> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        seen.add(id);
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(id);
        while (!stack.isEmpty()) {
            long current = stack.pop();
            List<Node> kids = getChildren(current);
            for (int i = kids.size() - 1; i >= 0; i--) {
                Node kid = kids.get(i);
                if (seen.add(kid.id)) {
                    result.add(kid);
                    stack.push(kid.id);
                }
            }
        }
        return result;
    }

    /**
     * @return true if {@code id} is {@code ancestorId} or lies below it
     */
    public boolean isInSubtree(long id, long ancestorId) {
        Set<Long> seen = new HashSet<>();
        Long current = id;
        while (current != null && seen.add(current)) {
            if (current == ancestorId) return true;
            Node node = nodes.get(current);
            current = node == null ? null : node.parentId;
        }
        return false;
    }

    public List<Rel> getRelationshipsFrom(long id, int type) {
        List<Rel> result = new ArrayList<>();
        for (Rel rel : relsFrom.getOrDefault(id, Collections.emptyList())) {
            if (rel.type == type) result.add(rel);
        }
        return result;
    }

    public List<Rel> getRelationshipsTo(long id, int type) {
        List<Rel> result = new ArrayList<>();
        for (Rel rel : relsTo.getOrDefault(id, Collections.emptyList())) {
            if (rel.type == type) result.add(rel);
        }
        return result;
    }

    public List<Rel> getRelationshipsFrom(long id) {
        return Collections.unmodifiableList(relsFrom.getOrDefault(id, Collections.emptyList()));
    }

    public List<Rel> getRelationshipsTo(long id) {
        return Collections.unmodifiableList(relsTo.getOrDefault(id, Collections.emptyList()));
    }

    public boolean has(long from, long to, int type) {
        for (Rel rel : relsFrom.getOrDefault(from, Collections.emptyList())) {
            if (rel.to == to && rel.type == type) return true;
        }
        return false;
    }

    /**
     * @return path of names from the root to the object, separated by " / "
     */
    public String getPath(long id) {
        LinkedList<String> parts = new LinkedList<>();
        Set<Long> seen = new HashSet<>();
        Long current = id;
        while (current != null && seen.add(current)) {
            Node node = nodes.get(current);
            if (node == null) break;
            parts.addFirst(node.name);
            current = node.parentId;
        }
        return String.join(" / ", parts);
    }

    /**
     * An object of the snapshot.
     */
    public static class Node {
        public final long id;
        public final String name;
        public final String className;
        public final Map<String, String> localNames = new HashMap<>();
        Long parentId;
        boolean deleted;
        boolean sysAdmin;

        Node(long id, String name, String className) {
            this.id = id;
            this.name = name == null ? "" : name;
            this.className = className == null ? "" : className;
        }

        public Long getParentId() {
            return parentId;
        }

        public boolean isDeleted() {
            return deleted;
        }

        public Node setDeleted(boolean deleted) {
            this.deleted = deleted;
            return this;
        }

        public boolean isSysAdmin() {
            return sysAdmin;
        }

        public Node setSysAdmin(boolean sysAdmin) {
            this.sysAdmin = sysAdmin;
            return this;
        }

        public Node localName(String language, String name) {
            localNames.put(language, name);
            return this;
        }

        /**
         * @return true if the name or one of the translated names equals one of the given names (ignoring case)
         */
        public boolean matchesName(Collection<String> names) {
            for (String candidate : names) {
                if (candidate == null) continue;
                if (candidate.trim().equalsIgnoreCase(name.trim())) return true;
                for (String local : localNames.values()) {
                    if (local != null && candidate.trim().equalsIgnoreCase(local.trim())) return true;
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return name + " [" + id + "]";
        }
    }

    /**
     * A relationship of the snapshot.
     */
    public static class Rel {
        public final long from;
        public final long to;
        public final int type;

        Rel(long from, long to, int type) {
            this.from = from;
            this.to = to;
            this.type = type;
        }

        @Override
        public String toString() {
            return from + " -" + type + "-> " + to;
        }
    }
}
