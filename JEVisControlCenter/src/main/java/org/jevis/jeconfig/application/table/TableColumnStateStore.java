/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.application.table;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.MouseEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisUser;
import org.jevis.jeconfig.JEConfig;

import java.util.prefs.Preferences;

/**
 * Persists the column widths of a {@link TableView} or {@link TreeTableView} per user
 * (java.util.prefs, node {@value #ROOT_NODE}/&lt;user&gt;/&lt;tableKey&gt;).
 * <p>
 * Only widths changed by the user in the table header (dragging a column border or
 * double-clicking it) are saved, so programmatic auto-fitting does not overwrite the
 * stored values. Columns are identified by {@link TableColumnBase#getId()} or, if no id
 * is set, by their text. Stored widths are applied when columns are added to the table,
 * also later on. Auto-fit code should skip columns for which {@link #hasStoredWidth}
 * returns true.
 */
public class TableColumnStateStore {

    public static final String ROOT_NODE = "JEVis.JEConfig.TableColumns";
    private static final Logger logger = LogManager.getLogger(TableColumnStateStore.class);
    private static final String PROPERTY_KEY = TableColumnStateStore.class.getName();
    private static final String WIDTH_LISTENER_KEY = TableColumnStateStore.class.getName() + ".widthListener";
    private static final int MAX_NAME_LENGTH = 80;

    private final Preferences node;
    private boolean userResizing = false;

    private TableColumnStateStore(Preferences node) {
        this.node = node;
    }

    /**
     * Binds the column widths of the given table to the preferences of the current user.
     *
     * @param tableView the table
     * @param tableKey  unique key of the table, e.g. "alarms"
     * @return the store, or null if the preferences could not be opened
     */
    public static TableColumnStateStore bind(TableView<?> tableView, String tableKey) {
        TableColumnStateStore store = create(tableKey);
        if (store != null) {
            tableView.getProperties().put(PROPERTY_KEY, store);
            store.attachMouseTracking(tableView);
            store.attachColumns(tableView.getColumns());
        }
        return store;
    }

    /**
     * Binds the column widths of the given tree table to the preferences of the current user.
     */
    public static TableColumnStateStore bind(TreeTableView<?> treeTableView, String tableKey) {
        TableColumnStateStore store = create(tableKey);
        if (store != null) {
            treeTableView.getProperties().put(PROPERTY_KEY, store);
            store.attachMouseTracking(treeTableView);
            store.attachColumns(treeTableView.getColumns());
        }
        return store;
    }

    /**
     * @return true if the column belongs to a bound table and has a width stored by the user;
     * auto-fit code should leave such columns alone.
     */
    public static boolean hasStoredWidth(TableColumnBase<?, ?> column) {
        try {
            Node table = null;
            if (column instanceof TableColumn) {
                table = ((TableColumn<?, ?>) column).getTableView();
            } else if (column instanceof TreeTableColumn) {
                table = ((TreeTableColumn<?, ?>) column).getTreeTableView();
            }
            if (table == null) return false;
            Object store = table.getProperties().get(PROPERTY_KEY);
            if (!(store instanceof TableColumnStateStore)) return false;
            String key = columnKey(column);
            return key != null && ((TableColumnStateStore) store).node.getDouble(key, -1) > 0;
        } catch (Exception e) {
            logger.debug("Could not read stored column width", e);
            return false;
        }
    }

    private static TableColumnStateStore create(String tableKey) {
        try {
            String user = "default";
            try {
                JEVisUser currentUser = JEConfig.getDataSource().getCurrentUser();
                if (currentUser != null) {
                    if (currentUser.getAccountName() != null && !currentUser.getAccountName().isEmpty()) {
                        user = currentUser.getAccountName();
                    } else {
                        user = String.valueOf(currentUser.getUserID());
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not determine current user for column widths, using default: {}", e.getMessage());
            }
            Preferences node = Preferences.userRoot().node(ROOT_NODE)
                    .node(nodeName(user))
                    .node(nodeName(tableKey));
            return new TableColumnStateStore(node);
        } catch (Exception e) {
            logger.warn("Could not open column width preferences for table '{}': {}", tableKey, e.getMessage());
            return null;
        }
    }

    private void attachMouseTracking(Node table) {
        table.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            userResizing = event.getPickResult() != null && isInHeader(event.getPickResult().getIntersectedNode(), table);
        });
        table.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (userResizing) {
                /* keep the flag until resize operations triggered by the release are done */
                Platform.runLater(() -> {
                    userResizing = false;
                    try {
                        node.flush();
                    } catch (Exception e) {
                        logger.debug("Could not flush column width preferences", e);
                    }
                });
            }
        });
    }

    private static boolean isInHeader(Node node, Node table) {
        while (node != null && node != table) {
            if (node.getStyleClass().contains("column-header-background")) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    private <C extends TableColumnBase<?, ?>> void attachColumns(ObservableList<C> columns) {
        for (C column : columns) {
            attachColumn(column);
        }
        columns.addListener((ListChangeListener<C>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (C column : change.getAddedSubList()) {
                        attachColumn(column);
                    }
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void attachColumn(TableColumnBase<?, ?> column) {
        try {
            if (column.getColumns().isEmpty()) {
                restore(column);
            } else {
                attachColumns((ObservableList<TableColumnBase<?, ?>>) (ObservableList<?>) column.getColumns());
            }

            if (column.getProperties().containsKey(WIDTH_LISTENER_KEY)) return;
            ChangeListener<Number> listener = (observable, oldValue, newValue) -> {
                if (userResizing && column.getColumns().isEmpty()) {
                    save(column, newValue.doubleValue());
                }
            };
            column.getProperties().put(WIDTH_LISTENER_KEY, listener);
            column.widthProperty().addListener(listener);
        } catch (Exception e) {
            logger.warn("Could not bind column width: {}", e.getMessage());
        }
    }

    private void restore(TableColumnBase<?, ?> column) {
        try {
            String key = columnKey(column);
            if (key == null) return;
            double width = node.getDouble(key, -1);
            if (width > 0) {
                column.setPrefWidth(width);
            }
        } catch (Exception e) {
            logger.warn("Could not restore column width: {}", e.getMessage());
        }
    }

    private void save(TableColumnBase<?, ?> column, double width) {
        try {
            String key = columnKey(column);
            if (key == null || width <= 0) return;
            node.putDouble(key, width);
        } catch (Exception e) {
            logger.warn("Could not save column width: {}", e.getMessage());
        }
    }

    private static String columnKey(TableColumnBase<?, ?> column) {
        String key = column.getId();
        if (key == null || key.trim().isEmpty()) {
            key = column.getText();
        }
        if (key == null || key.trim().isEmpty()) {
            return null;
        }
        return shorten(key);
    }

    private static String nodeName(String name) {
        return shorten(name.replace('/', '_'));
    }

    private static String shorten(String name) {
        if (name.length() <= MAX_NAME_LENGTH) {
            return name;
        }
        String hash = Integer.toHexString(name.hashCode());
        return name.substring(0, MAX_NAME_LENGTH - hash.length() - 1) + "_" + hash;
    }
}
