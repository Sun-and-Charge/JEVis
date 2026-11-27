/**
 * Copyright (C) 2018 Envidatec GmbH <info@envidatec.com>
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
package org.jevis.jeconfig.plugin.object.attribute;

import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.constants.AlarmConstants;
import org.jevis.commons.dataprocessing.CleanDataObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.json.JsonAlarmConfig;
import org.jevis.commons.json.JsonScheduler;
import org.jevis.commons.json.JsonTools;
import org.jevis.commons.object.plugin.TargetHelper;
import org.jevis.commons.unit.UnitManager;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.application.jevistree.filter.JEVisTreeFilter;
import org.jevis.jeconfig.dialog.SelectTargetDialog;
import org.joda.time.DateTime;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Editor to configure JsonGapFillingConfig elements
 */
public class AlarmEditor implements AttributeEditor {
    private static final Logger logger = LogManager.getLogger(AlarmEditor.class);
    private final BooleanProperty _changed = new SimpleBooleanProperty(false);
    private final BooleanProperty _readOnly = new SimpleBooleanProperty(false);

    private final ObservableList<AlarmConstants.Operator> operator = FXCollections.observableArrayList(AlarmConstants.Operator.SMALLER, AlarmConstants.Operator.BIGGER, AlarmConstants.Operator.EQUALS,
            AlarmConstants.Operator.SMALLER_EQUALS, AlarmConstants.Operator.BIGGER_EQUALS, AlarmConstants.Operator.NOT_EQUALS);
    private String unitString = "";
    public JEVisAttribute _attribute;
    private final HBox box = new HBox(12);
    private JEVisSample _newSample;
    private JEVisSample _lastSample;
    private List<JsonAlarmConfig> _listConfig;
    private final boolean delete = false;
    private boolean initialized = false;

    public AlarmEditor(JEVisAttribute att) {

        logger.debug("==init== for: {}", att.getName());
        _attribute = att;
        _lastSample = _attribute.getLatestSample();
        try {
            JEVisAttribute valueAttribute = _attribute.getObject().getAttribute(CleanDataObject.VALUE_ATTRIBUTE_NAME);
            if (valueAttribute != null) {
                unitString = UnitManager.getInstance().format(valueAttribute.getDisplayUnit());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Build main UI
     */
    private void init() {
        Button openConfig = new Button(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.openconfig"));
        openConfig.setOnAction(action -> {
            try {
                show();
            } catch (JEVisException e) {
                e.printStackTrace();
            }
        });

        box.getChildren().setAll(openConfig);

        initialized = true;
    }

    @Override
    public boolean hasChanged() {
//        _changed.setValue(true);

        return _changed.getValue();
    }

    @Override
    public void update() {
        Platform.runLater(() -> {
            box.getChildren().clear();
            init();
        });
    }

    @Override
    public void commit() throws JEVisException {
        logger.debug("Alarm Editor.commit(): '{}' {} {}", _attribute.getName(), hasChanged(), _newSample);

        if (hasChanged() && delete) {
            _attribute.deleteAllSample();
        } else if (hasChanged() && _newSample != null) {
            _newSample.commit();
            _lastSample = _newSample;
            _newSample = null;
            _changed.setValue(false);
        }
    }

    @Override
    public Node getEditor() {
        try {
            if (!initialized) {
                init();
            }
        } catch (Exception ex) {
            logger.catching(ex);
        }

        return box;
    }

    @Override
    public BooleanProperty getValueChangedProperty() {
        return _changed;
    }

    @Override
    public void setReadOnly(boolean canRead) {
        _readOnly.setValue(canRead);
    }

    @Override
    public JEVisAttribute getAttribute() {
        return _attribute;
    }

    /**
     * Creates an default configuration with one Alarm configuration
     *
     * @return
     */
    private List<JsonAlarmConfig> createDefaultConfig() {
        List<JsonAlarmConfig> list = new ArrayList<>();

        JsonAlarmConfig newConfig1 = new JsonAlarmConfig();

        newConfig1.setName(I18n.getInstance().getString("newobject.alarmconfig1"));
        newConfig1.setId("0");
        newConfig1.setOperator(AlarmConstants.Operator.SMALLER.toString());

        JsonAlarmConfig newConfig2 = new JsonAlarmConfig();
        newConfig2.setId("1");

        newConfig2.setName(I18n.getInstance().getString("newobject.alarmconfig2"));
        newConfig2.setOperator(AlarmConstants.Operator.SMALLER.toString());

        list.add(newConfig1);
        list.add(newConfig2);

        return list;
    }

    /**
     * Parse an JsonString with an JsonAlarmConfig configuration.
     *
     * @param jsonstring
     * @return
     */
    private List<JsonAlarmConfig> parseJson(String jsonstring) throws IOException {
        List<JsonAlarmConfig> list = new ArrayList<>();


        if (jsonstring.startsWith("[")) {
            list = new ArrayList<>(Arrays.asList(JsonTools.objectMapper().readValue(jsonstring, JsonAlarmConfig[].class)));
        } else {
            list.add(JsonTools.objectMapper().readValue(jsonstring, JsonAlarmConfig.class));
        }


        return list;
    }

    /**
     * Show the configuration dialog
     *
     * @throws JEVisException
     */
    private void show() throws JEVisException {
        if (_lastSample != null && !_lastSample.getValueAsString().isEmpty()) {
            try {
                _listConfig = parseJson(_lastSample.getValueAsString());
            } catch (IOException e) {
                logger.error("Could not parse Json: {}", _lastSample.getValueAsString(), e);
            }
        } else {
            _listConfig = createDefaultConfig();
        }

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        /* fits the dialog width (a minimum of 800 px overflowed the 640 px dialog); tall enough for validity time and mean */
        tabPane.setPrefHeight(600);

        org.jevis.jeconfig.application.ui.SucDialog<ButtonType> dialog = new org.jevis.jeconfig.application.ui.SucDialog<>(I18n.getInstance().getString("plugin.configuration.alarmeditor.title"), I18n.getInstance().getString("plugin.configuration.alarmeditor.header"), org.jevis.jeconfig.application.ui.SucDialog.Size.L);

        for (JsonAlarmConfig config : _listConfig) {
            Tab newTab = new Tab(config.getName());
            tabPane.getTabs().add(newTab);
            fillTab(newTab, config);

        }

        /* further rules of any kind once rules are stored (before only the two default tabs of an empty row existed) */
        Button addStatic = new Button(I18n.getInstance().getString("newobject.alarmconfig1"), JEConfig.getSVGImage(Icon.PLUS, 14, 14));
        addStatic.setOnAction(event -> addRule(tabPane, "STATIC", I18n.getInstance().getString("newobject.alarmconfig1")));
        Button addDynamic = new Button(I18n.getInstance().getString("newobject.alarmconfig2"), JEConfig.getSVGImage(Icon.PLUS, 14, 14));
        addDynamic.setOnAction(event -> addRule(tabPane, "DYNAMIC", I18n.getInstance().getString("newobject.alarmconfig2")));
        Button removeRule = new Button(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.remove", "Regel entfernen", "Remove rule"), JEConfig.getSVGImage(Icon.DELETE, 14, 14));
        removeRule.setOnAction(event -> {
            Tab selected = tabPane.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            _listConfig.remove(tabPane.getTabs().indexOf(selected));
            tabPane.getTabs().remove(selected);
        });
        removeRule.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(tabPane.getTabs()));

        /* data outage rule (docs/ALARMS-OUTAGE.md): no new value for the given time */
        Button addOutage = new Button(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.addoutage", "Datenausfall überwachen", "Monitor data outage"));
        addOutage.setOnAction(event -> {
            JsonAlarmConfig outage = new JsonAlarmConfig();
            outage.setName(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.outage", "Datenausfall", "Data outage"));
            outage.setId(nextId(_listConfig));
            outage.setNoData("120");
            _listConfig.add(outage);
            Tab tab = new Tab(outage.getName());
            tabPane.getTabs().add(tab);
            fillTab(tab, outage);
            tabPane.getSelectionModel().select(tab);
        });
        /* R16: the rules shown here to other rows (added to their rules, or instead of them) */
        Button copyRules = new Button(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy", "Auf andere Datenreihen übertragen …", "Copy to other data rows …"), JEConfig.getSVGImage(Icon.COPY, 14, 14));
        copyRules.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(tabPane.getTabs()));
        copyRules.setOnAction(event -> chooseCopyTargets());
        HBox ruleButtons = new HBox(6, addStatic, addDynamic, addOutage, removeRule, copyRules);

        /* J25.5: "Speichern" in Sun, no default button (Enter in a field must not save), "Abbrechen" */
        ButtonType okType = dialog.addFormPrimary(org.jevis.jeconfig.application.navigation.PluginSidebar.text("dialog.ui.save", "Speichern", "Save"));
        Button okButton = (Button) dialog.getDialogPane().lookupButton(okType);
        Button cancelButton = dialog.addCancel();

        Separator separator = new Separator(Orientation.HORIZONTAL);
        separator.setPadding(new Insets(8, 0, 8, 0));

        VBox vBox = new VBox(6, tabPane, ruleButtons, separator);
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        dialog.getDialogPane().setContent(vBox);

        okButton.setOnAction(event -> {
            try {
                _newSample = _attribute.buildSample(new DateTime(), _listConfig.toString());
                _changed.setValue(true);
                logger.debug("Commit: {}", _newSample.getValueAsString());
                commit();
            } catch (JEVisException e) {
                logger.error("Could not commit.");
            }
            dialog.close();
        });

        cancelButton.setOnAction(event -> dialog.close());

        dialog.show();
    }

    /**
     * Fills the given tab content with an gui for the given alarm configuration
     *
     * @param tab
     * @param config
     */
    // ---------------------------------------------------------------- copy to other rows (R16)

    private void chooseCopyTargets() {
        try {
            List<JEVisTreeFilter> filters = new ArrayList<>();
            JEVisTreeFilter rows = SelectTargetDialog.buildAllDataAndCleanDataFilter();
            filters.add(rows);
            SelectTargetDialog select = new SelectTargetDialog(filters, rows, null, SelectionMode.MULTIPLE, _attribute.getDataSource(), new ArrayList<>());
            select.setOnCloseRequest(event -> {
                if (select.getResponse() != SelectTargetDialog.Response.OK) return;
                List<JEVisObject> targets = new ArrayList<>();
                for (UserSelection us : select.getUserSelection()) {
                    if (us.getSelectedObject() != null) targets.add(us.getSelectedObject());
                }
                if (!targets.isEmpty()) Platform.runLater(() -> confirmCopy(targets));
            });
            select.show();
        } catch (Exception e) {
            logger.error("Could not choose the rows to copy the alarm rules to", e);
        }
    }

    private void confirmCopy(List<JEVisObject> targets) {
        org.jevis.jeconfig.application.ui.SucDialog<ButtonType> ask = new org.jevis.jeconfig.application.ui.SucDialog<>(
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.title", "Alarmregeln übertragen", "Copy alarm rules"),
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.description", "%d Regeln auf %d Datenreihen", "%d rules to %d data rows"), _listConfig.size(), targets.size()),
                org.jevis.jeconfig.application.ui.SucDialog.Size.M);
        CheckBox replace = new CheckBox(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.replace", "Vorhandene Regeln der Ziel-Datenreihen ersetzen", "Replace the existing rules of the target rows"));
        Label hint = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.hint",
                "Übertragen werden die hier angezeigten Regeln; ohne „ersetzen“ kommen sie zu den vorhandenen Regeln hinzu (höchstens 10 je Datenreihe). Auf den Ziel-Datenreihen wird „Alarm aktiv“ eingeschaltet, Grenzwerte werden nicht übertragen.",
                "The rules shown here are copied; without “replace” they are added to the existing rules (at most 10 per row). “Alarm enabled” is switched on at the target rows; limits are not copied."));
        hint.setWrapText(true);
        ask.setBody(new VBox(10, replace, hint));
        ButtonType go = new ButtonType(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.submit", "Übertragen", "Copy"), ButtonBar.ButtonData.OK_DONE);
        ask.addPrimary(go);
        ask.addCancel();
        java.util.Optional<ButtonType> answer = ask.open();
        if (!answer.isPresent() || answer.get() != go) return;

        List<String> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        copyTo(targets, replace.isSelected(), changed, skipped);
        org.jevis.jeconfig.application.ui.SucDialog<ButtonType> result = org.jevis.jeconfig.application.ui.SucDialog.result(
                org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.title", "Alarmregeln übertragen", "Copy alarm rules"),
                String.format(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.result", "%d geändert, %d nicht geändert", "%d changed, %d not changed"), changed.size(), skipped.size()),
                org.jevis.jeconfig.application.ui.SucDialog.Size.M);
        StringBuilder text = new StringBuilder();
        for (String c : changed) text.append("✓ ").append(c).append(System.lineSeparator());
        for (String c : skipped) text.append("– ").append(c).append(System.lineSeparator());
        Label list = new Label(text.toString().trim());
        list.setWrapText(true);
        result.setBody(list);
        result.open();
    }

    /** writes the rules of the dialog to each target row the user may write (as the web service's copy) */
    private void copyTo(List<JEVisObject> targets, boolean replace, List<String> changed, List<String> skipped) {
        long source = _attribute.getObject().getID();
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (JEVisObject target : targets) {
            String label = rowLabel(target);
            try {
                JEVisObject clean = cleanOf(target);
                JEVisAttribute config = clean != null ? clean.getAttribute(_attribute.getName()) : null;
                if (config == null) {
                    skipped.add(label + ": " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.noClean", "keine bereinigte Datenreihe", "no clean data row"));
                    continue;
                }
                if (!done.add(clean.getID())) continue;
                if (clean.getID() == source) {
                    skipped.add(label + ": " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.source", "die Datenreihe selbst", "the row itself"));
                    continue;
                }
                if (!clean.getDataSource().getCurrentUser().canWrite(clean.getID())) {
                    skipped.add(label + ": " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.noWrite", "kein Schreibrecht", "no write right"));
                    continue;
                }
                List<JsonAlarmConfig> result = new ArrayList<>();
                if (!replace) {
                    JEVisSample latest = config.getLatestSample();
                    String json = latest != null ? latest.getValueAsString() : null;
                    if (json != null && !json.trim().isEmpty()) {
                        try {
                            result.addAll(parseJson(json.trim()));
                        } catch (Exception e) {
                            skipped.add(label + ": " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.unreadable", "vorhandene Regeln nicht lesbar, mit „ersetzen“ möglich", "existing rules not readable, possible with “replace”"));
                            continue;
                        }
                    }
                }
                if (result.size() + _listConfig.size() > 10) {
                    skipped.add(label + ": " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.copy.tooMany", "mehr als 10 Regeln", "more than 10 rules"));
                    continue;
                }
                java.util.Set<String> used = new java.util.HashSet<>();
                for (JsonAlarmConfig rule : result) if (rule.getId() != null) used.add(rule.getId());
                for (JsonAlarmConfig rule : _listConfig) {
                    JsonAlarmConfig copy = JsonTools.objectMapper().readValue(JsonTools.objectMapper().writeValueAsString(rule), JsonAlarmConfig.class);
                    if (copy.getId() == null || used.contains(copy.getId())) copy.setId(nextFree(used));
                    used.add(copy.getId());
                    result.add(copy);
                }
                DateTime now = new DateTime();
                config.buildSample(now, result.toString()).commit();
                JEVisAttribute enabled = clean.getAttribute("Alarm Enabled");
                JEVisSample on = enabled != null ? enabled.getLatestSample() : null;
                if (enabled != null && (on == null || !Boolean.TRUE.equals(on.getValueAsBoolean()))) enabled.buildSample(now, true).commit();
                changed.add(label);
            } catch (Exception e) {
                logger.error("Could not copy the alarm rules to {}", target.getID(), e);
                skipped.add(label + ": " + e.getMessage());
            }
        }
    }

    private static String nextFree(java.util.Set<String> used) {
        int next = 0;
        while (used.contains(String.valueOf(next))) next++;
        return String.valueOf(next);
    }

    /** the clean data row itself, or the first clean data child of a raw data row */
    private static JEVisObject cleanOf(JEVisObject object) throws JEVisException {
        if ("Clean Data".equals(object.getJEVisClassName())) return object;
        if ("Data".equals(object.getJEVisClassName())) {
            for (JEVisObject child : object.getChildren()) {
                if ("Clean Data".equals(child.getJEVisClassName())) return child;
            }
        }
        return null;
    }

    private static String rowLabel(JEVisObject object) {
        try {
            if ("Clean Data".equals(object.getJEVisClassName()) && !object.getParents().isEmpty()) {
                return object.getParents().get(0).getName() + " / " + object.getName();
            }
        } catch (Exception e) {
            /* name only */
        }
        return object.getName();
    }

    /** kinds of rules added in this dialog that have no limit yet (kindOf would read an empty rule by its id) */
    private final java.util.Map<JsonAlarmConfig, String> newKinds = new java.util.IdentityHashMap<>();

    private void addRule(TabPane tabPane, String kind, String name) {
        JsonAlarmConfig rule = new JsonAlarmConfig();
        rule.setName(name);
        rule.setId(nextId(_listConfig));
        rule.setOperator(AlarmConstants.Operator.BIGGER.toString());
        rule.setTolerance("0");
        newKinds.put(rule, kind);
        _listConfig.add(rule);
        Tab tab = new Tab(name);
        tabPane.getTabs().add(tab);
        fillTab(tab, rule);
        tabPane.getSelectionModel().select(tab);
    }

    /** scrolls vertically only; the rule fields take the width of the dialog */
    private static ScrollPane fitted(GridPane gridPane) {
        /* labels keep their width, the fields take the rest */
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(Region.USE_PREF_SIZE);
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        ColumnConstraints units = new ColumnConstraints();
        units.setMinWidth(Region.USE_PREF_SIZE);
        gridPane.getColumnConstraints().setAll(labels, fields, units);
        ScrollPane scroll = new ScrollPane(gridPane);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return scroll;
    }

    /** a free numeric id for a new rule */
    static String nextId(List<JsonAlarmConfig> list) {
        int max = 1;
        for (JsonAlarmConfig c : list) {
            try {
                max = Math.max(max, Integer.parseInt(c.getId()));
            } catch (Exception e) {
                // not a number
            }
        }
        return String.valueOf(max + 1);
    }

    /** kind of a rule as JEAlarm reads it: outage, comparison row, fixed limit; without either by id (1 = row) */
    static String kindOf(JsonAlarmConfig config) {
        if (config.getNoData() != null && !config.getNoData().trim().isEmpty()) return "NO_DATA";
        if (config.getLimitData() != null && !config.getLimitData().trim().isEmpty()) return "DYNAMIC";
        if (config.getLimit() != null && !config.getLimit().trim().isEmpty()) return "STATIC";
        return "1".equals(config.getId()) ? "DYNAMIC" : "STATIC";
    }

    private void fillTab(Tab tab, JsonAlarmConfig config) {
        String kind = newKinds.getOrDefault(config, kindOf(config));

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setHgap(10);
        gridPane.setVgap(5);
        gridPane.setMinHeight(220);//we have hidden element and need space


        Label limitDataLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.limitData"));
        Label limitLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.limit"));
        Label operatorLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.operator"));
        Label silentTimeLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.silentTime"));
        Label standbyLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.standbyTime"));
        Label toleranceLabel = new Label(I18n.getInstance().getString("plugin.object.attribute.alarmeditor.label.tolerance"));

        TextField nameField = new TextField();

        HBox limitDataBox = new HBox();
        Button treeButton = new Button(I18n
                .getInstance().getString("plugin.object.attribute.target.button"),
                JEConfig.getSVGImage(Icon.FOLDER_OPEN, 18, 18));

        Button gotoButton = new Button(I18n.getInstance().getString("plugin.object.attribute.target.goto"),
                JEConfig.getSVGImage(Icon.GO_TO_SOURCE, 18, 18));//icon
        gotoButton.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.object.attribute.target.goto.tooltip")));

        Region rightSpacer = new Region();
        HBox.setHgrow(rightSpacer, Priority.ALWAYS);

        limitDataBox.setSpacing(10);
        limitDataBox.getChildren().setAll(treeButton, gotoButton, rightSpacer);

        if (config.getLimitData() != null) {
            try {

                TargetHelper th = new TargetHelper(_attribute.getDataSource(), config.getLimitData());

                if (th.isValid() && th.targetObjectAccessible()) {

                    StringBuilder bText = new StringBuilder();

                    for (JEVisObject obj : th.getObject()) {
                        int index = th.getObject().indexOf(obj);
                        if (index > 0) bText.append("; ");

                        bText.append("[");
                        bText.append(obj.getID());
                        bText.append("] ");
                        bText.append(obj.getName());

                        if (th.isAttribute()) {

                            bText.append(" - ");
                            bText.append(th.getAttribute().get(index).getName());

                        }
                    }

                    treeButton.setText(bText.toString());
                }

            } catch (Exception ex) {
                logger.catching(ex);
            }
        }

        treeButton.setOnAction(event -> {
            TargetHelper th = null;
            if (config.getLimitData() != null) {
                try {
                    th = new TargetHelper(_attribute.getDataSource(), config.getLimitData());
                } catch (JEVisException e) {
                    logger.error("Could not get target.");
                }
            }

            List<JEVisTreeFilter> allFilter = new ArrayList<>();
            JEVisTreeFilter allDataFilter = SelectTargetDialog.buildAllDataAndCleanDataFilter();
            JEVisTreeFilter allAttributesFilter = SelectTargetDialog.buildAllAttributesFilter();
            allFilter.add(allDataFilter);
            allFilter.add(allAttributesFilter);

            List<UserSelection> openList = new ArrayList<>();
            if (th != null && !th.getAttribute().isEmpty()) {
                for (JEVisAttribute att : th.getAttribute())
                    openList.add(new UserSelection(UserSelection.SelectionType.Attribute, att, null, null));
            } else if (th != null && !th.getObject().isEmpty()) {
                for (JEVisObject obj : th.getObject())
                    openList.add(new UserSelection(UserSelection.SelectionType.Object, obj));
            }

            try {
                SelectTargetDialog selectTargetDialog = new SelectTargetDialog(allFilter, allDataFilter, null, SelectionMode.SINGLE, _attribute.getDataSource(), openList);

                selectTargetDialog.setOnCloseRequest(event1 -> {
                    if (selectTargetDialog.getResponse() == SelectTargetDialog.Response.OK) {
                        logger.trace("Selection Done");

                        StringBuilder newTarget = new StringBuilder();
                        List<UserSelection> selections = selectTargetDialog.getUserSelection();
                        for (UserSelection us : selections) {
                            int index = selections.indexOf(us);
                            if (index > 0) newTarget.append(";");

                            newTarget.append(us.getSelectedObject().getID());
                            if (us.getSelectedAttribute() != null) {
                                newTarget.append(":");
                                newTarget.append(us.getSelectedAttribute().getName());
                            } else {
                                newTarget.append(":Value");
                            }
                        }

                        treeButton.setText(newTarget.toString());
                        config.setLimitData(newTarget.toString());
                    }

                });
                selectTargetDialog.show();
            } catch (Exception e) {
                logger.error(e);
            }
        });

        TextField limitField = new TextField();
        Label unitField = new Label(unitString);

        ComboBox<AlarmConstants.Operator> operator = new ComboBox<>(this.operator);
        Callback<ListView<AlarmConstants.Operator>, ListCell<AlarmConstants.Operator>> cellFactory = new Callback<ListView<AlarmConstants.Operator>, ListCell<AlarmConstants.Operator>>() {
            @Override
            public ListCell<AlarmConstants.Operator> call(ListView<AlarmConstants.Operator> param) {
                return new ListCell<AlarmConstants.Operator>() {
                    @Override
                    protected void updateItem(AlarmConstants.Operator operator1, boolean empty) {
                        super.updateItem(operator1, empty);
                        if (empty || operator1 == null) {
                            setText("");
                        } else {
                            setText(AlarmConstants.Operator.getValue(operator1));
                        }
                    }
                };
            }
        };

        operator.setCellFactory(cellFactory);
        operator.setButtonCell(cellFactory.call(null));

        TextField toleranceField = new TextField();
        Label toleranceUnitLabel = new Label("%");

        ScheduleEditor silentTime;

        JsonScheduler silentTimeValue;
        if (config.getSilentTime() != null) {
            silentTimeValue = config.getSilentTime();
            silentTime = new ScheduleEditor(silentTimeValue);
        } else {
            silentTime = new ScheduleEditor();
        }

        silentTime.getValueChangedProperty().addListener((observable, oldValue, newValue) -> {
            config.setSilentTime(silentTime.getInputValue());
        });

        ScheduleEditor standbyTime;

        JsonScheduler standbyTimeValue;
        if (config.getStandbyTime() != null) {
            standbyTimeValue = config.getStandbyTime();
            standbyTime = new ScheduleEditor(standbyTimeValue);
        } else {
            standbyTime = new ScheduleEditor();
        }

        standbyTime.getValueChangedProperty().addListener((observable, oldValue, newValue) -> {
            config.setStandbyTime(standbyTime.getInputValue());
        });

        double prefFieldWidth = 150;


        /**
         * Text layout
         */


        /**
         * Fill configuration values into gui elements
         */
        if (config.getLimit() != null) {
            limitField.setText(config.getLimit());
        }

        if (config.getTolerance() != null) {
            toleranceField.setText(config.getTolerance());
        }

        if (config.getOperator() != null) {
            operator.getSelectionModel().select(AlarmConstants.Operator.parse(config.getOperator()));
        } else operator.getSelectionModel().selectFirst();


        /**
         * Change Listeners
         */
        limitField.textProperty().addListener((observable, oldValue, newValue) -> {
            config.setLimit(newValue);
        });
        toleranceField.textProperty().addListener((observable, oldValue, newValue) -> {
            config.setTolerance(newValue);
        });
        operator.valueProperty().addListener((observable, oldValue, newValue) -> {
            config.setOperator(newValue.toString());
        });


        /**
         * Create layout
         */
        int row = 0;
        if ("NO_DATA".equals(kind)) {
            /* data outage: minutes without a new raw value, no operator, tolerance, mean or validity time */
            Label noDataLabel = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.nodata", "Keine neuen Werte seit (Minuten)", "No new values for (minutes)"));
            TextField noDataField = new TextField(config.getNoData());
            noDataField.textProperty().addListener((o, a, b) -> config.setNoData(b == null || b.trim().isEmpty() ? null : b.trim()));
            gridPane.add(noDataLabel, 0, row);
            gridPane.add(noDataField, 1, row);
            row++;
            Label hint = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.nodata.hint",
                    "Geprüft wird der Eingang der Rohdaten bei jedem Lauf von JEAlarm. Eine E-Mail beim Beginn, das Ende steht in der Tageszusammenfassung.",
                    "The arrival of raw data is checked in every JEAlarm run. One e-mail at the start; the end is listed in the daily summary."));
            hint.setWrapText(true);
            hint.setMaxWidth(560);
            gridPane.add(hint, 0, row, 3, 1);
            row++;
            gridPane.add(silentTimeLabel, 0, row);
            gridPane.add(silentTime.getEditor(), 1, row);
            row++;
            gridPane.add(standbyLabel, 0, row);
            gridPane.add(standbyTime.getEditor(), 1, row);
            tab.setContent(fitted(gridPane));
            return;
        }
        /* the kind of the rule, the tab only shows its name */
        Label kindLabel = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.kind", "Art", "Kind"));
        Label kindValue = new Label("DYNAMIC".equals(kind)
                ? I18n.getInstance().getString("newobject.alarmconfig2") + " – " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.kind.dynamic", "Grenze aus einer Vergleichsreihe", "limit from a comparison row")
                : I18n.getInstance().getString("newobject.alarmconfig1") + " – " + org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.kind.static", "fester Grenzwert", "fixed limit"));
        kindValue.setStyle("-fx-font-weight: bold;");
        gridPane.add(kindLabel, 0, row);
        gridPane.add(kindValue, 1, row, 2, 1);
        row++;
        if ("DYNAMIC".equals(kind)) {
            gridPane.add(limitDataLabel, 0, row);
            gridPane.add(limitDataBox, 1, row);
        } else {
            gridPane.add(limitLabel, 0, row);
            gridPane.add(limitField, 1, row);
            gridPane.add(unitField, 2, row);
        }
        row++;
        gridPane.add(operatorLabel, 0, row);
        gridPane.add(operator, 1, row);
        row++;

        Separator separator1 = new Separator();
        separator1.setPadding(new Insets(4, 4, 4, 4));
        separator1.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(separator1, 0, row, 2, 1);
        row++;

        gridPane.add(silentTimeLabel, 0, row);
        gridPane.add(silentTime.getEditor(), 1, row);
        row++;

        Separator separator2 = new Separator();
        separator2.setPadding(new Insets(4, 4, 4, 4));
        separator2.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(separator2, 0, row, 2, 1);
        row++;

        gridPane.add(standbyLabel, 0, row);
        gridPane.add(standbyTime.getEditor(), 1, row);
        row++;

        Separator separator3 = new Separator();
        separator3.setPadding(new Insets(4, 4, 4, 4));
        separator3.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(separator3, 0, row, 2, 1);
        row++;

        gridPane.add(toleranceLabel, 0, row);
        gridPane.add(toleranceField, 1, row);
        gridPane.add(toleranceUnitLabel, 2, row);
        row++;

        /* moving mean and validity time (docs/ALARMS-OUTAGE.md); empty = not written (older clients reject the keys) */
        Label averageLabel = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.average", "Mittelwert über (Minuten)", "Mean over (minutes)"));
        TextField averageField = new TextField(config.getAverage() != null ? config.getAverage() : "");
        averageField.setPromptText(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.average.none", "Einzelwerte", "Single values"));
        averageField.textProperty().addListener((o, a, b) -> config.setAverage(b == null || b.trim().isEmpty() ? null : b.trim()));
        gridPane.add(averageLabel, 0, row);
        gridPane.add(averageField, 1, row);
        row++;
        Separator separator4 = new Separator();
        separator4.setPadding(new Insets(4, 4, 4, 4));
        gridPane.add(separator4, 0, row, 2, 1);
        row++;
        Label validLabel = new Label(org.jevis.jeconfig.application.navigation.PluginSidebar.text("plugin.alarmeditor.valid", "Gilt nur zu", "Applies only at"));
        ScheduleEditor validTime = config.getValidTime() != null ? new ScheduleEditor(config.getValidTime()) : new ScheduleEditor();
        validTime.getValueChangedProperty().addListener((o, a, b) -> {
            JsonScheduler value = validTime.getInputValue();
            config.setValidTime(value != null && value.getRules() != null && !value.getRules().isEmpty() ? value : null);
        });
        gridPane.add(validLabel, 0, row);
        gridPane.add(validTime.getEditor(), 1, row);

        tab.setContent(fitted(gridPane));
    }

    @Override
    public boolean isValid() {
        //TODO: implement validation
        return true;
    }


    public enum Response {NO, YES, CANCEL}
}
