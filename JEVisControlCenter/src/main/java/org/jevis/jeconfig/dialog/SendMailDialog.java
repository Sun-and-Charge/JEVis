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

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisObject;
import org.jevis.commons.i18n.I18n;
import org.jevis.commons.mail.MailRecipients;
import org.jevis.commons.utils.FileNames;
import org.jevis.commons.ws.json.JsonMailRequest;
import org.jevis.jeapi.ws.JEVisDataSourceWS;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.Chart.ChartPluginElements.TreeSelectionDialog;
import org.jevis.jeconfig.application.jevistree.UserSelection;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * "Send by e-mail" dialog (OpenJEVis #2234): sends an export of an analysis, a dashboard, ... as attachment through
 * the webservice, which uses the system mail settings. Recipients are e-mail addresses and JEVis users or groups.
 */
public class SendMailDialog {

    /**
     * Same limit as the webservice (MailRequestValidator), checked early to avoid a useless upload.
     */
    private static final long MAX_ATTACHMENT_BYTES = 15L * 1024 * 1024;
    private static final Logger logger = LogManager.getLogger(SendMailDialog.class);

    private final JEVisDataSource ds;
    private final String subject;
    private final String baseFileName;
    private final List<Format> formats;
    private final ObservableList<JEVisObject> selectedObjects = FXCollections.observableArrayList();

    /**
     * Creates the content of the attachment. Called on the JavaFX application thread, so snapshots of nodes work.
     */
    @FunctionalInterface
    public interface Exporter {
        byte[] export() throws Exception;
    }

    /**
     * An attachment format the source offers.
     */
    public static class Format {
        private final String name;
        private final String extension;
        private final String contentType;
        private final Exporter exporter;

        public Format(String name, String extension, String contentType, Exporter exporter) {
            this.name = name;
            this.extension = extension;
            this.contentType = contentType;
            this.exporter = exporter;
        }

        public static Format png(Exporter exporter) {
            return new Format(I18n.getInstance().getString("dialog.sendmail.format.png"), "png", "image/png", exporter);
        }

        public static Format pdf(Exporter exporter) {
            return new Format(I18n.getInstance().getString("dialog.sendmail.format.pdf"), "pdf", "application/pdf", exporter);
        }

        public static Format xlsx(Exporter exporter) {
            return new Format(I18n.getInstance().getString("dialog.sendmail.format.xlsx"), "xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", exporter);
        }

        public static Format csv(Exporter exporter) {
            return new Format(I18n.getInstance().getString("dialog.sendmail.format.csv"), "csv", "text/csv", exporter);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * @param subject      prefilled subject, e.g. the name of the analysis
     * @param baseFileName file name of the attachment without extension
     * @param formats      the formats the source offers, the first one is preselected
     */
    public SendMailDialog(JEVisDataSource ds, String subject, String baseFileName, List<Format> formats) {
        this.ds = ds;
        this.subject = subject;
        this.baseFileName = baseFileName;
        this.formats = formats;
    }

    private static String t(String key) {
        return I18n.getInstance().getString(key);
    }

    public void show() {
        if (!(ds instanceof JEVisDataSourceWS)) {
            Alert alert = new Alert(Alert.AlertType.WARNING, t("dialog.sendmail.notsupported"), ButtonType.OK);
            alert.initOwner(JEConfig.getStage());
            alert.showAndWait();
            return;
        }

        TextField toField = new TextField();
        toField.setPromptText(t("dialog.sendmail.to.prompt"));
        Button selectButton = new Button(t("dialog.sendmail.select"));
        selectButton.setOnAction(event -> selectUsersAndGroups());
        HBox toBox = new HBox(6, toField, selectButton);
        HBox.setHgrow(toField, Priority.ALWAYS);

        ListView<JEVisObject> objectList = new ListView<>(selectedObjects);
        objectList.setPrefHeight(80);
        objectList.setCellFactory(param -> new ListCell<JEVisObject>() {
            @Override
            protected void updateItem(JEVisObject item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String className = "";
                    try {
                        className = " (" + I18n.getInstance().getString(item.getJEVisClassName().equals("Group") ? "dialog.sendmail.group" : "dialog.sendmail.user") + ")";
                    } catch (Exception ignored) {
                    }
                    setText(item.getName() + className);
                }
            }
        });
        Button removeButton = new Button(t("dialog.sendmail.remove"));
        removeButton.disableProperty().bind(objectList.getSelectionModel().selectedItemProperty().isNull());
        removeButton.setOnAction(event -> selectedObjects.remove(objectList.getSelectionModel().getSelectedItem()));
        HBox objectBox = new HBox(6, objectList, removeButton);
        HBox.setHgrow(objectList, Priority.ALWAYS);

        TextField subjectField = new TextField(subject);
        TextArea textArea = new TextArea();
        textArea.setPrefRowCount(6);
        textArea.setWrapText(true);
        ComboBox<Format> formatBox = new ComboBox<>(FXCollections.observableArrayList(formats));
        formatBox.getSelectionModel().selectFirst();
        Label status = new Label();
        status.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.addRow(0, new Label(t("dialog.sendmail.to")), toBox);
        grid.add(objectBox, 1, 1);
        grid.addRow(2, new Label(t("dialog.sendmail.subject")), subjectField);
        grid.addRow(3, new Label(t("dialog.sendmail.text")), textArea);
        grid.addRow(4, new Label(t("dialog.sendmail.format")), formatBox);
        GridPane.setHgrow(toBox, Priority.ALWAYS);
        GridPane.setHgrow(subjectField, Priority.ALWAYS);
        GridPane.setVgrow(textArea, Priority.ALWAYS);

        VBox content = new VBox(10, grid, status);
        content.setPadding(new Insets(10));
        content.setPrefWidth(600);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(JEConfig.getStage());
        dialog.setTitle(t("dialog.sendmail.title"));
        dialog.setHeaderText(t("dialog.sendmail.title"));
        dialog.setResizable(true);
        Stage stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        TopMenu.applyActiveTheme(stage.getScene());
        ButtonType send = new ButtonType(t("dialog.sendmail.send"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(send, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(content);

        Button sendButton = (Button) dialog.getDialogPane().lookupButton(send);
        sendButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            List<String> addresses = MailRecipients.parseAddresses(toField.getText());
            if (addresses.isEmpty() && selectedObjects.isEmpty()) {
                status.setText(t("dialog.sendmail.norecipients"));
                return;
            }
            Format format = formatBox.getValue();
            JsonMailRequest request = new JsonMailRequest();
            request.setTo(addresses);
            for (JEVisObject object : selectedObjects) {
                try {
                    if ("Group".equals(object.getJEVisClassName())) {
                        request.getGroupIds().add(object.getID());
                    } else {
                        request.getUserIds().add(object.getID());
                    }
                } catch (Exception ex) {
                    logger.warn("Could not read class of {}", object, ex);
                }
            }
            request.setSubject(subjectField.getText());
            request.setText(textArea.getText());

            byte[] attachment;
            try {
                /* on the FX thread, snapshots need it */
                attachment = format != null ? format.exporter.export() : null;
            } catch (Exception ex) {
                logger.error("Export for mail failed", ex);
                status.setText(t("dialog.sendmail.error") + " " + ex.getMessage());
                return;
            }
            if (attachment != null) {
                if (attachment.length > MAX_ATTACHMENT_BYTES) {
                    status.setText(t("dialog.sendmail.toolarge"));
                    return;
                }
                request.getAttachments().add(new JsonMailRequest.Attachment(
                        FileNames.fixName(baseFileName) + "." + format.extension, format.contentType,
                        Base64.getEncoder().encodeToString(attachment)));
            }

            dialog.getDialogPane().setDisable(true);
            status.setText(t("dialog.sendmail.sending"));
            Thread thread = new Thread(() -> {
                try {
                    int recipients = ((JEVisDataSourceWS) ds).sendMail(request);
                    Platform.runLater(() -> {
                        dialog.setResult(send);
                        dialog.close();
                        Alert info = new Alert(Alert.AlertType.INFORMATION, I18n.getInstance().getString("dialog.sendmail.success", recipients), ButtonType.OK);
                        info.initOwner(JEConfig.getStage());
                        info.setHeaderText(null);
                        info.setTitle(t("dialog.sendmail.title"));
                        info.show();
                    });
                } catch (Exception ex) {
                    logger.error("Sending mail failed", ex);
                    Platform.runLater(() -> {
                        dialog.getDialogPane().setDisable(false);
                        status.setText(t("dialog.sendmail.error") + " " + ex.getMessage());
                    });
                }
            }, "send-mail");
            thread.setDaemon(true);
            thread.start();
        });
        Platform.runLater(toField::requestFocus);
        dialog.show();
    }

    private void selectUsersAndGroups() {
        try {
            List<JEVisClass> classes = new ArrayList<>();
            JEVisClass userClass = ds.getJEVisClass("User");
            JEVisClass groupClass = ds.getJEVisClass("Group");
            if (userClass != null) classes.add(userClass);
            if (groupClass != null) classes.add(groupClass);
            TreeSelectionDialog selectionDialog = new TreeSelectionDialog(ds, classes, SelectionMode.MULTIPLE);
            selectionDialog.showAndWait();
            if (selectionDialog.getResponse() == Response.OK) {
                for (UserSelection selection : selectionDialog.getUserSelection()) {
                    JEVisObject object = selection.getSelectedObject();
                    if (object != null && !selectedObjects.contains(object)) {
                        selectedObjects.add(object);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("Could not select users or groups", ex);
        }
    }
}
