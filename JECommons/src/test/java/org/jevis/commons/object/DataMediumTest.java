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
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DataMediumTest {

    private static JEVisObject object(long id, String className, String medium, JEVisObject parent) throws Exception {
        JEVisObject object = mock(JEVisObject.class);
        when(object.getID()).thenReturn(id);
        when(object.getJEVisClassName()).thenReturn(className);
        when(object.getParents()).thenReturn(parent != null ? Collections.singletonList(parent) : Collections.emptyList());
        boolean isData = className.contains("Data");
        when(object.getAttribute("Value")).thenReturn(isData ? mock(JEVisAttribute.class) : null);
        if (medium != null) {
            JEVisAttribute attribute = mock(JEVisAttribute.class);
            JEVisSample sample = mock(JEVisSample.class);
            when(sample.getValueAsString()).thenReturn(medium);
            when(attribute.hasSample()).thenReturn(true);
            when(attribute.getLatestSample()).thenReturn(sample);
            when(object.getAttribute(DataMedium.ATTRIBUTE_NAME)).thenReturn(attribute);
        }
        return object;
    }

    @Test
    public void meterClassMapping() {
        assertEquals(DataMedium.ELECTRICITY, DataMedium.fromMeterClass("Electricity Measurement Instrument"));
        assertEquals(DataMedium.COMPRESSED_AIR, DataMedium.fromMeterClass("Compressed-Air Measurement Instrument"));
        assertNull(DataMedium.fromMeterClass("Temperature Measurement Instrument"));
        assertNull(DataMedium.fromMeterClass(null));
    }

    @Test
    public void parseValues() {
        assertEquals(DataMedium.HEAT, DataMedium.parse(" heat "));
        assertNull(DataMedium.parse(""));
        assertNull(DataMedium.parse("unknown"));
        assertEquals(Long.valueOf(42), DataMedium.parseTargetId("42:Value"));
        assertNull(DataMedium.parseTargetId("abc"));
    }

    @Test
    public void ownAttributeWins() throws Exception {
        JEVisObject data = object(1, "Data", "GAS", null);
        Map<Long, String> meters = new HashMap<>();
        meters.put(1L, "Electricity Measurement Instrument");
        assertEquals(DataMedium.GAS, DataMedium.of(data, meters));
    }

    @Test
    public void cleanDataUsesParentData() throws Exception {
        JEVisObject data = object(1, "Data", "WATER", null);
        JEVisObject clean = object(2, "Clean Data", null, data);
        assertEquals(DataMedium.WATER, DataMedium.of(clean, new HashMap<>()));
    }

    @Test
    public void meterOnlineIdIsFallback() throws Exception {
        JEVisObject data = object(1, "Data", null, null);
        JEVisObject clean = object(2, "Clean Data", null, data);
        Map<Long, String> meters = new HashMap<>();
        meters.put(1L, "Heat Measurement Instrument");
        assertEquals(DataMedium.HEAT, DataMedium.of(clean, meters));
    }

    @Test
    public void meterAboveInTreeIsLastFallback() throws Exception {
        JEVisObject meter = object(5, "Electricity Measurement Instrument", null, null);
        JEVisObject data = object(1, "Data", null, meter);
        assertEquals(DataMedium.ELECTRICITY, DataMedium.of(data, new HashMap<>()));
        JEVisObject unknown = object(3, "Data", null, null);
        assertNull(DataMedium.of(unknown, new HashMap<>()));
    }
}
