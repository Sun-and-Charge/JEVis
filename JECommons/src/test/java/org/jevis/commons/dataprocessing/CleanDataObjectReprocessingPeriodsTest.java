/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.dataprocessing;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for Fix 1 (bounded reprocessing window) in {@link CleanDataObject#getReprocessingPeriods()}:
 * defaults to 1 (today's behavior: only the last-written period is redone) when unconfigured,
 * otherwise reads the "Reprocessing Periods" config attribute, clamped to a minimum of 1.
 */
public class CleanDataObjectReprocessingPeriodsTest {

    private CleanDataObject newCleanDataObject(JEVisObject cleanObject) throws Exception {
        JEVisDataSource ds = mock(JEVisDataSource.class);
        when(cleanObject.getDataSource()).thenReturn(ds);
        return new CleanDataObject(cleanObject);
    }

    @Test
    public void defaultsToOneWhenNotConfigured() throws Exception {
        JEVisObject cleanObject = mock(JEVisObject.class);
        // No "Reprocessing Periods" attribute configured - getAttribute(...) returns null.
        CleanDataObject cleanDataObject = newCleanDataObject(cleanObject);

        assertEquals(Integer.valueOf(1), cleanDataObject.getReprocessingPeriods());
    }

    @Test
    public void usesConfiguredValue() throws Exception {
        JEVisObject cleanObject = mock(JEVisObject.class);
        CleanDataObject cleanDataObject = newCleanDataObject(cleanObject);

        JEVisAttribute reprocessingPeriodsAttr = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsLong()).thenReturn(5L);
        when(reprocessingPeriodsAttr.getLatestSample()).thenReturn(sample);
        when(cleanObject.getAttribute("Reprocessing Periods")).thenReturn(reprocessingPeriodsAttr);

        assertEquals(Integer.valueOf(5), cleanDataObject.getReprocessingPeriods());
    }

    @Test
    public void clampsBelowOneToOne() throws Exception {
        JEVisObject cleanObject = mock(JEVisObject.class);
        CleanDataObject cleanDataObject = newCleanDataObject(cleanObject);

        JEVisAttribute reprocessingPeriodsAttr = mock(JEVisAttribute.class);
        JEVisSample sample = mock(JEVisSample.class);
        when(sample.getValueAsLong()).thenReturn(0L);
        when(reprocessingPeriodsAttr.getLatestSample()).thenReturn(sample);
        when(cleanObject.getAttribute("Reprocessing Periods")).thenReturn(reprocessingPeriodsAttr);

        assertEquals(Integer.valueOf(1), cleanDataObject.getReprocessingPeriods());
    }
}
