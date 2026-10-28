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
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.commons.i18n.I18n;

public class ReportAggregationBox extends JFXComboBox<AggregationPeriod> {

    public ReportAggregationBox() {
        super();
        final String keyNone = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.none");
        final String keyQuarterHourly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.quarterhourly");
        final String keyHourly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.hourly");
        final String keyDaily = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.daily");
        final String keyWeekly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.weekly");
        final String keyMonthly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.monthly");
        final String keyQuarterly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.quarterly");
        final String keyYearly = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.yearly");

        setItems(FXCollections.observableArrayList(AggregationPeriod.values()));

        Callback<ListView<AggregationPeriod>, ListCell<AggregationPeriod>> cellFactory = new Callback<javafx.scene.control.ListView<AggregationPeriod>, ListCell<AggregationPeriod>>() {
            @Override
            public ListCell<AggregationPeriod> call(javafx.scene.control.ListView<AggregationPeriod> param) {
                return new ListCell<AggregationPeriod>() {
                    @Override
                    protected void updateItem(AggregationPeriod aggregationPeriod, boolean empty) {
                        super.updateItem(aggregationPeriod, empty);
                        if (empty || aggregationPeriod == null) {
                            setText("");
                        } else {
                            String text = "";
                            switch (aggregationPeriod) {

                                case NONE:
                                    text = keyNone;
                                    break;
                                case QUARTER_HOURLY:
                                    text = keyQuarterHourly;
                                    break;
                                case HOURLY:
                                    text = keyHourly;
                                    break;
                                case DAILY:
                                    text = keyDaily;
                                    break;
                                case WEEKLY:
                                    text = keyWeekly;
                                    break;
                                case MONTHLY:
                                    text = keyMonthly;
                                    break;
                                case QUARTERLY:
                                    text = keyQuarterly;
                                    break;
                                case YEARLY:
                                    text = keyYearly;
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
