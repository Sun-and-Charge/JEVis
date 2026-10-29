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

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import org.jevis.jeconfig.tool.QrCodes;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MeterQrCodeTest {

    @Test
    public void payloadWithTargetAndServer() {
        assertEquals("jevis:meter?v=1&id=4711&target=5123:Value&server=https%3A%2F%2Fsunandcharge.app%3A443",
                MeterQrCode.payload(4711, 5123L, "Value", "https://sunandcharge.app:443/"));
    }

    @Test
    public void payloadEncodesAttributeNames() {
        assertEquals("jevis:meter?v=1&id=1&target=2:Raw%20Data", MeterQrCode.payload(1, 2L, "Raw Data", null));
    }

    @Test
    public void payloadWithoutTargetAndServer() {
        assertEquals("jevis:meter?v=1&id=42", MeterQrCode.payload(42, null, null, " "));
    }

    @Test
    public void qrCodeDecodesToPayload() throws Exception {
        String payload = MeterQrCode.payload(4711, 5123L, "Value", "https://sunandcharge.app:443");
        BitMatrix matrix = QrCodes.encode(payload, 300);
        int[] pixels = new int[matrix.getWidth() * matrix.getHeight()];
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                pixels[y * matrix.getWidth() + x] = matrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            }
        }
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(matrix.getWidth(), matrix.getHeight(), pixels)));
        assertEquals(payload, new QRCodeReader().decode(bitmap).getText());
    }

    @Test
    public void labelPdfHasOnePagePerTwentyFourMeters() throws Exception {
        MeterQrCode code = new MeterQrCode(4711, MeterQrCode.payload(4711, 5123L, "Value", null),
                "Gaszähler Muster", "Messpunkt A", "SN-0001", "Standort / Gebäude");
        MeterQrCode[] codes = new MeterQrCode[MeterLabelPdf.COLUMNS * MeterLabelPdf.ROWS + 1];
        Arrays.fill(codes, code);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new MeterLabelPdf().write(Arrays.asList(codes), out);

        String pdf = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        assertTrue(pdf.startsWith("%PDF"));
        assertTrue(pdf.contains("/Count 2"));
    }
}
