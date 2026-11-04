/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.rest.archive;

import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.ConnectionFactory;
import org.jevis.commons.ws.sql.archive.ArchiveSettings;
import org.jevis.commons.ws.sql.archive.ArchiveUtil;
import org.jevis.commons.ws.sql.archive.RowChecksum;
import org.jevis.commons.ws.sql.archive.SampleArchiver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command line tool of the old data archive (OpenJEVis #1440) for manual runs, dry runs, verification and restore.
 * The settings come from {@code webservice.archive.*} of the webservice config file and can be overridden by options.
 */
public final class ArchiveTool {

    private static final String USAGE = String.join(System.lineSeparator(),
            "Usage: java -cp JEWebService-<version>-jar-with-dependencies.jar org.jevis.rest.archive.ArchiveTool",
            "         --config <webservice.xml> <command> [options]",
            "Commands:",
            "  dry-run                      list the data rows and years that would be archived (nothing is changed)",
            "  archive                      write, verify and delete old samples (also if archive.enabled is false)",
            "  verify <file|dir>...         read archive files, print row count and checksum",
            "  restore <file|dir>...        insert the samples of archive files again (existing samples are kept)",
            "Options:",
            "  --dir <folder>               archive folder",
            "  --older-than-years <n>       archive complete years older than n years",
            "  --classes <a,b>              JEVis classes (exact names), default Data",
            "  --attributes <a,b>           attributes, default Value",
            "  --max-rows <n>               stop before the next data row/year after n rows",
            "  --max-minutes <n>            stop before the next data row/year after n minutes",
            "  --batch-size <n>             rows per DELETE/INSERT",
            "  --pause-ms <n>               pause between batches",
            "  --object <id>                restore: target object (default from the file name)",
            "  --attribute <name>           restore: target attribute (default from the file name)",
            "Exit code: 0 done, 1 error, 2 wrong usage, 3 limit reached (run again to continue)");

    private ArchiveTool() {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        String configFile = null;
        String command = null;
        List<String> files = new ArrayList<>();
        Long object = null;
        String attribute = null;
        List<String[]> overrides = new ArrayList<>();
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-c") || arg.equals("--config")) {
                    configFile = value(args, ++i, arg);
                } else if (arg.equals("--object")) {
                    object = Long.parseLong(value(args, ++i, arg));
                } else if (arg.equals("--attribute")) {
                    attribute = value(args, ++i, arg);
                } else if (arg.equals("-h") || arg.equals("--help")) {
                    System.out.println(USAGE);
                    return 0;
                } else if (arg.startsWith("--")) {
                    overrides.add(new String[]{arg, value(args, ++i, arg)});
                } else if (command == null) {
                    command = arg;
                } else {
                    files.add(arg);
                }
            }
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.err.println(USAGE);
            return 2;
        }
        if (configFile == null || command == null) {
            System.err.println(USAGE);
            return 2;
        }

        try {
            Config.readConfigurationFile(new File(configFile));
            ArchiveSettings settings = Config.getArchiveSettings();
            for (String[] override : overrides) {
                apply(settings, override[0], override[1]);
            }
            System.out.println("Time zone: " + TimeZone.getDefault().getID()
                    + " (must be the same as the webservice, normally UTC)");

            if (command.equals("verify")) {
                return verify(expand(files));
            }
            ConnectionFactory.getInstance().registerMySQLDriver(Config.getDBHost(), Config.getDBPort(), Config.getSchema(),
                    Config.getDBUser(), Config.getDBPW(), Config.getConnectionOptions());
            try (Connection connection = ConnectionFactory.getInstance().getConnection()) {
                SampleArchiver archiver = new SampleArchiver(connection, settings, System.out::println);
                switch (command) {
                    case "dry-run":
                        archiver.dryRun(System.currentTimeMillis());
                        return 0;
                    case "archive": {
                        SampleArchiver.RunResult result = archiver.archive(System.currentTimeMillis());
                        if (result.error != null) return 1;
                        return "limit reached".equals(result.status) ? 3 : 0;
                    }
                    case "restore": {
                        List<File> list = expand(files);
                        if (list.isEmpty()) throw new IllegalArgumentException("restore needs at least one archive file");
                        if (list.size() > 1 && (object != null || attribute != null)) {
                            throw new IllegalArgumentException("--object/--attribute are only allowed for a single file");
                        }
                        long inserted = 0;
                        for (File file : list) {
                            inserted += archiver.restore(file, object, attribute).inserted;
                        }
                        System.out.println("Restored " + list.size() + " files, " + inserted + " rows inserted");
                        return 0;
                    }
                    default:
                        System.err.println("Unknown command: " + command);
                        System.err.println(USAGE);
                        return 2;
                }
            }
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            return 2;
        } catch (Exception ex) {
            System.err.println("Error: " + ex.getMessage());
            ex.printStackTrace();
            return 1;
        }
    }

    private static int verify(List<File> files) throws IOException {
        if (files.isEmpty()) throw new IllegalArgumentException("verify needs at least one archive file");
        int errors = 0;
        for (File file : files) {
            try {
                RowChecksum checksum = SampleArchiver.verify(file);
                System.out.println(file + ": " + checksum.getCount() + " rows, checksum " + checksum.finish()
                        + ", sha256 " + SampleArchiver.sha256(file));
            } catch (IOException ex) {
                errors++;
                System.out.println(file + ": DAMAGED - " + ex.getMessage());
            }
        }
        return errors == 0 ? 0 : 1;
    }

    /**
     * Folders are replaced by the archive files they contain (recursively, sorted).
     */
    static List<File> expand(List<String> paths) throws IOException {
        List<File> list = new ArrayList<>();
        for (String path : paths) {
            File file = new File(path);
            if (file.isDirectory()) {
                try (Stream<java.nio.file.Path> stream = Files.walk(file.toPath())) {
                    list.addAll(stream.filter(p -> p.getFileName().toString().endsWith(ArchiveUtil.FILE_SUFFIX))
                            .sorted().map(java.nio.file.Path::toFile).collect(Collectors.toList()));
                }
            } else if (file.isFile()) {
                list.add(file);
            } else {
                throw new IllegalArgumentException("File not found: " + path);
            }
        }
        return list;
    }

    private static String value(String[] args, int index, String option) {
        if (index >= args.length) throw new IllegalArgumentException("Missing value for " + option);
        return args[index];
    }

    static void apply(ArchiveSettings settings, String option, String value) {
        switch (option) {
            case "--dir":
                settings.setDir(new File(value));
                break;
            case "--older-than-years":
                settings.setOlderThanYears((int) positive(option, value, 1));
                break;
            case "--classes":
                settings.setClasses(ArchiveSettings.parseList(value));
                break;
            case "--attributes":
                settings.setAttributes(ArchiveSettings.parseList(value));
                break;
            case "--max-rows":
                settings.setMaxRows(positive(option, value, 1));
                break;
            case "--max-minutes":
                settings.setMaxMinutes((int) positive(option, value, 1));
                break;
            case "--batch-size":
                settings.setBatchSize((int) Math.min(50000, positive(option, value, 1)));
                break;
            case "--pause-ms":
                settings.setPauseMs((int) positive(option, value, 0));
                break;
            default:
                throw new IllegalArgumentException("Unknown option: " + option);
        }
    }

    private static long positive(String option, String value, long min) {
        try {
            long number = Long.parseLong(value.trim());
            if (number >= min) return number;
        } catch (NumberFormatException ignored) {
            // reported below
        }
        throw new IllegalArgumentException("Invalid value for " + option + ": " + value);
    }
}
