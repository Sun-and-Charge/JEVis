/**
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
package org.jevis.jeconfig.application.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jevis.jeconfig.JEConfig;
import org.jevis.jeconfig.TopMenu;
import org.jevis.jeconfig.application.navigation.PluginSidebar;

import java.util.Optional;

/**
 * Base of the modal dialogs of the Control Center (J25.5 stage 5, docs/DESIGN-CC.md, after Grafana Saga "Modal"):
 * header with title and an optional one-line description, a scrollable body and an action bar that stays visible.
 * Exactly one primary button (Sun), the others secondary; destructive actions red; platform button order through
 * the ButtonBar. Escape and the close icon dismiss, with the "Änderungen verwerfen?" question while {@link #dirtyProperty()}
 * is set.
 */
public class SucDialog<R> extends Dialog<R> {

    /** S: confirmations, M: short forms, L: lists and tables */
    public enum Size {
        S(420), M(640), L(960);
        final double width;

        Size(double width) {
            this.width = width;
        }
    }

    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final Label description = new Label();

    public SucDialog(String title, String descriptionText, Size size) {
        /* owner is the focused window, so a dialog opened from another dialog lies above it and blocks it */
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst().orElse(JEConfig.getStage());
        if (owner != null) initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle(title);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getStyleClass().add("suc-dialog");
        pane.setPrefWidth(size.width);
        pane.setMinWidth(Math.min(size.width, 360));
        /* all actions at the right, the "other" actions (form primary, destructive) directly before OK and Abbrechen;
           the platform order would put them in the middle between two gaps */
        if (pane.lookup(".button-bar") instanceof ButtonBar) {
            ((ButtonBar) pane.lookup(".button-bar")).setButtonOrder("L_HE+FBXI_YNUOCA_R");
        }

        Label heading = new Label();
        heading.textProperty().bind(titleProperty());
        heading.getStyleClass().addAll("suc-heading", "suc-dialog-title");
        heading.setWrapText(true);
        description.getStyleClass().add("suc-dialog-description");
        description.setWrapText(true);
        /* old texts often repeat the title as header text; such a line says nothing and is hidden */
        setDescription(descriptionText != null && descriptionText.trim().equalsIgnoreCase(title == null ? "" : title.trim()) ? null : descriptionText);
        VBox header = new VBox(4, heading, description);
        header.getStyleClass().add("suc-dialog-header");
        pane.setHeader(header);

        Stage stage = (Stage) pane.getScene().getWindow();
        TopMenu.applyActiveTheme(pane.getScene());
        stage.setOnCloseRequest(event -> {
            if (!confirmDiscard()) event.consume();
        });
    }

    /** the body; long content scrolls while header and action bar stay visible */
    public void setBody(Node body) {
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("suc-dialog-body");
        getDialogPane().setContent(scroll);
    }

    /** the primary action (Sun, default button, Enter); label starts with a verb, e.g. "Speichern" */
    public Button addPrimary(ButtonType type) {
        getDialogPane().getButtonTypes().add(type);
        Button button = (Button) getDialogPane().lookupButton(type);
        button.setDefaultButton(true);
        return button;
    }

    /** the primary action with the given verb, e.g. "Berechnen" */
    public Button addPrimary(String verb) {
        return addPrimary(new ButtonType(verb, ButtonBar.ButtonData.OK_DONE));
    }

    /**
     * the primary action of a long form: Sun-coloured, but no default button (ButtonData.OTHER, placed left of "Abbrechen"), so Enter in a text
     * field of the form does not save
     */
    public ButtonType addFormPrimary(String verb) {
        ButtonType type = new ButtonType(verb, ButtonBar.ButtonData.OTHER);
        getDialogPane().getButtonTypes().add(type);
        Button button = (Button) getDialogPane().lookupButton(type);
        button.getStyleClass().add("primary");
        button.setDefaultButton(false);
        return type;
    }

    /** a window that only shows a result: title, optional description, content set by the caller, "Schließen" */
    public static SucDialog<ButtonType> result(String title, String descriptionText, Size size) {
        SucDialog<ButtonType> dialog = new SucDialog<>(title, descriptionText, size);
        ButtonType close = new ButtonType(text("dialog.ui.close", "Schließen", "Close"), ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        Button button = (Button) dialog.getDialogPane().lookupButton(close);
        button.setDefaultButton(true);
        button.setCancelButton(true);
        return dialog;
    }

    /** "Abbrechen" (Escape), with the unsaved-changes question */
    public Button addCancel() {
        ButtonType cancel = new ButtonType(text("dialog.ui.cancel", "Abbrechen", "Cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().add(cancel);
        Button button = (Button) getDialogPane().lookupButton(cancel);
        button.setCancelButton(true);
        button.addEventFilter(ActionEvent.ACTION, event -> {
            if (!confirmDiscard()) event.consume();
        });
        return button;
    }

    /** a destructive action, red and placed apart from the primary button */
    public Button addDestructive(ButtonType type) {
        getDialogPane().getButtonTypes().add(type);
        Button button = (Button) getDialogPane().lookupButton(type);
        button.getStyleClass().add("danger");
        button.setDefaultButton(false);
        return button;
    }

    /** the line below the title; empty or null hides it */
    public void setDescription(String text) {
        description.setText(text == null ? "" : text);
        description.setVisible(text != null && !text.isEmpty());
        description.setManaged(description.isVisible());
    }

    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    private boolean confirmDiscard() {
        if (!dirty.get()) return true;
        return Confirm.ask(text("dialog.ui.discard.title", "Änderungen verwerfen?", "Discard changes?"),
                text("dialog.ui.discard.text", "Die Änderungen in diesem Dialog gehen verloren.", "The changes in this dialog will be lost."),
                text("dialog.ui.discard.action", "Verwerfen", "Discard"), true);
    }

    public Optional<R> open() {
        return showAndWait();
    }

    static String text(String key, String german, String english) {
        return PluginSidebar.text(key, german, english);
    }
}
