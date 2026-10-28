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
package org.jevis.commons.driver;

import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

/**
 * @author bf
 */
public class DriverProperty {

    private JEVisObject driver;
    private DateTime fileDate;
    private String className;
    private String jevisName;
    private String fileSource;

    DriverProperty(JEVisObject driver, DateTime fileDate, String className, String jevisName, String fileSource) {
        this.driver = driver;
        this.fileDate = fileDate;
        this.className = className;
        this.jevisName = jevisName;
        this.fileSource = fileSource;
    }

    public JEVisObject getDriver() {
        return driver;
    }

    public void setDriver(JEVisObject driver) {
        this.driver = driver;
    }

    public DateTime getFileDate() {
        return fileDate;
    }

    public void setFileDate(DateTime fileDate) {
        this.fileDate = fileDate;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getJevisName() {
        return jevisName;
    }

    public void setJevisName(String jevisName) {
        this.jevisName = jevisName;
    }

    public String getFileSource() {
        return fileSource;
    }

    public void setFileSource(String fileSource) {
        this.fileSource = fileSource;
    }

    @Override
    public String toString() {
        return "DriverProperty{" +
                "driver=" + driver +
                ", fileDate=" + fileDate +
                ", className='" + className + '\'' +
                ", jevisName='" + jevisName + '\'' +
                ", fileSource='" + fileSource + '\'' +
                '}';
    }
}
