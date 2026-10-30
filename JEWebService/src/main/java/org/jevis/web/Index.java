/*
  Copyright (C) 2013 - 2014 Envidatec GmbH <info@envidatec.com>

  This file is part of JEWebService.

  JEWebService is free software: you can redistribute it and/or modify it under
  the terms of the GNU General Public License as published by the Free Software
  Foundation in version 3.

  JEWebService is distributed in the hope that it will be useful, but WITHOUT
  ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
  FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
  details.

  You should have received a copy of the GNU General Public License along with
  JEWebService. If not, see <http://www.gnu.org/licenses/>.

  JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.web;

import org.jevis.commons.ws.sql.Config;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * @author Florian Simon <florian.simon@envidatec.com>
 */
@Path("/")
public class Index {

    File indexfile = new File(Config.getWebDir() + "/index.html");

    /**
     * Serves the start page at the root. The page files are mounted under /web, so a base tag makes the relative
     * links of index.html resolve there.
     */
    @GET
    @Produces(MediaType.TEXT_HTML + ";charset=utf-8")
    public Response get() throws IOException {
        if (!indexfile.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String html = new String(Files.readAllBytes(indexfile.toPath()), StandardCharsets.UTF_8);
        html = html.replaceFirst("(?i)<head>", "<head>\n    <base href=\"/web/\">");
        return Response.ok(html).build();
    }


}
