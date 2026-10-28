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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityData;

public class Statistics {

    private final ObservableList<NonconformityData> nonconformityDataObservableList;

    private final BooleanProperty updateTrigger;

    public Statistics(ObservableList<NonconformityData> nonconformityDataObservableList, BooleanProperty updateTrigger) {
        this.nonconformityDataObservableList = nonconformityDataObservableList;
        this.updateTrigger = updateTrigger;
    }

    public StringProperty getAll(String text) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ nonconformityDataObservableList.size(),nonconformityDataObservableList,updateTrigger));
        return stringProperty;

    }

    public StringProperty getActive(String text) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ nonconformityDataObservableList.stream().filter(actionData -> actionData.getDoneDate()== null).count(),nonconformityDataObservableList,updateTrigger));
        return stringProperty;

    }


    public StringProperty getFinished(String text) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ nonconformityDataObservableList.stream().filter(data1 -> data1.getDoneDate()!= null).count(),nonconformityDataObservableList,updateTrigger));
        return stringProperty;

    }
}
