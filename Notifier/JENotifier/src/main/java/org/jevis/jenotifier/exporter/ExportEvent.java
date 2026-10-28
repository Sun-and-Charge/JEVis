/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier.exporter;

import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

public abstract class ExportEvent {

    public static String CLASS_NAME = "Export Event";
    protected JEVisObject eventObject;

    public ExportEvent(JEVisObject object) {
        eventObject = object;
//        init();
    }

    public void init() {
        initAttributes();
    }

    abstract void initAttributes();

    abstract boolean isTriggered(DateTime lastUpdate);
}
