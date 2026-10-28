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
package org.jevis.commons.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 *
 * @author bf
 */
public class JEVisServerConnectionCLI {

    private String _db;
    private String _port;
    private String _schema;
    private String _user;
    private String _pw;
    private String _jevisUser;
    private String _jevisPW;

    public JEVisServerConnectionCLI(String path) {
        Properties prop = new Properties();

        try {
            //load a properties file
            prop.load(new FileInputStream(path));

            //get the property value and print it out
            _db = prop.getProperty("Server");
            _port = prop.getProperty("Port");
            _schema = prop.getProperty("Schema");
            _user = prop.getProperty("User");
            _pw = prop.getProperty("PW");
            _jevisUser = prop.getProperty("JEVisUser");
            _jevisPW = prop.getProperty("JEVisPW");

        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public String getDb() {
        return _db;
    }

    public String getPort() {
        return _port;
    }

    public String getSchema() {
        return _schema;
    }

    public String getUser() {
        return _user;
    }

    public String getPw() {
        return _pw;
    }

    public String getJevisUser() {
        return _jevisUser;
    }

    public String getJevisPW() {
        return _jevisPW;
    }
}
