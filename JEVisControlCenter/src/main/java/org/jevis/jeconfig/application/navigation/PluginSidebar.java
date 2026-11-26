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

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Plugin;

import java.util.*;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Grouped, collapsible navigation of the Control Center modules (J25.5 stage 2, docs/DESIGN-CC.md), as in the web
 * app: Dashboards, Analysen, groups ISO 50001 / Betrieb / Anlagen / Finanzen, then Berichte and Konfiguration.
 * Modules without a known place go to "Weitere". Icon-only mode and collapsed groups are user preferences.
 */
public class PluginSidebar extends VBox {

    private static final Preferences PREFERENCES = Preferences.userRoot().node("JEVis.JEConfig.Navigation");
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass COMPACT = PseudoClass.getPseudoClass("compact");
    private static final PseudoClass ATTENTION = PseudoClass.getPseudoClass("attention");

    /** group key → plugin class names (Plugin.getClassName(), which differs from PLUGIN_NAME for some modules); a null key
     * means: no group header */
    private static final List<Map.Entry<String, List<String>>> LAYOUT = Arrays.asList(
            new AbstractMap.SimpleEntry<>(null, Arrays.asList("Dashboard Plugin", "Graph Plugin", "Load Profile Plugin")),
            new AbstractMap.SimpleEntry<>("iso", Arrays.asList("Action Plugin", "Action Plan Plugin", "Deviation Plugin", "Nonconformities Plugin", "Index of Legal Provisions Plugin")),
            new AbstractMap.SimpleEntry<>("operation", Arrays.asList("Appointment Plugin", "Maintenance Plugin", "Alarm Plugin", "Notes Plugin")),
            new AbstractMap.SimpleEntry<>("assets", Arrays.asList("Meter Plugin", "Base Data Plugin", "Equipment Plugin", "Energy Certificate Plugin")),
            new AbstractMap.SimpleEntry<>("finance", Arrays.asList("Accounting Plugin", "Template Result Calculation Plugin")),
            new AbstractMap.SimpleEntry<>(null, Arrays.asList("Report Plugin", "Configuration Plugin"))
    );

    private final Map<Plugin, Button> buttons = new LinkedHashMap<>();
    private final List<Label> groupHeaders = new ArrayList<>();
    private final Button compactToggle = new Button();
    private boolean compact = PREFERENCES.getBoolean("compact", false);

    public PluginSidebar(List<Plugin> plugins, Consumer<Plugin> onSelect) {
        getStyleClass().add("plugin-sidebar");
        VBox items = new VBox();
        items.getStyleClass().add("plugin-sidebar-items");

        Set<Plugin> placed = new HashSet<>();
        for (Map.Entry<String, List<String>> group : LAYOUT) {
            List<Plugin> members = new ArrayList<>();
            for (String className : group.getValue()) {
                for (Plugin plugin : plugins) {
                    if (className.equals(safeClassName(plugin)) && placed.add(plugin)) members.add(plugin);
                }
            }
            addGroup(items, group.getKey(), members, onSelect);
        }
        List<Plugin> rest = new ArrayList<>();
        for (Plugin plugin : plugins) if (!placed.contains(plugin)) rest.add(plugin);
        addGroup(items, "more", rest, onSelect);

        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("plugin-sidebar-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        compactToggle.getStyleClass().add("plugin-sidebar-toggle");
        compactToggle.setMaxWidth(Double.MAX_VALUE);
        compactToggle.setOnAction(event -> setCompact(!compact));

        getChildren().addAll(scroll, compactToggle);
        setCompact(compact);
    }

    private static String safeClassName(Plugin plugin) {
        try {
            return plugin.getClassName();
        } catch (Exception e) {
            return "";
        }
    }

    private void addGroup(VBox items, String key, List<Plugin> members, Consumer<Plugin> onSelect) {
        if (members.isEmpty()) return;
        VBox body = new VBox();
        if (key != null) {
            Label header = new Label(groupName(key));
            header.getStyleClass().add("plugin-sidebar-group");
            header.setMaxWidth(Double.MAX_VALUE);
            boolean collapsed = PREFERENCES.getBoolean("collapsed." + key, false);
            body.setVisible(!collapsed);
            body.setManaged(!collapsed);
            header.setText((collapsed ? "▸ " : "▾ ") + groupName(key));
            header.setOnMouseClicked(event -> {
                boolean nowCollapsed = body.isVisible();
                body.setVisible(!nowCollapsed);
                body.setManaged(!nowCollapsed);
                header.setText((nowCollapsed ? "▸ " : "▾ ") + groupName(key));
                PREFERENCES.putBoolean("collapsed." + key, nowCollapsed);
            });
            groupHeaders.add(header);
            items.getChildren().add(header);
        }
        for (Plugin plugin : members) {
            Button button = new Button(plugin.getName());
            Region icon = plugin.getIcon();
            if (icon != null) button.setGraphic(icon);
            button.getStyleClass().add("plugin-sidebar-item");
            button.setAlignment(Pos.CENTER_LEFT);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setFocusTraversable(false);
            Tooltip tooltip = new Tooltip(plugin.getName());
            button.setTooltip(tooltip);
            button.setOnAction(event -> onSelect.accept(plugin));
            buttons.put(plugin, button);
            body.getChildren().add(button);
        }
        items.getChildren().add(body);
    }

    private static String groupName(String key) {
        switch (key) {
            case "iso":
                return text("navigation.group.iso", "ISO 50001", "ISO 50001");
            case "operation":
                return text("navigation.group.operation", "Betrieb", "Operation");
            case "assets":
                return text("navigation.group.assets", "Anlagen", "Assets");
            case "finance":
                return text("navigation.group.finance", "Finanzen", "Finance");
            default:
                return text("navigation.group.more", "Weitere", "More");
        }
    }

    /** text from the language files, with a built-in German/English fallback while the key is missing there */
    public static String text(String key, String german, String english) {
        String value = I18n.getInstance().getString(key);
        if (value != null && !value.startsWith("*") && !value.equals(key)) return value;
        return "de".equals(I18n.getInstance().getLocale().getLanguage()) ? german : english;
    }

    /** icon-only mode: names as tooltips, group headers hidden */
    public void setCompact(boolean compact) {
        this.compact = compact;
        PREFERENCES.putBoolean("compact", compact);
        pseudoClassStateChanged(COMPACT, compact);
        buttons.values().forEach(button -> button.setContentDisplay(compact ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT));
        groupHeaders.forEach(header -> {
            header.setVisible(!compact);
            header.setManaged(!compact);
        });
        compactToggle.setText(compact ? "»" : "« " + text("navigation.collapse", "Einklappen", "Collapse"));
        setPrefWidth(compact ? 48 : 210);
        setMinWidth(compact ? 48 : 210);
        setMaxWidth(compact ? 48 : 210);
    }

    public void select(Plugin plugin) {
        buttons.forEach((p, button) -> button.pseudoClassStateChanged(SELECTED, p == plugin));
    }

    /** marks a module that needs attention (e.g. open alarms) */
    public void setAttention(Plugin plugin, boolean attention) {
        Button button = buttons.get(plugin);
        if (button != null) button.pseudoClassStateChanged(ATTENTION, attention);
    }
}
