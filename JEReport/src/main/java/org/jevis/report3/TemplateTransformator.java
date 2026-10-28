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

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jxls.common.Context;
import org.jxls.transform.poi.PoiTransformer;
import org.jxls.util.JxlsHelper;

import java.io.ByteArrayInputStream;
import java.io.File;

/**
 * @author broder
 */
public class TemplateTransformator {

    private byte[] outputBytes;

    public void transform(File templateFile, Context context) throws Exception {
//        InputStream input = new ByteArrayInputStream(templateFile);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

//        Transformer transformer = TransformerFactory.createTransformer(input, output);
        JxlsHelper jxlsHelper = JxlsHelper.getInstance();
        jxlsHelper.setUseFastFormulaProcessor(false);
        jxlsHelper.setProcessFormulas(true);

        XSSFWorkbook template = new XSSFWorkbook(templateFile);
        PoiTransformer poiTransformer = PoiTransformer.createTransformer(template);
        poiTransformer.setOutputStream(output);
        jxlsHelper.processTemplate(context, poiTransformer);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(output.toByteArray());
        XSSFWorkbook workbook = new XSSFWorkbook(byteArrayInputStream);
        workbook.setForceFormulaRecalculation(true);
        XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            workbook.write(bos);
        } finally {
            bos.close();
            workbook.close();
            byteArrayInputStream.close();
//            input.close();
            output.close();
        }
        outputBytes = bos.toByteArray();
    }

    public byte[] getOutputBytes() {
        return outputBytes;
    }
}
