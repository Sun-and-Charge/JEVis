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
package org.jevis.rest;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.jevis.commons.ws.sql.Config;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * The installer endpoint serves the files for the Microsoft App Installer of the Control Center.
 */
public class ResourceInstallerTest {

    private HttpServer server;
    private String base;
    private Path root;
    private Object oldInstallerDir;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("installer-test");
        Path dir = Files.createDirectory(root.resolve("installer"));
        Files.write(dir.resolve("JEVisCC.appinstaller"), "<AppInstaller/>".getBytes(StandardCharsets.UTF_8));
        Files.createDirectory(dir.resolve("sub"));
        Files.write(dir.resolve("sub").resolve("a.txt"), "abcdef".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("secret.txt"), "SECRET".getBytes(StandardCharsets.UTF_8));

        Field f = Config.class.getDeclaredField("installerDir");
        f.setAccessible(true);
        oldInstallerDir = f.get(null);
        f.set(null, dir.toString());

        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        base = "http://127.0.0.1:" + port;
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create(base + "/"), new ResourceConfig(ResourceInstaller.class));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdownNow();
        Field f = Config.class.getDeclaredField("installerDir");
        f.setAccessible(true);
        f.set(null, oldInstallerDir);
    }

    private String[] get(String path, String range) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        if (range != null) {
            c.setRequestProperty("Range", range);
        }
        int code = c.getResponseCode();
        InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        return new String[]{String.valueOf(code), body};
    }

    @Test
    public void servesFile() throws Exception {
        String[] r = get("/JEWebService/v1/installer/JEVisCC.appinstaller", null);
        assertEquals("200", r[0]);
        assertEquals("<AppInstaller/>", r[1]);
    }

    @Test
    public void servesFileInSubdirectoryWithRange() throws Exception {
        String[] r = get("/JEWebService/v1/installer/sub/a.txt", "bytes=1-3");
        assertEquals("206", r[0]);
        assertEquals("bcd", r[1]);
    }

    @Test
    public void rejectsPathOutsideInstallerDir() throws Exception {
        String[] r = get("/JEWebService/v1/installer/..%2Fsecret.txt", null);
        assertNotEquals("200", r[0]);
        assertNotEquals("SECRET", r[1]);
    }
}
