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
import org.jevis.jeconfig.application.Chart.Charts.regression.RegressionType;

public class RegressionBox extends JFXComboBox<RegressionType> {

    public RegressionBox() {
        super();

        ObservableList<RegressionType> regressionTypes = FXCollections.observableArrayList(RegressionType.values());
        regressionTypes.remove(0);
        setItems(regressionTypes);

        Callback<ListView<RegressionType>, ListCell<RegressionType>> cellFactory = new Callback<ListView<RegressionType>, ListCell<RegressionType>>() {
            @Override
            public ListCell<RegressionType> call(ListView<RegressionType> param) {
                return new ListCell<RegressionType>() {
                    @Override
                    public void updateItem(RegressionType item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            try {
                                switch (item) {
                                    case NONE:
                                        setText(I18n.getInstance().getString("dialog.regression.type.none"));
                                        break;
                                    case POLY:
                                        setText(I18n.getInstance().getString("dialog.regression.type.poly"));
                                        break;
                                    case EXP:
                                        setText(I18n.getInstance().getString("dialog.regression.type.exp"));
                                        break;
                                    case LOG:
                                        setText(I18n.getInstance().getString("dialog.regression.type.log"));
                                        break;
                                    case POW:
                                        setText(I18n.getInstance().getString("dialog.regression.type.pow"));
                                        break;
                                }
                            } catch (Exception ex) {
                                setGraphic(null);
                            }
                        }
                    }
                };
            }
        };

        setCellFactory(cellFactory);
        setButtonCell(cellFactory.call(null));

        getSelectionModel().select(RegressionType.POLY);
    }
}
