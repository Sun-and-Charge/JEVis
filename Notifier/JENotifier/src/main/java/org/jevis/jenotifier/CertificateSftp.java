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

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.KeyIdentityProvider;
import org.apache.sshd.sftp.client.SftpClient;
import org.apache.sshd.sftp.client.SftpClientFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SFTP channel of the energy certificate transfer (BA-07): password login, upload into the directory under a
 * temporary name and rename, so the receiver never reads a half-written file. The server key is accepted on first use
 * (its SHA-256 fingerprint is then stored by the caller) and must stay the same afterwards.
 */
final class CertificateSftp {

    static final Duration TIMEOUT = Duration.ofSeconds(30);

    private CertificateSftp() {
    }

    /**
     * @return the fingerprint of the server key
     */
    static String upload(String host, int port, String user, String password, String directory, String knownHostKey,
                         String fileName, byte[] content) throws IOException {
        AtomicReference<String> seen = new AtomicReference<>();
        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.setServerKeyVerifier((session, address, key) -> {
                String fingerprint = KeyUtils.getFingerPrint(key);
                seen.set(fingerprint);
                return knownHostKey == null || knownHostKey.isEmpty() || knownHostKey.equals(fingerprint);
            });
            client.setKeyIdentityProvider(KeyIdentityProvider.EMPTY_KEYS_PROVIDER);
            client.start();
            try (ClientSession session = client.connect(user, host, port).verify(TIMEOUT).getSession()) {
                session.addPasswordIdentity(password != null ? password : "");
                try {
                    session.auth().verify(TIMEOUT);
                } catch (IOException e) {
                    if (knownHostKey != null && !knownHostKey.isEmpty() && seen.get() != null && !knownHostKey.equals(seen.get())) {
                        throw new IOException("The server key of " + host + " has changed (" + seen.get() + "), transfer refused");
                    }
                    throw e;
                }
                try (SftpClient sftp = SftpClientFactory.instance().createSftpClient(session)) {
                    String dir = directory == null || directory.trim().isEmpty() ? "." : directory.trim();
                    while (dir.length() > 1 && dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
                    String target = dir + "/" + fileName;
                    String part = target + ".part";
                    try (OutputStream out = sftp.write(part)) {
                        out.write(content);
                    }
                    sftp.rename(part, target);
                }
            }
            return seen.get();
        }
    }
}
