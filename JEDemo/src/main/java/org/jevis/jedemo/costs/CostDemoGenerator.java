/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEDemo.
 * <p>
 * JEDemo is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEDemo is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEDemo. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEDemo is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jedemo.costs;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the files of the demo cost cases into {@code JEDemo/src/main/resources/costdemo/} (or the directory given as
 * argument): {@code templates/*.json} (Template File of the Result Calculation Templates),
 * {@code configurations/*.json} (Template File of the Accounting Configurations) and {@code setup-plan.json}.
 * The test {@code CostDemoFilesTest} fails if the files differ from what this class generates.
 */
public final class CostDemoGenerator {

    public static final String RESOURCE_DIR = "costdemo";

    private CostDemoGenerator() {
    }

    /**
     * @return relative path → file content (UTF-8, "\n" line ends, pretty printed with two spaces)
     */
    public static Map<String, String> files() {
        ObjectWriter writer = writer();
        Map<String, String> files = new LinkedHashMap<>();
        try {
            for (CostCase c : CostCases.cases()) {
                TemplateFactory.Built built = TemplateFactory.build(c);
                files.put("templates/" + c.file + ".json", write(writer, TemplateFactory.templateJson(built)));
                files.put("configurations/" + c.file + ".json", write(writer, TemplateFactory.configurationJson(built)));
            }
            files.put("setup-plan.json", write(writer, SetupPlan.build()));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return files;
    }

    private static ObjectWriter writer() {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return new ObjectMapper().writer(printer);
    }

    private static String write(ObjectWriter writer, JsonNode node) throws IOException {
        return writer.writeValueAsString(node) + "\n";
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "JEDemo/src/main/resources/" + RESOURCE_DIR);
        for (Map.Entry<String, String> e : files().entrySet()) {
            Path file = dir.resolve(e.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, e.getValue().getBytes(StandardCharsets.UTF_8));
            System.out.println("wrote " + file);
        }
    }
}
