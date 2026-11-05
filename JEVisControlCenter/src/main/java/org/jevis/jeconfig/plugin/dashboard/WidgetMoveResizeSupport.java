/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JEVisControlCenter.
 *
 * JEVisControlCenter is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 *
 * JEVisControlCenter is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.dashboard;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Point2D;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.jeconfig.plugin.dashboard.widget.Widget;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * Lets the user move and resize a dashboard {@link Widget} with the mouse while the dashboard is in edit mode.
 * <ul>
 *     <li>Dragging one of the borders or corners (a band of {@link WidgetHandle#DEFAULT_BAND} pixels) resizes the
 *     widget, the opposite borders stay in place.</li>
 *     <li>Dragging the inner area with the Ctrl key held moves the widget. Without Ctrl the inner area is left to
 *     the other mouse handling of the widget (selection, context menu).</li>
 *     <li>If snap to grid is on, the moved borders are aligned to the dashboard grid, with Shift to the fine grid.</li>
 * </ul>
 * Each widget gets its own instance, the state of a drag is not shared between widgets.
 * The geometry is done by {@link WidgetHandle} and {@link WidgetFrame}.
 */
public class WidgetMoveResizeSupport {

    private static final Logger logger = LogManager.getLogger(WidgetMoveResizeSupport.class);

    private final Widget widget;
    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", false);

    /* state of the running drag gesture, activeHandle is NONE if there is none */
    private WidgetHandle activeHandle = WidgetHandle.NONE;
    private WidgetFrame startFrame;
    private WidgetFrame appliedFrame;
    private double pressX;
    private double pressY;
    private boolean movedInGesture = false;
    private boolean suppressNextClick = false;
    private boolean installed = false;

    public WidgetMoveResizeSupport(Widget widget) {
        this.widget = widget;
        enabled.addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                endGesture();
                widget.setCursor(null);
            }
        });
    }

    /**
     * Registers the mouse handlers on the widget. Uses event handlers and filters that are added to the existing
     * ones, so the other mouse handling of the widget keeps working. Calling it more than once has no effect.
     */
    public void install() {
        if (installed) {
            return;
        }
        installed = true;
        widget.addEventHandler(MouseEvent.MOUSE_MOVED, this::onMouseMoved);
        widget.addEventHandler(MouseEvent.MOUSE_EXITED, this::onMouseExited);
        widget.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        widget.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::onMouseDragged);
        widget.addEventHandler(MouseEvent.MOUSE_RELEASED, this::onMouseReleased);
        /* a filter, so the selection toggle (Ctrl+click) of the widget does not run after a Ctrl+drag */
        widget.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
    }

    /**
     * Moving and resizing is only possible while this is true (dashboard in edit mode).
     */
    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    private void onMouseMoved(MouseEvent event) {
        if (!isEnabled()) {
            return;
        }
        widget.setCursor(cursorFor(handleAt(event)));
    }

    private void onMouseExited(MouseEvent event) {
        if (activeHandle == WidgetHandle.NONE) {
            widget.setCursor(null);
        }
    }

    private void onMousePressed(MouseEvent event) {
        suppressNextClick = false;
        if (!isEnabled() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        WidgetHandle handle = handleAt(event);
        Point2D pointer = pointerInParent(event);
        if (handle == WidgetHandle.NONE || pointer == null) {
            return;
        }

        activeHandle = handle;
        pressX = pointer.getX();
        pressY = pointer.getY();
        startFrame = currentFrame();
        appliedFrame = startFrame;
        movedInGesture = false;
        if (handle.isResize()) {
            widget.setCursor(cursorFor(handle));
        }
    }

    private void onMouseDragged(MouseEvent event) {
        if (activeHandle == WidgetHandle.NONE || !isEnabled()) {
            return;
        }
        Point2D pointer = pointerInParent(event);
        if (pointer == null) {
            return;
        }
        double dx = pointer.getX() - pressX;
        double dy = pointer.getY() - pressY;

        DashBoardPane pane = dashBoardPane();
        boolean snap = isSnapToGrid(pane);
        boolean fine = event.isShiftDown();

        WidgetFrame target;
        if (activeHandle.isResize()) {
            target = startFrame.resize(activeHandle, dx, dy, WidgetFrame.MIN_SIZE, WidgetFrame.MIN_SIZE);
            if (snap) {
                target = target.snapBorders(activeHandle, snapX(pane, fine), snapY(pane, fine),
                        WidgetFrame.MIN_SIZE, WidgetFrame.MIN_SIZE);
            }
        } else if (event.isControlDown()) {
            target = startFrame.translate(dx, dy);
            if (snap) {
                target = target.snapPosition(snapX(pane, fine), snapY(pane, fine));
            }
            movedInGesture = true;
        } else {
            return;
        }

        apply(target);
        event.consume();
    }

    private void onMouseReleased(MouseEvent event) {
        if (activeHandle == WidgetHandle.NONE) {
            return;
        }
        suppressNextClick = movedInGesture;
        endGesture();
        widget.setCursor(null);
    }

    private void onMouseClicked(MouseEvent event) {
        if (suppressNextClick && !event.isStillSincePress()) {
            event.consume();
        }
        suppressNextClick = false;
    }

    private void endGesture() {
        activeHandle = WidgetHandle.NONE;
        startFrame = null;
        appliedFrame = null;
        movedInGesture = false;
    }

    private void apply(WidgetFrame target) {
        if (target.equals(appliedFrame)) {
            return;
        }
        if (target.getX() != widget.getLayoutX()) {
            widget.setLayoutX(target.getX());
        }
        if (target.getY() != widget.getLayoutY()) {
            widget.setLayoutY(target.getY());
        }
        if (!target.hasSameSize(appliedFrame)) {
            widget.setNodeSize(target.getWidth(), target.getHeight());
        }
        appliedFrame = target;
    }

    private WidgetFrame currentFrame() {
        double width = widget.getWidth();
        double height = widget.getHeight();
        if ((width <= 0 || height <= 0) && widget.getConfig() != null && widget.getConfig().getSize() != null) {
            width = widget.getConfig().getSize().getWidth();
            height = widget.getConfig().getSize().getHeight();
        }
        return new WidgetFrame(widget.getLayoutX(), widget.getLayoutY(), width, height);
    }

    private WidgetHandle handleAt(MouseEvent event) {
        /* the handlers are registered on the widget, so the event coordinates are local to it */
        return WidgetHandle.at(event.getX(), event.getY(), widget.getWidth(), widget.getHeight(), WidgetHandle.DEFAULT_BAND);
    }

    /**
     * @return the mouse position in the coordinate system of the dashboard (the widget's parent), which takes
     * the zoom of the dashboard into account
     */
    private Point2D pointerInParent(MouseEvent event) {
        Parent parent = widget.getParent();
        if (parent == null) {
            return null;
        }
        return parent.sceneToLocal(event.getSceneX(), event.getSceneY());
    }

    private DashBoardPane dashBoardPane() {
        if (widget.getParent() instanceof DashBoardPane) {
            return (DashBoardPane) widget.getParent();
        }
        return widget.getControl() != null ? widget.getControl().getDashboardPane() : null;
    }

    private boolean isSnapToGrid(DashBoardPane pane) {
        try {
            return pane != null && widget.getControl() != null && widget.getControl().snapToGridProperty.get();
        } catch (Exception ex) {
            logger.debug("Could not read snap to grid setting", ex);
            return false;
        }
    }

    private static DoubleUnaryOperator snapX(DashBoardPane pane, boolean fine) {
        List<Double> grid = fine ? pane.xGridsFine : pane.xGrids;
        return value -> pane.getNextGridX(value, grid);
    }

    private static DoubleUnaryOperator snapY(DashBoardPane pane, boolean fine) {
        List<Double> grid = fine ? pane.yGridsFine : pane.yGrids;
        return value -> pane.getNextGridY(value, grid);
    }

    private static Cursor cursorFor(WidgetHandle handle) {
        switch (handle) {
            case NORTH_WEST:
                return Cursor.NW_RESIZE;
            case NORTH:
                return Cursor.N_RESIZE;
            case NORTH_EAST:
                return Cursor.NE_RESIZE;
            case EAST:
                return Cursor.E_RESIZE;
            case SOUTH_EAST:
                return Cursor.SE_RESIZE;
            case SOUTH:
                return Cursor.S_RESIZE;
            case SOUTH_WEST:
                return Cursor.SW_RESIZE;
            case WEST:
                return Cursor.W_RESIZE;
            case INSIDE:
                return Cursor.DEFAULT;
            default:
                return null;
        }
    }
}
