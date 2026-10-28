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
package org.jevis.jeconfig.plugin.action.data;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.commons.classes.JC;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeconfig.JEConfig;
import org.joda.time.DateTime;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class FileData {

    public StringProperty nameProperty = new SimpleStringProperty("-");
    public ObjectProperty<DateTime> changeDateProperty = new SimpleObjectProperty<>(null);
    public StringProperty userProperty = new SimpleStringProperty();

    private JEVisObject fileObj;

    public FileData(JEVisObject obj) {
        this.fileObj = obj;

        try {
            JEVisAttribute file = obj.getAttribute(JC.File.a_File);
            if (file.hasSample()) {
                JEVisSample lastFile = file.getLatestSample();
                nameProperty.set(lastFile.getValueAsString());
                changeDateProperty.set(lastFile.getTimestamp());
                userProperty.set(lastFile.getNote());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    public FileData(String name, DateTime changeDateProperty, String userProperty) {
        this.nameProperty.set(name);
        this.changeDateProperty.set(changeDateProperty);
        this.userProperty.set(userProperty);
    }

    public JEVisObject getFileObj() {
        return fileObj;
    }

    public void saveFileDialog() throws JEVisException, IOException {
        FileChooser fileChooser = new FileChooser();
        //fileChooser.setTitle("Save Image");

        JEVisAttribute fileAtt = fileObj.getAttribute(JC.File.a_File);

        JEVisSample lastFile = fileAtt.getLatestSample();
        fileChooser.setInitialFileName(lastFile.getValueAsFile().getFilename());

        File file = fileChooser.showSaveDialog(JEConfig.getStage());
        if (file != null) {
            lastFile.getValueAsFile().saveToFile(file);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(I18n.getInstance().getString("plugin.action.savefile.title"));
            //alert.setHeaderText(I18n.getInstance().getString("plugin.action.savefile.header", selectedFile.getName()));
            alert.setHeaderText(null);
            alert.setContentText(I18n.getInstance().getString("plugin.action.savefile.message"));

            ButtonType openButton = new ButtonType(I18n.getInstance().getString("plugin.action.savefile.open"));
            ButtonType okButton = new ButtonType(I18n.getInstance().getString("plugin.action.savefile.ok"), ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(openButton, okButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == openButton) {
                Desktop desktop = Desktop.getDesktop();
                desktop.open(file);
            }

        }
    }
}
