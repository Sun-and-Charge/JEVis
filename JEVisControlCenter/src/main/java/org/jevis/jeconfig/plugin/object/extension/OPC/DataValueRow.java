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
package org.jevis.jeconfig.plugin.object.extension.OPC;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.joda.time.DateTime;

public class DataValueRow {

    public final ObjectProperty<DataValue> dateValueroperty;
    public final StringProperty tsProperty;
    public final StringProperty valueProperty;
    public final StringProperty qualityProperty;

    public DataValueRow(DataValue dataValue) {
        this.dateValueroperty = new SimpleObjectProperty<>(dataValue);

        //tsProperty =new SimpleStringProperty(dateValueroperty.getValue().getSourceTime().toString());
        tsProperty = new SimpleStringProperty((new DateTime(dateValueroperty.getValue().getSourceTime().getJavaDate())).toString());
        valueProperty = new SimpleStringProperty(dateValueroperty.getValue().getValue().getValue().toString());
        qualityProperty = new SimpleStringProperty(dateValueroperty.getValue().getStatusCode().toString());
    }
}
