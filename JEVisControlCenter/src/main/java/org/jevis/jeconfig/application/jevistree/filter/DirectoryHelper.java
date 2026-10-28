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
package org.jevis.jeconfig.application.jevistree.filter;

import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;

import java.util.ArrayList;
import java.util.List;

public class DirectoryHelper {

    private static DirectoryHelper instance;
    private final JEVisDataSource dataSource;
    private List<JEVisClass> directories;
    private List<String> directoriesNames;

    private DirectoryHelper(JEVisDataSource dataSource) {
        this.dataSource = dataSource;
    }

    public static DirectoryHelper getInstance(JEVisDataSource ds) {

        if (DirectoryHelper.instance == null) {
            DirectoryHelper.instance = new DirectoryHelper(ds);
        }
        return DirectoryHelper.instance;
    }

    public List<String> getDirectoryNames() throws JEVisException {
        if (directories == null) {
            JEVisClass directoryClass = dataSource.getJEVisClass("Directory");
            directories = directoryClass.getHeirs();
            directoriesNames = new ArrayList<>();
            directories.forEach(jeVisClass -> {
                try {
                    directoriesNames.add(jeVisClass.getName());
                } catch (Exception ex) {
                }
            });
        }

        return directoriesNames;
    }
}
