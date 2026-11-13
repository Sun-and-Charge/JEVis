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
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Fix {@value SetupPlan#FIX_REMAP_DASHBOARD_ROWS} of the setup plan: sets the object id of data rows of dashboard
 * widgets ({@code widget[uuid].dataHandler.chartModels[*].chartData[row].id}, rows counted over all chart models) from
 * a wrong id to the right one. A row is only changed if it still holds the wrong id and the new target passes the
 * check of the caller; a row that already holds the new id counts as done. Everything else of the file is kept.
 */
final class DashboardRowRemap {

    private DashboardRowRemap() {
    }

    static final class Result {
        /**
         * the dashboard file with the changed rows, null if nothing is to be changed
         */
        final ObjectNode updated;
        final List<String> changed = new ArrayList<>();
        final List<String> done = new ArrayList<>();
        final List<String> skipped = new ArrayList<>();

        private Result(ObjectNode updated) {
            this.updated = updated;
        }
    }

    /**
     * @param root     the dashboard file
     * @param fix      the fix of the plan ({@code rows}: {@code widget}, {@code row}, {@code from}, {@code to},
     *                 {@code toPath})
     * @param targetOk the new target passes the check (exists, class, path, below the organisation)
     */
    static Result apply(JsonNode root, JsonNode fix, Predicate<JsonNode> targetOk) {
        if (root == null || !root.isObject()) {
            Result r = new Result(null);
            r.skipped.add("the dashboard file is no JSON object");
            return r;
        }
        ObjectNode copy = root.deepCopy();
        JsonNode widgets = copy.has("widget") ? copy.get("widget") : copy.get("Widget");
        Result result = new Result(copy);
        for (JsonNode r : fix.path("rows")) {
            String uuid = r.path("widget").asText();
            int index = r.path("row").asInt();
            long from = r.path("from").asLong();
            long to = r.path("to").asLong();
            String text = "widget " + uuid + " row " + index + " (" + r.path("toPath").asText() + ")";
            ObjectNode row = row(widgets, uuid, index);
            if (row == null) {
                result.skipped.add(text + ": no such widget row");
                continue;
            }
            long current = row.path("id").asLong(-1);
            if (current == to) {
                result.done.add(text);
                continue;
            }
            if (current != from) {
                result.skipped.add(text + ": now " + current + ", not " + from);
                continue;
            }
            if (!targetOk.test(r)) {
                result.skipped.add(text + ": the target " + to + " does not match");
                continue;
            }
            row.put("id", to);
            result.changed.add(text + ": " + from + " → " + to);
        }
        return result.changed.isEmpty() ? copyWithout(result) : result;
    }

    private static Result copyWithout(Result r) {
        Result empty = new Result(null);
        empty.done.addAll(r.done);
        empty.skipped.addAll(r.skipped);
        return empty;
    }

    private static ObjectNode row(JsonNode widgets, String uuid, int index) {
        if (widgets == null || !widgets.isArray()) return null;
        for (JsonNode w : widgets) {
            if (!uuid.equals(w.path("uuid").asText())) continue;
            int i = 0;
            for (JsonNode model : w.path("dataHandler").path("chartModels")) {
                for (JsonNode data : model.path("chartData")) {
                    if (i++ == index) return data.isObject() ? (ObjectNode) data : null;
                }
            }
            return null;
        }
        return null;
    }
}
