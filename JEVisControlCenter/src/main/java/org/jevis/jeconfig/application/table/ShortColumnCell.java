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

import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

public class ShortColumnCell<T> implements Callback<TableColumn<T, String>, TableCell<T, String>> {

            @Override
            public TableCell<T, String> call(TableColumn<T, String> param) {
                return new TableCell<T, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        setTextOverrun(OverrunStyle.ELLIPSIS);
                        setWrapText(false);
                        setGraphic(null);
                        if (item != null) {
                            if (!empty) {
                                if (item.contains("\n")) {
                                    setText(item.substring(0, item.indexOf("\n")));
                                } else {
                                    setText(item);
                                }
                            }
                        }
                        else {
                            setText(null);
                        }

                    }
                };
            }
        };



