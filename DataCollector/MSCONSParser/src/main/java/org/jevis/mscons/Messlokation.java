/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of MSCONSParser.
 * <p>
 * MSCONSParser is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * MSCONSParser is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * MSCONSParser. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * MSCONSParser is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.mscons;

import org.jevis.commons.driver.Result;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;

public class Messlokation {
    private String messlokationNumber;
    private DateTime fromDateTime;

    private DateTime untilDateTime;
    List<MsconsSample> sampleList = new ArrayList<>();

    public Messlokation(String messlokationNumber) {
        this.messlokationNumber = messlokationNumber;
    }

    public String getMesslokationNumber() {
        return messlokationNumber;
    }

    public void setMesslokationNumber(String messlokationNumber) {
        this.messlokationNumber = messlokationNumber;
    }

    public DateTime getFromDateTime() {
        return fromDateTime;
    }

    public void setFromDateTime(DateTime fromDateTime) {
        this.fromDateTime = fromDateTime;
    }

    public DateTime getUntilDateTime() {
        return untilDateTime;
    }

    public void setUntilDateTime(DateTime untilDateTime) {
        this.untilDateTime = untilDateTime;
    }

    public List<MsconsSample> getSampleList() {
        return sampleList;
    }

    public void setSampleList(List<MsconsSample> sampleList) {
        this.sampleList = sampleList;
    }



    @Override
    public String toString() {
        return "Messlokation{" +
                "messlokationNumber='" + messlokationNumber + '\'' +
                ", fromDateTime=" + fromDateTime +
                ", untilDateTime=" + untilDateTime +
                '}';
    }

    enum StartEnd {
        Start,END
    }
}
