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
package org.jevis.jeconfig.application.tools;

import javafx.geometry.Rectangle2D;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.jevis.jeconfig.JEConfig;

import java.util.List;

/**
 * Sizes windows and dialogs relative to the screen they are shown on.
 * <p>
 * Requested sizes are the pixel sizes that fit at 100 % Windows scaling (default font
 * {@value #BASE_FONT_SIZE}px). With Windows display scaling of 125 % JavaFX 8 keeps the
 * pixel scale at 1 but uses a bigger default font (15px), so fixed window sizes clip
 * their text. With a pixel scale (e.g. -Dglass.win.uiScale=1.25 or 150 %) the logical
 * screen gets smaller instead (1920x1080 becomes 1536x864), so fixed window sizes can be
 * larger than the screen. The helpers here grow the requested size with the default
 * font and limit it to a fraction of the visual bounds of the screen, set minimum sizes
 * and make the window resizable. At 100 % the requested size is used unchanged.
 */
public final class WindowSize {

    /**
     * Maximum part of the visual screen bounds a window may use.
     */
    public static final double MAX_SCREEN_FRACTION = 0.9;

    /**
     * Default minimum size as part of the requested size.
     */
    public static final double DEFAULT_MIN_FRACTION = 0.6;

    /**
     * Default font size in pixels at 100 % Windows scaling.
     */
    public static final double BASE_FONT_SIZE = 12d;

    private WindowSize() {
    }

    /**
     * Factor of the current default font size to the default font size at 100 % Windows
     * scaling: 1.0 at 100 %, 1.25 at 125 % (JavaFX 8). Never smaller than 1 and at most 2.
     */
    public static double fontScale() {
        double size = Font.getDefault().getSize();
        if (Double.isNaN(size) || size <= 0) return 1d;
        return Math.max(1d, Math.min(2d, size / BASE_FONT_SIZE));
    }

    /**
     * Pixel size at 100 % scaled with {@link #fontScale()}, not limited to the screen.
     */
    public static double scaled(double size) {
        return size * fontScale();
    }

    /**
     * Returns the visual bounds of the screen the given window is on. Falls back to the
     * main window of the Control Center and then to the primary screen.
     */
    public static Rectangle2D getVisualBounds(Window owner) {
        Rectangle2D bounds = boundsOf(owner);
        if (bounds == null) {
            bounds = boundsOf(JEConfig.getStage());
        }
        if (bounds == null) {
            bounds = Screen.getPrimary().getVisualBounds();
        }
        return bounds;
    }

    private static Rectangle2D boundsOf(Window window) {
        if (window == null) return null;
        double x = window.getX();
        double y = window.getY();
        double w = window.getWidth();
        double h = window.getHeight();
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(w) || Double.isNaN(h) || w <= 0 || h <= 0) {
            return null;
        }
        List<Screen> screens = Screen.getScreensForRectangle(x, y, w, h);
        if (screens.isEmpty()) return null;
        return screens.get(0).getVisualBounds();
    }

    /**
     * Requested width (pixels at 100 %) scaled with the font, but at most
     * {@link #MAX_SCREEN_FRACTION} of the screen width.
     */
    public static double fitWidth(Window owner, double requested) {
        return Math.min(scaled(requested), getVisualBounds(owner).getWidth() * MAX_SCREEN_FRACTION);
    }

    /**
     * Requested height (pixels at 100 %) scaled with the font, but at most
     * {@link #MAX_SCREEN_FRACTION} of the screen height.
     */
    public static double fitHeight(Window owner, double requested) {
        return Math.min(scaled(requested), getVisualBounds(owner).getHeight() * MAX_SCREEN_FRACTION);
    }

    /**
     * Height (pixels at 100 %) for a node inside a window, scaled with the font. Leaves
     * {@code reserved} pixels (at 100 %) of the usable screen height for the other parts
     * of the window (header, buttons, decoration).
     */
    public static double fitHeight(Window owner, double requested, double reserved) {
        double available = getVisualBounds(owner).getHeight() * MAX_SCREEN_FRACTION - scaled(reserved);
        return Math.max(Math.min(scaled(requested), available), Math.min(scaled(requested), 200d));
    }

    /**
     * Width (pixels at 100 %) for a node inside a window, scaled with the font. Leaves
     * {@code reserved} pixels (at 100 %) of the usable screen width for the other parts
     * of the window.
     */
    public static double fitWidth(Window owner, double requested, double reserved) {
        double available = getVisualBounds(owner).getWidth() * MAX_SCREEN_FRACTION - scaled(reserved);
        return Math.max(Math.min(scaled(requested), available), Math.min(scaled(requested), 200d));
    }

    /**
     * Sets the size of a stage relative to the screen with a default minimum size.
     */
    public static void apply(Stage stage, Window owner, double width, double height) {
        apply(stage, owner, width, height, width * DEFAULT_MIN_FRACTION, height * DEFAULT_MIN_FRACTION);
    }

    /**
     * Sets the size of a stage: the requested size if it fits on the screen, otherwise
     * {@link #MAX_SCREEN_FRACTION} of the screen. The minimum size is limited the same way.
     * The stage becomes resizable.
     */
    public static void apply(Stage stage, Window owner, double width, double height, double minWidth, double minHeight) {
        Window screenOwner = owner != null ? owner : stage.getOwner();
        double w = fitWidth(screenOwner, width);
        double h = fitHeight(screenOwner, height);
        stage.setMinWidth(Math.min(scaled(minWidth), w));
        stage.setMinHeight(Math.min(scaled(minHeight), h));
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setResizable(true);
    }

    /**
     * Sets only the width of a stage (relative to the screen); the height follows the
     * content. Use for message windows so text never gets clipped. Combine with
     * {@link #keepOnScreen(Window)}.
     */
    public static void applyWidth(Stage stage, Window owner, double width, double minWidth) {
        Window screenOwner = owner != null ? owner : stage.getOwner();
        double w = fitWidth(screenOwner, width);
        stage.setMinWidth(Math.min(scaled(minWidth), w));
        stage.setWidth(w);
    }

    /**
     * Sets the preferred size of a dialog relative to the screen and makes it resizable.
     */
    public static void apply(Dialog<?> dialog, double width, double height) {
        apply(dialog, width, height, width * DEFAULT_MIN_FRACTION, height * DEFAULT_MIN_FRACTION);
    }

    /**
     * Sets the preferred and minimum size of a dialog relative to the screen and makes it
     * resizable.
     */
    public static void apply(Dialog<?> dialog, double width, double height, double minWidth, double minHeight) {
        Window owner = dialog.getOwner();
        DialogPane pane = dialog.getDialogPane();
        double w = fitWidth(owner, width);
        double h = fitHeight(owner, height);
        pane.setMinSize(Math.min(scaled(minWidth), w), Math.min(scaled(minHeight), h));
        pane.setPrefSize(w, h);
        dialog.setResizable(true);
    }

    /**
     * Sets a minimum size of a dialog relative to the screen and makes it resizable. The
     * preferred size still comes from the content.
     */
    public static void applyMin(Dialog<?> dialog, double minWidth, double minHeight) {
        Window owner = dialog.getOwner();
        dialog.getDialogPane().setMinSize(fitWidth(owner, minWidth), fitHeight(owner, minHeight));
        dialog.setResizable(true);
        keepOnScreen(dialog);
    }

    /**
     * Limits a dialog whose size comes from its content to the screen once it is shown.
     */
    public static void keepOnScreen(Dialog<?> dialog) {
        if (dialog.getDialogPane().getScene() != null && dialog.getDialogPane().getScene().getWindow() != null) {
            keepOnScreen(dialog.getDialogPane().getScene().getWindow());
        }
    }

    /**
     * Limits a window whose size comes from its content to the screen once it is shown.
     */
    public static void keepOnScreen(Window window) {
        window.addEventHandler(WindowEvent.WINDOW_SHOWN, event -> {
            Window owner = window instanceof Stage && ((Stage) window).getOwner() != null ? ((Stage) window).getOwner() : window;
            Rectangle2D bounds = getVisualBounds(owner);
            boolean changed = false;
            if (window.getWidth() > bounds.getWidth()) {
                window.setWidth(bounds.getWidth() * MAX_SCREEN_FRACTION);
                changed = true;
            }
            if (window.getHeight() > bounds.getHeight()) {
                window.setHeight(bounds.getHeight() * MAX_SCREEN_FRACTION);
                changed = true;
            }
            if (changed) {
                window.centerOnScreen();
            }
            if (window.getY() < bounds.getMinY()) {
                window.setY(bounds.getMinY());
            }
            if (window.getX() < bounds.getMinX()) {
                window.setX(bounds.getMinX());
            }
        });
    }
}
