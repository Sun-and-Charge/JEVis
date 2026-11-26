/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JENotifier.
 * <p>
 * JENotifier is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JENotifier is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JENotifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JENotifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier;

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.*;

/** SFTP channel of the energy certificate transfer against a local MINA server (no network). */
public class CertificateSftpTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private SshServer server(File root) throws IOException {
        SshServer sshd = SshServer.setUpDefaultServer();
        sshd.setHost("127.0.0.1");
        sshd.setPort(0);
        sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());
        sshd.setPasswordAuthenticator((user, password, session) -> "demo".equals(user) && "geheim".equals(password));
        sshd.setSubsystemFactories(Collections.singletonList(new SftpSubsystemFactory()));
        sshd.setFileSystemFactory(new VirtualFileSystemFactory(root.toPath()));
        sshd.start();
        return sshd;
    }

    @Test
    public void uploadsAndPinsTheServerKey() throws Exception {
        File root = folder.newFolder("root");
        new File(root, "in").mkdirs();
        SshServer sshd = server(root);
        String key;
        try {
            key = CertificateSftp.upload("127.0.0.1", sshd.getPort(), "demo", "geheim", "/in/", null,
                    "Energieausweise.csv", "a;b".getBytes(StandardCharsets.UTF_8));
            assertNotNull(key);
            assertTrue(key.startsWith("SHA256:"));
            assertEquals("a;b", new String(Files.readAllBytes(new File(root, "in/Energieausweise.csv").toPath()), StandardCharsets.UTF_8));
            assertFalse("no temporary file left", new File(root, "in/Energieausweise.csv.part").exists());

            /* the same key is accepted again */
            assertEquals(key, CertificateSftp.upload("127.0.0.1", sshd.getPort(), "demo", "geheim", "in", key,
                    "Energieausweise2.csv", new byte[]{1}));
            try {
                CertificateSftp.upload("127.0.0.1", sshd.getPort(), "demo", "falsch", "in", key, "x.csv", new byte[]{1});
                fail("wrong password must fail");
            } catch (IOException expected) {
                /* ok */
            }
        } finally {
            sshd.stop(true);
        }

        /* another server key: refused */
        SshServer other = server(root);
        try {
            CertificateSftp.upload("127.0.0.1", other.getPort(), "demo", "geheim", "in", "SHA256:something-else",
                    "y.csv", new byte[]{1});
            fail("a changed server key must be refused");
        } catch (IOException expected) {
            assertFalse(new File(root, "in/y.csv").exists());
        } finally {
            other.stop(true);
        }
    }
}
