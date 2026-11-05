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

import java.util.function.DoubleUnaryOperator;

/**
 * Immutable position and size of a dashboard widget, with the calculations needed to move and resize it
 * with the mouse.
 * <p>
 * Pure geometry without JavaFX dependencies, so it can be unit tested.
 */
public final class WidgetFrame {

    /**
     * Smallest width and height a widget can be resized to with the mouse, in pixels.
     */
    public static final double MIN_SIZE = 20d;

    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public WidgetFrame(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    /**
     * @return x position of the right border
     */
    public double getMaxX() {
        return x + width;
    }

    /**
     * @return y position of the bottom border
     */
    public double getMaxY() {
        return y + height;
    }

    /**
     * @return true if width and height are the same as in the other frame
     */
    public boolean hasSameSize(WidgetFrame other) {
        return other != null && Double.compare(width, other.width) == 0 && Double.compare(height, other.height) == 0;
    }

    /**
     * @return this frame moved by the given distance, the size stays the same
     */
    public WidgetFrame translate(double dx, double dy) {
        return new WidgetFrame(x + dx, y + dy, width, height);
    }

    /**
     * Calculates the frame after dragging one of the resize handles by the given distance. The borders the handle
     * does not move stay where they are. If the frame would get smaller than the minimum size, it keeps the minimum
     * size and the border opposite to the dragged one still does not move.
     *
     * @param handle    the dragged handle; {@link WidgetHandle#INSIDE} and {@link WidgetHandle#NONE} change nothing
     * @param dx        horizontal distance the mouse moved since the drag started
     * @param dy        vertical distance the mouse moved since the drag started
     * @param minWidth  smallest allowed width
     * @param minHeight smallest allowed height
     */
    public WidgetFrame resize(WidgetHandle handle, double dx, double dy, double minWidth, double minHeight) {
        double[] horizontal = resizeAxis(x, width, dx, handle.movesLeft(), handle.movesRight(), minWidth);
        double[] vertical = resizeAxis(y, height, dy, handle.movesTop(), handle.movesBottom(), minHeight);
        return new WidgetFrame(horizontal[0], vertical[0], horizontal[1], vertical[1]);
    }

    /**
     * Resize along one axis.
     *
     * @return {new start, new length}
     */
    private static double[] resizeAxis(double start, double length, double delta, boolean movesStart, boolean movesEnd, double min) {
        if (movesStart) {
            double end = start + length;
            double newStart = Math.min(start + delta, end - min);
            return new double[]{newStart, end - newStart};
        }
        if (movesEnd) {
            return new double[]{start, Math.max(min, length + delta)};
        }
        return new double[]{start, length};
    }

    /**
     * Aligns a moved frame to a grid: the top left corner is snapped, the size stays the same.
     *
     * @param snapX returns the grid position for a x value
     * @param snapY returns the grid position for a y value
     */
    public WidgetFrame snapPosition(DoubleUnaryOperator snapX, DoubleUnaryOperator snapY) {
        return new WidgetFrame(snapX.applyAsDouble(x), snapY.applyAsDouble(y), width, height);
    }

    /**
     * Aligns a resized frame to a grid: every border moved by the handle is snapped, the other borders stay where
     * they are. If snapping a border would make the frame smaller than the minimum size, this border is not snapped.
     *
     * @param handle    the dragged handle
     * @param snapX     returns the grid position for a x value
     * @param snapY     returns the grid position for a y value
     * @param minWidth  smallest allowed width
     * @param minHeight smallest allowed height
     */
    public WidgetFrame snapBorders(WidgetHandle handle, DoubleUnaryOperator snapX, DoubleUnaryOperator snapY, double minWidth, double minHeight) {
        double[] horizontal = snapAxis(x, width, handle.movesLeft(), handle.movesRight(), snapX, minWidth);
        double[] vertical = snapAxis(y, height, handle.movesTop(), handle.movesBottom(), snapY, minHeight);
        return new WidgetFrame(horizontal[0], vertical[0], horizontal[1], vertical[1]);
    }

    /**
     * Snap along one axis.
     *
     * @return {new start, new length}
     */
    private static double[] snapAxis(double start, double length, boolean movesStart, boolean movesEnd, DoubleUnaryOperator snap, double min) {
        double end = start + length;
        if (movesStart) {
            double snappedStart = snap.applyAsDouble(start);
            if (end - snappedStart >= min) {
                return new double[]{snappedStart, end - snappedStart};
            }
        } else if (movesEnd) {
            double snappedEnd = snap.applyAsDouble(end);
            if (snappedEnd - start >= min) {
                return new double[]{start, snappedEnd - start};
            }
        }
        return new double[]{start, length};
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WidgetFrame)) return false;
        WidgetFrame other = (WidgetFrame) o;
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0
                && Double.compare(width, other.width) == 0 && Double.compare(height, other.height) == 0;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(x);
        result = 31 * result + Double.hashCode(y);
        result = 31 * result + Double.hashCode(width);
        result = 31 * result + Double.hashCode(height);
        return result;
    }

    @Override
    public String toString() {
        return "WidgetFrame[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
}
