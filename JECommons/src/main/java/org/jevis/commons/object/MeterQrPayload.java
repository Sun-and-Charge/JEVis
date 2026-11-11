/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.object;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Text of the QR code of a meter (Measurement Instrument), read by the manual reading app to select the meter.
 * Used by the Control Center's meter labels and the web service.
 * <p>
 * Payload format (version 1), see docs/meter-qr-code.md:
 * {@code jevis:meter?v=1&id=<meterId>[&target=<objectId>:<attribute>][&server=<webservice url>]}
 */
public final class MeterQrPayload {
    public static final String SCHEME = "jevis:meter";
    public static final int VERSION = 1;
    /**
     * Attribute used if the "Online ID" of a meter points to an object only
     */
    public static final String DEFAULT_TARGET_ATTRIBUTE = "Value";

    private MeterQrPayload() {
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
}
