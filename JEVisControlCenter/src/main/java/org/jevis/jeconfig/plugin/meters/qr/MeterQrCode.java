/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.meters.qr;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jeconfig.plugin.meters.data.MeterData;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * QR code content and label text of one meter, used by the manual reading app to select the meter.
 * <p>
 * Payload format (version 1), see docs/meter-qr-code.md:
 * {@code jevis:meter?v=1&id=<meterId>[&target=<objectId>:<attribute>][&server=<webservice url>]}
 */
public class MeterQrCode {
    public static final String SCHEME = "jevis:meter";
    public static final int VERSION = 1;
    private static final Logger logger = LogManager.getLogger(MeterQrCode.class);
    private static final String DEFAULT_TARGET_ATTRIBUTE = "Value";

    private final long meterId;
    private final String payload;
    private final String name;
    private final String measuringPointName;
    private final String serialNumber;
    private final String path;

    public MeterQrCode(long meterId, String payload, String name, String measuringPointName, String serialNumber, String path) {
        this.meterId = meterId;
        this.payload = payload;
        this.name = name;
        this.measuringPointName = measuringPointName;
        this.serialNumber = serialNumber;
        this.path = path;
    }

    /**
     * Reads the meter's target, serial number and location from the data source.
     */
    public static MeterQrCode of(MeterData meterData, JEVisDataSource ds, ObjectRelations objectRelations) {
        JEVisObject meter = meterData.getJeVisObject();

        Long targetId = null;
        String targetAttribute = null;
        try {
            TargetHelper th = new TargetHelper(ds, meter.getAttribute(JC.MeasurementInstrument.a_OnlineID));
            if (th.isValid() && !th.getObject().isEmpty()) {
                targetId = th.getObject().get(0).getID();
                targetAttribute = th.isAttribute() && !th.getAttribute().isEmpty()
                        ? th.getAttribute().get(0).getName()
                        : DEFAULT_TARGET_ATTRIBUTE;
            }
        } catch (Exception e) {
            logger.warn("Could not resolve target of meter {}", meter.getID(), e);
        }

        String server = ds instanceof JEVisDataSourceWS ? ((JEVisDataSourceWS) ds).getHost() : null;

        String path = "";
        try {
            path = objectRelations.getObjectPath(meter);
        } catch (Exception e) {
            logger.warn("Could not get path of meter {}", meter.getID(), e);
        }

        return new MeterQrCode(meter.getID(),
                payload(meter.getID(), targetId, targetAttribute, server),
                meter.getName(),
                latestValue(meter, JC.MeasurementInstrument.a_MeasuringPointName),
                latestValue(meter, JC.MeasurementInstrument.a_SerialNumber),
                path);
    }

    /**
     * Builds the QR code text. Target and server are left out when null or empty.
     */
    public static String payload(long meterId, Long targetId, String targetAttribute, String server) {
        StringBuilder sb = new StringBuilder(SCHEME)
                .append("?v=").append(VERSION)
                .append("&id=").append(meterId);
        if (targetId != null && targetAttribute != null && !targetAttribute.isEmpty()) {
            sb.append("&target=").append(targetId).append(':').append(encode(targetAttribute));
        }
        if (server != null && !server.trim().isEmpty()) {
            String s = server.trim();
            while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
            sb.append("&server=").append(encode(s));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String latestValue(JEVisObject object, String attributeName) {
        try {
            JEVisAttribute attribute = object.getAttribute(attributeName);
            if (attribute == null) return "";
            JEVisSample sample = attribute.getLatestSample();
            return sample != null && sample.getValueAsString() != null ? sample.getValueAsString() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public long getMeterId() {
        return meterId;
    }

    public String getPayload() {
        return payload;
    }

    public String getName() {
        return name;
    }

    public String getMeasuringPointName() {
        return measuringPointName;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getPath() {
        return path;
    }
}
