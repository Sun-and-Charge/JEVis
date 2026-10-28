/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of SQL-Driver.
 * <p>
 * SQL-Driver is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * SQL-Driver is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * SQL-Driver. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * SQL-Driver is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.datacollector.sqldriver;

import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.jeapi.ws.JEVisDataSourceWS;

/**
 * Debug Class
 */
public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Start Debug: FuE server, User: " + args[0] + " sql Server: " + args[2] + " JEVis: " + args[3]);

        JEVisDataSource dataSource = new JEVisDataSourceWS(args[3]);
        dataSource.connect(args[0], args[1]);
        JEVisObject serverOBJ = dataSource.getObject(Long.parseLong(args[2]));
        SQLDriver driver = new SQLDriver();
        driver.initialize(serverOBJ);
        driver.run();
    }

}