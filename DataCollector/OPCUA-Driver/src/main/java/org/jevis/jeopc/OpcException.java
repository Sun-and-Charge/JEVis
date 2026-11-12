/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of OPCUA-Driver.
 * <p>
 * OPCUA-Driver is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * OPCUA-Driver is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * OPCUA-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * OPCUA-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeopc;

/**
 * Error of an OPC UA request: an error response of the opcua-bridge (see docs/OPCUA-BRIDGE.md) or a problem with
 * the bridge process itself.
 */
public class OpcException extends Exception {

    /** Unknown command or parameter. */
    public static final String BAD_REQUEST = "bad_request";
    /** No session, connect first. */
    public static final String NOT_CONNECTED = "not_connected";
    /** The connection to the server failed. */
    public static final String CONNECT_FAILED = "connect_failed";
    /** The request did not finish in time (bridge or Java side). */
    public static final String TIMEOUT = "timeout";
    /** Service fault or bad status of the OPC UA server, see {@link #getStatus()}. */
    public static final String OPCUA = "opcua";
    /** Internal error of the bridge. */
    public static final String INTERNAL = "internal";
    /** Java side only: the bridge program is missing, could not be started or ended unexpectedly. */
    public static final String BRIDGE = "bridge";

    private final String code;
    private final String status;

    public OpcException(String code, String status, String message) {
        super(message);
        this.code = code == null ? INTERNAL : code;
        this.status = status;
    }

    public OpcException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null ? INTERNAL : code;
        this.status = null;
    }

    /**
     * @return the error code, one of the constants of this class
     */
    public String getCode() {
        return code;
    }

    /**
     * @return the OPC UA status name such as {@code BadNodeIdUnknown}, or null
     */
    public String getStatus() {
        return status;
    }

    /**
     * @return true if the error is a bad status of the OPC UA server and not a connection or bridge problem
     */
    public boolean isServerStatus() {
        return OPCUA.equals(code);
    }

    @Override
    public String toString() {
        return "OpcException{" + code + (status != null ? ", " + status : "") + ": " + getMessage() + "}";
    }
}
