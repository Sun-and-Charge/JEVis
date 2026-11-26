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

import org.joda.time.DateTime;

public class MsconsSample {

    private DateTime start;
    private DateTime end;

    private double quantity;

    private String unit;

    /** OBIS code of the register (PIA+5), e.g. 1-1:1.29.0; null when the message has no PIA segment */
    private String obis;

    /** QTY qualifier: 220 = true value, 67 = substitute value, 201 = proposed value */
    private String qualifier = "220";

    public String getObis() {
        return obis;
    }

    public void setObis(String obis) {
        this.obis = obis;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public DateTime getStart() {
        return start;
    }

    public void setStart(DateTime start) {
        this.start = start;
    }

    public DateTime getEnd() {
        return end;
    }

    public void setEnd(DateTime end) {
        this.end = end;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Override
    public String toString() {
        return "MsconsSample{" +
                "start=" + start +
                ", end=" + end +
                ", quantity=" + quantity +
                ", unit='" + unit + '\'' +
                '}';
    }
}
