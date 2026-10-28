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
package org.jevis.commons.json;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisObject;

import java.io.*;

/**
 * @author fs
 */
public class JsonFileExporter {
    private static final Logger logger = LogManager.getLogger(JsonFileExporter.class);

    public static void writeToFile(File file, JsonObject obj) {

//        Gson gson = new Gson();
//        Gson gson = new GsonBuilder().setPrettyPrinting().create();
//        String json = gson.toJson(obj);

        try {
            //write converted json data to a file named "file.json"
            FileWriter writer = new FileWriter(file);
            writer.write(JsonTools.prettyObjectMapper().writeValueAsString(obj));
            writer.close();

        } catch (IOException e) {
            logger.fatal(e);
        }

    }

    public static JEVisObject loadFromFile(File file) {
//        Gson gson = new Gson();

        try {

            BufferedReader br = new BufferedReader(
                    new FileReader(file));

            //convert the json string back to object
            JsonObject obj = JsonTools.objectMapper().readValue(br, JsonObject.class);

            logger.info(obj);

        } catch (IOException e) {
            logger.fatal(e);
        }

        return null;
    }

}
