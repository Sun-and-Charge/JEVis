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
package org.jevis.jeconfig.ws;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.URL;

public class CertificateImporter {

    private static final Logger logger = LogManager.getLogger(CertificateImporter.class);

    public void checkAndImport(URL destinationURL, String keystore, String keystorepw, boolean isUI, boolean autoAccept) throws Exception {
        boolean needSSL = needSSLDownload(destinationURL);
        if (needSSL) {
            try {
                LoadCerts loadCerts = new LoadCerts();
                loadCerts.importCertificate(destinationURL, keystore, keystorepw, isUI, autoAccept);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    public boolean needSSLDownload(URL destinationURL) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection) destinationURL.openConnection();
            conn.connect();
            return false;
        } catch (SSLHandshakeException sslEx) {
            logger.warn("SSL Error: {}", sslEx.getMessage());
            return true;
        } catch (IOException e) {
            return false;
        }

    }
}
