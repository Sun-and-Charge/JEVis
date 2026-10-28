/**
 * Copyright (C) JEVis contributors
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
package org.jevis.jeconfig.application.Chart.Charts.TableCells;

import com.ibm.icu.text.NumberFormat;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.util.Callback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.jeconfig.application.Chart.ChartElements.TableHSample;
import org.jevis.jeconfig.application.tools.ColorHelper;

/**
 * JavaFX cell factory for the value column in a
 * {@link org.jevis.jeconfig.application.Chart.Charts.TableChartH} table.
 * <p>
 * Each cell renders the numeric sample value using the series-specific {@link NumberFormat},
 * appends the unit label (when non-empty), and applies a colour to the text matching the
 * series colour stored on the row's {@link TableHSample}.
 * <p>
 * A null check on {@link javafx.scene.control.TableCell#getTableRow()} guards against NPE
 * during cell lifecycle events before the row has been associated.
 *
 * @param <T> unused generic parameter — the cell is always parameterised as
 *            {@code TableCell<TableHSample, Double>}
 * @see TableHSample
 */
public class ValueTableCell<T> implements Callback<TableColumn<TableHSample, Double>, TableCell<TableHSample, Double>> {
    private static final Logger logger = LogManager.getLogger(ValueTableCell.class);

    public ValueTableCell() {
        super();
    }


    @Override
    public TableCell<TableHSample, Double> call(TableColumn<TableHSample, Double> tableColumn) {
        TableCell<TableHSample, Double> tableCell = new TableCell<TableHSample, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                } else {
                    TableRow<?> row = getTableRow();
                    if (row == null) return;
                    TableHSample sample = (TableHSample) row.getItem();
                    if (sample != null) {
                        String content = getString(sample.getSampleValue(), sample.getNf(), sample.getSampleUnit());
                        setText(content);

                        if (sample.getSampleColor() != null) {
                            setStyle("-fx-text-fill: " + ColorHelper.toRGBCode(sample.getSampleColor()));
                        }
                    }
                }


            }
        };

        return tableCell;


    }

    /**
     * Formats a numeric value with its unit label.
     *
     * @param value     the numeric sample value to format
     * @param nf        the series-specific number format
     * @param unitLabel the measurement unit label; the label is omitted when empty
     * @return the formatted string, e.g. {@code "123.45 kWh"} or {@code "42"}
     */
    private String getString(double value, NumberFormat nf, String unitLabel) {
        String formatted = nf.format(value);
        return unitLabel.isEmpty() ? formatted : formatted + " " + unitLabel;
    }
}
