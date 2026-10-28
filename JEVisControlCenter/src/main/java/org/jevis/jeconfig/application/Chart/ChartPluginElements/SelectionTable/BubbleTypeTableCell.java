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
import org.jevis.commons.chart.BubbleType;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.BubbleTypeComboBox;
import org.jevis.jeconfig.application.Chart.data.ChartData;

public class BubbleTypeTableCell extends TableCell<ChartData, BubbleType> {

    private BubbleTypeComboBox bubbleTypeComboBox;


    public BubbleTypeTableCell() {
        super();
    }

    public static Callback<TableColumn<ChartData, BubbleType>, TableCell<ChartData, BubbleType>> forTableColumn() {
        return list -> new BubbleTypeTableCell();
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
            if (bubbleTypeComboBox == null) {
                bubbleTypeComboBox = BubbleTypeComboBox.createComboBox(this);
            }

            BubbleTypeComboBox.startEdit(this, null, null, bubbleTypeComboBox);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        BubbleTypeComboBox.cancelEdit(this, null);
    }

    @Override
    public void updateItem(BubbleType item, boolean empty) {
        super.updateItem(item, empty);
        BubbleTypeComboBox.updateItem(this, null, null, bubbleTypeComboBox);
    }

}
