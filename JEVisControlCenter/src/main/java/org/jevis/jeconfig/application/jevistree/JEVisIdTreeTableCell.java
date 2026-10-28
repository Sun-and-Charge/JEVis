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
package org.jevis.jeconfig.application.jevistree;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.layout.StackPane;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JEVisIdTreeTableCell extends TreeTableCell<JEVisTreeRow, Long> {
    private static final Logger logger = LogManager.getLogger(ColumnFactory.class);
    private final StackPane stackPane = new StackPane();
    private final Label label = new Label();

    public JEVisIdTreeTableCell() {
        stackPane.setStyle("-fx-background-color: transparent;");
        label.setStyle("-fx-background-color: transparent;");
        StackPane.setAlignment(stackPane, Pos.CENTER_RIGHT);

        stackPane.getChildren().setAll(label);
    }

    public static Callback<TreeTableColumn<JEVisTreeRow, Long>, TreeTableCell<JEVisTreeRow, Long>> callback() {
        return (param) -> new JEVisIdTreeTableCell();
    }

    @Override
    public void commitEdit(Long newValue) {
        super.commitEdit(newValue);
    }

    @Override
    protected void updateItem(Long item, boolean empty) {
        super.updateItem(item, empty); //To change body of generated methods, choose Tools | Templates.
        setText(null);
        setGraphic(null);
        if (!empty) {
            JEVisTreeRow jeVisTreeRow = getTreeTableRow().getItem();
            if (jeVisTreeRow != null) {
                if (jeVisTreeRow.getType() == JEVisTreeRow.TYPE.OBJECT) {
                    label.setText(item + "");
                } else {
                    label.setText("");
                }
            }

            setGraphic(stackPane);
        }
    }
}
