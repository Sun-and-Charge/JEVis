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
package org.jevis.jeconfig.application.table;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import org.jevis.jeconfig.plugin.nonconformities.data.NonconformityData;

import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public class StringListColumnCell<T> implements Callback<TableColumn<T, String>, TableCell<T, String>> {

    NumberFormat currencyFormat = NumberFormat.getCurrencyInstance();

    {
        currencyFormat.setCurrency(Currency.getInstance(Locale.GERMANY));
    }

    ;

    @Override
    public TableCell<T, String> call(TableColumn<T, String> param) {
        return new TableCell<T, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (item != null && !empty) {
                    setText(item.replace(";", ", "));
                } else {
                    setText(null);
                }
            }
        };
    }
}
