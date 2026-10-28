/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of EC-Predictor-Driver.
 * <p>
 * EC-Predictor-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * EC-Predictor-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * EC-Predictor-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * EC-Predictor-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.ecp;

import org.jevis.api.JEVisObject;

public class ECChannel {

    private final JEVisObject channelObject;
    private final JEVisObject source;
    private final JEVisObject target;

    public ECChannel(JEVisObject channelObject, JEVisObject source, JEVisObject target) {
        this.channelObject = channelObject;
        this.source = source;
        this.target = target;
    }

    public JEVisObject getChannelObject() {
        return channelObject;
    }

    public JEVisObject getSource() {
        return source;
    }

    public JEVisObject getTarget() {
        return target;
    }

    @Override
    public String toString() {
        return "org.jevis.ecpdatasource.ECChannel{" +
                "source=" + source +
                ", target=" + target +
                '}';
    }
}
