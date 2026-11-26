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

/**
 * One sample of the attribute "Outage Log" of an "Alarm Configuration" (JSON, sample time = event time;
 * docs/ALARMS-OUTAGE.md). Kept apart from "Log File" so that older Control Centers keep reading their alarm lists.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OutageEvent {

    public static final String ATTRIBUTE = "Outage Log";
    public static final String START = "START";
    public static final String END = "END";

    /** START or END */
    public String event;
    /** ROW or SOURCE */
    public String kind;
    public long object;
    /** newest value / readout before the outage, ISO; null if there was none */
    public String since;
    public int minutes;
    /** data source in outage at the same time (row outages) */
    public Long cause;
    /** END: time stamp of the first new value / readout, ISO */
    public String resumed;
    /** END: values with time stamps inside the outage arrived later (late delivery) instead of a gap */
    public Boolean backfilled;

    public static OutageEvent start(OutageState.Open o) {
        OutageEvent e = new OutageEvent();
        e.event = START;
        e.kind = o.kind;
        e.object = o.object;
        e.since = o.since;
        e.minutes = o.minutes;
        e.cause = o.cause;
        return e;
    }

    public static OutageEvent end(OutageState.Open o, String resumed, boolean backfilled) {
        OutageEvent e = start(o);
        e.event = END;
        e.resumed = resumed;
        e.backfilled = backfilled;
        return e;
    }

    public static OutageEvent fromJson(String json) {
        try {
            return OutageState.MAPPER.readValue(json, OutageEvent.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public String toJson() {
        try {
            return OutageState.MAPPER.writeValueAsString(this);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
