/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.unit.ChartUnits;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "Value is a Quantity" of the clean data decides, also when the calculation input is the raw data row */
public class QuantityUnitsCleanDataTest {

    private static JEVisObject clean(boolean quantity) throws Exception {
        return withSwitch("Clean Data", quantity);
    }

    private static JEVisObject withSwitch(String className, boolean quantity) throws Exception {
        JEVisObject clean = mock(JEVisObject.class);
        when(clean.getJEVisClassName()).thenReturn(className);
        JEVisAttribute switchAttribute = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsBoolean()).thenReturn(quantity);
        when(switchAttribute.hasSample()).thenReturn(true);
        when(switchAttribute.getLatestSample()).thenReturn(sample);
        when(clean.getAttribute("Value is a Quantity")).thenReturn(switchAttribute);
        return clean;
    }

    private static JEVisAttribute valueOf(JEVisObject object) {
        JEVisAttribute value = mock(JEVisAttribute.class);
        when(value.getObject()).thenReturn(object);
        return value;
    }

    @Test
    public void rawDataRowUsesTheSwitchOfItsCleanData() throws Exception {
        JEVisObject raw = mock(JEVisObject.class);
        when(raw.getJEVisClassName()).thenReturn("Data");
        JEVisObject clean = clean(true);
        when(raw.getChildren()).thenReturn(Collections.singletonList(clean));
        assertTrue(new QuantityUnits().isQuantityIfCleanData(valueOf(raw), false));

        JEVisObject rawNoQuantity = mock(JEVisObject.class);
        when(rawNoQuantity.getJEVisClassName()).thenReturn("Data");
        JEVisObject cleanNoQuantity = clean(false);
        when(rawNoQuantity.getChildren()).thenReturn(Collections.singletonList(cleanNoQuantity));
        assertFalse(new QuantityUnits().isQuantityIfCleanData(valueOf(rawNoQuantity), true));
    }

    @Test
    public void cleanDataRowAndOtherRows() throws Exception {
        assertTrue(new QuantityUnits().isQuantityIfCleanData(valueOf(clean(true)), false));
        JEVisObject other = mock(JEVisObject.class);
        when(other.getJEVisClassName()).thenReturn("String Data");
        /* other classes keep what the unit says */
        assertTrue(new QuantityUnits().isQuantityIfCleanData(valueOf(other), true));
        assertFalse(new QuantityUnits().isQuantityIfCleanData(valueOf(other), false));
    }

    @Test
    public void baseDataRowHasItsOwnSwitch() throws Exception {
        /* "Stammdaten" as calculation input (bug report 2026-11-27): the year total took the January value */
        assertTrue(new QuantityUnits().isQuantityIfCleanData(valueOf(withSwitch("Base Data", true)), false));
        assertFalse(new QuantityUnits().isQuantityIfCleanData(valueOf(withSwitch("Base Data", false)), true));
    }
}
