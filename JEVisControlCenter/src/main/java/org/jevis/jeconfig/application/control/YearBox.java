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
import org.joda.time.DateTime;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class YearBox extends JFXComboBox<Integer> {

    private MonthBox monthBox;
    private DayBox dayBox;

    public YearBox(DateTime nextTS) {
        super();

        List<Integer> list = new ArrayList<>();

        for (int i = 1990; i < 2050; i++) {
            list.add(i);
        }

        ObservableList<Integer> integers = FXCollections.observableArrayList(list);
        setItems(integers);

        Integer y = DateTime.now().getYear();

        if (nextTS != null) {
            getSelectionModel().select(Integer.valueOf(nextTS.getYear()));
        } else {
            getSelectionModel().select(y);
        }

        getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                Integer year = newValue;
                Integer month = monthBox.getSelectionModel().getSelectedIndex() + 1;
                YearMonth yearMonthObject = YearMonth.of(year, month);
                dayBox.setDays(yearMonthObject.lengthOfMonth());
            }
        });
    }

    public YearBox() {
        this(null);
    }

    public void setRelations(MonthBox monthBox, DayBox dayBox) {
        this.monthBox = monthBox;
        this.dayBox = dayBox;
    }

    public void setTS(DateTime nextTS) {
        if (nextTS != null) {
            Platform.runLater(() -> getSelectionModel().select(Integer.valueOf(nextTS.getYear())));
        }
    }
}
