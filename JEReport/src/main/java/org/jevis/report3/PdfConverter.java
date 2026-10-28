/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEReport.
 * <p>
 * JEReport is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEReport is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEReport. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEReport is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.report3;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.JodConverter;
import org.jodconverter.local.office.LocalOfficeManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;


/**
 * @author broder
 */
public class PdfConverter {
    private static final Logger logger = LogManager.getLogger(PdfConverter.class);

    private File xlsFile;
    private File pdfFile;

    public PdfConverter(String filePrefix, byte[] fileContent) {
        try {
            Path xlsFilePath = Files.createTempFile(filePrefix, ".xlsx");
            xlsFile = xlsFilePath.toFile();
            Files.write(xlsFilePath, fileContent);
            xlsFile.deleteOnExit();

            Path pdfFilePath = Files.createTempFile(filePrefix, ".pdf");
            pdfFile = pdfFilePath.toFile();
            pdfFile.deleteOnExit();
        } catch (IOException ex) {
            logger.error(ex);
        }
    }

    public File runPdfConverter() {

        final LocalOfficeManager officeManager = LocalOfficeManager.builder()
                .processTimeout(900000L)
                .taskExecutionTimeout(900000L)
                .taskQueueTimeout(1200000L)
                .install()
                .build();

        try {
            officeManager.start();
            JodConverter
                    .convert(xlsFile)
                    .to(pdfFile)
                    .execute();
        } catch (OfficeException e) {
            logger.error(e);
        } finally {
            OfficeUtils.stopQuietly(officeManager);
        }
        return pdfFile;
    }

    public static void main(String[] args) throws IOException {
        Locale aDefault = Locale.getDefault();
        Charset defaultCharset = Charset.defaultCharset();
        String displayName = defaultCharset.displayName();
        String displayLanguage = aDefault.getDisplayLanguage();
        Path fileLocation = Paths.get("");
        byte[] data = Files.readAllBytes(fileLocation);
        PdfConverter converter = new PdfConverter("test", data);
        converter.runPdfConverter();
    }
}
