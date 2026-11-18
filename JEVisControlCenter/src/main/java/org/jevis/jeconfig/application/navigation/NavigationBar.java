/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.application.navigation;

import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;
import org.jevis.api.JEVisObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Top row of the main window (J25.5 stage 3): menu, site picker and the search button (Strg+K). The site limits
 * the search and the overview pages; with only one site the picker is hidden.
 */
public class NavigationBar extends HBox {

    public NavigationBar(MenuBar menu, QuickSearch search, Runnable openSearch) {
        getStyleClass().add("navigation-bar");
        setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(menu, Priority.ALWAYS);
        menu.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(menu);

        List<JEVisObject> sites = search.sites();
        if (sites.size() > 1) {
            List<JEVisObject> items = new ArrayList<>();
            items.add(null);
            items.addAll(sites);
            ComboBox<JEVisObject> sitePicker = new ComboBox<>();
            sitePicker.getItems().setAll(items);
            sitePicker.getStyleClass().add("navigation-site");
            sitePicker.setTooltip(new Tooltip(PluginSidebar.text("navigation.site.tooltip",
                    "Standort für Suche und Übersichten", "Site for search and overviews")));
            StringConverter<JEVisObject> converter = new StringConverter<JEVisObject>() {
                @Override
                public String toString(JEVisObject object) {
                    return object == null ? PluginSidebar.text("navigation.site.all", "Alle Standorte", "All sites") : object.getName();
                }

                @Override
                public JEVisObject fromString(String string) {
                    return null;
                }
            };
            sitePicker.setConverter(converter);
            sitePicker.setButtonCell(new ListCell<JEVisObject>() {
                @Override
                protected void updateItem(JEVisObject item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : converter.toString(item));
                }
            });
            sitePicker.setCellFactory(view -> new ListCell<JEVisObject>() {
                @Override
                protected void updateItem(JEVisObject item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : converter.toString(item));
                }
            });
            long selected = NavigationStore.siteProperty().get();
            JEVisObject current = null;
            for (JEVisObject site : sites) if (site.getID() == selected) current = site;
            sitePicker.getSelectionModel().select(current);
            sitePicker.valueProperty().addListener((observable, oldValue, newValue) ->
                    NavigationStore.siteProperty().set(newValue == null ? 0 : newValue.getID()));
            getChildren().add(sitePicker);
        }

        Button searchButton = new Button(PluginSidebar.text("navigation.search.button", "Suchen", "Search") + "   Strg+K");
        searchButton.getStyleClass().add("navigation-search");
        searchButton.setFocusTraversable(false);
        searchButton.setOnAction(event -> openSearch.run());
        getChildren().add(searchButton);
    }
}
