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
package org.jevis.jeconfig.plugin.dashboard.timeframe;

import javafx.scene.control.DatePicker;
import javafx.scene.Node;
import javafx.scene.control.skin.DatePickerSkin;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.layout.BorderPane;
import javafx.stage.Popup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.time.LocalDate;

public class TimeFrameEditor extends Popup {

    private static final Logger logger = LogManager.getLogger(TimeFrameEditor.class);
    private final SimpleObjectProperty<Interval> intervalProperty;
    private TimeFrame timeFrame;

    DatePicker datePicker;

    public TimeFrameEditor(TimeFrame timeFrame, Interval interval) {
        super();
        this.intervalProperty = new SimpleObjectProperty<>(this, "interval", interval);
        this.timeFrame = timeFrame;
        this.datePicker = new DatePicker(LocalDate.now());
        this.datePicker.setShowWeekNumbers(true);
        /* the calendar of the date picker shown directly (public API of the skin since JavaFX 9) */
        DatePickerSkin datePickerSkin = new DatePickerSkin(this.datePicker);
        Node popupContent = datePickerSkin.getPopupContent();


        BorderPane borderPane = new BorderPane();
        borderPane.setCenter(popupContent);

        getContent().add(borderPane);
        this.datePicker.valueProperty().addListener((observable, oldValue, newValue) -> {
            setIntervalResult();
            this.hide();
        });

    }

    public void setDate(DateTime date) {
        this.datePicker.setValue(toLocalDate(date));
    }

    public void setTimeFrame(TimeFrame timeFrame) {
        this.timeFrame = timeFrame;
    }

    private void setIntervalResult() {
        LocalDate localDate = this.datePicker.valueProperty().getValue();
        if (this.datePicker.valueProperty().getValue().isAfter(LocalDate.now())) {
            localDate = LocalDate.now();
            this.datePicker.setValue(localDate);
            return;
        }

        DateTime newDateTime = new DateTime(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), 0, 0);
        setInterval(this.timeFrame.getInterval(newDateTime, false));
    }

    public SimpleObjectProperty<Interval> intervalProperty() {
        return intervalProperty;
    }

    public Interval getInterval() {
        return intervalProperty.get();
    }

    public void setInterval(Interval interval) {
        intervalProperty.setValue(interval);
    }

    public LocalDate toLocalDate(DateTime dateTime) {
        return LocalDate.of(dateTime.getYear(), dateTime.getMonthOfYear(), dateTime.getDayOfMonth());
    }


}
