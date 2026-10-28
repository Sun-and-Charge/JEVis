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
package org.jevis.jeconfig.application.tools;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.ObservableList;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SingleSelectionModel;
import javafx.scene.paint.Color;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;

public class DisabledItemsComboBox<T> extends JFXComboBox<T> {

    private ArrayList<T> disabledItems = new ArrayList<T>();

    public DisabledItemsComboBox() {
        super();
        setup();
    }

    public DisabledItemsComboBox(ObservableList<T> list) {
        super(list);
        setup();
    }

    private void setup() {

        SingleSelectionModel<T> model = new SingleSelectionModel<T>() {

            @Override
            public void select(T item) {

                if (disabledItems.contains(item)) {
                    return;
                }

                super.select(item);
            }

            @Override
            public void select(int index) {
                T item = getItems().get(index);

                if (disabledItems.contains(item)) {
                    return;
                }

                super.select(index);
            }

            @Override
            protected int getItemCount() {
                return getItems().size();
            }

            @Override
            protected T getModelItem(int index) {
                return getItems().get(index);
            }

        };

        Callback<ListView<T>, ListCell<T>> callback = new Callback<ListView<T>, ListCell<T>>() {

            @Override
            public ListCell<T> call(ListView<T> param) {
                final ListCell<T> cell = new ListCell<T>() {
                    @Override
                    public void updateItem(T item, boolean empty) {

                        super.updateItem(item, empty);

                        if (item != null) {

                            setText(item.toString());

                            if (disabledItems.contains(item)) {
                                setTextFill(Color.LIGHTGRAY);
                                setDisable(true);
                            }

                        } else {

                            setText(null);

                        }
                    }
                };

                return cell;
            }

        };

        setSelectionModel(model);
        setCellFactory(callback);

    }

    public void setDisabledItems(T... items) {
        for (int i = 0; i < items.length; i++) {
            disabledItems.add(items[i]);
        }
    }

    public void setDisabledItems(ArrayList<T> items) {
        disabledItems = items;
    }

    public void setDisabledItems(List<T> items) {
        for (int i = 0; i < items.size(); i++) {
            disabledItems.add(items.get(i));
        }
    }
}