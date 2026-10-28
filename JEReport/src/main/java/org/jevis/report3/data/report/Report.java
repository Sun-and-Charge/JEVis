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
package org.jevis.report3.data.report;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisFile;
import org.jevis.report3.TemplateTransformator;
import org.jxls.common.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

/**
 * @author broder
 */
public class Report {

    private static final Logger logger = LogManager.getLogger(Report.class);
    private final ReportProperty reportProperty;
    private final Map<String, Object> contextMap;

    public Report(ReportProperty property, Map<String, Object> contextMap) {
        this.reportProperty = property;
        this.contextMap = contextMap;
    }

    public byte[] getReportFile() {
        //get the template
        JEVisFile template = reportProperty.getTemplate();
        TemplateTransformator templateTransformator = new TemplateTransformator();

        Context context = new Context(contextMap);
        context.getConfig().setIsFormulaProcessingRequired(true);

        try {
            File templateFile = File.createTempFile("template", ".xlsx");
            templateFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(templateFile)) {
                fos.write(template.getBytes());
            }

            templateTransformator.transform(templateFile, context);
        } catch (Exception ex) {
            logger.fatal("error while transformation of the template", ex);
        }
        return templateTransformator.getOutputBytes();
    }
}
