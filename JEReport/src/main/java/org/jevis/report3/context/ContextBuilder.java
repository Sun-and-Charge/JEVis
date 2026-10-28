/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.context;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.report3.data.report.ReportProperty;
import org.jevis.report3.data.report.intervals.IntervalCalculator;
import org.jevis.report3.data.reportlink.ReportData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author broder
 */
public class ContextBuilder {

    private static final Logger logger = LogManager.getLogger(ContextBuilder.class);
    private IntervalCalculator intervalCalc;

    public void setIntervalCalculator(IntervalCalculator intervalCalc) {
        this.intervalCalc = intervalCalc;
    }

    void addAttributeMapToLinkMap(ConcurrentHashMap<String, Object> linkMap, ConcurrentHashMap<String, Object> attributeMap) {
        linkMap.putAll(attributeMap);
    }

    public Map<String, Object> buildContext(List<ReportData> reportLinkProperty, ReportProperty property, IntervalCalculator intervalCalc) {
        Map<String, Object> templateMap = new HashMap<>();
        for (ReportData linkProperty : reportLinkProperty) {
            templateMap.putAll(linkProperty.getReportMap(property, intervalCalc));
        }
        logger.info("Built Context for {} report links", templateMap.size());

        return templateMap;
    }
}
