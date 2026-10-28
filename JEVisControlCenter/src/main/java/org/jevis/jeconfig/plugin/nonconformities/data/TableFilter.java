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
package org.jevis.jeconfig.plugin.nonconformities.data;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.joda.time.DateTime;

public class TableFilter {


    private ObservableList<String> status = FXCollections.observableArrayList();
    private ObservableList<String> medium = FXCollections.observableArrayList();
    private ObservableList<String> field = FXCollections.observableArrayList();
    private DATE_COMPARE plannedDateComp = DATE_COMPARE.EQUALS;
    private String plannedDateFilter = "";

    public boolean show(NonconformityData data) {
        boolean isOneTrue = false;

        if (!plannedDateFilter.isEmpty()) {
            DateTime now = new DateTime();
            if (plannedDateComp == DATE_COMPARE.EQUALS) {
                boolean isBigger = false;


                if ((data.deadLineProperty().get().getYear() + "").contains(plannedDateFilter)) {
                    isBigger = true;
                }

                if ((data.deadLineProperty().get().getMonthOfYear() + "").contains(plannedDateFilter)) {
                    isBigger = true;
                }

                if ((data.deadLineProperty().get().getDayOfMonth() + "").contains(plannedDateFilter)) {
                    isBigger = true;
                }

                if (isBigger) {
                    isOneTrue = true;
                }
            }
        }

        return isOneTrue;
    }

    public void setPlannedDateComp(DATE_COMPARE plannedDateComp) {
        this.plannedDateComp = plannedDateComp;
    }

    public void setPlannedDateFilter(String plannedDateFilter) {
        this.plannedDateFilter = plannedDateFilter;
    }

    public static enum DATE_COMPARE {
        BIGGER_THAN, SMALLER_THAN, EQUALS
    }


}
