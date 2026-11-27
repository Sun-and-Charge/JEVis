/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of FolderDataSource.
 * <p>
 * FolderDataSource is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * FolderDataSource is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * FolderDataSource. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * FolderDataSource is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.folderdatasource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.*;
import org.jevis.commons.driver.*;
import org.jevis.commons.utils.CommonMethods;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Data source "Local Folder Server" (R02): reads files from a folder on the DataCollector's own machine, e.g. an
 * inbox another system drops e-invoices into. Channels ("Local Folder Channel", attribute "Path" as regex as for
 * sFTP) pass the files to their parsers; after all parsers succeeded the files are deleted ("Delete File on
 * successful parsing") or moved into "Move To Folder".
 */
public class FolderDataSource implements DataSource {

    public static final String SERVER_CLASS = "Local Folder Server";
    public static final String CHANNEL_CLASS = "Local Folder Channel";
    public static final String PATH = "Path";
    public static final String MOVE_TO = "Move To Folder";
    private static final Logger logger = LogManager.getLogger(FolderDataSource.class);

    private final List<JEVisObject> channels = new ArrayList<>();
    private Importer importer;
    private DateTimeZone timezone = DateTimeZone.UTC;
    private boolean deleteOnSuccess;
    private String moveTo;
    private String log = "Folder";

    @Override
    public void initialize(JEVisObject server) {
        log = String.format("[%s] %s -", server.getID(), server.getName());
        try {
            String zone = latest(server, DataCollectorTypes.DataSource.TIMEZONE);
            if (zone != null) timezone = DateTimeZone.forID(zone);
            deleteOnSuccess = Boolean.parseBoolean(latest(server, DataCollectorTypes.DataSource.DELETE_ON_SUCCESS));
            moveTo = latest(server, MOVE_TO);
            JEVisClass channelClass = server.getDataSource().getJEVisClass(CHANNEL_CLASS);
            for (JEVisObject channel : CommonMethods.getChildrenRecursive(server, channelClass)) {
                if (channels.stream().noneMatch(c -> c.getID().equals(channel.getID()))) channels.add(channel);
            }
        } catch (Exception e) {
            logger.error("{} could not initialize", log, e);
        }
        importer = ImporterFactory.getImporter(server);
        if (importer != null) importer.initialize(server);
    }

    private static String latest(JEVisObject object, String attribute) {
        try {
            JEVisAttribute a = object.getAttribute(attribute);
            JEVisSample s = a != null ? a.getLatestSample() : null;
            return s != null ? s.getValueAsString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void run() {
        for (JEVisObject channel : channels) {
            try {
                List<JEVisObject> parsers = channel.getChildren(channel.getDataSource().getJEVisClass(DataCollectorTypes.Parser.NAME), true);
                if (parsers.isEmpty()) {
                    logger.warn("{} channel {} has no parser", log, channel.getID());
                    continue;
                }
                String regex = latest(channel, PATH);
                if (regex == null || regex.trim().isEmpty()) {
                    logger.warn("{} channel {} has no path", log, channel.getID());
                    continue;
                }
                String last = latest(channel, DataCollectorTypes.Channel.LAST_READOUT);
                long after = last != null ? new DateTime(last).getMillis() : 0L;
                List<Path> files = FolderScan.find(regex.trim(), after);
                logger.info("{} channel {}: {} new files", log, channel.getID(), files.size());
                if (files.isEmpty()) continue;

                boolean successful = true;
                for (JEVisObject parserObject : parsers) {
                    List<InputStream> input = new ArrayList<>();
                    try {
                        Parser parser = ParserFactory.getParser(parserObject);
                        parser.initialize(parserObject);
                        for (Path file : files) input.add(new BufferedInputStream(Files.newInputStream(file)));
                        parser.parse(input, timezone);
                        List<Result> results = parser.getResult();
                        if (!results.isEmpty()) JEVisImporterAdapter.importResults(results, importer, channel);
                        if (!parser.getReport().errors().isEmpty()) successful = false;
                    } catch (Exception e) {
                        successful = false;
                        logger.error("{} parser {} of channel {} failed", log, parserObject.getID(), channel.getID(), e);
                    } finally {
                        for (InputStream in : input) {
                            try {
                                in.close();
                            } catch (IOException e) {
                                logger.warn("{} could not close a file", log);
                            }
                        }
                    }
                }
                if (successful) {
                    for (Path file : files) {
                        try {
                            if (moveTo != null && !moveTo.trim().isEmpty()) FolderScan.moveTo(file, Paths.get(moveTo.trim()));
                            else if (deleteOnSuccess) Files.deleteIfExists(file);
                        } catch (IOException e) {
                            logger.error("{} could not move/delete {}", log, file, e);
                        }
                    }
                }
            } catch (Exception e) {
                logger.error("{} channel {} failed", log, channel.getID(), e);
            }
        }
    }

    @Override
    public List<InputStream> sendSampleRequest(JEVisObject channel) {
        return new ArrayList<>();
    }

    @Override
    public void parse(List<InputStream> input) {
        /* done per channel in run() */
    }

    @Override
    public void importResult() {
        /* done per channel in run() */
    }
}
