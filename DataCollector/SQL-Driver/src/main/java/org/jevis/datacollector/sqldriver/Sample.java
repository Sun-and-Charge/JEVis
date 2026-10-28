/**
 * Copyright (C) JEVis contributors
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

import org.jevis.api.JEVisSample;

public class Sample {

    private final JEVisSample jeVisSample;
    private final SampleStatus sampleStatus;

    public Sample(JEVisSample jeVisSample, SampleStatus sampleStatus) {
        this.jeVisSample = jeVisSample;
        this.sampleStatus = sampleStatus;
    }

    public JEVisSample getJeVisSample() {
        return jeVisSample;
    }

    public SampleStatus getSampleStatus() {
        return sampleStatus;
    }

    @Override
    public String toString() {
        return "Sample{" +
                "jeVisSample=" + jeVisSample +
                ", sampleStatus=" + sampleStatus +
                '}';
    }
}
