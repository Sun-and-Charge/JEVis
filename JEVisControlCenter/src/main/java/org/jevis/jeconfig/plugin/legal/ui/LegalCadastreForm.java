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
package org.jevis.jeconfig.plugin.legal.ui;

import javafx.scene.control.TextField;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.plugin.legal.data.IndexOfLegalProvisions;
import org.jevis.jeconfig.tool.ScreenSize;

import java.util.Optional;

public class LegalCadastreForm extends org.jevis.jeconfig.application.ui.SucDialog<javafx.scene.control.ButtonType> {

    Label nameLabel = new Label(I18n.getInstance().getString("plugin.indexoflegalprovisions.planname"));


    TextField nameField = new TextField();

    Label l_category = new Label(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.category"));
    Label l_scope = new Label(I18n.getInstance().getString("plugin.indexoflegalprovisions.obligation.scope"));

    ListView<String> categoryListView = new ListView<>();
    ListView<String> validityListView = new ListView<>();


    private IndexOfLegalProvisions indexOfLegalProvisions;
    StackPane stackPane = new StackPane();
    private double iconSize = 12;

    public LegalCadastreForm(IndexOfLegalProvisions indexOfLegalProvisions) {
        super(I18n.getInstance().getString("plugin.indexoflegalprovisions.indexoflegalprovisions.dialog.title"), I18n.getInstance().getString("plugin.indexoflegalprovisions.indexoflegalprovisions.dialog.header"), Size.L);
        this.indexOfLegalProvisions = indexOfLegalProvisions;
        /* J25.5: "Speichern" and "Abbrechen" instead of a single OK */
        addFormPrimary(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.save", "Speichern", "Save"));
        addCancel();


        GridPane gridPane = new GridPane();
        gridPane.setHgap(12);
        gridPane.setVgap(8);

        GridPane categoryPane = buildCustomList(categoryListView);
        GridPane levelPane = buildCustomList(validityListView);


        gridPane.add(nameLabel, 0, 0);
        gridPane.add(nameField, 1, 0);


        gridPane.add(l_category, 0, 4, 2, 1);
        gridPane.add(categoryPane, 0, 5, 2, 2);

        gridPane.add(l_scope, 0, 2, 2, 1);
        gridPane.add(levelPane, 0, 3, 2, 1);


        stackPane.getChildren().add(gridPane);
        getDialogPane().setContent(stackPane);

        updateView(indexOfLegalProvisions);
    }


    public void updateView(IndexOfLegalProvisions indexOfLegalProvisions) {

        nameField.textProperty().bindBidirectional(indexOfLegalProvisions.getName());

        categoryListView.setItems(indexOfLegalProvisions.getCategories());
        validityListView.setItems(indexOfLegalProvisions.getScopes());


    }


    private GridPane buildCustomList(ListView<String> listView) {
        Button addButton = new Button("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, iconSize, iconSize));
        Button removeButton = new Button("", JEConfig.getSVGImage(Icon.MINUS_CIRCLE, iconSize, iconSize));

        addButton.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog("");
            dialog.setTitle(I18n.getInstance().getString("plugin.indexoflegalprovisions.newentry.title"));
            dialog.setHeaderText(I18n.getInstance().getString("plugin.indexoflegalprovisions.newentry.header"));
            dialog.setContentText(I18n.getInstance().getString("plugin.indexoflegalprovisions.newentry.content"));

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent() && !result.get().isEmpty()) {
                listView.getItems().add(result.get());
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
}
