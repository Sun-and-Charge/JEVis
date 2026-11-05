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

import java.util.function.DoubleUnaryOperator;

import static org.junit.Assert.assertEquals;

public class WidgetFrameTest {

    private static final double MIN = WidgetFrame.MIN_SIZE;
    /* grid with 25 px spacing */
    private static final DoubleUnaryOperator GRID = value -> Math.round(value / 25d) * 25d;
    private static final WidgetFrame FRAME = new WidgetFrame(100, 50, 200, 100);

    private static void assertFrame(double x, double y, double width, double height, WidgetFrame actual) {
        assertEquals(new WidgetFrame(x, y, width, height), actual);
    }

    @Test
    public void translateKeepsSize() {
        assertFrame(130, 40, 200, 100, FRAME.translate(30, -10));
    }

    @Test
    public void resizeRightAndBottom() {
        assertFrame(100, 50, 230, 100, FRAME.resize(WidgetHandle.EAST, 30, 99, MIN, MIN));
        assertFrame(100, 50, 200, 80, FRAME.resize(WidgetHandle.SOUTH, 99, -20, MIN, MIN));
        assertFrame(100, 50, 190, 115, FRAME.resize(WidgetHandle.SOUTH_EAST, -10, 15, MIN, MIN));
    }

    @Test
    public void resizeLeftAndTopKeepsOppositeBorder() {
        WidgetFrame west = FRAME.resize(WidgetHandle.WEST, -20, 0, MIN, MIN);
        assertFrame(80, 50, 220, 100, west);
        assertEquals(FRAME.getMaxX(), west.getMaxX(), 0);

        WidgetFrame north = FRAME.resize(WidgetHandle.NORTH, 0, 30, MIN, MIN);
        assertFrame(100, 80, 200, 70, north);
        assertEquals(FRAME.getMaxY(), north.getMaxY(), 0);

        assertFrame(110, 45, 190, 105, FRAME.resize(WidgetHandle.NORTH_WEST, 10, -5, MIN, MIN));
    }

    @Test
    public void cornersCombineBothAxes() {
        assertFrame(100, 40, 220, 110, FRAME.resize(WidgetHandle.NORTH_EAST, 20, -10, MIN, MIN));
        assertFrame(90, 50, 210, 120, FRAME.resize(WidgetHandle.SOUTH_WEST, -10, 20, MIN, MIN));
    }

    @Test
    public void minimumSizeRightAndBottom() {
        assertFrame(100, 50, MIN, MIN, FRAME.resize(WidgetHandle.SOUTH_EAST, -500, -500, MIN, MIN));
    }

    @Test
    public void minimumSizeLeftAndTopDoesNotJump() {
        /* dragging far beyond the opposite border: the right/bottom border stays fixed */
        WidgetFrame frame = FRAME.resize(WidgetHandle.NORTH_WEST, 500, 500, MIN, MIN);
        assertFrame(300 - MIN, 150 - MIN, MIN, MIN, frame);
        assertEquals(FRAME.getMaxX(), frame.getMaxX(), 0);
        assertEquals(FRAME.getMaxY(), frame.getMaxY(), 0);
    }

    @Test
    public void insideAndNoneDoNotResize() {
        assertEquals(FRAME, FRAME.resize(WidgetHandle.INSIDE, 10, 10, MIN, MIN));
        assertEquals(FRAME, FRAME.resize(WidgetHandle.NONE, 10, 10, MIN, MIN));
    }

    @Test
    public void snapPositionKeepsSize() {
        assertFrame(125, 25, 200, 100, new WidgetFrame(118, 33, 200, 100).snapPosition(GRID, GRID));
    }

    @Test
    public void snapBordersRightAndBottom() {
        /* x/y unchanged, the right/bottom border lands on the grid */
        WidgetFrame frame = new WidgetFrame(110, 60, 193, 88).snapBorders(WidgetHandle.SOUTH_EAST, GRID, GRID, MIN, MIN);
        assertFrame(110, 60, 190, 90, frame);
        assertEquals(300, frame.getMaxX(), 0);
        assertEquals(150, frame.getMaxY(), 0);
    }

    @Test
    public void snapBordersLeftAndTop() {
        /* x/y on the grid, the right/bottom border stays */
        WidgetFrame frame = new WidgetFrame(112, 62, 188, 88).snapBorders(WidgetHandle.NORTH_WEST, GRID, GRID, MIN, MIN);
        assertFrame(100, 50, 200, 100, frame);
    }

    @Test
    public void snapOnlyMovedBorders() {
        WidgetFrame frame = new WidgetFrame(112, 62, 188, 88).snapBorders(WidgetHandle.EAST, GRID, GRID, MIN, MIN);
        assertFrame(112, 62, 188, 88, frame);
    }

    @Test
    public void snapDoesNotGoBelowMinimum() {
        /* snapping the right border (110) to 100 would give a width of 0: it is not snapped */
        WidgetFrame frame = new WidgetFrame(90, 50, 20, 20).snapBorders(WidgetHandle.EAST, GRID, GRID, MIN, MIN);
        assertFrame(90, 50, 20, 20, frame);
    }
}
