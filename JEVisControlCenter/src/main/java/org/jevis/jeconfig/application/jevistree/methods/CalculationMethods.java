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
package org.jevis.jeconfig.application.jevistree.methods;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.object.plugin.TargetHelper;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CalculationMethods extends CommonMethods {
    private static final Logger logger = LogManager.getLogger(CalculationMethods.class);

    public static void deleteAllCalculations(JEVisObject jeVisObject, DateTime from, DateTime to) {
        JEVisClass calculationClass = null;
        JEVisClass outputClass = null;
        JEVisClass cleanDataClass = null;
        JEVisClass rawDataClass = null;
        JEVisDataSource ds = null;
        try {
            ds = jeVisObject.getDataSource();
            calculationClass = ds.getJEVisClass("Calculation");
            outputClass = ds.getJEVisClass("Output");
            rawDataClass = ds.getJEVisClass("Data");
            cleanDataClass = ds.getJEVisClass("Clean Data");

        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ds == null) {
            return;
        }

        List<JEVisObject> rawData = new ArrayList<>();
        try {
            rawData = getAllRawDataRec(jeVisObject, rawDataClass);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<JEVisObject> allCalculations = new ArrayList<>();
        try {
            allCalculations = ds.getObjects(calculationClass, false);
        } catch (Exception e) {
            e.printStackTrace();
        }

        List<JEVisObject> allTargets = new ArrayList<>();
        Map<JEVisObject, JEVisObject> targetAndCalc = new HashMap<>();
        for (JEVisObject calcObject : allCalculations) {
            try {
                for (JEVisObject output : calcObject.getChildren(outputClass, false)) {
                    JEVisAttribute attribute = output.getAttribute("Output");
                    if (attribute != null && attribute.hasSample()) {
                        try {
                            TargetHelper th = new TargetHelper(ds, attribute);
                            if (!th.getObject().isEmpty()) {
                                allTargets.addAll(th.getObject());
                                targetAndCalc.put(th.getObject().get(0), calcObject);
                            }
                        } catch (Exception e) {
                            logger.error("Error with output {}:{}", output.getName(), output.getID(), e);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        List<JEVisObject> foundCalcTarget = new ArrayList<>();
        List<JEVisObject> calculationsToDisable = new ArrayList<>();
        for (JEVisObject target : allTargets) {
            if (rawData.contains(target)) {
                foundCalcTarget.add(target);
                calculationsToDisable.add(targetAndCalc.get(target));
            }
        }

        for (JEVisObject concernedCalculation : calculationsToDisable) {
            setEnabled(concernedCalculation, "Calculation", false);
        }

        deleteSamplesInList(from, to, foundCalcTarget);

        List<JEVisObject> allCleanData = new ArrayList<>();
        for (JEVisObject data : foundCalcTarget) {
            try {
                allCleanData.addAll(org.jevis.commons.utils.CommonMethods.getAllChildrenRecursive(data));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (JEVisObject object: allCleanData){
            org.jevis.commons.utils.CommonMethods.deleteAllSamples(object, from, to, false, true);
        }

        for (JEVisObject concernedCalculation : calculationsToDisable) {
            setEnabled(concernedCalculation, "Calculation", true);
        }
    }

    public static List<JEVisObject> getAllRawDataRec(JEVisObject parent, JEVisClass rawDataClass) throws JEVisException {
        List<JEVisObject> list = new ArrayList<>();
        if (parent.getJEVisClass() != null && parent.getJEVisClass().equals(rawDataClass)) {
            list.add(parent);
        }
        for (JEVisObject child : parent.getChildren()) {
            list.addAll(getAllRawDataRec(child, rawDataClass));
        }
        return list;
    }
}
