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
package org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTimeZone;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TimeZoneBox extends JFXComboBox<DateTimeZone> {

    public TimeZoneBox() {

        Callback<ListView<DateTimeZone>, ListCell<DateTimeZone>> cellFactory = new Callback<javafx.scene.control.ListView<DateTimeZone>, ListCell<DateTimeZone>>() {
            @Override
            public ListCell<DateTimeZone> call(javafx.scene.control.ListView<DateTimeZone> param) {
                return new ListCell<DateTimeZone>() {
                    @Override
                    protected void updateItem(DateTimeZone dateTimeZone, boolean empty) {
                        super.updateItem(dateTimeZone, empty);
                        if (empty || dateTimeZone == null) {
                            setText("");
                        } else {

                            String text = dateTimeZone.getID() + " | " +
                                    DateTimeZone.getNameProvider().getShortName(I18n.getInstance().getLocale(), dateTimeZone.getID(), dateTimeZone.getNameKey(0));
//                                    + " | " + DateTimeZone.getNameProvider().getName(I18n.getInstance().getLocale(), dateTimeZone.getID(), dateTimeZone.getNameKey(0));

                            setText(text);
                        }
                    }
                };
            }
        };
        setCellFactory(cellFactory);
        setButtonCell(cellFactory.call(null));

        List<String> allTimeZoneStrings = new ArrayList<>(DateTimeZone.getAvailableIDs());
        allTimeZoneStrings.add(0, "Europe/Berlin");
        allTimeZoneStrings.add(0, "UTC");
        List<DateTimeZone> allTimeZones = allTimeZoneStrings.stream().map(DateTimeZone::forID).collect(Collectors.toList());

        setItems(FXCollections.observableArrayList(allTimeZones));

        getSelectionModel().select(DateTimeZone.getDefault());

    }
}
