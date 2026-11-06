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
package org.jevis.commons.chart;

import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.Locale;

/**
 * What to evaluate: the settings of the Control Center's analysis tool bar (time frame, aggregation, manipulation,
 * custom workday) plus the time zone and locale of the user, which the Control Center takes from the computer.
 */
public class EvaluationSettings {
    private TimeFrame timeFrame = AnalysisDefinition.DEFAULT_TIME_FRAME;
    private DateTime from;
    private DateTime to;
    private AggregationPeriod aggregation = AnalysisDefinition.DEFAULT_AGGREGATION;
    private ManipulationMode manipulation = AnalysisDefinition.DEFAULT_MANIPULATION;
    private DateTimeZone zone = DateTimeZone.getDefault();
    private boolean customWorkDay = true;
    private Locale holidayLocale;
    private DateTime now;

    public TimeFrame getTimeFrame() {
        return timeFrame;
    }

    public EvaluationSettings setTimeFrame(TimeFrame timeFrame) {
        this.timeFrame = timeFrame;
        return this;
    }

    /**
     * @return the start for {@link TimeFrame#CUSTOM}
     */
    public DateTime getFrom() {
        return from;
    }

    public EvaluationSettings setFrom(DateTime from) {
        this.from = from;
        return this;
    }

    /**
     * @return the end for {@link TimeFrame#CUSTOM}
     */
    public DateTime getTo() {
        return to;
    }

    public EvaluationSettings setTo(DateTime to) {
        this.to = to;
        return this;
    }

    public AggregationPeriod getAggregation() {
        return aggregation;
    }

    public EvaluationSettings setAggregation(AggregationPeriod aggregation) {
        this.aggregation = aggregation != null ? aggregation : AggregationPeriod.NONE;
        return this;
    }

    public ManipulationMode getManipulation() {
        return manipulation;
    }

    public EvaluationSettings setManipulation(ManipulationMode manipulation) {
        this.manipulation = manipulation != null ? manipulation : ManipulationMode.NONE;
        return this;
    }

    /**
     * @return the time zone of the user, used for the time frame, the aggregation and the day filter
     */
    public DateTimeZone getZone() {
        return zone;
    }

    public EvaluationSettings setZone(DateTimeZone zone) {
        this.zone = zone != null ? zone : DateTimeZone.getDefault();
        return this;
    }

    /**
     * @return the "custom workday" switch of the tool bar (default on), passed to
     * {@code JEVisAttribute.getSamples(..., customWorkDay, ...)} for aggregated data
     */
    public boolean isCustomWorkDay() {
        return customWorkDay;
    }

    public EvaluationSettings setCustomWorkDay(boolean customWorkDay) {
        this.customWorkDay = customWorkDay;
        return this;
    }

    /**
     * @return locale whose public holidays the day filter excludes in addition to those of the building
     * (Control Center: the UI locale); {@code null} for building holidays only
     */
    public Locale getHolidayLocale() {
        return holidayLocale;
    }

    public EvaluationSettings setHolidayLocale(Locale holidayLocale) {
        this.holidayLocale = holidayLocale;
        return this;
    }

    /**
     * @return the current instant for the time frame, {@code null} for the system clock (for tests)
     */
    public DateTime getNow() {
        return now;
    }

    public EvaluationSettings setNow(DateTime now) {
        this.now = now;
        return this;
    }
}
