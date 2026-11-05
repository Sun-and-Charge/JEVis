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

/**
 * The part of a dashboard widget a mouse pointer is over while the dashboard is edited: one of the eight
 * resize handles along the border, the inner area (used for moving) or nothing.
 * <p>
 * Pure geometry without JavaFX dependencies, so it can be unit tested.
 */
public enum WidgetHandle {
    NORTH_WEST(true, false, true, false),
    NORTH(false, false, true, false),
    NORTH_EAST(false, true, true, false),
    EAST(false, true, false, false),
    SOUTH_EAST(false, true, false, true),
    SOUTH(false, false, false, true),
    SOUTH_WEST(true, false, false, true),
    WEST(true, false, false, false),
    /**
     * the inner area of the widget, not part of a resize band
     */
    INSIDE(false, false, false, false),
    /**
     * the point is not on the widget at all
     */
    NONE(false, false, false, false);

    /**
     * Default width of the resize band along each border, in pixels.
     */
    public static final double DEFAULT_BAND = 8d;

    private final boolean left;
    private final boolean right;
    private final boolean top;
    private final boolean bottom;

    WidgetHandle(boolean left, boolean right, boolean top, boolean bottom) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
    }

    /**
     * Finds the handle for a point given in the coordinate system of the widget (0/0 is its top left corner).
     *
     * @param x      horizontal position of the point
     * @param y      vertical position of the point
     * @param width  width of the widget
     * @param height height of the widget
     * @param band   width of the resize band along each border
     * @return the handle; {@link #NONE} if the point lies outside the widget
     */
    public static WidgetHandle at(double x, double y, double width, double height, double band) {
        if (x < 0 || y < 0 || x > width || y > height) {
            return NONE;
        }

        int horizontal = side(x, width, band);
        int vertical = side(y, height, band);

        if (vertical < 0) {
            return horizontal < 0 ? NORTH_WEST : horizontal > 0 ? NORTH_EAST : NORTH;
        }
        if (vertical > 0) {
            return horizontal < 0 ? SOUTH_WEST : horizontal > 0 ? SOUTH_EAST : SOUTH;
        }
        return horizontal < 0 ? WEST : horizontal > 0 ? EAST : INSIDE;
    }

    /**
     * @return -1 if the position is in the band at the start, 1 if it is in the band at the end, 0 otherwise.
     * If the widget is so small that both bands overlap, the nearer border wins.
     */
    private static int side(double position, double length, double band) {
        double toStart = position;
        double toEnd = length - position;
        boolean nearStart = toStart < band;
        boolean nearEnd = toEnd < band;
        if (nearStart && nearEnd) {
            return toStart <= toEnd ? -1 : 1;
        }
        if (nearStart) {
            return -1;
        }
        if (nearEnd) {
            return 1;
        }
        return 0;
    }

    /**
     * @return true if this handle moves the left border
     */
    public boolean movesLeft() {
        return left;
    }

    /**
     * @return true if this handle moves the right border
     */
    public boolean movesRight() {
        return right;
    }

    /**
     * @return true if this handle moves the top border
     */
    public boolean movesTop() {
        return top;
    }

    /**
     * @return true if this handle moves the bottom border
     */
    public boolean movesBottom() {
        return bottom;
    }

    /**
     * @return true if this is one of the eight resize handles
     */
    public boolean isResize() {
        return left || right || top || bottom;
    }
}
