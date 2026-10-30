/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisSample;

/**
 * A file sample with its PDF and optional XLSX file. Built from samples, the files are only downloaded when they are
 * requested, so listing many report files stays fast.
 */
public class JEVisFileWithSample {
    private static final Logger logger = LogManager.getLogger(JEVisFileWithSample.class);
    private final JEVisSample jeVisSample;
    private final JEVisSample xlsxSample;
    private JEVisFile pdfFile;
    private JEVisFile xlsxFile;

    public JEVisFileWithSample(JEVisSample jeVisSample, JEVisFile pdfFile, JEVisFile xlsxFile) {
        this.jeVisSample = jeVisSample;
        this.xlsxSample = null;
        this.pdfFile = pdfFile;
        this.xlsxFile = xlsxFile;
    }

    public JEVisFileWithSample(JEVisSample jeVisSample, JEVisFile pdfFile) {
        this(jeVisSample, pdfFile, null);
    }

    /**
     * Files are loaded from the samples on first access.
     *
     * @param pdfSample  sample of the PDF file
     * @param xlsxSample sample of the XLSX file, may be {@code null}
     */
    public JEVisFileWithSample(JEVisSample pdfSample, JEVisSample xlsxSample) {
        this.jeVisSample = pdfSample;
        this.xlsxSample = xlsxSample;
    }

    public JEVisSample getJeVisSample() {
        return jeVisSample;
    }

    public synchronized JEVisFile getPdfFile() {
        if (pdfFile == null && jeVisSample != null) {
            pdfFile = load(jeVisSample);
        }
        return pdfFile;
    }

    public synchronized JEVisFile getXlsxFile() {
        if (xlsxFile == null && xlsxSample != null) {
            xlsxFile = load(xlsxSample);
        }
        return xlsxFile;
    }

    private static JEVisFile load(JEVisSample sample) {
        try {
            return sample.getValueAsFile();
        } catch (JEVisException ex) {
            logger.error("Could not load file of sample {}", sample, ex);
            return null;
        }
    }
}
