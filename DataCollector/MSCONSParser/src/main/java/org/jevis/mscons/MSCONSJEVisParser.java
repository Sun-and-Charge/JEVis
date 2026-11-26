/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of MSCONSParser.
 * <p>
 * MSCONSParser is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * MSCONSParser is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * MSCONSParser. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * MSCONSParser is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.mscons;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;

import org.jevis.api.JEVisType;
import org.jevis.commons.DatabaseHelper;
import org.jevis.commons.driver.DataCollectorTypes;
import org.jevis.commons.driver.Parser;
import org.jevis.commons.driver.ParserReport;
import org.jevis.commons.driver.Result;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MSCONSJEVisParser implements Parser {

    private static final Logger logger = LogManager.getLogger(MSCONSJEVisParser.class);

    private List<JEVisObject> msconsChannels = new ArrayList<>();

    List<Result> results = new ArrayList<>();

    JEVisObject parserObject;

    @Override
    public void initialize(JEVisObject parserObject) {

        this.parserObject = parserObject;
        try {
            msconsChannels.addAll(getChannels(parserObject));
        } catch (Exception e) {
          logger.error(e);
        }

    }

    @Override
    public void parse(List<InputStream> input, DateTimeZone timezone) {


        for (InputStream inputStream : input) {

            MsconsParser msconsParser = new MsconsParser(inputStream);


            if(!msconsParser.parse()) continue;

            MsconsPojo msconsPojo = msconsParser.getMsconsPojo();
            boolean newerFile = true;
            try {
                if (parserObject.getAttribute("File Created Date").hasSample()) {
                    /* skip this file only; the next file of the same run may be newer (was "break") */
                    /* compare with the stored creation date of the last file, not with the time it was imported */
                    JEVisSample last = parserObject.getAttribute("File Created Date").getLatestSample();
                    DateTime lastCreated;
                    try {
                        lastCreated = DateTime.parse(last.getValueAsString());
                    } catch (Exception notADate) {
                        lastCreated = last.getTimestamp();
                    }
                    if (!msconsPojo.getInterchangeHeader().getCreatedDateTime().isAfter(lastCreated))
                        newerFile = false;
                }
            } catch (Exception e) {
                logger.error(e);
            }
            if (!newerFile) {
                logger.info("MSCONS file from {} is not newer than the last imported file, skipped", msconsPojo.getInterchangeHeader().getCreatedDateTime());
                continue;
            }


            msconsChannels.forEach(jeVisObject -> {
                String messlukationnumber;
                try {

                    if (!jeVisObject.getAttribute(DataCollectorTypes.Channel.MSCONSChannel.MESSLIKATION).hasSample())
                        return;
                    messlukationnumber = jeVisObject.getAttribute(DataCollectorTypes.Channel.MSCONSChannel.MESSLIKATION).getLatestSample().getValueAsString().trim();
                    /* "<location>|<OBIS>" selects one register of a location with several registers, e.g. DE0001…|1-1:1.29.0 */
                    String location = messlukationnumber;
                    String obis = null;
                    int bar = messlukationnumber.indexOf('|');
                    if (bar >= 0) {
                        location = messlukationnumber.substring(0, bar).trim();
                        obis = messlukationnumber.substring(bar + 1).trim();
                    }
                    final String locationNumber = location;
                    final String register = obis;
                    Optional<Messlokation> messlokationOptional = msconsPojo.getMesslokation().stream().filter(nr -> nr.getMesslokationNumber().equals(locationNumber)).findFirst();
                    if (!messlokationOptional.isPresent()) return;
                    List<MsconsSample> samples = messlokationOptional.get().getSampleList().stream()
                            .filter(sample -> register == null || register.equals(sample.getObis()))
                            .collect(Collectors.toList());
                    results.addAll(convertMSCONSSampletoResult(samples, getStartEnd(jeVisObject), getTargetValueAtribute(jeVisObject)));


                } catch (Exception e) {
                    logger.error(e);
                }


            });

            try {
                parserObject.getAttribute("Sender").buildSample(DateTime.now(), msconsPojo.getMessageHeader().getSender()).commit();
                parserObject.getAttribute("Recipient").buildSample(DateTime.now(), msconsPojo.getMessageHeader().getRecipient()).commit();
                parserObject.getAttribute("File Created Date").buildSample(DateTime.now(), msconsPojo.getInterchangeHeader().getCreatedDateTime()).commit();
            } catch (Exception e) {
                logger.error(e);
            }


        }


    }

    @Override
    public List<Result> getResult() {
        return results;
    }

    @Override
    public ParserReport getReport() {
        return null;
    }

    public List<JEVisObject> getChannels(JEVisObject jeVisObject) {
        List<JEVisObject> channels = new ArrayList<>();
        try {
            JEVisClass channelDirClass = jeVisObject.getDataSource().getJEVisClass(DataCollectorTypes.ChannelDirectory.MSCONShannelDirectory.NAME);
            JEVisClass channelClass = jeVisObject.getDataSource().getJEVisClass(DataCollectorTypes.Channel.MSCONSChannel.NAME);
            jeVisObject.getChildren(channelDirClass, false).forEach(dir -> {
                channels.addAll(getChannels(dir));
            });
            List<JEVisObject> channelsToBeAdded = jeVisObject.getChildren(channelClass, false);
            logger.debug("Added Channels to List {}", channelsToBeAdded);
            channels.addAll(channelsToBeAdded);
        } catch (Exception e) {
            logger.error(e);
        }
        return channels;

    }

    public List<Result> convertMSCONSSampletoResult(List<MsconsSample> sampleList, String startEnd, String targetString) {
        List<Result> resultList = null;

        if (startEnd.equals("START")) {
            resultList = sampleList.stream().map(msconsSample -> new Result(targetString, msconsSample.getQuantity(), msconsSample.getStart())).collect(Collectors.toList());

        } else if (startEnd.equals("END")) {
            resultList = sampleList.stream().map(msconsSample -> new Result(targetString, msconsSample.getQuantity(), msconsSample.getEnd())).collect(Collectors.toList());
        }

        return resultList;
    }

    private String getTargetValueAtribute(JEVisObject jsonChannel) throws JEVisException {
        JEVisClass channelClass = jsonChannel.getDataSource().getJEVisClass(DataCollectorTypes.Channel.MSCONSChannel.NAME);
        JEVisType targetIdType = channelClass.getType(DataCollectorTypes.Channel.MSCONSChannel.TARGETID);
        String targetString = DatabaseHelper.getObjectAsString(jsonChannel, targetIdType);
        return targetString;
    }

    private String getStartEnd(JEVisObject jeVisObject) {
        String StartEnd = null;
        try {
            StartEnd = jeVisObject.getAttribute("Date Time").getLatestSample().getValueAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return StartEnd;


    }

}
