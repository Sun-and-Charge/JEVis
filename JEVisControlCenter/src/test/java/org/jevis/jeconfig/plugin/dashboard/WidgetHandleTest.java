/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEVisControlCenter.
 *
 * JEVisControlCenter is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 *
 * JEVisControlCenter is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.dashboard;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WidgetHandleTest {

    private static final double W = 200;
    private static final double H = 100;
    private static final double BAND = WidgetHandle.DEFAULT_BAND;

    private static WidgetHandle at(double x, double y) {
        return WidgetHandle.at(x, y, W, H, BAND);
    }

    @Test
    public void inside() {
        assertEquals(WidgetHandle.INSIDE, at(100, 50));
        assertEquals(WidgetHandle.INSIDE, at(8, 8));
        assertEquals(WidgetHandle.INSIDE, at(192, 92));
    }

    @Test
    public void edges() {
        assertEquals(WidgetHandle.NORTH, at(100, 0));
        assertEquals(WidgetHandle.NORTH, at(100, 7.9));
        assertEquals(WidgetHandle.SOUTH, at(100, 99));
        assertEquals(WidgetHandle.SOUTH, at(100, 100));
        assertEquals(WidgetHandle.WEST, at(3, 50));
        assertEquals(WidgetHandle.EAST, at(197, 50));
    }

    @Test
    public void corners() {
        assertEquals(WidgetHandle.NORTH_WEST, at(2, 2));
        assertEquals(WidgetHandle.NORTH_EAST, at(198, 2));
        assertEquals(WidgetHandle.SOUTH_WEST, at(2, 98));
        assertEquals(WidgetHandle.SOUTH_EAST, at(198, 98));
        /* the corner is where both bands overlap, outside of it only one band applies */
        assertEquals(WidgetHandle.NORTH, at(9, 2));
        assertEquals(WidgetHandle.WEST, at(2, 9));
    }

    @Test
    public void outside() {
        assertEquals(WidgetHandle.NONE, at(-1, 50));
        assertEquals(WidgetHandle.NONE, at(100, -1));
        assertEquals(WidgetHandle.NONE, at(201, 50));
        assertEquals(WidgetHandle.NONE, at(100, 101));
    }

    @Test
    public void smallWidgetUsesNearerBorder() {
        /* 10 px wide: both bands cover the whole width */
        assertEquals(WidgetHandle.WEST, WidgetHandle.at(4, 50, 10, 100, BAND));
        assertEquals(WidgetHandle.EAST, WidgetHandle.at(6, 50, 10, 100, BAND));
    }

    @Test
    public void movedBorders() {
        assertTrue(WidgetHandle.NORTH_WEST.movesLeft());
        assertTrue(WidgetHandle.NORTH_WEST.movesTop());
        assertFalse(WidgetHandle.NORTH_WEST.movesRight());
        assertFalse(WidgetHandle.NORTH_WEST.movesBottom());
        assertTrue(WidgetHandle.SOUTH_EAST.movesRight());
        assertTrue(WidgetHandle.SOUTH_EAST.movesBottom());
        assertTrue(WidgetHandle.EAST.isResize());
        assertFalse(WidgetHandle.INSIDE.isResize());
        assertFalse(WidgetHandle.NONE.isResize());
    }
}
