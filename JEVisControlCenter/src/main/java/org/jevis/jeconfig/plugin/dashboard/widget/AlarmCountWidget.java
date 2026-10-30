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
package org.jevis.jeconfig.plugin.dashboard.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jfoenix.controls.JFXCheckBox;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.alarm.AlarmConfiguration;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.json.JsonAlarm;
import org.jevis.commons.json.JsonTools;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.dashboard.DashboardControl;
import org.jevis.jeconfig.plugin.dashboard.config2.AlarmCountNode;
import org.jevis.jeconfig.plugin.dashboard.config2.Size;
import org.jevis.jeconfig.plugin.dashboard.config2.WidgetConfigDialog;
import org.jevis.jeconfig.plugin.dashboard.config2.WidgetPojo;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.*;

/**
 * Shows the number of alarms in the dashboard interval, for all alarm configurations the user can read or only for
 * one alarm configuration / alarm directory. The alarms are read from the alarm log like in the alarm module.
 */
public class AlarmCountWidget extends Widget {

    public static String ALARM_NODE_NAME = "alarmCount";
    public static String WIDGET_ID = "Alarm Count";
    private static final String ALARM_DIRECTORY_CLASS = "Alarm Directory";
    private static final Logger logger = LogManager.getLogger(AlarmCountWidget.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Label label = new Label();
    private AlarmCountNode alarmNode = new AlarmCountNode();

    public AlarmCountWidget(DashboardControl control, WidgetPojo config) {
        super(control, config);
        setId(WIDGET_ID);
    }

    @Override
    public void debug() {

    }

    @Override
    public WidgetPojo createDefaultConfig() {
        WidgetPojo widgetPojo = new WidgetPojo();
        widgetPojo.setTitle(I18n.getInstance().getString("plugin.dashboard.alarmwidget.newname"));
        widgetPojo.setType(typeID());
        widgetPojo.setSize(new Size(control.getActiveDashboard().yGridInterval * 1, control.getActiveDashboard().xGridInterval * 4));
        return widgetPojo;
    }

    @Override
    public void updateData(Interval interval) {
        Platform.runLater(() -> {
            showAlertOverview(false, "");
            showProgressIndicator(true);
            this.label.setText(I18n.getInstance().getString("plugin.dashboard.loading"));
        });

        try {
            int count = countAlarms(interval);
            Platform.runLater(() -> this.label.setText(String.valueOf(count)));
        } catch (Exception ex) {
            logger.error("Failed to count alarms of widget {}", getConfig().getUuid(), ex);
            Platform.runLater(() -> {
                this.label.setText("error");
                showAlertOverview(true, ex.getMessage());
            });
        }

        Platform.runLater(() -> showProgressIndicator(false));
    }

    /**
     * Counts the logged alarms of all enabled alarm configurations (or the selected one / the ones below the selected
     * directory) in the interval.
     */
    private int countAlarms(Interval interval) throws Exception {
        if (interval == null) {
            return 0;
        }
        JEVisDataSource ds = getDataSource();
        Long filterID = alarmNode.getAlarmObject() != null && alarmNode.getAlarmObject() > 0 ? alarmNode.getAlarmObject() : null;

        JEVisClass alarmConfigClass = ds.getJEVisClass(AlarmConfiguration.CLASS_NAME);
        int count = 0;
        for (JEVisObject object : ds.getObjects(alarmConfigClass, true)) {
            if (filterID != null && !isSelfOrBelow(object, filterID, new HashSet<>())) {
                continue;
            }
            AlarmConfiguration alarmConfiguration = new AlarmConfiguration(ds, object);
            if (!Boolean.TRUE.equals(alarmConfiguration.isEnabled())) {
                continue;
            }
            JEVisAttribute fileLog = alarmConfiguration.getFileLogAttribute();
            if (fileLog == null || !fileLog.hasSample()) {
                continue;
            }
            for (JEVisSample sample : fileLog.getSamples(interval.getStart(), interval.getEnd())) {
                try {
                    JsonAlarm[] alarms = JsonTools.objectMapper().readValue(sample.getValueAsFile().getBytes(), JsonAlarm[].class);
                    count += alarms.length;
                } catch (Exception ex) {
                    logger.warn("Could not read alarm log of {}:{}", object.getName(), object.getID(), ex);
                }
            }
        }
        return count;
    }

    private static boolean isSelfOrBelow(JEVisObject object, Long parentID, Set<Long> visited) throws JEVisException {
        if (object == null || !visited.add(object.getID())) {
            return false;
        }
        if (object.getID().equals(parentID)) {
            return true;
        }
        for (JEVisObject parent : object.getParents()) {
            if (isSelfOrBelow(parent, parentID, visited)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateLayout() {

    }

    @Override
    public void updateConfig() {
        Platform.runLater(() -> {
            Background bgColor = new Background(new BackgroundFill(this.config.getBackgroundColor(), CornerRadii.EMPTY, Insets.EMPTY));
            this.label.setBackground(bgColor);
            this.label.setTextFill(this.config.getFontColor());
            this.label.setContentDisplay(ContentDisplay.CENTER);
            this.label.setAlignment(this.config.getTitlePosition());
            Font font = Font.font(this.label.getFont().getFamily(), this.getConfig().getFontWeight(), this.getConfig().getFontPosture(), this.config.getFontSize());
            this.label.setFont(font);
            this.label.setUnderline(this.getConfig().getFontUnderlined());
        });
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public List<DateTime> getMaxTimeStamps() {
        return new ArrayList<>();
    }

    @Override
    public void openConfig() {
        WidgetConfigDialog widgetConfigDialog = new WidgetConfigDialog(this);
        widgetConfigDialog.addGeneralTabsDataModel(null);

        Tab tab = new Tab(I18n.getInstance().getString("plugin.dashboard.alarmwidget.tab"));
        widgetConfigDialog.addTab(tab);

        JFXCheckBox filterBox = new JFXCheckBox(I18n.getInstance().getString("plugin.dashboard.alarmwidget.filter"));
        ListView<JEVisObject> listView = new ListView<>();
        try {
            ObjectRelations objectRelations = new ObjectRelations(getDataSource());
            List<JEVisObject> objects = new ArrayList<>();
            try {
                objects.addAll(getDataSource().getObjects(getDataSource().getJEVisClass(ALARM_DIRECTORY_CLASS), true));
            } catch (Exception ex) {
                logger.warn("Could not load alarm directories", ex);
            }
            objects.addAll(getDataSource().getObjects(getDataSource().getJEVisClass(AlarmConfiguration.CLASS_NAME), true));
            AlphanumComparator ac = new AlphanumComparator();
            objects.sort((o1, o2) -> ac.compare(objectRelations.getObjectPath(o1) + o1.getName(), objectRelations.getObjectPath(o2) + o2.getName()));

            listView.setItems(FXCollections.observableArrayList(objects));
            listView.setCellFactory(param -> new ListCell<JEVisObject>() {
                @Override
                protected void updateItem(JEVisObject obj, boolean empty) {
                    super.updateItem(obj, empty);
                    if (empty || obj == null || obj.getName() == null) {
                        setText("");
                    } else {
                        setText(objectRelations.getObjectPath(obj) + obj.getName());
                    }
                }
            });

            for (JEVisObject object : objects) {
                if (object.getID().equals(alarmNode.getAlarmObject())) {
                    listView.getSelectionModel().select(object);
                    listView.scrollTo(object);
                    break;
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to build alarm widget config tab", ex);
        }

        filterBox.setSelected(alarmNode.getAlarmObject() != null && alarmNode.getAlarmObject() > 0);
        listView.disableProperty().bind(filterBox.selectedProperty().not());

        BorderPane content = new BorderPane(listView);
        content.setTop(filterBox);
        BorderPane.setMargin(filterBox, new Insets(8));
        tab.setContent(content);

        Optional<ButtonType> result = widgetConfigDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                JEVisObject selected = listView.getSelectionModel().getSelectedItem();
                alarmNode.setAlarmObject(filterBox.isSelected() && selected != null ? selected.getID() : 0L);
                widgetConfigDialog.commitSettings();
                control.updateWidget(this);
            } catch (Exception ex) {
                logger.error(ex);
            }
        }
    }

    @Override
    public void init() {
        try {
            JsonNode node = this.config.getConfigNode(ALARM_NODE_NAME);
            if (node != null) {
                alarmNode = this.mapper.treeToValue(node, AlarmCountNode.class);
            }
        } catch (Exception ex) {
            logger.error("Failed to read alarm widget config", ex);
        }
        this.label.setPadding(new Insets(0, 8, 0, 8));
        setGraphic(this.label);
    }

    @Override
    public String typeID() {
        return WIDGET_ID;
    }

    @Override
    public ObjectNode toNode() {
        ObjectNode dashBoardNode = super.createDefaultNode();
        dashBoardNode.set(ALARM_NODE_NAME, mapper.valueToTree(alarmNode));
        return dashBoardNode;
    }

    @Override
    public ImageView getImagePreview() {
        return JEConfig.getImage("widget/ValueWidget.png", this.previewSize.getHeight(), this.previewSize.getWidth());
    }
}
