/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
import org.jevis.api.*;
import org.jevis.commons.DatabaseHelper;
import org.jevis.commons.dataprocessing.VirtualSample;
import org.jevis.commons.driver.*;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;

import static org.jevis.commons.driver.DataCollectorTypes.DataSource.TIMEZONE;

/**
 * OPC UA Server Driver for the JEDataCollector
 *
 * @author florian.simon@envidatec.com
 */
public class OPCUAServer {

    private final static Logger logger = LogManager.getLogger(OPCUAServer.class.getName());


    /**
     * The default value is {@value}.
     */
    public final String DEFAULT_PROTOCOL = "opc.tcp";
    /**
     * The default value is {@value}.
     */
    public final String USER_DEFAULT = "";
    /**
     * The default value is {@value}.
     */
    public final String DEFAULT_PASSWORD = "";
    /**
     * The default value is {@value}.
     */
    public final String DEFAULT_PORT = "4840";
    /**
     * The default value is {@value}.
     */
    public final String DEFAULT_CONNECTION_TIMEOUT = "30";
    /**
     * The default value is {@value}.
     */
    public final String DEFAULT_READ_TIMEOUT = "60";

    private final int DAYS_PER_Request = 30;
    private final String host;
    private final Integer port;
    private final String protocol;
    private final Integer connectionTimeout;
    private final Integer readTimeout;
    private final String user;
    private final String password;
    private final DateTimeZone timezone;
    private final List<OPCUAChannel> channelDirectories = new ArrayList<>();
    private final JEVisObject dataSourceObject;

    /**
     * Default constructor
     * <p>
     * TODO: support different protocol
     * TODO: select endpoint
     *
     * @param dataSourceObject
     */
    public OPCUAServer(JEVisObject dataSourceObject) {
        logger.info("Init: {}", dataSourceObject);
        this.dataSourceObject = dataSourceObject;

        Helper helper = new Helper();
        host = helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.HOST);
        port = Integer.parseInt(helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.PORT, DEFAULT_PORT));
        connectionTimeout = Integer.parseInt(helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.CONNECTION_TIMEOUT, DEFAULT_CONNECTION_TIMEOUT));
        readTimeout = Integer.parseInt(helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.READ_TIMEOUT, DEFAULT_READ_TIMEOUT));
        user = helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.OPCUA.USER, USER_DEFAULT);
        logger.debug("user attribute: '{}' User: {}", DataCollectorTypes.DataSource.DataServer.OPCUA.USER, user);
        password = helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.OPCUA.PASSWORD, DEFAULT_PASSWORD);
        protocol = String.valueOf(helper.getValue(dataSourceObject, DataCollectorTypes.DataSource.DataServer.OPCUA.PROTOCOL, DEFAULT_PROTOCOL));


        JEVisClass opcServerJClass = null;
        try {
            opcServerJClass = dataSourceObject.getJEVisClass();
        } catch (JEVisException e) {
            e.printStackTrace();
        }
        JEVisType timezoneType = null;
        try {
            if (opcServerJClass != null) {
                timezoneType = opcServerJClass.getType(TIMEZONE);
            }
        } catch (JEVisException e) {
            e.printStackTrace();
        }
        String timezoneString = null;
        try {
            timezoneString = DatabaseHelper.getObjectAsString(dataSourceObject, timezoneType);
        } catch (JEVisException e) {
            e.printStackTrace();
        }
        if (timezoneString != null) {
            timezone = DateTimeZone.forID(timezoneString);
        } else {
            timezone = DateTimeZone.UTC;
        }
        logger.debug("OPC UA Server - Timezone: {}", timezone);

        // Get all data channel directories
        try {
            // Get channel directory class
            JEVisClass dirJClass = dataSourceObject.getDataSource().getJEVisClass(OPCUAChannel.DIR_JEVIS_CLASS);
            JEVisClass channelJClass = dataSourceObject.getDataSource().getJEVisClass(OPCUAChannel.JEVIS_CLASS);

            getChannelRecursive(dataSourceObject, channelJClass, dirJClass, channelDirectories);
            logger.info("Found {} channel directories.", channelDirectories.size());

        } catch (Exception ex) {
            logger.error("Error while getting data channel directories");
            logger.error(ex.getMessage());
        }
    }

    /**
     * Return the configured URL
     *
     * @return
     */
    public String getURL() {
        return protocol + "://" + host + ":" + port;
    }

    /**
     * Start the readout process
     */
    public void run() {

        OPCClient opcClient = null;
        try {
            logger.info("Start Readout");
            /** example: opc.tcp://10.1.2.128:4840 **/
            String deviceAddress = getURL();
            logger.info("Endpoint: {}", deviceAddress);
            opcClient = new OPCClient(deviceAddress);
            configureTimeouts(opcClient);

            OpcEndpoint endpointDescription = opcClient.autoSelectEndpoint();
            opcClient.setEndpoints(endpointDescription);
            if (user != null && !user.isEmpty()) {
                logger.info("Using user: {}", user);
                opcClient.setIdentification(user, password);
            }


            logger.info("Call connect");
            opcClient.connect();
            logger.info("Connection done start readout");

            Importer importer = new JEVisImporter();
            importer.initialize(dataSourceObject);


            for (OPCUAChannel OPCUAChannel : channelDirectories) {
                try {
                    logger.info("Start readout for channel: {}", OPCUAChannel);

                    boolean reachedEnd = false;
                    DateTime starttime = OPCUAChannel.getLastReadout();

                    while (!reachedEnd) {
                        DateTime endTime = calculateEndDate(starttime);
                        List<JEVisSample> statusResults = new ArrayList<>();

                        List<Result> results = readChannel(opcClient, OPCUAChannel, starttime, endTime, statusResults);

                        logger.debug("read end: {} {}", results.isEmpty(), endTime.isAfter(new DateTime()));
                        if (results.isEmpty() && endTime.isAfter(new DateTime())) {
                            reachedEnd = true;
                        } else {
                            starttime = endTime;
                        }

                        if (!results.isEmpty() || !statusResults.isEmpty()) {
                            logger.info("Import samples into: {} sample: {} status: {}", OPCUAChannel.getJeVisObject(), results.size(), statusResults.size());

                            JEVisImporterAdapter.importResults(results, statusResults, importer, OPCUAChannel.getJeVisObject());
                        } else {
                            logger.info("No Data to import for {}", OPCUAChannel.getJeVisObject());
                        }


                    }
                } catch (Exception ex) {
                    logger.error("Error while reading channel {}", OPCUAChannel, ex);
                }

            }
        } catch (Exception ex) {
            logger.error(ex);
        } finally {
            try {
                if (opcClient != null) {
                    opcClient.close();
                }
            } catch (Exception ex) {
                logger.error(ex);
            }
        }
    }

    /**
     * Applies the connection and read timeouts (seconds) of the data source to the client. A history request may
     * take several calls to the server (continuation points), so it gets ten times the read timeout, at least
     * ten minutes.
     *
     * @param opcClient client to configure
     */
    public void configureTimeouts(OPCClient opcClient) {
        if (connectionTimeout != null && connectionTimeout > 0) {
            opcClient.setConnectTimeoutMs(connectionTimeout * 1000L);
        }
        if (readTimeout != null && readTimeout > 0) {
            opcClient.setRequestTimeoutMs(readTimeout * 1000L);
            opcClient.setHistoryTimeoutMs(Math.max(readTimeout * 10000L, 600000L));
        }
    }

    /**
     * Calculates the enddate for a data call based on the last date plus the DAYS_PER_Request setting.
     *
     * @param date last date to start from
     * @return
     */
    private DateTime calculateEndDate(DateTime date) {
        logger.debug("calculateEndDate: date: {} d2: {} bool: {}", date, date.plusDays(DAYS_PER_Request), date.plusDays(DAYS_PER_Request).isBeforeNow());
        if (date.plusDays(DAYS_PER_Request).isBeforeNow()) {
            return date.plusDays(DAYS_PER_Request).withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        } else {
            return DateTime.now().withHourOfDay(23).withMinuteOfHour(59).withSecondOfMinute(59);
        }
    }


    /**
     * Read the data from the given channel.
     *
     * @param opcClient
     * @param OPCUAChannel
     * @param startDate
     * @param endTime
     * @param statusResults
     * @return List of Result samples
     * @throws Exception
     */
    private List<Result> readChannel(OPCClient opcClient, OPCUAChannel OPCUAChannel, DateTime startDate, DateTime endTime, List<JEVisSample> statusResults) throws Exception {
        logger.error("Start readout for channel: {} from: {} until: {}", OPCUAChannel.getJeVisObject(), startDate, endTime);


        List<OpcValue> valueList = opcClient.getHistory(OPCUAChannel, startDate, endTime);
        List<Result> results = new ArrayList<>();

        logger.info("Samples received: {}", valueList.size());
        convertValues(valueList, OPCUAChannel.getTargetString(), timezone, results, statusResults);

        return results;

    }


    /**
     * Converts history values into results: good values to samples of the target (numbers as Double, else text),
     * bad values to status samples with the status code, uncertain values to status samples with -1.
     * Values without timestamp or without value are skipped.
     *
     * @param values        values of the history request
     * @param target        target string of the channel
     * @param timezone      timezone of the timestamps
     * @param results       receives the samples
     * @param statusResults receives the status samples
     */
    static void convertValues(List<OpcValue> values, String target, DateTimeZone timezone, List<Result> results, List<JEVisSample> statusResults) {
        for (OpcValue dataValue : values) {
            try {
                if (dataValue.getTimestamp() == null) {
                    logger.debug("Value without timestamp skipped: {}", dataValue);
                    continue;
                }
                DateTime ts = dataValue.getTimestamp().withZone(timezone);

                if (dataValue.isBad()) {
                    statusResults.add(new VirtualSample(ts, dataValue.getStatusCode()));
                } else if (!dataValue.isGood()) {
                    statusResults.add(new VirtualSample(ts, -1L));
                    logger.warn("Error status for value: {}", dataValue);
                } else {
                    Object value = dataValue.toSampleValue();
                    if (value == null) {
                        logger.debug("Value without value skipped: {}", dataValue);
                        continue;
                    }
                    logger.debug(" Target: {} TS: {} -> v: {} raw: {} ", target, ts, value, dataValue.getValue());
                    results.add(new Result(target, value, ts));
                }
            } catch (Exception ex) {
                logger.error("DataValue error; {}", dataValue, ex);
            }
        }
    }

    /**
     * Fetch all channel setting in for the given OPC Server in the jevistree
     *
     * @param parent
     * @param channelClass
     * @param dirClass
     * @param children
     * @throws JEVisException
     */
    private void getChannelRecursive(JEVisObject parent, JEVisClass channelClass, JEVisClass dirClass, List<OPCUAChannel> children) throws JEVisException {
        parent.getChildren().forEach(jeVisObject -> {
            try {

                if (jeVisObject.getJEVisClassName().equals(channelClass.getName())) {
                    try {
                        children.add(new OPCUAChannel(jeVisObject));
                    } catch (Exception ex) {
                        logger.error("Error in channel: {}", jeVisObject);
                    }
                }

                if (jeVisObject.getJEVisClassName().equals(dirClass.getName())) {
                    getChannelRecursive(jeVisObject, channelClass, dirClass, children);
                }

            } catch (Exception ex) {
                logger.error("Error in child: {}", jeVisObject);
            }


        });

    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }
}
