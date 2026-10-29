/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.plugin.meters;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeconfig.GlobalToolBar;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.application.tools.JEVisHelp;
import org.jevis.jeconfig.dialog.LocalNameDialog;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.plugin.meters.event.PrecisionEvent;
import org.jevis.jeconfig.plugin.meters.export.MetersExport;
import org.jevis.jeconfig.plugin.meters.qr.MeterLabelPdf;
import org.jevis.jeconfig.plugin.meters.qr.MeterQrCode;
import org.jevis.jeconfig.plugin.meters.ui.MeterTable;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MetersToolbar extends ToolBar {
    private static final Logger logger = LogManager.getLogger(MeterController.class);


    private final double iconSize = 20;

    private final ToggleButton deleteItem = new ToggleButton("", JEConfig.getSVGImage(Icon.DELETE, iconSize, iconSize));
    private final ToggleButton exportxlsx = new ToggleButton("", JEConfig.getSVGImage(Icon.EXCEL, this.iconSize, this.iconSize));
    private final ToggleButton qrLabels = new ToggleButton("", JEConfig.getSVGImage(Icon.QR_CODE, this.iconSize, this.iconSize));
    private final ToggleButton rename = new ToggleButton("", JEConfig.getSVGImage(Icon.TRANSLATE, this.iconSize, this.iconSize));

    private final ToggleButton add = new ToggleButton("", JEConfig.getSVGImage(Icon.PLUS_CIRCLE, iconSize, iconSize));
    private final ToggleButton switchButton = new ToggleButton("", JEConfig.getSVGImage(Icon.SWITCH, iconSize, iconSize));
    private final ToggleButton increasePrecision = new ToggleButton("", JEConfig.getSVGImage(Icon.DECIMAL_INCREASE, iconSize, iconSize));
    private final ToggleButton decreasePrecision = new ToggleButton("", JEConfig.getSVGImage(Icon.DECIMAL_DECREASE, iconSize, iconSize));

    private final ToggleButton helpButton = JEVisHelp.getInstance().buildHelpButtons(iconSize, iconSize);
    private final ToggleButton infoButton = JEVisHelp.getInstance().buildInfoButtons(iconSize, iconSize);

    private final MeterController meterController;

    public MetersToolbar(MeterController meterController) {
        this.meterController = meterController;

        getItems().addAll(add, exportxlsx, qrLabels, rename, deleteItem, switchButton, increasePrecision, decreasePrecision);


        add.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.add")));
        exportxlsx.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.exportxlsx")));
        qrLabels.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.qr.tooltip")));
        rename.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.rename")));
        deleteItem.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.delete")));
        switchButton.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.switchmeter")));
        increasePrecision.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.increaseprecision")));
        decreasePrecision.setTooltip(new Tooltip(I18n.getInstance().getString("plugin.meters.tooltip.decreaseprecision")));

        add.setOnAction(actionEvent -> meterController.addMeter());
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save");
        fileChooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("Excel", ".xlsx"));

        exportxlsx.setOnAction(actionEvent -> {
            File file = fileChooser.showSaveDialog(meterController.getContent().getScene().getWindow());
            MetersExport metersExport = new MetersExport(meterController.getActiveTab().getPlan());
            metersExport.export();
            try {
                metersExport.save(file);
            } catch (IOException ioException) {
                logger.error(ioException);
            }
        });

        qrLabels.setOnAction(actionEvent -> exportQrLabels());

        switchButton.setOnAction(actionEvent -> {
            logger.debug(meterController.getSelectedItem());

            meterController.openDataForm(meterController.getSelectedItem(), true, false);
        });


        increasePrecision.setOnAction(actionEvent -> {
            meterController.getActiveTable().getSelectedItems().forEach(meterData -> {
                meterController.getActiveTable().getPrecisionEventHandler().fireEvent(new PrecisionEvent(meterData, PrecisionEvent.TYPE.INCREASE));
            });
        });

        decreasePrecision.setOnAction(actionEvent -> {

            meterController.getActiveTable().getSelectedItems().forEach(meterData -> {
                meterController.getActiveTable().getPrecisionEventHandler().fireEvent(new PrecisionEvent(meterData, PrecisionEvent.TYPE.DECREASE));
            });

        });

        rename.setOnAction(actionEvent -> {
            MeterData meterData = meterController.getSelectedItem();
            LocalNameDialog localNameDialog = new LocalNameDialog(meterData.getJeVisObject());
            LocalNameDialog.Response response = localNameDialog.show();
            if (response.equals(LocalNameDialog.Response.YES)) {

                MeterTable meterTable = meterController.getActiveTable();
                meterTable.replaceItem(meterData);
                meterTable.refresh();
            }
        });


        deleteItem.setOnAction(actionEvent -> {
            MeterData meterData = meterController.getSelectedItem();
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.getInstance().getString("plugin.meters.delete.title"), ButtonType.OK, ButtonType.CANCEL);

            alert.setTitle(I18n.getInstance().getString("plugin.meters.delete.title"));
            alert.setContentText(String.format(I18n.getInstance().getString("plugin.meters.delete.body"), meterData.getName()));
            Optional<ButtonType> result = alert.showAndWait();
            if (result.orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try {
                meterController.getActiveTable().removeItem(meterData);
                meterData.getJeVisObject().delete();
            } catch (JEVisException jeVisException) {
                logger.error(jeVisException);
            }
            meterController.getActiveTable().refresh();
        });


        this.meterController.canDeleteProperty().addListener((observableValue, aBoolean, t1) -> {
            deleteItem.setDisable(!t1);
        });

        this.meterController.canWriteProperty().addListener((observableValue, aBoolean, t1) -> {
            add.setDisable(!t1);
        });

        add.setDisable(!meterController.isCanWrite());
        deleteItem.setDisable(!meterController.isCanDelete());

        getItems().stream().filter(node -> node instanceof ToggleButton).forEach(node -> GlobalToolBar.changeBackgroundOnHoverUsingBinding(node));


        getItems().addAll(JEVisHelp.getInstance().buildSpacerNode(), helpButton, infoButton);
        Platform.runLater(() -> JEVisHelp.getInstance().addHelpItems(MeterPlugin.class.getSimpleName(), "", JEVisHelp.LAYOUT.VERTICAL_BOT_CENTER, getItems()));


    }

    /**
     * Writes QR code labels of the selected meters, or of all filtered meters if none is selected, to a PDF.
     */
    private void exportQrLabels() {
        MeterTable table = meterController.getActiveTable();
        List<MeterData> meters = new ArrayList<>(table.getSelectedItems().isEmpty() ? table.getItems() : table.getSelectedItems());
        if (meters.isEmpty()) {
            new Alert(Alert.AlertType.INFORMATION, I18n.getInstance().getString("plugin.meters.qr.empty"), ButtonType.OK).showAndWait();
            return;
        }

        FileChooser pdfChooser = new FileChooser();
        pdfChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        pdfChooser.setInitialFileName("QR_Labels.pdf");
        File file = pdfChooser.showSaveDialog(meterController.getContent().getScene().getWindow());
        if (file == null) return;

        JEVisDataSource ds = meterController.getDataSource();
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                updateTitle(I18n.getInstance().getString("plugin.meters.qr.generating"));
                ObjectRelations objectRelations = new ObjectRelations(ds);
                List<MeterQrCode> codes = new ArrayList<>();
                for (MeterData meterData : meters) {
                    codes.add(MeterQrCode.of(meterData, ds, objectRelations));
                }
                new MeterLabelPdf().write(codes, file);
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file);
                }
                return null;
            }
        };
        task.setOnFailed(event -> {
            logger.error("Could not create QR labels", task.getException());
            new Alert(Alert.AlertType.ERROR, I18n.getInstance().getString("plugin.meters.qr.error"), ButtonType.OK).showAndWait();
        });
        JEConfig.getStatusBar().addTask(MetersToolbar.class.getName(), task, null, true);
    }
}
