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
package org.jevis.commons.export;

import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class MetaObject {

    JsonObject object;
    private Mode mode = Mode.NONE;

    public MetaObject(JsonObject obj) {
        this.object = obj;
    }

    public String getKey() {
        return String.valueOf(object.getId());
    }

    public JsonObject getObject() {
        return object;
    }

    public List<String> getRequrements() {
        List<String> keyList = new ArrayList<>();
        for (JsonAttribute att : object.getAttributes()) {
            //check based on GUI Type? hardcode test
            if (att.getType().equals("Input")) {
                if (att.getLatestValue() != null) {
                    keyList.add(att.getLatestValue().getValue().split(";")[0]);
                }
            }
        }
        return keyList;
    }

    public Mode getMode() {
        return mode;
    }


    enum Mode {
        ALL_DATA, NONE
    }

}
