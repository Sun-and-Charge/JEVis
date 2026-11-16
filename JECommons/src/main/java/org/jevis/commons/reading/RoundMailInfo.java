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
package org.jevis.commons.reading;

import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

/**
 * A reading round as {@code GET /web/reading-rounds} lists it (stage E8.3, docs/WEB-API.md), with the fields the
 * round mails need. Filled by the JENotifier service from the web service's answer.
 */
public class RoundMailInfo {

    /**
     * A meter of the round in round order.
     */
    public static class Meter {
        public long id;
        public String name;
        public String location;
        public boolean readable;
        public boolean done;
        public boolean automatic;
    }

    /**
     * A reader or supervisor (user or group); {@code name} null if not readable for the service.
     */
    public static class Person {
        public long id;
        public String name;
        public String kind;
    }

    public long id;
    public String name;
    public String siteName;
    public boolean enabled;
    /**
     * ISO period, e.g. {@code P1M}.
     */
    public String period;
    /**
     * The current due date, null before the start date.
     */
    public LocalDate due;
    /**
     * {@code INACTIVE}, {@code NOT_STARTED}, {@code DONE}, {@code OPEN}, {@code LATE} or {@code ESCALATED}.
     */
    public String state;
    public int reminderDays;
    public int escalationDays;
    public String remarks;
    public int done;
    public int total;
    public List<Person> readers = new ArrayList<>();
    public List<Person> supervisor = new ArrayList<>();
    public List<Meter> meters = new ArrayList<>();

    /**
     * @return the number of meters not done for the due date
     */
    public int open() {
        return Math.max(0, total - done);
    }
}
