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
package org.jevis.jeconfig.plugin;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.joda.time.DateTime;

public class AnalysisRequest {

    private AggregationPeriod aggregationPeriod;
    private ManipulationMode manipulationMode;
    private JEVisObject object;
    private JEVisAttribute attribute;
    private DateTime startDate;
    private DateTime endDate;

    public AnalysisRequest(JEVisObject object, AggregationPeriod aggregationPeriod, ManipulationMode manipulationMode, DateTime startDate, DateTime endDate) {
        this.manipulationMode = manipulationMode;
        this.aggregationPeriod = aggregationPeriod;
        this.object = object;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public DateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(DateTime startDate) {
        this.startDate = startDate;
    }

    public DateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(DateTime endDate) {
        this.endDate = endDate;
    }

    public AggregationPeriod getAggregationPeriod() {
        return aggregationPeriod;
    }

    public void setAggregationPeriod(AggregationPeriod aggregationPeriod) {
        this.aggregationPeriod = aggregationPeriod;
    }

    public ManipulationMode getManipulationMode() {
        return manipulationMode;
    }

    public void setManipulationMode(ManipulationMode manipulationMode) {
        this.manipulationMode = manipulationMode;
    }

    public JEVisObject getObject() {
        return object;
    }

    public void setObject(JEVisObject object) {
        this.object = object;
    }

    public JEVisAttribute getAttribute() {
        return attribute;
    }

    public void setAttribute(JEVisAttribute attribute) {
        this.attribute = attribute;
    }
}
