/**
 * Copyright (C) 2009 - 2014 Envidatec GmbH <info@envidatec.com>
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
package org.jevis.jeconfig.plugin.classes.editor;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisType;
import org.jevis.commons.constants.DisplayType;
import org.jevis.commons.constants.GUIConstants;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.Constants;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.dialog.ExceptionDialog;
import org.jevis.jeconfig.plugin.classes.ClassHelper;
import org.jevis.jeconfig.plugin.classes.ClassTree;
import org.jevis.jeconfig.plugin.classes.relationship.ValidParentEditor;
import org.jevis.jeconfig.tool.ImageConverter;
import tech.units.indriya.AbstractUnit;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * @author Florian Simon <florian.simon@envidatec.com>
 */
public class ClassEditor {

    private static final Logger logger = LogManager.getLogger(ClassEditor.class);
    //    private final UnitChooser pop = new UnitChooser();
    private final VBox _view;
    JFXButton fIcon;
    JFXTextField fName = new JFXTextField();
    JFXTextArea fDescription = new JFXTextArea();
    //    private Desktop desktop = Desktop.getDesktop();
    private JEVisClass _class;
    JFXCheckBox fUnique = new JFXCheckBox();
    private TitledPane t2;
    private List<JEVisType> _toDelete;
    private JFXTextField fInherit;
    private ClassTree _tree = null;

    public ClassEditor() {
        _view = new VBox();
//        _view.setStyle("-fx-background-color: #E2E2E2");
        _view.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);
    }

    public void checkIfSaved(JEVisClass obj) {

    }

    public void setTreeView(ClassTree tree) {
        _tree = tree;
    }

    public void setJEVisClass(final JEVisClass jclass) {
        Platform.runLater(new Runnable() {

            @Override
            public void run() {
                _class = jclass;
                _toDelete = new ArrayList<>();

                final Accordion accordion = new Accordion();
//                accordion.setStyle("-fx-background-color: #E2E2E2");
                accordion.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);

                GridPane gridPane = new GridPane();
                gridPane.setPadding(new Insets(5, 0, 20, 20));
                gridPane.setHgap(7);
                gridPane.setVgap(7);

                Label lName = new Label(I18n.getInstance().getString("plugin.classes.editor.name"));
                Label lDescription = new Label(I18n.getInstance().getString("plugin.classes.editor.description"));
                Label lIsUnique = new Label(I18n.getInstance().getString("plugin.classes.editor.unique"));
                Label lIcon = new Label(I18n.getInstance().getString("plugin.classes.editor.icon"));
                Label lRel = new Label(I18n.getInstance().getString("plugin.classes.editor.relationship"));
//                Label lInherit = new Label("Inheritance:");
                Label lTypes = new Label(I18n.getInstance().getString("plugin.classes.editor.types"));

                fName.prefWidthProperty().set(250d);

                fIcon = new JFXButton("", getIcon(jclass));

                fUnique.setSelected(false);

                int x = 0;

                gridPane.add(lName, 0, x);
                gridPane.add(fName, 1, x);
//                gridPane.add(lInherit, 0, 1);
//                gridPane.add(fInherit, 1, 1);
                gridPane.add(lIcon, 0, ++x);
                gridPane.add(fIcon, 1, x);
                gridPane.add(lIsUnique, 0, ++x);
                gridPane.add(fUnique, 1, x);
                gridPane.add(lDescription, 0, ++x);
                gridPane.add(fDescription, 1, x, 1, 2);

//                GridPane.setHalignment(lInherit, HPos.LEFT);
                GridPane.setHalignment(lIcon, HPos.LEFT);
                GridPane.setHalignment(lName, HPos.LEFT);
                GridPane.setHalignment(lIsUnique, HPos.LEFT);
                GridPane.setHalignment(lDescription, HPos.LEFT);
                GridPane.setValignment(lDescription, VPos.TOP);
                GridPane.setHalignment(lRel, HPos.LEFT);
                GridPane.setValignment(lRel, VPos.TOP);
//        GridPane.setHgrow(tTable, Priority.ALWAYS);
                GridPane.setHalignment(lTypes, HPos.LEFT);
                GridPane.setValignment(lTypes, VPos.TOP);

                try {
                    if (jclass != null) {
                        fName.setText(jclass.getName());
                        if (jclass.getInheritance() != null) {
                            HBox inBox = new HBox();
                            Label inLabel = new Label(jclass.getInheritance().getName());
                            inLabel.setMaxHeight(8);
                            ImageView inIcon = getIcon(jclass.getInheritance());
                            inIcon.fitHeightProperty().bind(inLabel.heightProperty());
                            inBox.getChildren().setAll(inIcon, inLabel);
//                            fInherit.setText(jclass.getInheritance().getName());
                        } else {
//                            fInherit.setText("Choose...");
                        }

                        fDescription.setWrapText(true);
                        fDescription.setText(jclass.getDescription());
                        fUnique.setSelected(jclass.isUnique());
                    }

                } catch (JEVisException ex) {
                    ExceptionDialog dia = new ExceptionDialog();
                    dia.show(JEConfig.getStage(), I18n.getInstance().getString("dialog.error.title"),
                            I18n.getInstance().getString("dialog.error.servercommit"), ex, null);
                }

                fIcon.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        FileChooser fileChooser = new FileChooser();
                        if (JEConfig.getLastPath() != null && JEConfig.getLastPath().isDirectory() && JEConfig.getLastPath().canRead()) {
                            fileChooser.setInitialDirectory(JEConfig.getLastPath().getParentFile());
                        }

                        FileChooser.ExtensionFilter allImagesFilter = new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.gif");
                        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("PNG files (*.png)", "*.png");
                        FileChooser.ExtensionFilter gifFilter = new FileChooser.ExtensionFilter("GIF files (*.gif)", "*.gif");
                        FileChooser.ExtensionFilter jpgFilter = new FileChooser.ExtensionFilter("JPG files (*.jpg)", "*.jpg");
                        FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All files (*.*)", "*.*");
                        fileChooser.getExtensionFilters().addAll(allImagesFilter, gifFilter, extFilter, jpgFilter, allFilter);
                        final File file = fileChooser.showOpenDialog(JEConfig.getStage());
                        if (file != null) {
                            openFile(file);
                            JEConfig.setLastPath(file);
                            try {
                                _class.setIcon(file);
                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        Image image = new Image(file.toURI().toString());
                                        ImageView iView = new ImageView(image);
                                        iView.setFitHeight(30);
                                        iView.setFitWidth(30);
                                        fIcon.setGraphic(iView);
                                    }
                                });

                            } catch (JEVisException ex) {
                                logger.catching(ex);
                                ExceptionDialog dia = new ExceptionDialog();
                                dia.show(I18n.getInstance().getString("dialog.error.title"),
                                        I18n.getInstance().getString("plugin.classes.editor.error.icon.message"), ex.getMessage(), ex, null);
                            }
                        }
                    }
                });

                try {
                    fIcon.setDisable(!_class.getDataSource().getCurrentUser().isSysAdmin());
                } catch (Exception ex) {

                }

                ScrollPane cpGenerell = new ScrollPane();
                cpGenerell.setContent(gridPane);

                final TitledPane t1 = new TitledPane(I18n.getInstance().getString("plugin.classes.editor.tab.general"), cpGenerell);
                t2 = new TitledPane(I18n.getInstance().getString("plugin.classes.editor.tab.types"), buildTypeNode());

                ValidParentEditor redit = new ValidParentEditor();
                redit.setJEVisClass(jclass);

                final TitledPane t3 = new TitledPane(I18n.getInstance().getString("plugin.classes.editor.tab.valid_parents"), redit.getView());

                t1.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);
                t2.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);
                t3.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);

                cpGenerell.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);

                accordion.getPanes().addAll(t1, t2, t3);
                t1.setAnimated(false);
                t2.setAnimated(false);
                t3.setAnimated(false);
                Platform.runLater(new Runnable() {
                    @Override
                    public void run() {
                        accordion.setExpandedPane(t1);//TODO the selected pane is not blue highlighted like if the user clicked.....
                    }
                });

                boolean sysAdmin = false;
                try {
                    sysAdmin = JEConfig.getDataSource().getCurrentUser().isSysAdmin();
                } catch (Exception ex) {
                    logger.error("Could not check the Sys Admin right", ex);
                }
                /* renaming a class would orphan its objects, it is not supported */
                fName.setEditable(false);
                fName.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.classes.editor.rename.notsupported")));
                fUnique.setDisable(!sysAdmin);
                fDescription.setEditable(sysAdmin);

                _view.getChildren().setAll(accordion);
                VBox.setVgrow(accordion, Priority.ALWAYS);
            }
        });

    }

    public Node getView() {
        return _view;
    }

    /**
     * @return
     * @TODO: make this an extra GUI class
     */
    private Node buildTypeNode() {
        ScrollPane cp = new ScrollPane();
//        cp.setStyle("-fx-background-color: #E2E2E2");
        cp.setStyle("-fx-background-color: " + Constants.Color.LIGHT_GREY2);
        GridPane gridPane = new GridPane();
        gridPane.setPadding(new Insets(5, 0, 5, 20));
        gridPane.setHgap(7);
        gridPane.setVgap(7);

        Label headerName = new Label(I18n.getInstance().getString("plugin.classes.editor.type.name"));
        Label headerPType = new Label(I18n.getInstance().getString("plugin.classes.editor.type.prim_type"));
        Label headerGType = new Label(I18n.getInstance().getString("plugin.classes.editor.type.guitype"));
        Label headerValidity = new Label(I18n.getInstance().getString("plugin.classes.editor.type.validity"));
        Label headerDescription = new Label(I18n.getInstance().getString("plugin.classes.editor.type.description"));

        Separator headerSep = new Separator();
        gridPane.add(headerName, 0, 0);
        gridPane.add(headerPType, 1, 0);
        gridPane.add(headerGType, 2, 0);
        gridPane.add(headerValidity, 3, 0);
        gridPane.add(headerDescription, 4, 0);
        gridPane.add(headerSep, 0, 1, 8, 1);

        boolean sysAdmin = false;
        try {
            sysAdmin = JEConfig.getDataSource().getCurrentUser().isSysAdmin();
        } catch (Exception ex) {
            logger.error("Could not check the Sys Admin right", ex);
        }
        final String validityLast = I18n.getInstance().getString("plugin.classes.editor.type.validity.last");
        final String validityAtDate = I18n.getInstance().getString("plugin.classes.editor.type.validity.atdate");

        int row = 2;
        try {
            sortTypes();
            final List<JEVisType> types = _class.getTypes();
            if (types.isEmpty()) {
                Label emty = new Label(I18n.getInstance().getString("plugin.classes.editor.type.emty"));
                gridPane.add(emty, 0, row, 4, 1);
                row++;
            }
            for (int i = 0; i < types.size(); i++) {
                final JEVisType type = types.get(i);
                boolean isInherited = type.isInherited();
                boolean editable = sysAdmin && !isInherited;

                final Label lName = new Label(type.getName());

                final ChoiceBox<String> guiType = new ChoiceBox<>();
                guiType.setMaxWidth(500);
                guiType.setPrefWidth(160);

                List<String> gTypes = new ArrayList<>();
                for (DisplayType id : GUIConstants.getALL(type.getPrimitiveType())) {
                    gTypes.add(id.getId());
                }
                if (type.getGUIDisplayType() != null && !gTypes.contains(type.getGUIDisplayType())) {
                    gTypes.add(type.getGUIDisplayType());
                }

                guiType.setItems(FXCollections.observableList(gTypes));
                guiType.getSelectionModel().select(type.getGUIDisplayType());
                guiType.valueProperty().addListener((observable, oldValue, newValue) -> {
                    try {
                        if (newValue != null) {
                            type.setGUIDisplayType(newValue);
                        }
                    } catch (JEVisException ex) {
                        logger.catching(ex);
                    }
                });

                ChoiceBox<String> primType = new ChoiceBox<>();
                primType.setItems(ClassHelper.getAllPrimitiveTypes());
                primType.getSelectionModel().select(ClassHelper.getNameforPrimitiveType(type));
                primType.valueProperty().addListener((observable, oldValue, newValue) -> {
                    try {
                        type.setPrimitiveType(ClassHelper.getIDforPrimitiveType(newValue));

                        List<String> newGTypes = new ArrayList<>();
                        for (DisplayType id : GUIConstants.getALL(type.getPrimitiveType())) {
                            logger.trace("Add Guid Tpye: {}", id.getId());
                            newGTypes.add(id.getId());
                        }

                        guiType.setItems(FXCollections.observableList(newGTypes));
                        guiType.getSelectionModel().selectFirst();

                    } catch (JEVisException ex) {
                        logger.catching(ex);
                    }
                });

                ChoiceBox<String> validity = new ChoiceBox<>(FXCollections.observableArrayList(validityLast, validityAtDate));
                validity.getSelectionModel().select(type.getValidity() == JEVisConstants.Validity.AT_DATE ? validityAtDate : validityLast);
                validity.valueProperty().addListener((observable, oldValue, newValue) -> {
                    try {
                        type.setValidity(validityAtDate.equals(newValue) ? JEVisConstants.Validity.AT_DATE : JEVisConstants.Validity.LAST);
                    } catch (JEVisException ex) {
                        logger.catching(ex);
                    }
                });

                JFXTextField description = new JFXTextField(type.getDescription() == null ? "" : type.getDescription());
                description.setPrefWidth(250);
                description.textProperty().addListener((observable, oldValue, newValue) -> {
                    try {
                        type.setDescription(newValue);
                    } catch (JEVisException ex) {
                        logger.catching(ex);
                    }
                });

                final int index = i;
                JFXButton up = new JFXButton();
                up.setGraphic(JEConfig.getSVGImage(Icon.ARROW_UP, 20, 20));
                up.setDisable(!editable || index == 0 || types.get(index - 1).isInherited());
                up.setOnAction(t -> swapPosition(type, types.get(index - 1)));

                JFXButton down = new JFXButton();
                down.setGraphic(JEConfig.getSVGImage(Icon.ARROW_DOWN, 20, 20));
                down.setDisable(!editable || index == types.size() - 1 || types.get(index + 1).isInherited());
                down.setOnAction(t -> swapPosition(type, types.get(index + 1)));

                JFXButton remove = new JFXButton();
                remove.setGraphic(JEConfig.getSVGImage(Icon.DELETE, 20, 20));
                remove.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.classes.editor.type.remove.tooltip")));
                remove.setOnAction(t -> {
                    /* persisted with the save action of the plugin */
                    try {
                        _class.getTypes().remove(type);
                    } catch (JEVisException ex) {
                        logger.catching(ex);
                    }
                    _toDelete.add(type);
                    t2.setContent(buildTypeNode());
                });

                //                                              x, y
                gridPane.add(lName, 0, row);
                gridPane.add(primType, 1, row);
                gridPane.add(guiType, 2, row);
                gridPane.add(validity, 3, row);
                gridPane.add(description, 4, row);
                gridPane.add(remove, 5, row);
                gridPane.add(up, 6, row);
                gridPane.add(down, 7, row);

                lName.setDisable(isInherited);
                if (isInherited) {
                    lName.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.classes.editor.type.inherited")));
                }
                primType.setDisable(!editable);
                guiType.setDisable(!editable);
                validity.setDisable(!editable);
                description.setEditable(editable);
                description.setDisable(isInherited);
                remove.setDisable(!editable);

                GridPane.setHgrow(lName, Priority.ALWAYS);

                row++;

            }
        } catch (JEVisException ex) {
            logger.catching(ex);
        }

        Separator newSep = new Separator();
        gridPane.add(newSep, 0, row++, 8, 1);

        final JFXTextField fName = new JFXTextField();
        fName.setPromptText(I18n.getInstance().getString("plugin.classes.editor.type.newname"));
//        final ChoiceBox pTypeBox = buildPrimitiveTypeBox(null);

        JFXButton newB = new JFXButton();
        newB.setGraphic(JEConfig.getSVGImage(Icon.PLUS, 20, 20));
        newB.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent t) {
                createTypeAction(fName.getText());
            }
        });
        try {
            newB.setDisable(!JEConfig.getDataSource().getCurrentUser().isSysAdmin());
            fName.setDisable(!JEConfig.getDataSource().getCurrentUser().isSysAdmin());
        } catch (Exception ex) {

        }

        fName.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent ke) {
                if (ke.getCode().equals(KeyCode.ENTER)) {
                    createTypeAction(fName.getText());
                }
            }
        });

        fName.visibleProperty().addListener(new ChangeListener<Boolean>() {

            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue) {
                    Platform.runLater(new Runnable() {
                        @Override
                        public void run() {

                            fName.requestFocus();
                        }
                    });
                }
            }
        });

//        ChoiceBox guiType = new ChoiceBox();
//        guiType.setItems(FXCollections.observableArrayList(
//                "Text", "IP-Address", "Number", "File Selector", "Check Box", "PASSWORD Field"));
        gridPane.add(fName, 0, row);
//        gridPane.add(pTypeBox, 1, row);
//        gridPane.add(guiType, 2, row);
        gridPane.add(newB, 1, row);

        cp.setContent(gridPane);

        return cp;
    }

    private void createTypeAction(String name) {
        try {
            if (name.isEmpty()) {
                return;
            }

            if (!name.matches("^[A-Za-z0-9 _]*[A-Za-z0-9][A-Za-z0-9 _]*$")) {
                //no specail chars allowed, will this be a problem is some countries?
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(I18n.getInstance().getString("plugin.classes.editor.type.alert.name.title"));
                alert.setHeaderText(I18n.getInstance().getString("plugin.classes.editor.type.alert.name.header"));
                alert.setContentText(I18n.getInstance().getString("plugin.classes.editor.type.alert.name.message"));
                alert.showAndWait();
                return;
            }

            if (_class.getType(name) != null) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(I18n.getInstance().getString("plugin.classes.editor.type.alert.exist.title"));
                alert.setHeaderText(I18n.getInstance().getString("plugin.classes.editor.type.alert.exist.header"));
                alert.setContentText(I18n.getInstance().getString("plugin.classes.editor.type.error.exist.messagetype"));
                alert.showAndWait();
                return;
            }

            int position = 0;
            for (JEVisType type : _class.getTypes()) {
                position = Math.max(position, type.getGUIPosition());
            }

            JEVisType newType = _class.buildType(name);
            newType.setPrimitiveType(JEVisConstants.PrimitiveType.STRING);
            List<DisplayType> displayTypes = GUIConstants.getALL(JEVisConstants.PrimitiveType.STRING);
            if (!displayTypes.isEmpty()) {
                newType.setGUIDisplayType(displayTypes.get(0).getId());
            }
            newType.setValidity(JEVisConstants.Validity.LAST);
            newType.setGUIPosition(position + 1);
            _toDelete.remove(newType);

            t2.setContent(buildTypeNode());

        } catch (Exception ex) {
            logger.catching(ex);
        }
    }

    /**
     * Sorts the types of the class by GUI position, then by name.
     */
    private void sortTypes() throws JEVisException {
        _class.getTypes().sort((a, b) -> {
            try {
                int cmp = Integer.compare(a.getGUIPosition(), b.getGUIPosition());
                return cmp != 0 ? cmp : a.getName().compareTo(b.getName());
            } catch (Exception ex) {
                return 0;
            }
        });
    }

    /**
     * Swaps the GUI position of two types; if both have the same position the first one moves to the place of the
     * second one.
     */
    private void swapPosition(JEVisType type, JEVisType other) {
        try {
            int typePos = type.getGUIPosition();
            int otherPos = other.getGUIPosition();
            if (typePos == otherPos) {
                int index = _class.getTypes().indexOf(type);
                int otherIndex = _class.getTypes().indexOf(other);
                typePos = otherIndex > index ? otherPos + 1 : otherPos - 1;
                type.setGUIPosition(typePos);
            } else {
                type.setGUIPosition(otherPos);
                other.setGUIPosition(typePos);
            }
            t2.setContent(buildTypeNode());
        } catch (JEVisException ex) {
            logger.catching(ex);
        }
    }

    private void setUnitButton(Button button, JEVisType type) throws JEVisException {
        if (type.getUnit() != null) {
//            logger.info("editor.Unit: " + type.getUnit());
            if (type.getUnit().equals(AbstractUnit.ONE)) {
//                button.setText("None");
            } else {
//                logger.info(UnitManager.getInstance().format(type.getUnit()));
//                button.setText(type.getUnit().toString());
//                button.setText(UnitManager.getInstance().format(type.getUnit()));
            }

        }
    }

    private void openFile(File file) {
        try {
//            Image image = new Image(file.toURI().toString());
//            ImageView iv = new ImageView(image);
//            _class.setIcon(new ImageIcon(convertToAwtImage(image).getScaledInstance(60, 60, java.awt.Image.SCALE_SMOOTH)));
            _class.setIcon(file);
//            File newIcon = desktop.open(file);
            _class.commit();

            //reload tree(icon)
            if (_tree != null) {
                _tree.reload(_class);
            }

//            fIcon.setGraphic(getImageView(_class));
        } catch (Exception ex) {
            logger.catching(ex);

            ExceptionDialog dia = new ExceptionDialog();
            dia.show(JEConfig.getStage(), I18n.getInstance().getString("plugin.classes.editor.type.alert.openfile.title"),
                    I18n.getInstance().getString("plugin.classes.editor.type.alert.openfile.message"), ex, null);

        }
    }

    /**
     * Saves the class with its types and relationships (only Sys Admins; OpenJEVis #2079). Removed types are
     * already removed from the class, the web service refuses the change while objects have values in them.
     */
    public void commitAll() {
        if (_class == null) {
            return;
        }
        try {
            if (!_class.getDataSource().getCurrentUser().isSysAdmin()) {
                return;
            }
            logger.trace("commitAll() old: '{}' new: '{}'", _class.getName(), fName.getText());
            logger.trace("Type.count: {}", _class.getTypes().size());
            if (!fName.getText().equals(_class.getName())) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(I18n.getInstance().getString("plugin.classes.editor.type.alert.save.title"));
                alert.setHeaderText(I18n.getInstance().getString("plugin.classes.editor.type.alert.save.message"));
                alert.setContentText(I18n.getInstance().getString("plugin.classes.editor.rename.notsupported"));
                alert.showAndWait();
                fName.setText(_class.getName());
                return;
            }
            _class.setDescription(fDescription.getText());
            _class.setUnique(fUnique.isSelected());

            _class.commit();
            _toDelete.clear();

            setJEVisClass(_class);

        } catch (Exception ex) {
            logger.error("Error while saving class", ex);
            ExceptionDialog dia = new ExceptionDialog();
            dia.show(I18n.getInstance().getString("plugin.classes.editor.type.alert.save.title"),
                    I18n.getInstance().getString("plugin.classes.editor.type.alert.save.message"), ex.getMessage(), ex, null);
        }
    }

    public void rollback() {
        try {
            for (JEVisType type : _class.getTypes()) {
                type.rollBack();
            }
            _class.rollBack();

        } catch (JEVisException ex) {
            logger.catching(ex);

            ExceptionDialog dia = new ExceptionDialog();
            dia.show(JEConfig.getStage(), "Error", "Could not  rollback changes", ex, null);
        }
    }

    private ImageView getIcon(JEVisClass jclass) {
        try {
//            logger.info("getIcon for :" + jclass);
            if (jclass.getIcon() == null) {
                return JEConfig.getImage(Icon.PNG_UNKNOWN, 30, 30);
            }

            return ImageConverter.convertToImageView(jclass.getIcon(), 30, 30);
        } catch (Exception ex) {
            logger.info("Error while geeting class icon: " + ex);
            ex.printStackTrace();
            return JEConfig.getImage(Icon.PNG_UNKNOWN, 30, 30);
        }

    }
}
