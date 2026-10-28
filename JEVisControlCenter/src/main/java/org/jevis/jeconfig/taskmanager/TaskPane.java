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
package org.jevis.jeconfig.taskmanager;

import com.jfoenix.controls.JFXProgressBar;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.AnchorPane;
import javafx.util.Callback;
import org.jevis.jeconfig.tool.Layouts;

public class TaskPane extends AnchorPane {

    TableView<Task> taskTableView = new TableView<>();
    final ObservableList<Task> tasksList;

    public TaskPane(ObservableList<Task> tasks) {
        this.tasksList = tasks;


        taskTableView.setEditable(false);

        TableColumn<Task, String> taskNameColumn = new TableColumn("Task");
        taskNameColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Task, String>, ObservableValue<String>>() {
            @Override
            public ObservableValue<String> call(TableColumn.CellDataFeatures<Task, String> param) {
                return new SimpleStringProperty(param.getValue().getTitle());
            }
        });
        taskNameColumn.setCellFactory(TextFieldTableCell.forTableColumn());


        TableColumn<Task, JFXProgressBar> taskProgressColumn = new TableColumn("Progress");
        taskProgressColumn.setCellValueFactory(new Callback<TableColumn.CellDataFeatures<Task, JFXProgressBar>, ObservableValue<JFXProgressBar>>() {
            @Override
            public ObservableValue<JFXProgressBar> call(TableColumn.CellDataFeatures<Task, JFXProgressBar> param) {
                JFXProgressBar progressBar = new JFXProgressBar();
                progressBar.progressProperty().bind(param.getValue().progressProperty());
                return new SimpleObjectProperty<>(progressBar);
            }
        });
        taskProgressColumn.setCellFactory(new Callback<TableColumn<Task, JFXProgressBar>, TableCell<Task, JFXProgressBar>>() {
            @Override
            public TableCell<Task, JFXProgressBar> call(TableColumn<Task, JFXProgressBar> param) {
                return new TableCell<Task, JFXProgressBar>() {
                    @Override
                    protected void updateItem(JFXProgressBar item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(null);
                        setGraphic(item);
                    }
                };
            }
        });
        taskTableView.getColumns().addAll(taskNameColumn,taskProgressColumn);
        this.getChildren().add(taskTableView);
        Layouts.setAnchor(taskTableView,0);

    }

    public void addTask(Task task){
        tasksList.add(task);
    }

}
