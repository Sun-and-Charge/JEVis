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

import org.jevis.api.JEVisObject;
import org.jevis.commons.driver.DataCollectorTypes;
import org.joda.time.DateTimeZone;

import java.util.List;

/**
 * JEVis Class: Loytec XML-DL Dataserver
 */
public interface LoytecXmlDlServerClass extends DataCollectorTypes.DataSource.DataServer {

    // Settings
    /**
     * max is 500
     */
    Integer NUMBER_OF_SAMPLES_PER_REQUEST = 500;

    // JEVis class mapping strings
    String NAME = "Loytec XML-DL Server";
    String USER = "User";
    String PASSWORD = "Password";
    String SSL = "SSL";
    String LOGHANDLE_BASE_PATH = "LogHandle Base Path";

    // Default values
    String USER_DEFAULT = "operator";
    String DEFAULT_PASSWORD = "loytec4u";
    String DEFAULT_SSL = "false";
    String DEFAULT_PORT = "80";
    String DEFAULT_CONNECTION_TIMEOUT = "30";
    String DEFAULT_READ_TIMEOUT = "60";
    String DEFAULT_LOGHANDLE_BASE_PATH = "00/var/lib/";
    String DEFAULT_TIMEZONE = "UTC";

    // Methods to implement
    String getHost();

    Integer getPort();

    Integer getConnectionTimeout();

    Integer getReadTimeout();

    Boolean getSsl();

    String getName();

    String getUser();

    String getPassword();

    String getLogHandleBasePath();

    DateTimeZone getTimezone();

    List<LoytecXmlDlChannelDirectory> getChannelDirectories();

    JEVisObject getObject();
}
