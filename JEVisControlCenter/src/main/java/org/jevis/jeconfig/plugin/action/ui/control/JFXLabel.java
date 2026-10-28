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
package org.jevis.jeconfig.plugin.action.ui.control;


import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;


public class JFXLabel extends GridPane {

    Label label = new Label();

    public JFXLabel() {
        setMinHeight(35);
        //setBackground(new Background(new BackgroundFill(Color.CYAN, null, null)));
        Region spacer = new Region();
        spacer.setMaxHeight(2);
        //spacer.setBackground(new Background(new BackgroundFill(Color.BLUE, null, null)));

        Line line = new Line();
        line.setEndX(100);
        setVgrow(spacer, Priority.ALWAYS);
        setVgrow(line, Priority.NEVER);
        setVgrow(label, Priority.ALWAYS);
        setValignment(label, VPos.CENTER);
        setHgrow(line, Priority.ALWAYS);
        //Separator separator = new Separator(Orientation.HORIZONTAL);
        //add(spacer, 0, 0);
        add(label, 0, 1);
        add(line, 0, 2);

        setPadding(new Insets(0));

        spacer.widthProperty().addListener((observable, oldValue, newValue) -> {
            Platform.runLater(() -> {
                line.setEndX(newValue.doubleValue() - 2);
            });
        });

    }

    public void setText(String text) {
        label.setText(text);
    }

}
