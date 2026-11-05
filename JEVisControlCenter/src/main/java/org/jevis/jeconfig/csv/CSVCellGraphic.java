/**
 * Copyright (C) 2014 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEConfig.
 *
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 *
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.csv;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;

/**
 *
 * @author Florian Simon <florian.simon@envidatec.com>
 */
public class CSVCellGraphic {

    HBox root = new HBox(5);
    Label textLabel = new Label("");
    public static String ICON_INVALID = Icon.INVALID;
    public static String ICON_VALID = Icon.VALID;

    //TODO maybe load an icon only once
    Region iconValid = JEConfig.getSVGImage(ICON_VALID, 20, 20, Icon.CSS_VALID);
    Region iconInVlaid = JEConfig.getSVGImage(ICON_INVALID, 20, 20, Icon.CSS_INVALID);
    Tooltip tooltip = new Tooltip();

    public CSVCellGraphic(String text) {
        root.setPadding(new Insets(3));
        root.setAlignment(Pos.CENTER_RIGHT);
        setText(text);
        textLabel.setTooltip(tooltip);
        setToolTipText(text);
        root.getChildren().setAll(textLabel, iconInVlaid);
    }

    public void setToolTipText(final String text) {
        tooltip.setText(text);
    }

    public void setText(final String text) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                textLabel.setText(text);
            }
        });
//        textLabel.setText(text);
    }

    public void setValid(final boolean isvalid) {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (isvalid) {
                    root.getChildren().set(1, iconValid);
                } else {
                    root.getChildren().set(1, iconInVlaid);
                }
            }
        });
//        if (isvalid) {
//            root.getChildren().set(1, iconValid);
//        } else {
//            root.getChildren().set(1, iconInVlaid);
//        }
    }

    public void setIgnore() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                root.getChildren().set(1, new Region());
            }
        });
    }

    public Node getGraphic() {
        return root;
    }
}
