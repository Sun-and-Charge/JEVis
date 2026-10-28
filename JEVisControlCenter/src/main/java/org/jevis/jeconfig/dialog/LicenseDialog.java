/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.dialog;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;

import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Shows the license of JEVis and of all third-party components.
 * <p>
 * The library list is generated at build time by the license-maven-plugin
 * (see pom.xml), so new dependencies show up automatically. Resources which are
 * not Maven dependencies (icons, fonts, copied code) are listed in
 * jevis-licenses/bundled-assets.tsv.
 */
public class LicenseDialog {

    public static final String PROJECT_URL = "https://github.com/Sun-and-Charge/JEVis";
    private static final Logger logger = LogManager.getLogger(LicenseDialog.class);
    private static final String THIRD_PARTY_FILE = "/jevis-licenses/THIRD-PARTY.txt";
    private static final String BUNDLED_ASSETS_FILE = "/jevis-licenses/bundled-assets.tsv";
    private static final String LICENSE_FILE = "/jevis-licenses/LICENSE";
    private static final String NOTICE_FILE = "/jevis-licenses/NOTICE";

    private static void openBrowser(String url) {
        new Thread(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                logger.error("Could not open {}", url, e);
            }
        }).start();
    }

    public void show() {
        Stage stage = new Stage();
        stage.setTitle(I18n.getInstance().getString("licenses.title"));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(JEConfig.getStage());
        stage.setWidth(950);
        stage.setHeight(650);
        stage.setResizable(true);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getTabs().addAll(
                new Tab(I18n.getInstance().getString("licenses.tab.project"), buildProjectTab()),
                new Tab(I18n.getInstance().getString("licenses.tab.libraries"), buildComponentTab(THIRD_PARTY_FILE)),
                new Tab(I18n.getInstance().getString("licenses.tab.assets"), buildComponentTab(BUNDLED_ASSETS_FILE)));

        JFXButton close = new JFXButton(I18n.getInstance().getString("licenses.close"));
        close.setDefaultButton(true);
        close.setOnAction(event -> stage.close());
        HBox buttonPanel = new HBox(close);
        buttonPanel.setAlignment(Pos.CENTER_RIGHT);
        buttonPanel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(tabPane);
        root.setBottom(buttonPanel);

        Scene scene = new Scene(root);
        TopMenu.applyActiveTheme(scene);
        stage.setScene(scene);
        stage.showAndWait();
    }

    private VBox buildProjectTab() {
        Label summary = new Label(I18n.getInstance().getString("licenses.project.summary"));
        summary.setWrapText(true);

        Hyperlink projectLink = new Hyperlink(PROJECT_URL);
        projectLink.setOnAction(event -> openBrowser(PROJECT_URL));

        JFXTextArea text = new JFXTextArea();
        text.setEditable(false);
        text.setWrapText(true);
        String notice = readResource(NOTICE_FILE);
        String license = readResource(LICENSE_FILE);
        if (notice == null && license == null) {
            text.setText(I18n.getInstance().getString("licenses.missing"));
        } else {
            text.setText((notice != null ? notice + "\n\n" : "") + (license != null ? license : ""));
        }
        VBox.setVgrow(text, Priority.ALWAYS);

        VBox box = new VBox(8, summary, projectLink, text);
        box.setPadding(new Insets(10));
        return box;
    }

    private VBox buildComponentTab(String resource) {
        List<Component> components = readComponents(resource);
        FilteredList<Component> filtered = new FilteredList<>(FXCollections.observableArrayList(components), c -> true);

        JFXTextField search = new JFXTextField();
        search.setPromptText(I18n.getInstance().getString("licenses.search"));
        search.textProperty().addListener((observable, oldValue, newValue) -> {
            String filter = newValue == null ? "" : newValue.trim().toLowerCase(Locale.ROOT);
            filtered.setPredicate(c -> filter.isEmpty() || c.matches(filter));
        });

        TableView<Component> table = new TableView<>(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label(I18n.getInstance().getString(
                components.isEmpty() ? "licenses.missing" : "licenses.nomatch")));
        table.getColumns().add(column("licenses.col.name", c -> c.name, 300));
        TableColumn<Component, String> versionColumn = column("licenses.col.version", c -> c.version, 90);
        versionColumn.setMinWidth(90);
        versionColumn.setMaxWidth(120);
        table.getColumns().add(versionColumn);
        table.getColumns().add(column("licenses.col.license", c -> c.licenses, 260));

        TableColumn<Component, String> urlColumn = column("licenses.col.url", c -> c.url, 260);
        urlColumn.setCellFactory(col -> new TableCell<Component, String>() {
            private final Hyperlink link = new Hyperlink();

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isEmpty()) {
                    setGraphic(null);
                } else {
                    link.setText(item);
                    link.setOnAction(event -> openBrowser(item));
                    setGraphic(link);
                }
            }
        });
        table.getColumns().add(urlColumn);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label count = new Label();
        count.textProperty().bind(javafx.beans.binding.Bindings.size(filtered).asString(
                I18n.getInstance().getString("licenses.count")));

        VBox box = new VBox(8, search, table, count);
        box.setPadding(new Insets(10));
        return box;
    }

    private TableColumn<Component, String> column(String key, Function<Component, String> value, double width) {
        TableColumn<Component, String> column = new TableColumn<>(I18n.getInstance().getString(key));
        column.setCellValueFactory(param -> new ReadOnlyStringWrapper(value.apply(param.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    /**
     * Reads a tab separated component list: group, artifact, version, name, url, licenses ('|' separated).
     * Empty lines and lines starting with '#' are ignored.
     */
    private List<Component> readComponents(String resource) {
        List<Component> components = new ArrayList<>();
        String content = readResource(resource);
        if (content == null) {
            return components;
        }
        for (String line : content.split("\\r?\\n")) {
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                logger.warn("Invalid license entry in {}: {}", resource, line);
                continue;
            }
            String name = parts[3].trim().isEmpty() ? parts[1].trim() : parts[3].trim();
            String coordinates = parts[0].trim().isEmpty() ? "" : parts[0].trim() + ":" + parts[1].trim();
            components.add(new Component(name, coordinates, parts[2].trim(), parts[4].trim(),
                    String.join(", ", parts[5].trim().split("\\|"))));
        }
        components.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return components;
    }

    private String readResource(String resource) {
        try (InputStream in = LicenseDialog.class.getResourceAsStream(resource)) {
            if (in == null) {
                logger.warn("License resource not found: {}", resource);
                return null;
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            logger.error("Could not read license resource {}", resource, e);
            return null;
        }
    }

    private static class Component {
        private final String name;
        private final String coordinates;
        private final String version;
        private final String url;
        private final String licenses;

        Component(String name, String coordinates, String version, String url, String licenses) {
            this.name = name;
            this.coordinates = coordinates;
            this.version = version;
            this.url = url;
            this.licenses = licenses;
        }

        boolean matches(String filter) {
            return (name + " " + coordinates + " " + version + " " + licenses + " " + url)
                    .toLowerCase(Locale.ROOT).contains(filter);
        }
    }
}
