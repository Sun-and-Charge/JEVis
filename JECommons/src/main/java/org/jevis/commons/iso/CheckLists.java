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
package org.jevis.commons.iso;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The check lists of actions and nonconformities (key "Check List" of their JSON) with the keys of the web API.
 * Booleans are given as stored ({@code false} if missing, like the defaults of the Control Center).
 */
public final class CheckLists {

    /**
     * Stored key → web API key of the action check list (all booleans except "New EnPI").
     */
    static final String[][] ACTION_KEYS = {
            {"Process Document", "processDocument"},
            {"isNeedWorkInstruction", "workInstruction"},
            {"Test Instruction", "testInstruction"},
            {"Drawing", "drawing"},
            {"Other", "other"},
            {"Additional Action", "additionalAction"},
            {"Affect Others", "affectsOthers"},
            {"Consumption Documented", "consumptionDocumented"},
            {"Target Reached", "targetReached"},
            {"Document Correction Text", "documentCorrectionText"},
            {"Document Correction", "documentCorrection"},
            {"Additional Meter", "additionalMeter"},
    };
    static final String ACTION_NEW_ENPI = "New EnPI";

    /**
     * Stored key → web API key of the nonconformity check list.
     */
    static final String[][] NONCONFORMITY_KEYS = {
            {"Immediate Action Required", "immediateActionRequired"},
            {"Effect on ongoing Processes", "effectOnOngoingProcesses"},
            {"Routinely Affected", "routinelyAffected"},
            {"Employee Trained", "employeeTrained"},
            {"Documents Changes Needed", "documentsChangesNeeded"},
            {"Process Instructions", "processInstructions"},
            {"Work Instructions", "workInstructions"},
            {"Test Instructions", "testInstructions"},
            {"Design", "design"},
            {"Model", "model"},
            {"Miscellaneous", "miscellaneous"},
            {"Metrics", "metrics"},
    };

    private CheckLists() {
    }

    /**
     * @param stored the stored check list (may be null)
     * @return the action check list with the booleans and {@code newEnPI} (text)
     */
    public static Map<String, Object> action(Map<String, Object> stored) {
        Map<String, Object> json = booleans(stored, ACTION_KEYS);
        Object newEnPI = stored != null ? stored.get(ACTION_NEW_ENPI) : null;
        json.put("newEnPI", newEnPI != null ? String.valueOf(newEnPI) : "");
        return json;
    }

    /**
     * @param stored the stored check list (may be null)
     * @return the nonconformity check list (booleans)
     */
    public static Map<String, Object> nonconformity(Map<String, Object> stored) {
        return booleans(stored, NONCONFORMITY_KEYS);
    }

    private static Map<String, Object> booleans(Map<String, Object> stored, String[][] keys) {
        Map<String, Object> json = new LinkedHashMap<>();
        for (String[] key : keys) {
            json.put(key[1], bool(stored != null ? stored.get(key[0]) : null));
        }
        return json;
    }

    static boolean bool(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean(((String) value).trim());
        if (value instanceof Number) return ((Number) value).doubleValue() != 0;
        return false;
    }
}
