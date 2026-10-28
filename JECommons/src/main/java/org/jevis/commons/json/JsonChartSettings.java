/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.json;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

public class JsonChartSettings {

    private List<JsonChartSetting> listSettings = new ArrayList<>();
    private String isAutoSize = Boolean.TRUE.toString();

    @XmlElement(name = "listSettings")
    public List<JsonChartSetting> getListSettings() {
        return listSettings;
    }

    public void setListSettings(List<JsonChartSetting> listSettings) {
        this.listSettings = listSettings;
    }

    @XmlElement(name = "autoSize")
    public String getAutoSize() {
        return isAutoSize;
    }

    public void setAutoSize(String isAutoSize) {
        this.isAutoSize = isAutoSize;
    }

    @Override
    public String toString() {
        try {
            return JsonTools.prettyObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return "";
    }
}
