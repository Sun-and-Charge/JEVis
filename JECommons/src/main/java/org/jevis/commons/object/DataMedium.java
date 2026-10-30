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
import org.jevis.api.JEVisSample;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Medium of a data object, e.g. for a cost breakdown by medium (upstream issue #1370).
 * <p>
 * The medium is read from the optional "Medium" attribute of the data object. If it is empty, it is taken from the
 * parent data object (e.g. for Clean Data), then from the measurement instrument whose "Online ID" points to the
 * data object, and finally from a measurement instrument above it in the tree.
 */
public enum DataMedium {
    ELECTRICITY, HEAT, COLD, GAS, WATER, STEAM, COMPRESSED_AIR, AIR, NITROGEN, OIL, OTHER;

    public static final String ATTRIBUTE_NAME = "Medium";
    private static final String MEASUREMENT_INSTRUMENT = "Measurement Instrument";
    private static final String ONLINE_ID = "Online ID";
    private static final Logger logger = LogManager.getLogger(DataMedium.class);

    /**
     * @return the medium for a measurement instrument class name, or null if the class does not define one
     */
    public static DataMedium fromMeterClass(String className) {
        if (className == null) {
            return null;
        }
        switch (className) {
            case "Electricity Measurement Instrument":
                return ELECTRICITY;
            case "Heat Measurement Instrument":
                return HEAT;
            case "Gas Measurement Instrument":
                return GAS;
            case "Water Measurement Instrument":
                return WATER;
            case "Compressed-Air Measurement Instrument":
                return COMPRESSED_AIR;
            case "Air Measurement Instrument":
                return AIR;
            case "Nitrogen Measurement Instrument":
                return NITROGEN;
            default:
                return null;
        }
    }

    /**
     * @return the medium for an attribute value, or null if it is empty or unknown
     */
    public static DataMedium parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return DataMedium.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Resolves the medium of a data object.
     *
     * @return the medium, or null if it cannot be determined
     */
    public static DataMedium of(JEVisObject dataObject) {
        return of(dataObject, null);
    }

    /**
     * Same as {@link #of(JEVisObject)}, with a map of data object id to meter class name from
     * {@link #meterClassesByOnlineId(JEVisDataSource)} so that many objects can be resolved without reading all
     * meters each time.
     */
    public static DataMedium of(JEVisObject dataObject, Map<Long, String> meterClassesByOnlineId) {
        try {
            JEVisObject current = dataObject;
            Map<Long, String> meters = meterClassesByOnlineId;
            for (int depth = 0; current != null && depth < 3; depth++) {
                DataMedium own = fromAttribute(current);
                if (own != null) {
                    return own;
                }

                if (meters == null) {
                    meters = meterClassesByOnlineId(current.getDataSource());
                }
                DataMedium meter = fromMeterClass(meters.get(current.getID()));
                if (meter != null) {
                    return meter;
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
                DataMedium meter = fromMeterClass(parent.getJEVisClassName());
                if (meter != null) {
                    return meter;
                }
                parent = firstParent(parent);
            }
        } catch (Exception e) {
            logger.warn("Could not determine medium of object {}", dataObject, e);
        }
        return null;
    }

    /**
     * @return map of the data object id an "Online ID" points to, to the class name of the measurement instrument
     */
    public static Map<Long, String> meterClassesByOnlineId(JEVisDataSource ds) {
        Map<Long, String> result = new HashMap<>();
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
                    result.put(id, meter.getJEVisClassName());
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

    private static DataMedium fromAttribute(JEVisObject object) throws Exception {
        JEVisAttribute attribute = object.getAttribute(ATTRIBUTE_NAME);
        if (attribute != null && attribute.hasSample()) {
            JEVisSample sample = attribute.getLatestSample();
            return sample != null ? parse(sample.getValueAsString()) : null;
        }
        return null;
    }

    private static boolean isDataObject(JEVisObject object) throws Exception {
        return object.getAttribute("Value") != null;
    }

    private static JEVisObject firstParent(JEVisObject object) throws Exception {
        List<JEVisObject> parents = object.getParents();
        return parents == null || parents.isEmpty() ? null : parents.get(0);
    }
}
