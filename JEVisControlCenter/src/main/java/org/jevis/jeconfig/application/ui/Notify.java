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

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jevis.jeconfig.JEConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Short notifications (toasts) at the bottom right of the main window instead of information alerts
 * (docs/DESIGN-CC.md): success and information disappear after a few seconds, errors stay until clicked.
 * An error says what went wrong and what to do.
 */
public final class Notify {

    private static final List<Popup> OPEN = new ArrayList<>();

    private Notify() {
    }

    public static void success(String text) {
        show(text, "success", Duration.seconds(4));
    }

    public static void info(String text) {
        show(text, "info", Duration.seconds(6));
    }

    /** stays until clicked */
    public static void error(String text) {
        show(text, "error", null);
    }

    private static void show(String text, String kind, Duration duration) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(text, kind, duration));
            return;
        }
        Window owner = JEConfig.getStage();
        if (owner == null) return;

        Label label = new Label(text);
        label.setWrapText(true);
        label.setMaxWidth(380);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().addAll("suc-toast", "suc-toast-" + kind);
        try {
            box.getStylesheets().addAll(owner.getScene().getStylesheets());
        } catch (Exception ignored) {
        }

        Popup popup = new Popup();
        popup.getContent().add(box);
        popup.setAutoHide(false);
        box.setOnMouseClicked(event -> close(popup));
        OPEN.add(popup);
        popup.show(owner);
        layout(owner);

        if (duration != null) {
            PauseTransition pause = new PauseTransition(duration);
            pause.setOnFinished(event -> close(popup));
            pause.play();
        }
    }

    private static void close(Popup popup) {
        popup.hide();
        OPEN.remove(popup);
        Window owner = JEConfig.getStage();
        if (owner != null) layout(owner);
    }

    /** stacks the open toasts upwards from the bottom right corner, above the status bar */
    private static void layout(Window owner) {
        double y = owner.getY() + owner.getHeight() - 48;
        for (int i = OPEN.size() - 1; i >= 0; i--) {
            Popup popup = OPEN.get(i);
            double height = popup.getContent().get(0).getLayoutBounds().getHeight();
            double width = popup.getContent().get(0).getLayoutBounds().getWidth();
            y -= height + 8;
            popup.setX(owner.getX() + owner.getWidth() - width - 24);
            popup.setY(y);
        }
    }
}
