/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.tool;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;

import java.util.ArrayList;
import java.util.List;

public class IDFetcher {
    private static final Logger logger = LogManager.getLogger(IDFetcher.class);

    public IDFetcher(JEVisObject obj) {
        List<JEVisObject> object = new ArrayList<>();
        object.add(obj);
        try {
            getChildrenIDS(object, obj);
            printIDs(object);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    private void getChildrenIDS(List<JEVisObject> objects, JEVisObject parent) throws JEVisException {

        for (JEVisObject object : parent.getChildren()) {
            try {
                objects.add(object);
                getChildrenIDS(objects, object);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

    }

    public void printIDs(List<JEVisObject> object) {
        logger.debug("==============");
        object.forEach(object1 -> {
            logger.debug(object1.getID() + ",");
        });
        logger.debug("==============");
    }

}



