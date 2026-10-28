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
package org.jevis.jeconfig.dialog;

import com.jfoenix.controls.JFXTextArea;
import javafx.scene.control.Alert;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Window;
import org.jevis.commons.i18n.I18n;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionDialog2 {

    public static void showException(Window parent, Exception ex) {

        String header = ex.toString();
        String cause = ex.toString();
        if (ex.getCause() != null) {
            cause = ex.getCause().getMessage();
        }

        showException(parent,
                I18n.getInstance().getString("dialog.error.title"),
                header, cause, ex);
    }

    public static void showException(Window parent, String title, String header, String content, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);


        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        String exceptionText = sw.toString();
//        Label label = new Label("The exception stacktrace was:");

        JFXTextArea textArea = new JFXTextArea(exceptionText);
        textArea.setEditable(false);
        textArea.setWrapText(true);

        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);
        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);

        GridPane expContent = new GridPane();
        expContent.setMaxWidth(Double.MAX_VALUE);
//        expContent.add(label, 0, 0);
        expContent.add(textArea, 0, 1);

// Set expandable Exception into the dialog pane.
        alert.getDialogPane().setExpandableContent(expContent);
        alert.initOwner(parent);
        alert.setWidth(500);
        alert.setHeight(500);
        alert.showAndWait();
    }

}
