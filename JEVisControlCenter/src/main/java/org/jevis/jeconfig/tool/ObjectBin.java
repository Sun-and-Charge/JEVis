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

import javafx.scene.control.TreeItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.jeconfig.application.jevistree.JEVisTreeRow;
import org.jevis.jeconfig.application.jevistree.TreeHelper;

import java.util.ArrayList;
import java.util.List;

public class ObjectBin {

    private static final Logger logger = LogManager.getLogger(ObjectBin.class);

    public static void deleteObject(List<TreeItem<JEVisTreeRow>> objects) {
        //user can delete


        objects.forEach(treeItem -> {
            try {
                JEVisObject jeVisObject = treeItem.getValue().getJEVisObject();
                JEVisDataSource ds = jeVisObject.getDataSource();
                JEVisObject binObject = getUserBin(jeVisObject);

                logger.error("Bin Dir: {}", binObject);
                logger.error("Can create in Bin Dir: {}", ds.getCurrentUser().canCreate(binObject.getID()));
                logger.error("Can move Object: {}", ds.getCurrentUser().canDelete(jeVisObject.getID()));
                if (ds.getCurrentUser().canCreate(binObject.getID()) && ds.getCurrentUser().canDelete(jeVisObject.getID())) {
                    List<JEVisObject> objectToMove = new ArrayList<>();
                    objectToMove.add(jeVisObject);
                    TreeHelper.moveObject(objectToMove, binObject);
                }

            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        });

    }

    public static JEVisObject getUserBin(JEVisObject object) {

        try {
            JEVisObject firstBin = CommonMethods.getFirstParentalObjectOfClass(object, "Recycle Bin");
            return firstBin;
        } catch (Exception ex) {
            logger.error(ex, ex);
        }
        return null;
    }

}
