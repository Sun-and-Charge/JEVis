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
package org.jevis.commons.report;

import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.dataprocessing.FixedPeriod;
import org.jevis.commons.dataprocessing.ManipulationMode;

public class ReportPeriodConfiguration {
    private AggregationPeriod reportAggregation;
    private ManipulationMode reportManipulation;
    private PeriodMode periodMode;
    private FixedPeriod fixedPeriod;

    public ReportPeriodConfiguration(AggregationPeriod reportAggregation, ManipulationMode reportManipulation, PeriodMode periodMode, FixedPeriod fixedPeriod) {
        this.reportAggregation = reportAggregation;
        this.reportManipulation = reportManipulation;
        this.periodMode = periodMode;
        this.fixedPeriod = fixedPeriod;
    }

    public AggregationPeriod getReportAggregation() {
        return reportAggregation;
    }

    public void setReportAggregation(AggregationPeriod reportAggregation) {
        this.reportAggregation = reportAggregation;
    }

    public ManipulationMode getReportManipulation() {
        return reportManipulation;
    }

    public void setReportManipulation(ManipulationMode reportManipulation) {
        this.reportManipulation = reportManipulation;
    }

    public PeriodMode getPeriodMode() {
        return periodMode;
    }

    public void setPeriodMode(PeriodMode periodMode) {
        this.periodMode = periodMode;
    }

    public FixedPeriod getFixedPeriod() {
        return fixedPeriod;
    }

    public void setFixedPeriod(FixedPeriod fixedPeriod) {
        this.fixedPeriod = fixedPeriod;
    }

    @Override
    public String toString() {
        return "ReportPeriodConfiguration{" +
                "reportAggregation=" + reportAggregation +
                ", reportManipulation=" + reportManipulation +
                ", periodMode=" + periodMode +
                ", fixedPeriod=" + fixedPeriod +
                '}';
    }
}
