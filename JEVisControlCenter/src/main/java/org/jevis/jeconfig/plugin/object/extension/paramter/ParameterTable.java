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
package org.jevis.jeconfig.plugin.object.extension.paramter;


import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import org.jevis.commons.driver.Parameter;
import org.jevis.commons.driver.VarFiller;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTimeZone;

public class ParameterTable extends TableView<Parameter> {

    public ParameterTable() {
        super();
        buildTable();
    }

    public void buildTable() {
        TableColumn<Parameter, String> format = new TableColumn(I18n.getInstance().getString("plugin.objects.extension.paramter.source"));
        format.setCellFactory(TextFieldTableCell.forTableColumn());
        format.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFormat()));
        format.setOnEditCommit(event -> event.getRowValue().setFormat(event.getNewValue()));
        format.setEditable(true);

        TableColumn<Parameter, VarFiller.Variable> sourceCol = new TableColumn(I18n.getInstance().getString("plugin.objects.extension.paramter.format"));
        sourceCol.setCellFactory(ComboBoxTableCell.forTableColumn(VarFiller.Variable.LAST_TS, VarFiller.Variable.CURRENT_TS, VarFiller.Variable.CURRENT_TS2));
        sourceCol.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getVariable()));
        sourceCol.setOnEditCommit(event -> event.getRowValue().setVariable(event.getNewValue()));
        sourceCol.setEditable(true);




        TableColumn<Parameter, String> timeZoneCol = new TableColumn(I18n.getInstance().getString("plugin.objects.extension.paramter.timezone"));
        timeZoneCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(DateTimeZone.getAvailableIDs())));
        timeZoneCol.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getTimezone()));
        timeZoneCol.setOnEditCommit(event -> event.getRowValue().setTimezone(event.getNewValue()));
        timeZoneCol.setEditable(true);





        this.setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY);


        this.getColumns().addAll(sourceCol,format,timeZoneCol);

        this.setEditable(true);


    }
}
