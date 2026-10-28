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

import java.util.List;

/**
 * Common interface for file parsers that produce rows of import data.
 */
public interface ImportParser {
    /** Returns all data rows (after headers have been skipped). */
    List<ImportLine> getLines();
    /** Maximum number of columns across all rows. */
    int getColumnCount();
    /** Number of header rows that are skipped. */
    int getHeaderLines();
    /** Set the number of header rows to skip (does NOT trigger a re-parse). */
    void setHeaderLines(int count);
    /** Re-parse the source file with current settings. */
    void refresh();
}
