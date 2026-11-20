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
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.*;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Generates the JXLS templates of the EnPI report and the energy management report (docs/REPORTS-ENMS.md) into
 * {@code var/report-templates}. Run {@link #main(String[])} with the target directory after a change and commit the
 * files; {@code ReportTemplatesTest} fills them with sample data.
 */
public final class ReportTemplates {
    public static final String ENPI_FILE = "EnPI-Bericht.xlsx";
    public static final String ENMS_FILE = "Energiemanagementbericht.xlsx";

    /** EnPIs of the EnPI report: label, variable stem (…_Value_MONTHLY_CURRENT/LAST/FIXED_TO_REPORT_END) */
    static final String[][] ENPIS = {
            {"Strom gesamt", "EnPI_Strom_Gesamt"},
            {"Strom Verwaltung", "EnPI_Strom_Verwaltung"},
            {"Strom Produktion", "EnPI_Strom_Produktion"},
            {"Gas gesamt", "EnPI_Gas_Gesamt"},
            {"Gas Verwaltung", "EnPI_Gas_Verwaltung"},
            {"Gas Produktion", "EnPI_Gas_Produktion"}};
    /** consumptions of the EnPI report: label, variable stem (…_Value_MONTHLY_CURRENT/LAST) */
    static final String[][] ENPI_CONSUMPTIONS = {
            {"Strom gesamt", "Strom_Gesamt"},
            {"Strom Verwaltung", "Strom_Verwaltung"},
            {"Strom Produktion", "Strom_Produktion"},
            {"Gas gesamt", "Gas_main_meter_energy"},
            {"Gas Verwaltung", "Gas_Verwaltung"},
            {"Gas Produktion", "Gas_Produktion"},
            {"Produktionsmenge", "Produktionsmenge"}};
    /** consumptions of the energy management report: label, variable stem (…_Value_MONTHLY_CURRENT) */
    static final String[][] ENMS_CONSUMPTIONS = {
            {"Strom gesamt", "Strom_Gesamt"},
            {"Strom Verwaltung", "Strom_Verwaltung"},
            {"Strom Produktion", "Strom_Produktion"},
            {"Strom HLK", "Strom_HLK"},
            {"Strom Drittmengen", "Strom_Drittmengen"},
            {"Gas gesamt", "Gas_Hauptzaehler_Energie"}};

    private ReportTemplates() {
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "var/report-templates");
        dir.mkdirs();
        write(enpiReport(), new File(dir, ENPI_FILE));
        write(energyManagementReport(), new File(dir, ENMS_FILE));
        System.out.println("written to " + dir.getAbsolutePath());
    }

    static void write(XSSFWorkbook workbook, File file) throws Exception {
        try (OutputStream out = new FileOutputStream(file)) {
            workbook.write(out);
        }
        workbook.close();
    }

    static String v(String stem, String period) {
        return stem + "_Value_MONTHLY_" + period;
    }

    /* ------------------------------------------------------------------ EnPI report */

    public static XSSFWorkbook enpiReport() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);

        /* cover sheet, then the overview */
        cover(wb, st, "EnPI-Bericht", "Energiekennzahlen (EnPI) nach ISO 50001", new String[][]{
                {"EnPI", "Kennzahlen und Verbrauch des Berichtsmonats gegenüber dem Vormonat, Diagramme"},
                {"Verlauf", "Kennzahlen der letzten 12 Monate mit Mittelwert"},
                {"Baseline", "Regression Strom Produktion zur Produktionsmenge, erwarteter und gemessener Verbrauch"}});
        Sheet s = wb.createSheet("EnPI");
        int r = st.title(s, "EnPI-Bericht", "Energiekennzahlen (EnPI) nach ISO 50001${" + SITE + "}", 6);
        st.label(s, r, 0, "Berichtsmonat");
        st.expr(s, r, 1, "${" + v("EnPI_Strom_Gesamt", "CURRENT") + ".value[0].timestamp}", st.month);
        r += 2;
        st.header(s, r++, "Kennzahl", "Einheit", "Berichtsmonat", "Vormonat", "Veränderung", "Mittel 12 Monate");
        int firstEnpiRow = r;
        for (String[] e : ENPIS) {
            st.text(s, r, 0, e[0], st.cell);
            st.expr(s, r, 1, "${" + v(e[1], "CURRENT") + ".value[0].unit}", st.cell);
            st.expr(s, r, 2, "${" + v(e[1], "CURRENT") + ".value[0].value}", st.number2);
            st.expr(s, r, 3, "${" + v(e[1], "LAST") + ".value[0].value}", st.number2);
            st.formula(s, r, 4, change("C" + (r + 1), "D" + (r + 1)), st.percent);
            /* the mean of the block on the sheet "Verlauf" (row filled in below) */
            r++;
        }
        r++;
        st.header(s, r++, "Verbrauch", "Einheit", "Berichtsmonat", "Vormonat", "Veränderung", "");
        for (String[] c : ENPI_CONSUMPTIONS) {
            st.text(s, r, 0, c[0], st.cell);
            st.expr(s, r, 1, "${" + v(c[1], "CURRENT") + ".value[0].unit}", st.cell);
            st.expr(s, r, 2, "${" + v(c[1], "CURRENT") + ".value[0].value}", st.number0);
            st.expr(s, r, 3, "${" + v(c[1], "LAST") + ".value[0].value}", st.number0);
            st.formula(s, r, 4, change("C" + (r + 1), "D" + (r + 1)), st.percent);
            r++;
        }
        r++;
        st.note(s, r, "Veränderung: Berichtsmonat gegenüber Vormonat. Der Verlauf der letzten 12 Monate steht im Blatt „Verlauf“, "
                + "der Baseline-Vergleich für Strom Produktion im Blatt „Baseline“.", 6);
        int chartRow = r + 2;
        int lastRow = chartRow + 2 * CHART_ROWS + 1;
        st.widths(s, 30, 12, 16, 16, 14, 18);
        st.area(s, "F" + (lastRow + 1));

        /* sheet 2: last 12 months per EnPI (each block: header, rows, mean) */
        Sheet t = wb.createSheet("Verlauf");
        int tr = st.title(t, "Verlauf der Kennzahlen", "Letzte 12 Monate bis zum Berichtsmonat", 3);
        int[] meanRows = new int[ENPIS.length];
        for (int i = 0; i < ENPIS.length; i++) {
            String var = v(ENPIS[i][1], "FIXED_TO_REPORT_END");
            st.header(t, tr++, ENPIS[i][0], "Wert", "Einheit");
            int each = tr;
            st.expr(t, tr, 0, "${s.timestamp}", st.monthCell);
            st.expr(t, tr, 1, "${s.value}", st.number2);
            st.expr(t, tr, 2, "${s.unit}", st.cell);
            st.formula(t, tr, 3, label("A" + (tr + 1)), st.cell);
            st.each(t, tr, 0, var + ".value", "s", null, "D" + (tr + 1));
            tr++;
            st.text(t, tr, 0, "Mittel", st.bold);
            st.formula(t, tr, 1, "AVERAGE(B" + (each + 1) + ")", st.boldNumber2);
            meanRows[i] = tr;
            tr += 2;
        }
        st.widths(t, 22, 14, 12, 8);
        t.setColumnHidden(3, true);
        st.area(t, "D" + tr);
        /* the overview refers to the means (JXLS moves the references with the expanded blocks) */
        for (int i = 0; i < ENPIS.length; i++) {
            st.formula(s, firstEnpiRow + i, 5, "Verlauf!B" + (meanRows[i] + 1), st.number2);
        }

        /* sheet 3: baseline of the production electricity against the production quantity */
        Sheet b = wb.createSheet("Baseline");
        int br = st.title(b, "Baseline Strom Produktion", "Lineare Regression des Stromverbrauchs der Produktion auf die "
                + "Produktionsmenge der letzten 12 Monate (Modell: Verbrauch = Grundlast + Steigung · Menge)", 4);
        st.header(b, br++, "Monat", "Produktionsmenge", "Strom Produktion", "Modell");
        int bEach = br;
        String qty = v("Produktionsmenge", "FIXED_TO_REPORT_END");
        String power = v("Strom_Produktion", "FIXED_TO_REPORT_END");
        st.expr(b, br, 0, "${q.timestamp}", st.monthCell);
        st.expr(b, br, 1, "${q.value}", st.number0);
        st.expr(b, br, 2, "${" + power + ".value[i].value}", st.number0);
        st.formula(b, br, 3, "IF(ISNUMBER(B" + (br + 1) + "),B" + (br + 3) + "+B" + (br + 2) + "*B" + (br + 1) + ",\"\")", st.number0);
        st.each(b, br, 0, qty + ".value", "q", "i", "D" + (br + 1));
        br++;
        String xs = "B" + (bEach + 1), ys = "C" + (bEach + 1);
        st.text(b, br, 0, "Steigung (kWh je Einheit)", st.bold);
        st.formula(b, br++, 1, "SLOPE(" + ys + "," + xs + ")", st.boldNumber2);
        st.text(b, br, 0, "Grundlast (kWh)", st.bold);
        st.formula(b, br++, 1, "INTERCEPT(" + ys + "," + xs + ")", st.boldNumber0);
        st.text(b, br, 0, "Bestimmtheitsmaß R²", st.bold);
        st.formula(b, br++, 1, "CORREL(" + ys + "," + xs + ")^2", st.boldNumber2);
        br++;
        st.header(b, br++, "Berichtsmonat", "Produktionsmenge", "Strom Produktion", "Erwartet (Modell)");
        int cur = br;
        st.expr(b, br, 0, "${" + v("Produktionsmenge", "CURRENT") + ".value[0].timestamp}", st.monthCell);
        st.expr(b, br, 1, "${" + v("Produktionsmenge", "CURRENT") + ".value[0].value}", st.number0);
        st.expr(b, br, 2, "${" + v("Strom_Produktion", "CURRENT") + ".value[0].value}", st.number0);
        st.formula(b, br, 3, "B" + (bEach + 3) + "+B" + (bEach + 2) + "*B" + (cur + 1), st.number0);
        br += 2;
        st.text(b, br, 0, "Abweichung vom Modell", st.bold);
        st.formula(b, br, 1, "C" + (cur + 1) + "-D" + (cur + 1), st.boldNumber0);
        st.formula(b, br++, 2, "IF(D" + (cur + 1) + "<>0,C" + (cur + 1) + "/D" + (cur + 1) + "-1,\"\")", st.percent);
        br++;
        st.note(b, br, "Eine negative Abweichung bedeutet: weniger verbraucht, als bei dieser Produktionsmenge zu erwarten war. "
                + "R² nahe 1: die Produktionsmenge erklärt den Verbrauch gut.", 4);
        st.widths(b, 26, 18, 18, 18);
        st.area(b, "D" + (br + 1));

        /* charts on the overview; the ranges are the rows after JXLS expanded the 12 months (block i of "Verlauf":
           0-based rows 4 + 15·i … 15 + 15·i; "Baseline": rows 4 … 15) */
        XSSFSheet verlauf = wb.getSheet("Verlauf");
        st.lineChart((XSSFSheet) s, chartRow, 6, "EnPI gesamt – letzte 12 Monate", rows(4, 12, 3),
                new String[]{"Strom gesamt", "Gas gesamt"}, new CellRangeAddress[]{rows(4, 12, 1), rows(4 + 15 * 3, 12, 1)},
                verlauf, new byte[][]{BLUEBERRY, SUN});
        XSSFSheet base = wb.getSheet("Baseline");
        s.setRowBreak(chartRow - 1);
        st.scatterChart((XSSFSheet) s, chartRow + CHART_ROWS + 1, 6, "Baseline Strom Produktion",
                base, rows(4, 12, 1), rows(4, 12, 2), rows(4, 12, 3));
        return wb;
    }

    /* ------------------------------------------------------------- energy management report */

    public static XSSFWorkbook energyManagementReport() {
        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);

        /* cover sheet, then the overview */
        cover(wb, st, "Energiemanagementbericht", "Jahresbericht als Grundlage der Managementbewertung nach ISO 50001", new String[][]{
                {"Übersicht", "Verbrauch, Kosten und Kennzahlen gegenüber dem Vorjahr, Maßnahmen und Abweichungen, Diagramme"},
                {"Verbrauch", "Verbrauch je Monat im Berichtsjahr und im Vorjahr"},
                {"Kosten", "Energiekosten je Monat im Berichtsjahr und im Vorjahr"},
                {"Maßnahmen", "alle Maßnahmen des Aktionsplans mit Status und Einsparung"},
                {"Abweichungen", "alle Abweichungen mit Status"}});
        Sheet s = wb.createSheet("Übersicht");
        int r = st.title(s, "Energiemanagementbericht", "Jahresbericht als Grundlage der Managementbewertung nach ISO 50001${" + SITE + "}", 5);
        st.label(s, r, 0, "Berichtszeitraum");
        st.expr(s, r, 1, "${enms.periodStart} – ${enms.periodEnd}", st.cell);
        r += 2;
        st.header(s, r++, "Verbrauch und Kosten", "Einheit", "Berichtsjahr", "Vorjahr", "Veränderung");
        int consRow = r;
        st.text(s, r, 0, "Strom gesamt", st.cell);
        st.expr(s, r, 1, "${" + v("Strom_Gesamt", "CURRENT") + ".value[0].unit}", st.cell);
        r++;
        st.text(s, r, 0, "Gas gesamt", st.cell);
        st.expr(s, r, 1, "${" + v("Gas_Hauptzaehler_Energie", "CURRENT") + ".value[0].unit}", st.cell);
        r++;
        st.text(s, r, 0, "Energiekosten Strom (netto)", st.cell);
        st.text(s, r, 1, "€", st.cell);
        r++;
        st.text(s, r, 0, "Energiekosten Gas (netto)", st.cell);
        st.text(s, r, 1, "€", st.cell);
        r++;
        st.text(s, r, 0, "EnPI Strom gesamt (Jahresmittel)", st.cell);
        st.expr(s, r, 1, "${" + v("EnPI_Strom_Gesamt", "CURRENT") + ".value[0].unit}", st.cell);
        r++;
        st.text(s, r, 0, "EnPI Gas gesamt (Jahresmittel)", st.cell);
        st.expr(s, r, 1, "${" + v("EnPI_Gas_Gesamt", "CURRENT") + ".value[0].unit}", st.cell);
        r++;
        for (int i = consRow; i < r; i++) st.formula(s, i, 4, change("C" + (i + 1), "D" + (i + 1)), st.percent);
        r++;
        st.header(s, r++, "Maßnahmen (Aktionsplan)", "", "Anzahl / Wert", "", "");
        st.summary(s, r++, "Maßnahmen gesamt", "${enms.actionSummary.total}", st.number0);
        st.summary(s, r++, "davon offen", "${enms.actionSummary.open}", st.number0);
        st.summary(s, r++, "im Berichtsjahr umgesetzt", "${enms.actionSummary.doneInPeriod}", st.number0);
        st.summary(s, r++, "Einsparung umgesetzter Maßnahmen (kWh/a)", "${enms.actionSummary.achievedSavingKWh}", st.number0);
        st.summary(s, r++, "Einsparung umgesetzter Maßnahmen (€/a)", "${enms.actionSummary.savingEuroPerYear}", st.number0);
        st.summary(s, r++, "geplante Einsparung offener Maßnahmen (kWh/a)", "${enms.actionSummary.plannedSavingKWh}", st.number0);
        st.summary(s, r++, "Investitionen (€)", "${enms.actionSummary.investment}", st.number0);
        r++;
        st.header(s, r++, "Abweichungen", "", "Anzahl", "", "");
        st.summary(s, r++, "neu im Berichtsjahr", "${enms.nonconformitySummary.newInPeriod}", st.number0);
        st.summary(s, r++, "im Berichtsjahr abgeschlossen", "${enms.nonconformitySummary.closedInPeriod}", st.number0);
        st.summary(s, r++, "offen am Jahresende", "${enms.nonconformitySummary.open}", st.number0);
        st.summary(s, r++, "davon überfällig", "${enms.nonconformitySummary.overdue}", st.number0);
        r++;
        st.note(s, r, "Verbrauch und Kosten je Monat: Blätter „Verbrauch“ und „Kosten“. Einzelne Maßnahmen und Abweichungen: "
                + "Blätter „Maßnahmen“ und „Abweichungen“.", 5);
        int chartRow = r + 2;
        st.widths(s, 44, 10, 16, 16, 14);
        st.area(s, "E" + (chartRow + 3 * (CHART_ROWS + 1) + 1));

        /* sheet 2: consumption per month, current year against the previous year */
        Sheet c = wb.createSheet("Verbrauch");
        int cr = st.title(c, "Verbrauch je Monat", "Berichtsjahr und Vorjahr", 3 + ENMS_CONSUMPTIONS.length);
        String[] head = new String[3 + ENMS_CONSUMPTIONS.length];
        head[0] = "Monat";
        for (int i = 0; i < ENMS_CONSUMPTIONS.length; i++) head[1 + i] = ENMS_CONSUMPTIONS[i][0];
        head[1 + ENMS_CONSUMPTIONS.length] = "Strom gesamt Vorjahr";
        head[2 + ENMS_CONSUMPTIONS.length] = "Gas gesamt Vorjahr";
        st.header(c, cr++, head);
        int cEach = cr;
        String months = v("Strom_Gesamt", "CURRENT");
        st.expr(c, cr, 0, "${m.timestamp}", st.monthCell);
        for (int i = 0; i < ENMS_CONSUMPTIONS.length; i++) {
            st.expr(c, cr, 1 + i, "${" + v(ENMS_CONSUMPTIONS[i][1], "CURRENT") + ".value[i].value}", st.number0);
        }
        st.expr(c, cr, 1 + ENMS_CONSUMPTIONS.length, "${" + v("Strom_Gesamt", "LAST") + ".value[i].value}", st.number0);
        st.expr(c, cr, 2 + ENMS_CONSUMPTIONS.length, "${" + v("Gas_Hauptzaehler_Energie", "LAST") + ".value[i].value}", st.number0);
        st.formula(c, cr, 3 + ENMS_CONSUMPTIONS.length, label("A" + (cr + 1)), st.cell);
        st.each(c, cr, 0, months + ".value", "m", "i", col(3 + ENMS_CONSUMPTIONS.length) + (cr + 1));
        cr++;
        st.text(c, cr, 0, "Summe", st.bold);
        for (int i = 1; i <= 2 + ENMS_CONSUMPTIONS.length; i++) {
            st.formula(c, cr, i, "SUM(" + col(i) + (cEach + 1) + ")", st.boldNumber0);
        }
        int cSum = cr;
        st.widths(c, 12, 14, 18, 18, 12, 20, 12, 22, 20, 8);
        c.setColumnHidden(3 + ENMS_CONSUMPTIONS.length, true);
        st.area(c, col(3 + ENMS_CONSUMPTIONS.length) + (cr + 1));

        /* sheet 3: costs per month from the energy invoices, and the EnPIs */
        Sheet k = wb.createSheet("Kosten");
        int kr = st.title(k, "Energiekosten und Kennzahlen je Monat", "Nettokosten aus den Energierechnungen, EnPIs", 7);
        st.header(k, kr++, "Monat", "Strom (€)", "Gas (€)", "Strom Vorjahr (€)", "Gas Vorjahr (€)", "EnPI Strom", "EnPI Gas");
        int kEach = kr;
        st.expr(k, kr, 0, "${m.timestamp}", st.monthCell);
        st.expr(k, kr, 1, "${" + v("Kosten_Strom_Netto", "CURRENT") + ".value[i].value}", st.number0);
        st.expr(k, kr, 2, "${" + v("Kosten_Gas_Netto", "CURRENT") + ".value[i].value}", st.number0);
        st.expr(k, kr, 3, "${" + v("Kosten_Strom_Netto", "LAST") + ".value[i].value}", st.number0);
        st.expr(k, kr, 4, "${" + v("Kosten_Gas_Netto", "LAST") + ".value[i].value}", st.number0);
        st.expr(k, kr, 5, "${" + v("EnPI_Strom_Gesamt", "CURRENT") + ".value[i].value}", st.number2);
        st.expr(k, kr, 6, "${" + v("EnPI_Gas_Gesamt", "CURRENT") + ".value[i].value}", st.number2);
        st.formula(k, kr, 7, label("A" + (kr + 1)), st.cell);
        st.each(k, kr, 0, months + ".value", "m", "i", "H" + (kr + 1));
        kr++;
        st.text(k, kr, 0, "Summe / Mittel", st.bold);
        for (int i = 1; i <= 4; i++) st.formula(k, kr, i, "SUM(" + col(i) + (kEach + 1) + ")", st.boldNumber0);
        st.formula(k, kr, 5, "AVERAGE(F" + (kEach + 1) + ")", st.boldNumber2);
        st.formula(k, kr, 6, "AVERAGE(G" + (kEach + 1) + ")", st.boldNumber2);
        int kSum = kr;
        kr += 2;
        st.header(k, kr++, "EnPI Vorjahr", "Strom", "Gas", "", "", "", "");
        int kLast = kr;
        st.expr(k, kr, 0, "${l.timestamp}", st.monthCell);
        st.expr(k, kr, 1, "${l.value}", st.number2);
        st.expr(k, kr, 2, "${" + v("EnPI_Gas_Gesamt", "LAST") + ".value[j].value}", st.number2);
        st.each(k, kr, 0, v("EnPI_Strom_Gesamt", "LAST") + ".value", "l", "j", "C" + (kr + 1));
        kr++;
        st.text(k, kr, 0, "Mittel", st.bold);
        st.formula(k, kr, 1, "AVERAGE(B" + (kLast + 1) + ")", st.boldNumber2);
        st.formula(k, kr, 2, "AVERAGE(C" + (kLast + 1) + ")", st.boldNumber2);
        int kLastMean = kr;
        st.widths(k, 16, 13, 13, 19, 17, 13, 13, 8);
        k.setColumnHidden(7, true);
        st.area(k, "H" + (kr + 1));

        /* overview values (references into the sheets; JXLS moves them with the expanded rows) */
        int sg = 1 + indexOf(ENMS_CONSUMPTIONS, "Strom_Gesamt"), gg = 1 + indexOf(ENMS_CONSUMPTIONS, "Gas_Hauptzaehler_Energie");
        st.formula(s, consRow, 2, "Verbrauch!" + col(sg) + (cSum + 1), st.number0);
        st.formula(s, consRow, 3, "Verbrauch!" + col(1 + ENMS_CONSUMPTIONS.length) + (cSum + 1), st.number0);
        st.formula(s, consRow + 1, 2, "Verbrauch!" + col(gg) + (cSum + 1), st.number0);
        st.formula(s, consRow + 1, 3, "Verbrauch!" + col(2 + ENMS_CONSUMPTIONS.length) + (cSum + 1), st.number0);
        st.formula(s, consRow + 2, 2, "Kosten!B" + (kSum + 1), st.number0);
        st.formula(s, consRow + 2, 3, "Kosten!D" + (kSum + 1), st.number0);
        st.formula(s, consRow + 3, 2, "Kosten!C" + (kSum + 1), st.number0);
        st.formula(s, consRow + 3, 3, "Kosten!E" + (kSum + 1), st.number0);
        st.formula(s, consRow + 4, 2, "Kosten!F" + (kSum + 1), st.number2);
        st.formula(s, consRow + 4, 3, "Kosten!B" + (kLastMean + 1), st.number2);
        st.formula(s, consRow + 5, 2, "Kosten!G" + (kSum + 1), st.number2);
        st.formula(s, consRow + 5, 3, "Kosten!C" + (kLastMean + 1), st.number2);

        /* sheet 4: actions */
        Sheet a = wb.createSheet("Maßnahmen");
        int ar = st.title(a, "Maßnahmen", "Alle Maßnahmen der Aktionspläne, Stand Ende des Berichtszeitraums", 8);
        st.header(a, ar++, "Nr.", "Titel", "Status", "Verantwortlich", "Geplant", "Umgesetzt", "Einsparung kWh/a", "Einsparung €/a");
        st.expr(a, ar, 0, "${x.number}", st.cell);
        st.expr(a, ar, 1, "${x.title}", st.wrap);
        st.expr(a, ar, 2, "${x.status}", st.cell);
        st.expr(a, ar, 3, "${x.responsible}", st.cell);
        st.expr(a, ar, 4, "${x.plannedDateText}", st.cell);
        st.expr(a, ar, 5, "${x.doneDateText}", st.cell);
        st.expr(a, ar, 6, "${x.savingKWh}", st.number0);
        st.expr(a, ar, 7, "${x.savingEuroPerYear}", st.number0);
        st.each(a, ar, 0, "enms.actions", "x", null, "H" + (ar + 1));
        ar++;
        st.widths(a, 10, 40, 14, 20, 12, 12, 19, 16);
        st.area(a, "H" + ar);

        /* sheet 5: nonconformities */
        Sheet n = wb.createSheet("Abweichungen");
        int nr = st.title(n, "Abweichungen", "Alle Abweichungen, Stand Ende des Berichtszeitraums", 7);
        st.header(n, nr++, "Nr.", "Titel", "Verantwortlich", "Erfasst", "Geplant", "Abgeschlossen", "Überfällig");
        st.expr(n, nr, 0, "${x.number}", st.cell);
        st.expr(n, nr, 1, "${x.title}", st.wrap);
        st.expr(n, nr, 2, "${x.responsible}", st.cell);
        st.expr(n, nr, 3, "${x.createDateText}", st.cell);
        st.expr(n, nr, 4, "${x.plannedDateText}", st.cell);
        st.expr(n, nr, 5, "${x.doneDateText}", st.cell);
        st.expr(n, nr, 6, "${x.overdue ? 'ja' : ''}", st.cell);
        st.each(n, nr, 0, "enms.nonconformities", "x", null, "G" + (nr + 1));
        nr++;
        st.widths(n, 10, 44, 20, 12, 12, 14, 11);
        st.area(n, "G" + nr);

        /* charts on the overview (0-based rows 4 … 15 of "Verbrauch"/"Kosten" after JXLS expanded the 12 months) */
        XSSFSheet verbrauch = wb.getSheet("Verbrauch"), kosten = wb.getSheet("Kosten");
        int sCol = 1 + indexOf(ENMS_CONSUMPTIONS, "Strom_Gesamt"), gCol = 1 + indexOf(ENMS_CONSUMPTIONS, "Gas_Hauptzaehler_Energie");
        CellRangeAddress months12 = rows(4, 12, 3 + ENMS_CONSUMPTIONS.length);
        s.setRowBreak(chartRow - 1);
        s.setRowBreak(chartRow + 2 * (CHART_ROWS + 1) - 1);
        st.barChart((XSSFSheet) s, chartRow, 5, "Stromverbrauch je Monat (kWh)", verbrauch, months12,
                new String[]{"Berichtsjahr", "Vorjahr"}, new CellRangeAddress[]{rows(4, 12, sCol), rows(4, 12, 1 + ENMS_CONSUMPTIONS.length)},
                new byte[][]{BLUEBERRY, GREY});
        st.barChart((XSSFSheet) s, chartRow + CHART_ROWS + 1, 5, "Gasverbrauch je Monat (kWh)", verbrauch, months12,
                new String[]{"Berichtsjahr", "Vorjahr"}, new CellRangeAddress[]{rows(4, 12, gCol), rows(4, 12, 2 + ENMS_CONSUMPTIONS.length)},
                new byte[][]{SUN, GREY});
        st.barChart((XSSFSheet) s, chartRow + 2 * (CHART_ROWS + 1), 5, "Energiekosten je Monat (€, netto)", kosten, rows(4, 12, 7),
                new String[]{"Strom", "Gas"}, new CellRangeAddress[]{rows(4, 12, 1), rows(4, 12, 2)}, new byte[][]{BLUEBERRY, SUN});
        return wb;
    }

    static final int CHART_ROWS = 18;
    /** " – <site>" or nothing (JEXL, inside ${…}); the variable "report" comes from JEReport (ReportInfo) */
    static final String SITE = "empty(report.site) ? '' : ' – ' + report.site";

    /**
     * Cover sheet: title, the facts of the report (variable {@code report}: name, site, organisation, period, creation
     * time), the table of contents and a block for the approval (management review).
     */
    static void cover(XSSFWorkbook wb, Styles st, String title, String subtitle, String[][] contents) {
        Sheet c = wb.createSheet("Deckblatt");
        st.widths(c, 24, 30, 30, 16);
        int r = 2;
        st.text(c, r, 0, title, st.coverTitle);
        c.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
        c.getRow(r).setHeightInPoints(40);
        r++;
        st.text(c, r, 0, subtitle, st.coverSubtitle);
        c.addMergedRegion(new CellRangeAddress(r, r, 0, 3));
        c.getRow(r).setHeightInPoints(20);
        r++;
        for (int i = 0; i < 4; i++) st.at(c, r, i).setCellStyle(st.sunBar);
        c.getRow(r).setHeightInPoints(5);
        r += 3;
        String[][] facts = {
                {"Bericht", "${report.name}"},
                {"Standort", "${report.site}"},
                {"Organisation", "${report.organisation}"},
                {"Berichtszeitraum", "${report.period}"},
                {"Von – bis", "${report.periodStart} – ${report.periodEnd}"},
                {"Erstellt am", "${report.createdAt}"}};
        for (String[] f : facts) {
            st.text(c, r, 0, f[0], st.factLabel);
            st.expr(c, r, 1, f[1], st.factValue);
            c.addMergedRegion(new CellRangeAddress(r, r, 1, 3));
            c.getRow(r).setHeightInPoints(20);
            r++;
        }
        r += 2;
        st.header(c, r, "Inhalt", "", "", "");
        r++;
        for (String[] e : contents) {
            st.text(c, r, 0, e[0], st.bold);
            st.text(c, r, 1, e[1], st.wrap);
            c.addMergedRegion(new CellRangeAddress(r, r, 1, 3));
            c.getRow(r).setHeightInPoints(e[1].length() > 60 ? 28 : 16);
            r++;
        }
        r += 2;
        st.header(c, r, "Geprüft und freigegeben", "", "", "");
        r++;
        for (String f : new String[]{"Name", "Funktion", "Datum", "Unterschrift"}) {
            st.text(c, r, 0, f, st.signLabel);
            for (int i = 1; i < 4; i++) st.at(c, r, i).setCellStyle(st.signLine);
            c.getRow(r).setHeightInPoints(f.equals("Unterschrift") ? 40 : 26);
            r++;
        }
        r += 2;
        st.note(c, r, "Automatisch erstellt aus den Daten des Energiemanagementsystems. Felder ohne Daten bleiben leer.", 4);
        st.area(c, "D" + (r + 1));
    }
    static final byte[] BLUEBERRY = {(byte) 0x1a, (byte) 0x2a, (byte) 0x45};
    static final byte[] SUN = {(byte) 0xff, (byte) 0xde, (byte) 0x00};
    static final byte[] GREY = {(byte) 0x9a, (byte) 0xa3, (byte) 0xb2};

    /** month label of a timestamp cell for the chart axes (hidden helper column) */
    static String label(String cell) {
        /* MONTH/YEAR instead of TEXT: LibreOffice recalculates TEXT with the format codes of its language */
        return "IF(ISNUMBER(" + cell + "),IF(MONTH(" + cell + ")<10,\"0\",\"\")&MONTH(" + cell + ")&\".\"&RIGHT(YEAR(" + cell
                + "),2),\"\")";
    }

    /** {@code count} rows from the 0-based {@code firstRow} in one column */
    static CellRangeAddress rows(int firstRow, int count, int column) {
        return new CellRangeAddress(firstRow, firstRow + count - 1, column, column);
    }

    private static int indexOf(String[][] list, String stem) {
        for (int i = 0; i < list.length; i++) if (list[i][1].equals(stem)) return i;
        throw new IllegalArgumentException(stem);
    }

    /** relative change of a against b, empty without numbers */
    static String change(String a, String b) {
        return "IF(AND(ISNUMBER(" + a + "),ISNUMBER(" + b + ")),IF(" + b + "<>0," + a + "/" + b + "-1,\"\"),\"\")";
    }

    static String col(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /** cell styles and small writing helpers */
    static final class Styles {
        final XSSFWorkbook wb;
        final CellStyle cell, wrap, bold, number0, number2, boldNumber0, boldNumber2, percent, month, monthCell, head, titleStyle, subtitle, noteStyle,
                coverTitle, coverSubtitle, sunBar, factLabel, factValue, signLabel, signLine;
        private final CreationHelper helper;

        Styles(XSSFWorkbook wb) {
            this.wb = wb;
            helper = wb.getCreationHelper();
            XSSFColor blueberry = new XSSFColor(new byte[]{(byte) 0x1a, (byte) 0x2a, (byte) 0x45}, null);
            XSSFColor sun = new XSSFColor(new byte[]{(byte) 0xff, (byte) 0xde, (byte) 0x00}, null);
            XSSFColor light = new XSSFColor(new byte[]{(byte) 0xee, (byte) 0xf1, (byte) 0xf5}, null);
            Font base = font(false, 10, null);
            Font strong = font(true, 10, null);
            cell = style(base, null, null);
            wrap = style(base, null, null);
            wrap.setWrapText(true);
            bold = style(strong, null, null);
            number0 = style(base, "#,##0", null);
            number2 = style(base, "#,##0.00", null);
            boldNumber0 = style(strong, "#,##0", null);
            boldNumber2 = style(strong, "#,##0.00", null);
            percent = style(base, "+0.0%;-0.0%;0.0%", null);
            month = style(strong, "[$-407]MMMM yyyy", null);
            month.setAlignment(HorizontalAlignment.LEFT);
            monthCell = style(base, "[$-407]MMM yyyy", null);
            monthCell.setAlignment(HorizontalAlignment.LEFT);
            head = style(font(true, 10, new XSSFColor(new byte[]{(byte) 0xff, (byte) 0xff, (byte) 0xff}, null)), null, blueberry);
            titleStyle = style(font(true, 16, blueberry), null, null);
            ((XSSFCellStyle) titleStyle).setBorderBottom(BorderStyle.THICK);
            ((XSSFCellStyle) titleStyle).setBottomBorderColor(sun);
            subtitle = style(font(false, 10, new XSSFColor(new byte[]{(byte) 0x5b, (byte) 0x65, (byte) 0x76}, null)), null, null);
            subtitle.setWrapText(true);
            subtitle.setVerticalAlignment(VerticalAlignment.TOP);
            noteStyle = style(font(false, 9, new XSSFColor(new byte[]{(byte) 0x5b, (byte) 0x65, (byte) 0x76}, null)), null, light);
            noteStyle.setWrapText(true);
            noteStyle.setVerticalAlignment(VerticalAlignment.TOP);
            coverTitle = style(font(true, 26, blueberry), null, null);
            coverSubtitle = style(font(false, 12, new XSSFColor(new byte[]{(byte) 0x5b, (byte) 0x65, (byte) 0x76}, null)), null, null);
            sunBar = style(base, null, sun);
            factLabel = style(font(false, 11, new XSSFColor(new byte[]{(byte) 0x5b, (byte) 0x65, (byte) 0x76}, null)), null, null);
            factValue = style(font(true, 11, blueberry), null, null);
            factValue.setAlignment(HorizontalAlignment.LEFT);
            signLabel = style(base, null, null);
            signLabel.setVerticalAlignment(VerticalAlignment.BOTTOM);
            signLine = style(base, null, null);
            ((XSSFCellStyle) signLine).setBorderBottom(BorderStyle.THIN);
            ((XSSFCellStyle) signLine).setBottomBorderColor(blueberry);
        }

        private Font font(boolean boldFont, int size, XSSFColor color) {
            XSSFFont f = wb.createFont();
            f.setFontName("Arial");
            f.setBold(boldFont);
            f.setFontHeightInPoints((short) size);
            if (color != null) f.setColor(color);
            return f;
        }

        private CellStyle style(Font font, String format, XSSFColor fill) {
            XSSFCellStyle s = wb.createCellStyle();
            s.setFont(font);
            if (format != null) s.setDataFormat(helper.createDataFormat().getFormat(format));
            if (fill != null) {
                s.setFillForegroundColor(fill);
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }

        Cell at(Sheet sheet, int row, int column) {
            Row r = sheet.getRow(row);
            if (r == null) r = sheet.createRow(row);
            Cell c = r.getCell(column);
            return c != null ? c : r.createCell(column);
        }

        /** @return the next free row */
        int title(Sheet sheet, String title, String sub, int columns) {
            text(sheet, 0, 0, title, titleStyle);
            for (int i = 1; i < columns; i++) at(sheet, 0, i).setCellStyle(titleStyle);
            sheet.getRow(0).setHeightInPoints(26);
            text(sheet, 1, 0, sub, subtitle);
            for (int i = 1; i < columns; i++) at(sheet, 1, i).setCellStyle(subtitle);
            if (columns > 1) sheet.addMergedRegion(new CellRangeAddress(1, 1, 0, columns - 1));
            sheet.getRow(1).setHeightInPoints(sub.length() > 70 ? 28 : 15);
            return 3;
        }

        void label(Sheet sheet, int row, int column, String text) {
            text(sheet, row, column, text, bold);
        }

        void text(Sheet sheet, int row, int column, String text, CellStyle style) {
            Cell c = at(sheet, row, column);
            c.setCellValue(text);
            c.setCellStyle(style);
        }

        void expr(Sheet sheet, int row, int column, String expression, CellStyle style) {
            text(sheet, row, column, expression, style);
        }

        void formula(Sheet sheet, int row, int column, String formula, CellStyle style) {
            Cell c = at(sheet, row, column);
            c.setCellFormula(formula);
            c.setCellStyle(style);
        }

        void header(Sheet sheet, int row, String... titles) {
            for (int i = 0; i < titles.length; i++) text(sheet, row, i, titles[i], head);
        }

        void summary(Sheet sheet, int row, String label, String expression, CellStyle style) {
            text(sheet, row, 0, label, cell);
            expr(sheet, row, 2, expression, style);
        }

        void note(Sheet sheet, int row, String text, int columns) {
            text(sheet, row, 0, text, noteStyle);
            for (int i = 1; i < columns; i++) at(sheet, row, i).setCellStyle(noteStyle);
            sheet.addMergedRegion(new CellRangeAddress(row, row, 0, columns - 1));
            sheet.getRow(row).setHeightInPoints(30);
        }

        void widths(Sheet sheet, int... chars) {
            for (int i = 0; i < chars.length; i++) sheet.setColumnWidth(i, chars[i] * 256);
        }

        private XSSFChart chart(XSSFSheet sheet, int row, int columns, String title) {
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 0, row, columns, row + CHART_ROWS);
            XSSFChart chart = drawing.createChart(anchor);
            chart.setTitleText(title);
            chart.setTitleOverlay(false);
            XDDFChartLegend legend = chart.getOrAddLegend();
            legend.setPosition(LegendPosition.BOTTOM);
            /* the month labels are in hidden helper columns */
            if (chart.getCTChart().isSetPlotVisOnly()) chart.getCTChart().getPlotVisOnly().setVal(false);
            else chart.getCTChart().addNewPlotVisOnly().setVal(false);
            return chart;
        }

        private static XDDFShapeProperties fill(byte[] rgb, boolean line) {
            XDDFShapeProperties props = new XDDFShapeProperties();
            XDDFSolidFillProperties solid = new XDDFSolidFillProperties(XDDFColor.from(rgb));
            if (line) {
                XDDFLineProperties lp = new XDDFLineProperties();
                lp.setFillProperties(solid);
                lp.setWidth(2.25);
                props.setLineProperties(lp);
            } else {
                props.setFillProperties(solid);
            }
            return props;
        }

        void barChart(XSSFSheet sheet, int row, int columns, String title, XSSFSheet data, CellRangeAddress categories,
                      String[] names, CellRangeAddress[] values, byte[][] colors) {
            XSSFChart chart = chart(sheet, row, columns, title);
            XDDFCategoryAxis x = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis y = chart.createValueAxis(AxisPosition.LEFT);
            y.setCrosses(AxisCrosses.AUTO_ZERO);
            y.setNumberFormat("#,##0");
            XDDFBarChartData bars = (XDDFBarChartData) chart.createData(ChartTypes.BAR, x, y);
            bars.setBarDirection(BarDirection.COL);
            bars.setVaryColors(false);
            XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(data, categories);
            for (int i = 0; i < values.length; i++) {
                XDDFChartData.Series series = bars.addSeries(cat, XDDFDataSourcesFactory.fromNumericCellRange(data, values[i]));
                series.setTitle(names[i], null);
                series.setShapeProperties(fill(colors[i], false));
            }
            chart.plot(bars);
        }

        void lineChart(XSSFSheet sheet, int row, int columns, String title, CellRangeAddress categories, String[] names,
                       CellRangeAddress[] values, XSSFSheet data, byte[][] colors) {
            XSSFChart chart = chart(sheet, row, columns, title);
            XDDFCategoryAxis x = chart.createCategoryAxis(AxisPosition.BOTTOM);
            XDDFValueAxis y = chart.createValueAxis(AxisPosition.LEFT);
            y.setCrosses(AxisCrosses.AUTO_ZERO);
            XDDFLineChartData lines = (XDDFLineChartData) chart.createData(ChartTypes.LINE, x, y);
            lines.setVaryColors(false);
            XDDFDataSource<String> cat = XDDFDataSourcesFactory.fromStringCellRange(data, categories);
            for (int i = 0; i < values.length; i++) {
                XDDFLineChartData.Series series = (XDDFLineChartData.Series) lines.addSeries(cat, XDDFDataSourcesFactory.fromNumericCellRange(data, values[i]));
                series.setTitle(names[i], null);
                series.setSmooth(false);
                series.setMarkerStyle(MarkerStyle.CIRCLE);
                series.setMarkerSize((short) 5);
                series.setShapeProperties(fill(colors[i], true));
            }
            chart.plot(lines);
        }

        /** measured values as points, the model as line */
        void scatterChart(XSSFSheet sheet, int row, int columns, String title, XSSFSheet data, CellRangeAddress x,
                          CellRangeAddress measured, CellRangeAddress model) {
            XSSFChart chart = chart(sheet, row, columns, title);
            XDDFValueAxis xAxis = chart.createValueAxis(AxisPosition.BOTTOM);
            xAxis.setTitle("Produktionsmenge");
            XDDFValueAxis yAxis = chart.createValueAxis(AxisPosition.LEFT);
            yAxis.setTitle("Strom Produktion (kWh)");
            yAxis.setCrosses(AxisCrosses.AUTO_ZERO);
            yAxis.setNumberFormat("#,##0");
            XDDFScatterChartData scatter = (XDDFScatterChartData) chart.createData(ChartTypes.SCATTER, xAxis, yAxis);
            scatter.setVaryColors(false);
            XDDFNumericalDataSource<Double> xs = XDDFDataSourcesFactory.fromNumericCellRange(data, x);
            XDDFScatterChartData.Series points = (XDDFScatterChartData.Series) scatter.addSeries(xs, XDDFDataSourcesFactory.fromNumericCellRange(data, measured));
            points.setTitle("Messwerte", null);
            points.setMarkerStyle(MarkerStyle.CIRCLE);
            points.setMarkerSize((short) 7);
            XDDFShapeProperties noLine = new XDDFShapeProperties();
            XDDFLineProperties none = new XDDFLineProperties();
            none.setFillProperties(new XDDFNoFillProperties());
            noLine.setLineProperties(none);
            points.setShapeProperties(noLine);
            XDDFScatterChartData.Series line = (XDDFScatterChartData.Series) scatter.addSeries(xs, XDDFDataSourcesFactory.fromNumericCellRange(data, model));
            line.setTitle("Modell", null);
            line.setMarkerStyle(MarkerStyle.NONE);
            line.setShapeProperties(fill(SUN, true));
            chart.plot(scatter);
            /* marker colour of the measured values */
            points.getCTScatterSer().getMarker().addNewSpPr().addNewSolidFill().addNewSrgbClr().setVal(BLUEBERRY);
        }

        /** JXLS area over the sheet (comment in A1) */
        void area(Sheet sheet, String lastCell) {
            comment(sheet, 0, 0, "jx:area(lastCell=\"" + lastCell + "\")");
            /* A4, one page wide (as many pages high as needed), landscape when wider than 6 columns */
            int columns = lastCell.charAt(0) - 'A' + 1;
            PrintSetup print = sheet.getPrintSetup();
            print.setPaperSize(PrintSetup.A4_PAPERSIZE);
            print.setLandscape(columns > 6);
            print.setFitWidth((short) 1);
            print.setFitHeight((short) 0);
            sheet.setFitToPage(true);
            sheet.setAutobreaks(true);
            sheet.setMargin(Sheet.LeftMargin, 0.6);
            sheet.setMargin(Sheet.RightMargin, 0.6);
            /* page footer: sheet name left, page numbers right */
            sheet.getFooter().setLeft("&A");
            sheet.getFooter().setRight("Seite &P von &N");
        }

        /** JXLS each over one row */
        void each(Sheet sheet, int row, int column, String items, String var, String varIndex, String lastCell) {
            comment(sheet, row, column, "jx:each(items=\"" + items + "\" var=\"" + var + "\""
                    + (varIndex != null ? " varIndex=\"" + varIndex + "\"" : "") + " lastCell=\"" + lastCell + "\")");
        }

        private void comment(Sheet sheet, int row, int column, String text) {
            Cell c = at(sheet, row, column);
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            ClientAnchor anchor = helper.createClientAnchor();
            anchor.setCol1(column);
            anchor.setRow1(row);
            anchor.setCol2(column + 3);
            anchor.setRow2(row + 2);
            Comment comment = c.getCellComment();
            if (comment != null) {
                comment.setString(helper.createRichTextString(comment.getString().getString() + "\n" + text));
                return;
            }
            comment = drawing.createCellComment(anchor);
            comment.setString(helper.createRichTextString(text));
            c.setCellComment(comment);
        }
    }
}
