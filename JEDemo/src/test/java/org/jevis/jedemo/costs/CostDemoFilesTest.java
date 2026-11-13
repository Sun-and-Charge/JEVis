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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.trc.AccountingTemplateHandler;
import org.jevis.commons.trc.SelectionTemplate;
import org.jevis.commons.trc.TemplateHandler;
import org.jevis.jedemo.InvoiceCase;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The files in src/main/resources/costdemo are what {@link CostDemoGenerator} generates. After a change of the cases
 * run {@code CostDemoGenerator} (or copy target/costdemo-generated) and commit the files.
 */
public class CostDemoFilesTest {

    @Test
    public void resourcesAreUpToDate() throws Exception {
        Map<String, String> files = CostDemoGenerator.files();
        Path out = Paths.get("target", "costdemo-generated");
        List<String> differ = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            Path file = out.resolve(e.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, e.getValue().getBytes(StandardCharsets.UTF_8));
            String resource = resource(CostDemoGenerator.RESOURCE_DIR + "/" + e.getKey());
            if (resource == null || !resource.replace("\r\n", "\n").equals(e.getValue())) differ.add(e.getKey());
        }
        assertTrue("Out of date (generated into " + out.toAbsolutePath() + "): " + differ, differ.isEmpty());
    }

    @Test
    public void filesAreReadable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> files = CostDemoGenerator.files();
        assertEquals(13, files.size());
        for (Map.Entry<String, String> e : files.entrySet()) {
            byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
            if (e.getKey().startsWith("templates/")) {
                assertNotNull(e.getKey(), TemplateHandler.parse(bytes));
                assertEquals("SimpleDataHandler", mapper.readTree(bytes).get("type").asText());
            } else if (e.getKey().startsWith("configurations/")) {
                SelectionTemplate selection = AccountingTemplateHandler.parse(bytes);
                assertNotNull(e.getKey(), selection);
                assertFalse(selection.getSelectedInputs().isEmpty());
                assertTrue(mapper.readTree(bytes).get("templateKey").asText().startsWith("@T."));
            }
        }
    }

    @Test
    public void everyKeyOfThePlanIsDefined() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode plan = SetupPlan.build();
        Set<String> defined = new HashSet<>();
        for (JsonNode o : plan.get("objects")) assertTrue("unique key " + o.get("key"), defined.add(o.get("key").asText()));
        for (JsonNode t : plan.get("templates")) defined.add(t.get("key").asText());
        for (JsonNode k : plan.get("configurations")) defined.add(k.get("key").asText());
        Set<String> used = new HashSet<>();
        for (Map.Entry<String, String> e : CostDemoGenerator.files().entrySet()) collect(mapper.readTree(e.getValue()), used);
        for (JsonNode o : plan.get("objects")) {
            JsonNode parent = o.get("parent");
            if (parent != null && parent.has("key")) assertTrue("parent " + parent, defined.contains(parent.get("key").asText()));
        }
        used.removeAll(defined);
        assertTrue("undefined keys: " + used, used.isEmpty());

        /* all keys resolved: the invoice cases have no problems */
        JsonNode cases = SetupPlan.resolveKeys(plan.get("service").get("attributes").get("Invoice Cases"), key -> 1000L + key.length());
        assertEquals(5, cases.size());
        for (JsonNode node : cases) {
            InvoiceCase ic = mapper.treeToValue(node, InvoiceCase.class);
            assertTrue(ic.key + ": " + ic.problems(), ic.problems().isEmpty());
        }
    }

    private static void collect(JsonNode node, Set<String> keys) {
        if (node.isTextual() && node.asText().startsWith("@")) keys.add(node.asText().substring(1));
        node.forEach(child -> collect(child, keys));
    }

    private static String resource(String name) throws Exception {
        try (InputStream in = CostDemoFilesTest.class.getClassLoader().getResourceAsStream(name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
