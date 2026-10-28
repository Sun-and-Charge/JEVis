/**
 * Copyright (C) JEVis contributors
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
package org.jevis.commons.dataprocessing.processor.workflow;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ResourceManager}.
 */
public class ResourceManagerTest {

    private ResourceManager rm;

    @Before
    public void setUp() {
        rm = new ResourceManager();
    }

    @Test
    public void testDefault_isClean() {
        assertTrue("Default processing type should be CLEAN", rm.isClean());
        assertFalse(rm.isForecast());
        assertFalse(rm.isMath());
    }

    @Test
    public void testSetForecast() {
        rm.setProcessingType(ProcessingType.FORECAST);

        assertFalse(rm.isClean());
        assertTrue(rm.isForecast());
        assertFalse(rm.isMath());
    }

    @Test
    public void testSetMath() {
        rm.setProcessingType(ProcessingType.MATH);

        assertFalse(rm.isClean());
        assertFalse(rm.isForecast());
        assertTrue(rm.isMath());
    }

    @Test
    public void testSetCleanExplicitly() {
        rm.setProcessingType(ProcessingType.FORECAST);
        rm.setProcessingType(ProcessingType.CLEAN);

        assertTrue(rm.isClean());
    }

    @Test
    public void testIntervalsStartsAsEmptyList() {
        assertNotNull(rm.getIntervals());
        assertTrue(rm.getIntervals().isEmpty());
    }

    @Test
    public void testSetIntervals_null_thenGetReturnsNull() {
        rm.setIntervals(null);
        assertNull(rm.getIntervals());
    }

    @Test
    public void testSetIntervals_list() {
        rm.setIntervals(new ArrayList<>());
        assertNotNull(rm.getIntervals());
        assertTrue(rm.getIntervals().isEmpty());
    }

    @Test
    public void testSampleCacheReset() {
        rm.setSampleCache(new ArrayList<>());
        rm.setSampleCache(null);
        // getSampleCache() will attempt DB access if not null — just verify setter/getter work
        // without triggering the lazy-load (cleanDataObject is null → returns null)
        assertNull(rm.getSampleCache());
    }

    @Test
    public void testGetID_withoutCleanDataObject_returnsMinusOne() {
        assertEquals(-1L, (long) rm.getID());
    }
}
