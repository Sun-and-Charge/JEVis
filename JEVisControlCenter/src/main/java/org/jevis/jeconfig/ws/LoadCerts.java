/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.commons.i18n.I18n;
import org.joda.time.DateTime;

import javax.net.ssl.*;
import java.io.*;
import java.net.URL;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;


public class LoadCerts extends Application {

    private static final char[] HEXDIGITS = "0123456789abcdef".toCharArray();
    private static final Logger logger = LogManager.getLogger(LoadCerts.class);

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int b : bytes) {
            b &= 0xff;
            sb.append(HEXDIGITS[b >> 4]);
            sb.append(HEXDIGITS[b & 15]);
            sb.append(' ');
        }
        return sb.toString();
    }

    public void importCertificate(URL destinationURL, String keystore, String pw, boolean isUI, boolean autoAccept) throws Exception {
        String host = destinationURL.getHost();
        char[] passphrase = pw.toCharArray();
        boolean isQuiet = autoAccept;

        /* the trust store of the user (accepted certificates) or the one of the Java installation; written is only the
         * one of the user, the Java installation may need administrator rights (C:\Program Files) */
        KeyStore ks;
        if (keystore != null && new File(keystore).isFile()) {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = new FileInputStream(keystore)) {
                ks.load(in, passphrase);
            }
        } else {
            ks = UserTrustStore.loadCurrent();
        }

        logger.debug("host: {}, Keystore: {}", host, keystore);

        SSLContext context = SSLContext.getInstance("TLS");
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
        SaveTrustManager tm = new SaveTrustManager(defaultTrustManager);
        context.init(null, new TrustManager[]{tm}, null);
        SSLSocketFactory factory = context.getSocketFactory();

        /* "https://host" without a port has getPort() == -1 */
        int port = destinationURL.getPort() > 0 ? destinationURL.getPort() : destinationURL.getDefaultPort();
        logger.debug("Opening connection to: {}, Port: {}", host, port);

        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);
        socket.setSoTimeout(10000);

        try {
            logger.info("Initiating SSL handshake...");
            socket.startHandshake();
            socket.close();
            logger.info("Certificate is already trusted");
        } catch (SSLException e) {
            logger.debug(e, e);
            //e.printStackTrace(System.out);
        }

        X509Certificate[] chain = tm.chain;
        if (chain == null) {
            logger.error("Could not obtain server certificate chain");
            return;
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        logger.debug("Server sent {} certificate", chain.length);

        MessageDigest sha1 = MessageDigest.getInstance("SHA1");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        for (int i = 0; i < chain.length; i++) {
            X509Certificate cert = chain[i];
            sha1.update(cert.getEncoded());
            md5.update(cert.getEncoded());
            System.out.println();
            logger.debug("Subject: {}\nIssuer: {}\nsha1: {}", cert.getSubjectDN(), cert.getIssuerDN(), toHexString(sha1.digest()));
        }

        boolean userSasYes = false;
        if (isUI) {
            try {
                X509Certificate cert = chain[0];

                Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
                String message = cert.getSubjectDN().getName() + "\n" +
                        cert.getIssuerDN().getName() + "\n" +
                        "SHA1: " + toHexString(sha1.digest()) + "\n" +
                        "MD5:  " + toHexString(md5.digest());
                dialog.setTitle(I18n.getInstance().getString("app.login.certificate.import.title"));
                dialog.setHeaderText(I18n.getInstance().getString("app.login.certificate.import.header")
                        + "\n" +
                        I18n.getInstance().getString("app.login.certificate.import.header2")
                );

                dialog.setContentText(message);
                dialog.setResizable(true);
                dialog.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                dialog.getDialogPane().setPrefSize(680, 320);
                Optional<ButtonType> result = dialog.showAndWait();
                if (result.get() == ButtonType.OK) {
                    userSasYes = true;
                } else {
                    //
                }

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (isQuiet || userSasYes) {
            X509Certificate cert = chain[0];
            String alias = host + "-" + DateTime.now().toString("yyyyMMdd");
            /* stored for the current user and used right away, no restart needed */
            UserTrustStore.addCertificate(alias, cert);
            logger.info("cert: {}", cert);
        }


    }

    @Override
    public void start(Stage stage) throws Exception {
    }

    //Inner class to save the default trust manager
    private static class SaveTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SaveTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            // This change has been done due to the following resolution advised for Java 1.7+
            // http://infposs.blogspot.kr/2013/06/installcert-and-java-7.html
            return new X509Certificate[0];
            //throw new UnsupportedOperationException();
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }
}
