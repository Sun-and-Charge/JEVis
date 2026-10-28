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
package org.jevis.jeconfig.application.Chart.ChartPluginElements.SelectionTable;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import org.jevis.jeconfig.application.Chart.ChartElements.TableEntry;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.ValueWithDateTimeBox;
import org.jevis.jeconfig.application.Chart.data.ValueWithDateTime;

public class ValueWithDateTimeFieldTableCell extends TableCell<TableEntry, ValueWithDateTime> {

    private ValueWithDateTimeBox valueWithDateTimeBox;


    public ValueWithDateTimeFieldTableCell() {
        super();
    }

    public static Callback<TableColumn<TableEntry, ValueWithDateTime>, TableCell<TableEntry, ValueWithDateTime>> forTableColumn() {
        return list -> new ValueWithDateTimeFieldTableCell();
    }

    @Override
    public void startEdit() {
        if (!isEditable()
                || !getTableView().isEditable()
                || !getTableColumn().isEditable()) {
            return;
        }
        super.startEdit();

        if (isEditing()) {
            if (valueWithDateTimeBox == null) {
                valueWithDateTimeBox = ValueWithDateTimeBox.createComboBox(this);
            }

            ValueWithDateTimeBox.startEdit(this, null, null, valueWithDateTimeBox);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        ValueWithDateTimeBox.cancelEdit(this, null);
    }

    @Override
    public void updateItem(ValueWithDateTime item, boolean empty) {
        super.updateItem(item, empty);
        ValueWithDateTimeBox.updateItem(this, null, null, valueWithDateTimeBox);
    }

}
