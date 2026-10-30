/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.plugin.action.ui;

import com.jfoenix.controls.JFXTextField;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.converter.DoubleStringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.plugin.action.data.ActionData;
import org.jevis.jeconfig.plugin.action.data.ActionPlanData;
import org.jevis.jeconfig.plugin.action.data.Medium;
import org.jevis.jeconfig.tool.ScreenSize;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ActionPlanForm extends Alert {
    private static final Logger logger = LogManager.getLogger(ActionPlanForm.class);
    private final ActionPlanData actionPlan;
    private final double iconSize = 12;
    Label nameLabel = new Label("Name");
    Label statusLabel = new Label("Status");
    Label fieldsLabel = new Label("Bereiche");
    Label mediumLabel = new Label("Medien");
    Label enpiLabel = new Label("EnPI");
    Label sueLabel = new Label("SEU");
    Label numberPrefix = new Label("Nr. Prefix");
    JFXTextField f_numberPrefix = new JFXTextField();
    JFXTextField nameField = new JFXTextField();
    ListView<String> statusListView = new ListView<>();
    ListView<String> fieldsListView = new ListView<>();
    TableView<Medium> mediumListView = new TableView<>();
    ListView<JEVisObject> enpiListView = new ListView<>();
    ListView<String> sueListView = new ListView<>();
    StackPane stackPane = new StackPane();
    /**
     * Renamed tags, applied to the actions of the plan by {@link #commitRenames()}.
     */
    private final List<Runnable> pendingRenames = new ArrayList<>();

    /**
     * Replaces a renamed tag in an action.
     */
    private interface TagRename {
        /**
         * @return {@code true} if the action was changed
         */
        boolean rename(ActionData action, String oldValue, String newValue);
    }

    private static final TagRename STATUS_RENAME = (action, oldValue, newValue) -> {
        if (oldValue.equals(action.statusTags.get())) {
            action.statusTags.set(newValue);
            return true;
        }
        return false;
    };

    private static final TagRename FIELD_RENAME = (action, oldValue, newValue) -> {
        String renamed = renameInList(action.fieldTags.get(), oldValue, newValue);
        if (renamed != null) {
            action.fieldTags.set(renamed);
            return true;
        }
        return false;
    };

    private static final TagRename SEU_RENAME = (action, oldValue, newValue) -> {
        String renamed = renameInList(action.seuTagsProperty().get(), oldValue, newValue);
        if (renamed != null) {
            action.seuTagsProperty().set(renamed);
            return true;
        }
        return false;
    };

    public ActionPlanForm(ActionPlanData actionPlan) {
        super(AlertType.INFORMATION);
        this.actionPlan = actionPlan;
        this.initOwner(JEConfig.getStage());

        setTitle(I18n.getInstance().getString("planform.editor.title"));
        setHeaderText(I18n.getInstance().getString("planform.editor.header"));
        setResizable(true);
        setWidth(ScreenSize.fitScreenWidth(800));
        setHeight(ScreenSize.fitScreenHeight(600));

        Stage stage = (Stage) this.getDialogPane().getScene().getWindow();
        TopMenu.applyActiveTheme(stage.getScene());
        stage.setAlwaysOnTop(false);

        GridPane statusPane = buildMediumView(statusListView, STATUS_RENAME);
        GridPane mediumPane = buildMediumView(mediumListView);
        GridPane suePane = buildMediumView(sueListView, SEU_RENAME);
        GridPane fieldPane = buildMediumView(fieldsListView, FIELD_RENAME);
        GridPane enpiPane = buildENPIList(enpiListView);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(12);
        gridPane.setVgap(8);


        gridPane.add(nameLabel, 0, 0);
        gridPane.add(nameField, 1, 0);
        gridPane.add(numberPrefix, 0, 1);
        gridPane.add(f_numberPrefix, 1, 1);
        gridPane.add(new Region(), 0, 2);

        gridPane.add(statusLabel, 3, 0, 2, 1);
        gridPane.add(statusPane, 3, 1, 2, 2);

        gridPane.add(mediumLabel, 0, 3, 2, 1);
        gridPane.add(mediumPane, 0, 4, 2, 1);

        gridPane.add(sueLabel, 0, 5, 2, 1);
        gridPane.add(suePane, 0, 6, 2, 1);

        gridPane.add(fieldsLabel, 3, 3, 2, 1);
        gridPane.add(fieldPane, 3, 4, 2, 1);

        gridPane.add(enpiLabel, 3, 5, 2, 1);
        gridPane.add(enpiPane, 3, 6, 2, 1);

        stackPane.getChildren().add(gridPane);
        getDialogPane().setContent(stackPane);
        updateView(actionPlan);
    }


    public void updateView(ActionPlanData actionPlan) {
        //nameField.setText(actionPlan.getName());

        nameField.textProperty().bindBidirectional(actionPlan.getName());
        f_numberPrefix.textProperty().bindBidirectional(actionPlan.noPrefixProperty());
        statusListView.setItems(actionPlan.getStatustags());
        fieldsListView.setItems(actionPlan.getFieldsTags());

        ObservableList<Medium> mediumsTMP = FXCollections.observableArrayList(
                new Medium("1", "Strom", 2),
                new Medium("2", "Gas", 3)
        );
        mediumListView.setItems(actionPlan.getMedium());

        //mediumListView.setItems(actionPlan.getMediumTags());
        sueListView.setItems(actionPlan.significantEnergyUseTags());

    }

    private GridPane buildENPIList(ListView<JEVisObject> listView) {
        enpiListView.setCellFactory(new Callback<ListView<JEVisObject>, ListCell<JEVisObject>>() {
            @Override
            public ListCell<JEVisObject> call(ListView<JEVisObject> param) {
                return new ListCell<JEVisObject>() {
                    @Override
                    protected void updateItem(JEVisObject item, boolean empty) {
                        super.updateItem(item, empty);
                        //System.out.println("Update Item: " + item + "   is empty: " + empty);
                        if (item != null) {
                            setText(item.getName());
                            try {
                                //setTooltip(new Tooltip(objectRelations.getObjectPath(item) + objectRelations.getRelativePath(item) + item.getName()));
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        } else {
                            setText(null);
                        }

                    }
                };
            }
        });
        //actionPlan.getEnpis().add(actionPlan.getObject());
        enpiListView.setItems(actionPlan.getEnpis());
        enpiListView.selectionModelProperty().addListener(new ChangeListener<MultipleSelectionModel<JEVisObject>>() {
            @Override
            public void changed(ObservableValue<? extends MultipleSelectionModel<JEVisObject>> observable, MultipleSelectionModel<JEVisObject> oldValue, MultipleSelectionModel<JEVisObject> newValue) {

            }
        });

        Button addButton = new Button("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, iconSize, iconSize));
        Button removeButton = new Button("", JEConfig.getSVGImage(Icon.MINUS_CIRCLE, iconSize, iconSize));
        addButton.setOnAction(event -> {
            try {
                List<JEVisClass> classes = new ArrayList<>();

                TreeSelectionDialog treeSelectionDialog = new TreeSelectionDialog(actionPlan.getObject().getDataSource(), classes, SelectionMode.MULTIPLE, new ArrayList<>(), false);
                Optional<ButtonType> optional = treeSelectionDialog.showAndWait();
                if (optional.get() == treeSelectionDialog.buttonOK) {
                    logger.debug("apply");
                    treeSelectionDialog.getUserSelection().forEach(userSelection -> {
                        actionPlan.getEnpis().add(userSelection.getSelectedObject());
                    });
                }

            } catch (Exception ex) {
                logger.error(ex);
            }


        });

        removeButton.setOnAction(event -> {
            try {
                listView.getItems().remove(listView.getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
            }
        });

        listView.setMaxHeight(100);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);

        gridPane.add(listView, 0, 0, 1, 3);
        gridPane.add(addButton, 1, 0);
        gridPane.add(removeButton, 1, 1);

        return gridPane;

    }

    /**
     * Replaces a value in a ";" separated list.
     *
     * @return the new list or {@code null} if the value is not in the list
     */
    private static String renameInList(String list, String oldValue, String newValue) {
        if (list == null || list.isEmpty()) return null;
        List<String> values = new ArrayList<>(Arrays.asList(list.split(";")));
        if (!values.contains(oldValue)) return null;
        return values.stream().map(value -> value.equals(oldValue) ? newValue : value).collect(Collectors.joining(";"));
    }

    /**
     * Replaces the renamed tags in all actions of the plan and saves the changed actions.
     * Called when the settings are confirmed.
     */
    public void commitRenames() {
        pendingRenames.forEach(Runnable::run);
        pendingRenames.clear();
    }

    private GridPane buildMediumView(ListView<String> listView, TagRename tagRename) {
        Button addButton = new Button("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, iconSize, iconSize));
        Button removeButton = new Button("", JEConfig.getSVGImage(Icon.MINUS_CIRCLE, iconSize, iconSize));

        addButton.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog("");
            dialog.initOwner(getDialogPane().getScene().getWindow());
            dialog.setTitle(I18n.getInstance().getString("planform.editor.newentry.title"));
            dialog.setHeaderText(I18n.getInstance().getString("planform.editor.newentry.header"));
            dialog.setContentText(I18n.getInstance().getString("planform.editor.newentry.name"));

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().trim().isEmpty() && !listView.getItems().contains(result.get().trim())) {
                listView.getItems().add(result.get().trim());
                FXCollections.sort(listView.getItems(), String.CASE_INSENSITIVE_ORDER);
            }
        });

        listView.setEditable(true);
        listView.setCellFactory(TextFieldListCell.forListView());
        listView.setOnEditCommit(event -> {
            int index = event.getIndex();
            if (index < 0 || index >= listView.getItems().size() || event.getNewValue() == null) return;
            String oldValue = listView.getItems().get(index);
            String newValue = event.getNewValue().trim();
            if (newValue.isEmpty() || newValue.equals(oldValue) || listView.getItems().contains(newValue)) return;

            listView.getItems().set(index, newValue);
            FXCollections.sort(listView.getItems(), String.CASE_INSENSITIVE_ORDER);
            pendingRenames.add(() -> renameInActions(tagRename, oldValue, newValue));
        });

        removeButton.setOnAction(event -> {
            try {
                listView.getItems().remove(listView.getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
            }
        });

        //Icon.PLUS_CIRCLE
        //Icon.MINUS_CIRCLE

        listView.setMaxHeight(100);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);

        gridPane.add(listView, 0, 0, 1, 3);
        gridPane.add(addButton, 1, 0);
        gridPane.add(removeButton, 1, 1);

        return gridPane;

    }

    private void renameInActions(TagRename tagRename, String oldValue, String newValue) {
        try {
            actionPlan.loadActionList();
            for (ActionData action : actionPlan.getActionData()) {
                if (tagRename.rename(action, oldValue, newValue)) {
                    action.commit();
                }
            }
        } catch (Exception ex) {
            logger.error("Could not rename {} to {} in the actions", oldValue, newValue, ex);
        }
    }

    private GridPane buildMediumView(TableView<Medium> listView) {
        Button addButton = new Button("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, iconSize, iconSize));
        Button removeButton = new Button("", JEConfig.getSVGImage(Icon.MINUS_CIRCLE, iconSize, iconSize));

        addButton.setOnAction(event -> {
            listView.getItems().add(new Medium());
        });

        removeButton.setOnAction(event -> {
            try {
                listView.getItems().remove(listView.getSelectionModel().getSelectedItem());
            } catch (Exception ex) {
            }
        });

        //Icon.PLUS_CIRCLE
        //Icon.MINUS_CIRCLE

        listView.setMaxHeight(100);
        TableColumn<Medium, String> nameCol = new TableColumn("Name");
        TableColumn<Medium, Double> co2Col = new TableColumn("tCO2/MWh"); //CO2-Faktor
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        listView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        co2Col.setCellValueFactory(new PropertyValueFactory<>("co2"));
        co2Col.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));

        listView.getColumns().addAll(nameCol, co2Col);
        nameCol.setEditable(true);
        co2Col.setEditable(true);
        listView.setEditable(true);

        GridPane gridPane = new GridPane();
        gridPane.setHgap(8);
        gridPane.setVgap(8);

        gridPane.add(listView, 0, 0, 1, 3);
        gridPane.add(addButton, 1, 0);
        gridPane.add(removeButton, 1, 1);

        return gridPane;

    }
}
