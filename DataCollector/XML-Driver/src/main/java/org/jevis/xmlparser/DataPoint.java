/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of XML-Driver.
 * <p>
 * XML-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * XML-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * XML-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * XML-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.xmlparser;

/**
 * @author bf
 */
public class DataPoint {

    private String mappingIdentifier;
    private String valueIdentifier;
    private String target;

    public String getMappingIdentifier() {
        return mappingIdentifier;
    }

    public void setMappingIdentifier(String mappingIdentifier) {
        this.mappingIdentifier = mappingIdentifier;
    }

    public String getValueIdentifier() {
        return valueIdentifier;
    }

    public void setValueIdentifier(String valueIdentifier) {
        this.valueIdentifier = valueIdentifier;
    }

    public String getTargetStr() {
        return target;
    }

    public void setTargetStr(String target) {
        this.target = target;
    }


}
