/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Configuration {
    private static final Logger logger = LogManager.getLogger(Configuration.class);

    public String server = "";
    public String deviceid = "";
    public String apikey = "";

    public String jevisServer = "";
    public String jevisUser = "";
    public String jevisPassword = "";
    /**
     * API key of a JEVis service account, preferred over user/password
     */
    public String jevisApiKey = "";

    public String jsonNode = "total_act";

    public Map<String, Long> mapping = new HashMap<>();

    public int total_act_power_id = 0;

    public Configuration(String file) throws ConfigurationException {
        File cfile = new File(file);
        XMLConfiguration config = new XMLConfiguration(cfile);
        System.out.println("File: " + config + " read: " + cfile.canRead());

        server = getParameter(config, "shelly.cloud", "https://shelly-83-eu.shelly.cloud");
        deviceid = getParameter(config, "shelly.device", "");
        apikey = getParameter(config, "shelly.apikey", "");
        jsonNode = getParameter(config, "shelly.jsonnode", "total_act");

        jevisServer = getParameter(config, "jevis.server", "");
        jevisUser = getParameter(config, "jevis.user", "");
        jevisApiKey = getParameter(config, "jevis.apikey", "");
        jevisPassword = getParameter(config, "jevis.password", "");

        List<HierarchicalConfiguration> dps = config.configurationsAt("mapping.dp");
        for (HierarchicalConfiguration dp : dps) {
            try {
                System.out.println(dp.getString("jevis") + " " + dp.getString("json"));
                mapping.put(dp.getString("json"), dp.getLong("jevis"));
            } catch (Exception ex) {
                logger.error(ex, ex);
            }
        }

        System.out.printf("Server: %s\nDevice: %s\njevis: %s\nuser: %s%n",
                server, deviceid, jevisServer, jevisUser);

    }


    public static String getParameter(XMLConfiguration config, String key, String defaultValue) {
        try {
            return config.getString(key);
        } catch (NullPointerException nex) {
            logger.error("Missing parameter in config file: '{}' using default value: '{}'", key, defaultValue);
            return defaultValue;
        }
    }
}
