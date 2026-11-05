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
package org.jevis.web;

import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.server.StaticHttpHandler;

import java.io.File;

/**
 * Serves the built web app (JEWebApp/dist) at /app.
 * <p>
 * The app routes in the browser (e.g. /app/analyses/123), so every request for a path without file extension that
 * does not exist is answered with index.html ("SPA fallback"). Missing files with an extension stay 404.
 * index.html is never cached, the files below /assets/ have a content hash in their name and are cached for a year.
 */
public class WebAppHttpHandler extends StaticHttpHandler {

    private final File index;

    public WebAppHttpHandler(String directory) {
        super(directory);
        this.index = new File(directory, "index.html");
        /* the file cache would keep an old index.html after an update of the app */
        setFileCacheEnabled(false);
    }

    /**
     * @return true if the last segment of the path has a file extension (e.g. "app.js")
     */
    static boolean hasExtension(String uri) {
        String last = uri.substring(uri.lastIndexOf('/') + 1);
        return last.lastIndexOf('.') > 0;
    }

    @Override
    protected boolean handle(String uri, Request request, Response response) throws Exception {
        if (uri.startsWith("/assets/")) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            response.setHeader("Cache-Control", "no-cache");
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        return super.handle(uri, request, response);
    }

    @Override
    protected void onMissingResource(Request request, Response response) throws Exception {
        String uri = getRelativeURI(request);
        if (uri == null || uri.isEmpty()) {
            /* "/app" without slash: relative links of the app need the slash */
            response.sendRedirect(request.getContextPath() + "/");
            return;
        }
        if (hasExtension(uri) || !index.isFile()) {
            super.onMissingResource(request, response);
            return;
        }
        response.setHeader("Cache-Control", "no-cache");
        response.setContentType("text/html;charset=utf-8");
        sendFile(response, index);
    }
}
