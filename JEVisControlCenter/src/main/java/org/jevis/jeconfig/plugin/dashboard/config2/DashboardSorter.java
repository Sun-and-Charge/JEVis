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
package org.jevis.jeconfig.plugin.dashboard.config2;

import javafx.collections.ObservableList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.application.Chart.ChartTools;

/**
 * Sort lists of dashboard objects
 * This class is to clean up code an hide this log ugly code...
 */
public class DashboardSorter {

    private static final Logger logger = LogManager.getLogger(DashboardSorter.class);
    public static final String ANALYSES_DIRECTORY_CLASS_NAME = "Analyses Directory";
    public static final String BUILDING_CLASS_NAME = "Building";
    public static final String ANALYSIS_CLASS_NAME = "Dashboard Analysis";


    /**
     * Sort a list of dashboard objects based on the location in the tree(organisation/building)
     *
     * @param ds
     * @param observableList
     */
    public static void sortDashboards(JEVisDataSource ds, ObservableList<JEVisObject> observableList) {
        ObjectRelations objectRelations = new ObjectRelations(ds);

        AlphanumComparator ac = new AlphanumComparator();
        if (!ChartTools.isMultiDir(ds) && !ChartTools.isMultiSite(ds))
            observableList.sort((o1, o2) -> ac.compare(o1.getName(), o2.getName()));
        else {
            observableList.sort((o1, o2) -> {

                String prefix1 = "";
                String prefix2 = "";

                if (ChartTools.isMultiSite(ds)) {
                    prefix1 += objectRelations.getObjectPath(o1);
                }
                if (ChartTools.isMultiDir(ds, o1)) {
                    prefix1 += objectRelations.getRelativePath(o1);
                }
                prefix1 += o1.getName();

                if (ChartTools.isMultiSite(ds)) {
                    prefix2 += objectRelations.getObjectPath(o2);
                }
                if (ChartTools.isMultiDir(ds, o2)) {
                    prefix2 += objectRelations.getRelativePath(o2);
                }
                prefix2 += o2.getName();

                return ac.compare(prefix1, prefix2);
            });
        }
    }
}
