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
package org.jevis.jeconfig.plugin.dashboard.config2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.commons.utils.AlphanumComparator;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.Boxes.AnalysesComboBox;
import org.jevis.jeconfig.plugin.dashboard.DashBordPlugIn;
import org.jevis.jeconfig.plugin.dashboard.DashboardControl;
import org.jevis.jeconfig.plugin.dashboard.config.GraphAnalysisLinkerNode;
import org.jevis.jeconfig.tool.SvgImages;
import org.joda.time.DateTime;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ImageConfig {

    private static final Logger logger = LogManager.getLogger(ImageConfig.class);

    private final String JSON_IMAGE_ID = "objectID";
    private Long objectID = -1L;
    private final String JSON_DASHBOARD_LINK = "dashboardLinker";
    private final String JSON_ANALYSIS_LINK = "analysisLinker";
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Optional link target opened on click: a dashboard or an analysis (0 = no link)
     */
    private DashboardLinkerNode dashboardLink = new DashboardLinkerNode();
    private GraphAnalysisLinkerNode analysisLink = new GraphAnalysisLinkerNode();

    final DashboardControl dashboardControl;

    public ImageConfig(DashboardControl control) {
        this(control, null);
    }

    public Long getObjectID() {
        return objectID;
    }

    public ImageConfig(DashboardControl control, JsonNode jsonNode) {
        this.dashboardControl = control;

        if (jsonNode != null) {
            objectID = jsonNode.get(JSON_IMAGE_ID).asLong(-1L);
            try {
                if (jsonNode.has(JSON_DASHBOARD_LINK)) {
                    dashboardLink = mapper.treeToValue(jsonNode.get(JSON_DASHBOARD_LINK), DashboardLinkerNode.class);
                }
                if (jsonNode.has(JSON_ANALYSIS_LINK)) {
                    analysisLink = mapper.treeToValue(jsonNode.get(JSON_ANALYSIS_LINK), GraphAnalysisLinkerNode.class);
                }
            } catch (Exception ex) {
                logger.error("Failed to read link of image widget", ex);
            }
        }
    }


    private class ImageTab extends Tab implements ConfigTab {
        ImageConfig limit;
        ComboBox<LinkType> linkTypeBox;
        ComboBox<JEVisObject> linkTargetBox;

        public ImageTab(String text, ImageConfig limit) {
            super(text);
            this.limit = limit;
        }

        @Override
        public void commitChanges() {
            /* the image file is stored directly on upload, only the link is committed here */
            if (linkTypeBox == null || linkTargetBox == null) {
                return;
            }
            JEVisObject target = linkTargetBox.getValue();
            LinkType type = linkTypeBox.getValue();
            dashboardLink.setDashboardObject(type == LinkType.DASHBOARD && target != null ? target.getID() : 0L);
            analysisLink.setGraphAnalysisObject(type == LinkType.ANALYSIS && target != null ? target.getID() : 0L);
        }
    }

    public enum LinkType {
        NONE, DASHBOARD, ANALYSIS
    }

    /**
     * @return the configured link type; a dashboard link wins if both are set
     */
    public LinkType getLinkType() {
        if (dashboardLink.getDashboardObject() != null && dashboardLink.getDashboardObject() > 0) {
            return LinkType.DASHBOARD;
        }
        if (analysisLink.getGraphAnalysisObject() != null && analysisLink.getGraphAnalysisObject() > 0) {
            return LinkType.ANALYSIS;
        }
        return LinkType.NONE;
    }

    public DashboardLinkerNode getDashboardLink() {
        return dashboardLink;
    }

    public GraphAnalysisLinkerNode getAnalysisLink() {
        return analysisLink;
    }

    private List<JEVisObject> getLinkTargets(LinkType type, ObjectRelations objectRelations) {
        List<JEVisObject> objects = new ArrayList<>();
        if (type == null || type == LinkType.NONE) {
            return objects;
        }
        try {
            String className = type == LinkType.DASHBOARD ? DashBordPlugIn.CLASS_ANALYSIS : AnalysesComboBox.ANALYSIS_CLASS_NAME;
            JEVisClass jeVisClass = dashboardControl.getDataSource().getJEVisClass(className);
            objects.addAll(dashboardControl.getDataSource().getObjects(jeVisClass, true));
            AlphanumComparator ac = new AlphanumComparator();
            objects.sort((o1, o2) -> ac.compare(objectRelations.getObjectPath(o1) + o1.getName(), objectRelations.getObjectPath(o2) + o2.getName()));
        } catch (Exception ex) {
            logger.error("Failed to load link targets of image widget", ex);
        }
        return objects;
    }

    private void addLinkRows(ImageTab tab, GridPane gridPane, int row) {
        ObjectRelations objectRelations = new ObjectRelations(dashboardControl.getDataSource());

        ComboBox<LinkType> linkTypeBox = new ComboBox<>(FXCollections.observableArrayList(LinkType.values()));
        Callback<ListView<LinkType>, ListCell<LinkType>> typeCellFactory = param -> new ListCell<LinkType>() {
            @Override
            protected void updateItem(LinkType item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else if (item == LinkType.DASHBOARD) {
                    setText(I18n.getInstance().getString("plugin.dashboard.imagewidget.link.dashboard"));
                } else if (item == LinkType.ANALYSIS) {
                    setText(I18n.getInstance().getString("plugin.dashboard.imagewidget.link.analysis"));
                } else {
                    setText(I18n.getInstance().getString("plugin.dashboard.imagewidget.link.none"));
                }
            }
        };
        linkTypeBox.setCellFactory(typeCellFactory);
        linkTypeBox.setButtonCell(typeCellFactory.call(null));

        ComboBox<JEVisObject> linkTargetBox = new ComboBox<>();
        linkTargetBox.setPrefWidth(300);
        Callback<ListView<JEVisObject>, ListCell<JEVisObject>> targetCellFactory = param -> new ListCell<JEVisObject>() {
            @Override
            protected void updateItem(JEVisObject item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(objectRelations.getObjectPath(item) + item.getName());
                }
            }
        };
        linkTargetBox.setCellFactory(targetCellFactory);
        linkTargetBox.setButtonCell(targetCellFactory.call(null));

        linkTypeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            linkTargetBox.getItems().setAll(getLinkTargets(newValue, objectRelations));
            linkTargetBox.setDisable(newValue == null || newValue == LinkType.NONE);
        });

        LinkType currentType = getLinkType();
        linkTypeBox.setValue(currentType);
        linkTargetBox.setDisable(currentType == LinkType.NONE);
        Long currentTarget = currentType == LinkType.DASHBOARD ? dashboardLink.getDashboardObject() : analysisLink.getGraphAnalysisObject();
        for (JEVisObject object : linkTargetBox.getItems()) {
            if (object.getID().equals(currentTarget)) {
                linkTargetBox.setValue(object);
                break;
            }
        }

        tab.linkTypeBox = linkTypeBox;
        tab.linkTargetBox = linkTargetBox;

        gridPane.addRow(row, new Label(I18n.getInstance().getString("plugin.dashboard.imagewidget.link")), linkTypeBox);
        gridPane.addRow(row + 1, new Label(I18n.getInstance().getString("plugin.dashboard.imagewidget.link.target")), linkTargetBox);
    }

    public void setObjectID(Long objectID) {
        this.objectID = objectID;
    }

    private static Label errorLabel(Throwable t) {
        Label label = new Label(SvgImages.describeError(t));
        label.setWrapText(true);
        label.setMaxWidth(400);
        label.setStyle("-fx-text-fill: firebrick;");
        return label;
    }

    public Tab getConfigTab() {
        ImageTab tab = new ImageTab(I18n.getInstance().getString("plugin.dashboard.imagewidget.tab")
                , this);

        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(10));
        gridPane.setVgap(8);
        gridPane.setHgap(8);


        Label limitTypeLabel = new Label(I18n.getInstance().getString("plugin.dashboard.imagewidget.file"));
        Label fileSizeLabel = new Label(I18n.getInstance().getString("plugin.dashboard.imagewidget.filesize"));
        Label fileSizeField = new Label();

        Button fileButton = new Button();
        fileButton.setOnAction(event -> {
            FileChooser fileChooser = new FileChooser();
            FileChooser.ExtensionFilter pictures = new FileChooser.ExtensionFilter("Pictures", "*.png", "*.gif", "*.jpg", "*.jpeg", "*.bmp", "*.svg");
            fileChooser.getExtensionFilters().add(pictures);
            fileChooser.setSelectedExtensionFilter(pictures);
            /* own the chooser by the config dialog so it opens in front of it */
            File newBackground = fileChooser.showOpenDialog(fileButton.getScene() != null ? fileButton.getScene().getWindow() : JEConfig.getStage());
            if (newBackground != null) {
                try {
                    //BufferedImage bufferedImage = ImageIO.read(newBackground);
                    //javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                    JEVisClass fileClass = dashboardControl.getDataSource().getJEVisClass("File");
                    JEVisObject fileObject = getFileObject();

                    if (fileObject == null) {
                        /* no image yet, or the stored one was deleted: create a new file object */
                        fileObject = dashboardControl.getActiveDashboard().getDashboardObject().buildObject(FilenameUtils.removeExtension(newBackground.getName()), fileClass);
                        fileObject.commit();
                        objectID = fileObject.getID();
                    }


                    JEVisFile jeVisFile = new JEVisFileImp(newBackground.getName(), newBackground);
                    JEVisSample jeVisSample = fileObject.getAttribute("File").buildSample(DateTime.now(), jeVisFile);
                    jeVisSample.commit();

                    fileSizeField.setText(jeVisFile.getBytes().length + " bytes");

                    fileButton.setGraphic(createPreview(jeVisFile));

                } catch (Exception e) {
                    logger.error("Failed to upload image file {}", newBackground, e);
                    fileButton.setGraphic(errorLabel(e));
                }

            }

        });

        JEVisFile currentFile = getCurrentFile();
        if (currentFile != null) {
            fileButton.setGraphic(createPreview(currentFile));
            fileSizeField.setText(currentFile.getBytes().length + " bytes");
        } else {
            fileButton.setGraphic(new Label(I18n.getInstance().getString("plugin.dashboard.imagewidget.filenew")));
        }

        gridPane.addRow(0, fileSizeLabel, fileSizeField);
        gridPane.addRow(1, limitTypeLabel, fileButton);
        addLinkRows(tab, gridPane, 2);
        //gridPane.add(new Separator(Orientation.HORIZONTAL_TOP_LEFT), 0, 2, 2, 1);
        //gridPane.add(editorPane, 0, 3, 2, 1);


        tab.setContent(gridPane);
        return tab;
    }

    /**
     * @return the configured file object, or {@code null} if none is set or it does not exist anymore
     * (deleted, or not readable for the current user)
     */
    public JEVisObject getFileObject() {
        if (objectID == null || objectID < 1) {
            return null;
        }
        try {
            JEVisObject object = dashboardControl.getDataSource().getObject(objectID);
            if (object == null || object.getAttribute("File") == null) {
                logger.warn("Image object {} of the image widget does not exist", objectID);
                return null;
            }
            return object;
        } catch (Exception ex) {
            logger.warn("Could not load image object {}", objectID, ex);
            return null;
        }
    }

    /**
     * @return the latest image file, or {@code null} if there is none
     */
    public JEVisFile getCurrentFile() {
        JEVisObject object = getFileObject();
        if (object == null) {
            return null;
        }
        try {
            JEVisSample sample = object.getAttribute("File").getLatestSample();
            return sample != null ? sample.getValueAsFile() : null;
        } catch (Exception ex) {
            logger.warn("Could not load image file of object {}", objectID, ex);
            return null;
        }
    }

    /**
     * @return the preview of the image, or a label with the reason if the image can not be shown
     */
    private Node createPreview(JEVisFile file) {
        try {
            Image image;
            if (SvgImages.isSvg(file.getFilename(), file.getBytes())) {
                image = SvgImages.render(file.getBytes(), 200, 200);
            } else {
                InputStream in = new ByteArrayInputStream(file.getBytes());
                image = new Image(in);
                if (image.isError()) {
                    throw new IllegalArgumentException("Unsupported image format: " + file.getFilename(), image.getException());
                }
            }
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setFitHeight(200);
            return imageView;
        } catch (Throwable t) {
            logger.error("Failed to show image {}", file.getFilename(), t);
            return errorLabel(t);
        }
    }

    public ObjectNode toJSON() {
        ObjectNode dataNode = JsonNodeFactory.instance.objectNode();
        dataNode.put(JSON_IMAGE_ID, objectID.toString());
        if (getLinkType() == LinkType.DASHBOARD) {
            dataNode.set(JSON_DASHBOARD_LINK, mapper.valueToTree(dashboardLink));
        } else if (getLinkType() == LinkType.ANALYSIS) {
            dataNode.set(JSON_ANALYSIS_LINK, mapper.valueToTree(analysisLink));
        }

        return dataNode;
    }


}