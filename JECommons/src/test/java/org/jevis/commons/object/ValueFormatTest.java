/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.object;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.junit.Test;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ValueFormatTest {

    private static JEVisAttribute attribute(String value) throws Exception {
        JEVisAttribute attribute = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsString()).thenReturn(value);
        when(attribute.hasSample()).thenReturn(true);
        when(attribute.getLatestSample()).thenReturn(sample);
        return attribute;
    }

    private static JEVisObject object(long id, String className, String decimals, JEVisObject parent) throws Exception {
        JEVisObject object = mock(JEVisObject.class);
        when(object.getID()).thenReturn(id);
        when(object.getJEVisClassName()).thenReturn(className);
        when(object.getParents()).thenReturn(parent != null ? Collections.singletonList(parent) : Collections.emptyList());
        boolean isData = className.contains("Data");
        when(object.getAttribute("Value")).thenReturn(isData ? mock(JEVisAttribute.class) : null);
        if (decimals != null) {
            JEVisAttribute attribute = attribute(decimals);
            when(object.getAttribute(ValueFormat.ATTRIBUTE_NAME)).thenReturn(attribute);
        }
        return object;
    }

    @Test
    public void parseIsLenient() {
        assertEquals(Integer.valueOf(3), ValueFormat.parse("3"));
        assertEquals(Integer.valueOf(3), ValueFormat.parse(" 3.0 "));
        assertEquals(Integer.valueOf(1), ValueFormat.parse("1,0"));
        assertEquals(Integer.valueOf(4), ValueFormat.parse("4 digits"));
        assertEquals(Integer.valueOf(0), ValueFormat.parse("0"));
        assertEquals(Integer.valueOf(ValueFormat.MAX_DECIMALS), ValueFormat.parse("99"));
        assertNull(ValueFormat.parse(null));
        assertNull(ValueFormat.parse(""));
        assertNull(ValueFormat.parse("abc"));
        assertNull(ValueFormat.parse("-1"));
    }

    @Test
    public void ownAttributeWins() throws Exception {
        JEVisObject data = object(1, "Data", "4", null);
        Map<Long, Integer> meters = new HashMap<>();
        meters.put(1L, 1);
        assertEquals(4, ValueFormat.decimals(data, meters));
    }

    @Test
    public void cleanDataUsesParentData() throws Exception {
        JEVisObject data = object(1, "Data", "3", null);
        JEVisObject clean = object(2, "Clean Data", null, data);
        assertEquals(3, ValueFormat.decimals(clean, new HashMap<>()));
    }

    @Test
    public void cleanDataOwnAttributeWinsOverParent() throws Exception {
        JEVisObject data = object(1, "Data", "3", null);
        JEVisObject clean = object(2, "Clean Data", "0", data);
        assertEquals(0, ValueFormat.decimals(clean, new HashMap<>()));
    }

    @Test
    public void meterOnlineIdIsFallback() throws Exception {
        JEVisObject data = object(1, "Data", null, null);
        JEVisObject clean = object(2, "Clean Data", null, data);
        Map<Long, Integer> meters = new HashMap<>();
        meters.put(1L, 5);
        assertEquals(5, ValueFormat.decimals(clean, meters));
    }

    @Test
    public void meterAboveInTreeIsLastFallback() throws Exception {
        JEVisObject meter = object(5, "Electricity Measurement Instrument", "1", null);
        JEVisObject data = object(1, "Data", null, meter);
        assertEquals(1, ValueFormat.decimals(data, new HashMap<>()));

        /* an object above that is no measurement instrument is ignored */
        JEVisObject folder = object(6, "Building", "4", null);
        JEVisObject other = object(3, "Data", null, folder);
        assertEquals(ValueFormat.DEFAULT_DECIMALS, ValueFormat.decimals(other, new HashMap<>()));
        assertNull(ValueFormat.configuredDecimals(other, new HashMap<>()));
    }

    @Test
    public void defaultWithoutConfiguration() throws Exception {
        JEVisObject data = object(1, "Data", null, null);
        assertEquals(ValueFormat.DEFAULT_DECIMALS, ValueFormat.decimals(data, new HashMap<>()));
        assertEquals(ValueFormat.DEFAULT_DECIMALS, ValueFormat.decimals(null));
    }

    @Test
    public void meterMapIsReadFromDataSourceAndCached() throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        JEVisClass meterClass = mock(JEVisClass.class);
        when(ds.getJEVisClass("Measurement Instrument")).thenReturn(meterClass);

        JEVisObject meter = object(5, "Heat Measurement Instrument", "3", null);
        JEVisAttribute onlineId = attribute("1:Value");
        when(meter.getAttribute("Online ID")).thenReturn(onlineId);
        JEVisObject meterWithoutDecimals = object(6, "Heat Measurement Instrument", null, null);
        JEVisAttribute onlineId2 = attribute("2");
        when(meterWithoutDecimals.getAttribute("Online ID")).thenReturn(onlineId2);
        when(ds.getObjects(meterClass, true)).thenReturn(Arrays.asList(meter, meterWithoutDecimals));

        Map<Long, Integer> decimals = ValueFormat.meterDecimalsByOnlineId(ds);
        assertEquals(1, decimals.size());
        assertEquals(Integer.valueOf(3), decimals.get(1L));

        JEVisObject data = object(1, "Data", null, null);
        when(data.getDataSource()).thenReturn(ds);
        ValueFormat.invalidateCache(ds);
        assertEquals(3, ValueFormat.decimals(data));
        assertEquals(3, ValueFormat.decimals(data));
        /* the meters are read once and then taken from the cache */
        verify(ds, times(2)).getObjects(meterClass, true);

        ValueFormat.invalidateCache(ds);
        assertEquals(3, ValueFormat.decimals(data));
        verify(ds, times(3)).getObjects(meterClass, true);
    }

    @Test
    public void numberFormatHasFixedDecimalsAndGrouping() {
        NumberFormat german = ValueFormat.numberFormat(3, Locale.GERMANY);
        assertEquals("1.234,500", german.format(1234.5));
        NumberFormat english = ValueFormat.numberFormat(0, Locale.US);
        assertEquals("1,235", english.format(1234.5));
        assertEquals("12,345.68", ValueFormat.numberFormat(2, Locale.US).format(12345.678));
        /* out of range values are clamped */
        assertEquals("1", ValueFormat.numberFormat(-1, Locale.US).format(1.2));
    }

    @Test
    public void numberFormatOfDataObject() throws Exception {
        JEVisObject data = object(1, "Data", "1", null);
        when(data.getDataSource()).thenReturn(mock(JEVisDataSource.class));
        assertEquals("0,3", ValueFormat.numberFormat(data, Locale.GERMANY).format(0.25));
    }
}
