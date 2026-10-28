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
import org.jevis.api.*;
import org.jevis.commons.DatabaseHelper;
import org.jevis.commons.object.plugin.TargetHelper;
import org.joda.time.DateTime;

public class LoytecXmlDlOutputChannel implements LoytecXmlDlOutputChannelClass {
    private final static Logger log = LogManager.getLogger(LoytecXmlDlChannel.class.getName());

    private final JEVisObject channelObject;
    private final Helper helper = new Helper();
    private String name;
    private String nodeId;
    private DateTime lastReadout;
    private JEVisAttribute targetAtt;
    private JEVisAttribute statusLog;
    private String targetString;
    private JEVisObject targetObj;

    public LoytecXmlDlOutputChannel(JEVisObject channelObject) throws JEVisException {
        this.channelObject = channelObject;

        try {
            channelObject.getDataSource().reloadAttribute(channelObject);
        } catch (Exception e) {
            log.error("Could not reload attributes for object {}:{}", channelObject.getName(), channelObject.getID(), e);
        }

        this.update();

        log.debug("Init LoytecXmlDlChannel: " + name + " OPC Node ID: " + nodeId + " Tar: " + targetObj.getName() + ":" + targetObj.getID() + "  LastR: " + lastReadout);
    }

    public void update() throws JEVisException {
        log.debug("Getting channel name");
        name = channelObject.getName();
        log.debug("Getting channel trend id");
        nodeId = helper.getValue(channelObject, OPC_NODE_ID);
        log.debug("Getting channel target id");


        JEVisAttribute channelTargetAtt = channelObject.getAttribute(TARGET_ID);

        JEVisSample sample = channelTargetAtt.getLatestSample();
        if (sample != null) {
            targetString = sample.getValueAsString();
        }

        TargetHelper th = new TargetHelper(channelObject.getDataSource(), channelTargetAtt);
        if (!th.getAttribute().isEmpty()) {
            targetAtt = th.getAttribute().get(0);
        }
        targetObj = targetAtt.getObject();
        targetObj.getID();


        log.debug("Getting channel last read out");

        //JEVisAttribute lastReadOut = channelObject.getAttribute(LAST_READOUT);
        //channelObject.getDataSource().reloadAttribute(lastReadOut);
        JEVisClass channelClass = channelObject.getJEVisClass();
        JEVisType lastReadoutType = channelClass.getType(LAST_READOUT);
        lastReadout = DatabaseHelper.getObjectAsDate(channelObject, lastReadoutType);

        //targetObj.getDataSource().reloadAttribute(targetAtt);
        //lastSampleTimeStamp = targetAtt.getLatestSample().getTimestamp();

        JEVisType statusLogType = channelClass.getType(STATUS_LOG);
        statusLog = channelObject.getAttribute(statusLogType);
    }

    @Override
    public String getNodeID() {
        return nodeId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getTrendId() {
        return "";
    }

    @Override
    public Long getTargetId() {
        return targetAtt.getObjectID();
    }

    @Override
    public DateTime getLastReadout() {
        return lastReadout;
    }

    @Override
    public JEVisObject getJeVisObject() {
        return channelObject;
    }

    @Override
    public JEVisAttribute getTarget() {
        return targetAtt;
    }

    @Override
    public String getTargetString() {
        return targetString;
    }

    @Override
    public JEVisAttribute getStatusLog() {
        return statusLog;
    }
}
