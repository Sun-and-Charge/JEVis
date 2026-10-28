/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.meters.event;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisEventListener;

import javax.swing.event.EventListenerList;

public class PrecisionEventHandler {
    private static final Logger logger = LogManager.getLogger(PrecisionEventHandler.class);
    private final EventListenerList listeners = new EventListenerList();

    public void addEventListener(PrecisionEventListener listener) {

        if (this.listeners.getListeners(JEVisEventListener.class).length > 0) {
        }

        this.listeners.add(PrecisionEventListener.class, listener);
    }

    public void removeEventListener(PrecisionEventListener listener) {
        this.listeners.remove(PrecisionEventListener.class, listener);
    }

    public PrecisionEventListener[] getEventListener() {
        return this.listeners.getListeners(PrecisionEventListener.class);
    }

    private synchronized void notifyListeners(PrecisionEvent event) {
        logger.error("PrecisionEvent: {}", event);
        for (PrecisionEventListener l : this.listeners.getListeners(PrecisionEventListener.class)) {
            l.fireEvent(event);
        }
    }

    public void fireEvent(PrecisionEvent event) {
        notifyListeners(event);
    }
}
