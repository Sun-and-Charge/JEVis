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

import java.util.ArrayList;
import java.util.List;

public class DayBox extends JFXComboBox<Integer> {

    public DayBox() {
        super();

        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < 32; i++) {
            list.add(i);
        }

        ObservableList<Integer> enterDataTypes = FXCollections.observableArrayList(list);
        setItems(enterDataTypes);

        getSelectionModel().select(DateTime.now().getDayOfMonth() - 1);
    }

    public void setDays(int days) {
        List<Integer> list = new ArrayList<>();
        for (int i = 1; i < days + 1; i++) {
            list.add(i);
        }

        Platform.runLater(() -> getItems().setAll(list));
    }
}
