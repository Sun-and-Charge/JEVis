/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * The route sheet of a reading round (stage E8.2, {@code GET /web/reading-rounds/{id}/sheet.pdf}): A4 portrait, a
 * header with the round, one block per meter with QR code, last reading, expected range and empty boxes for the
 * reading, the date, the initials and a remark; blocks are not split across pages, footer "Seite x von y".
 * <p>
 * Pure layout: the input ({@link Sheet}) holds the texts already formatted, so the class is tested without a store.
 * Helvetica (standard 14 font, WinAnsiEncoding) covers the German umlauts and ß; other characters are printed as "?".
 */
final class RouteSheetPdf {

    static final float MARGIN = 40f;
    static final float FOOTER_Y = 24f;
    static final float BOTTOM = 46f;
    static final float ROW_HEIGHT = 96f;
    static final float QR_SIZE = 72f;
    static final int MAX_READER_LINES = 3;
    static final int MAX_REMARK_LINES = 6;

    private static final Map<String, String[]> TEXTS = new HashMap<>();

    static {
        TEXTS.put("title", new String[]{"Laufzettel", "Route sheet"});
        TEXTS.put("site", new String[]{"Standort", "Site"});
        TEXTS.put("due", new String[]{"Fällig", "Due"});
        TEXTS.put("period", new String[]{"Turnus", "Period"});
        TEXTS.put("readers", new String[]{"Ableser", "Readers"});
        TEXTS.put("remarks", new String[]{"Bemerkungen", "Remarks"});
        TEXTS.put("printed", new String[]{"Gedruckt", "Printed"});
        TEXTS.put("location", new String[]{"Ort", "Location"});
        TEXTS.put("serial", new String[]{"Zählernr.", "Serial no."});
        TEXTS.put("meterPoint", new String[]{"Zählpunkt", "Meter point"});
        TEXTS.put("last", new String[]{"Letzter Stand", "Last reading"});
        TEXTS.put("expected", new String[]{"Erwartet", "Expected"});
        TEXTS.put("reading", new String[]{"Zählerstand", "Reading"});
        TEXTS.put("date", new String[]{"Datum", "Date"});
        TEXTS.put("initials", new String[]{"Kürzel", "Initials"});
        TEXTS.put("remark", new String[]{"Bemerkung", "Remark"});
        TEXTS.put("page", new String[]{"Seite %d von %d", "Page %d of %d"});
        TEXTS.put("hidden", new String[]{"%d Zähler ohne Leserecht nicht aufgeführt", "%d meters without read right not listed"});
        TEXTS.put("none", new String[]{"Keine Zähler abzulesen", "No meters to read"});
    }

    private RouteSheetPdf() {
    }

    /**
     * One meter of the sheet; all texts formatted, null if unknown.
     */
    static final class Row {
        int position;
        String name;
        String location;
        String serialNumber;
        String meterPoint;
        /**
         * content of the QR code (link to the reading page of the meter), null for none
         */
        String link;
        /**
         * last reading with unit and date, e.g. {@code "12.345,6 kWh (02.11.2026)"}
         */
        String last;
        /**
         * expected range with unit, e.g. {@code "12.100 … 12.600 kWh"}
         */
        String expected;
    }

    /**
     * The whole sheet; all texts formatted.
     */
    static final class Sheet {
        /**
         * {@code de} or anything else (English)
         */
        String language = "de";
        String roundName;
        String site;
        String due;
        String period;
        List<String> readers = new ArrayList<>();
        String remarks;
        String printed;
        /**
         * number of meters of the round the user may not read (left out)
         */
        int hidden;
        List<Row> rows = new ArrayList<>();
    }

    static String text(String key, String language) {
        String[] texts = TEXTS.get(key);
        return "de".equals(language) ? texts[0] : texts[1];
    }

    /**
     * Fonts and the content stream of the page being drawn.
     */
    private static final class Canvas {
        final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDPageContentStream stream;

        void text(PDFont font, float size, float x, float y, String text) throws IOException {
            if (text == null || text.isEmpty()) return;
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y);
            stream.showText(text);
            stream.endText();
        }
    }

    /**
     * @return the PDF
     */
    static byte[] render(Sheet sheet) throws IOException {
        String lang = sheet.language;
        try (PDDocument document = new PDDocument()) {
            Canvas canvas = new Canvas();
            PDRectangle size = PDRectangle.A4;
            float width = size.getWidth() - 2 * MARGIN;
            float top = size.getHeight() - MARGIN;

            /* header of the first page */
            List<String[]> info = new ArrayList<>();
            if (sheet.site != null) info.add(new String[]{text("site", lang), sheet.site});
            String due = sheet.due != null ? sheet.due : "";
            if (sheet.period != null) due += " (" + text("period", lang) + ": " + sheet.period + ")";
            info.add(new String[]{text("due", lang), due});
            float labelWidth = 70f;
            List<String> readerLines = wrap(canvas.regular, 9, String.join(", ", sheet.readers), width - labelWidth, MAX_READER_LINES);
            List<String> remarkLines = sheet.remarks != null
                    ? wrap(canvas.regular, 9, sheet.remarks, width - labelWidth, MAX_REMARK_LINES) : Collections.emptyList();
            float firstHeader = 22 + 18 + 12 * info.size() + 12 * Math.max(1, readerLines.size()) + 12 * remarkLines.size()
                    + 12 + (sheet.hidden > 0 ? 12 : 0) + 10;
            float otherHeader = 22;

            int perFirst = Math.max(1, (int) Math.floor((top - firstHeader - BOTTOM) / ROW_HEIGHT));
            int perOther = Math.max(1, (int) Math.floor((top - otherHeader - BOTTOM) / ROW_HEIGHT));
            int rows = sheet.rows.size();
            int pages = rows <= perFirst ? 1 : 1 + (int) Math.ceil((rows - perFirst) / (double) perOther);

            int index = 0;
            for (int page = 1; page <= pages; page++) {
                PDPage pdPage = new PDPage(size);
                document.addPage(pdPage);
                try (PDPageContentStream stream = new PDPageContentStream(document, pdPage)) {
                    canvas.stream = stream;
                    float y = top;
                    if (page == 1) {
                        canvas.text(canvas.bold, 16, MARGIN, y - 16, fit(canvas.bold, 16, text("title", lang), width));
                        y -= 22;
                        canvas.text(canvas.bold, 13, MARGIN, y - 13, fit(canvas.bold, 13, sheet.roundName, width));
                        y -= 18;
                        for (String[] line : info) {
                            y = labelLine(canvas, y, line[0], Collections.singletonList(fit(canvas.regular, 9, line[1], width - labelWidth)), labelWidth);
                        }
                        y = labelLine(canvas, y, text("readers", lang), readerLines.isEmpty() ? Collections.singletonList("–") : readerLines, labelWidth);
                        if (!remarkLines.isEmpty()) y = labelLine(canvas, y, text("remarks", lang), remarkLines, labelWidth);
                        y = labelLine(canvas, y, text("printed", lang), Collections.singletonList(fit(canvas.regular, 9, sheet.printed, width - labelWidth)), labelWidth);
                        if (sheet.hidden > 0) {
                            canvas.text(canvas.regular, 9, MARGIN, y - 9, fit(canvas.regular, 9, String.format(text("hidden", lang), sheet.hidden), width));
                            y -= 12;
                        }
                        y -= 4;
                    } else {
                        String head = text("title", lang) + " – " + safe(canvas.bold, sheet.roundName) + (sheet.due != null ? " – " + sheet.due : "");
                        canvas.text(canvas.bold, 9, MARGIN, y - 9, fit(canvas.bold, 9, head, width));
                        y -= 16;
                    }
                    stream.setLineWidth(0.8f);
                    stream.moveTo(MARGIN, y);
                    stream.lineTo(MARGIN + width, y);
                    stream.stroke();
                    y -= 6;

                    if (rows == 0 && page == 1) {
                        canvas.text(canvas.regular, 10, MARGIN, y - 14, text("none", lang));
                    }
                    int count = page == 1 ? perFirst : perOther;
                    for (int i = 0; i < count && index < rows; i++, index++) {
                        drawRow(canvas, sheet.rows.get(index), MARGIN, y, width, lang);
                        y -= ROW_HEIGHT;
                    }
                    String footer = String.format(text("page", lang), page, pages);
                    float footerWidth = width(canvas.regular, 8, footer);
                    canvas.text(canvas.regular, 8, MARGIN + width - footerWidth, FOOTER_Y, footer);
                    canvas.text(canvas.regular, 8, MARGIN, FOOTER_Y, fit(canvas.regular, 8, safe(canvas.regular, sheet.roundName), width - footerWidth - 20));
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * Draws "label: line" and further lines below the first, indented.
     *
     * @return the y below the lines
     */
    private static float labelLine(Canvas canvas, float y, String label, List<String> lines, float labelWidth) throws IOException {
        canvas.text(canvas.bold, 9, MARGIN, y - 9, safe(canvas.bold, label) + ":");
        for (String line : lines) {
            canvas.text(canvas.regular, 9, MARGIN + labelWidth, y - 9, line);
            y -= 12;
        }
        return y;
    }

    private static void drawRow(Canvas canvas, Row row, float x, float top, float width, String lang) throws IOException {
        PDPageContentStream stream = canvas.stream;
        float textWidth = width - QR_SIZE - 8;
        String title = row.position + ". " + (row.name != null ? row.name : "");
        canvas.text(canvas.bold, 10, x, top - 12, fit(canvas.bold, 10, title, textWidth));

        List<String> details = new ArrayList<>();
        if (row.location != null) details.add(text("location", lang) + ": " + row.location);
        if (row.serialNumber != null) details.add(text("serial", lang) + ": " + row.serialNumber);
        if (row.meterPoint != null) details.add(text("meterPoint", lang) + ": " + row.meterPoint);
        canvas.text(canvas.regular, 8, x, top - 24, fit(canvas.regular, 8, String.join("  |  ", details), textWidth));

        List<String> values = new ArrayList<>();
        values.add(text("last", lang) + ": " + (row.last != null ? row.last : "–"));
        values.add(text("expected", lang) + ": " + (row.expected != null ? row.expected : "–"));
        canvas.text(canvas.regular, 8, x, top - 35, fit(canvas.regular, 8, String.join("  |  ", values), textWidth));

        /* the boxes to fill in */
        float boxTop = top - 42;
        float boxHeight = 42;
        float[] shares = {0.30f, 0.20f, 0.14f, 0.36f};
        String[] labels = {text("reading", lang), text("date", lang), text("initials", lang), text("remark", lang)};
        float boxX = x;
        stream.setLineWidth(0.6f);
        for (int i = 0; i < shares.length; i++) {
            float boxWidth = textWidth * shares[i];
            stream.addRect(boxX, boxTop - boxHeight, boxWidth - 4, boxHeight);
            stream.stroke();
            canvas.text(canvas.regular, 6.5f, boxX + 3, boxTop - 8, fit(canvas.regular, 6.5f, labels[i], boxWidth - 10));
            boxX += boxWidth;
        }

        if (row.link != null) {
            try {
                drawQr(stream, ResourceWebRegisters.encode(row.link), x + width - QR_SIZE, top - 4, QR_SIZE);
            } catch (Exception e) {
                /* no QR code for this link (too long): the block stays usable without it */
            }
        }

        stream.setLineWidth(0.3f);
        stream.moveTo(x, top - ROW_HEIGHT + 4);
        stream.lineTo(x + width, top - ROW_HEIGHT + 4);
        stream.stroke();
    }

    /**
     * Draws the dark modules of the code as filled rectangles (runs of a row joined).
     *
     * @param top the upper edge
     */
    static void drawQr(PDPageContentStream stream, BitMatrix matrix, float x, float top, float size) throws IOException {
        int modules = Math.max(matrix.getWidth(), matrix.getHeight());
        float module = size / modules;
        for (int row = 0; row < matrix.getHeight(); row++) {
            int column = 0;
            while (column < matrix.getWidth()) {
                if (!matrix.get(column, row)) {
                    column++;
                    continue;
                }
                int start = column;
                while (column < matrix.getWidth() && matrix.get(column, row)) column++;
                stream.addRect(x + start * module, top - (row + 1) * module, (column - start) * module, module);
            }
        }
        stream.fill();
    }

    // ---------------------------------------------------------------- text

    /**
     * @return the text with line breaks and tabs as spaces and the characters the font cannot show as "?"
     */
    static String safe(PDFont font, String text) {
        if (text == null) return "";
        StringBuilder result = new StringBuilder();
        text.codePoints().forEach(cp -> {
            if (cp == '\r' || cp == '\n' || cp == '\t') {
                result.append(' ');
                return;
            }
            if (Character.isISOControl(cp)) return;
            String ch = new String(Character.toChars(cp));
            try {
                font.encode(ch);
                result.append(ch);
            } catch (IllegalArgumentException | IOException e) {
                result.append('?');
            }
        });
        return result.toString();
    }

    static float width(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /**
     * @return the safe text, cut with "…" to fit the width
     */
    static String fit(PDFont font, float size, String text, float maxWidth) throws IOException {
        String safe = safe(font, text);
        if (width(font, size, safe) <= maxWidth) return safe;
        String cut = safe;
        while (!cut.isEmpty() && width(font, size, cut + "…") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
    }

    /**
     * @return the safe text wrapped at spaces and line breaks to the width, at most {@code maxLines} lines (the last
     * one cut with "…" if there is more)
     */
    static List<String> wrap(PDFont font, float size, String text, float maxWidth, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return lines;
        boolean more = false;
        outer:
        for (String paragraph : text.split("\\r?\\n|\\r")) {
            StringBuilder line = new StringBuilder();
            for (String word : safe(font, paragraph).trim().split(" +")) {
                if (word.isEmpty()) continue;
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(font, size, candidate) <= maxWidth || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    if (lines.size() == maxLines) {
                        more = true;
                        break outer;
                    }
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                if (lines.size() == maxLines) {
                    more = true;
                    break;
                }
                lines.add(line.toString());
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean last = i == lines.size() - 1;
            lines.set(i, last && more ? fit(font, size, line + " …", maxWidth) : fit(font, size, line, maxWidth));
        }
        return lines;
    }
}
