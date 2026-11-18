/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.Chart.ChartElements;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.List;

/**
 * Legend of the pie charts and the dashboard widgets (J25: replaces the internal JavaFX
 * {@code com.sun.javafx.charts.Legend}, which is not accessible on Java 9+). Same style classes as the JavaFX legend,
 * so existing CSS keeps working.
 */
public class ChartLegend extends FlowPane {

    private final ObservableList<LegendItem> items = FXCollections.observableArrayList();

    public ChartLegend() {
        getStyleClass().add("chart-legend");
        setHgap(10);
        setVgap(4);
        setPadding(new Insets(4));
        items.addListener((ListChangeListener<LegendItem>) change -> {
            List<Node> labels = new ArrayList<>();
            for (LegendItem item : items) labels.add(item.getLabel());
            getChildren().setAll(labels);
        });
    }

    public ObservableList<LegendItem> getItems() {
        return items;
    }

    public void setVertical(boolean vertical) {
        setOrientation(vertical ? Orientation.VERTICAL : Orientation.HORIZONTAL);
    }

    public boolean isVertical() {
        return getOrientation() == Orientation.VERTICAL;
    }

    /** one entry: the symbol as graphic of a label */
    public static class LegendItem {
        private final Label label = new Label();

        public LegendItem(String text) {
            this(text, null);
        }

        public LegendItem(String text, Node symbol) {
            label.setText(text);
            label.setGraphic(symbol);
            label.getStyleClass().add("chart-legend-item");
        }

        public Label getLabel() {
            return label;
        }

        public String getText() {
            return label.getText();
        }

        public void setText(String text) {
            label.setText(text);
        }

        public Node getSymbol() {
            return label.getGraphic();
        }

        public void setSymbol(Node symbol) {
            label.setGraphic(symbol);
        }
    }
}
