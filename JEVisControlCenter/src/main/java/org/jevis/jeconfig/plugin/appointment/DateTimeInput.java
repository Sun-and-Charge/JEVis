/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.plugin.appointment;

import com.jfoenix.controls.JFXDatePicker;
import com.jfoenix.controls.JFXTimePicker;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.util.converter.LocalTimeStringConverter;
import org.joda.time.LocalDateTime;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.FormatStyle;

/**
 * Date and time picker for a joda {@link LocalDateTime}.
 */
public class DateTimeInput extends HBox {

    private final JFXDatePicker datePicker = new JFXDatePicker();
    private final JFXTimePicker timePicker = new JFXTimePicker();

    public DateTimeInput(LocalDateTime value, LocalTime defaultTime) {
        super(6);
        setAlignment(Pos.CENTER_LEFT);
        timePicker.set24HourView(true);
        timePicker.setConverter(new LocalTimeStringConverter(FormatStyle.SHORT));
        datePicker.setPrefWidth(140);
        timePicker.setPrefWidth(100);
        if (value != null) {
            datePicker.setValue(LocalDate.of(value.getYear(), value.getMonthOfYear(), value.getDayOfMonth()));
            timePicker.setValue(LocalTime.of(value.getHourOfDay(), value.getMinuteOfHour()));
        } else {
            timePicker.setValue(defaultTime);
        }
        getChildren().addAll(datePicker, timePicker);
    }

    /**
     * @return the value, or null if no date is chosen
     */
    public LocalDateTime getValue() {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            return null;
        }
        LocalTime time = timePicker.getValue() != null ? timePicker.getValue() : LocalTime.MIDNIGHT;
        return new LocalDateTime(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), time.getHour(), time.getMinute());
    }
}
