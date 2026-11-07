/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.commons.iso;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumption or EnPI of an action (keys "Consumption" and "EnPI" of the action JSON) with the values stored by the
 * Control Center (not recomputed).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsumptionData {

    @JsonProperty("Before")
    private Double before = 0.0;
    @JsonProperty("After")
    private Double after = 0.0;
    @JsonProperty("Diff")
    private Double diff = 0.0;
    @JsonProperty("Object")
    private Long object = 0L;
    @JsonProperty("Calculation")
    private Long calculation = 0L;
    @JsonProperty("Data Source")
    private String dataSource = "";
    @JsonProperty("Before From Date")
    private String beforeFrom;
    @JsonProperty("Before Until Date")
    private String beforeUntil;
    @JsonProperty("After From Date")
    private String afterFrom;
    @JsonProperty("After Until Date")
    private String afterUntil;

    /**
     * The data object of the values: the link "Data Source" (as the Control Center uses it), otherwise "Object";
     * placeholders of the Control Center (ids ≤ 0) are no object.
     *
     * @return the object id or null
     */
    public Long getObjectId() {
        if (dataSource != null && !dataSource.trim().isEmpty()) {
            try {
                long id = Long.parseLong(dataSource.trim());
                if (id > 0) return id;
            } catch (NumberFormatException e) {
                /* no object */
            }
        }
        return object != null && object > 0 ? object : null;
    }

    public Double getBefore() {
        return IsoJson.finite(before);
    }

    public Double getAfter() {
        return IsoJson.finite(after);
    }

    public Double getDiff() {
        return IsoJson.finite(diff);
    }

    /**
     * @param unit the unit of the data object (null if unknown or not readable)
     * @return {@code before, after, diff, unit, objectId, beforeFrom, beforeUntil, afterFrom, afterUntil}
     */
    public Map<String, Object> toJson(String unit) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("before", getBefore());
        json.put("after", getAfter());
        json.put("diff", getDiff());
        json.put("unit", unit);
        json.put("objectId", getObjectId());
        json.put("beforeFrom", IsoJson.date(beforeFrom));
        json.put("beforeUntil", IsoJson.date(beforeUntil));
        json.put("afterFrom", IsoJson.date(afterFrom));
        json.put("afterUntil", IsoJson.date(afterUntil));
        return json;
    }
}
