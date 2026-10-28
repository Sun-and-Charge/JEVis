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
package org.jevis.jeconfig.plugin.loadprofile.data;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.joda.time.DateTime;

public class MixedLoadProfile {
    private final ObjectProperty<DateTime> dateTime = new SimpleObjectProperty<>(this, "dateTime");
    private final ObjectProperty<Object> workdayValue = new SimpleObjectProperty<>(this, "workdayValue");
    private final ObjectProperty<Object> holidayValue = new SimpleObjectProperty<>(this, "holidayValue");

    public MixedLoadProfile(DateTime dateTime, Object workdayValue, Object holidayValue) {
        this.dateTime.set(dateTime);
        this.workdayValue.set(workdayValue);
        this.holidayValue.set(holidayValue);
    }

    public DateTime getDateTime() {
        return dateTime.get();
    }

    public ObjectProperty<DateTime> dateTimeProperty() {
        return dateTime;
    }

    public Object getWorkdayValue() {
        return workdayValue.get();
    }

    public ObjectProperty<Object> workdayValueProperty() {
        return workdayValue;
    }

    public Object getHolidayValue() {
        return holidayValue.get();
    }

    public ObjectProperty<Object> holidayValueProperty() {
        return holidayValue;
    }
}
