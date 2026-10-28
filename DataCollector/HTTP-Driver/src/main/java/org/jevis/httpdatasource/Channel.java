/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of HTTP-Driver.
 * <p>
 * HTTP-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * HTTP-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * HTTP-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * HTTP-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.httpdatasource;

import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

/**
 * @author broder
 */
public class Channel {
    private String _path;
    private DateTime _lastReadout;
    private JEVisObject channelObject;
    private DateTime nextReadout;
    private String getRequestBody;

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

    public JEVisObject getChannelObject() {
        return channelObject;
    }

    public void setChannelObject(JEVisObject channelObject) {
        this.channelObject = channelObject;
    }

    public DateTime getNextReadout() {
        return nextReadout;
    }

    public void setNextReadout(DateTime nextReadout) {
        this.nextReadout = nextReadout;
    }

    public String getGetRequestBody() {
        return getRequestBody;
    }

    public void setGetRequestBody(String getRequestBody) {
        this.getRequestBody = getRequestBody;
    }
}
