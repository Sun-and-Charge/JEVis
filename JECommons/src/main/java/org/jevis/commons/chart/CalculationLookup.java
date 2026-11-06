/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.object.plugin.TargetHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the calculation whose output targets a data object, port of the Control Center's
 * {@code ChartTools.isObjectCalculated}/{@code getCalculationMap}.
 * <p>
 * Unlike the Control Center (static map for the whole application) the map belongs to one instance, i.e. to one
 * data source/user; it is built on first use from all readable objects of class "Calculation".
 */
public class CalculationLookup {
    private static final Logger logger = LogManager.getLogger(CalculationLookup.class);
    private final JEVisDataSource ds;
    private Map<Long, Long> calculationMap;

    public CalculationLookup(JEVisDataSource ds) {
        this.ds = ds;
    }

    /**
     * @param object a data object (for a clean data row: its parent data object, like the Control Center)
     * @return the id of the calculation object whose "Output" targets the object, or -1
     */
    public long isObjectCalculated(JEVisObject object) {
        if (object == null) return -1;
        try {
            Long id = getCalculationMap().get(object.getID());
            return id != null ? id : -1;
        } catch (Exception e) {
            logger.error("Could not find calculation for object {}:{}", object.getName(), object.getID(), e);
        }
        return -1;
    }

    private synchronized Map<Long, Long> getCalculationMap() throws JEVisException {
        if (calculationMap != null) return calculationMap;
        Map<Long, Long> map = new HashMap<>();
        JEVisClass calculationClass = ds.getJEVisClass("Calculation");
        if (calculationClass != null) {
            JEVisClass outputClass = ds.getJEVisClass("Output");
            for (JEVisObject calculation : ds.getObjects(calculationClass, true)) {
                try {
                    JEVisObject output = null;
                    List<JEVisObject> outputs = calculation.getChildren(outputClass, true);
                    if (outputs != null && !outputs.isEmpty()) output = outputs.get(0);
                    if (output == null) continue;
                    JEVisAttribute targetAttribute = output.getAttribute("Output");
                    if (targetAttribute == null) continue;
                    try {
                        TargetHelper th = new TargetHelper(ds, targetAttribute);
                        if (th.getObject() != null && !th.getObject().isEmpty() && th.getObject().get(0) != null) {
                            map.put(th.getObject().get(0).getID(), calculation.getID());
                        }
                    } catch (Exception ignored) {
                    }
                } catch (Exception e) {
                    logger.debug("Could not read calculation {}", calculation, e);
                }
            }
        }
        calculationMap = map;
        return calculationMap;
    }
}
