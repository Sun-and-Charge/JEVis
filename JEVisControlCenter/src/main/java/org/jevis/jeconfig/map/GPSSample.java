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
package org.jevis.jeconfig.map;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;

/**
 *
 * @author broder
 */
public class GPSSample {

    private final StringProperty date;
    private final DoubleProperty longitude;
    private final DoubleProperty latitude;

    public GPSSample(Double latValue, Double longValue, DateTime timestamp) {
        date = new SimpleStringProperty(timestamp.toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
        longitude = new SimpleDoubleProperty(longValue);
        latitude = new SimpleDoubleProperty(latValue);
    }

    public String getDate() {
        return date.get();
    }

    public Double getLongitude() {
        return longitude.get();
    }

    public Double getLatitude() {
        return latitude.get();
    }

}
