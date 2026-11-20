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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.BooleanSupplier;

/**
 * The editor drawer on the right of the plugin area (docs/DESIGN-CC.md: editors with more than five fields open in a
 * side panel, the content behind stays visible). One panel at a time: header (title, subtitle, close), scrollable
 * body, footer with "Speichern" and "Abbrechen". Escape, the close icon, "Abbrechen", opening another editor and
 * switching the module ask before unsaved changes are lost.
 */
public final class SidePanel extends BorderPane {

    private static final SidePanel INSTANCE = new SidePanel();

    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final ScrollPane scroll = new ScrollPane();
    private final Button save = new Button(SucDialog.text("dialog.ui.save", "Speichern", "Save"));
    private final Button cancel = new Button(SucDialog.text("dialog.ui.cancel", "Abbrechen", "Cancel"));
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private BooleanSupplier onSave;
    private Runnable onClosed;

    private SidePanel() {
        getStyleClass().add("suc-side-panel");
        setPrefWidth(640);
        setMinWidth(420);
        setMaxWidth(760);

        title.getStyleClass().addAll("suc-heading", "suc-side-panel-title");
        title.setWrapText(true);
        subtitle.getStyleClass().add("suc-side-panel-subtitle");
        subtitle.setWrapText(true);
        Button close = new Button("✕");
        close.getStyleClass().addAll("flat", "suc-side-panel-close");
        close.setFocusTraversable(false);
        close.setOnAction(event -> requestClose());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(8, new VBox(2, title, subtitle), spacer, close);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.getStyleClass().add("suc-side-panel-header");
        setTop(titleRow);

        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("suc-side-panel-body");
        setCenter(scroll);

        save.getStyleClass().add("primary");
        save.setOnAction(event -> {
            if (onSave == null || onSave.getAsBoolean()) {
                resetDirty();
                close();
            }
        });
        cancel.setOnAction(event -> requestClose());
        Region footSpacer = new Region();
        HBox.setHgrow(footSpacer, Priority.ALWAYS);
        HBox footer = new HBox(8, footSpacer, save, cancel);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("suc-side-panel-footer");
        footer.setPadding(new Insets(10, 20, 14, 20));
        setBottom(footer);

        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                requestClose();
                event.consume();
            }
        });
        setVisible(false);
        setManaged(false);
    }

    private void resetDirty() {
        /* editors may bind the flag to their own change property */
        if (dirty.isBound()) dirty.unbind();
        dirty.set(false);
    }

    public static SidePanel get() {
        return INSTANCE;
    }

    /**
     * Shows an editor; asks first when the open editor has unsaved changes.
     *
     * @param onSave saves the changes; true closes the panel, false keeps it open (e.g. after a validation error)
     * @return false when the user kept the open editor
     */
    public boolean show(String titleText, String subtitleText, Node body, BooleanSupplier onSave) {
        if (isOpen() && !confirmDiscard()) return false;
        if (isOpen()) close();
        title.setText(titleText);
        subtitle.setText(subtitleText == null ? "" : subtitleText);
        subtitle.setVisible(subtitleText != null && !subtitleText.isEmpty());
        subtitle.setManaged(subtitle.isVisible());
        scroll.setContent(body);
        scroll.setVvalue(0);
        this.onSave = onSave;
        save.setDisable(false);
        save.setVisible(true);
        save.setManaged(true);
        resetDirty();
        setVisible(true);
        setManaged(true);
        requestFocus();
        return true;
    }

    /** read-only editors (no write right): no "Speichern", "Abbrechen" closes */
    public void setReadOnly(boolean readOnly) {
        save.setVisible(!readOnly);
        save.setManaged(!readOnly);
    }

    /** closes after asking about unsaved changes; false when the user kept the editor */
    public boolean requestClose() {
        if (!isOpen()) return true;
        if (!confirmDiscard()) return false;
        close();
        return true;
    }

    /** closes without asking */
    public void close() {
        setVisible(false);
        setManaged(false);
        scroll.setContent(null);
        resetDirty();
        onSave = null;
        Runnable closed = onClosed;
        onClosed = null;
        if (closed != null) closed.run();
    }

    public boolean isOpen() {
        return isVisible();
    }

    /** set by the editor on every change */
    public BooleanProperty dirtyProperty() {
        return dirty;
    }

    /** runs once when the panel closes (saved, cancelled or replaced) */
    public void setOnClosed(Runnable onClosed) {
        this.onClosed = onClosed;
    }

    private boolean confirmDiscard() {
        if (!dirty.get()) return true;
        return Confirm.ask(SucDialog.text("dialog.ui.discard.title", "Änderungen verwerfen?", "Discard changes?"),
                SucDialog.text("dialog.ui.discard.panel", "Die Änderungen in diesem Editor gehen verloren.", "The changes in this editor will be lost."),
                SucDialog.text("dialog.ui.discard.action", "Verwerfen", "Discard"), true);
    }
}
