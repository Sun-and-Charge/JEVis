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
package org.jevis.jeconfig.application.table;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Column menu for a {@link TableView}: lets the user show and hide the columns of the table.
 * <p>
 * The menu replaces the built-in menu of the "+" button JavaFX shows at the right end of the table header when
 * {@link TableView#tableMenuButtonVisibleProperty()} is true (the button itself stays). It also opens on a right
 * click on the table header and with Ctrl+Shift+Space while the table has the focus. It contains entries to show
 * and to hide all columns, one check box per column (the menu stays open while columns are toggled) and the
 * {@link #getAdditionalItems() additional items} of the caller.
 * <p>
 * The table skin may be created later (e.g. when the table is shown the first time), the menu then attaches itself
 * to the header as soon as it exists.
 *
 * <pre>
 *     TableColumnMenu columnMenu = TableColumnMenu.install(tableView);
 * </pre>
 */
public class TableColumnMenu {

    private static final Logger logger = LogManager.getLogger(TableColumnMenu.class);
    private static final String PROPERTY_KEY = TableColumnMenu.class.getName();
    private static final String HEADER_STYLE_CLASS = "column-header-background";
    private static final String MENU_BUTTON_SELECTOR = ".show-hide-columns-button";
    private static final KeyCombination SHORTCUT = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    /**
     * A click that closes the menu by auto hide must not open it again right away.
     */
    private static final long REOPEN_DELAY_MS = 300;
    private static final double INDENT = 12d;

    private final TableView<?> tableView;
    private final ContextMenu contextMenu = new ContextMenu();
    private final ObservableList<MenuItem> additionalItems = FXCollections.observableArrayList();
    /* undoes the bindings between the check boxes and the columns when the menu is closed */
    private final List<Runnable> releaseBindings = new ArrayList<>();
    private long hiddenAt = 0;

    private TableColumnMenu(TableView<?> tableView) {
        this.tableView = tableView;

        contextMenu.setOnHidden(event -> {
            hiddenAt = System.currentTimeMillis();
            releaseBindings();
        });

        tableView.skinProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                attachToHeader();
                /* some skins add the header children later */
                Platform.runLater(this::attachToHeader);
            }
        });
        tableView.tableMenuButtonVisibleProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(this::attachToHeader));
        tableView.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (SHORTCUT.match(event)) {
                toggle();
                event.consume();
            }
        });

        if (tableView.getSkin() != null) {
            attachToHeader();
        }
    }

    /**
     * Installs the column menu on the table. Installing it twice returns the existing menu.
     *
     * @param tableView the table, its skin does not need to exist yet
     * @return the column menu of the table
     */
    public static TableColumnMenu install(TableView<?> tableView) {
        Object existing = tableView.getProperties().get(PROPERTY_KEY);
        if (existing instanceof TableColumnMenu) {
            return (TableColumnMenu) existing;
        }
        TableColumnMenu columnMenu = new TableColumnMenu(tableView);
        tableView.getProperties().put(PROPERTY_KEY, columnMenu);
        return columnMenu;
    }

    /**
     * @return menu items appended after the column entries (separated by a separator); may be changed at any time
     */
    public ObservableList<MenuItem> getAdditionalItems() {
        return additionalItems;
    }

    public boolean isShowing() {
        return contextMenu.isShowing();
    }

    /**
     * Opens the menu below the "+" button of the table header, or below the header if there is no button.
     * Does nothing if the menu is already open or the table is not shown.
     */
    public void show() {
        if (contextMenu.isShowing()) {
            return;
        }

        Node header = findHeader();
        Node button = findMenuButton(header);
        Node anchor;
        if (isShown(button)) {
            anchor = button;
        } else if (isShown(header)) {
            anchor = header;
        } else {
            anchor = tableView;
        }
        if (anchor.getScene() == null || anchor.getScene().getWindow() == null) {
            return;
        }

        rebuildItems();
        contextMenu.show(anchor, Side.BOTTOM, 0, 0);

        if (anchor == button) {
            /* the button is at the right end of the table: align the right borders of menu and button */
            Bounds buttonBounds = button.localToScreen(button.getLayoutBounds());
            if (buttonBounds != null && contextMenu.getWidth() > 0) {
                contextMenu.setX(buttonBounds.getMaxX() - contextMenu.getWidth());
            }
        }
    }

    public void hide() {
        contextMenu.hide();
    }

    /**
     * Closes the menu if it is open, opens it otherwise.
     */
    public void toggle() {
        if (contextMenu.isShowing()) {
            contextMenu.hide();
        } else if (System.currentTimeMillis() - hiddenAt > REOPEN_DELAY_MS) {
            show();
        }
    }

    private void attachToHeader() {
        try {
            Node header = findHeader();
            if (header != null && markAttached(header)) {
                header.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        toggle();
                        event.consume();
                    }
                });
                /* the click belongs to the press above, other click handlers of the table must not see it */
                header.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                    if (event.getButton() == MouseButton.SECONDARY) {
                        event.consume();
                    }
                });
            }

            Node button = findMenuButton(header);
            if (button != null && markAttached(button)) {
                /* a filter, so the built-in column menu of the button does not open */
                button.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
                    event.consume();
                    if (event.getButton() == MouseButton.PRIMARY) {
                        toggle();
                    }
                });
            }
        } catch (Exception ex) {
            logger.warn("Could not attach the column menu to the table header: {}", ex.getMessage());
        }
    }

    /**
     * @return true if the node was not yet marked, i.e. the handlers still have to be added
     */
    private boolean markAttached(Node node) {
        return node.getProperties().putIfAbsent(PROPERTY_KEY, this) == null;
    }

    private Node findHeader() {
        for (Node child : tableView.getChildrenUnmodifiable()) {
            if (child.getStyleClass().contains(HEADER_STYLE_CLASS)) {
                return child;
            }
        }
        return null;
    }

    private static Node findMenuButton(Node header) {
        if (header instanceof Parent) {
            return header.lookup(MENU_BUTTON_SELECTOR);
        }
        return null;
    }

    private static boolean isShown(Node node) {
        return node != null && node.isVisible() && node.getScene() != null;
    }

    private void rebuildItems() {
        releaseBindings();
        contextMenu.getItems().clear();

        MenuItem selectAll = new MenuItem(I18n.getInstance().getString("table.columns.selectall"));
        selectAll.setOnAction(event -> setAllVisible(tableView.getColumns(), true));
        MenuItem deselectAll = new MenuItem(I18n.getInstance().getString("table.columns.deselectall"));
        deselectAll.setOnAction(event -> setAllVisible(tableView.getColumns(), false));
        contextMenu.getItems().addAll(selectAll, deselectAll, new SeparatorMenuItem());

        addColumnItems(tableView.getColumns(), 0);

        if (!additionalItems.isEmpty()) {
            contextMenu.getItems().add(new SeparatorMenuItem());
            contextMenu.getItems().addAll(additionalItems);
        }
    }

    private void addColumnItems(List<? extends TableColumn<?, ?>> columns, int depth) {
        for (int i = 0; i < columns.size(); i++) {
            TableColumn<?, ?> column = columns.get(i);

            CheckBox checkBox = new CheckBox(columnLabel(column, i));
            checkBox.setMaxWidth(Double.MAX_VALUE);
            checkBox.setPadding(new Insets(0, 0, 0, depth * INDENT));
            checkBox.setSelected(column.isVisible());
            checkBox.selectedProperty().bindBidirectional(column.visibleProperty());
            releaseBindings.add(() -> checkBox.selectedProperty().unbindBidirectional(column.visibleProperty()));

            /* hideOnClick=false: the menu stays open while columns are toggled */
            contextMenu.getItems().add(new CustomMenuItem(checkBox, false));

            addColumnItems(column.getColumns(), depth + 1);
        }
    }

    private static String columnLabel(TableColumn<?, ?> column, int index) {
        if (column.getText() != null && !column.getText().trim().isEmpty()) {
            return column.getText();
        }
        if (column.getId() != null && !column.getId().trim().isEmpty()) {
            return column.getId();
        }
        return "#" + (index + 1);
    }

    private static void setAllVisible(List<? extends TableColumn<?, ?>> columns, boolean visible) {
        for (TableColumn<?, ?> column : columns) {
            column.setVisible(visible);
            setAllVisible(column.getColumns(), visible);
        }
    }

    private void releaseBindings() {
        releaseBindings.forEach(Runnable::run);
        releaseBindings.clear();
    }
}
