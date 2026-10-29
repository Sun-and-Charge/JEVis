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
package org.jevis.jeconfig.plugin.meters.qr;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.relationship.ObjectRelations;
import org.jevis.jeconfig.Icon;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.plugin.meters.data.MeterData;
import org.jevis.jeconfig.tool.QrCodes;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.Collections;

/**
 * Shows the QR code of one meter with buttons to save it as PNG or PDF label and to copy it.
 */
public class MeterQrPane extends VBox {
    private static final Logger logger = LogManager.getLogger(MeterQrPane.class);
    private static final int DISPLAY_SIZE = 260;
    private static final int PNG_SIZE = 600;

    public MeterQrPane(MeterData meterData, JEVisDataSource ds) {
        super(8);
        setPadding(new Insets(12));
        setAlignment(Pos.TOP_CENTER);

        MeterQrCode code = MeterQrCode.of(meterData, ds, new ObjectRelations(ds));

        ImageView qrView = new ImageView(QrCodes.image(code.getPayload(), DISPLAY_SIZE));

        Label name = new Label(code.getName());
        name.setFont(Font.font(null, FontWeight.BOLD, 14));
        VBox info = new VBox(2, name);
        info.setAlignment(Pos.CENTER);
        if (!code.getMeasuringPointName().isEmpty()) info.getChildren().add(new Label(code.getMeasuringPointName()));
        if (!code.getSerialNumber().isEmpty()) {
            info.getChildren().add(new Label(I18n.getInstance().getString("plugin.meters.qr.serial") + " " + code.getSerialNumber()));
        }
        if (!code.getPath().isEmpty()) info.getChildren().add(new Label(code.getPath()));
        if (!code.getPayload().contains("&target=")) {
            Label noTarget = new Label(I18n.getInstance().getString("plugin.meters.qr.notarget"));
            noTarget.setWrapText(true);
            noTarget.setStyle("-fx-text-fill: #c0392b;");
            info.getChildren().add(noTarget);
        }

        TextField payload = new TextField(code.getPayload());
        payload.setEditable(false);
        payload.setPrefColumnCount(40);
        HBox payloadBox = new HBox(6, new Label(I18n.getInstance().getString("plugin.meters.qr.payload")), payload);
        payloadBox.setAlignment(Pos.CENTER);

        Button savePng = new Button(I18n.getInstance().getString("plugin.meters.qr.savepng"), JEConfig.getSVGImage(Icon.SAVE, 14, 14));
        savePng.setOnAction(event -> savePng(code));

        Button savePdf = new Button(I18n.getInstance().getString("plugin.meters.qr.savepdf"), JEConfig.getSVGImage(Icon.PRINT, 14, 14));
        savePdf.setOnAction(event -> savePdf(code));

        Button copy = new Button(I18n.getInstance().getString("plugin.meters.qr.copy"), JEConfig.getSVGImage(Icon.COPY, 14, 14));
        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putImage(QrCodes.image(code.getPayload(), PNG_SIZE));
            content.putString(code.getPayload());
            Clipboard.getSystemClipboard().setContent(content);
        });

        HBox buttons = new HBox(8, savePng, savePdf, copy);
        buttons.setAlignment(Pos.CENTER);

        getChildren().addAll(qrView, info, payloadBox, buttons);
    }

    private static String fileName(MeterQrCode code) {
        return ("QR_" + code.getName()).replaceAll("[^\\p{L}\\p{N}._-]+", "_");
    }

    private void savePng(MeterQrCode code) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG", "*.png"));
        fileChooser.setInitialFileName(fileName(code) + ".png");
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        try {
            Image image = QrCodes.image(code.getPayload(), PNG_SIZE);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
        } catch (Exception e) {
            logger.error("Could not save QR code", e);
        }
    }

    private void savePdf(MeterQrCode code) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fileChooser.setInitialFileName(fileName(code) + ".pdf");
        File file = fileChooser.showSaveDialog(getScene().getWindow());
        if (file == null) return;
        try {
            new MeterLabelPdf().write(Collections.singletonList(code), file);
        } catch (Exception e) {
            logger.error("Could not save QR label", e);
        }
    }
}
