/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest;

import org.jevis.commons.ws.sql.Config;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class CorsFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        String origin = requestContext.getHeaderString("Origin");
        if (!Config.getCORS().isEmpty()) {
            if (Config.getCORS().contains("*") | Config.getCORS().contains(origin)) {
                if (Config.getCORS().contains("*")) {
                    responseContext.getHeaders().add(
                            "Access-Control-Allow-Origin",
                            requestContext.getHeaderString("*"));
                } else {
                    responseContext.getHeaders().add(
                            "Access-Control-Allow-Origin", origin);
                }

                responseContext.getHeaders().add(
                        "Access-Control-Allow-Credentials", "true");
                responseContext.getHeaders().add(
                        "Access-Control-Allow-Headers",
                        "origin, content-type, accept, authorization");
                responseContext.getHeaders().add(
                        "Access-Control-Allow-Methods",
                        "GET, OPTIONS, HEAD"); //, POST, PUT, DELETE, OPTIONS, HEAD
            }
        }


    }
}
