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

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

/**
 *
 * @author broder
 */
class RoundButton extends JButton {

    public RoundButton(Color color) {
        setModel(new DefaultButtonModel());
        init(null, null);
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        setBackground(color);
        setContentAreaFilled(true);
        setFocusPainted(true);
        //setVerticalAlignment(SwingConstants.TOP);
        setAlignmentY(Component.TOP_ALIGNMENT);
        initShape();
    }
    protected Shape shape, base;

    protected void initShape() {
        if (!getBounds().equals(base)) {
            Dimension s = getPreferredSize();
            base = getBounds();
            shape = new Ellipse2D.Float(0, 0, s.width - 1, s.height - 1);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(5, 5);
    }

    @Override
    protected void paintBorder(Graphics g) {
        initShape();
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        //g2.setStroke(new BasicStroke(1.0f));
        g2.fill(shape);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    @Override
    public boolean contains(int x, int y) {
        initShape();
        return shape.contains(x, y);
        //or return super.contains(x, y) && ((image.getRGB(x, y) >> 24) & 0xff) > 0;
    }
}
