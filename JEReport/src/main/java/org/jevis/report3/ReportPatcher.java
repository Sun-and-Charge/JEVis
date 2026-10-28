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
package org.jevis.report3;

import org.jevis.api.JEVisException;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.report3.patch.ReportPatches;

/**
 *
 * @author broder
 */
public class ReportPatcher {

    public static void main(String[] args) throws JEVisException {
        ReportPatcher patch = new ReportPatcher();
        patch.run();
    }

    public void run() throws JEVisException {
        JEVisDataSourceWS datasource = new JEVisDataSourceWS("http://localhost:18090");
        ReportPatches.insertScheduledAndEventReportUnderReport(datasource);
    }
}
