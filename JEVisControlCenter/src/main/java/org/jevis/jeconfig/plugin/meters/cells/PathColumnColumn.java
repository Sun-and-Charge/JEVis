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
package org.jevis.jeconfig.plugin.meters.cells;

import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.cell.TextFieldTableCell;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeconfig.plugin.meters.data.MeterData;


public class PathColumnColumn extends TableColumn<MeterData, String> {
    public PathColumnColumn(ObjectRelations objectRelations, int width, String name) {
        super(name);
        this.setCellValueFactory(param -> {
            return new SimpleStringProperty(objectRelations.getObjectPath(param.getValue().getJeVisObject()));
        });
        this.setCellFactory(TextFieldTableCell.forTableColumn());
        this.setPrefWidth(width);
        this.setStyle("-fx-alignment: CENTER-LEFT;");

    }
}

