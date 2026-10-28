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
import org.jevis.commons.dataprocessing.AggregationPeriod;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.AggregationPeriodBox;
import org.jevis.jeconfig.application.Chart.data.ChartData;

public class AggregationPeriodTableCell extends TableCell<ChartData, AggregationPeriod> {

    private AggregationPeriodBox aggregationPeriodBox;


    public AggregationPeriodTableCell() {
        super();
    }

    public static Callback<TableColumn<ChartData, AggregationPeriod>, TableCell<ChartData, AggregationPeriod>> forTableColumn() {
        return list -> new AggregationPeriodTableCell();
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
            if (aggregationPeriodBox == null) {
                aggregationPeriodBox = AggregationPeriodBox.createComboBox(this);
            }

            AggregationPeriodBox.startEdit(this, null, null, aggregationPeriodBox);
        }
    }

    @Override
    public void cancelEdit() {
        super.cancelEdit();
        AggregationPeriodBox.cancelEdit(this, null);
    }

    @Override
    public void updateItem(AggregationPeriod item, boolean empty) {
        super.updateItem(item, empty);
        AggregationPeriodBox.updateItem(this, null, null, aggregationPeriodBox);
    }

}
