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
package org.jevis.jeconfig.application.control;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.jevis.commons.dataprocessing.FixedPeriod;
import org.jevis.commons.i18n.I18n;

public class ReportFixedPeriodBox extends JFXComboBox<FixedPeriod> {

    public ReportFixedPeriodBox() {
        super();
        final String keyNone = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.none");
        final String keyQuarterHour = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.quarterhour");
        final String keyHour = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.hour");
        final String keyDay = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.day");
        final String keyWeek = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.week");
        final String keyMonth = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.month");
        final String keyQuarter = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.quarter");
        final String keyYear = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.year");
        final String keyThreeYears = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.threeyears");
        final String keyFiveYears = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.fiveyears");
        final String keyTenYears = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.tenyears");

        setItems(FXCollections.observableArrayList(FixedPeriod.values()));

        Callback<ListView<FixedPeriod>, ListCell<FixedPeriod>> cellFactory = new Callback<ListView<FixedPeriod>, ListCell<FixedPeriod>>() {
            @Override
            public ListCell<FixedPeriod> call(ListView<FixedPeriod> param) {
                return new ListCell<FixedPeriod>() {
                    @Override
                    protected void updateItem(FixedPeriod fixedPeriod, boolean empty) {
                        super.updateItem(fixedPeriod, empty);
                        if (empty || fixedPeriod == null) {
                            setText("");
                        } else {
                            String text = "";
                            switch (fixedPeriod) {
                                case NONE:
                                    text = keyNone;
                                    break;
                                case QUARTER_HOUR:
                                    text = keyQuarterHour;
                                    break;
                                case HOUR:
                                    text = keyHour;
                                    break;
                                case DAY:
                                    text = keyDay;
                                    break;
                                case WEEK:
                                    text = keyWeek;
                                    break;
                                case MONTH:
                                    text = keyMonth;
                                    break;
                                case QUARTER:
                                    text = keyQuarter;
                                    break;
                                case YEAR:
                                    text = keyYear;
                                    break;
                                case THREEYEARS:
                                    text = keyThreeYears;
                                    break;
                                case FIVEYEARS:
                                    text = keyFiveYears;
                                    break;
                                case TENYEARS:
                                    text = keyTenYears;
                                    break;
                            }

                            setText(text);
                        }
                    }
                };
            }
        };
        setCellFactory(cellFactory);
        setButtonCell(cellFactory.call(null));

        getSelectionModel().selectFirst();
    }
}
