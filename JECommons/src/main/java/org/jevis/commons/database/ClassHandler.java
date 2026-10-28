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

import java.io.File;

/**
 * @author broder
 */
public class ClassHandler {

    private static final Logger logger = LogManager.getLogger(ClassHandler.class);
    private final JEVisDataSource dataSource;

    public ClassHandler(JEVisDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void renameClass(String report, String periodicReport) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(report);
            jeVisClass.setName(periodicReport);
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void printClass(String report) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(report);
            logger.info(jeVisClass.getName());
            for (JEVisType attribute : jeVisClass.getTypes()) {
                logger.info(attribute.getName());
            }
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void createClass(String className) {
        try {
            dataSource.buildClass(className);

        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void setInheritanceParent(String parentName, String childName) {
        try {
            JEVisClass jeVisParentClass = dataSource.getJEVisClass(parentName);
            JEVisClass jeVisChildClass = dataSource.getJEVisClass(childName);
            jeVisChildClass.buildRelationship(jeVisParentClass, JEVisConstants.ClassRelationship.INHERIT, JEVisConstants.Direction.FORWARD);
            jeVisChildClass.commit();

        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void removeType(String className, String typeName) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(className);
            for (JEVisType type : jeVisClass.getTypes()) {
                if (type.getName().equals(typeName)) {
                    type.delete();
                }
            }
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void addType(String className, String typeName, int primitivType) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(className);
            jeVisClass.buildType(typeName).setPrimitiveType(primitivType);
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void addType(String className, String typeName) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(className);
            jeVisClass.buildType(typeName);
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void setIcon(String className, String iconPath) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(className);
            jeVisClass.setIcon(new File(iconPath));
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void setOkParent(String okParentName, String childName) {
        try {
            JEVisClass jeVisParentClass = dataSource.getJEVisClass(okParentName);
            JEVisClass jeVisChildClass = dataSource.getJEVisClass(childName);
            jeVisChildClass.buildRelationship(jeVisParentClass, JEVisConstants.ClassRelationship.OK_PARENT, JEVisConstants.Direction.FORWARD);
            jeVisChildClass.commit();

        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }

    public void renameType(String className, String oldName, String newName) {
        try {
            JEVisClass jeVisClass = dataSource.getJEVisClass(className);
            JEVisType type = jeVisClass.getType(oldName);
            type.setName(newName);
            type.commit();
            jeVisClass.commit();
        } catch (JEVisException ex) {
            logger.fatal(ex);
        }
    }
}
