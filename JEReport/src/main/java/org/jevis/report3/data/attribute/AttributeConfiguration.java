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
package org.jevis.report3.data.attribute;

import java.util.Map;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.report3.data.attribute.AttributeConfigurationFactory.ReportConfigurationName;
import org.jevis.report3.data.attribute.AttributeConfigurationFactory.ReportConfigurationType;

/**
 *
 * @author broder
 */
public class AttributeConfiguration {

    private final ReportConfigurationType configType;
    private final ReportConfigurationName configName;
    private final Map<String, JEVisAttribute> attributeMap;

    public AttributeConfiguration(ReportConfigurationName configName, ReportConfigurationType configType, Map<String, JEVisAttribute> attributeMap) {
        this.configName = configName;
        this.configType = configType;
        this.attributeMap = attributeMap;
    }

    public ReportConfigurationType getConfigType() {
        return configType;
    }

    public ReportConfigurationName getConfigName() {
        return configName;
    }

    public JEVisAttribute getAttribute(String attrName) {
        return attributeMap.get(attrName);
    }

}
