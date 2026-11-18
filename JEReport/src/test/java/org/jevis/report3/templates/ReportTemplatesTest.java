/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.report3.templates;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.jevis.report3.TemplateTransformator;
import org.jevis.report3.data.reportlink.EnergyManagementFunction;
import org.jevis.commons.iso.ActionItem;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jxls.common.Context;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fills the generated templates with sample data the way JEReport does ({@link TemplateTransformator}) and checks the
 * computed cells.
 */
class ReportTemplatesTest {
    private static final DateTimeZone ZONE = DateTimeZone.forID("Europe/Berlin");

    /** a report link value as JEReport builds it: {value: [{value, unit, timestamp}]} */
    private static Map<String, Object> link(String unit, DateTime firstMonth, double... values) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            Map<String, Object> sample = new HashMap<>();
            sample.put("value", values[i]);
            sample.put("unit", unit);
            sample.put("timestamp", DateUtil.getExcelDate(firstMonth.plusMonths(i).toLocalDateTime().toDate()));
            samples.add(sample);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("value", samples);
        return map;
    }

    private static Workbook fill(XSSFWorkbook template, Map<String, Object> data, Path dir, String name) throws Exception {
        File file = dir.resolve(name).toFile();
        ReportTemplates.write(template, file);
        TemplateTransformator transformator = new TemplateTransformator();
        transformator.transform(file, new Context(data));
        /* -DreportPreviewDir=<dir>: keep the filled files, e.g. to look at them as PDF */
        String preview = System.getProperty("reportPreviewDir");
        if (preview != null && !preview.isEmpty()) {
            java.nio.file.Files.write(new File(preview, "filled-" + name).toPath(), transformator.getOutputBytes());
        }
        Workbook result = new XSSFWorkbook(new ByteArrayInputStream(transformator.getOutputBytes()));
        assertNoTemplateText(result);
        return result;
    }

    private static void assertNoTemplateText(Workbook workbook) {
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                for (Cell cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        assertFalse(cell.getStringCellValue().contains("${"), sheet.getSheetName() + "!" + cell.getAddress() + " " + cell.getStringCellValue());
                    }
                }
            }
        }
    }

    /** the cell right of the first cell with this text */
    private static Cell rightOf(Sheet sheet, String label, int offset) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().equals(label)) {
                    return row.getCell(cell.getColumnIndex() + offset);
                }
            }
        }
        fail("no cell '" + label + "' in " + sheet.getSheetName());
        return null;
    }

    /** the cell some rows below the first cell with this text */
    private static Cell below(Sheet sheet, String label, int rows) {
        for (Row row : sheet) {
            for (Cell cell : row) {
                if (cell.getCellType() == CellType.STRING && cell.getStringCellValue().equals(label)) {
                    return sheet.getRow(row.getRowNum() + rows).getCell(cell.getColumnIndex());
                }
            }
        }
        fail("no cell '" + label + "' in " + sheet.getSheetName());
        return null;
    }

    private static double number(Cell cell) {
        assertEquals(CellType.FORMULA == cell.getCellType() ? CellType.NUMERIC : CellType.NUMERIC,
                cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType(), cell.getAddress().toString());
        return cell.getNumericCellValue();
    }

    @Test
    void enpiReportComputesChangesMeansAndTheBaseline(@TempDir Path dir) throws Exception {
        DateTime month = new DateTime(2026, 10, 1, 0, 0, ZONE);
        DateTime yearStart = month.minusMonths(11);
        Map<String, Object> data = new HashMap<>();
        for (String[] e : ReportTemplates.ENPIS) {
            data.put(ReportTemplates.v(e[1], "CURRENT"), link("kWh/t", month, 110));
            data.put(ReportTemplates.v(e[1], "LAST"), link("kWh/t", month.minusMonths(1), 100));
            double[] year = new double[12];
            Arrays.fill(year, 90);
            year[11] = 210;
            data.put(ReportTemplates.v(e[1], "FIXED_TO_REPORT_END"), link("kWh/t", yearStart, year));
        }
        for (String[] c : ReportTemplates.ENPI_CONSUMPTIONS) {
            data.put(ReportTemplates.v(c[1], "CURRENT"), link("kWh", month, 5000));
            data.put(ReportTemplates.v(c[1], "LAST"), link("kWh", month.minusMonths(1), 4000));
        }
        /* production electricity = 1000 + 2 · quantity exactly */
        double[] qty = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000, 1100, 1200};
        double[] power = new double[12];
        for (int i = 0; i < 12; i++) power[i] = 1000 + 2 * qty[i];
        data.put(ReportTemplates.v("Produktionsmenge", "FIXED_TO_REPORT_END"), link("t", yearStart, qty));
        data.put(ReportTemplates.v("Strom_Produktion", "FIXED_TO_REPORT_END"), link("kWh", yearStart, power));
        data.put(ReportTemplates.v("Produktionsmenge", "CURRENT"), link("t", month, 500));
        data.put(ReportTemplates.v("Strom_Produktion", "CURRENT"), link("kWh", month, 2100));

        Workbook wb = fill(ReportTemplates.enpiReport(), data, dir, ReportTemplates.ENPI_FILE);
        Sheet overview = wb.getSheet("EnPI");
        assertEquals(110, number(rightOf(overview, "Strom gesamt", 2)), 1e-9);
        assertEquals(0.10, number(rightOf(overview, "Strom gesamt", 4)), 1e-9);
        /* mean of 11 × 90 and 210 */
        assertEquals(100, number(rightOf(overview, "Gas Produktion", 5)), 1e-9);

        Sheet baseline = wb.getSheet("Baseline");
        assertEquals(2, number(rightOf(baseline, "Steigung (kWh je Einheit)", 1)), 1e-9);
        assertEquals(1000, number(rightOf(baseline, "Grundlast (kWh)", 1)), 1e-6);
        assertEquals(1, number(rightOf(baseline, "Bestimmtheitsmaß R²", 1)), 1e-9);
        /* model column of the first month: 1000 + 2 · 100 */
        assertEquals(1200, number(below(baseline, "Modell", 1)), 1e-6);
        assertEquals(3400, number(below(baseline, "Modell", 12)), 1e-6);
        /* expected 1000 + 2 · 500 = 2000, measured 2100 */
        assertEquals(100, number(rightOf(baseline, "Abweichung vom Modell", 1)), 1e-6);
        assertEquals(0.05, number(rightOf(baseline, "Abweichung vom Modell", 2)), 1e-9);
    }

    @Test
    void energyManagementReportSumsTheYearAndListsActions(@TempDir Path dir) throws Exception {
        DateTime yearStart = new DateTime(2026, 1, 1, 0, 0, ZONE);
        Map<String, Object> data = new HashMap<>();
        double[] twelve = new double[12];
        Arrays.fill(twelve, 1000);
        double[] lastYear = new double[12];
        Arrays.fill(lastYear, 1250);
        for (String[] c : ReportTemplates.ENMS_CONSUMPTIONS) {
            data.put(ReportTemplates.v(c[1], "CURRENT"), link("kWh", yearStart, twelve));
        }
        data.put(ReportTemplates.v("Strom_Gesamt", "LAST"), link("kWh", yearStart.minusYears(1), lastYear));
        data.put(ReportTemplates.v("Gas_Hauptzaehler_Energie", "LAST"), link("kWh", yearStart.minusYears(1), lastYear));
        data.put(ReportTemplates.v("Kosten_Strom_Netto", "CURRENT"), link("€", yearStart, twelve));
        data.put(ReportTemplates.v("Kosten_Gas_Netto", "CURRENT"), link("€", yearStart, twelve));
        data.put(ReportTemplates.v("Kosten_Strom_Netto", "LAST"), link("€", yearStart.minusYears(1), lastYear));
        data.put(ReportTemplates.v("Kosten_Gas_Netto", "LAST"), link("€", yearStart.minusYears(1), lastYear));
        data.put(ReportTemplates.v("EnPI_Strom_Gesamt", "CURRENT"), link("kWh/t", yearStart, twelve));
        data.put(ReportTemplates.v("EnPI_Gas_Gesamt", "CURRENT"), link("kWh/t", yearStart, twelve));
        data.put(ReportTemplates.v("EnPI_Strom_Gesamt", "LAST"), link("kWh/t", yearStart.minusYears(1), lastYear));
        data.put(ReportTemplates.v("EnPI_Gas_Gesamt", "LAST"), link("kWh/t", yearStart.minusYears(1), lastYear));
        ActionItem action = ActionItem.parse("{\"No\":7,\"Title\":\"LED-Umrüstung Halle 2\",\"Status Tags\":\"Umgesetzt\","
                + "\"Create Date\":\"2025-05-01T00:00:00.000+02:00\",\"Done Date\":\"2026-06-01T00:00:00.000+02:00\","
                + "\"Consumption\":{\"Diff\":12000},\"NPV\":{\"Saving\":3000,\"Investment\":15000}}");
        data.put("enms", EnergyManagementFunction.build(
                Collections.singletonList(new EnergyManagementFunction.Plan<>("Aktionsplan", "MW", Collections.emptyList(), Collections.singletonList(action))),
                Collections.emptyList(), yearStart, yearStart.plusYears(1), ZONE));

        Workbook wb = fill(ReportTemplates.energyManagementReport(), data, dir, ReportTemplates.ENMS_FILE);
        Sheet overview = wb.getSheet("Übersicht");
        assertEquals(12000, number(rightOf(overview, "Strom gesamt", 2)), 1e-9);
        assertEquals(15000, number(rightOf(overview, "Strom gesamt", 3)), 1e-9);
        assertEquals(-0.2, number(rightOf(overview, "Strom gesamt", 4)), 1e-9);
        assertEquals(12000, number(rightOf(overview, "Energiekosten Gas (netto)", 2)), 1e-9);
        assertEquals(1000, number(rightOf(overview, "EnPI Strom gesamt (Jahresmittel)", 2)), 1e-9);
        assertEquals(1250, number(rightOf(overview, "EnPI Strom gesamt (Jahresmittel)", 3)), 1e-9);
        assertEquals(1, number(rightOf(overview, "im Berichtsjahr umgesetzt", 2)), 1e-9);
        assertEquals(3000, number(rightOf(overview, "Einsparung umgesetzter Maßnahmen (€/a)", 2)), 1e-9);
        assertEquals("01.01.2026 – 31.12.2026", rightOf(overview, "Berichtszeitraum", 1).getStringCellValue());

        Sheet actions = wb.getSheet("Maßnahmen");
        assertEquals("LED-Umrüstung Halle 2", rightOf(actions, "MW 007", 1).getStringCellValue());
        assertEquals("01.06.2026", rightOf(actions, "MW 007", 5).getStringCellValue());
        assertEquals(16, wb.getSheet("Verbrauch").getLastRowNum(), "title, header in row 4, 12 months, sum");
    }
}
