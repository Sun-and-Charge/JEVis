/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.map.waypoint;

import org.jxmapviewer.JXMapViewer;
import org.jxmapviewer.viewer.WaypointPainter;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;

/**
 * "Paints" the Swing waypoints. In fact, just takes care of correct positioning of the representing button.
 *
 * @author Daniel Stahr
 */
public class SwingWaypointOverlayPainter extends WaypointPainter<SwingWaypoint> {

    @Override
    protected void doPaint(Graphics2D g, JXMapViewer jxMapViewer, int width, int height) {
        for (SwingWaypoint swingWaypoint : getWaypoints()) {
            Point2D point = jxMapViewer.getTileFactory().geoToPixel(
                    swingWaypoint.getPosition(), jxMapViewer.getZoom());
            Rectangle rectangle = jxMapViewer.getViewportBounds();
            int buttonX = (int)(point.getX() - rectangle.getX());
            int buttonY = (int)(point.getY() - rectangle.getY());
            JButton button = swingWaypoint.getButton();
            button.setLocation(buttonX - button.getWidth() / 2, buttonY - button.getHeight() / 2);
        }
    }
}
