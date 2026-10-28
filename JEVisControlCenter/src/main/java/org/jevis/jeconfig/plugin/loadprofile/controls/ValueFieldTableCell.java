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
package org.jevis.jeconfig.plugin.loadprofile.controls;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import org.jevis.jeconfig.plugin.loadprofile.data.MixedLoadProfile;

public class ValueFieldTableCell extends TableCell<MixedLoadProfile, Object> {

    private ValueField valueField;


    public ValueFieldTableCell() {
        super();
    }

    public static Callback<TableColumn<MixedLoadProfile, Object>, TableCell<MixedLoadProfile, Object>> forTableColumn() {
        return list -> new ValueFieldTableCell();
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
            if (valueField == null) {
                valueField = ValueField.createComboBox(this);
            }

            ValueField.startEdit(this, null, null, valueField);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        ValueField.cancelEdit(this, null);
    }

    @Override
    public void updateItem(Object item, boolean empty) {
        super.updateItem(item, empty);
        ValueField.updateItem(this, null, null, valueField);
    }

}
