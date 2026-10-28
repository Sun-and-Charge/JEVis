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

import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import org.apache.logging.log4j.LogManager;

/**
 *
 * @author fs
 */
public class RequestLog {
    
    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(RequestLog.class);
    
    public static void log(String user , Request request,long time, Response resonse,UriInfo url){
        logger.error("[{}][{}][{}][{} ms][{} byte] {}", user , request.getMethod(), resonse.getStatus(),time, resonse.getLength(),url.getRequestUri());

    }
    
}
