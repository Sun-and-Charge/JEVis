/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dashboard;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

/**
 * What to evaluate of a dashboard: the time frame of the time frame bar with its reference date (or from/to for
 * {@link DashboardTimeFrames#CUSTOM}) and the time zone of the user.
 */
public class DashboardRequest {
    private String timeFrame;
    private DateTime date;
    private DateTime from;
    private DateTime to;
    private DateTimeZone zone = DateTimeZone.getDefault();
    private DateTime now;
    private boolean customWorkDay = true;

    /**
     * @return the time frame id, {@code null} for the time frame of the dashboard
     */
    public String getTimeFrame() {
        return timeFrame;
    }

    public DashboardRequest setTimeFrame(String timeFrame) {
        this.timeFrame = timeFrame;
        return this;
    }

    /**
     * @return the reference date, {@code null} for now
     */
    public DateTime getDate() {
        return date;
    }

    public DashboardRequest setDate(DateTime date) {
        this.date = date;
        return this;
    }

    public DateTime getFrom() {
        return from;
    }

    public DashboardRequest setFrom(DateTime from) {
        this.from = from;
        return this;
    }

    public DateTime getTo() {
        return to;
    }

    public DashboardRequest setTo(DateTime to) {
        this.to = to;
        return this;
    }

    public DateTimeZone getZone() {
        return zone;
    }

    public DashboardRequest setZone(DateTimeZone zone) {
        this.zone = zone != null ? zone : DateTimeZone.getDefault();
        return this;
    }

    /**
     * @return the current instant, {@code null} for the system clock (for tests)
     */
    public DateTime getNow() {
        return now;
    }

    public DashboardRequest setNow(DateTime now) {
        this.now = now;
        return this;
    }

    /**
     * @return the "custom workday" switch of the dashboard tool bar (default on)
     */
    public boolean isCustomWorkDay() {
        return customWorkDay;
    }

    public DashboardRequest setCustomWorkDay(boolean customWorkDay) {
        this.customWorkDay = customWorkDay;
        return this;
    }
}
