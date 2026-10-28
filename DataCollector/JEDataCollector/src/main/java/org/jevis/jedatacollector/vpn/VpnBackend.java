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

import java.util.List;

/**
 * System side of the VPN handling: creates connectors and controls tunnel processes per profile.
 */
public interface VpnBackend {

    VpnConnector createConnector(String profile);

    /**
     * @return profiles for which a session started by the collector was never closed cleanly
     * (leftovers of a crash or kill -9)
     */
    List<String> findOrphanedSessions();

    TunnelStatus status(String profile);

    /**
     * Stops the tunnel process of the profile (SIGTERM, then SIGKILL). Must never throw.
     *
     * @return true if it is verified that the process is gone
     */
    boolean forceStop(String profile);

    void clearMarker(String profile);

    enum TunnelStatus {
        RUNNING,
        STOPPED,
        /**
         * The status could not be determined, e.g. because sudo or the wrapper is not set up.
         */
        UNKNOWN
    }
}
