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
import de.jollyday.CalendarHierarchy;
import de.jollyday.HolidayManager;
import de.jollyday.ManagerParameters;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.utils.AlphanumComparator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CalendarBox extends JFXComboBox<CalendarRow> {

    public CalendarBox() {
        List<CalendarRow> list = new ArrayList<>();
        Locale locale = I18n.getInstance().getLocale();
        for (String countryCode : HolidayManager.getSupportedCalendarCodes()) {
            HolidayManager instance = HolidayManager.getInstance(ManagerParameters.create(countryCode));
            String countryName = instance.getCalendarHierarchy().getDescription(locale);

            if (!instance.getCalendarHierarchy().getChildren().isEmpty()) {
                for (Map.Entry<String, CalendarHierarchy> entry : instance.getCalendarHierarchy().getChildren().entrySet()) {
                    String stateCode = entry.getKey();
                    CalendarHierarchy calendarHierarchy = entry.getValue();
                    String stateName = calendarHierarchy.getDescription();
                    CalendarRow calendarRow = new CalendarRow(countryCode, countryName, stateCode, stateName);
                    list.add(calendarRow);
                }
            } else {
                CalendarRow calendarRow = new CalendarRow(countryCode, countryName);
                list.add(calendarRow);
            }
        }

        AlphanumComparator ac = new AlphanumComparator();
        list.sort((o1, o2) -> ac.compare(o1.getCountryName() + " " + o1.getStateName(), o2.getCountryName() + " " + o2.getStateName()));

        Callback<ListView<CalendarRow>, ListCell<CalendarRow>> cellFactory = new Callback<ListView<CalendarRow>, ListCell<CalendarRow>>() {
            @Override
            public ListCell<CalendarRow> call(ListView<CalendarRow> param) {
                return new ListCell<CalendarRow>() {
                    @Override
                    public void updateItem(CalendarRow item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(null);
                        setGraphic(null);
                        if (item != null && !empty) {
                            setText(item.getCountryName() + " " + item.getStateName());
                        }
                    }
                };
            }
        };

        setCellFactory(cellFactory);
        setButtonCell(cellFactory.call(null));

        setItems(FXCollections.observableArrayList(list));
    }


}
