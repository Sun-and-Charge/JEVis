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
import org.jevis.commons.dataprocessing.ManipulationMode;
import org.jevis.commons.i18n.I18n;

public class ReportManipulationBox extends JFXComboBox<ManipulationMode> {

    public ReportManipulationBox() {
        super();

        final String keyNone = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.none");
        final String keyAverage = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.average");
        final String keySortedMin = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.sortedmin");
        final String keySortedMax = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.sortedmax");
        final String keyMin = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.min");
        final String keyMax = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.max");
        final String keyMedian = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.median");
        final String keyRunningMean = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.runningmean");
        final String keyCentricRunningMean = I18n.getInstance().getString("plugin.object.report.dialog.aggregation.centricrunningmean");

        setItems(FXCollections.observableArrayList(ManipulationMode.values()));

        Callback<ListView<ManipulationMode>, ListCell<ManipulationMode>> cellFactory = new Callback<ListView<ManipulationMode>, ListCell<ManipulationMode>>() {
            @Override
            public ListCell<ManipulationMode> call(ListView<ManipulationMode> param) {
                return new ListCell<ManipulationMode>() {
                    @Override
                    protected void updateItem(ManipulationMode manipulationMode, boolean empty) {
                        super.updateItem(manipulationMode, empty);
                        if (empty || manipulationMode == null) {
                            setText("");
                        } else {
                            String text = "";
                            switch (manipulationMode) {
                                case AVERAGE:
                                    text += keyAverage;
                                    break;
                                case MIN:
                                    text += keyMin;
                                    break;
                                case MAX:
                                    text += keyMax;
                                    break;
                                case MEDIAN:
                                    text += keyMedian;
                                    break;
                                case RUNNING_MEAN:
                                    text += keyRunningMean;
                                    break;
                                case CENTRIC_RUNNING_MEAN:
                                    text += keyCentricRunningMean;
                                    break;
                                case SORTED_MIN:
                                    text += keySortedMin;
                                    break;
                                case SORTED_MAX:
                                    text += keySortedMax;
                                    break;
                                case CUMULATE:
                                    break;
                                case NONE:
                                    text = keyNone;
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
