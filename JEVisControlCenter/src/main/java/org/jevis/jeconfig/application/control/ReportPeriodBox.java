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
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.report.PeriodMode;

public class ReportPeriodBox extends JFXComboBox<PeriodMode> {

    public ReportPeriodBox(ObservableList<PeriodMode> items) {
        super(items);
        final String keyCurrent = I18n.getInstance().getString("plugin.object.report.dialog.period.current");
        final String keyLast = I18n.getInstance().getString("plugin.object.report.dialog.period.last");
        final String keyAll = I18n.getInstance().getString("plugin.object.report.dialog.period.all");
        final String keyFixed = I18n.getInstance().getString("plugin.object.report.dialog.period.fixed");
        final String keyFixedReportStart = I18n.getInstance().getString("plugin.object.report.dialog.period.fixedreportstart");
        final String keyFixedRelativeStart = I18n.getInstance().getString("plugin.object.report.dialog.period.relativestart");

        setItems(FXCollections.observableArrayList(PeriodMode.values()));

        Callback<ListView<PeriodMode>, ListCell<PeriodMode>> cellFactory = new Callback<javafx.scene.control.ListView<PeriodMode>, ListCell<PeriodMode>>() {
            @Override
            public ListCell<PeriodMode> call(javafx.scene.control.ListView<PeriodMode> param) {
                return new ListCell<PeriodMode>() {
                    @Override
                    protected void updateItem(PeriodMode aggregationPeriod, boolean empty) {
                        super.updateItem(aggregationPeriod, empty);
                        if (empty || aggregationPeriod == null) {
                            setText("");
                        } else {
                            String text = "";
                            switch (aggregationPeriod) {
                                case CURRENT:
                                    text = keyCurrent;
                                    break;
                                case LAST:
                                    text = keyLast;
                                    break;
                                case ALL:
                                    text = keyAll;
                                    break;
                                case FIXED:
                                    text = keyFixed;
                                    break;
                                case FIXED_TO_REPORT_END:
                                    text = keyFixedReportStart;
                                    break;
                                case RELATIVE:
                                    text = keyFixedRelativeStart;
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
