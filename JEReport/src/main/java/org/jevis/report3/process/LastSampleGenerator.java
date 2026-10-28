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
package org.jevis.report3.process;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.report3.data.attribute.ReportAttributeProperty;
import org.jevis.report3.data.report.ReportProperty;
import org.jevis.report3.data.reportlink.ReportLinkProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * @author broder
 */
public class LastSampleGenerator {
    private static final Logger logger = LogManager.getLogger(LastSampleGenerator.class);

    public Map<String, Object> work(ReportLinkProperty linkData, ReportAttributeProperty attributeData, ReportProperty property) throws JEVisException {
        JEVisObject dataObject = linkData.getDataObject();
        Map<String, Object> resultMap = new HashMap<>();

        JEVisAttribute attr = dataObject.getAttribute(attributeData.getAttributeName());
        JEVisSample latestSample = attr.getLatestSample();
        if (latestSample != null) {
            resultMap = ProcessHelper.getAttributeSample(latestSample, attr, property.getTimeZone());
        }
        return resultMap;
    }
}
