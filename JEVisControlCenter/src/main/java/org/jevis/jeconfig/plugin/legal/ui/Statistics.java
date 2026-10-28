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
package org.jevis.jeconfig.plugin.legal.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import org.jevis.jeconfig.plugin.legal.data.ObligationData;

public class Statistics {

    private final ObservableList<ObligationData> obligationData;
    private final BooleanProperty update;

    public Statistics(ObservableList<ObligationData> obligationData, BooleanProperty update) {
        this.obligationData = obligationData;
        this.update = update;
    }

    public StringProperty getAll(String text) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ obligationData.size(), obligationData,update));
        return stringProperty;

    }

    public StringProperty getRelevant(String text, boolean compare) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ obligationData.stream().filter(obligationData1 -> obligationData1.getRelevant() == compare).count() ,obligationData,update));
        return stringProperty;

    }


    public StringProperty getCategory(String text,String compare) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ obligationData.stream().filter(obligationData1 -> obligationData1.getCategory().equals(compare)).count(),obligationData,update));
        return stringProperty;

    }

    public StringProperty getScope(String text,String compare) {


        StringProperty stringProperty = new SimpleStringProperty();
        stringProperty.bind(Bindings.createStringBinding(() -> text+ obligationData.stream().filter(obligationData1 -> obligationData1.getScope().equals(compare)).count(),obligationData,update));
        return stringProperty;

    }
}
