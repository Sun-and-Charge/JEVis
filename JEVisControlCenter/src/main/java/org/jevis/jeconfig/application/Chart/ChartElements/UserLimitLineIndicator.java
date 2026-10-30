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

import de.gsi.chart.axes.Axis;
import de.gsi.chart.plugins.ChartPlugin;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import org.jevis.jeconfig.application.Chart.data.UserLimitLine;

/**
 * Chart plugin that draws the label of a {@link UserLimitLine} and optionally fills the area
 * above or below the line semi-transparently (upstream issues #2233 / #1036).
 * <p>
 * The dashed line, the label and the fill are overlay nodes clipped to the plot area. A data set in
 * the chart renderer was not used: for some chart types its coordinates ended up far outside the
 * canvas and stroking the dashed line blocked the render thread. The overlay is mouse transparent
 * so that zooming and the data point table keep working.
 */
public class UserLimitLineIndicator extends ChartPlugin {

    private static final double FILL_OPACITY = 0.15;
    private static final double LABEL_PADDING = 4d;

    private final Axis axis;
    private final UserLimitLine limitLine;
    private final Rectangle area = new Rectangle();
    private final Line line = new Line();
    private final Label label = new Label();
    private final ChangeListener<Number> axisBoundsListener = (obs, oldValue, newValue) -> layoutChildren();

    public UserLimitLineIndicator(Axis axis, UserLimitLine limitLine) {
        super();
        this.axis = axis;
        this.limitLine = limitLine;

        Color color = limitLine.getFxColor();

        line.setMouseTransparent(true);
        line.setStroke(color);
        line.setStrokeWidth(1.5);
        line.getStrokeDashArray().setAll(10d, 6d);

        area.setMouseTransparent(true);
        area.setStroke(null);
        area.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), FILL_OPACITY));

        label.setMouseTransparent(true);
        label.setText(limitLine.getLabel());
        label.setTextFill(color);
        label.setStyle("-fx-font-size: 11px;");

        chartProperty().addListener((obs, oldChart, newChart) -> {
            if (oldChart != null) {
                axis.minProperty().removeListener(axisBoundsListener);
                axis.maxProperty().removeListener(axisBoundsListener);
            }
            if (newChart != null) {
                axis.minProperty().addListener(axisBoundsListener);
                axis.maxProperty().addListener(axisBoundsListener);
            }
        });
    }

    public UserLimitLine getLimitLine() {
        return limitLine;
    }

    @Override
    public void layoutChildren() {
        if (getChart() == null) {
            return;
        }

        final Bounds plotAreaBounds = getChart().getCanvas().getBoundsInLocal();
        final double minX = plotAreaBounds.getMinX();
        final double maxX = plotAreaBounds.getMaxX();
        final double minY = plotAreaBounds.getMinY();
        final double maxY = plotAreaBounds.getMaxY();

        final double yPos = minY + axis.getDisplayPosition(limitLine.getValue());
        if (Double.isNaN(yPos) || Double.isInfinite(yPos) || maxX <= minX || maxY <= minY) {
            removeNode(area);
            removeNode(line);
            removeNode(label);
            return;
        }

        if (yPos >= minY && yPos <= maxY) {
            line.setStartX(minX);
            line.setEndX(maxX);
            line.setStartY(yPos);
            line.setEndY(yPos);
            addNode(line);
        } else {
            removeNode(line);
        }

        /* fill area above or below the line, clipped to the plot area */
        String fill = limitLine.getFill();
        double clampedY = Math.max(minY, Math.min(maxY, yPos));
        double top = clampedY;
        double bottom = clampedY;
        if (UserLimitLine.FILL_ABOVE.equals(fill)) {
            top = minY;
        } else if (UserLimitLine.FILL_BELOW.equals(fill)) {
            bottom = maxY;
        }

        if (bottom - top > 0.5) {
            area.setX(minX);
            area.setY(top);
            area.setWidth(maxX - minX);
            area.setHeight(bottom - top);
            addNode(area);
        } else {
            removeNode(area);
        }

        /* label right aligned just above the line */
        String text = limitLine.getLabel();
        if (text != null && !text.isEmpty() && yPos >= minY && yPos <= maxY) {
            final double width = label.prefWidth(-1);
            final double height = label.prefHeight(width);
            double labelY = yPos - height - 1;
            if (labelY < minY) {
                labelY = yPos + 1;
            }
            label.resizeRelocate(Math.max(minX, maxX - width - LABEL_PADDING), labelY, width, height);
            addNode(label);
        } else {
            removeNode(label);
        }
    }

    private void addNode(Node node) {
        if (!getChartChildren().contains(node)) {
            if (node == area) {
                getChartChildren().add(0, node);
            } else {
                getChartChildren().add(node);
            }
        }
    }

    private void removeNode(Node node) {
        getChartChildren().remove(node);
    }
}
