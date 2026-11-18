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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.utils.CommonMethods;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Consumer;

/**
 * Overview page of the dashboards or analyses (J25.5 stage 4), as in the web app: search, the site of the top bar,
 * favourites, recently opened and all entries grouped by site. A click opens the entry; ☆ marks a favourite.
 */
public class OverviewPane extends BorderPane {
    private static final Logger logger = LogManager.getLogger(OverviewPane.class);

    private final JEVisDataSource ds;
    private final String className;
    private final Consumer<JEVisObject> onOpen;
    private final TextField search = new TextField();
    private final VBox sections = new VBox(18);
    private final Map<Long, String> siteNames = new HashMap<>();
    private final Map<Long, Long> siteIds = new HashMap<>();
    private List<JEVisObject> objects = new ArrayList<>();

    /**
     * @param title   heading, e.g. "Analysen"
     * @param actions buttons next to the search (e.g. "Neue Analyse"; the first one is the primary button)
     */
    public OverviewPane(JEVisDataSource ds, String className, String title, Consumer<JEVisObject> onOpen, Button... actions) {
        this.ds = ds;
        this.className = className;
        this.onOpen = onOpen;
        getStyleClass().add("overview-pane");

        Label heading = new Label(title);
        heading.getStyleClass().addAll("suc-heading", "overview-title");

        search.setPromptText(PluginSidebar.text("navigation.overview.search", "Suchen", "Search"));
        search.getStyleClass().add("overview-search");
        search.setPrefWidth(360);
        search.textProperty().addListener((observable, oldValue, newValue) -> render());
        search.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) search.clear();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(12, heading, spacer, search);
        head.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < actions.length; i++) {
            if (i == 0) actions[i].setDefaultButton(true);
            head.getChildren().add(actions[i]);
        }
        head.setPadding(new Insets(20, 24, 12, 24));
        setTop(head);

        sections.setPadding(new Insets(4, 24, 24, 24));
        ScrollPane scroll = new ScrollPane(sections);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("overview-scroll");
        setCenter(scroll);

        NavigationStore.siteProperty().addListener((observable, oldValue, newValue) -> render());
    }

    /** loads the entries the user can read (again) and shows them */
    public void refresh() {
        Thread loader = new Thread(() -> {
            List<JEVisObject> loaded = new ArrayList<>();
            try {
                loaded.addAll(ds.getObjects(ds.getJEVisClass(className), false));
                for (JEVisObject object : loaded) {
                    JEVisObject site = CommonMethods.getFirstParentalObjectOfClass(object, QuickSearch.CLASS_SITE);
                    siteIds.put(object.getID(), site == null ? 0L : site.getID());
                    siteNames.put(object.getID(), site == null ? "" : site.getName());
                }
            } catch (Exception e) {
                logger.error("Could not load {}", className, e);
            }
            loaded.sort(Comparator.comparing(JEVisObject::getName, String.CASE_INSENSITIVE_ORDER));
            Platform.runLater(() -> {
                objects = loaded;
                render();
            });
        }, "overview-" + className);
        loader.setDaemon(true);
        loader.start();
    }

    public void focusSearch() {
        Platform.runLater(search::requestFocus);
    }

    private static String fold(String text) {
        return Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private void render() {
        long site = NavigationStore.siteProperty().get();
        String[] words = fold(search.getText()).trim().split("\\s+");
        List<JEVisObject> visible = new ArrayList<>();
        for (JEVisObject object : objects) {
            if (site != 0 && !Long.valueOf(site).equals(siteIds.get(object.getID()))) continue;
            String haystack = fold(object.getName() + " " + siteNames.get(object.getID()));
            boolean match = true;
            for (String word : words) if (!word.isEmpty() && !haystack.contains(word)) match = false;
            if (match) visible.add(object);
        }
        Map<Long, JEVisObject> byId = new LinkedHashMap<>();
        visible.forEach(object -> byId.put(object.getID(), object));

        sections.getChildren().clear();
        boolean searching = !search.getText().trim().isEmpty();
        if (!searching) {
            addSection(PluginSidebar.text("navigation.overview.favourites", "Favoriten", "Favourites"), pick(byId, NavigationStore.favourites()));
            addSection(PluginSidebar.text("navigation.overview.recent", "Zuletzt geöffnet", "Recently opened"), pick(byId, NavigationStore.recent()));
        }
        /* all entries, grouped by site */
        Map<String, List<JEVisObject>> bySite = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (JEVisObject object : visible) {
            String siteName = siteNames.getOrDefault(object.getID(), "");
            bySite.computeIfAbsent(siteName.isEmpty() ? PluginSidebar.text("navigation.overview.nosite", "Ohne Standort", "Without site") : siteName,
                    key -> new ArrayList<>()).add(object);
        }
        if (bySite.isEmpty()) {
            Label empty = new Label(searching
                    ? PluginSidebar.text("navigation.overview.nohits", "Keine Treffer", "No results")
                    : PluginSidebar.text("navigation.overview.empty", "Noch keine Einträge", "No entries yet"));
            empty.getStyleClass().add("overview-empty");
            sections.getChildren().add(empty);
        }
        bySite.forEach(this::addSection);
    }

    private static List<JEVisObject> pick(Map<Long, JEVisObject> byId, List<Long> ids) {
        List<JEVisObject> picked = new ArrayList<>();
        for (Long id : ids) if (byId.containsKey(id)) picked.add(byId.get(id));
        return picked;
    }

    private void addSection(String title, List<JEVisObject> entries) {
        if (entries.isEmpty()) return;
        Label header = new Label(title + "  " + entries.size());
        header.getStyleClass().add("overview-section");
        FlowPane cards = new FlowPane(10, 10);
        for (JEVisObject object : entries) cards.getChildren().add(card(object));
        sections.getChildren().add(new VBox(8, header, cards));
    }

    private javafx.scene.Node card(JEVisObject object) {
        Label name = new Label(object.getName());
        name.getStyleClass().add("overview-card-name");
        name.setWrapText(true);
        Label site = new Label(siteNames.getOrDefault(object.getID(), ""));
        site.getStyleClass().add("overview-card-site");
        Button star = new Button(NavigationStore.isFavourite(object) ? "★" : "☆");
        star.getStyleClass().addAll("flat", "quick-search-star");
        star.setFocusTraversable(false);
        star.setTooltip(new Tooltip(PluginSidebar.text("navigation.search.favourite", "Favorit", "Favourite")));
        star.setOnAction(event -> {
            NavigationStore.toggleFavourite(object);
            render();
            event.consume();
        });
        /* long names wrap, the star keeps its size */
        star.setMinWidth(Region.USE_PREF_SIZE);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setMinHeight(Region.USE_PREF_SIZE);
        name.setTooltip(new Tooltip(object.getName()));
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox top = new HBox(4, name, star);
        top.setAlignment(Pos.TOP_LEFT);
        VBox card = new VBox(4, top, site);
        card.getStyleClass().add("overview-card");
        card.setPrefWidth(260);
        card.setOnMouseClicked(event -> {
            NavigationStore.recordOpened(object);
            onOpen.accept(object);
        });
        return card;
    }

}
