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

import java.util.*;

/**
 * An ordered list of changes (create object, add relationship, remove relationship). Plans are computed without
 * touching the server, shown as preview and then executed with {@link RightsPlanExecutor}. Objects that do not exist
 * yet are referenced by {@link Ref}s that get their id when they are created.
 */
public class RightsPlan {

    private final List<Action> actions = new ArrayList<>();
    private final Set<String> relationshipKeys = new HashSet<>();
    private final List<String> warnings = new ArrayList<>();

    public Ref existing(RightsModel.Node node) {
        if (node == null) throw new IllegalArgumentException("node must not be null");
        return new Ref(node.id, node.name, node.className, null, Collections.emptyMap());
    }

    /**
     * Plans the creation of an object. All creations are executed before all relationship changes.
     */
    public Ref create(Ref parent, String name, String className, Map<String, String> localNames, Category category) {
        Ref ref = new Ref(null, name, className, parent, localNames == null ? Collections.emptyMap() : localNames);
        actions.add(new Action(ActionType.CREATE_OBJECT, category, ref, parent, null, 0));
        return ref;
    }

    /**
     * Plans a new relationship; the same relationship is planned only once.
     *
     * @return true if it was added to the plan
     */
    public boolean addRelationship(Ref from, Ref to, int type, Category category) {
        String key = "+" + from.key() + ">" + to.key() + ":" + type;
        if (!relationshipKeys.add(key)) return false;
        actions.add(new Action(ActionType.ADD_RELATIONSHIP, category, null, from, to, type));
        return true;
    }

    public boolean removeRelationship(Ref from, Ref to, int type, Category category) {
        String key = "-" + from.key() + ">" + to.key() + ":" + type;
        if (!relationshipKeys.add(key)) return false;
        actions.add(new Action(ActionType.REMOVE_RELATIONSHIP, category, null, from, to, type));
        return true;
    }

    public boolean plansRelationship(Ref from, Ref to, int type) {
        return relationshipKeys.contains("+" + from.key() + ">" + to.key() + ":" + type);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    /**
     * @return all actions, object creations first (in planning order), then relationship changes
     */
    public List<Action> getActions() {
        List<Action> result = new ArrayList<>();
        for (Action action : actions) if (action.type == ActionType.CREATE_OBJECT) result.add(action);
        for (Action action : actions) if (action.type != ActionType.CREATE_OBJECT) result.add(action);
        return result;
    }

    public void addAll(RightsPlan other) {
        for (Action action : other.actions) {
            if (action.type == ActionType.CREATE_OBJECT) {
                actions.add(action);
            } else {
                String key = (action.type == ActionType.ADD_RELATIONSHIP ? "+" : "-") + action.from.key() + ">" + action.to.key() + ":" + action.relType;
                if (relationshipKeys.add(key)) actions.add(action);
            }
        }
        warnings.addAll(other.warnings);
    }

    public boolean isEmpty() {
        return actions.isEmpty();
    }

    public int count(ActionType type, Category category) {
        int count = 0;
        for (Action action : actions) {
            if (action.type == type && (category == null || action.category == category)) count++;
        }
        return count;
    }

    public enum ActionType {
        CREATE_OBJECT, ADD_RELATIONSHIP, REMOVE_RELATIONSHIP
    }

    /**
     * What an action is about, for grouping in the preview.
     */
    public enum Category {
        DIRECTORY, GROUP, ROLE, USER, OWNER, ENTRY_POINT, ROLE_RIGHT, ROLE_MEMBER, MEMBERSHIP
    }

    /**
     * Reference to an existing object (id known) or to an object created by the plan (id set on execution).
     */
    public static class Ref {
        private static int counter = 0;
        private final String name;
        private final String className;
        private final Ref parent;
        private final Map<String, String> localNames;
        private final String plannedKey;
        private Long id;

        Ref(Long id, String name, String className, Ref parent, Map<String, String> localNames) {
            this.id = id;
            this.name = name;
            this.className = className;
            this.parent = parent;
            this.localNames = localNames;
            synchronized (Ref.class) {
                this.plannedKey = id == null ? "new" + (++counter) : null;
            }
        }

        public Long getId() {
            return id;
        }

        void setId(Long id) {
            this.id = id;
        }

        /**
         * @return true if the object is created by the plan
         */
        public boolean isPlanned() {
            return plannedKey != null;
        }

        public String getName() {
            return name;
        }

        public String getClassName() {
            return className;
        }

        public Ref getParent() {
            return parent;
        }

        public Map<String, String> getLocalNames() {
            return localNames;
        }

        String key() {
            return plannedKey != null ? plannedKey : String.valueOf(id);
        }

        @Override
        public String toString() {
            return isPlanned() ? name + " [new]" : name + " [" + id + "]";
        }
    }

    /**
     * One change of a plan.
     */
    public static class Action {
        private final ActionType type;
        private final Category category;
        private final Ref object;
        private final Ref from;
        private final Ref to;
        private final int relType;
        private boolean done;
        private String error;

        Action(ActionType type, Category category, Ref object, Ref from, Ref to, int relType) {
            this.type = type;
            this.category = category;
            this.object = object;
            this.from = from;
            this.to = to;
            this.relType = relType;
        }

        public ActionType getType() {
            return type;
        }

        public Category getCategory() {
            return category;
        }

        /**
         * @return the created object (CREATE_OBJECT)
         */
        public Ref getObject() {
            return object;
        }

        /**
         * @return the parent (CREATE_OBJECT) or the start object of the relationship
         */
        public Ref getFrom() {
            return from;
        }

        public Ref getTo() {
            return to;
        }

        public int getRelType() {
            return relType;
        }

        public boolean isDone() {
            return done;
        }

        void setDone(boolean done) {
            this.done = done;
        }

        public String getError() {
            return error;
        }

        void setError(String error) {
            this.error = error;
        }

        @Override
        public String toString() {
            switch (type) {
                case CREATE_OBJECT:
                    return "create " + object.getClassName() + " '" + object.getName() + "' below " + from;
                case ADD_RELATIONSHIP:
                    return "add " + from + " -" + relType + "-> " + to;
                default:
                    return "remove " + from + " -" + relType + "-> " + to;
            }
        }
    }
}
