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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * Accepted certificates go into the user's trust store, never into the Java installation.
 */
public class UserTrustStoreTest {

    private String userHome;
    private File tempHome;
    private SSLContext defaultContext;

    @Before
    public void setUp() throws Exception {
        userHome = System.getProperty("user.home");
        tempHome = Files.createTempDirectory("jevis-truststore").toFile();
        System.setProperty("user.home", tempHome.getAbsolutePath());
        defaultContext = SSLContext.getDefault();
    }

    @After
    public void tearDown() {
        System.setProperty("user.home", userHome);
        SSLContext.setDefault(defaultContext);
        System.clearProperty("javax.net.ssl.trustStore");
        System.clearProperty("javax.net.ssl.trustStorePassword");
        System.clearProperty("javax.net.ssl.trustStoreType");
    }

    @Test
    public void certificateIsAddedToTheUserStoreOnly() throws Exception {
        File javaStore = UserTrustStore.getJavaTrustStore();
        long javaStoreModified = javaStore.lastModified();
        KeyStore java = UserTrustStore.load(javaStore);
        String someAlias = Collections.list(java.aliases()).get(0);
        X509Certificate certificate = (X509Certificate) java.getCertificate(someAlias);

        assertFalse(UserTrustStore.getFile().exists());
        UserTrustStore.addCertificate("jevis.example.org-20261104", certificate);

        File userStore = UserTrustStore.getFile();
        assertTrue(userStore.isFile());
        assertTrue(userStore.getAbsolutePath().startsWith(tempHome.getAbsolutePath()));
        KeyStore saved = UserTrustStore.load(userStore);
        assertTrue(saved.containsAlias("jevis.example.org-20261104"));
        assertEquals("all certificates of the Java installation are kept", java.size() + 1, saved.size());
        assertEquals(javaStoreModified, javaStore.lastModified());
        assertEquals(userStore.getAbsolutePath(), System.getProperty("javax.net.ssl.trustStore"));

        /* a second certificate is added to the existing user store */
        UserTrustStore.addCertificate("second-20261104", certificate);
        assertEquals(java.size() + 2, UserTrustStore.load(userStore).size());
        assertEquals(java.size() + 2, UserTrustStore.loadCurrent().size());
    }

    @Test
    public void installWithoutUserStoreChangesNothing() throws Exception {
        UserTrustStore.install();
        assertNull(System.getProperty("javax.net.ssl.trustStore"));
        assertSame(defaultContext, SSLContext.getDefault());
    }
}
