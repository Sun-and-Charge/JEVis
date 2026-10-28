/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of Shelly-Driver.
 * <p>
 * Shelly-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Shelly-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Shelly-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Shelly-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.shelly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ShellyParser {

    public ShellyParser(String json, Configuration config) throws JsonProcessingException {

        System.out.println();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode actualObj = mapper.readTree(json);

        JsonNode test = actualObj.findValue("total_act");

        System.out.println("Test: " + test);
        if (test.isDouble()) {
            System.out.println("2: " + test.doubleValue());
        }

        JsonNode test2 = actualObj.findParent("total_act");
        System.out.println("test2: " + test2);

        JsonNode result = actualObj.findPath("total_act");
        System.out.println(result.get("total_act"));

    }


}
