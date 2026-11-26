/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * HTTP channel of the certificate transfer: POST of the export as request body ({@code Content-Type} of the format,
 * {@code Content-Disposition} with the file name, optional {@code Authorization: Bearer}). Only http and https;
 * certificates and host names are checked by the JVM (no trust-all). A 2xx answer is success.
 */
public final class CertificateHttp {

    static final int TIMEOUT_MS = 30_000;

    private CertificateHttp() {
    }

    public static void post(String url, String token, String fileName, byte[] content, String contentType) throws IOException {
        URL target = new URL(url);
        String protocol = target.getProtocol().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equals(protocol) && !"http".equals(protocol)) throw new IOException("Only http and https URLs");
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(content.length);
            connection.setRequestProperty("Content-Type", contentType);
            connection.setRequestProperty("Content-Disposition", "attachment; filename*=UTF-8''"
                    + URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20"));
            connection.setRequestProperty("User-Agent", "JEVis");
            if (token != null && !token.trim().isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            try (OutputStream out = connection.getOutputStream()) {
                out.write(content);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status + " " + safe(connection.getResponseMessage()));
            drain(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private static void drain(InputStream in) throws IOException {
        if (in == null) return;
        try (InputStream i = in) {
            byte[] buffer = new byte[4096];
            while (i.read(buffer) != -1) {
                /* discard */
            }
        }
    }

    private static String safe(String s) {
        return s != null ? s : "";
    }
}
