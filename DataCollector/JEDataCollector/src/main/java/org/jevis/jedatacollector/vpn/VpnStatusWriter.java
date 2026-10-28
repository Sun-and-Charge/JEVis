/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDataCollector.
 * <p>
 * JEDataCollector is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEDataCollector is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDataCollector. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDataCollector is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedatacollector.vpn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.commons.driver.DataCollectorTypes;
import org.joda.time.DateTime;

/**
 * Writes connection results to the status attributes of the VPN Connection object.
 */
public class VpnStatusWriter {

    private static final Logger logger = LogManager.getLogger(VpnStatusWriter.class);

    public void connected(VpnConfig config) {
        DateTime now = DateTime.now();
        write(config, DataCollectorTypes.VpnConnection.LAST_CONNECT, now, now.toString());
    }

    public void error(VpnConfig config, String message) {
        String value = message == null ? "" : message.length() > 500 ? message.substring(0, 500) : message;
        write(config, DataCollectorTypes.VpnConnection.LAST_ERROR, DateTime.now(), value);
    }

    /**
     * Stores the traffic of a finished session, which allows monitoring the data volume per site.
     */
    public void disconnected(VpnConfig config, VpnStats stats) {
        if (stats == null || (stats.getBytesIn() == 0 && stats.getBytesOut() == 0)) return;
        DateTime now = DateTime.now();
        write(config, DataCollectorTypes.VpnConnection.BYTES_IN, now, stats.getBytesIn());
        write(config, DataCollectorTypes.VpnConnection.BYTES_OUT, now, stats.getBytesOut());
    }

    private void write(VpnConfig config, String attributeName, DateTime timestamp, Object value) {
        JEVisObject object = config == null ? null : config.getObject();
        if (object == null) return;
        try {
            JEVisAttribute attribute = object.getAttribute(attributeName);
            if (attribute != null) {
                attribute.buildSample(timestamp, value).commit();
            }
        } catch (Exception e) {
            logger.warn("Could not write {} of VPN Connection {}", attributeName, config.getName(), e);
        }
    }
}
