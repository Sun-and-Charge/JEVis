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
package org.jevis.report3.patch;

import org.jevis.api.JEVisDataSource;
import org.jevis.commons.patch.Patch;

/**
 *
 * @author broder
 */
public enum ReportPatch implements Patch {

    FIRST_VERSION("1.0") {

                @Override
                public void apply() {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }

                @Override
                public void undo() {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }

            },
    REPORT_PERIODIC("1.1") {

                @Override
                public void apply() {
                    ReportPatches.insertPeriodicReportUnderReport(datasource);
                }

                @Override
                public void undo() {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }

            },
    EVENT_SCHEDULE("1.2") {
                @Override
                public void apply() {
                    ReportPatches.insertScheduledAndEventReportUnderReport(datasource);
                }

                @Override
                public void undo() {
                    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                }
            };
    private final String version;
    private static JEVisDataSource datasource;

    ReportPatch(String version) {
        this.version = version;
    }

    @Override
    public void setJEVisDataSource(JEVisDataSource ds) {
        ReportPatch.datasource = ds;
    }

    @Override
    public String getVersion() {
        return version;
    }
}
