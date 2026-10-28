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
package org.jevis.jeconfig.plugin.action.ui.control;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import org.jevis.jeconfig.plugin.action.data.ActionData;
import org.jevis.jeconfig.plugin.action.ui.NumerFormating;

import java.text.NumberFormat;

public class CurrencyColumnCell implements Callback<TableColumn<ActionData, Double>, TableCell<ActionData, Double>> {

    NumberFormat currencyFormat = NumerFormating.getInstance().getCurrencyFormat();

    @Override
    public TableCell<ActionData, Double> call(TableColumn<ActionData, Double> param) {
        return new TableCell<ActionData, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    if (item.equals(-0d)) {
                        setText(currencyFormat.format(0d));
                    } else {
                        setText(currencyFormat.format(item));
                    }

                } else {
                    setText(null);
                }
            }
        };
    }
}
