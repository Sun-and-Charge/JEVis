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
package org.jevis.jeconfig.tool;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.tools.JEVisHelp;

import java.util.HashMap;
import java.util.Map;

public class FontTester {
    private static final Logger logger = LogManager.getLogger(FontTester.class);
    private final ToggleButton helpButton = JEVisHelp.getInstance().buildHelpButtons(20, 20);
    private final ToggleButton infoButton = JEVisHelp.getInstance().buildInfoButtons(20, 20);
    int i = 0;

    public FontTester() {
        ToolBar toolBar = new ToolBar();

        Map<String, Font> stringFontMap = new HashMap<>();

        Font.getFamilies().forEach(s -> {
            String bName = s.split(" ")[0];

            if (!stringFontMap.containsKey(bName)) {
                stringFontMap.put(bName, Font.font(bName));
            }
        });

        Font.getFontNames().forEach(s -> {
            i++;
            if (i > 100 && i < 150) {

                ToggleButton newB = new ToggleButton("", JEConfig.getImage("list-add.png", 20, 20));
                Font font = Font.font(s);
                Tooltip tooltip = new Tooltip("Aktiviere Zyklische update Funktion");
                tooltip.setFont(font);
                newB.setTooltip(tooltip);
                newB.setOnAction(event -> {
                    logger.debug("Font Fanaly: " + s);
                });
                toolBar.getItems().add(newB);
            } else {

            }
        });
        toolBar.getItems().addAll(JEVisHelp.getInstance().buildSpacerNode(), helpButton, infoButton);
        JEVisHelp.getInstance().addHelpItems("tester", "", JEVisHelp.LAYOUT.VERTICAL_BOT_CENTER, toolBar.getItems());
        JEVisHelp.getInstance().setActivePlugin("tester");

        BorderPane borderPane = new BorderPane();
        borderPane.setTop(toolBar);
        GridPane gridPane = new GridPane();
        gridPane.add(new Label(), 0, 0);
        gridPane.setMinHeight(500);
        borderPane.setCenter(gridPane);

        Scene scene = new Scene(borderPane);
        Stage window = new Stage();
        window.setScene(scene);
        window.show();
    }
}
