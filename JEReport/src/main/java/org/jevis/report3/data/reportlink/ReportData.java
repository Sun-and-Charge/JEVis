/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3.data.reportlink;

import org.jevis.api.JEVisObject;
import org.jevis.report3.data.report.ReportProperty;
import org.jevis.report3.data.report.intervals.IntervalCalculator;
import org.joda.time.DateTime;

import java.util.Map;

/**
 * @author broder
 */
public interface ReportData {

    Map<String, Object> getReportMap(ReportProperty property, IntervalCalculator intervalCalc);

    JEVisObject getDataObject();

    //
//    public JEVisObject getLinkObject();
    LinkStatus getReportLinkStatus(DateTime end);

    class LinkStatus {

        private final boolean sanityCheck;
        private final String message;

        public LinkStatus(boolean sanityCheck, String message) {
            this.sanityCheck = sanityCheck;
            this.message = message;
        }

        public boolean isSanityCheck() {
            return sanityCheck;
        }

        public String getMessage() {
            return message;
        }

    }
}
