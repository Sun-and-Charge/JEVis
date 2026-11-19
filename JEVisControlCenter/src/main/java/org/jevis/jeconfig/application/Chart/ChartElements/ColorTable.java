/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.application.Chart.ChartElements;

import javafx.scene.paint.Color;

import java.util.List;

public class ColorTable {
    /*
     * Default colours for new rows (J25.5): the Grafana classic palette (blue, orange, green, red, purple, yellow
     * and their dark and light variants), readable on the white plot. Rows of existing analyses keep their colour.
     */
    public static final Color[] color_list = {
            Color.web("0x5794F2"),    // blue
            Color.web("0xFF9830"),    // orange
            Color.web("0x73BF69"),    // green
            Color.web("0xF2495C"),    // red
            Color.web("0xB877D9"),    // purple
            Color.web("0xE0B400"),    // dark yellow
            Color.web("0x1F60C4"),    // dark blue
            Color.web("0xFA6400"),    // dark orange
            Color.web("0x37872D"),    // dark green
            Color.web("0xC4162A"),    // dark red
            Color.web("0x8F3BB8"),    // dark purple
            Color.web("0x8AB8FF"),    // light blue
            Color.web("0xFFB357"),    // light orange
            Color.web("0x96D98D"),    // light green
            Color.web("0xFF7383"),    // light red
            Color.web("0xCA95E5"),    // light purple
            Color.web("0x6B768A"),    // grey
            Color.web("0x1A2A45"),    // blueberry
            Color.web("0x0891B2"),    // cyan
            Color.web("0xD97706"),    // amber
    };
    public static Color STANDARD_COLOR = Color.LIGHTBLUE;

    public static Color getNextColor(List<Color> usedColors) {
        for (Color color : color_list) {
            if (!usedColors.contains(color)) {
                usedColors.add(color);
                return color;
            }
        }
        return STANDARD_COLOR;
    }
}
