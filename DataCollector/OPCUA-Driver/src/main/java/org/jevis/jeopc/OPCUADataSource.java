/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.commons.driver.DataSource;

import java.io.InputStream;
import java.util.List;

public class OPCUADataSource implements DataSource {

    private final String version = "1.1 2020-11-30";
    private final static Logger log = LogManager.getLogger(OPCUADataSource.class.getName());

    private JEVisObject dataSourceObj = null;
    private OPCUAServer opcuaServer = null;

    @Override
    public void run() {
        log.debug("run:");

        opcuaServer.run();
        log.debug("done");

    }

    @Override
    public void initialize(JEVisObject dataSourceJEVis) {
        log.debug("initialize: Driver version {} for object: {}", version, dataSourceJEVis);

        dataSourceObj = dataSourceJEVis;
        opcuaServer = new OPCUAServer(dataSourceJEVis);


        log.debug("Done init");
    }

    @Override
    public List<InputStream> sendSampleRequest(JEVisObject channel) throws Exception {
        log.debug("sendSampleRequest: " + channel);

        return null;
    }

    @Override
    public void parse(List<InputStream> input) {
        log.debug("parse: {}", input);
    }

    @Override
    public void importResult() {
        log.debug("importResult");
    }
}
