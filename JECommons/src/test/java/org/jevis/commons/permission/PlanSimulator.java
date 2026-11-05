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

import org.jevis.api.JEVisConstants;

/**
 * Test helper: applies a plan to a {@link RightsModel} the way the executor applies it to the server.
 */
final class PlanSimulator {

    private static long nextId = 100000;

    private PlanSimulator() {
    }

    static void apply(RightsModel model, RightsPlan plan) {
        for (RightsPlan.Action action : plan.getActions()) {
            switch (action.getType()) {
                case CREATE_OBJECT:
                    RightsPlan.Ref ref = action.getObject();
                    long id = nextId++;
                    RightsModel.Node node = model.addObject(id, ref.getName(), ref.getClassName());
                    node.localNames.putAll(ref.getLocalNames());
                    model.addRelationship(id, action.getFrom().getId(), JEVisConstants.ObjectRelationship.PARENT);
                    ref.setId(id);
                    break;
                case ADD_RELATIONSHIP:
                    model.addRelationship(action.getFrom().getId(), action.getTo().getId(), action.getRelType());
                    break;
                case REMOVE_RELATIONSHIP:
                    model.removeRelationship(action.getFrom().getId(), action.getTo().getId(), action.getRelType());
                    break;
            }
        }
    }
}
