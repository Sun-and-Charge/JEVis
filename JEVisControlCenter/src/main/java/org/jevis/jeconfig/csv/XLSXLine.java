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
package org.jevis.jeconfig.csv;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFRow;

/**
 * Wraps a single Apache POI {@link XSSFRow} and exposes it via the {@link ImportLine} interface.
 */
public class XLSXLine implements ImportLine {

    private final XSSFRow row;
    private final int rowNumber;
    private final DataFormatter formatter;
    private final FormulaEvaluator evaluator;
    private final int columnCount;

    public XLSXLine(XSSFRow row, int rowNumber, DataFormatter formatter, FormulaEvaluator evaluator, int columnCount) {
        this.row = row;
        this.rowNumber = rowNumber;
        this.formatter = formatter;
        this.evaluator = evaluator;
        this.columnCount = columnCount;
    }

    @Override
    public String getColumn(int index) {
        if (row == null) return "";
        try {
            return formatter.formatCellValue(row.getCell(index), evaluator);
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public int getColumnCount() {
        return columnCount;
    }

    @Override
    public boolean isEmpty() {
        if (row == null) return true;
        for (int i = 0; i < columnCount; i++) {
            String val = getColumn(i);
            if (val != null && !val.trim().isEmpty()) return false;
        }
        return true;
    }

    @Override
    public int getRowNumber() {
        return rowNumber;
    }
}
