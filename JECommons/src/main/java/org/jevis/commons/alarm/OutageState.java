/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.alarm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Attribute "Outage State" of an "Alarm Configuration" (latest sample, JSON; docs/ALARMS-OUTAGE.md): the open
 * outages by object key ({@code row:<id>} / {@code source:<id>}) and the time of the last daily summary.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutageState {

    public static final String ATTRIBUTE = "Outage State";
    static final ObjectMapper MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private Map<String, Open> open = new LinkedHashMap<>();
    private String lastSummary;

    /** an open outage */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Open {
        /** ROW or SOURCE */
        public String kind;
        public long object;
        /** newest value (row) resp. readout (source) when the outage was found, ISO; null if there was none */
        public String since;
        /** when the outage was found, ISO */
        public String opened;
        public int minutes;
        /** data source in outage at the same time (row outages), null if none */
        public Long cause;

        public Open() {
        }

        public Open(String kind, long object, String since, String opened, int minutes, Long cause) {
            this.kind = kind;
            this.object = object;
            this.since = since;
            this.opened = opened;
            this.minutes = minutes;
            this.cause = cause;
        }
    }

    public static String rowKey(long id) {
        return "row:" + id;
    }

    public static String sourceKey(long id) {
        return "source:" + id;
    }

    public static OutageState fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return new OutageState();
        try {
            OutageState s = MAPPER.readValue(json, OutageState.class);
            return s != null ? s : new OutageState();
        } catch (Exception e) {
            return new OutageState();
        }
    }

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    public Map<String, Open> getOpen() {
        if (open == null) open = new LinkedHashMap<>();
        return open;
    }

    public void setOpen(Map<String, Open> open) {
        this.open = open;
    }

    public String getLastSummary() {
        return lastSummary;
    }

    public void setLastSummary(String lastSummary) {
        this.lastSummary = lastSummary;
    }
}
