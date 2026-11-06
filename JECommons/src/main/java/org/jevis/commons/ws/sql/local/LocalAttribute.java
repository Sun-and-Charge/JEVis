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
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import tech.units.indriya.AbstractUnit;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only {@link JEVisAttribute} of a {@link LocalDataSource}; behaves like {@code JEVisAttributeWS}. Samples are
 * read from the database on every call (not cached). Writing samples or metadata throws
 * {@link UnsupportedOperationException}.
 */
public class LocalAttribute implements JEVisAttribute {

    private static final Logger logger = LogManager.getLogger(LocalAttribute.class);
    private static final DateTimeFormatter ATT_DTF = ISODateTimeFormat.dateTime();

    private final LocalDataSource ds;
    private final JsonAttribute json;
    private volatile Integer cachedPrimitiveType = null;

    LocalAttribute(LocalDataSource ds, JsonAttribute json) {
        this.ds = ds;
        this.json = json;
    }

    JsonAttribute getJson() {
        return json;
    }

    @Override
    public String getName() {
        return json.getType();
    }

    @Override
    public JEVisType getType() throws JEVisException {
        JEVisObject obj = getObject();
        if (obj == null || obj.getJEVisClass() == null) {
            return null;
        }
        return obj.getJEVisClass().getType(getName());
    }

    @Override
    public JEVisObject getObject() {
        return ds.getObject(getObjectID());
    }

    @Override
    public Long getObjectID() {
        return json.getObjectID();
    }

    // ---------------------------------------------------------------- samples

    /**
     * All samples, like the client's {@code getSamples(null, null)} (no limit).
     */
    @Override
    public List<JEVisSample> getAllSamples() {
        return ds.getSamples(this, null, null, Long.MAX_VALUE);
    }

    /**
     * Raw samples between {@code from} and {@code to} (both inclusive, {@code null} for open, milliseconds are
     * dropped as by the REST client), without limit.
     */
    @Override
    public List<JEVisSample> getSamples(DateTime from, DateTime to) {
        return ds.getSamples(this, from, to, Long.MAX_VALUE);
    }

    /**
     * Same result as {@code GET .../samples?from&until&cwd&ap&mm&tz} of the web service; the
     * {@link org.jevis.commons.ws.sql.sg.JsonSampleGenerator} is called directly.
     */
    @Override
    public List<JEVisSample> getSamples(DateTime from, DateTime to, boolean customWorkDay, String aggregationPeriod,
                                        String manipulationMode, String timeZone) {
        return ds.getSamples(this, from, to, customWorkDay, aggregationPeriod, manipulationMode, timeZone);
    }

    /**
     * The latest sample as delivered with the attribute (no extra query), {@code null} without samples. Password
     * values are masked.
     */
    @Override
    public JEVisSample getLatestSample() {
        JsonSample latest = json.getLatestValue();
        if (latest == null || latest.getTs() == null) {
            return null;
        }
        JsonSample copy = new JsonSample();
        copy.setTs(latest.getTs());
        copy.setNote(latest.getNote());
        copy.setValue(latest.getValue());
        JsonObject obj = ds.getJson(getObjectID());
        if (obj != null && LocalDataSource.isPasswordAttribute(obj, json.getPrimitiveType(), getName())) {
            copy.setValue(LocalSample.PASSWORD_MASK);
        }
        return new LocalSample(ds, copy, this);
    }

    @Override
    public boolean hasSample() {
        return getTimestampOfFirstSample() != null;
    }

    @Override
    public DateTime getTimestampOfFirstSample() {
        try {
            return ATT_DTF.parseDateTime(json.getBegins());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public DateTime getTimestampOfLastSample() {
        try {
            return ATT_DTF.parseDateTime(json.getEnds());
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public long getSampleCount() {
        return json.getSampleCount();
    }

    // ---------------------------------------------------------------- metadata

    /**
     * The primitive type of the attribute type of the class; the stored one if the type is unknown.
     */
    @Override
    public int getPrimitiveType() {
        Integer cached = cachedPrimitiveType;
        if (cached == null) {
            try {
                JEVisType type = getType();
                cached = type != null ? type.getPrimitiveType() : json.getPrimitiveType();
            } catch (JEVisException ex) {
                cached = json.getPrimitiveType();
            }
            cachedPrimitiveType = cached;
        }
        return cached;
    }

    @Override
    public JEVisUnit getDisplayUnit() {
        try {
            return new JEVisUnitImp(json.getDisplayUnit());
        } catch (Exception ex) {
            return new JEVisUnitImp(AbstractUnit.ONE);
        }
    }

    @Override
    public JEVisUnit getInputUnit() {
        try {
            return new JEVisUnitImp(json.getInputUnit());
        } catch (Exception ex) {
            logger.debug("No unit selected using fallback unit.one");
            return new JEVisUnitImp(AbstractUnit.ONE);
        }
    }

    @Override
    public Period getDisplaySampleRate() {
        return parsePeriod(json.getDisplaySampleRate());
    }

    @Override
    public Period getInputSampleRate() {
        return parsePeriod(json.getInputSampleRate());
    }

    /**
     * Like the client: empty or unreadable is {@link Period#ZERO}.
     */
    static Period parsePeriod(String period) {
        try {
            if (period == null || period.isEmpty()) {
                return Period.ZERO;
            }
            return Period.parse(period);
        } catch (Exception ex) {
            return Period.ZERO;
        }
    }

    @Override
    public boolean isType(JEVisType type) {
        try {
            JEVisType own = getType();
            return own != null && type != null && own.getName().equals(type.getName());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public List<JEVisOption> getOptions() {
        return new ArrayList<>();
    }

    /**
     * No-op like the client (options are not stored).
     */
    @Override
    public void addOption(JEVisOption option) {
    }

    /**
     * No-op like the client (options are not stored).
     */
    @Override
    public void removeOption(JEVisOption option) {
    }

    // ---------------------------------------------------------------- write operations

    @Override
    public int addSamples(List<JEVisSample> samples) {
        throw LocalDataSource.readOnly("addSamples");
    }

    @Override
    public JEVisSample buildSample(DateTime ts, Object value) {
        throw LocalDataSource.readOnly("buildSample");
    }

    @Override
    public JEVisSample buildSample(DateTime ts, double value, JEVisUnit unit) {
        throw LocalDataSource.readOnly("buildSample");
    }

    @Override
    public JEVisSample buildSample(DateTime ts, Object value, String note) {
        throw LocalDataSource.readOnly("buildSample");
    }

    @Override
    public JEVisSample buildSample(DateTime ts, double value, String note, JEVisUnit unit) {
        throw LocalDataSource.readOnly("buildSample");
    }

    @Override
    public boolean delete() {
        throw LocalDataSource.readOnly("delete");
    }

    @Override
    public boolean deleteAllSample() {
        throw LocalDataSource.readOnly("deleteAllSample");
    }

    @Override
    public boolean deleteSamplesBetween(DateTime from, DateTime to) {
        throw LocalDataSource.readOnly("deleteSamplesBetween");
    }

    @Override
    public void setDisplayUnit(JEVisUnit unit) {
        throw LocalDataSource.readOnly("setDisplayUnit");
    }

    @Override
    public void setInputUnit(JEVisUnit unit) {
        throw LocalDataSource.readOnly("setInputUnit");
    }

    @Override
    public void setInputSampleRate(Period period) {
        throw LocalDataSource.readOnly("setInputSampleRate");
    }

    @Override
    public void setDisplaySampleRate(Period period) {
        throw LocalDataSource.readOnly("setDisplaySampleRate");
    }

    @Override
    public void commit() {
        throw LocalDataSource.readOnly("commit");
    }

    @Override
    public void rollBack() {
        throw LocalDataSource.readOnly("rollBack");
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public int compareTo(JEVisAttribute other) {
        return other.getName().compareTo(getName());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof JEVisAttribute) {
            JEVisAttribute att = (JEVisAttribute) other;
            return getObjectID().equals(att.getObjectID()) && getName().equals(att.getName());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 * getObjectID().hashCode() + (getName() != null ? getName().hashCode() : 0);
    }

    @Override
    public String toString() {
        return "LocalAttribute [ type: '" + json.getType() + "' object: '" + json.getObjectID() + "' basicType: '"
                + json.getPrimitiveType() + "' maxTS: '" + json.getEnds() + "']";
    }
}
