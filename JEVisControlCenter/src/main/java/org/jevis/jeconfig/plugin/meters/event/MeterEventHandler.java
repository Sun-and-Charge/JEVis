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

public class MeterEventHandler {
    private static final Logger logger = LogManager.getLogger(MeterEventHandler.class);
    private final EventListenerList listeners = new EventListenerList();

    public void addEventListener(MeterPlanEventListener listener) {

        if (this.listeners.getListeners(JEVisEventListener.class).length > 0) {
        }

        this.listeners.add(MeterPlanEventListener.class, listener);
    }

    public void removeEventListener(MeterPlanEventListener listener) {
        this.listeners.remove(MeterPlanEventListener.class, listener);
    }

    public MeterPlanEventListener[] getEventListener() {
        return this.listeners.getListeners(MeterPlanEventListener.class);
    }

    private synchronized void notifyListeners(MeterPlanEvent event) {
        logger.debug("SampleHandlerEvent: {}", event);
        for (MeterPlanEventListener l : this.listeners.getListeners(MeterPlanEventListener.class)) {
            l.fireEvent(event);
        }
    }

    public void fireEvent(MeterPlanEvent event) {
        notifyListeners(event);
    }
}
