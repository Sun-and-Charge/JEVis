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
package org.jevis.jeconfig.application.Chart.data;

import com.ibm.icu.text.NumberFormat;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jevis.api.JEVisUnit;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;

import java.util.ArrayList;

public class ValueWithDateTime {
    private final SimpleDoubleProperty value = new SimpleDoubleProperty(this, "value", 0d);
    private final SimpleListProperty<DateTime> dateTime = new SimpleListProperty<>(this, "dateTime", FXCollections.observableArrayList(new ArrayList<DateTime>()));
    private final SimpleObjectProperty<JEVisUnit> unit = new SimpleObjectProperty<>(this, "unit");
    private final NumberFormat numberFormat;

    public ValueWithDateTime(Double value, NumberFormat numberFormat) {
        this.numberFormat = numberFormat;
        this.value.set(value);
    }

    public ValueWithDateTime(DateTime dateTime, Double value) {
        this.dateTime.get().add(dateTime);
        this.value.set(value);
        this.numberFormat = NumberFormat.getInstance(I18n.getInstance().getLocale());
    }

    public Double getValue() {
        return value.get();
    }

    public void setValue(Double value) {
        this.value.set(value);
    }

    public SimpleDoubleProperty valueProperty() {
        return value;
    }

    public ObservableList<DateTime> getDateTime() {
        return dateTime.get();
    }

    public void setDateTime(ObservableList<DateTime> dateTime) {
        this.dateTime.set(dateTime);
    }

    public SimpleListProperty<DateTime> dateTimeProperty() {
        return dateTime;
    }

    public JEVisUnit getUnit() {
        return unit.get();
    }

    public void setUnit(JEVisUnit unit) {
        this.unit.set(unit);
    }

    public SimpleObjectProperty<JEVisUnit> unitProperty() {
        return unit;
    }

    public void minCheck(DateTime dateTime, Double currentValue) {
        Double min = Math.min(getValue(), currentValue);
        if (!getValue().equals(min)) {
            setValue(min);
            getDateTime().clear();
            getDateTime().add(dateTime);
        } else if (getValue().equals(currentValue) && getDateTime().size() < 40) {
            getDateTime().add(dateTime);
        }
    }

    public void maxCheck(DateTime dateTime, Double currentValue) {
        Double max = Math.max(getValue(), currentValue);
        if (!getValue().equals(max)) {
            setValue(max);
            getDateTime().clear();
            getDateTime().add(dateTime);
        } else if (getValue().equals(currentValue) && getDateTime().size() < 40) {
            getDateTime().add(dateTime);
        }
    }

    public NumberFormat getNumberFormat() {
        return this.numberFormat;
    }
}
