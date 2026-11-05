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

import java.util.HashMap;
import java.util.List;

/**
 * Executes a {@link RightsPlan} against a data source. Failed actions are logged and marked, the remaining actions
 * still run (an action whose objects could not be created is skipped).
 */
public class RightsPlanExecutor {

    private static final Logger logger = LogManager.getLogger(RightsPlanExecutor.class);
    private final JEVisDataSource ds;
    private long objectDelayMs = 500;
    private volatile boolean cancelled = false;

    public RightsPlanExecutor(JEVisDataSource ds) {
        this.ds = ds;
    }

    /**
     * Wait time after each created object, as the building template always did. Default 500 ms.
     */
    public RightsPlanExecutor setObjectDelayMs(long objectDelayMs) {
        this.objectDelayMs = objectDelayMs;
        return this;
    }

    public void cancel() {
        cancelled = true;
    }

    /**
     * @return counts of done and failed actions
     */
    public Result execute(RightsPlan plan, ProgressListener listener) throws InterruptedException {
        List<RightsPlan.Action> actions = plan.getActions();
        Result result = new Result();
        int index = 0;
        for (RightsPlan.Action action : actions) {
            if (cancelled || Thread.currentThread().isInterrupted()) {
                result.cancelled = true;
                break;
            }
            index++;
            try {
                switch (action.getType()) {
                    case CREATE_OBJECT:
                        createObject(action);
                        break;
                    case ADD_RELATIONSHIP:
                        addRelationship(action);
                        break;
                    case REMOVE_RELATIONSHIP:
                        removeRelationship(action);
                        break;
                }
                action.setDone(true);
                result.done++;
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                logger.error("Permission action failed: {}", action, e);
                action.setError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                result.failed++;
            }
            if (listener != null) listener.progress(index, actions.size(), action);
        }
        return result;
    }

    private void createObject(RightsPlan.Action action) throws JEVisException, InterruptedException {
        RightsPlan.Ref ref = action.getObject();
        Long parentId = action.getFrom().getId();
        if (parentId == null) throw new IllegalStateException("parent was not created: " + action.getFrom().getName());
        JEVisObject parent = ds.getObject(parentId);
        JEVisClass jClass = ds.getJEVisClass(ref.getClassName());
        if (parent == null || jClass == null) {
            throw new IllegalStateException("parent or class not found: " + parentId + " / " + ref.getClassName());
        }
        JEVisObject created = parent.buildObject(ref.getName(), jClass);
        if (!ref.getLocalNames().isEmpty()) {
            created.setLocalNames(new HashMap<>(ref.getLocalNames()));
        }
        created.commit();
        if (created.getID() == null) throw new IllegalStateException("object was not created: " + ref.getName());
        ref.setId(created.getID());
        if (objectDelayMs > 0) Thread.sleep(objectDelayMs);
    }

    private void addRelationship(RightsPlan.Action action) throws JEVisException {
        Long from = action.getFrom().getId();
        Long to = action.getTo().getId();
        if (from == null || to == null) throw new IllegalStateException("object was not created: " + action);
        JEVisRelationship rel = ds.buildRelationship(from, to, action.getRelType());
        if (rel == null) throw new IllegalStateException("relationship was not created: " + action);
    }

    private void removeRelationship(RightsPlan.Action action) throws JEVisException {
        Long from = action.getFrom().getId();
        Long to = action.getTo().getId();
        if (from == null || to == null) throw new IllegalStateException("unknown object: " + action);
        if (!ds.deleteRelationship(from, to, action.getRelType())) {
            throw new IllegalStateException("relationship was not removed: " + action);
        }
    }

    public interface ProgressListener {
        void progress(int done, int total, RightsPlan.Action action);
    }

    public static class Result {
        private int done;
        private int failed;
        private boolean cancelled;

        public int getDone() {
            return done;
        }

        public int getFailed() {
            return failed;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }
}
