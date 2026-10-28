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
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.CheckBox;
import org.jevis.jeconfig.application.Chart.data.ChartData;

public class CheckBoxTableCell extends TableCell<ChartData, Boolean> {
    private CheckBox checkBox;


    public CheckBoxTableCell() {
        super();
    }

    public static Callback<TableColumn<ChartData, Boolean>, TableCell<ChartData, Boolean>> forTableColumn() {
        return list -> new CheckBoxTableCell();

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
            if (checkBox == null) {
                checkBox = CheckBox.createComboBox(this);
            }

            CheckBox.startEdit(this, null, null, checkBox);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        CheckBox.cancelEdit(this, null);
    }

    @Override
    public void updateItem(Boolean item, boolean empty) {
        super.updateItem(item, empty);
        CheckBox.updateItem(this, null, null, checkBox);
    }
}
