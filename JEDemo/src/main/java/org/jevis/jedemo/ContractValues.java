/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.object.plugin.RangingValues;
import org.joda.time.DateTime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Reads the values the invoices of the demo cost cases are priced with, the same way the result calculation
 * ({@code org.jevis.commons.trc.ResultCalculator}) reads them: prices as the last sample at or before an instant
 * ({@code NON_PERIODIC}), yearly amounts as the last sample inside a calendar year ({@code YEARLY_VALUE}), ranging
 * tables from the latest sample ({@code RANGING_VALUE}); consumption as the sum and peaks as the maximum of a row.
 */
public class ContractValues {

    private static final DateTime EARLIEST = new DateTime(1990, 1, 1, 0, 0);
    private final JEVisDataSource ds;
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    public ContractValues(JEVisDataSource ds) {
        this.ds = ds;
    }

    private JEVisAttribute attribute(Ref object, String attribute) throws JEVisException {
        if (object == null || !object.isResolved() || attribute == null) return null;
        JEVisObject o = ds.getObject(object.getId());
        return o != null ? o.getAttribute(attribute) : null;
    }

    /**
     * @return the last numeric sample at or before the instant, null if there is none
     */
    public BigDecimal at(Ref object, String attribute, Instant instant) throws JEVisException {
        JEVisAttribute att = attribute(object, attribute);
        if (att == null) return null;
        List<JEVisSample> samples = att.getSamples(EARLIEST, new DateTime(instant.toEpochMilli()));
        for (int i = samples.size() - 1; i >= 0; i--) {
            JEVisSample sample = samples.get(i);
            if (sample.getTimestamp().getMillis() > instant.toEpochMilli()) continue;
            Double value = sample.getValueAsDouble();
            if (value != null && !value.isNaN() && !value.isInfinite()) return BigDecimal.valueOf(value);
        }
        return null;
    }

    /**
     * @return the last numeric sample in [from, to), null if there is none
     */
    public BigDecimal lastIn(Ref object, String attribute, Instant from, Instant to) throws JEVisException {
        JEVisAttribute att = attribute(object, attribute);
        if (att == null) return null;
        List<JEVisSample> samples = att.getSamples(new DateTime(from.toEpochMilli()), new DateTime(to.toEpochMilli() - 1));
        for (int i = samples.size() - 1; i >= 0; i--) {
            JEVisSample sample = samples.get(i);
            long ts = sample.getTimestamp().getMillis();
            if (ts < from.toEpochMilli() || ts >= to.toEpochMilli()) continue;
            Double value = sample.getValueAsDouble();
            if (value != null && !value.isNaN() && !value.isInfinite()) return BigDecimal.valueOf(value);
        }
        return null;
    }

    /**
     * @return the value of the latest ranging table for the determination value, null if there is none
     */
    public BigDecimal ranging(Ref object, String attribute, double determination) throws JEVisException {
        JEVisAttribute att = attribute(object, attribute);
        if (att == null) return null;
        JEVisSample latest = att.getLatestSample();
        if (latest == null) return null;
        RangingValues values = new RangingValues();
        try {
            mapper.readerForUpdating(values).readValue(latest.getValueAsString());
        } catch (Exception e) {
            return null;
        }
        Double value = values.getValue(determination);
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    /**
     * Sum and maximum of the numeric samples of a row's "Value" in [from, to).
     */
    public static final class Aggregate {
        private double sum;
        private double max = -Double.MAX_VALUE;
        private int count;

        void add(double value) {
            sum += value;
            max = Math.max(max, value);
            count++;
        }

        public double getSum() {
            return sum;
        }

        /**
         * @return the maximum, null without samples
         */
        public Double getMax() {
            return count > 0 ? max : null;
        }

        public int getCount() {
            return count;
        }
    }

    /**
     * @return sum and maximum of the row's values in [from, to); null if the row or its "Value" does not exist
     */
    public Aggregate aggregate(Ref row, Instant from, Instant to) throws JEVisException {
        JEVisAttribute att = attribute(row, "Value");
        if (att == null) return null;
        Aggregate aggregate = new Aggregate();
        for (JEVisSample s : att.getSamples(new DateTime(from.toEpochMilli()), new DateTime(to.toEpochMilli()))) {
            long ts = s.getTimestamp().getMillis();
            if (ts < from.toEpochMilli() || ts >= to.toEpochMilli()) continue;
            Double v = s.getValueAsDouble();
            if (v == null || v.isNaN() || v.isInfinite()) continue;
            aggregate.add(v);
        }
        return aggregate;
    }

    /**
     * @return time stamp of the latest sample of the row's "Value", null if there is none
     */
    public Instant latest(Ref row) throws JEVisException {
        JEVisAttribute att = attribute(row, "Value");
        JEVisSample latest = att != null ? att.getLatestSample() : null;
        return latest != null ? Instant.ofEpochMilli(latest.getTimestamp().getMillis()) : null;
    }
}
