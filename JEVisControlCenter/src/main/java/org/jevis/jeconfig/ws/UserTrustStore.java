/**
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

/**
 * Trust store of the current user for server certificates accepted in the Control Center.
 * <p>
 * Accepted certificates used to be written into the cacerts file of the Java installation, which needs administrator
 * rights when Java is installed under "C:\Program Files" ("Zugriff verweigert"). They are now stored in
 * {@code <user home>/.jevis/truststore.jks}: a copy of the Java cacerts plus the accepted certificates. It is used for
 * all HTTPS connections of the Control Center from the start of the program and right after an import.
 */
public class UserTrustStore {

    private static final Logger logger = LogManager.getLogger(UserTrustStore.class);
    private static final String PASSWORD = "changeit";

    private UserTrustStore() {
    }

    public static File getFile() {
        return new File(System.getProperty("user.home") + File.separator + ".jevis", "truststore.jks");
    }

    /**
     * The cacerts (or jssecacerts) file of the running Java installation.
     */
    public static File getJavaTrustStore() {
        File dir = new File(System.getProperty("java.home") + File.separator + "lib" + File.separator + "security");
        File jssecacerts = new File(dir, "jssecacerts");
        return jssecacerts.isFile() ? jssecacerts : new File(dir, "cacerts");
    }

    /**
     * Uses the trust store of the user, if there is one, for the HTTPS connections of this program. Called at the
     * start of the program, before the first connection to the server.
     */
    public static void install() {
        File file = getFile();
        if (!file.isFile()) {
            return;
        }
        try {
            KeyStore keyStore = load(file);
            apply(keyStore);
            System.setProperty("javax.net.ssl.trustStore", file.getAbsolutePath());
            System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);
            System.setProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
            logger.info("Using the trust store of the user: {}", file);
        } catch (Exception ex) {
            logger.error("Could not use the trust store of the user {}", file, ex);
        }
    }

    /**
     * @return the user's trust store if it exists, otherwise the trust store of the Java installation
     */
    public static KeyStore loadCurrent() throws Exception {
        File file = getFile();
        return load(file.isFile() ? file : getJavaTrustStore());
    }

    /**
     * Adds an accepted certificate to the user's trust store (created from the Java cacerts if it does not exist yet)
     * and uses it right away, so no restart and no administrator rights are needed.
     */
    public static void addCertificate(String alias, X509Certificate certificate) throws Exception {
        KeyStore keyStore = loadCurrent();
        keyStore.setCertificateEntry(alias, certificate);
        save(keyStore, getFile());
        apply(keyStore);
        System.setProperty("javax.net.ssl.trustStore", getFile().getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", PASSWORD);
        System.setProperty("javax.net.ssl.trustStoreType", KeyStore.getDefaultType());
        logger.info("Certificate {} added to the trust store of the user {}", alias, getFile());
    }

    static KeyStore load(File file) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (InputStream in = new FileInputStream(file)) {
            keyStore.load(in, PASSWORD.toCharArray());
        }
        return keyStore;
    }

    static void save(KeyStore keyStore, File file) throws Exception {
        File dir = file.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("Could not create " + dir);
        }
        File temp = new File(dir, file.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(temp)) {
            keyStore.store(out, PASSWORD.toCharArray());
        }
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void apply(KeyStore keyStore) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), null);
        SSLContext.setDefault(context);
        HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
    }
}
