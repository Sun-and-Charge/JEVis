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
package org.jevis.jeconfig.plugin.dashboard.config2;

import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.plugin.dashboard.DashboardControl;

public class MoveDialog extends Dialog<ButtonType> {


    public MoveDialog(Window owner, DashboardControl control) {
        super();
        this.initOwner(owner);
        this.getDialogPane().getButtonTypes().setAll();

        GridPane gp = new GridPane();
        gp.setPadding(new Insets(8, 8, 8, 8));
        gp.setHgap(8);
        gp.setVgap(8);

        JFXButton leftButton = new JFXButton("<");
        JFXButton rightButton = new JFXButton(">");
        JFXButton downButton = new JFXButton("down");
        JFXButton upButton = new JFXButton("^");

        TextField pixels = new TextField(control.getActiveDashboard().getxGridInterval() + "");
        pixels.setMaxWidth(35);

        gp.add(upButton, 1, 0);
        gp.add(leftButton, 0, 1);
        gp.add(pixels, 1, 1);
        gp.add(rightButton, 2, 1);
        gp.add(downButton, 1, 2);
        getDialogPane().setContent(gp);


        upButton.setOnAction(event -> {
            control.moveSelected(Double.parseDouble(pixels.getText()), 0, 0, 0);
        });
        downButton.setOnAction(event -> {
            control.moveSelected(0, Double.parseDouble(pixels.getText()), 0, 0);
        });

        leftButton.setOnAction(event -> {
            control.moveSelected(0, 0, Double.parseDouble(pixels.getText()), 0);
        });
        rightButton.setOnAction(event -> {
            control.moveSelected(0, 0, 0, Double.parseDouble(pixels.getText()));
        });


        final ButtonType cancel = new ButtonType(I18n.getInstance().getString("jevistree.dialog.new.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);

        getDialogPane().getButtonTypes().addAll(cancel);
        this.setOnCloseRequest(event -> {
            this.hide();
        });
    }


}
