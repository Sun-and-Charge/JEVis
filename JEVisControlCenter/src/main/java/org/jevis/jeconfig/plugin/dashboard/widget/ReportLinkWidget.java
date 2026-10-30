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
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.dashboard.DashboardControl;
import org.jevis.jeconfig.plugin.dashboard.config2.ReportLinkerNode;
import org.jevis.jeconfig.plugin.dashboard.config2.Size;
import org.jevis.jeconfig.plugin.dashboard.config2.WidgetConfigDialog;
import org.jevis.jeconfig.plugin.dashboard.config2.WidgetPojo;
import org.jevis.jeconfig.plugin.reports.ReportPlugin;
import org.jevis.jeconfig.tool.Layouts;
import org.joda.time.DateTime;
import org.joda.time.Interval;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Button that opens the reports module with the linked report selected.
 */
public class ReportLinkWidget extends Widget {

    public static String REPORT_LINKER_NODE_NAME = "reportLinker";
    public static String WIDGET_ID = "Report-Link";
    private static final Logger logger = LogManager.getLogger(ReportLinkWidget.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnchorPane anchorPane = new AnchorPane();
    private final JFXButton linkButton = new JFXButton("", JEConfig.getImage("Report.png", 20, 20));
    private ReportLinkerNode linkerNode = new ReportLinkerNode();
    private JEVisObject linkedReportObj;

    public ReportLinkWidget(DashboardControl control, WidgetPojo config) {
        super(control, config);
    }

    @Override
    public void debug() {

    }

    @Override
    public WidgetPojo createDefaultConfig() {
        WidgetPojo widgetPojo = new WidgetPojo();
        widgetPojo.setTitle(I18n.getInstance().getString("plugin.dashboard.reportlinkerwidget.newname"));
        widgetPojo.setType(typeID());
        widgetPojo.setBorderSize(new BorderWidths(0));
        widgetPojo.setSize(new Size(control.getActiveDashboard().yGridInterval * 1, control.getActiveDashboard().xGridInterval * 1));
        widgetPojo.setBackgroundColor(Color.web("#ffffff", 0.0));

        return widgetPojo;
    }

    @Override
    public void updateData(Interval interval) {
        Platform.runLater(() -> showProgressIndicator(false));
    }

    @Override
    public void updateLayout() {

    }

    @Override
    public void updateConfig() {
        Platform.runLater(() -> {
            try {
                this.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, CornerRadii.EMPTY, Insets.EMPTY)));
                this.anchorPane.setBackground(new Background(new BackgroundFill(this.config.getBackgroundColor(), CornerRadii.EMPTY, Insets.EMPTY)));
                this.setBorder(null);

                linkedReportObj = null;
                if (linkerNode.getReportObject() != null && linkerNode.getReportObject() > 0) {
                    linkedReportObj = getDataSource().getObject(linkerNode.getReportObject());
                }
                if (linkedReportObj != null) {
                    linkButton.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.dashboard.linker.open") + " " + linkedReportObj.getName()));
                } else {
                    linkButton.setTooltip(null);
                    logger.warn("Linked report {} not found", linkerNode.getReportObject());
                }

                this.layout();
            } catch (Exception ex) {
                logger.error("Failed to update report link widget config", ex);
            }
        });
    }

    private void openLink() {
        if (linkedReportObj != null) {
            JEConfig.openObjectInPlugin(ReportPlugin.PLUGIN_NAME, linkedReportObj);
        } else {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setContentText(I18n.getInstance().getString("plugin.dashboard.linker.error.message"));
            alert.showAndWait();
        }
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public List<DateTime> getMaxTimeStamps() {
        return new ArrayList<>();
    }

    @Override
    public void openConfig() {
        WidgetConfigDialog widgetConfigDialog = new WidgetConfigDialog(this);
        widgetConfigDialog.addGeneralTabsDataModel(null);

        Tab tab = new Tab("Link");
        widgetConfigDialog.addTab(tab);

        ListView<JEVisObject> reportListView = new ListView<>();
        try {
            ObjectRelations objectRelations = new ObjectRelations(getDataSource());
            JEVisClass reportClass = getDataSource().getJEVisClass(ReportPlugin.REPORT_CLASS);
            List<JEVisObject> allReports = new ArrayList<>(getDataSource().getObjects(reportClass, true));
            AlphanumComparator ac = new AlphanumComparator();
            allReports.sort((o1, o2) -> ac.compare(objectRelations.getObjectPath(o1) + o1.getName(), objectRelations.getObjectPath(o2) + o2.getName()));
            reportListView.setItems(FXCollections.observableArrayList(allReports));
            reportListView.setCellFactory(param -> new ListCell<JEVisObject>() {
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

            for (JEVisObject report : allReports) {
                if (report.getID().equals(linkerNode.getReportObject())) {
                    reportListView.getSelectionModel().select(report);
                    reportListView.scrollTo(report);
                    break;
                }
            }
        } catch (Exception ex) {
            logger.error("Failed to build report link widget config tab", ex);
        }
        tab.setContent(reportListView);

        Optional<ButtonType> result = widgetConfigDialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                JEVisObject selected = reportListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    linkerNode.setReportObject(selected.getID());
                }
                widgetConfigDialog.commitSettings();
                control.updateWidget(this);
            } catch (Exception ex) {
                logger.error(ex);
            }
        }
    }

    @Override
    public void init() {
        linkButton.setStyle("-fx-background-color: transparent;");
        linkButton.setOnAction(event -> openLink());
        anchorPane.getChildren().add(linkButton);
        Layouts.setAnchor(linkButton, 0);
        try {
            JsonNode node = this.config.getConfigNode(REPORT_LINKER_NODE_NAME);
            if (node != null) {
                linkerNode = this.mapper.treeToValue(node, ReportLinkerNode.class);
            }
        } catch (Exception ex) {
            logger.error("Failed to read report link widget config", ex);
        }
        Layouts.setAnchor(anchorPane, 5);
        setGraphic(this.anchorPane);
    }

    @Override
    public String typeID() {
        return WIDGET_ID;
    }

    @Override
    public ObjectNode toNode() {
        ObjectNode dashBoardNode = super.createDefaultNode();
        dashBoardNode.set(REPORT_LINKER_NODE_NAME, mapper.valueToTree(linkerNode));
        return dashBoardNode;
    }

    @Override
    public ImageView getImagePreview() {
        return JEConfig.getImage("widget/LinkWidget2.png", this.previewSize.getHeight(), this.previewSize.getWidth());
    }

}
