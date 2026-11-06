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
package org.jevis.commons.ws.sql.local;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * {@link JEVisSample} read by a {@link LocalDataSource}; value conversion like {@code JEVisSampleWS}.
 * <p>
 * <b>In-memory changes:</b> {@link #setValue(Object)}, {@link #setValue(Object, JEVisUnit)} and
 * {@link #setNote(String)} change this sample object only, e.g. when an analysis replaces values that violate a
 * limit. Nothing is ever written to the database: {@link #commit()} throws {@link UnsupportedOperationException}.
 */
public class LocalSample implements JEVisSample {

    /**
     * Value returned instead of a password hash, same as the web service.
     */
    static final String PASSWORD_MASK = "********";
    private static final Logger logger = LogManager.getLogger(LocalSample.class);
    private static final DateTimeFormatter SAMPLE_DTF = ISODateTimeFormat.dateTime();

    private final LocalDataSource ds;
    private final LocalAttribute attribute;
    private final JsonSample json;
    private final DateTime timestamp;
    private Object valueObj = null;
    private JEVisFile file = null;
    private boolean changed = false;

    LocalSample(LocalDataSource ds, JsonSample json, LocalAttribute attribute) {
        this.ds = ds;
        this.json = json;
        this.attribute = attribute;
        this.timestamp = SAMPLE_DTF.parseDateTime(json.getTs());
        objectifyValue();
    }

    private static NumberFormat numberFormat() {
        return NumberFormat.getInstance(Locale.US);
    }

    /**
     * Parses the stored string by the primitive type of the attribute, like the client.
     */
    private void objectifyValue() {
        valueObj = null;
        String value = json.getValue();
        if (value == null) {
            return;
        }
        try {
            int type = attribute.getPrimitiveType();
            if (type == JEVisConstants.PrimitiveType.DOUBLE) {
                valueObj = Double.valueOf(value);
                return;
            } else if (type == JEVisConstants.PrimitiveType.LONG) {
                valueObj = Long.valueOf(value);
                return;
            } else if (type == JEVisConstants.PrimitiveType.BOOLEAN) {
                valueObj = getValueAsBoolean();
                return;
            }
        } catch (Exception ex) {
            logger.debug("Error while casting value '{}' of attribute {}", value, attribute, ex);
            valueObj = null;
        }
        valueObj = value;
    }

    @Override
    public DateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public Object getValue() {
        if (valueObj != null) {
            return valueObj;
        }
        return json.getValue();
    }

    @Override
    public String getValueAsString() {
        return json.getValue();
    }

    @Override
    public Long getValueAsLong() {
        try {
            return valueObj instanceof Long ? (Long) valueObj : numberFormat().parse(getValueAsString()).longValue();
        } catch (Exception ex) {
            return 0L;
        }
    }

    @Override
    public Long getValueAsLong(JEVisUnit unit) throws JEVisException {
        double lValue = getValueAsLong().doubleValue();
        Double dValue = getUnit().convertTo(unit, lValue);
        return dValue.longValue();
    }

    @Override
    public Double getValueAsDouble() {
        return valueObj instanceof Double ? (Double) valueObj : Double.parseDouble(getValueAsString());
    }

    @Override
    public Double getValueAsDouble(JEVisUnit unit) throws JEVisException {
        return getUnit().convertTo(unit, getValueAsDouble());
    }

    @Override
    public Boolean getValueAsBoolean() {
        if (valueObj instanceof Boolean) {
            return (Boolean) valueObj;
        }
        String value = json.getValue();
        if ("1".equals(value)) {
            return true;
        } else if ("0".equals(value)) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * The file of a file sample, read from the file directory of the web service; {@code null} if it is missing.
     */
    @Override
    public JEVisFile getValueAsFile() {
        if (file == null || file.getBytes() == null) {
            file = ds.readFile(attribute, timestamp, getValueAsString());
        }
        return file;
    }

    @Override
    public JEVisSelection getValueAsSelection() {
        throw new UnsupportedOperationException("Selections are not supported");
    }

    @Override
    public JEVisMultiSelection getValueAsMultiSelection() {
        throw new UnsupportedOperationException("Selections are not supported");
    }

    /**
     * Changes the value of this in-memory sample only; it is never persisted.
     */
    @Override
    public void setValue(Object value) throws ClassCastException {
        valueObj = value;
        json.setValue(value == null ? "" : value.toString());
        changed = true;
    }

    /**
     * Converts the value from {@code unit} into the input unit of the attribute and sets it, in memory only (never
     * persisted). Only {@link Double} and {@link Long} values are accepted, like the client.
     */
    @Override
    public void setValue(Object value, JEVisUnit unit) throws JEVisException, ClassCastException {
        double converted;
        if (value instanceof Double) {
            converted = unit.convertTo(getUnit(), Double.parseDouble(value.toString()));
        } else if (value instanceof Long) {
            converted = unit.convertTo(getUnit(), Long.parseLong(value.toString()));
        } else {
            throw new ClassCastException();
        }
        json.setValue(converted + "");
        objectifyValue();
        changed = true;
    }

    @Override
    public JEVisAttribute getAttribute() {
        return attribute;
    }

    @Override
    public String getNote() {
        return json.getNote() == null ? "" : json.getNote();
    }

    /**
     * Changes the note of this in-memory sample only; it is never persisted.
     */
    @Override
    public void setNote(String note) {
        json.setNote(note);
        changed = true;
    }

    @Override
    public JEVisUnit getUnit() throws JEVisException {
        return attribute.getInputUnit();
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public void commit() {
        throw LocalDataSource.readOnly("commit");
    }

    @Override
    public void rollBack() {
        throw LocalDataSource.readOnly("rollBack");
    }

    /**
     * @return {@code true} if the value or note was changed in memory
     */
    @Override
    public boolean hasChanged() {
        return changed;
    }

    @Override
    public String toString() {
        return "LocalSample{ ts:" + getTimestamp() + " Value: " + getValueAsString() + "}";
    }
}
