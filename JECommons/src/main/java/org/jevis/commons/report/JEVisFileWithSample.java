/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.report;

import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisSample;

public class JEVisFileWithSample {
    private final JEVisSample jeVisSample;
    private final JEVisFile pdfFile;
    private final JEVisFile xlsxFile;

    public JEVisFileWithSample(JEVisSample jeVisSample, JEVisFile pdfFile, JEVisFile xlsxFile) {
        this.jeVisSample = jeVisSample;
        this.pdfFile = pdfFile;
        this.xlsxFile = xlsxFile;
    }

    public JEVisFileWithSample(JEVisSample jeVisSample, JEVisFile pdfFile) {
        this(jeVisSample, pdfFile, null);
    }

    public JEVisSample getJeVisSample() {
        return jeVisSample;
    }

    public JEVisFile getPdfFile() {
        return pdfFile;
    }

    public JEVisFile getXlsxFile() {
        return xlsxFile;
    }
}
