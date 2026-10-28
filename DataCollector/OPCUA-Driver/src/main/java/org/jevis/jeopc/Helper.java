/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;


public class Helper {

    private final static Logger log = LogManager.getLogger(Helper.class.getName());

    /**
     * Returns the last value of a JEVis object attribute. Invalid values are set to null.
     *
     * @param jevisObject   JEVis Object used for getting attribute values
     * @param attributeName Name of the attribute
     * @return The attributes last value
     */
    public String getValue(JEVisObject jevisObject, String attributeName) {
        return getValue(jevisObject, attributeName, null, false);
    }

    /**
     * Returns the last value of a JEVis object attribute. Invalid sample values are replaced with default.
     *
     * @param jevisObject   JEVis Object used for getting attribute values
     * @param attributeName Name of the attribute
     * @param defaultValue  Optional default value
     * @return The attributes last value
     */
    public String getValue(JEVisObject jevisObject, String attributeName, String defaultValue) {
        return getValue(jevisObject, attributeName, defaultValue, true);
    }

    /**
     * Returns the last value of a JEVis object attribute
     *
     * @param jevisObject       JEVis Object used for getting attribute values
     * @param attributeName     Name of the attribute
     * @param defaultValue      Optional default value
     * @param replaceEmptyValue Optional replace the value with default if sample value is invalid
     * @return The attributes last value
     */
    public String getValue(JEVisObject jevisObject, String attributeName, String defaultValue, Boolean replaceEmptyValue) {

        // Set default value
        String value = defaultValue;

        try {
            JEVisAttribute attribute = jevisObject.getAttribute(attributeName);
            if (attribute != null) {
                JEVisSample sample;
                if (attribute.hasSample()) {
                    sample = attribute.getLatestSample();
                    if (sample != null) {
                        value = sample.getValueAsString();
                        if (value.isEmpty()) {
                            if (replaceEmptyValue) {
                                log.warn("Node '" + jevisObject.getName() + "' Attribute '"
                                        + attributeName + "' not specified. Replaced with default: "
                                        + defaultValue);
                                value = defaultValue;
                            } else {
                                log.warn("Node '" + jevisObject.getName() + "' Attribute '"
                                        + attributeName + "' not specified.");
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Error while getting attribute value " + attributeName + " from " + jevisObject.getName());
            log.debug(ex.getMessage());
        }
        log.debug("Config Device: {} Attribute: '{}' Value: '{}' Default: '{}'", jevisObject.getID(), attributeName, value, defaultValue);

        return value;
    }
}