/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of SOAP-Driver.
 * <p>
 * SOAP-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * SOAP-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * SOAP-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * SOAP-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.soapdatasource;

import org.joda.time.DateTime;

/**
 *
 * @author bf
 */
public class Channel {

    private String _path;
    private DateTime _lastReadout;
    private String _template;

    public String getTemplate() {
        return _template;
    }

    public void setTemplate(String _template) {
        this._template = _template;
    }

    public String getPath() {
        return _path;
    }

    public void setPath(String _path) {
        this._path = _path;
    }

    public DateTime getLastReadout() {
        return _lastReadout;
    }

    public void setLastReadout(DateTime _lastReadout) {
        this._lastReadout = _lastReadout;
    }

}
