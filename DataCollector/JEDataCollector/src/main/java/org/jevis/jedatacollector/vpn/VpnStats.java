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

/**
 * Traffic counters of a tunnel session as reported by the OpenVPN management interface.
 */
public class VpnStats {

    public static final VpnStats EMPTY = new VpnStats(0, 0);

    private final long bytesIn;
    private final long bytesOut;

    public VpnStats(long bytesIn, long bytesOut) {
        this.bytesIn = bytesIn;
        this.bytesOut = bytesOut;
    }

    public long getBytesIn() {
        return bytesIn;
    }

    public long getBytesOut() {
        return bytesOut;
    }

    @Override
    public String toString() {
        return "in=" + bytesIn + " B, out=" + bytesOut + " B";
    }
}
