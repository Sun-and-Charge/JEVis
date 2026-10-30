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

import de.gsi.chart.Chart;
import de.gsi.chart.ui.geometry.Side;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.ArrayList;
import java.util.List;

/**
 * Aligns the plot areas of several stacked chartfx charts (upstream issue #2347).
 * <p>
 * The left and right y-axes of the charts of an analysis usually have different widths
 * (different units, tick label lengths, a second y-axis only in some charts), so the plot
 * areas start and end at different x positions. This helper computes the natural (preferred)
 * width of the left and right axis panes of all registered charts and sets the maximum as
 * minimum width on every chart's axis panes. Charts without a second y-axis therefore reserve
 * the same space on the right side.
 * <p>
 * The natural width is taken from {@code prefWidth(-1)}, which does not depend on the minimum
 * width set by this class, so the calculation is stable and cannot run into a layout loop.
 * Recalculations are coalesced via {@link Platform#runLater(Runnable)}.
 */
public class XYChartAxisAligner {

    private static final Side[] SIDES = {Side.LEFT, Side.RIGHT};
    private static final double EPSILON = 0.5;

    private final List<Chart> charts = new ArrayList<>();
    private final List<Region> observedRegions = new ArrayList<>();
    private final List<Pane> observedPanes = new ArrayList<>();
    private final InvalidationListener widthListener = observable -> scheduleAlign();
    private final ListChangeListener<Node> childrenListener = change -> {
        reattachRegionListeners();
        scheduleAlign();
    };
    private boolean alignScheduled = false;

    /**
     * Replaces the aligned charts. Must be called on the FX thread.
     *
     * @param newCharts the charts that are shown stacked and should share the same plot area x positions
     */
    public void setCharts(List<Chart> newCharts) {
        for (Chart chart : charts) {
            for (Side side : SIDES) {
                chart.getAxesPane(side).setMinWidth(Region.USE_COMPUTED_SIZE);
            }
        }
        detach();
        charts.clear();
        if (newCharts != null) {
            charts.addAll(newCharts);
        }
        attach();
        scheduleAlign();
    }

    private void attach() {
        for (Chart chart : charts) {
            for (Side side : SIDES) {
                Pane pane = chart.getAxesPane(side);
                pane.getChildren().addListener(childrenListener);
                observedPanes.add(pane);
            }
        }
        reattachRegionListeners();
    }

    private void detach() {
        for (Pane pane : observedPanes) {
            pane.getChildren().removeListener(childrenListener);
        }
        observedPanes.clear();
        for (Region region : observedRegions) {
            region.widthProperty().removeListener(widthListener);
        }
        observedRegions.clear();
    }

    private void reattachRegionListeners() {
        for (Region region : observedRegions) {
            region.widthProperty().removeListener(widthListener);
        }
        observedRegions.clear();

        for (Pane pane : observedPanes) {
            /* the axes (chartfx axes are Regions) change their width when tick labels or units change */
            for (Node child : pane.getChildren()) {
                if (child instanceof Region) {
                    Region region = (Region) child;
                    region.widthProperty().addListener(widthListener);
                    observedRegions.add(region);
                }
            }
        }
    }

    private void scheduleAlign() {
        if (alignScheduled) {
            return;
        }
        alignScheduled = true;
        Platform.runLater(this::align);
    }

    private void align() {
        alignScheduled = false;
        if (charts.size() < 2) {
            for (Chart chart : charts) {
                for (Side side : SIDES) {
                    setMinWidth(chart.getAxesPane(side), Region.USE_COMPUTED_SIZE);
                }
            }
            return;
        }

        for (Side side : SIDES) {
            double max = 0d;
            for (Chart chart : charts) {
                max = Math.max(max, naturalWidth(chart.getAxesPane(side)));
            }
            for (Chart chart : charts) {
                setMinWidth(chart.getAxesPane(side), max > 0 ? Math.ceil(max) : Region.USE_COMPUTED_SIZE);
            }
        }
    }

    private static double naturalWidth(Pane pane) {
        double width = 0d;
        boolean hasVisibleChild = false;
        for (Node child : pane.getChildren()) {
            if (child.isManaged() && child.isVisible()) {
                hasVisibleChild = true;
                break;
            }
        }
        if (hasVisibleChild) {
            width = pane.prefWidth(-1);
        }
        return Double.isNaN(width) ? 0d : width;
    }

    private static void setMinWidth(Region region, double value) {
        if (Math.abs(region.getMinWidth() - value) > EPSILON) {
            region.setMinWidth(value);
        }
    }
}
