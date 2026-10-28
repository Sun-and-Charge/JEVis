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

import org.jevis.api.JEVisObject;

import java.util.List;


public class TreeInbreedFinder {

    public TreeInbreedFinder(List<JEVisObject> object) {

        object.forEach(object1 -> {
            System.out.println("Object: " + object1.getID());
            String path = "";
            getParent(1, path, object1);
            System.out.println("#-> " + path);
        });
    }


    private void getParent(int level, String path, JEVisObject object) {
        try {
            if (object.getID().equals(9271l)) {
                System.out.println(object);
            }

            level++;
            if (object != null) {
                path = path + "/" + object.getID();
                if (object.getParent() != null) {
                    getParent(level, path, object.getParent());
                    System.out.println(path);
                }
            }

        } catch (Exception ex) {
            ex.getMessage();
        }
    }

}
