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
package org.jevis.jeconfig.plugin.dashboard.datahandler;

import org.jevis.commons.database.SampleHandler;

import java.util.EventObject;

public class SampleHandlerEvent extends EventObject {

    private final TYPE type;
    /**
     * Constructs a prototypical Event.
     *
     * @param source The object on which the Event initially occurred.
     * @param type
     * @throws IllegalArgumentException if source is null.
     */
    public SampleHandlerEvent(Object source, TYPE type) {

        super(source);
        this.type = type;
    }

    @Override
    public String toString() {
        return "SampleHandlerEvent{" +
                "type=" + type +
                ", source=" + source +
                '}';
    }

    public enum TYPE {
        UPDATE
    }


}
