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
import javafx.scene.text.FontPosture;
import javafx.util.Callback;
import org.jevis.commons.i18n.I18n;

public class FontPostureBox extends JFXComboBox<FontPosture> {

    public FontPostureBox() {
        super(FXCollections.observableArrayList(FontPosture.values()));

        Callback<ListView<FontPosture>, ListCell<FontPosture>> cellFactory = new Callback<ListView<FontPosture>, ListCell<FontPosture>>() {
            @Override
            public ListCell<FontPosture> call(ListView<FontPosture> param) {
                return new ListCell<FontPosture>() {
                    @Override
                    protected void updateItem(FontPosture item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item != null && !empty) {
                            switch (item) {
                                case REGULAR:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontposturebox.regular"));
                                    break;
                                case ITALIC:
                                    setText(I18n.getInstance().getString("plugin.dashboard.controls.fontposturebox.italic"));
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
