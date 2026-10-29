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
package org.jevis.jeconfig.plugin.meters.qr;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.itextpdf.text.pdf.qrcode.EncodeHintType;
import com.itextpdf.text.pdf.qrcode.ErrorCorrectionLevel;
import org.jevis.commons.i18n.I18n;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes QR code labels for meters to an A4 PDF.
 * <p>
 * Layout: 3 x 8 labels of 70 x 37 mm without gaps (e.g. Avery Zweckform 3474). Adjust the constants for other sheets.
 */
public class MeterLabelPdf {

    static final int COLUMNS = 3;
    static final int ROWS = 8;
    private static final float LABEL_WIDTH = mm(70);
    private static final float LABEL_HEIGHT = mm(37);
    private static final float PAGE_MARGIN_LEFT = mm(0);
    private static final float PAGE_MARGIN_TOP = mm(0.5f);
    private static final float PADDING = mm(3);
    private static final float QR_SIZE = mm(31);
    private static final float GAP = mm(2);

    private final Font nameFont;
    private final Font textFont;
    private final Font pathFont;

    public MeterLabelPdf() throws IOException, DocumentException {
        BaseFont regular = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        BaseFont bold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        nameFont = new Font(bold, 8);
        textFont = new Font(regular, 7);
        pathFont = new Font(regular, 6, Font.NORMAL, BaseColor.DARK_GRAY);
    }

    private static float mm(float millimeters) {
        return Utilities.millimetersToPoints(millimeters);
    }

    public void write(List<MeterQrCode> codes, File file) throws IOException, DocumentException {
        try (OutputStream out = new FileOutputStream(file)) {
            write(codes, out);
        }
    }

    public void write(List<MeterQrCode> codes, OutputStream out) throws DocumentException {
        Document document = new Document(PageSize.A4, 0, 0, 0, 0);
        PdfWriter writer = PdfWriter.getInstance(document, out);
        document.addTitle(I18n.getInstance().getString("plugin.meters.qr.labels.title"));
        document.open();
        PdfContentByte cb = writer.getDirectContent();

        int perPage = COLUMNS * ROWS;
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0 && i % perPage == 0) document.newPage();
            int slot = i % perPage;
            float x = PAGE_MARGIN_LEFT + (slot % COLUMNS) * LABEL_WIDTH;
            float top = PageSize.A4.getHeight() - PAGE_MARGIN_TOP - (slot / COLUMNS) * LABEL_HEIGHT;
            drawLabel(cb, codes.get(i), x, top - LABEL_HEIGHT);
        }
        if (codes.isEmpty()) document.add(new Paragraph(" "));
        document.close();
    }

    private void drawLabel(PdfContentByte cb, MeterQrCode code, float x, float y) throws DocumentException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BarcodeQRCode qr = new BarcodeQRCode(code.getPayload(), 1, 1, hints);
        float modules = qr.getBarcodeSize().getWidth();
        PdfTemplate template = cb.createTemplate(QR_SIZE, QR_SIZE);
        qr.placeBarcode(template, BaseColor.BLACK, QR_SIZE / modules);
        float qrY = y + (LABEL_HEIGHT - QR_SIZE) / 2;
        cb.addTemplate(template, x + PADDING, qrY);

        float textLeft = x + PADDING + QR_SIZE + GAP;
        ColumnText column = new ColumnText(cb);
        column.setSimpleColumn(textLeft, y + PADDING, x + LABEL_WIDTH - PADDING, y + LABEL_HEIGHT - PADDING);
        column.addElement(paragraph(code.getName(), nameFont));
        if (!code.getMeasuringPointName().isEmpty()) column.addElement(paragraph(code.getMeasuringPointName(), textFont));
        if (!code.getSerialNumber().isEmpty()) {
            column.addElement(paragraph(I18n.getInstance().getString("plugin.meters.qr.serial") + " " + code.getSerialNumber(), textFont));
        }
        column.addElement(paragraph("ID " + code.getMeterId(), textFont));
        if (!code.getPath().isEmpty()) column.addElement(paragraph(code.getPath(), pathFont));
        column.go();
    }

    private static Paragraph paragraph(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setLeading(font.getSize() * 1.2f);
        paragraph.setSpacingAfter(1);
        return paragraph;
    }
}
