/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo.costs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.function.Function;

/**
 * Fix {@value SetupPlan#FIX_REMOVE_DASHBOARD_WIDGET} of the setup plan: removes one widget from a dashboard file
 * ("Data Model File" as the Control Center writes it, widgets in {@code "widget"}), only if it still is the widget the
 * plan describes (uuid, type and the wrong target of the given class). Everything else of the file is kept as it is.
 */
final class DashboardWidgetFix {

    private DashboardWidgetFix() {
    }

    static final class Result {
        /**
         * the dashboard file without the widget, null if nothing is to be changed
         */
        final ObjectNode updated;
        /**
         * why nothing is changed, or what is removed
         */
        final String reason;

        private Result(ObjectNode updated, String reason) {
            this.updated = updated;
            this.reason = reason;
        }
    }

    /**
     * @param root    the dashboard file
     * @param fix     the fix of the plan ({@code widgetUuid}, {@code widgetType}, {@code targetField}, {@code target},
     *                {@code targetClass})
     * @param classOf object id → class name, null if the object does not exist
     */
    static Result apply(JsonNode root, JsonNode fix, Function<Long, String> classOf) {
        if (root == null || !root.isObject()) return new Result(null, "the dashboard file is no JSON object");
        String arrayName = root.has("widget") ? "widget" : root.has("Widget") ? "Widget" : null;
        JsonNode widgets = arrayName != null ? root.get(arrayName) : null;
        if (widgets == null || !widgets.isArray()) return new Result(null, "the dashboard file has no widget list");
        String uuid = fix.path("widgetUuid").asText();
        int index = -1;
        for (int i = 0; i < widgets.size(); i++) {
            if (uuid.equals(widgets.get(i).path("uuid").asText())) {
                if (index >= 0) return new Result(null, "widget " + uuid + " occurs more than once, not changed");
                index = i;
            }
        }
        if (index < 0) return new Result(null, "widget " + uuid + " is no longer in the dashboard (already removed)");
        JsonNode widget = widgets.get(index);
        String type = text(widget, "WidgetType", "widgetType");
        String expectedType = fix.path("widgetType").asText();
        if (!expectedType.equals(type)) {
            return new Result(null, "widget " + uuid + " is now of type '" + type + "', not '" + expectedType + "': not changed");
        }
        JsonNode target = widget;
        for (String part : fix.path("targetField").asText().split("\\.")) target = target.path(part);
        long expectedTarget = fix.path("target").asLong();
        if (target.asLong(-1) != expectedTarget) {
            return new Result(null, "widget " + uuid + " " + fix.path("targetField").asText() + " is now '" + target.asText()
                    + "', not " + expectedTarget + ": not changed");
        }
        String targetClass = classOf.apply(expectedTarget);
        String expectedClass = fix.path("targetClass").asText();
        if (!expectedClass.equals(targetClass)) {
            return new Result(null, "the target " + expectedTarget + " of widget " + uuid + " is "
                    + (targetClass == null ? "not found" : "of class '" + targetClass + "'") + ", not '" + expectedClass
                    + "': not changed");
        }
        ObjectNode copy = ((ObjectNode) root).deepCopy();
        ((ArrayNode) copy.get(arrayName)).remove(index);
        return new Result(copy, "remove widget " + uuid + " (" + type + ", '" + text(widget, "title", "Title")
                + "', " + fix.path("targetField").asText() + " = " + expectedTarget + ", a " + expectedClass + ")");
    }

    private static String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) return value.asText();
        }
        return null;
    }
}
