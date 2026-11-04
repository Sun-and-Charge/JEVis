/**
 * Copyright (C) 2014 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEApplication.
 *
 * JEApplication is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 *
 * JEApplication is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JEApplication. If not, see <http://www.gnu.org/licenses/>.
 *
 * JEApplication is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.dialog;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jevis.jeconfig.application.resource.ResourceLoader;

/**
 *
 * @author Florian Simon <florian.simon@envidatec.com>
 */
public class DialogHeader {

    public static Node getDialogHeader(String icon, String text) {
        BorderPane header = new BorderPane();
//        header.setStyle("-fx-background-color: linear-gradient(#e2e2e2,#eeeeee);");
        header.setPadding(new Insets(10, 10, 10, 10));
        header.getStyleClass().add("dialog-header");

        Label topTitle = new Label(text);

        topTitle.setWrapText(true);

        topTitle.setMinHeight(Region.USE_PREF_SIZE);
        topTitle.getStyleClass().add("dialog-header-title");

        ImageView imageView = ResourceLoader.getImage(icon, 64, 64);

        VBox vboxLeft = new VBox();
        VBox vboxRight = new VBox();
        vboxLeft.getChildren().add(topTitle);
        vboxLeft.setAlignment(Pos.CENTER_LEFT);
        vboxRight.setAlignment(Pos.CENTER_LEFT);
        vboxRight.getChildren().add(imageView);

        /* in the center, so that a long title wraps instead of pushing the icon out of the window (#1768) */
        header.setCenter(vboxLeft);

        header.setRight(vboxRight);

        return header;
    }

    public static Node getDialogHeader(ImageView icon, String text) {
        BorderPane header = new BorderPane();
//        header.setStyle("-fx-background-color: linear-gradient(#e2e2e2,#eeeeee);");
        header.setPadding(new Insets(10, 10, 10, 10));
        header.getStyleClass().add("dialog-header");
        Label topTitle = new Label(text);
        topTitle.setWrapText(true);
        topTitle.setMinHeight(Region.USE_PREF_SIZE);
        topTitle.getStyleClass().add("dialog-header-title");

        //ImageView imageView = ResourceLoader.getImage(icon, 64, 64);

        VBox vboxLeft = new VBox();
        VBox vboxRight = new VBox();
        vboxLeft.getChildren().add(topTitle);
        vboxLeft.setAlignment(Pos.CENTER_LEFT);
        vboxRight.setAlignment(Pos.CENTER_LEFT);
        vboxRight.getChildren().add(icon);

        /* in the center, so that a long title wraps instead of pushing the icon out of the window (#1768) */
        header.setCenter(vboxLeft);

        header.setRight(vboxRight);

        return header;
    }

    public static Node getDialogHeader(Region icon, String text) {
        BorderPane header = new BorderPane();
//        header.setStyle("-fx-background-color: linear-gradient(#e2e2e2,#eeeeee);");
        header.setPadding(new Insets(10, 10, 10, 10));
        header.getStyleClass().add("dialog-header");
        Label topTitle = new Label(text);
        topTitle.setWrapText(true);
        topTitle.setMinHeight(Region.USE_PREF_SIZE);
        topTitle.getStyleClass().add("dialog-header-title");

        //ImageView imageView = ResourceLoader.getImage(icon, 64, 64);

        VBox vboxLeft = new VBox();
        VBox vboxRight = new VBox();
        vboxLeft.getChildren().add(topTitle);
        vboxLeft.setAlignment(Pos.CENTER_LEFT);
        vboxRight.setAlignment(Pos.CENTER_LEFT);
        vboxRight.getChildren().add(icon);

        /* in the center, so that a long title wraps instead of pushing the icon out of the window (#1768) */
        header.setCenter(vboxLeft);

        header.setRight(vboxRight);

        return header;
    }


}
