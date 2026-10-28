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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.commons.constants.EnterDataTypes;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.i18n.I18n;
import org.joda.time.Period;

public class DataTypeBox extends JFXComboBox<EnterDataTypes> {

    public DataTypeBox() {
        super();

        ObservableList<EnterDataTypes> enterDataTypes = FXCollections.observableArrayList(EnterDataTypes.values());
        setItems(enterDataTypes);

        Callback<ListView<EnterDataTypes>, ListCell<EnterDataTypes>> cellFactory = new Callback<ListView<EnterDataTypes>, ListCell<EnterDataTypes>>() {
            @Override
            public ListCell<EnterDataTypes> call(ListView<EnterDataTypes> param) {
                return new ListCell<EnterDataTypes>() {
                    @Override
                    public void updateItem(EnterDataTypes item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            try {
                                switch (item) {
                                    case YEAR:
                                        setText(I18n.getInstance().getString("dialog.enterdata.type.year"));
                                        break;
                                    case MONTH:
                                        setText(I18n.getInstance().getString("dialog.enterdata.type.month"));
                                        break;
                                    case DAY:
                                        setText(I18n.getInstance().getString("dialog.enterdata.type.day"));
                                        break;
                                    case SPECIFIC_DATETIME:
                                        setText(I18n.getInstance().getString("dialog.enterdata.type.specific"));
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

        getSelectionModel().select(EnterDataTypes.MONTH);
    }

    public Period selectFromPeriod(JEVisObject selectedObject) {
        Period p = Period.ZERO;
        if (selectedObject != null) {
            JEVisAttribute periodAttribute = null;
            try {
                periodAttribute = selectedObject.getAttribute(CleanDataObject.AttributeName.PERIOD.getAttributeName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (periodAttribute != null) {

                try {
                    p = new Period(periodAttribute.getLatestSample().getValueAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                if (p.equals(Period.days(1))) {
                    Platform.runLater(() -> getSelectionModel().select(EnterDataTypes.DAY));
                } else if (p.equals(Period.weeks(1))) {
                    Platform.runLater(() -> getSelectionModel().select(EnterDataTypes.DAY));
                } else if (p.equals(Period.months(1))) {
                    Platform.runLater(() -> getSelectionModel().select(EnterDataTypes.MONTH));
                } else if (p.equals(Period.years(1))) {
                    Platform.runLater(() -> getSelectionModel().select(EnterDataTypes.YEAR));
                } else {
                    Platform.runLater(() -> getSelectionModel().select(EnterDataTypes.SPECIFIC_DATETIME));
                }
            }
        }
        return p;
    }
}
