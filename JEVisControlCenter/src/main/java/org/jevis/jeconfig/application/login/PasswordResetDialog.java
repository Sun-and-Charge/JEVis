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
package org.jevis.jeconfig.application.login;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.i18n.I18n;
import org.jevis.jeapi.ws.JEVisDataSourceWS;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * "Forgot password" dialogs of the login (OpenJEVis #2267): step 1 requests a code by mail, step 2 sets a new
 * password with the code. Must be called on the JavaFX application thread.
 */
public class PasswordResetDialog {

    private static final Logger logger = LogManager.getLogger(PasswordResetDialog.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final Window owner;
    private final JEVisDataSourceWS ds;

    public PasswordResetDialog(Window owner, JEVisDataSourceWS ds) {
        this.owner = owner;
        this.ds = ds;
    }

    private static String t(String key) {
        return I18n.getInstance().getString(key);
    }

    /**
     * Shows the dialogs.
     *
     * @param login prefilled user name, may be empty
     * @return the login if the password was changed, otherwise {@code null}
     */
    public String show(String login) {
        TextField loginField = new TextField(login != null ? login : "");
        loginField.setPromptText(t("app.login.forgot.login"));
        Label info = wrapLabel(t("app.login.forgot.step1"));
        Label status = wrapLabel("");

        ButtonType request = new ButtonType(t("app.login.forgot.request"), ButtonBar.ButtonData.OK_DONE);
        ButtonType haveCode = new ButtonType(t("app.login.forgot.havecode"), ButtonBar.ButtonData.NEXT_FORWARD);
        Dialog<ButtonType> dialog = createDialog(new VBox(8, info, loginField, status), request, haveCode, ButtonType.CANCEL);

        Button requestButton = (Button) dialog.getDialogPane().lookupButton(request);
        requestButton.disableProperty().bind(loginField.textProperty().isEmpty());
        dialog.getDialogPane().lookupButton(haveCode).disableProperty().bind(loginField.textProperty().isEmpty());
        requestButton.addEventFilter(ActionEvent.ACTION, event -> {
            /* stay open until the answer arrives, then continue with the code */
            event.consume();
            String value = loginField.getText().trim();
            runInBackground(dialog, status, () -> ds.requestPasswordReset(value), answer -> {
                dialog.setResult(haveCode);
                dialog.close();
            });
        });
        Platform.runLater(loginField::requestFocus);

        ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (result != haveCode && result != request) return null;
        return showCodeDialog(loginField.getText().trim(), dialog.getDialogPane().getProperties().containsKey("sent"));
    }

    private String showCodeDialog(String login, boolean sent) {
        Label info = wrapLabel((sent ? t("app.login.forgot.sent") + "\n\n" : "") + t("app.login.forgot.step2"));
        TextField code = new TextField();
        code.setPromptText("123456");
        PasswordField password = new PasswordField();
        PasswordField repeat = new PasswordField();
        Label status = wrapLabel("");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(t("app.login.forgot.code")), code);
        grid.addRow(1, new Label(t("app.login.forgot.newpassword")), password);
        grid.addRow(2, new Label(t("app.login.forgot.repeat")), repeat);
        GridPane.setHgrow(code, Priority.ALWAYS);

        ButtonType set = new ButtonType(t("app.login.forgot.set"), ButtonBar.ButtonData.OK_DONE);
        Dialog<ButtonType> dialog = createDialog(new VBox(10, info, grid, status), set, ButtonType.CANCEL);
        Button setButton = (Button) dialog.getDialogPane().lookupButton(set);
        setButton.disableProperty().bind(code.textProperty().isEmpty().or(password.textProperty().isEmpty()));
        setButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (password.getText().length() < MIN_PASSWORD_LENGTH) {
                status.setText(t("app.login.forgot.tooshort"));
                return;
            }
            if (!password.getText().equals(repeat.getText())) {
                status.setText(t("app.login.forgot.mismatch"));
                return;
            }
            String codeValue = code.getText().trim();
            String newPassword = password.getText();
            runInBackground(dialog, status, () -> ds.resetPassword(login, codeValue, newPassword), error -> {
                if (error == null) {
                    dialog.setResult(set);
                    dialog.close();
                } else {
                    status.setText(t("app.login.forgot.invalid"));
                }
            });
        });
        Platform.runLater(code::requestFocus);

        if (dialog.showAndWait().orElse(ButtonType.CANCEL) != set) return null;
        Alert done = new Alert(Alert.AlertType.INFORMATION, t("app.login.forgot.success"), ButtonType.OK);
        done.initOwner(owner);
        done.setTitle(t("app.login.forgot.title"));
        done.setHeaderText(null);
        done.showAndWait();
        return login;
    }

    private Dialog<ButtonType> createDialog(VBox content, ButtonType... buttons) {
        Dialog<ButtonType> dialog = new Dialog<>();
        if (owner != null) dialog.initOwner(owner);
        dialog.setTitle(t("app.login.forgot.title"));
        dialog.setHeaderText(t("app.login.forgot.title"));
        dialog.setResizable(true);
        content.setPadding(new Insets(10));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(buttons);
        return dialog;
    }

    private static Label wrapLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(440);
        return label;
    }

    /**
     * Runs a webservice call outside of the FX thread, disables the dialog meanwhile and shows errors in the status
     * label.
     */
    private <T> void runInBackground(Dialog<ButtonType> dialog, Label status, Callable<T> call, Consumer<T> onSuccess) {
        dialog.getDialogPane().setDisable(true);
        status.setText("");
        Thread thread = new Thread(() -> {
            try {
                T result = call.call();
                Platform.runLater(() -> {
                    dialog.getDialogPane().setDisable(false);
                    dialog.getDialogPane().getProperties().put("sent", Boolean.TRUE);
                    onSuccess.accept(result);
                });
            } catch (Exception ex) {
                logger.error("Password reset request failed", ex);
                Platform.runLater(() -> {
                    dialog.getDialogPane().setDisable(false);
                    status.setText(t("app.login.forgot.error") + " " + ex.getMessage());
                });
            }
        }, "password-reset");
        thread.setDaemon(true);
        thread.start();
    }
}
