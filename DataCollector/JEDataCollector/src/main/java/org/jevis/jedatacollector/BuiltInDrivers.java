/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDataCollector.
 * <p>
 * JEDataCollector is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEDataCollector is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDataCollector. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDataCollector is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedatacollector;

import org.jevis.csvparser.JEVisCSVParser;
import org.jevis.datacollector.sqldriver.SQLDriver;
import org.jevis.dwddatasource.DWDDataSource;
import org.jevis.ecp.ECPredictorDataSource;
import org.jevis.emaildatasource.EMailDataSource;
import org.jevis.ftpdatasource.FTPDataSource;
import org.jevis.httpdatasource.JEVisHTTPDataSource;
import org.jevis.jeopc.OPCUADataSource;
import org.jevis.jevisserver.JEVisServer;
import org.jevis.jsonparser.JEVisJSONParser;
import org.jevis.loytecxmldl.LoytecXmlDlDataSource;
import org.jevis.modbusdatasource.ModbusDataSource;
import org.jevis.mscons.MSCONSJEVisParser;
import org.jevis.revolutionpiserver.RevolutionPiServer;
import org.jevis.sftpdatasource.sFTPDataSource;
import org.jevis.soapdatasource.JEVisSOAPDataSource;
import org.jevis.vida350datasource.VIDA350DataSource;
import org.jevis.wdparser.JEVisDWDWDParser;
import org.jevis.xlsxparser.JEVisXLSXParser;
import org.jevis.xmlparser.JEVisXMLParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The data source and parser drivers shipped with JEDataCollector, keyed by the JEVis class they handle. Subclasses of
 * these JEVis classes use the same driver. A new driver module is added to the pom and registered here.
 */
public final class BuiltInDrivers {

    private BuiltInDrivers() {
    }

    public static Map<String, Class> dataSources() {
        Map<String, Class> drivers = new LinkedHashMap<>();
        drivers.put("DWD Server", DWDDataSource.class);
        drivers.put("ECP Server", ECPredictorDataSource.class);
        drivers.put("IMAP EMail Server", EMailDataSource.class);
        drivers.put("POP3 EMail Server", EMailDataSource.class);
        drivers.put("FTP Server", FTPDataSource.class);
        drivers.put("sFTP Server", sFTPDataSource.class);
        drivers.put("HTTP Server", JEVisHTTPDataSource.class);
        drivers.put("JEVis Server", JEVisServer.class);
        drivers.put("Loytec XML-DL Server", LoytecXmlDlDataSource.class);
        drivers.put("Modbus Master", ModbusDataSource.class);
        drivers.put("OPC UA Server", OPCUADataSource.class);
        drivers.put("Revolution PI Server", RevolutionPiServer.class);
        drivers.put("SOAP Server", JEVisSOAPDataSource.class);
        drivers.put("SQL Server", SQLDriver.class);
        drivers.put("VIDA350", VIDA350DataSource.class);
        return Collections.unmodifiableMap(drivers);
    }

    public static Map<String, Class> parsers() {
        Map<String, Class> parsers = new LinkedHashMap<>();
        parsers.put("CSV Parser", JEVisCSVParser.class);
        parsers.put("DWD Parser", JEVisDWDWDParser.class);
        parsers.put("JSON Parser", JEVisJSONParser.class);
        parsers.put("MSCONS Parser", MSCONSJEVisParser.class);
        parsers.put("XLSX Parser", JEVisXLSXParser.class);
        parsers.put("XML Parser", JEVisXMLParser.class);
        return Collections.unmodifiableMap(parsers);
    }
}
