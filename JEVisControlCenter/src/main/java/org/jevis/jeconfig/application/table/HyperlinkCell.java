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


import javafx.scene.control.*;
import javafx.util.Callback;


import java.awt.*;
import java.net.URI;


public class HyperlinkCell<T> implements Callback<TableColumn<T, String>, TableCell<T, String>> {

            @Override
            public TableCell<T, String> call(TableColumn<T, String> param) {
                return new TableCell<T, String>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        Hyperlink hyperlink = new Hyperlink();
                        hyperlink.setText(item);
                        System.out.println(item);
                        hyperlink.setOnAction(actionEvent -> {
                                    Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
                                    if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
                                        try {
                                            desktop.browse(new URI(item));
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }

                        );

                        if (item == null || empty) {
                            setText(null);
                            setGraphic(null);
                        } else {
                            setGraphic(hyperlink);
                        }
                    }
                };
            }
        };



