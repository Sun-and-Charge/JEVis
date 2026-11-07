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
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.List;

/**
 * A medium of an action plan (attribute "Custom Medium" of the plan): id, name and CO₂ factor (kg/kWh), stored as
 * JSON list. Old plans store a {@code ;} separated list of names (starting with {@code ;}); for those the name is the
 * id and the CO₂ factor is 0.4, like the Control Center.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Medium {

    /**
     * CO₂ factor of media from the old {@code ;} list.
     */
    public static final double LEGACY_CO2 = 0.400;
    private static final double DEFAULT_CO2 = 0.366;

    @JsonProperty("id")
    private String id = "";
    @JsonProperty("name")
    private String name = "";
    @JsonProperty("co2")
    private Double co2 = DEFAULT_CO2;

    public Medium() {
    }

    public Medium(String id, String name, double co2) {
        this.id = id;
        this.name = name;
        this.co2 = co2;
    }

    /**
     * @param value the attribute "Custom Medium" of an action plan
     * @return the media (empty for an empty or unreadable value)
     */
    public static List<Medium> parseList(String value) {
        List<Medium> media = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return media;
        String trimmed = value.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<Medium> parsed = IsoJson.mapper().readValue(trimmed, new TypeReference<List<Medium>>() {
                });
                if (parsed != null) {
                    for (Medium medium : parsed) {
                        if (medium != null) media.add(medium);
                    }
                }
                return media;
            } catch (Exception e) {
                return media;
            }
        }
        for (String name : IsoJson.splitList(value)) {
            media.add(new Medium(name, name, LEGACY_CO2));
        }
        return media;
    }

    /**
     * @return the medium with this id or null
     */
    public static Medium byId(List<Medium> media, String id) {
        if (id == null) return null;
        for (Medium medium : media) {
            if (id.equals(medium.getId())) return medium;
        }
        return null;
    }

    public String getId() {
        return IsoJson.text(id);
    }

    public String getName() {
        return IsoJson.text(name);
    }

    /**
     * @return the CO₂ factor, null if not a finite number
     */
    public Double getCo2() {
        return IsoJson.finite(co2);
    }
}
