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
package org.jevis.jeconfig.connectionencoder;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPasswordField;
import com.jfoenix.controls.JFXTextField;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyEvent;
import org.jevis.commons.cli.ConnectionEncoder;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * FXML Controller class
 *
 * @author fs
 */
public class ConnectionController implements Initializable {

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        onChanged(null);
    }

    @FXML
    private JFXTextField host;

    @FXML
    private JFXTextField port;

    @FXML
    private JFXTextField result;

    @FXML
    private JFXTextField schema;

    @FXML
    private JFXTextField user;

    @FXML
    private JFXPasswordField passwd;

    @FXML
    private JFXButton CopyCon;

    @FXML
    void onChanged(KeyEvent event) {
        String hostS = host.getText().isEmpty() ? host.getPromptText() : host.getText();
        String portS = port.getText().isEmpty() ? port.getPromptText() : port.getText();
        String schemaS = schema.getText().isEmpty() ? schema.getPromptText() : schema.getText();
        String userS = user.getText().isEmpty() ? user.getPromptText() : user.getText();
        String pwS = passwd.getText().isEmpty() ? passwd.getPromptText() : passwd.getText();

        result.setText(ConnectionEncoder.encode(hostS, portS, schemaS, userS, pwS));
    }

    @FXML
    void onCopy(ActionEvent event) {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(result.getText());
        clipboard.setContent(content);
    }

}
