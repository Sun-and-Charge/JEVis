/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of loytec-xml-dl.
 * <p>
 * loytec-xml-dl is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * loytec-xml-dl is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * loytec-xml-dl. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * loytec-xml-dl is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.loytecxmldl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

public class OPCUAStatus {
    private final static Logger log = LogManager.getLogger(OPCUAStatus.class.getName());
    static final int SUCCESS = 1; // The Opc Node was successfully written with the new value
    static final int OPC_SERVER_NOT_REACHABLE = 2; // could not connect to OPC-UA Server
    static final int NOT_WRITTEN = 3; // Error occurred during write action
    static final String STATUS_LOG = "Status Log";
    private final int status;

    public OPCUAStatus(int status) {
        this.status = status;
    }

    public void writeStatus(JEVisObject jeVisObject, DateTime dateTime) {
        try {
            jeVisObject.getAttribute(STATUS_LOG).buildSample(dateTime, status).commit();
        } catch (JEVisException e) {
            log.error(e);
        }
    }
}
