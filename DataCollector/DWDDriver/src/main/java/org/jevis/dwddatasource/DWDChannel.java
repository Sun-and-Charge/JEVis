/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of DWDDriver.
 * <p>
 * DWDDriver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * DWDDriver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * DWDDriver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * DWDDriver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.dwddatasource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisType;
import org.jevis.commons.DatabaseHelper;
import org.jevis.commons.driver.DataCollectorTypes;
import org.jevis.commons.driver.dwd.Aggregation;
import org.jevis.commons.driver.dwd.Attribute;
import org.joda.time.DateTime;

public class DWDChannel {
    private static final Logger logger = LogManager.getLogger(DWDChannel.class);
    private final JEVisObject object;
    private Long id;
    private String name;
    private Attribute attribute;
    private Aggregation aggregation;
    private String dataName;
    private String target;
    private DateTime lastReadout;


    public DWDChannel(JEVisObject channelObject) {
        this.object = channelObject;

        try {
            JEVisType idAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.ID);
            JEVisType attributeAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.ATTRIBUTE);
            JEVisType aggregationAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.AGGREGATION);
            JEVisType dataNameAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.DATA_NAME);
            JEVisType targetAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.TARGET);
            JEVisType lastReadoutAttribute = channelObject.getJEVisClass().getType(DataCollectorTypes.Channel.DWDChannel.LAST_READOUT);

            id = DatabaseHelper.getObjectAsLong(channelObject, idAttribute);
            name = channelObject.getName();
            attribute = Attribute.valueOf(DatabaseHelper.getObjectAsString(channelObject, attributeAttribute));
            aggregation = Aggregation.getEnum(DatabaseHelper.getObjectAsString(channelObject, aggregationAttribute));
            dataName = DatabaseHelper.getObjectAsString(channelObject, dataNameAttribute);
            target = DatabaseHelper.getObjectAsString(channelObject, targetAttribute);
            lastReadout = DatabaseHelper.getObjectAsDate(channelObject, lastReadoutAttribute);

        } catch (Exception e) {
            logger.error(e);
        }
    }

    public JEVisObject getObject() {
        return object;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Attribute getAttribute() {
        return attribute;
    }

    public Aggregation getAggregation() {
        return aggregation;
    }

    public String getDataName() {
        return dataName;
    }

    public String getTarget() {
        return target;
    }

    public DateTime getLastReadout() {
        return lastReadout;
    }
}
