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

import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

public class SummeryData {
    private final ObservableMap<TableColumn, StringProperty> summeryList;
    private final ObservableMap<TableColumn, StringProperty> cellCSSList = FXCollections.observableHashMap();

    public SummeryData(ObservableMap<TableColumn, StringProperty> summeryList) {
        this.summeryList = summeryList;
    }

    public StringProperty getProperty(TableColumn column) {
        return summeryList.getOrDefault(column, null);
    }

    public ObservableMap<TableColumn, StringProperty> getSummeryList() {
        return summeryList;
    }

    public ObservableMap<TableColumn, StringProperty> getCellCss() {
        return cellCSSList;
    }

    public interface SummeryFunction {

        TableColumn getColumn();

        StringProperty getValue();

        default TableCell<SummeryData, String> getCellFactory() {
            return null;
        }

    }
}
