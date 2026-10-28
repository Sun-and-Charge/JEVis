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

public class ReportAttribute {
    private String attributeName;
    private ReportPeriodConfiguration reportPeriodConfiguration;

    public ReportAttribute(String attributeName, ReportPeriodConfiguration reportPeriodConfiguration) {
        this.attributeName = attributeName;
        this.reportPeriodConfiguration = reportPeriodConfiguration;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public ReportPeriodConfiguration getReportPeriodConfiguration() {
        return reportPeriodConfiguration;
    }

    public void setReportPeriodConfiguration(ReportPeriodConfiguration reportPeriodConfiguration) {
        this.reportPeriodConfiguration = reportPeriodConfiguration;
    }

    @Override
    public String toString() {
        return "ReportAttribute{" +
                "attributeName='" + attributeName + '\'' +
                ", reportPeriodConfiguration=" + reportPeriodConfiguration +
                '}';
    }
}
