/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.chart;

import org.joda.time.DateTime;

/**
 * One value of a data row: timestamp, value ({@link Double}, or {@link String} for text data rows, may be
 * {@code null}) and the note of the sample.
 */
public class ChartSample {
    private DateTime timestamp;
    private Object value;
    private String note;

    public ChartSample(DateTime timestamp, Object value, String note) {
        this.timestamp = timestamp;
        this.value = value;
        this.note = note;
    }

    public DateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(DateTime timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * @return epoch milliseconds of the timestamp
     */
    public long getMillis() {
        return timestamp.getMillis();
    }

    /**
     * @return a {@link Double}, a {@link String} (text data rows) or {@code null}
     */
    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    /**
     * @return the value as double, like {@code JEVisSample.getValueAsDouble()}
     * @throws IllegalStateException if the value is not a number
     */
    public double getDouble() {
        if (value instanceof Number) return ((Number) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Value is not a number: " + value, e);
            }
        }
        throw new IllegalStateException("Value is not a number: " + value);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "ChartSample{" + timestamp + ", " + value + ", '" + note + "'}";
    }
}
