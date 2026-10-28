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
import javafx.scene.paint.Color;
import javafx.util.Callback;
import org.jevis.jeconfig.application.Chart.data.ChartData;
import org.jevis.jeconfig.application.control.ColorPickerAdv;

public class ColorTableCell extends TableCell<ChartData, Color> {

    private ColorPickerAdv colorPicker;


    public ColorTableCell() {
        super();
    }

    public static Callback<TableColumn<ChartData, Color>, TableCell<ChartData, Color>> forTableColumn() {
        return list -> new ColorTableCell();
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
            if (colorPicker == null) {
                colorPicker = ColorPickerAdv.createColorPicker(this);
            }

            ColorPickerAdv.startEdit(this, null, null, colorPicker);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        ColorPickerAdv.cancelEdit(this, null);
    }

    @Override
    public void updateItem(Color item, boolean empty) {
        super.updateItem(item, empty);
        ColorPickerAdv.updateItem(this, null, null, colorPicker);
    }

}
