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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.utils.CommonMethods;
import org.jevis.jeconfig.Plugin;
import org.jevis.jeconfig.TopMenu;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Consumer;

/**
 * Search over modules, dashboards and analyses (Strg+K, J25.5 stage 3), as in the web app. Without a query it shows
 * the favourites and the recently opened entries. The selected site limits dashboards and analyses.
 */
public class QuickSearch {
    public static final String CLASS_ANALYSIS = "Analysis";
    public static final String CLASS_DASHBOARD = "Dashboard Analysis";
    public static final String CLASS_SITE = "Building";
    private static final Logger logger = LogManager.getLogger(QuickSearch.class);

    private final JEVisDataSource ds;
    private final List<Plugin> plugins;
    private final Consumer<Plugin> openPlugin;
    private final Consumer<JEVisObject> openObject;
    private final Map<Long, JEVisObject> siteOfObject = new HashMap<>();

    public QuickSearch(JEVisDataSource ds, List<Plugin> plugins, Consumer<Plugin> openPlugin, Consumer<JEVisObject> openObject) {
        this.ds = ds;
        this.plugins = plugins;
        this.openPlugin = openPlugin;
        this.openObject = openObject;
    }

    /** one hit of the search */
    static final class Entry {
        final Plugin plugin;
        final JEVisObject object;
        final String name;
        final String kind;
        final String site;

        Entry(Plugin plugin, JEVisObject object, String name, String kind, String site) {
            this.plugin = plugin;
            this.object = object;
            this.name = name;
            this.kind = kind;
            this.site = site;
        }
    }

    /** the sites (Building objects) the user can read, by name */
    public List<JEVisObject> sites() {
        List<JEVisObject> sites = new ArrayList<>();
        try {
            sites.addAll(ds.getObjects(ds.getJEVisClass(CLASS_SITE), false));
            sites.sort(Comparator.comparing(JEVisObject::getName, String.CASE_INSENSITIVE_ORDER));
        } catch (Exception e) {
            logger.warn("Could not load sites", e);
        }
        return sites;
    }

    private JEVisObject siteOf(JEVisObject object) {
        return siteOfObject.computeIfAbsent(object.getID(), id -> {
            try {
                return CommonMethods.getFirstParentalObjectOfClass(object, CLASS_SITE);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private List<Entry> objects(String className, String kind) {
        List<Entry> entries = new ArrayList<>();
        long site = NavigationStore.siteProperty().get();
        try {
            for (JEVisObject object : ds.getObjects(ds.getJEVisClass(className), false)) {
                JEVisObject objectSite = siteOf(object);
                if (site != 0 && (objectSite == null || objectSite.getID() != site)) continue;
                entries.add(new Entry(null, object, object.getName(), kind, objectSite == null ? "" : objectSite.getName()));
            }
        } catch (Exception e) {
            logger.warn("Could not load {}", className, e);
        }
        return entries;
    }

    private List<Entry> allEntries() {
        List<Entry> entries = new ArrayList<>();
        for (Plugin plugin : plugins) {
            entries.add(new Entry(plugin, null, plugin.getName(), PluginSidebar.text("navigation.search.module", "Modul", "Module"), ""));
        }
        entries.addAll(objects(CLASS_DASHBOARD, PluginSidebar.text("navigation.search.dashboard", "Dashboard", "Dashboard")));
        entries.addAll(objects(CLASS_ANALYSIS, PluginSidebar.text("navigation.search.analysis", "Analyse", "Analysis")));
        return entries;
    }

    private static String fold(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    /** entries matching every word of the query; without a query favourites, then recently opened, then modules */
    List<Entry> find(List<Entry> all, String query) {
        List<Entry> result = new ArrayList<>();
        String[] words = fold(query).trim().split("\\s+");
        if (query == null || query.trim().isEmpty()) {
            Map<Long, Entry> byId = new HashMap<>();
            for (Entry entry : all) if (entry.object != null) byId.put(entry.object.getID(), entry);
            Set<Long> shown = new HashSet<>();
            for (Long id : NavigationStore.favourites()) if (byId.containsKey(id) && shown.add(id)) result.add(byId.get(id));
            for (Long id : NavigationStore.recent()) if (byId.containsKey(id) && shown.add(id)) result.add(byId.get(id));
            for (Entry entry : all) if (entry.plugin != null) result.add(entry);
            return result;
        }
        for (Entry entry : all) {
            String haystack = fold(entry.name + " " + entry.site + " " + entry.kind);
            boolean match = true;
            for (String word : words) {
                if (!haystack.contains(word)) {
                    match = false;
                    break;
                }
            }
            if (match) result.add(entry);
        }
        /* names starting with the query first, favourites before the rest */
        String first = words[0];
        result.sort(Comparator.comparing((Entry e) -> !fold(e.name).startsWith(first))
                .thenComparing(e -> !NavigationStore.isFavourite(e.object))
                .thenComparing(e -> e.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public void show(Window owner) {
        List<Entry> all = allEntries();
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);

        TextField query = new TextField();
        query.setPromptText(PluginSidebar.text("navigation.search.prompt", "Module, Dashboards und Analysen suchen",
                "Search modules, dashboards and analyses"));
        query.getStyleClass().add("quick-search-field");

        ObservableList<Entry> items = FXCollections.observableArrayList(find(all, ""));
        ListView<Entry> list = new ListView<>(items);
        list.getStyleClass().add("quick-search-list");
        list.setPrefHeight(420);
        list.setCellFactory(view -> new ListCell<Entry>() {
            @Override
            protected void updateItem(Entry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                Label name = new Label(entry.name);
                name.getStyleClass().add("quick-search-name");
                Label detail = new Label(entry.site.isEmpty() ? entry.kind : entry.kind + " · " + entry.site);
                detail.getStyleClass().add("quick-search-detail");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                HBox row = new HBox(10, name, spacer, detail);
                row.setAlignment(Pos.CENTER_LEFT);
                if (entry.object != null) {
                    Button star = new Button(NavigationStore.isFavourite(entry.object) ? "★" : "☆");
                    star.getStyleClass().addAll("flat", "quick-search-star");
                    star.setFocusTraversable(false);
                    star.setTooltip(new Tooltip(PluginSidebar.text("navigation.search.favourite", "Favorit", "Favourite")));
                    star.setOnAction(event -> {
                        NavigationStore.toggleFavourite(entry.object);
                        list.refresh();
                    });
                    row.getChildren().add(star);
                }
                setText(null);
                setGraphic(row);
            }
        });

        Label hint = new Label(PluginSidebar.text("navigation.search.hint",
                "Enter öffnet · ↑↓ wählen · Esc schließt · ☆ merkt als Favorit",
                "Enter opens · ↑↓ select · Esc closes · ☆ marks as favourite"));
        hint.getStyleClass().add("quick-search-hint");

        Runnable open = () -> {
            Entry entry = list.getSelectionModel().getSelectedItem();
            if (entry == null && !list.getItems().isEmpty()) entry = list.getItems().get(0);
            if (entry == null) return;
            stage.close();
            Entry chosen = entry;
            Platform.runLater(() -> {
                if (chosen.plugin != null) {
                    openPlugin.accept(chosen.plugin);
                } else {
                    NavigationStore.recordOpened(chosen.object);
                    openObject.accept(chosen.object);
                }
            });
        };

        query.textProperty().addListener((observable, oldValue, newValue) -> {
            items.setAll(find(all, newValue));
            if (!items.isEmpty()) list.getSelectionModel().select(0);
        });
        query.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN) {
                list.getSelectionModel().selectNext();
                list.scrollTo(list.getSelectionModel().getSelectedIndex());
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                list.getSelectionModel().selectPrevious();
                list.scrollTo(list.getSelectionModel().getSelectedIndex());
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                open.run();
                event.consume();
            }
        });
        list.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) open.run();
        });
        list.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) open.run();
        });

        VBox box = new VBox(8, query, list, hint);
        box.setPadding(new Insets(12));
        box.getStyleClass().add("quick-search");
        Scene scene = new Scene(box, 640, 500);
        TopMenu.applyActiveTheme(scene);
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) stage.close();
        });
        stage.focusedProperty().addListener((observable, oldValue, focused) -> {
            if (!focused) stage.close();
        });
        stage.setScene(scene);
        if (owner != null) {
            stage.setX(owner.getX() + (owner.getWidth() - 640) / 2);
            stage.setY(owner.getY() + 80);
        }
        if (!items.isEmpty()) list.getSelectionModel().select(0);
        stage.show();
        query.requestFocus();
    }
}
