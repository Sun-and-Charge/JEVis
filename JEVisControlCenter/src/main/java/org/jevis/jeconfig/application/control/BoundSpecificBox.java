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
import org.jevis.commons.constants.GapFillingBoundToSpecific;
import org.jevis.commons.i18n.I18n;

public class BoundSpecificBox extends JFXComboBox<GapFillingBoundToSpecific> {
    private final ObservableList<GapFillingBoundToSpecific> options = FXCollections.observableArrayList(GapFillingBoundToSpecific.NONE, GapFillingBoundToSpecific.WEEKDAY,
            GapFillingBoundToSpecific.WEEKOFYEAR, GapFillingBoundToSpecific.MONTHOFYEAR);

    public BoundSpecificBox() {
        super();

        Callback<ListView<GapFillingBoundToSpecific>, ListCell<GapFillingBoundToSpecific>> cellFactoryBoundToSpecificBox = new Callback<javafx.scene.control.ListView<GapFillingBoundToSpecific>, ListCell<GapFillingBoundToSpecific>>() {
            @Override
            public ListCell<GapFillingBoundToSpecific> call(javafx.scene.control.ListView<GapFillingBoundToSpecific> param) {
                return new ListCell<GapFillingBoundToSpecific>() {
                    @Override
                    protected void updateItem(GapFillingBoundToSpecific boundToSpecific, boolean empty) {
                        super.updateItem(boundToSpecific, empty);
                        if (empty || boundToSpecific == null) {
                            setText("");
                        } else {
                            String text = "";
                            switch (boundToSpecific) {
                                case NONE:
                                    text = I18n.getInstance().getString("plugin.object.attribute.gapfillingeditor.boundtospecific.none");
                                    break;
                                case WEEKDAY:
                                    text = I18n.getInstance().getString("plugin.object.attribute.gapfillingeditor.boundtospecific.weekday");
                                    break;
                                case WEEKOFYEAR:
                                    text = I18n.getInstance().getString("plugin.object.attribute.gapfillingeditor.boundtospecific.weekofyear");
                                    break;
                                case MONTHOFYEAR:
                                    text = I18n.getInstance().getString("plugin.object.attribute.gapfillingeditor.boundtospecific.monthofyear");
                                    break;
                            }
                            setText(text);
                        }
                    }
                };
            }
        };
        setCellFactory(cellFactoryBoundToSpecificBox);
        setButtonCell(cellFactoryBoundToSpecificBox.call(null));
        setItems(options);
    }

    public ObservableList<GapFillingBoundToSpecific> getOptions() {
        return options;
    }
}
