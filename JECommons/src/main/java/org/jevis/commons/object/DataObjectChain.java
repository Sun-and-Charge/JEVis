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
package org.jevis.commons.object;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback chain for settings of a data row that can also be made on the measurement instrument, shared by
 * {@link DataMedium} (upstream issue #1370) and {@link ValueFormat} (upstream issue #1194).
 * <p>
 * The value is read from the data object itself, then from the measurement instrument whose "Online ID" points to it,
 * then the same for the parent data object (e.g. the Data of a Clean Data) and finally from a measurement instrument
 * above the data object in the tree.
 */
final class DataObjectChain {

    static final String MEASUREMENT_INSTRUMENT = "Measurement Instrument";
    static final String ONLINE_ID = "Online ID";
    private static final Logger logger = LogManager.getLogger(DataObjectChain.class);

    private DataObjectChain() {
    }

    /**
     * Reads a value from an object, null if it has none.
     */
    interface Reader<T> {
        T read(JEVisObject object) throws Exception;
    }

    /**
     * @param dataObject  the data object
     * @param own         value of a data object itself
     * @param byOnlineId  value of the measurement instrument whose "Online ID" points to the given data object
     * @param meterAbove  value of an object above the data object in the tree, if it is a measurement instrument
     * @return the first value found, or null
     */
    static <T> T resolve(JEVisObject dataObject, Reader<T> own, Reader<T> byOnlineId, Reader<T> meterAbove) throws Exception {
        JEVisObject current = dataObject;
        for (int depth = 0; current != null && depth < 3; depth++) {
            T value = own.read(current);
            if (value != null) {
                return value;
            }

            value = byOnlineId.read(current);
            if (value != null) {
                return value;
            }

            JEVisObject parent = firstParent(current);
            if (parent == null || !isDataObject(parent)) {
                break;
            }
            current = parent;
        }

        /* data objects below a measurement instrument */
        JEVisObject parent = firstParent(dataObject);
        for (int depth = 0; parent != null && depth < 10; depth++) {
            T value = meterAbove.read(parent);
            if (value != null) {
                return value;
            }
            parent = firstParent(parent);
        }
        return null;
    }

    /**
     * @return all measurement instruments by the id of the data object their "Online ID" points to
     */
    static Map<Long, JEVisObject> metersByOnlineId(JEVisDataSource ds) {
        Map<Long, JEVisObject> result = new HashMap<>();
        try {
            JEVisClass meterClass = ds.getJEVisClass(MEASUREMENT_INSTRUMENT);
            if (meterClass == null) {
                return result;
            }
            for (JEVisObject meter : ds.getObjects(meterClass, true)) {
                JEVisAttribute onlineId = meter.getAttribute(ONLINE_ID);
                if (onlineId == null || !onlineId.hasSample()) {
                    continue;
                }
                Long id = parseTargetId(onlineId.getLatestSample().getValueAsString());
                if (id != null) {
                    result.put(id, meter);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not read measurement instruments", e);
        }
        return result;
    }

    static Long parseTargetId(String target) {
        if (target == null) {
            return null;
        }
        String id = target.split("[:;]")[0].trim();
        try {
            return id.isEmpty() ? null : Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isDataObject(JEVisObject object) throws Exception {
        return object.getAttribute("Value") != null;
    }

    static JEVisObject firstParent(JEVisObject object) throws Exception {
        List<JEVisObject> parents = object.getParents();
        return parents == null || parents.isEmpty() ? null : parents.get(0);
    }
}
