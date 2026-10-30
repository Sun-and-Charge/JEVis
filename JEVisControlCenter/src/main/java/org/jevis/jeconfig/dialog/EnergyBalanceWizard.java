/**
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
package org.jevis.jeconfig.dialog;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.calculation.CalcInputType;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.utils.NameFormatter;
import org.jevis.commons.utils.ObjectHelper;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.application.jevistree.JEVisTree;
import org.jevis.jeconfig.application.jevistree.TreeHelper;
import org.jevis.jeconfig.application.jevistree.UserSelection;
import org.jevis.jeconfig.application.jevistree.dialog.NewObject;
import org.jevis.jeconfig.application.resource.ResourceLoader;
import org.jevis.jeconfig.dialog.EnergyBalanceExpression.Sign;
import org.joda.time.DateTime;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Energy balance wizard (OpenJEVis #2368): creates a normal Calculation "main meter minus sub-meters"
 * with an output data object (and optionally a second calculation for the difference in percent).
 */
public class EnergyBalanceWizard extends Dialog<ButtonType> {
    private static final Logger logger = LogManager.getLogger(EnergyBalanceWizard.class);
    private static final String BUILDING_CLASS = "Building";
    private static final String CALCULATION_DIRECTORY_CLASS = "Calculation Directory";
    private static final String DATA_DIRECTORY_CLASS = "Data Directory";
    private static final String CLEAN_DATA_CLASS = "Clean Data";
    private static final String VALUE_IS_A_QUANTITY = "Value is a Quantity";

    private final JEVisObject object;
    private final JEVisTree tree;
    private final JEVisDataSource ds;
    private final JFXTextField nameField = new JFXTextField();
    private final MeterRow mainRow;
    private final List<MeterRow> subRows = new ArrayList<>();
    private final VBox subRowBox = new VBox(6);
    private final JFXButton directoryButton = new JFXButton();
    private final JFXCheckBox percentBox = new JFXCheckBox(I18n.getInstance().getString("plugin.energybalance.percent"));
    private final Label previewLabel = new Label();
    private JEVisObject calculationDirectory;
    private JEVisObject dataDirectory;

    /**
     * @param object a Calculation Directory or a Building (or an object below a building)
     * @param tree   tree in which the new calculation is selected afterwards, may be null
     */
    public EnergyBalanceWizard(JEVisObject object, JEVisTree tree) {
        super();
        this.object = object;
        this.tree = tree;
        this.ds = dataSourceOf(object);

        setTitle(I18n.getInstance().getString("plugin.energybalance.title"));
        setHeaderText(I18n.getInstance().getString("plugin.energybalance.header"));
        setResizable(true);
        initOwner(JEConfig.getStage());
        initModality(Modality.APPLICATION_MODAL);
        Stage stage = (Stage) getDialogPane().getScene().getWindow();
        TopMenu.applyActiveTheme(stage.getScene());
        stage.setAlwaysOnTop(true);

        findDirectories();

        nameField.setPromptText(I18n.getInstance().getString("plugin.energybalance.name"));
        nameField.setPrefWidth(400);

        mainRow = new MeterRow(false);

        JFXButton addButton = new JFXButton(I18n.getInstance().getString("plugin.energybalance.addsubmeter"),
                ResourceLoader.getImage("list-add.png", 15, 15));
        addButton.setOnAction(event -> addSubRow());
        addSubRow();

        updateDirectoryButton();
        directoryButton.setOnAction(event -> selectDataDirectory());
        percentBox.selectedProperty().addListener((observable, oldValue, newValue) -> updatePreview());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        int row = 0;
        grid.add(new Label(I18n.getInstance().getString("plugin.energybalance.name")), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label(I18n.getInstance().getString("plugin.energybalance.mainmeter")), 0, row);
        grid.add(mainRow, 1, row++);
        Label subLabel = new Label(I18n.getInstance().getString("plugin.energybalance.submeters"));
        GridPane.setValignment(subLabel, VPos.TOP);
        grid.add(subLabel, 0, row);
        VBox subBox = new VBox(6, subRowBox, addButton);
        ScrollPane scrollPane = new ScrollPane(subBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(220);
        GridPane.setHgrow(scrollPane, Priority.ALWAYS);
        grid.add(scrollPane, 1, row++);
        grid.add(new Label(I18n.getInstance().getString("plugin.energybalance.datadirectory")), 0, row);
        grid.add(directoryButton, 1, row++);
        grid.add(percentBox, 1, row++);
        grid.add(new Label(I18n.getInstance().getString("plugin.energybalance.expression")), 0, row);
        previewLabel.setWrapText(true);
        previewLabel.setMinHeight(Region.USE_PREF_SIZE);
        grid.add(previewLabel, 1, row);

        VBox content = new VBox(8, grid);
        content.setPadding(new Insets(8));
        content.setPrefWidth(820);
        getDialogPane().setContent(content);

        ButtonType okType = new ButtonType(I18n.getInstance().getString("graph.dialog.ok"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(I18n.getInstance().getString("graph.dialog.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(cancelType, okType);
        Button okButton = (Button) getDialogPane().lookupButton(okType);
        okButton.setDefaultButton(true);
        okButton.addEventFilter(ActionEvent.ACTION, event -> {
            String error = validate();
            if (error != null) {
                event.consume();
                showWarning(error);
                return;
            }
            try {
                JEVisObject calculation = create();
                selectInTree(calculation);
            } catch (Exception ex) {
                event.consume();
                logger.error("Could not create energy balance", ex);
                ExceptionDialog2.showException(JEConfig.getStage(), ex);
            }
        });

        updatePreview();
    }

    private static JEVisDataSource dataSourceOf(JEVisObject obj) {
        try {
            return obj.getDataSource();
        } catch (JEVisException e) {
            logger.error("Could not get data source of {}", obj, e);
            return JEConfig.getDataSource();
        }
    }

    private void findDirectories() {
        try {
            if (isOfClass(object, CALCULATION_DIRECTORY_CLASS)) {
                calculationDirectory = object;
            }
            JEVisObject building = findBuilding(object);
            if (building != null) {
                for (JEVisObject child : building.getChildren()) {
                    if (calculationDirectory == null && isOfClass(child, CALCULATION_DIRECTORY_CLASS)) {
                        calculationDirectory = child;
                    } else if (dataDirectory == null && isOfClass(child, DATA_DIRECTORY_CLASS)) {
                        dataDirectory = child;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Could not find the directories of {}", object, e);
        }
    }

    private static JEVisObject findBuilding(JEVisObject obj) throws JEVisException {
        if (isOfClass(obj, BUILDING_CLASS)) return obj;
        for (JEVisObject parent : obj.getParents()) {
            JEVisObject building = findBuilding(parent);
            if (building != null) return building;
        }
        return null;
    }

    private static boolean isOfClass(JEVisObject obj, String className) throws JEVisException {
        JEVisClass jeVisClass = obj.getJEVisClass();
        if (jeVisClass == null) return false;
        if (jeVisClass.getName().equals(className)) return true;
        JEVisClass inheritance = jeVisClass.getInheritance();
        return inheritance != null && inheritance.getName().equals(className);
    }

    private void addSubRow() {
        MeterRow row = new MeterRow(true);
        subRows.add(row);
        subRowBox.getChildren().add(row);
        updatePreview();
    }

    private void removeSubRow(MeterRow row) {
        subRows.remove(row);
        subRowBox.getChildren().remove(row);
        updatePreview();
    }

    private List<Sign> signs() {
        List<Sign> signs = new ArrayList<>();
        for (MeterRow row : subRows) {
            signs.add(row.signBox.getValue());
        }
        return signs;
    }

    private void updatePreview() {
        String text = EnergyBalanceExpression.difference(signs());
        if (percentBox.isSelected()) {
            text += "\n" + EnergyBalanceExpression.percentage(signs());
        }
        previewLabel.setText(text);
    }

    private void updateDirectoryButton() {
        if (dataDirectory != null) {
            directoryButton.setText(dataDirectory.getName());
            directoryButton.setTooltip(new Tooltip("ID: " + dataDirectory.getID()));
        } else {
            directoryButton.setText(I18n.getInstance().getString("plugin.energybalance.select"));
        }
    }

    private void selectDataDirectory() {
        try {
            List<JEVisClass> classes = classWithHeirs(DATA_DIRECTORY_CLASS);
            List<UserSelection> selection = dataDirectory != null
                    ? Collections.singletonList(new UserSelection(UserSelection.SelectionType.Object, dataDirectory))
                    : new ArrayList<>();
            openSelection(classes, selection, selected -> {
                dataDirectory = selected;
                updateDirectoryButton();
            });
        } catch (Exception e) {
            logger.error("Could not open data directory selection", e);
        }
    }

    private List<JEVisClass> classWithHeirs(String... classNames) throws JEVisException {
        List<JEVisClass> classes = new ArrayList<>();
        for (String className : classNames) {
            JEVisClass jeVisClass = ds.getJEVisClass(className);
            if (jeVisClass == null) continue;
            if (!classes.contains(jeVisClass)) classes.add(jeVisClass);
            for (JEVisClass heir : jeVisClass.getHeirs()) {
                if (!classes.contains(heir)) classes.add(heir);
            }
        }
        return classes;
    }

    private void openSelection(List<JEVisClass> classes, List<UserSelection> selection, Consumer<JEVisObject> onSelected) {
        TreeSelectionDialog selectionDialog = new TreeSelectionDialog(ds, classes, SelectionMode.SINGLE, selection, false);
        selectionDialog.setOnCloseRequest(event -> {
            if (selectionDialog.getResponse() == Response.OK && !selectionDialog.getUserSelection().isEmpty()) {
                JEVisObject selected = selectionDialog.getUserSelection().get(0).getSelectedObject();
                if (selected != null) {
                    onSelected.accept(selected);
                }
            }
        });
        selectionDialog.show();
    }

    private String validate() {
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            return I18n.getInstance().getString("plugin.energybalance.error.name");
        }
        if (mainRow.target == null) {
            return I18n.getInstance().getString("plugin.energybalance.error.main");
        }
        if (subRows.isEmpty()) {
            return I18n.getInstance().getString("plugin.energybalance.error.submeters");
        }
        for (MeterRow row : subRows) {
            if (row.target == null) {
                return I18n.getInstance().getString("plugin.energybalance.error.submeters");
            }
        }
        try {
            JEVisUser user = ds.getCurrentUser();
            if (calculationDirectory == null || dataDirectory == null
                    || !user.canCreate(calculationDirectory.getID()) || !user.canCreate(dataDirectory.getID())) {
                return I18n.getInstance().getString("plugin.energybalance.error.directory");
            }
        } catch (Exception e) {
            logger.error("Could not check permissions", e);
            return I18n.getInstance().getString("dialog.warning.notallowed");
        }
        return null;
    }

    private void showWarning(String text) {
        Alert alert = new Alert(Alert.AlertType.WARNING, text);
        alert.initOwner(getDialogPane().getScene().getWindow());
        alert.showAndWait();
    }

    /**
     * Creates the balance calculation and, if selected, the percentage calculation.
     *
     * @return the balance calculation
     */
    private JEVisObject create() throws JEVisException {
        String name = nameField.getText().trim();
        String suffix = I18n.getInstance().getString("plugin.energybalance.output.suffix");
        List<Sign> signs = signs();

        JEVisObject balance = createCalculation(name, EnergyBalanceExpression.difference(signs), name + " " + suffix, false);
        if (percentBox.isSelected()) {
            createCalculation(name + " %", EnergyBalanceExpression.percentage(signs), name + " " + suffix + " %", true);
        }
        return balance;
    }

    private JEVisObject createCalculation(String calcName, String expression, String outputName, boolean percent) throws JEVisException {
        JEVisClass calcClass = ds.getJEVisClass(JC.Calculation.name);
        JEVisClass inputClass = ds.getJEVisClass(JC.Input.name);
        JEVisClass outputClass = ds.getJEVisClass(JC.Output.name);
        JEVisClass dataClass = ds.getJEVisClass(JC.Data.name);
        DateTime now = new DateTime();

        JEVisObject calculation = calculationDirectory.buildObject(calcName, calcClass);
        calculation.commit();
        try {
            NewObject.createDefaultValues(calculation, false);
        } catch (Exception ex) {
            logger.error("Could not set default values of calculation {}", calculation, ex);
        }
        calculation.getAttribute(JC.Calculation.a_Expression).buildSample(now, expression).commit();

        createInput(calculation, inputClass, EnergyBalanceExpression.MAIN_IDENTIFIER, mainRow, now);
        for (int i = 0; i < subRows.size(); i++) {
            createInput(calculation, inputClass, EnergyBalanceExpression.subIdentifier(i), subRows.get(i), now);
        }

        /* output: new Data object with Clean Data child, period and unit of the main meter */
        JEVisObject mainSource = mainRow.inputTarget();
        JEVisObject outputData = dataDirectory.buildObject(outputName, dataClass);
        outputData.commit();
        try {
            NewObject.createDefaultValues(outputData, true, periodOf(mainSource));
            if (percent) {
                markAsNoQuantity(outputData);
            } else {
                copyUnitAndQuantity(mainSource, outputData);
            }
        } catch (Exception ex) {
            logger.error("Could not set default values of output data {}", outputData, ex);
        }

        JEVisObject output = calculation.buildObject(NameFormatter.createVariableName(outputData), outputClass);
        output.commit();
        output.getAttribute(JC.Output.a_Output).buildSample(now, outputData.getID() + ":Value").commit();

        calculation.getAttribute(JC.Calculation.a_Enabled).buildSample(now, true).commit();
        return calculation;
    }

    private void createInput(JEVisObject calculation, JEVisClass inputClass, String identifier, MeterRow row, DateTime now) throws JEVisException {
        JEVisObject target = row.inputTarget();
        JEVisObject input = calculation.buildObject(identifier, inputClass);
        input.commit();
        input.getAttribute(JC.Input.a_Identifier).buildSample(now, identifier).commit();
        input.getAttribute(JC.Input.a_InputData).buildSample(now, target.getID() + ":Value").commit();
        CalcInputType type = row.typeBox.getValue() != null ? row.typeBox.getValue() : CalcInputType.PERIODIC;
        input.getAttribute(JC.Input.a_InputDataType).buildSample(now, type.name()).commit();
    }

    private static Period periodOf(JEVisObject obj) {
        try {
            JEVisAttribute periodAttribute = obj.getAttribute(JC.Data.a_Period);
            if (periodAttribute != null && periodAttribute.hasSample()) {
                return new Period(periodAttribute.getLatestSample().getValueAsString());
            }
            JEVisAttribute valueAttribute = obj.getAttribute(JC.Data.a_Value);
            if (valueAttribute != null && valueAttribute.getInputSampleRate() != null) {
                return valueAttribute.getInputSampleRate();
            }
        } catch (Exception e) {
            logger.warn("Could not read period of {}", obj, e);
        }
        return null;
    }

    private static void copyUnitAndQuantity(JEVisObject source, JEVisObject outputData) throws JEVisException {
        JEVisAttribute sourceValue = source.getAttribute(JC.Data.a_Value);
        List<JEVisObject> targets = new ArrayList<>();
        targets.add(outputData);
        targets.addAll(outputData.getChildren(outputData.getDataSource().getJEVisClass(CLEAN_DATA_CLASS), false));

        for (JEVisObject target : targets) {
            JEVisAttribute targetValue = target.getAttribute(JC.Data.a_Value);
            if (sourceValue != null && targetValue != null) {
                boolean changed = false;
                if (sourceValue.getInputUnit() != null) {
                    targetValue.setInputUnit(sourceValue.getInputUnit());
                    changed = true;
                }
                if (sourceValue.getDisplayUnit() != null) {
                    targetValue.setDisplayUnit(sourceValue.getDisplayUnit());
                    changed = true;
                }
                if (changed) targetValue.commit();
            }

            if (!target.equals(outputData)) {
                JEVisAttribute sourceQuantity = source.getAttribute(VALUE_IS_A_QUANTITY);
                JEVisAttribute targetQuantity = target.getAttribute(VALUE_IS_A_QUANTITY);
                if (sourceQuantity != null && sourceQuantity.hasSample() && targetQuantity != null) {
                    targetQuantity.buildSample(new DateTime(), sourceQuantity.getLatestSample().getValueAsBoolean()).commit();
                }
            }
        }
    }

    /**
     * A percentage is not a quantity, its clean data must not be summed up.
     */
    private static void markAsNoQuantity(JEVisObject outputData) throws JEVisException {
        for (JEVisObject cleanData : outputData.getChildren(outputData.getDataSource().getJEVisClass(CLEAN_DATA_CLASS), false)) {
            JEVisAttribute quantity = cleanData.getAttribute(VALUE_IS_A_QUANTITY);
            if (quantity != null) {
                quantity.buildSample(new DateTime(), false).commit();
            }
        }
    }

    private void selectInTree(JEVisObject calculation) {
        if (tree == null || calculation == null) return;
        Platform.runLater(() -> {
            try {
                List<JEVisObject> toOpen = ObjectHelper.getAllParents(calculation);
                toOpen.add(calculation);
                TreeHelper.openPath(tree, toOpen, tree.getRoot(), calculation);
            } catch (Exception e) {
                logger.warn("Could not select new calculation in tree", e);
            }
        });
    }

    /**
     * One meter of the balance: selection button, input data type and, for sub-meters, sign and remove button.
     */
    private class MeterRow extends HBox {
        private final JFXComboBox<Sign> signBox = new JFXComboBox<>();
        private final JFXComboBox<CalcInputType> typeBox = new JFXComboBox<>();
        private final JFXButton selectButton = new JFXButton(I18n.getInstance().getString("plugin.energybalance.select"));
        private JEVisObject target;

        MeterRow(boolean subMeter) {
            super(6);
            setAlignment(Pos.CENTER_LEFT);

            typeBox.getItems().addAll(CalcInputType.PERIODIC, CalcInputType.ASYNC);
            typeBox.getSelectionModel().select(CalcInputType.PERIODIC);
            typeBox.setConverter(new StringConverter<CalcInputType>() {
                @Override
                public String toString(CalcInputType type) {
                    if (type == null) return "";
                    return I18n.getInstance().getString(type == CalcInputType.ASYNC
                            ? "plugin.energybalance.inputtype.async" : "plugin.energybalance.inputtype.periodic");
                }

                @Override
                public CalcInputType fromString(String string) {
                    return null;
                }
            });
            typeBox.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.energybalance.inputtype")));

            selectButton.setMinWidth(300);
            selectButton.setAlignment(Pos.CENTER_LEFT);
            selectButton.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(selectButton, Priority.ALWAYS);
            selectButton.setOnAction(event -> select());

            if (subMeter) {
                signBox.getItems().addAll(Sign.MINUS, Sign.PLUS);
                signBox.getSelectionModel().select(Sign.MINUS);
                signBox.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.energybalance.sign")));
                signBox.valueProperty().addListener((observable, oldValue, newValue) -> updatePreview());
                JFXButton removeButton = new JFXButton("", ResourceLoader.getImage("list-remove.png", 15, 15));
                removeButton.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.energybalance.removesubmeter")));
                removeButton.setOnAction(event -> removeSubRow(this));
                getChildren().addAll(signBox, selectButton, typeBox, removeButton);
            } else {
                getChildren().addAll(selectButton, typeBox);
            }
        }

        private void select() {
            try {
                List<JEVisClass> classes = classWithHeirs(JC.Data.name, CLEAN_DATA_CLASS);
                List<UserSelection> selection = target != null
                        ? Collections.singletonList(new UserSelection(UserSelection.SelectionType.Object, target))
                        : new ArrayList<>();
                openSelection(classes, selection, selected -> {
                    target = selected;
                    String text = selected.getName();
                    try {
                        if (isOfClass(selected, CLEAN_DATA_CLASS) && !selected.getParents().isEmpty()) {
                            text = selected.getParents().get(0).getName() + " / " + selected.getName();
                        }
                    } catch (Exception e) {
                        logger.debug("Could not read parent of {}", selected, e);
                    }
                    selectButton.setText(text);
                    selectButton.setTooltip(new Tooltip("ID: " + selected.getID()));
                });
            } catch (Exception e) {
                logger.error("Could not open data selection", e);
            }
        }

        /**
         * @return the object used as input: the selected Clean Data, or the first Clean Data below a selected Data object
         */
        JEVisObject inputTarget() throws JEVisException {
            if (target != null && !isOfClass(target, CLEAN_DATA_CLASS)) {
                JEVisClass cleanDataClass = ds.getJEVisClass(CLEAN_DATA_CLASS);
                List<JEVisObject> cleanData = target.getChildren(cleanDataClass, true);
                if (!cleanData.isEmpty()) return cleanData.get(0);
            }
            return target;
        }
    }
}
