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
package org.jevis.jeconfig.plugin.nonconformities.ui;

import com.jfoenix.controls.JFXDatePicker;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.jevis.jeconfig.JEConfig;
import org.joda.time.DateTime;

import java.time.LocalDate;

public class TimeRangeDialog extends Alert {

    JFXDatePicker fromDatePicker = new JFXDatePicker();
    JFXDatePicker untilDatePicker = new JFXDatePicker();

    public TimeRangeDialog(DateTime from, DateTime until) {
        super(AlertType.CONFIRMATION);
        setHeaderText("Zeitbereich auswählen");
        setTitle("Zeitbereich auswählen");
        initOwner(JEConfig.getStage());
        setResizable(true);

        GridPane gridPane = new GridPane();

        gridPane.setPadding(new Insets(20));
        gridPane.setVgap(15);
        gridPane.setHgap(15);


        if (from != null) {
            LocalDate date = LocalDate.of(from.getYear(), from.getMonthOfYear(), from.getDayOfMonth());
            fromDatePicker.valueProperty().set(date);
        }
        if (until != null) {
            LocalDate date = LocalDate.of(until.getYear(), until.getMonthOfYear(), until.getDayOfMonth());
            untilDatePicker.valueProperty().set(date);
        }
        gridPane.add(new Label("Ab: "), 0, 0);
        gridPane.add(fromDatePicker, 1, 0);

        gridPane.add(new Label("Ais: "), 0, 1);
        gridPane.add(untilDatePicker, 1, 1);

        getDialogPane().setContent(gridPane);
    }

    private DateTime toDateTime(LocalDate localDate) {
        return new DateTime(localDate.getYear(), localDate.getMonthValue(), localDate.getDayOfMonth(), 0, 0);
    }

    public DateTime getFromDate() {
        return toDateTime(fromDatePicker.valueProperty().get());
    }

    public DateTime getUntilDate() {
        return toDateTime(untilDatePicker.valueProperty().get());
    }
}
