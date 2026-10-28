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
package org.jevis.jeconfig.application.control;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LanguageBox extends JFXComboBox {

    public LanguageBox() {
        init();
    }

    private void init() {
        Callback<ListView<Locale>, ListCell<Locale>> cellFactory = new Callback<ListView<Locale>, ListCell<Locale>>() {
            @Override
            public ListCell<Locale> call(ListView<Locale> param) {
                return new ListCell<Locale>() {
                    {
                        super.setPrefWidth(260);
                    }

                    @Override
                    public void updateItem(Locale item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null) {
                            try {
                                HBox box = new HBox(5);
                                box.setAlignment(Pos.CENTER_LEFT);

                                Image img = new Image("/icons/" + item.getLanguage() + ".png");
                                ImageView iv = new ImageView(img);
                                iv.fitHeightProperty().setValue(20);
                                iv.fitWidthProperty().setValue(20);
                                iv.setSmooth(true);

                                Label name = new Label(item.getDisplayLanguage());
                                name.setTextFill(javafx.scene.paint.Color.BLACK);

                                box.getChildren().setAll(iv, name);
                                setGraphic(box);
                            } catch (Exception ex) {
                                setGraphic(null);
                            }
                        }
                    }
                };
            }
        };

        List<Locale> availableLanguages = Arrays.asList(Locale.getAvailableLocales());
        ObservableList<Locale> options = FXCollections.observableArrayList(availableLanguages);

        final JFXComboBox<Locale> comboBox = new JFXComboBox<Locale>(options);
        comboBox.setCellFactory(cellFactory);
        comboBox.setButtonCell(cellFactory.call(null));

        if (availableLanguages.contains(Locale.getDefault())) {
            comboBox.getSelectionModel().select(Locale.getDefault());
        }
    }
}
