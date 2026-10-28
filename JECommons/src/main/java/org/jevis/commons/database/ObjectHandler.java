/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.database;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;

import java.util.List;

/**
 * @author broder
 */
public class ObjectHandler {
    private static final Logger logger = LogManager.getLogger(ObjectHandler.class);

    private final JEVisDataSource dataSource;

    public ObjectHandler(JEVisDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void printObject(long l) {
        try {
            JEVisObject jevisObject = dataSource.getObject(l);
            logger.info(jevisObject.getName());
            for (JEVisAttribute attr : jevisObject.getAttributes()) {
                Object attrValue = null;
                if (attr.getLatestSample() != null) {
                    attrValue = attr.getLatestSample().getValue();
                }
                logger.info(attr.getName() + "," + attrValue);
            }
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public JEVisObject getFirstParent(JEVisObject calcObject) {
        JEVisObject parent = null;
        try {
            if (!calcObject.getParents().isEmpty()) {
                parent = calcObject.getParents().get(0);
            }
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
        return parent;
    }

    public JEVisObject getObject(long l) {
        JEVisObject object = null;
        try {
            object = dataSource.getObject(l);
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
        return object;
    }

    public JEVisObject buildObject(JEVisObject parentObject, String typeName, String objectName) {
        JEVisObject childObject = null;
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(typeName);
            childObject = parentObject.buildObject(objectName, jeVisClass);
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
        return childObject;
    }

    public void deleteChildren(long l) {
        try {
            JEVisObject object = dataSource.getObject(l);
            List<JEVisObject> children = object.getChildren();
            for (JEVisObject obj : children) {
            }
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

}
