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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the cost demo setup ({@link CostSetup}) found, would do (dry run) or did (apply): every planned action, every
 * skipped item with its reason, every problem and the manual steps afterwards. Each entry is logged when it is added;
 * {@link #write(Path, String)} stores the report as Markdown and JSON.
 */
public final class SetupReport {

    private static final Logger logger = LogManager.getLogger(SetupReport.class);
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public enum Kind {
        /**
         * an object is created
         */
        CREATE(true),
        /**
         * samples (or a unit) are added where there are none
         */
        WRITE(true),
        /**
         * an existing value is replaced (old → new); the object is backed up first
         */
        UPDATE(true),
        /**
         * an object goes to the recycle bin; backed up first
         */
        DELETE(true),
        /**
         * an existing object of the plan is used as it is
         */
        REUSE(false),
        /**
         * an item of the plan is left out (already done, check failed, no longer matching), with the reason
         */
        SKIP(false),
        /**
         * a check of the plan and its result
         */
        CHECK(false),
        /**
         * backed up before the changes
         */
        BACKUP(false),
        /**
         * carried out (apply)
         */
        DONE(false),
        WARNING(false),
        /**
         * blocks the apply: nothing is written while there is a problem
         */
        PROBLEM(false),
        INFO(false);

        private final boolean action;

        Kind(boolean action) {
            this.action = action;
        }

        public boolean isAction() {
            return action;
        }
    }

    public static final class Entry {
        private final Kind kind;
        private final String section;
        private final String text;
        private final ObjectNode data;

        Entry(Kind kind, String section, String text, ObjectNode data) {
            this.kind = kind;
            this.section = section;
            this.text = text;
            this.data = data;
        }

        public Kind getKind() {
            return kind;
        }

        public String getSection() {
            return section;
        }

        public String getText() {
            return text;
        }

        public ObjectNode getData() {
            return data;
        }

        @Override
        public String toString() {
            return kind + " [" + section + "] " + text;
        }
    }

    private final boolean apply;
    private final Instant time;
    private final List<Entry> entries = new ArrayList<>();
    private final List<String> manualSteps = new ArrayList<>();
    private String organisation;
    private String result;
    private String backupDirectory;

    SetupReport(boolean apply, Instant time) {
        this.apply = apply;
        this.time = time;
    }

    public Entry add(Kind kind, String section, String text) {
        return add(kind, section, text, null);
    }

    public Entry add(Kind kind, String section, String text, ObjectNode data) {
        Entry entry = new Entry(kind, section, text, data);
        entries.add(entry);
        String prefix = !apply && kind.isAction() ? "[dry run] would " : "";
        switch (kind) {
            case PROBLEM:
                logger.error("Cost setup {}: {}{}", section, prefix, text);
                break;
            case WARNING:
                logger.warn("Cost setup {}: {}", section, text);
                break;
            default:
                logger.info("Cost setup {} {}: {}{}", kind, section, prefix, text);
        }
        return entry;
    }

    public List<Entry> getEntries() {
        return entries;
    }

    public List<Entry> entries(Kind kind) {
        List<Entry> list = new ArrayList<>();
        for (Entry e : entries) if (e.kind == kind) list.add(e);
        return list;
    }

    public int count(Kind kind) {
        return entries(kind).size();
    }

    /**
     * @return the number of planned actions (create, write, update, delete)
     */
    public int actions() {
        int n = 0;
        for (Entry e : entries) if (e.kind.isAction()) n++;
        return n;
    }

    public boolean hasProblems() {
        return count(Kind.PROBLEM) > 0;
    }

    public boolean isApply() {
        return apply;
    }

    public String getResult() {
        return result;
    }

    void setResult(String result) {
        this.result = result;
        logger.info("Cost setup result: {}", result);
    }

    void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    void setBackupDirectory(String backupDirectory) {
        this.backupDirectory = backupDirectory;
    }

    public String getBackupDirectory() {
        return backupDirectory;
    }

    void addManualStep(String step) {
        manualSteps.add(step);
    }

    public List<String> getManualSteps() {
        return manualSteps;
    }

    // ------------------------------------------------------------------------------------------------ output

    public ObjectNode toJson() {
        ObjectNode root = F.objectNode();
        root.put("mode", apply ? "apply" : "dryrun");
        root.put("time", time.toString());
        root.put("organisation", organisation);
        root.put("result", result);
        if (backupDirectory != null) root.put("backup", backupDirectory);
        ObjectNode counts = root.putObject("counts");
        for (Map.Entry<Kind, Integer> e : counts().entrySet()) counts.put(e.getKey().name(), e.getValue());
        ArrayNode list = root.putArray("entries");
        for (Entry e : entries) {
            ObjectNode node = list.addObject();
            node.put("kind", e.kind.name());
            node.put("section", e.section);
            node.put("text", e.text);
            if (e.data != null) node.set("data", e.data);
        }
        ArrayNode steps = root.putArray("manualSteps");
        manualSteps.forEach(steps::add);
        return root;
    }

    private Map<Kind, Integer> counts() {
        Map<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Entry e : entries) counts.merge(e.kind, 1, Integer::sum);
        return counts;
    }

    public String toMarkdown() {
        StringBuilder md = new StringBuilder();
        md.append("# Cost demo setup - ").append(apply ? "apply" : "dry run (nothing written)").append("\n\n");
        md.append("- Time: ").append(time).append("\n");
        md.append("- Organisation: ").append(organisation).append("\n");
        md.append("- Result: **").append(result).append("**\n");
        if (backupDirectory != null) md.append("- Backup: `").append(backupDirectory).append("`\n");
        md.append("- Counts:");
        for (Map.Entry<Kind, Integer> e : counts().entrySet()) md.append(' ').append(e.getKey()).append(' ').append(e.getValue()).append(',');
        if (md.charAt(md.length() - 1) == ',') md.setLength(md.length() - 1);
        md.append("\n\n");

        List<Entry> problems = entries(Kind.PROBLEM);
        if (!problems.isEmpty()) {
            md.append("## Problems (nothing is written while there are problems)\n\n");
            for (Entry e : problems) md.append("- [").append(e.section).append("] ").append(e.text).append('\n');
            md.append('\n');
        }
        List<Entry> warnings = entries(Kind.WARNING);
        if (!warnings.isEmpty()) {
            md.append("## Warnings\n\n");
            for (Entry e : warnings) md.append("- [").append(e.section).append("] ").append(e.text).append('\n');
            md.append('\n');
        }

        String section = null;
        for (Entry e : entries) {
            if (e.kind == Kind.PROBLEM || e.kind == Kind.WARNING) continue;
            if (!e.section.equals(section)) {
                section = e.section;
                md.append("## ").append(section).append("\n\n");
            }
            md.append("- ").append(e.kind).append(": ").append(e.text.replace("\n", " ")).append('\n');
        }
        if (!manualSteps.isEmpty()) {
            md.append("\n## Manual steps afterwards\n\n");
            int i = 1;
            for (String step : manualSteps) md.append(i++).append(". ").append(step).append('\n');
        }
        return md.toString();
    }

    /**
     * Writes {@code <base>.md} and {@code <base>.json} into the directory.
     *
     * @return the Markdown file
     */
    public Path write(Path directory, String base) throws IOException {
        Files.createDirectories(directory);
        Path md = directory.resolve(base + ".md");
        Files.write(md, toMarkdown().getBytes(StandardCharsets.UTF_8));
        Files.write(directory.resolve(base + ".json"),
                new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsBytes(toJson()));
        return md;
    }
}
