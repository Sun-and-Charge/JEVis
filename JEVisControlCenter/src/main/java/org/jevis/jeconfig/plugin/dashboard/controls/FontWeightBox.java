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
package org.jevis.jeconfig.plugin.dashboard.controls;

import com.jfoenix.controls.JFXComboBox;
import javafx.collections.FXCollections;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.text.FontWeight;
import javafx.util.Callback;
import org.jevis.commons.i18n.I18n;

public class FontWeightBox extends JFXComboBox<FontWeight> {

    public FontWeightBox() {
        super(FXCollections.observableArrayList(FontWeight.values()));

        Callback<ListView<FontWeight>, ListCell<FontWeight>> cellFactory = new Callback<ListView<FontWeight>, ListCell<FontWeight>>() {
            @Override
            public ListCell<FontWeight> call(ListView<FontWeight> param) {
                return new ListCell<FontWeight>() {
                    @Override
                    protected void updateItem(FontWeight item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            switch (item) {
                                case THIN:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.thin"));
                                    break;
                                case EXTRA_LIGHT:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.extralight"));
                                    break;
                                case LIGHT:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.light"));
                                    break;
                                case NORMAL:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.normal"));
                                    break;
                                case MEDIUM:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.medium"));
                                    break;
                                case SEMI_BOLD:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.semibold"));
                                    break;
                                case BOLD:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.bold"));
                                    break;
                                case EXTRA_BOLD:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.extrabold"));
                                    break;
                                case BLACK:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontweightbox.black"));
                                    break;
                            }
                        } else {
                            setText(null);
                        }
                    }
                };
            }
        };

        setCellFactory(cellFactory);
        setButtonCell(cellFactory.call(null));
    }

}
