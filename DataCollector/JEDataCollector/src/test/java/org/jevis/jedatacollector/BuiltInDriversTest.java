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

import org.jevis.commons.driver.DataSource;
import org.jevis.commons.driver.Parser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BuiltInDriversTest {

    /**
     * Class definitions shipped in var/classes, read from the "name" field of each JSON file.
     */
    private static Set<String> definedClasses() throws IOException {
        Path classDir = Paths.get("../../var/classes");
        Pattern name = Pattern.compile("\\A\\s*\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"");
        try (Stream<Path> files = Files.walk(classDir)) {
            return files.filter(p -> p.toString().endsWith(".json")).map(p -> {
                try {
                    Matcher m = name.matcher(new String(Files.readAllBytes(p), "UTF-8"));
                    return m.find() ? m.group(1) : null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).filter(n -> n != null).collect(Collectors.toSet());
        }
    }

    @Test
    public void dataSourceDriversCanBeCreated() throws Exception {
        for (Map.Entry<String, Class> driver : BuiltInDrivers.dataSources().entrySet()) {
            assertTrue(driver.getValue().newInstance() instanceof DataSource, driver.getKey());
        }
    }

    @Test
    public void parserDriversCanBeCreated() throws Exception {
        for (Map.Entry<String, Class> parser : BuiltInDrivers.parsers().entrySet()) {
            assertTrue(parser.getValue().newInstance() instanceof Parser, parser.getKey());
        }
    }

    @Test
    public void driversMatchClassDefinitions() throws Exception {
        Set<String> classes = definedClasses();
        for (String jevisClass : BuiltInDrivers.dataSources().keySet()) {
            assertTrue(classes.contains(jevisClass), "no class definition for " + jevisClass);
        }
        for (String jevisClass : BuiltInDrivers.parsers().keySet()) {
            // DWD Parser has no JSON definition in var/classes, it only exists in older databases
            if (!jevisClass.equals("DWD Parser")) {
                assertTrue(classes.contains(jevisClass), "no class definition for " + jevisClass);
            }
        }
    }
}
