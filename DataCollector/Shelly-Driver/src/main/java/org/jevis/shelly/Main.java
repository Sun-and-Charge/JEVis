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

public class Main {


    public static void main(String[] args) throws Exception {


        Configuration configuration = new Configuration("C:/Users/fs/Documents/test.conf");


        ShellyCloudClient shellyCloudClient = new ShellyCloudClient(configuration);
    }
}
