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
package org.jevis.jeconfig.application.tools;

import javafx.scene.Node;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.skin.TableColumnHeader;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * Sets a table column to the width of its content, as a double click on the column border does.
 * <p>
 * JavaFX 8 offered this only as the private {@code TableViewSkin.resizeColumnToFitContent}; since JavaFX 9 it is the
 * protected {@link TableColumnHeader#resizeColumnToFitContent(int)}, which is called here. If the JavaFX module does
 * not allow that access, the width is measured from the header and the cell texts.
 */
public final class ColumnAutoFit {
    private static final Logger logger = LogManager.getLogger(ColumnAutoFit.class);
    /** rows measured by the fallback */
    private static final int MAX_ROWS = 50;
    private static final double PADDING = 24;
    private static Method resizeMethod;
    private static boolean resizeMethodChecked;

    private ColumnAutoFit() {
    }

    /**
     * @param table  the table that shows the column (its skin must exist, else nothing happens)
     * @param column a {@link TableColumn} of the table; other objects are ignored
     */
    public static void fit(TableView<?> table, Object column) {
        if (table == null || table.getSkin() == null || !(column instanceof TableColumn)) return;
        TableColumn<?, ?> tableColumn = (TableColumn<?, ?>) column;
        TableColumnHeader header = headerOf(table, tableColumn);
        Method method = resizeMethod();
        if (header != null && method != null) {
            try {
                method.invoke(header, -1);
                return;
            } catch (Exception e) {
                logger.debug("resizeColumnToFitContent failed, measuring the column", e);
            }
        }
        measure(table, tableColumn);
    }

    private static synchronized Method resizeMethod() {
        if (!resizeMethodChecked) {
            resizeMethodChecked = true;
            try {
                Method method = TableColumnHeader.class.getDeclaredMethod("resizeColumnToFitContent", int.class);
                method.setAccessible(true);
                resizeMethod = method;
            } catch (Exception e) {
                logger.info("TableColumnHeader.resizeColumnToFitContent not accessible, columns are measured instead: {}", e.getMessage());
            }
        }
        return resizeMethod;
    }

    private static TableColumnHeader headerOf(TableView<?> table, TableColumn<?, ?> column) {
        for (Node node : table.lookupAll(".column-header")) {
            if (node instanceof TableColumnHeader && ((TableColumnHeader) node).getTableColumn() == column) {
                return (TableColumnHeader) node;
            }
        }
        return null;
    }

    /** width of the header text and of the cell values of the first rows */
    private static void measure(TableView<?> table, TableColumn<?, ?> column) {
        double width = textWidth(column.getText());
        int rows = Math.min(MAX_ROWS, table.getItems().size());
        for (int i = 0; i < rows; i++) {
            Object value = column.getCellData(i);
            if (value != null) width = Math.max(width, textWidth(String.valueOf(value)));
        }
        double target = width + PADDING;
        if (column.getMaxWidth() > 0) target = Math.min(target, column.getMaxWidth());
        column.setPrefWidth(Math.max(column.getMinWidth(), target));
    }

    private static double textWidth(String text) {
        if (text == null || text.isEmpty()) return 0;
        return new Text(text).getLayoutBounds().getWidth();
    }
}
