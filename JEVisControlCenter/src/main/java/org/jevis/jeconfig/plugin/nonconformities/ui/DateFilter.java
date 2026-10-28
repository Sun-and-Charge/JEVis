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
package org.jevis.jeconfig.plugin.nonconformities.ui;

import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityData;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.jevis.jeconfig.plugin.nonconformities.ui.DateFilter.DateField.*;

public class DateFilter {

    DateTime from;
    DateTime until;
    DateField dateField;

    public DateFilter(DateField dateField, DateTime from, DateTime until) {
        this.dateField = dateField;
        this.from = from;
        this.until = until;
    }

    public DateTime getFromDate() {
        return from;
    }

    public DateTime getUntilDate() {
        return until;
    }

    public DateField dateField() {
        return dateField;
    }


    private List<DateTime> getDate(NonconformityData data) {
        List<DateTime> dateTimes = new ArrayList<>();
        if (dateField == COMPLETED) {
            setDoneDate(data, dateTimes);
        } else if (dateField == DateFilter.DateField.CREATED) {
            setCreateDate(data, dateTimes);
        } else if (dateField == IMPLEMENTATION) {
            setDeadLine(data, dateTimes);
        } else if (dateField == ALL) {
            setDoneDate(data, dateTimes);
            setCreateDate(data, dateTimes);
            setDeadLine(data, dateTimes);
        }
        return dateTimes;
    }

    private static void setDeadLine(NonconformityData data, List<DateTime> dateTimes) {
        if (data.getDeadLine() != null) {
            dateTimes.add(data.getDeadLine());
        }
    }

    private static void setCreateDate(NonconformityData data, List<DateTime> dateTimes) {
        if (data.getCreateDate() != null) {
            dateTimes.add(data.getCreateDate());
        }
    }

    private static void setDoneDate(NonconformityData data, List<DateTime> dateTimes) {
        if (data.getDoneDate() != null) {
            dateTimes.add(data.getDoneDate());
        }
    }

    public boolean show(NonconformityData data) {
        if (getDate(data).size() == 0) {
            return true;
        }
        try {
            Optional<DateTime> optionalDateTimeMax = getDate(data).stream().max(DateTime::compareTo);
            Optional<DateTime> optionalDateTimeMin = getDate(data).stream().min(DateTime::compareTo);

            if (optionalDateTimeMax.isPresent() && optionalDateTimeMin.isPresent()) {
                if(optionalDateTimeMax.get().isBefore(getFromDate())) return false;
                if(optionalDateTimeMin.get().isAfter(getUntilDate())) return false;
            }else {
                return false;
            }
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
    }

    @Override
    public String toString() {
        return "DateFilter{" +
                "from=" + from +
                ", until=" + until +
                ", dateField=" + dateField +
                '}';
    }

    public static enum DateField {
        ALL, IMPLEMENTATION, COMPLETED, CREATED
    }
}
