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
 * One tunnel session for one profile. Instances are not reused after {@link #disconnect()}.
 */
public interface VpnConnector {

    /**
     * Establishes the tunnel and blocks until it is usable.
     *
     * @throws VpnException if the tunnel could not be established within the configured timeout
     */
    void connect(VpnConfig config) throws VpnException, InterruptedException;

    /**
     * Closes the tunnel. Must never throw and must work on an interrupted thread.
     *
     * @return true if it is verified that the tunnel process is gone
     */
    boolean disconnect();

    /**
     * @return true while the tunnel process is running and connected
     */
    boolean isAlive();

    /**
     * @return true if the tunnel was already running when connecting (e.g. started manually).
     * Such tunnels are used but never closed by the collector.
     */
    boolean isExternal();

    VpnStats getStats();

    /**
     * @return human readable details for logs and alert mails (pid, state, last log lines)
     */
    String getDiagnostics();
}
