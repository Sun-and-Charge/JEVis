/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
import org.jevis.api.JEVisObject;
import org.jevis.commons.driver.DataCollectorTypes;
import org.jevis.jeopc.OPCClient;
import org.jevis.jeopc.OPCUAServer;
import org.jevis.jeopc.OpcEndpoint;
import org.jevis.jeopc.OpcException;
import org.joda.time.DateTime;

public class OPCUAWriter {
    private static final Logger logger = LogManager.getLogger(OPCUAWriter.class);
    private static final String USER = "User";
    private static final String PASSWORD = "Password";
    private static final String TARGET_ID = "Target ID";
    private static final String VALUE = "Value";
    private static final String OPC_ID = "OPC ID";
    private OPCClient opcClient;

    public Boolean sendOPCUANotification(JEVisObject outputChannel, JEVisObject dataObject) {
        try {
            if (!outputChannel.getAttribute(DataCollectorTypes.Channel.LAST_READOUT).hasSample() || dataObject.getAttribute(VALUE).getTimestampOfLastSample().getMillis() > outputChannel.getAttribute(DataCollectorTypes.Channel.LAST_READOUT).getTimestampOfLastSample().getMillis()) {

                logger.info("new Data to write for JEVis ID {}", outputChannel.getID());

                String nodeId = outputChannel.getAttribute(OPC_ID).getLatestSample().getValue().toString().trim();
                String datatype = opcClient.getDataType(nodeId);
                logger.info("OPC-UA Node datatype: {}", datatype);

                if ("Double".equals(datatype) || "Float".equals(datatype)) {
                    opcClient.writeValue(dataObject.getAttribute(VALUE).getLatestSample().getValueAsDouble(), nodeId, datatype);
                } else if (OPCClient.isIntegerType(datatype)) {
                    opcClient.writeValue(Math.round(dataObject.getAttribute(VALUE).getLatestSample().getValueAsDouble()), nodeId, datatype);
                } else if ("Boolean".equals(datatype)) {
                    Boolean value = convertToBool(dataObject.getAttribute(VALUE).getLatestSample().getValueAsDouble());
                    if (value == null) {
                        throw new Exception("Value is not 0 or 1 for boolean node " + nodeId);
                    }
                    opcClient.writeValue(value, nodeId);
                } else if ("String".equals(datatype)) {
                    opcClient.writeValue(dataObject.getAttribute(VALUE).getLatestSample().getValueAsString(), nodeId);
                } else {
                    throw new Exception("Datatype not supported: " + datatype);
                }

                OPCUAStatus opcUAStatus = new OPCUAStatus(OPCUAStatus.SUCCESS);
                opcUAStatus.writeStatus(outputChannel, DateTime.now());

                return true;
            }
        } catch (Exception e) {
            OPCUAStatus opcUAStatus = new OPCUAStatus(OPCUAStatus.NOT_WRITTEN);
            opcUAStatus.writeStatus(outputChannel, DateTime.now());
            logger.error(e);
        }
        return false;
    }

    /**
     * Connects to the OPC UA server of the data source.
     *
     * @throws OpcException if the server cannot be reached
     */
    public void connectToOPCUAServer(JEVisObject opcServerObj) throws OpcException {

        try {
            logger.info("set up OPC-UA Connection");

            OPCUAServer opcuaServer = new OPCUAServer(opcServerObj);

            opcClient = new OPCClient(opcuaServer.getURL());
            opcuaServer.configureTimeouts(opcClient);
            OpcEndpoint endpointDescription = opcClient.autoSelectEndpoint();
            opcClient.setEndpoints(endpointDescription);

            opcClient.setIdentification(opcuaServer.getUser(), opcuaServer.getPassword());
            logger.info("Connect to OPC-UA Server to: {}", opcuaServer.getURL());
            opcClient.connect();

        } catch (OpcException e) {
            logger.error("Error connection to OPC Server: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("Error connection to OPC Server", e);
        }
    }

    public void disconnect() {
        if (opcClient != null) {
            opcClient.close();
            opcClient = null;
            logger.info("Disconnected from OPC-UA Server");
        }
    }

    public Boolean convertToBool(Double value) {
        if (value == 1.0) {
            return true;
        } else if (value == 0.0) {
            return false;
        } else {
            return null;
        }
    }
}
