/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dimpex2;

import java.util.EventObject;

public class DimpExEvent extends EventObject {

    private final DimpExEvent.TYPE type;
    private final Object object;

    private final String message;

    public DimpExEvent(Object source, DimpExEvent.TYPE type, Object object, String message) {
        super(source);
        this.type = type;
        this.object = object;
        this.message = message;
    }

    public DimpExEvent.TYPE getType() {
        return type;
    }

    public Object getObject() {
        return object;
    }

    public String getMessage() {
        return message;
    }

    public enum TYPE {
        OBJECT_TMPFILE_CREATE
    }
}

