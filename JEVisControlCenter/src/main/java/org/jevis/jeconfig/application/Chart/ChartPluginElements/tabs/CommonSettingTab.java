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
package org.jevis.jeconfig.application.Chart.ChartPluginElements.tabs;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.layout.GridPane;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.application.Chart.data.DataModel;
import org.jevis.jeconfig.tool.NumberSpinner;

import java.math.BigDecimal;

public class CommonSettingTab extends Tab {

    public CommonSettingTab(DataModel dataModel) {
        super(I18n.getInstance().getString("graph.tabs.tab.common"));
        setClosable(false);

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10);
        gridPane.setVgap(5);

        Label labelChartsPerScreen = new Label(I18n.getInstance().getString("graph.tabs.tab.chartsperscreen"));
        Integer numberOfChartsPerScreen = dataModel.getChartsPerScreen();
        NumberSpinner chartsPerScreen = new NumberSpinner(new BigDecimal(numberOfChartsPerScreen), new BigDecimal(1));
        chartsPerScreen.numberProperty().addListener((observable, oldValue, newValue) -> dataModel.setChartsPerScreen(newValue.intValue()));

        Label labelHorizontalPies = new Label(I18n.getInstance().getString("graph.tabs.tab.horizontalpies"));
        Integer numberOfHorizontalPies = dataModel.getHorizontalPies();
        NumberSpinner horizontalPies = new NumberSpinner(new BigDecimal(numberOfHorizontalPies), new BigDecimal(1));
        horizontalPies.numberProperty().addListener((observable, oldValue, newValue) -> dataModel.setHorizontalPies(newValue.intValue()));

        Label labelHorizontalTables = new Label(I18n.getInstance().getString("graph.tabs.tab.horizontaltables"));
        Integer numberOfHorizontalTables = dataModel.getHorizontalTables();
        NumberSpinner horizontalTables = new NumberSpinner(new BigDecimal(numberOfHorizontalTables), new BigDecimal(1));
        horizontalTables.numberProperty().addListener((observable, oldValue, newValue) -> dataModel.setHorizontalTables(newValue.intValue()));

        int row = 0;
        gridPane.add(labelChartsPerScreen, 0, row);
        gridPane.add(chartsPerScreen, 1, row);
        row++;

        gridPane.add(labelHorizontalPies, 0, row);
        gridPane.add(horizontalPies, 1, row);
        row++;

        gridPane.add(labelHorizontalTables, 0, row);
        gridPane.add(horizontalTables, 1, row);
        row++;

        setContent(gridPane);
    }
}
