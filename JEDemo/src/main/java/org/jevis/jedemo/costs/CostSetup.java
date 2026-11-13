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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisFile;
import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisSample;
import org.jevis.api.JEVisUnit;
import org.jevis.commons.JEVisFileImp;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonUnit;
import org.jevis.jedemo.DemoConfig;
import org.joda.time.DateTime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Carries out the setup plan of the demo cost cases ({@code costdemo/setup-plan.json}, see docs/DEMO-COSTS.md) on one
 * demo organisation, once, as the restricted service user of the JEDemo job.
 * <p>
 * Two phases. <b>Plan</b> (read only, also the whole dry run): resolve every object of the plan, check the
 * preconditions and list each action — objects to create (class, name, parent path), samples to write (counts and
 * time ranges of the histories), values to replace (old → new), objects to delete (reason and check results), fixes —
 * and every problem. <b>Apply</b> (only without problems): back up every object that is changed or deleted, then carry
 * out the actions in plan order; the first failure stops it, the deletions come last.
 * <p>
 * Safety: the organisation's name must contain "Demo"; every object that is created, written or deleted must be below
 * it (checked by walking the parents, again right before each write); the only object outside that may be written is
 * the JEDemo service object of the organisation, and only its "Invoice Cases". Deletions go to the recycle bin.
 * <p>
 * Idempotent: objects are matched by parent + class + name (existing ones are reused), only missing samples are added,
 * existing values are replaced only if they differ, a template or configuration file is written only if its content
 * differs, deleted objects are not found again. A second run reports "nothing to do".
 */
public final class CostSetup {

    public static final String DRY_RUN = "dryrun";
    public static final String APPLY = "apply";

    static final String PRECONDITIONS = "Preconditions";
    static final String OBJECTS = "Objects";
    static final String TEMPLATES = "Templates";
    static final String CONFIGURATIONS = "Configurations";
    static final String SERVICE = "JEDemo service";
    static final String FIXES = "Fixes";
    static final String DELETE = "Delete";
    static final String BACKUP = "Backup";

    static final String TEMPLATE_FILE = "Template File";
    static final String INVOICE_CASES = DemoConfig.A_INVOICE_CASES;
    static final String INVOICE_CLASS = "Energy Invoice";
    static final String INVOICE_DATA_CLASS = "Energy Invoice Data";
    static final String INVOICE_NUMBER = "Invoice Number";
    static final String LINK_CLASS = "Link";
    static final String CLEAN_DATA_CLASS = "Clean Data";
    /**
     * classes whose settings are configuration of the data processing (period, differential conversion, ...): the
     * JEDataProcessor applies a setting only from its time stamp on, so they are written at {@link #CONFIG_TIMESTAMP}
     */
    static final Set<String> ROW_CLASSES = new HashSet<>(Arrays.asList("Data", CLEAN_DATA_CLASS));
    /**
     * Time stamp of configuration samples, as the Control Center writes them (1990-01-01 00:00 Europe/Berlin): the
     * setting holds for the whole history of the row. Settings of data rows (Data, Clean Data) are written here; other
     * objects get their settings at the time of the run, dated prices keep their own time stamps.
     */
    public static final DateTime CONFIG_TIMESTAMP = new DateTime(Instant.parse("1989-12-31T23:00:00Z").toEpochMilli());
    /**
     * classes whose file attribute may name an object (check {@value SetupPlan#CHECK_NOT_REFERENCED})
     */
    static final List<String> FILE_CLASSES = Arrays.asList("Result Calculation Template", "Accounting Configuration", "Dashboard Analysis");
    static final List<String> FILE_ATTRIBUTES = Arrays.asList(TEMPLATE_FILE, "Data Model File");

    private static final Logger logger = LogManager.getLogger(CostSetup.class);
    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int WRITE_CHUNK = 5000;
    private static final int MAX_PARENT_STEPS = 10000;

    private final JEVisDataSource ds;
    private final boolean apply;
    private final Clock clock;
    private final JsonNode plan;
    private final Function<String, byte[]> resources;
    private final Path backupDirectory;
    private final ObjectMapper mapper = new ObjectMapper();

    private SetupReport report;
    private JEVisObject organisation;
    private Instant now;
    /**
     * plan key → id of the existing (or, during apply, created) object
     */
    private final Map<String, Long> ids = new HashMap<>();
    /**
     * plan key → path for the report
     */
    private final Map<String, String> paths = new HashMap<>();
    /**
     * keys of the objects this run creates
     */
    private final Set<String> newKeys = new HashSet<>();
    /**
     * keys that could not be resolved (problem reported)
     */
    private final Set<String> brokenKeys = new HashSet<>();
    private final List<Step> steps = new ArrayList<>();
    private final Map<Long, String> backupObjects = new LinkedHashMap<>();
    private final Map<Long, String> backupTrees = new LinkedHashMap<>();
    /**
     * ids that the planned deletions remove (with the objects below them)
     */
    private final Set<Long> plannedDeletes = new HashSet<>();
    /**
     * ids removed during apply
     */
    private final Set<Long> deleted = new HashSet<>();
    private final Set<Long> verified = new HashSet<>();
    /**
     * keys of existing rows whose settings were not effective from the start of their history (written early now)
     */
    private final Set<String> settingsRepaired = new HashSet<>();
    /**
     * plan key → the plan object
     */
    private final Map<String, JsonNode> planObjects = new HashMap<>();
    private List<JEVisObject> subtreeCache;
    private SetupBackup backup;
    private JEVisObject service;
    /**
     * {clean rows of the raw rows, calculation id or null, clean rows of the derived rows} of the fix REPLACE_HISTORY, for the manual steps
     */
    private String[] chargeSteps;

    /**
     * @param apply           false: dry run (nothing is written)
     * @param plan            the setup plan
     * @param resources       file of the plan (e.g. {@code templates/A-strom-rlm.json}) → content, null if missing
     * @param backupDirectory where the objects are backed up before they are changed or deleted (apply)
     */
    public CostSetup(JEVisDataSource ds, boolean apply, Clock clock, JsonNode plan, Function<String, byte[]> resources,
                     Path backupDirectory) {
        this.ds = ds;
        this.apply = apply;
        this.clock = clock;
        this.plan = plan;
        this.resources = resources;
        this.backupDirectory = backupDirectory;
    }

    /**
     * The files of the plan from the class path ({@code costdemo/...}).
     */
    public static byte[] classpathResource(String name) {
        try (InputStream in = CostSetup.class.getClassLoader().getResourceAsStream(CostDemoGenerator.RESOURCE_DIR + "/" + name)) {
            if (in == null) return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + name, e);
        }
    }

    public static JsonNode classpathPlan() throws IOException {
        byte[] bytes = classpathResource("setup-plan.json");
        if (bytes == null) throw new IOException("setup-plan.json is missing in the jar");
        return new ObjectMapper().readTree(bytes);
    }

    // ================================================================================================ run

    /**
     * Plans (dry run) or plans and applies the setup for the organisation.
     */
    public SetupReport run(long organisationId) {
        now = clock.instant();
        report = new SetupReport(apply, now);
        try {
            runPhases(organisationId);
        } finally {
            manualSteps();
        }
        return report;
    }

    private void runPhases(long organisationId) {
        try {
            if (prepare(organisationId)) {
                planObjects();
                planFiles(TEMPLATES, plan.path("templates"));
                planFiles(CONFIGURATIONS, plan.path("configurations"));
                planService();
                planFixes();
                planDeletes();
            }
        } catch (Exception e) {
            logger.error("Planning failed", e);
            report.add(SetupReport.Kind.PROBLEM, PRECONDITIONS, "Planning failed: " + e);
        }

        if (report.hasProblems()) {
            report.setResult("refused: " + report.count(SetupReport.Kind.PROBLEM) + " problem(s), nothing written");
            return;
        }
        if (steps.isEmpty()) {
            report.setResult("nothing to do: the organisation already matches the plan");
            return;
        }
        String counts = report.count(SetupReport.Kind.CREATE) + " create, " + report.count(SetupReport.Kind.WRITE) + " write, "
                + report.count(SetupReport.Kind.UPDATE) + " update, " + report.count(SetupReport.Kind.DELETE) + " delete";
        if (!apply) {
            report.setResult("dry run: " + report.actions() + " planned actions (" + counts + "), nothing written");
            return;
        }
        if (!backupAll()) {
            report.setResult("aborted: the backup failed, nothing written");
            return;
        }
        int n = steps.size();
        for (int i = 0; i < n; i++) {
            Step step = steps.get(i);
            try {
                String done = step.action.run();
                if (done != null) report.add(SetupReport.Kind.DONE, step.section, done);
            } catch (Exception e) {
                logger.error("Cost setup step {} of {} failed: {}", i + 1, n, step.text, e);
                report.add(SetupReport.Kind.PROBLEM, step.section, "Step " + (i + 1) + " of " + n + " failed (" + step.text + "): " + e
                        + ". Stopped: the " + (n - i - 1) + " steps after it (and all deletions) were not carried out. "
                        + "Run the setup again after fixing the cause; it continues where it stopped.");
                report.setResult("stopped at step " + (i + 1) + " of " + n + "; the steps before it were written");
                return;
            }
        }
        report.setResult("applied: " + n + " steps (" + counts + ")");
    }

    // ================================================================================================ preconditions

    private boolean prepare(long organisationId) throws JEVisException {
        organisation = object(organisationId);
        if (organisation == null) {
            problem(PRECONDITIONS, "Organisation " + organisationId + " not found or not readable");
            return false;
        }
        report.setOrganisation(organisation.getName() + " [" + organisation.getID() + "]");
        if (!DemoConfig.isDemoName(organisation.getName())) {
            problem(PRECONDITIONS, "Refusing organisation '" + organisation.getName() + "' [" + organisationId
                    + "]: the name does not contain \"Demo\". Nothing is touched.");
            return false;
        }
        String planned = plan.path("organisation").asText();
        if (!planned.equals(organisation.getName())) {
            problem(PRECONDITIONS, "The plan is for the organisation '" + planned + "' (paths start with it), not '"
                    + organisation.getName() + "'");
            return false;
        }
        report.add(SetupReport.Kind.INFO, PRECONDITIONS, "Organisation '" + organisation.getName() + "' [" + organisationId
                + "]; plan version " + plan.path("version").asText() + "; " + (apply ? "apply" : "dry run, nothing is written")
                + "; time " + now);
        return true;
    }

    // ================================================================================================ objects

    private void planObjects() throws JEVisException {
        for (JsonNode o : plan.path("objects")) planObjects.put(o.path("key").asText(), o);
        for (JsonNode o : plan.path("objects")) {
            String key = o.path("key").asText();
            try {
                if ("update".equals(o.path("action").asText())) {
                    planExisting(o, key);
                } else {
                    planCreate(OBJECTS, o, key);
                }
            } catch (JEVisException | RuntimeException e) {
                logger.error("Could not plan @{}", key, e);
                brokenKeys.add(key);
                problem(OBJECTS, "@" + key + ": " + e);
            }
        }
    }

    private void planExisting(JsonNode o, String key) throws JEVisException {
        long id = o.path("id").asLong();
        String jevisClass = o.path("class").asText();
        String name = o.path("name").asText();
        JEVisObject obj = object(id);
        if (obj == null) {
            broken(OBJECTS, key, "object " + id + " ('" + name + "') not found or not readable");
            return;
        }
        if (!jevisClass.equals(obj.getJEVisClassName())) {
            broken(OBJECTS, key, "object " + id + " is a '" + obj.getJEVisClassName() + "', the plan expects '" + jevisClass + "'");
            return;
        }
        if (!inOrganisation(obj)) {
            broken(OBJECTS, key, "object " + id + " is not below the organisation");
            return;
        }
        if (!name.equals(obj.getName())) {
            report.add(SetupReport.Kind.WARNING, OBJECTS, "@" + key + ": object " + id + " is named '" + obj.getName()
                    + "', the plan says '" + name + "' (used by id)");
        }
        ids.put(key, id);
        paths.put(key, path(obj));
        report.add(SetupReport.Kind.REUSE, OBJECTS, "@" + key + " = " + jevisClass + " '" + path(obj) + "' [" + id + "]");
        planAttributes(o, key, obj);
    }

    /**
     * @return the existing object that is used (null if it is created or the key is broken)
     */
    private JEVisObject planCreate(String section, JsonNode o, String key) throws JEVisException {
        String jevisClass = o.path("class").asText();
        String name = o.path("name").asText();
        Parent parent = parent(section, o.path("parent"), key);
        if (parent == null) {
            brokenKeys.add(key);
            return null;
        }
        JEVisObject existing = null;
        if (parent.gone) {
            existing = findInOrganisation(jevisClass, name, key, section);
            if (existing == null) {
                broken(section, key, "the reference object " + parent.reference + " of the parent is gone and no "
                        + jevisClass + " '" + name + "' exists in the organisation");
                return null;
            }
        } else if (parent.object != null) {
            existing = child(parent.object, jevisClass, name);
        }
        if (existing != null) {
            if (!inOrganisation(existing)) {
                broken(section, key, "existing object " + existing.getID() + " is not below the organisation");
                return null;
            }
            ids.put(key, existing.getID());
            paths.put(key, path(existing));
            report.add(SetupReport.Kind.REUSE, section, "@" + key + " = " + jevisClass + " '" + path(existing) + "' ["
                    + existing.getID() + "] exists");
            if (OBJECTS.equals(section)) planAttributes(o, key, existing);
            return existing;
        }

        JEVisClass type = ds.getJEVisClass(jevisClass);
        if (type == null) {
            broken(section, key, "class '" + jevisClass + "' is unknown");
            return null;
        }
        for (String attribute : attributeNames(o)) {
            if (type.getTypes() != null && !type.getTypes().isEmpty() && type.getType(attribute) == null) {
                report.add(SetupReport.Kind.WARNING, section, "@" + key + ": class '" + jevisClass + "' has no attribute '"
                        + attribute + "' (the write fails if it is really missing)");
            }
        }
        String path = parent.path + " / " + name;
        paths.put(key, path);
        newKeys.add(key);
        ObjectNode data = F.objectNode();
        data.put("key", key);
        data.put("class", jevisClass);
        data.put("name", name);
        data.put("parent", parent.path);
        report.add(SetupReport.Kind.CREATE, section, "@" + key + ": " + jevisClass + " '" + name + "' below '" + parent.path + "'", data);
        Long parentId = parent.object != null ? parent.object.getID() : null;
        String parentKey = parent.object == null ? parent.key : null;
        steps.add(new Step(section, "create @" + key + " " + jevisClass + " '" + path + "'",
                () -> create(key, jevisClass, name, parentId, parentKey)));
        if (OBJECTS.equals(section)) planAttributes(o, key, null);
        return null;
    }

    private String create(String key, String jevisClass, String name, Long parentId, String parentKey) throws JEVisException {
        JEVisObject parent = parentId != null ? object(parentId) : live(parentKey);
        if (parent == null) throw new IllegalStateException("parent of @" + key + " not found");
        guard(parent);
        JEVisObject existing = child(parent, jevisClass, name);
        if (existing != null) {
            ids.put(key, existing.getID());
            return "@" + key + " exists meanwhile [" + existing.getID() + "], used";
        }
        JEVisClass type = ds.getJEVisClass(jevisClass);
        if (type == null) throw new IllegalStateException("class '" + jevisClass + "' unknown");
        JEVisObject created = parent.buildObject(name, type);
        created.commit();
        if (created.getID() == null || created.getID() <= 0) throw new IllegalStateException("no id for the new @" + key);
        ids.put(key, created.getID());
        guard(created);
        return "created @" + key + " " + jevisClass + " '" + paths.get(key) + "' [" + created.getID() + "]";
    }

    private static Set<String> attributeNames(JsonNode o) {
        Set<String> names = new LinkedHashSet<>();
        o.path("attributes").fieldNames().forEachRemaining(names::add);
        if (o.has("history")) names.add("Value");
        return names;
    }

    // ------------------------------------------------------------------------------------------------ attributes

    /**
     * @param obj the existing object, null if it is created by this run
     */
    private void planAttributes(JsonNode o, String key, JEVisObject obj) throws JEVisException {
        Set<String> planned = attributeNames(o);
        boolean row = ROW_CLASSES.contains(o.path("class").asText());
        DateTime start = row && obj != null ? historyStart(obj) : null;
        if (o.has("copyAttributesFrom")) {
            long sourceId = o.get("copyAttributesFrom").asLong();
            JEVisObject source = object(sourceId);
            if (source == null) {
                problem(OBJECTS, "@" + key + ": object " + sourceId + " to copy the settings from not found");
            } else {
                for (JEVisAttribute att : source.getAttributes()) {
                    if (planned.contains(att.getName()) || SetupBackup.isFile(att)) continue;
                    JEVisSample latest = att.getLatestSample();
                    if (latest == null || latest.getValueAsString() == null) continue;
                    planSetting(key, obj, att.getName(), F.textNode(latest.getValueAsString()), " (copied from " + sourceId + ")", row, start);
                }
            }
        }
        JsonNode attributes = o.path("attributes");
        for (String name : (Iterable<String>) attributes::fieldNames) {
            JsonNode spec = attributes.get(name);
            if (spec.has("unit")) planUnit(key, obj, name, spec.get("unit").asText());
            if (spec.has("value")) planSetting(key, obj, name, spec.get("value"), "", row, start);
            if (spec.has("samples")) planSamples(key, obj, name, spec.get("samples"));
        }
        if (o.has("history")) planHistory(key, obj, o.get("history"));
    }

    private JEVisAttribute existingAttribute(String key, JEVisObject obj, String name) throws JEVisException {
        JEVisAttribute att = obj.getAttribute(name);
        if (att == null) problem(OBJECTS, "@" + key + " [" + obj.getID() + "] has no attribute '" + name + "'");
        return att;
    }

    private void planUnit(String key, JEVisObject obj, String name, String unit) throws JEVisException {
        if (unit == null || unit.isEmpty()) return;
        if (obj != null) {
            JEVisAttribute att = existingAttribute(key, obj, name);
            if (att == null) return;
            String current = label(att.getInputUnit());
            if (unit.equals(current)) return;
            if (current != null && !current.isEmpty()) {
                report.add(SetupReport.Kind.WARNING, OBJECTS, paths.get(key) + " · " + name + ": unit is '" + current
                        + "', the plan says '" + unit + "'; kept (check by hand)");
                return;
            }
            backupObject(obj, "unit of " + name);
        }
        report.add(SetupReport.Kind.WRITE, OBJECTS, paths.get(key) + " · " + name + ": unit " + unit);
        steps.add(new Step(OBJECTS, "unit of @" + key + " · " + name, () -> {
            JEVisAttribute att = writable(key, name);
            JEVisUnit u = jevisUnit(unit);
            att.setInputUnit(u);
            att.setDisplayUnit(u);
            att.commit();
            return "@" + key + " · " + name + ": unit " + unit;
        }));
    }

    /**
     * @return the first time stamp of the row's history: the first raw value (for a clean data row the parent's), null
     * if there is none
     */
    private DateTime historyStart(JEVisObject row) throws JEVisException {
        JEVisObject raw = row;
        if (CLEAN_DATA_CLASS.equals(row.getJEVisClassName())) {
            List<JEVisObject> parents = row.getParents();
            raw = parents == null || parents.isEmpty() ? row : parents.get(0);
        }
        JEVisAttribute value = raw.getAttribute("Value");
        return value != null && value.hasSample() ? value.getTimestampOfFirstSample() : null;
    }

    /**
     * A setting written once.
     *
     * @param config true for the settings of a data row: written at {@link #CONFIG_TIMESTAMP}, and an existing row is
     *               repaired if the value in force at the start of its history ({@code start}) is not the wanted one
     */
    private void planSetting(String key, JEVisObject obj, String name, JsonNode value, String note, boolean config,
                             DateTime start) throws JEVisException {
        String wanted = display(value);
        if (config) {
            planConfigSetting(OBJECTS, key, obj, name, value, note, start);
            return;
        }
        SetupReport.Kind kind = SetupReport.Kind.WRITE;
        String text = paths.get(key) + " · " + name + " = " + wanted + note;
        if (obj != null) {
            JEVisAttribute att = existingAttribute(key, obj, name);
            if (att == null) return;
            JEVisSample latest = att.getLatestSample();
            JsonNode resolved = SetupPlan.resolveKeys(value, this::knownId);
            String old = latest != null ? latest.getValueAsString() : null;
            if (old != null && !hasPlanKeys(resolved) && same(old, resolved)) return;
            if (old != null) {
                kind = SetupReport.Kind.UPDATE;
                text = paths.get(key) + " · " + name + ": " + shorten(old) + " → " + wanted + note;
            }
            backupObject(obj, "setting " + name);
        }
        addSettingStep(OBJECTS, key, name, value, kind, text, new DateTime(now.toEpochMilli()));
    }

    private void planConfigSetting(String section, String key, JEVisObject obj, String name, JsonNode value, String note, DateTime start)
            throws JEVisException {
        String wanted = display(value);
        String at = " @ " + CONFIG_TIMESTAMP.toDateTime(org.joda.time.DateTimeZone.UTC) + " (configuration time stamp)";
        if (obj == null) {
            addSettingStep(section, key, name, value, SetupReport.Kind.WRITE, paths.get(key) + " · " + name + " = " + wanted + note + at, CONFIG_TIMESTAMP);
            return;
        }
        JEVisAttribute att = existingAttribute(key, obj, name);
        if (att == null) return;
        JsonNode resolved = SetupPlan.resolveKeys(value, this::knownId);
        boolean unresolved = hasPlanKeys(resolved);
        DateTime until = start != null && start.isAfter(CONFIG_TIMESTAMP) ? start : CONFIG_TIMESTAMP;
        JEVisSample effective = null;
        for (JEVisSample s : att.getSamples(new DateTime(0L), until)) {
            if (effective == null || !s.getTimestamp().isBefore(effective.getTimestamp())) effective = s;
        }
        JEVisSample latest = att.getLatestSample();
        boolean earlyOk = effective != null && !unresolved && same(effective.getValueAsString(), resolved);
        boolean latestOk = latest != null && !unresolved && same(latest.getValueAsString(), resolved);
        if (earlyOk && latestOk) return;
        backupObject(obj, "setting " + name);
        if (!earlyOk) {
            settingsRepaired.add(key);
            /* a later sample before the start (e.g. 2001 in the demo) would stay in force over one at the configuration
               time stamp: that sample is overwritten at its own time stamp (live 2026-11-13, charge points 1 and 3) */
            DateTime ts = effective != null && effective.getTimestamp().isAfter(CONFIG_TIMESTAMP) ? effective.getTimestamp() : CONFIG_TIMESTAMP;
            String writeAt = ts.equals(CONFIG_TIMESTAMP) ? at : " @ " + ts.toDateTime(org.joda.time.DateTimeZone.UTC) + " (time stamp of that value)";
            JEVisSample existing = sampleAt(att, ts);
            String before = effective == null ? "no value at the start of the history" + (start != null ? " (" + start + ")" : "")
                    : "value at the start of the history " + shorten(effective.getValueAsString());
            addSettingStep(section, key, name, value, existing == null ? SetupReport.Kind.WRITE : SetupReport.Kind.UPDATE,
                    paths.get(key) + " · " + name + ": " + before + " → " + wanted + note + writeAt, ts);
        }
        if (!latestOk && latest != null && latest.getTimestamp().isAfter(CONFIG_TIMESTAMP)) {
            addSettingStep(section, key, name, value, SetupReport.Kind.UPDATE, paths.get(key) + " · " + name + ": "
                    + shorten(latest.getValueAsString()) + " → " + wanted + note + " (latest value)", new DateTime(now.toEpochMilli()));
        }
    }

    private void addSettingStep(String section, String key, String name, JsonNode value, SetupReport.Kind kind, String text, DateTime ts) {
        ObjectNode data = F.objectNode();
        data.put("object", paths.get(key));
        data.put("attribute", name);
        data.set("value", value);
        data.put("ts", ts.toString());
        report.add(kind, section, text, data);
        steps.add(new Step(section, (key.startsWith("#") ? key : "@" + key) + " · " + name, () -> {
            JEVisAttribute att = writable(key, name);
            att.buildSample(ts, toWrite(resolveNow(value), att)).commit();
            return (key.startsWith("#") ? paths.get(key) + " [" + ids.get(key) + "]" : "@" + key) + " · " + name + " = "
                    + display(resolveNow(value)) + " @ " + ts;
        }));
    }

    private void planSamples(String key, JEVisObject obj, String name, JsonNode samples) throws JEVisException {
        List<JsonNode> write = new ArrayList<>();
        List<String> added = new ArrayList<>();
        JEVisAttribute att = obj != null ? existingAttribute(key, obj, name) : null;
        if (obj != null && att == null) return;
        for (JsonNode s : samples) {
            DateTime ts = timestamp(s.path("ts").asText());
            JsonNode value = s.get("value");
            if (att != null) {
                JEVisSample existing = sampleAt(att, ts);
                if (existing != null) {
                    if (same(existing.getValueAsString(), value)) continue;
                    report.add(SetupReport.Kind.UPDATE, OBJECTS, paths.get(key) + " · " + name + " @ " + s.path("ts").asText()
                            + ": " + shorten(existing.getValueAsString()) + " → " + display(value));
                    backupObject(obj, "sample of " + name);
                    write.add(s);
                    continue;
                }
                backupObject(obj, "samples of " + name);
            }
            added.add(s.path("ts").asText().substring(0, 10) + " = " + display(value));
            write.add(s);
        }
        if (!added.isEmpty()) {
            report.add(SetupReport.Kind.WRITE, OBJECTS, paths.get(key) + " · " + name + ": " + added.size() + " sample(s): "
                    + String.join(", ", added));
        }
        if (write.isEmpty()) return;
        steps.add(new Step(OBJECTS, "@" + key + " · " + name + " samples", () -> {
            JEVisAttribute target = writable(key, name);
            List<JEVisSample> list = new ArrayList<>();
            for (JsonNode s : write) list.add(target.buildSample(timestamp(s.path("ts").asText()), toWrite(s.get("value"), target)));
            target.addSamples(list);
            return "@" + key + " · " + name + ": " + list.size() + " sample(s)";
        }));
    }

    private void planHistory(String key, JEVisObject obj, JsonNode history) throws JEVisException {
        String profile = history.path("profile").asText();
        Instant from = timestamp(history.path("from").asText()).toDate().toInstant();
        String untilText = history.path("until").asText();
        Instant until = "setup".equals(untilText) ? now : timestamp(untilText).toDate().toInstant();
        List<History.Point> points;
        try {
            points = new ArrayList<>(History.generate(profile, from, until));
        } catch (IllegalArgumentException e) {
            problem(OBJECTS, "@" + key + ": " + e.getMessage());
            return;
        }
        int total = points.size();
        if (obj != null) {
            JEVisAttribute att = existingAttribute(key, obj, "Value");
            if (att == null) return;
            Set<Long> present = new HashSet<>();
            for (JEVisSample s : att.getSamples(new DateTime(from.toEpochMilli()), new DateTime(until.toEpochMilli()))) {
                present.add(s.getTimestamp().getMillis());
            }
            points.removeIf(p -> present.contains(p.getTs().toEpochMilli()));
        }
        if (points.isEmpty()) {
            report.add(SetupReport.Kind.SKIP, OBJECTS, paths.get(key) + " · Value: history " + profile + " complete ("
                    + total + " samples up to " + until + ")");
            return;
        }
        ObjectNode data = F.objectNode();
        data.put("object", paths.get(key));
        data.put("profile", profile);
        data.put("count", points.size());
        data.put("from", points.get(0).getTs().toString());
        data.put("to", points.get(points.size() - 1).getTs().toString());
        report.add(SetupReport.Kind.WRITE, OBJECTS, paths.get(key) + " · Value: history " + profile + ", " + points.size()
                + " samples " + points.get(0).getTs() + " .. " + points.get(points.size() - 1).getTs()
                + (points.size() < total ? " (" + (total - points.size()) + " present)" : ""), data);
        List<History.Point> write = points;
        steps.add(new Step(OBJECTS, "@" + key + " history " + profile, () -> {
            writePoints(writable(key, "Value"), write);
            return "@" + key + " · Value: " + write.size() + " history samples (" + profile + ")";
        }));
    }

    // ================================================================================================ templates

    private void planFiles(String section, JsonNode entries) throws JEVisException {
        for (JsonNode t : entries) {
            String key = t.path("key").asText();
            try {
                planFile(section, t, key);
            } catch (JEVisException | IOException | RuntimeException e) {
                logger.error("Could not plan @{}", key, e);
                brokenKeys.add(key);
                problem(section, "@" + key + ": " + e);
            }
        }
    }

    private void planFile(String section, JsonNode t, String key) throws JEVisException, IOException {
        String file = t.path("templateFile").asText();
        byte[] bytes = resources.apply(file);
        if (bytes == null) {
            broken(section, key, "file " + file + " missing");
            return;
        }
        JsonNode content = mapper.readTree(bytes);
        if (t.has("template")) {
            String template = t.get("template").asText().substring(1);
            if (brokenKeys.contains(template) || !paths.containsKey(template)) {
                broken(section, key, "its template @" + template + " is not available");
                return;
            }
        }
        JEVisObject existing = planCreate(section, t, key);
        if (brokenKeys.contains(key)) return;
        String name = t.path("name").asText();
        if (existing != null) {
            JEVisAttribute att = existingAttribute(key, existing, TEMPLATE_FILE);
            if (att == null) return;
            JsonNode current = latestJson(att);
            JsonNode resolved = SetupPlan.resolveKeys(content, this::knownId);
            if (current != null && !hasPlanKeys(resolved) && current.equals(normalize(resolved))) {
                report.add(SetupReport.Kind.SKIP, section, "'" + paths.get(key) + "' · " + TEMPLATE_FILE + " up to date");
                return;
            }
            backupObject(existing, TEMPLATE_FILE);
            report.add(current == null ? SetupReport.Kind.WRITE : SetupReport.Kind.UPDATE, section, "'" + paths.get(key) + "' · "
                    + TEMPLATE_FILE + ": new file sample from " + file + (current == null ? "" : " (content differs)"));
        } else {
            report.add(SetupReport.Kind.WRITE, section, "'" + paths.get(key) + "' · " + TEMPLATE_FILE + " from " + file
                    + " (keys resolved to the new ids)");
        }
        steps.add(new Step(section, "@" + key + " · " + TEMPLATE_FILE, () -> {
            JEVisAttribute att = writable(key, TEMPLATE_FILE);
            JsonNode resolved = resolveNow(content);
            writeFile(att, name, resolved);
            return "@" + key + " '" + paths.get(key) + "' · " + TEMPLATE_FILE + " written";
        }));
    }

    private void writeFile(JEVisAttribute att, String name, JsonNode content) throws Exception {
        String fileName = name.replaceAll("[^A-Za-z0-9]", "") + "_" + FILE_STAMP.format(now.atZone(History.ZONE)) + ".json";
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(content);
        att.buildSample(new DateTime(now.toEpochMilli()), new JEVisFileImp(fileName, bytes)).commit();
    }

    // ================================================================================================ service object

    private void planService() throws JEVisException, IOException {
        JsonNode section = plan.path("service");
        JsonNode wanted = section.path("attributes").get(INVOICE_CASES);
        if (wanted == null) return;
        JEVisClass type = ds.getJEVisClass(section.path("class").asText(DemoConfig.CLASS_NAME));
        if (type == null) {
            problem(SERVICE, "class '" + section.path("class").asText() + "' unknown");
            return;
        }
        List<JEVisObject> candidates = new ArrayList<>();
        for (JEVisObject o : ds.getObjects(type, false)) {
            if (isDeleted(o)) continue;
            JEVisAttribute att = o.getAttribute(DemoConfig.A_ORGANIZATION);
            JEVisSample s = att != null ? att.getLatestSample() : null;
            if (s != null && DemoConfig.parseIds(s.getValueAsString()).contains(organisation.getID())) candidates.add(o);
        }
        if (candidates.size() != 1) {
            problem(SERVICE, candidates.size() + " JEDemo service objects have the organisation " + organisation.getID()
                    + " in '" + DemoConfig.A_ORGANIZATION + "' (exactly one expected)");
            return;
        }
        service = candidates.get(0);
        report.add(SetupReport.Kind.INFO, SERVICE, "Service object '" + service.getName() + "' [" + service.getID() + "]"
                + (inOrganisation(service) ? "" : " (outside the organisation: only '" + INVOICE_CASES + "' is written)"));
        JEVisAttribute att = service.getAttribute(INVOICE_CASES);
        if (att == null) {
            problem(SERVICE, "the service object has no attribute '" + INVOICE_CASES + "' (update the JEDemo class first)");
            return;
        }
        JEVisSample latest = att.getLatestSample();
        String old = latest != null ? latest.getValueAsString() : null;
        JsonNode resolved = SetupPlan.resolveKeys(wanted, this::knownId);
        if (old != null && !old.trim().isEmpty() && !hasPlanKeys(resolved) && same(old, resolved)) {
            report.add(SetupReport.Kind.SKIP, SERVICE, "'" + INVOICE_CASES + "' up to date (" + wanted.size() + " cases)");
            return;
        }
        backupObject(service, INVOICE_CASES);
        String oldText = old == null || old.trim().isEmpty() ? "(empty)" : cases(old);
        report.add(old == null ? SetupReport.Kind.WRITE : SetupReport.Kind.UPDATE, SERVICE, "'" + INVOICE_CASES + "': " + oldText
                + " → " + wanted.size() + " cases " + keys(wanted) + " (keys resolved)");
        long serviceId = service.getID();
        steps.add(new Step(SERVICE, INVOICE_CASES, () -> {
            JEVisObject target = object(serviceId);
            if (target == null || !DemoConfig.CLASS_NAME.equals(target.getJEVisClassName())) {
                throw new IllegalStateException("service object " + serviceId + " missing");
            }
            JsonNode value = resolveNow(wanted);
            JEVisAttribute a = target.getAttribute(INVOICE_CASES);
            a.buildSample(new DateTime(now.toEpochMilli()), mapper.writeValueAsString(value)).commit();
            return "'" + INVOICE_CASES + "' of service " + serviceId + " written (" + value.size() + " cases)";
        }));
    }

    private String cases(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return node.isArray() ? node.size() + " cases " + keys(node) : "not a JSON array (" + shorten(json) + ")";
        } catch (IOException e) {
            return "not JSON (" + shorten(json) + ")";
        }
    }

    private static String keys(JsonNode cases) {
        List<String> keys = new ArrayList<>();
        for (JsonNode c : cases) keys.add(c.path("key").asText());
        return keys.toString();
    }

    // ================================================================================================ fixes

    private void planFixes() throws JEVisException {
        for (JsonNode fix : plan.path("fixes")) {
            String type = fix.path("type").asText();
            if (SetupPlan.FIX_CLEAN_REPROCESS.equals(type)) {
                planCleanReprocess(fix);
                continue;
            }
            if (SetupPlan.FIX_REPLACE_HISTORY.equals(type)) {
                planReplaceHistory(fix);
                continue;
            }
            if (!SetupPlan.FIX_REMOVE_DASHBOARD_WIDGET.equals(type)) {
                problem(FIXES, "unknown fix type '" + type + "'");
                continue;
            }
            long id = fix.path("dashboard").asLong();
            JEVisObject dashboard = object(id);
            String text = "dashboard " + id + " '" + fix.path("name").asText() + "', widget " + fix.path("widgetUuid").asText();
            String mismatch = dashboard == null ? "dashboard not found" : mismatch(dashboard, fix);
            if (mismatch != null) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": " + mismatch);
                continue;
            }
            if (!inOrganisation(dashboard)) {
                problem(FIXES, text + ": the dashboard is not below the organisation");
                continue;
            }
            DashboardWidgetFix.Result result = widgetFix(dashboard, fix);
            if (result.updated == null) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": " + result.reason);
                continue;
            }
            backupObject(dashboard, fix.path("attribute").asText());
            report.add(SetupReport.Kind.UPDATE, FIXES, "'" + path(dashboard) + "' [" + id + "] · " + fix.path("attribute").asText()
                    + ": " + result.reason + "; every other key kept (" + fix.path("reason").asText() + ")");
            steps.add(new Step(FIXES, text, () -> {
                JEVisObject target = object(id);
                if (target == null) throw new IllegalStateException("dashboard " + id + " missing");
                guard(target);
                DashboardWidgetFix.Result current = widgetFix(target, fix);
                if (current.updated == null) {
                    report.add(SetupReport.Kind.SKIP, FIXES, text + ": " + current.reason);
                    return null;
                }
                writeFile(target.getAttribute(fix.path("attribute").asText()), target.getName(), current.updated);
                return text + ": removed";
            }));
        }
    }

    /**
     * Fix {@value SetupPlan#FIX_CLEAN_REPROCESS}: the values of a clean data row of the plan that were computed while
     * its settings (or those of its raw row) were not in force from the start are deleted, so the JEDataProcessor
     * computes the row again from its first raw value.
     */
    private void planCleanReprocess(JsonNode fix) throws JEVisException {
        boolean force = fix.path("force").asBoolean(false);
        for (JsonNode k : fix.path("rows")) {
            String key = k.asText().replaceFirst("^@", "");
            String text = "@" + key;
            JsonNode o = planObjects.get(key);
            if (o == null || "update".equals(o.path("action").asText()) || !CLEAN_DATA_CLASS.equals(o.path("class").asText())) {
                problem(FIXES, text + ": not a clean data row created by the plan");
                continue;
            }
            if (brokenKeys.contains(key)) continue;
            if (newKeys.contains(key) || !ids.containsKey(key)) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": created in this run, no values to recompute");
                continue;
            }
            JEVisObject row = object(ids.get(key));
            if (row == null || !CLEAN_DATA_CLASS.equals(row.getJEVisClassName()) || !o.path("name").asText().equals(row.getName())) {
                problem(FIXES, text + ": the object " + ids.get(key) + " is not the plan's clean data row");
                continue;
            }
            if (!inOrganisation(row)) {
                problem(FIXES, text + ": the object " + row.getID() + " is not below the organisation");
                continue;
            }
            String parentKey = o.path("parent").path("key").asText(null);
            boolean repaired = settingsRepaired.contains(key) || parentKey != null && settingsRepaired.contains(parentKey);
            JEVisAttribute value = row.getAttribute("Value");
            if (!repaired && !force) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + " '" + paths.get(key) + "': settings in force from the start, values kept");
                continue;
            }
            if (value == null || !value.hasSample()) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + " '" + paths.get(key) + "': no values yet, nothing to delete");
                continue;
            }
            backupObject(row, "Value samples (recomputed)");
            report.add(SetupReport.Kind.UPDATE, FIXES, text + " '" + paths.get(key) + "' [" + row.getID() + "] · Value: delete all "
                    + value.getSampleCount() + " samples " + value.getTimestampOfFirstSample() + " .. " + value.getTimestampOfLastSample()
                    + ", computed " + (repaired ? "before its settings held from the start" : "(force)")
                    + "; the JEDataProcessor computes the row again from its first raw value");
            long id = row.getID();
            steps.add(new Step(FIXES, text + " · Value: delete samples", () -> {
                JEVisObject target = object(id);
                if (target == null) throw new IllegalStateException(text + " [" + id + "] missing");
                guard(target);
                if (!target.getAttribute("Value").deleteAllSample()) throw new IllegalStateException("deleting the values of " + id + " failed");
                return text + " [" + id + "] · Value: all samples deleted";
            }));
        }
    }

    /**
     * A row whose values are deleted so they are computed again (clean data row, calculation output).
     */
    private static final class Clear {
        final long id;
        final String text;
        final boolean hasValues;

        Clear(long id, String text, boolean hasValues) {
            this.id = id;
            this.text = text;
            this.hasValues = hasValues;
        }
    }

    /**
     * Fix {@value SetupPlan#FIX_REPLACE_HISTORY}: each raw row of the fix that does not hold the profile yet (checked
     * at every quarter hour of the "verify" windows) loses all its values and gets the profile (seeded by the row id)
     * from "from" up to the time of the run. The settings of the raw rows ("rowSettings") and of their clean rows
     * ("cleanSettings") are written at the configuration time stamp where they do not hold from the start; the values
     * of the clean rows and of the "derived" rows (calculation output and its clean row) are deleted, once before the
     * replacement and again after it (in case a service computed something from half-written data), so the
     * JEDataProcessor and JECalc compute them again. Rows that do not match the plan are skipped with a report line.
     */
    private void planReplaceHistory(JsonNode fix) throws JEVisException {
        String profile = fix.path("profile").asText();
        Instant from = timestamp(fix.path("from").asText()).toDate().toInstant();
        String untilText = fix.path("until").asText();
        Instant until = "setup".equals(untilText) ? now : timestamp(untilText).toDate().toInstant();
        try {
            History.generate(profile, 0, from, from);
        } catch (IllegalArgumentException e) {
            problem(FIXES, FIX_TEXT + e.getMessage());
            return;
        }
        DateTime newStart = new DateTime(from.toEpochMilli());
        List<Step> replacements = new ArrayList<>();
        List<Clear> clears = new ArrayList<>();
        List<String> cleanIds = new ArrayList<>();
        List<String> derivedCleanIds = new ArrayList<>();
        boolean anyReprocess = false;
        for (JsonNode r : fix.path("rows")) {
            long id = r.path("id").asLong();
            String text = FIX_TEXT + "row " + id + " '" + r.path("name").asText() + "'";
            JEVisObject raw = object(id);
            String mismatch = raw == null ? "not found" : mismatch(raw, r, true);
            if (mismatch != null) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": " + mismatch + "; left as it is");
                continue;
            }
            JEVisAttribute value = raw.getAttribute("Value");
            if (value == null) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": has no attribute 'Value'; left as it is");
                continue;
            }
            String key = fixKey(raw);
            int[] checked = new int[1];
            String differs = historyDiffers(value, profile, id, fix.path("verify"), from, checked);
            boolean replace = differs != null;
            if (replace) {
                List<History.Point> points = History.generate(profile, id, from, until);
                if (points.isEmpty()) {
                    report.add(SetupReport.Kind.SKIP, FIXES, text + ": the profile has no values up to " + until);
                    continue;
                }
                backupObject(raw, "Value samples (history replaced)");
                boolean has = value.hasSample();
                ObjectNode data = F.objectNode();
                data.put("object", paths.get(key));
                data.put("profile", profile);
                data.put("deleted", has ? value.getSampleCount() : 0);
                data.put("count", points.size());
                data.put("from", points.get(0).getTs().toString());
                data.put("to", points.get(points.size() - 1).getTs().toString());
                report.add(SetupReport.Kind.UPDATE, FIXES, text + " [" + id + "] · Value: " + differs + "; delete all "
                        + (has ? value.getSampleCount() + " samples " + value.getTimestampOfFirstSample() + " .. "
                        + value.getTimestampOfLastSample() : "0 samples") + ", write " + points.size() + " samples " + profile
                        + " " + points.get(0).getTs() + " .. " + points.get(points.size() - 1).getTs() + " (" + monthly(points)
                        + ")", data);
                replacements.add(new Step(FIXES, text + " · Value: replace history", () -> {
                    JEVisObject target = object(id);
                    if (target == null) throw new IllegalStateException(text + " missing");
                    guard(target);
                    String current = mismatch(target, r, true);
                    if (current != null) throw new IllegalStateException(text + ": " + current);
                    JEVisAttribute att = target.getAttribute("Value");
                    long old = att.hasSample() ? att.getSampleCount() : 0;
                    if (old > 0 && !att.deleteAllSample()) throw new IllegalStateException("deleting the values of " + id + " failed");
                    writePoints(att, points);
                    return text + " [" + id + "] · Value: " + old + " samples deleted, " + points.size() + " samples " + profile + " written";
                }));
            } else {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": history " + profile + " in place (" + checked[0]
                        + " values of the check windows match)");
            }
            DateTime start = replace ? newStart : historyStart(raw);
            planSettings(key, raw, fix.path("rowSettings"), start);

            String cleanKey = null;
            long cleanId = r.path("clean").asLong(0);
            if (cleanId > 0) {
                JEVisObject clean = object(cleanId);
                String cm = clean == null ? "not found" : !CLEAN_DATA_CLASS.equals(clean.getJEVisClassName())
                        ? "is a '" + clean.getJEVisClassName() + "', not a Clean Data" : firstParentMismatch(clean, id);
                if (cm == null && !inOrganisation(clean)) cm = "is not below the organisation";
                if (cm != null) {
                    report.add(SetupReport.Kind.WARNING, FIXES, text + ": clean row " + cleanId + " " + cm
                            + "; its settings and values are left as they are (check by hand)");
                } else {
                    cleanKey = fixKey(clean);
                    cleanIds.add(String.valueOf(cleanId));
                    planSettings(cleanKey, clean, fix.path("cleanSettings"), start);
                }
            }
            boolean reprocess = replace || settingsRepaired.contains(key) || cleanKey != null && settingsRepaired.contains(cleanKey);
            anyReprocess |= reprocess;
            if (cleanKey != null && reprocess) clears.add(clear(object(cleanId), cleanKey, replace ? "the raw row is replaced" : "its settings did not hold from the start"));
        }

        String calculation = null;
        for (JsonNode d : fix.path("derived")) {
            long id = d.path("id").asLong();
            String text = FIX_TEXT + "derived row " + id + (d.has("name") ? " '" + d.path("name").asText() + "'" : "");
            JEVisObject obj = object(id);
            String mismatch = obj == null ? "not found" : mismatch(obj, d, true);
            if (mismatch == null && !d.has("class") && !ROW_CLASSES.contains(obj.getJEVisClassName())) {
                mismatch = "is a '" + obj.getJEVisClassName() + "', not a data row";
            }
            if (mismatch != null) {
                report.add(SetupReport.Kind.SKIP, FIXES, text + ": " + mismatch + "; left as it is");
                continue;
            }
            String key = fixKey(obj);
            boolean clean = CLEAN_DATA_CLASS.equals(obj.getJEVisClassName());
            if (clean) {
                derivedCleanIds.add(String.valueOf(id));
                planSettings(key, obj, fix.path("cleanSettings"), historyStart(obj));
            } else {
                JEVisObject calc = calculationOf(id);
                if (calc != null) {
                    calculation = String.valueOf(calc.getID());
                    report.add(SetupReport.Kind.INFO, FIXES, text + " is the output of the Calculation '" + path(calc) + "' ["
                            + calc.getID() + "]: JECalc computes it again");
                } else {
                    report.add(SetupReport.Kind.INFO, FIXES, text + ": no readable Calculation has it as output; the jecalc "
                            + "service computes it again in its next cycle");
                }
            }
            if (anyReprocess || settingsRepaired.contains(key)) {
                clears.add(clear(obj, key, d.path("reason").asText(anyReprocess ? "computed from the replaced rows" : "")));
            }
        }

        for (Clear c : clears) {
            if (c.hasValues && !replacements.isEmpty()) steps.add(clearStep(c, "before the replacement"));
        }
        steps.addAll(replacements);
        for (Clear c : clears) steps.add(clearStep(c, replacements.isEmpty() ? "" : "after the replacement"));
        if (!cleanIds.isEmpty() || calculation != null) {
            chargeSteps = new String[]{String.join(",", cleanIds), calculation, String.join(",", derivedCleanIds)};
        }
    }

    private static final String FIX_TEXT = "REPLACE_HISTORY ";

    private Clear clear(JEVisObject obj, String key, String reason) throws JEVisException {
        JEVisAttribute value = obj.getAttribute("Value");
        boolean has = value != null && value.hasSample();
        String text = FIX_TEXT + "'" + paths.get(key) + "' [" + obj.getID() + "]";
        if (has) {
            backupObject(obj, "Value samples (computed again)");
            report.add(SetupReport.Kind.UPDATE, FIXES, text + " · Value: delete all " + value.getSampleCount() + " samples "
                    + value.getTimestampOfFirstSample() + " .. " + value.getTimestampOfLastSample() + " (" + reason
                    + "); computed again by the services");
        } else {
            report.add(SetupReport.Kind.INFO, FIXES, text + " · Value: no values yet (" + reason + ")");
        }
        return new Clear(obj.getID(), text, has);
    }

    private Step clearStep(Clear c, String when) {
        String suffix = when.isEmpty() ? "" : " " + when;
        return new Step(FIXES, c.text + " · Value: delete samples" + suffix, () -> {
            JEVisObject target = object(c.id);
            if (target == null) throw new IllegalStateException(c.text + " missing");
            guard(target);
            JEVisAttribute value = target.getAttribute("Value");
            if (value == null || !value.hasSample()) return c.text + " · Value: no values" + suffix;
            long n = value.getSampleCount();
            if (!value.deleteAllSample()) throw new IllegalStateException("deleting the values of " + c.id + " failed");
            return c.text + " · Value: " + n + " samples deleted" + suffix;
        });
    }

    private void planSettings(String key, JEVisObject obj, JsonNode settings, DateTime start) throws JEVisException {
        for (String name : (Iterable<String>) settings::fieldNames) {
            planConfigSetting(FIXES, key, obj, name, settings.get(name), "", start);
        }
    }

    /**
     * Registers an existing object of a fix like a plan object (for the settings), key "#id".
     */
    private String fixKey(JEVisObject obj) throws JEVisException {
        String key = "#" + obj.getID();
        ids.put(key, obj.getID());
        paths.put(key, path(obj));
        return key;
    }

    /**
     * @return why the row is not the one the fix describes (class, name, first parent, organisation), null if it is
     */
    private String mismatch(JEVisObject obj, JsonNode d, boolean inOrg) throws JEVisException {
        String m = mismatch(obj, d);
        if (m == null && d.has("parent")) m = firstParentMismatch(obj, d.get("parent").asLong());
        if (m == null && inOrg && !inOrganisation(obj)) m = "is not below the organisation";
        return m;
    }

    private static String firstParentMismatch(JEVisObject obj, long parent) throws JEVisException {
        List<JEVisObject> parents = obj.getParents();
        Long first = parents == null || parents.isEmpty() ? null : parents.get(0).getID();
        return first != null && first == parent ? null : "has the parent " + first + ", the plan expects " + parent;
    }

    /**
     * @return null if the attribute holds the profile at every quarter hour of the check windows (clipped to now; the
     * first week from {@code from} if none is left), otherwise the first difference
     */
    private String historyDiffers(JEVisAttribute att, String profile, long seed, JsonNode verify, Instant from, int[] checked)
            throws JEVisException {
        List<Instant[]> windows = new ArrayList<>();
        for (JsonNode w : verify) {
            Instant a = timestamp(w.path("from").asText()).toDate().toInstant();
            Instant b = timestamp(w.path("until").asText()).toDate().toInstant();
            if (b.isAfter(now)) b = now;
            if (a.isBefore(b)) windows.add(new Instant[]{a, b});
        }
        if (windows.isEmpty()) {
            Instant b = from.plus(java.time.Duration.ofDays(7));
            windows.add(new Instant[]{from, b.isAfter(now) ? now : b});
        }
        for (Instant[] w : windows) {
            Instant last = w[1].minusMillis(1);
            List<History.Point> points = History.generate(profile, seed, w[0], last);
            Map<Long, JEVisSample> have = new HashMap<>();
            for (JEVisSample s : att.getSamples(new DateTime(w[0].toEpochMilli()), new DateTime(last.toEpochMilli()))) {
                if (s.getTimestamp() != null) have.put(s.getTimestamp().getMillis(), s);
            }
            for (History.Point p : points) {
                JEVisSample s = have.get(p.getTs().toEpochMilli());
                if (s == null) return "no value at " + p.getTs() + " (profile " + p.getValue() + ")";
                Double v;
                try {
                    v = s.getValueAsDouble();
                } catch (Exception e) {
                    v = null;
                }
                if (v == null || Math.abs(v - p.getValue()) > 1e-4) {
                    return "value " + v + " at " + p.getTs() + ", the profile has " + p.getValue();
                }
                checked[0]++;
            }
        }
        return null;
    }

    /**
     * @return "monthly a–b kWh" over the complete months of the points
     */
    private static String monthly(List<History.Point> points) {
        Map<java.time.YearMonth, Double> sums = History.monthlySums(points);
        java.time.YearMonth last = java.time.YearMonth.from(points.get(points.size() - 1).getTs().atZone(History.ZONE));
        double min = Double.MAX_VALUE, max = 0;
        for (Map.Entry<java.time.YearMonth, Double> e : sums.entrySet()) {
            if (e.getKey().equals(last) && sums.size() > 1) continue;
            min = Math.min(min, e.getValue());
            max = Math.max(max, e.getValue());
        }
        return String.format(java.util.Locale.ROOT, "monthly %.0f–%.0f kWh", min, max);
    }

    /**
     * @return the Calculation whose "Output" child targets the object, null if none is readable
     */
    private JEVisObject calculationOf(long target) {
        try {
            JEVisClass type = ds.getJEVisClass("Calculation");
            if (type == null) return null;
            Pattern id = Pattern.compile("^\\s*(\\d+)");
            for (JEVisObject calc : ds.getObjects(type, false)) {
                if (isDeleted(calc)) continue;
                for (JEVisObject child : calc.getChildren()) {
                    if (!"Output".equals(child.getJEVisClassName())) continue;
                    String spec = latestString(child, "Output");
                    java.util.regex.Matcher m = spec != null ? id.matcher(spec) : null;
                    if (m != null && m.find() && Long.parseLong(m.group(1)) == target) return calc;
                }
            }
        } catch (Exception e) {
            logger.warn("Could not look up the calculation of {}", target, e);
        }
        return null;
    }

    private static void writePoints(JEVisAttribute att, List<History.Point> points) throws JEVisException {
        for (int from = 0; from < points.size(); from += WRITE_CHUNK) {
            List<JEVisSample> chunk = new ArrayList<>();
            for (History.Point p : points.subList(from, Math.min(points.size(), from + WRITE_CHUNK))) {
                chunk.add(att.buildSample(new DateTime(p.getTs().toEpochMilli()), p.getValue()));
            }
            att.addSamples(chunk);
        }
    }

    private DashboardWidgetFix.Result widgetFix(JEVisObject dashboard, JsonNode fix) throws JEVisException {
        JEVisAttribute att = dashboard.getAttribute(fix.path("attribute").asText());
        JsonNode root = att != null ? latestJson(att) : null;
        return DashboardWidgetFix.apply(root, fix, this::classOf);
    }

    private String classOf(Long id) {
        try {
            JEVisObject o = object(id);
            return o != null ? o.getJEVisClassName() : null;
        } catch (JEVisException e) {
            return null;
        }
    }

    // ================================================================================================ deletes

    private void planDeletes() throws JEVisException {
        for (JsonNode d : plan.path("delete")) {
            String label = label(d);
            JEVisObject obj = find(d);
            if (obj == null) {
                report.add(SetupReport.Kind.SKIP, DELETE, label + ": not found (already deleted)");
                continue;
            }
            String mismatch = mismatch(obj, d);
            if (mismatch != null) {
                report.add(SetupReport.Kind.WARNING, DELETE, label + ": " + mismatch + "; not deleted");
                continue;
            }
            if (!inOrganisation(obj)) {
                problem(DELETE, label + ": the object is not below the organisation");
                continue;
            }
            Verdict verdict = evaluate(d, obj, false);
            for (String check : verdict.checks) report.add(SetupReport.Kind.CHECK, DELETE, label + ": " + check);
            if (verdict.failure != null) {
                report.add(SetupReport.Kind.SKIP, DELETE, label + ": not deleted, " + verdict.failure);
                continue;
            }
            for (JEVisObject target : verdict.targets) {
                List<JEVisObject> tree = tree(target);
                tree.removeIf(o -> o != target && plannedDeletes.contains(o.getID()));
                for (JEVisObject o : tree) plannedDeletes.add(o.getID());
                backupTrees.put(target.getID(), "delete: " + d.path("reason").asText());
                ObjectNode data = F.objectNode();
                data.put("id", target.getID());
                data.put("class", target.getJEVisClassName());
                data.put("path", path(target));
                data.put("objects", tree.size());
                data.put("reason", d.path("reason").asText());
                report.add(SetupReport.Kind.DELETE, DELETE, target.getJEVisClassName() + " '" + path(target) + "' [" + target.getID()
                        + "]" + (tree.size() > 1 ? " with " + (tree.size() - 1) + " object(s) below" : "") + " → recycle bin; "
                        + d.path("reason").asText(), data);
            }
            steps.add(new Step(DELETE, label, () -> delete(d, label)));
        }
    }

    private String delete(JsonNode d, String label) throws Exception {
        JEVisObject obj = find(d);
        if (obj == null) {
            report.add(SetupReport.Kind.SKIP, DELETE, label + ": already gone");
            return null;
        }
        guard(obj);
        String mismatch = mismatch(obj, d);
        if (mismatch != null) throw new IllegalStateException(label + ": " + mismatch);
        Verdict verdict = evaluate(d, obj, true);
        for (String check : verdict.checks) report.add(SetupReport.Kind.CHECK, DELETE, label + ": " + check);
        if (verdict.failure != null) {
            report.add(SetupReport.Kind.WARNING, DELETE, label + ": not deleted, " + verdict.failure);
            return null;
        }
        List<String> done = new ArrayList<>();
        for (JEVisObject target : verdict.targets) {
            guard(target);
            List<JEVisObject> tree = tree(target);
            if (backup == null || !backup.contains(target.getID())) {
                if (backup == null) backup = new SetupBackup(backupDirectory);
                backup.backup(target, true, "delete: " + d.path("reason").asText());
            }
            if (!ds.deleteObject(target.getID(), false)) throw new IllegalStateException("deleting " + target.getID() + " failed");
            for (JEVisObject o : tree) deleted.add(o.getID());
            done.add(target.getJEVisClassName() + " '" + target.getName() + "' [" + target.getID() + "]");
        }
        return label + ": moved to the recycle bin: " + String.join(", ", done);
    }

    private static final class Verdict {
        final List<JEVisObject> targets = new ArrayList<>();
        final List<String> checks = new ArrayList<>();
        String failure;
    }

    /**
     * The checks of a deletion.
     *
     * @param exec true while applying (the actual state), false while planning (the deletions before it are assumed
     *             done, objects of the plan as created)
     */
    private Verdict evaluate(JsonNode d, JEVisObject obj, boolean exec) throws JEVisException {
        Verdict v = new Verdict();
        v.targets.add(obj);
        if (!d.path("checkFirst").asBoolean(false)) return v;
        JsonNode checks = d.get("checks");
        if (checks == null || !checks.isArray() || checks.size() == 0) {
            v.failure = "checkFirst without checks in the plan";
            return v;
        }
        Set<Long> gone = exec ? deleted : plannedDeletes;
        for (JsonNode check : checks) {
            String type = check.path("type").asText();
            String failure = null;
            String passed;
            switch (type) {
                case SetupPlan.CHECK_AFTER_CREATED: {
                    List<String> missing = new ArrayList<>();
                    for (JsonNode k : check.path("keys")) {
                        String key = k.asText().replaceFirst("^@", "");
                        boolean ok = exec ? ids.containsKey(key) && object(ids.get(key)) != null
                                : !brokenKeys.contains(key) && paths.containsKey(key);
                        if (!ok) missing.add("@" + key);
                    }
                    if (!missing.isEmpty()) failure = "not created: " + missing;
                    passed = check.path("keys").size() + " replacement(s) " + (exec ? "exist" : "exist or are created before");
                    break;
                }
                case SetupPlan.CHECK_NO_CHILDREN: {
                    List<String> left = new ArrayList<>();
                    for (JEVisObject c : obj.getChildren()) {
                        if (!gone.contains(c.getID()) && !isDeleted(c)) left.add("'" + c.getName() + "' [" + c.getID() + "]");
                    }
                    if (!left.isEmpty()) failure = "has " + left.size() + " child(ren): " + left;
                    passed = "no children" + (exec ? "" : " (after the deletions before it)");
                    break;
                }
                case SetupPlan.CHECK_NOT_REFERENCED: {
                    List<String> refs = references(obj.getID(), gone, exec);
                    if (!refs.isEmpty()) failure = "named in " + refs;
                    passed = "not named in any template, configuration or dashboard of the organisation";
                    break;
                }
                case SetupPlan.CHECK_OLD_INVOICES: {
                    Pattern pattern = Pattern.compile(check.path("numberPattern").asText());
                    List<JEVisObject> matching = new ArrayList<>();
                    List<String> others = new ArrayList<>();
                    int series = 0;
                    for (JEVisObject c : obj.getChildren()) {
                        if (gone.contains(c.getID()) || isDeleted(c)) continue;
                        String cls = c.getJEVisClassName();
                        if (INVOICE_CLASS.equals(cls)) {
                            String number = latestString(c, INVOICE_NUMBER);
                            if (number != null && pattern.matcher(number.trim()).matches()) {
                                matching.add(c);
                            } else {
                                others.add("invoice '" + c.getName() + "' (" + number + ")");
                            }
                        } else if (INVOICE_DATA_CLASS.equals(cls)) {
                            series++;
                        } else {
                            others.add(cls + " '" + c.getName() + "'");
                        }
                    }
                    if (others.isEmpty()) {
                        passed = "only " + matching.size() + " old invoice(s) matching " + pattern + " and " + series
                                + " series: the folder is deleted";
                    } else if (matching.isEmpty()) {
                        passed = null;
                        failure = "no invoice matches " + pattern + "; the folder holds other objects " + others;
                    } else {
                        v.targets.clear();
                        v.targets.addAll(matching);
                        passed = matching.size() + " invoice(s) matching " + pattern + " are deleted; the folder and its "
                                + series + " series are kept for " + others.size() + " other object(s): " + others;
                    }
                    break;
                }
                default:
                    failure = "unknown check '" + type + "'";
                    passed = null;
            }
            if (failure != null) {
                v.checks.add(type + " failed: " + failure);
                if (v.failure == null) v.failure = type + " failed";
            } else {
                v.checks.add(type + " passed: " + passed);
            }
        }
        return v;
    }

    /**
     * @return templates, configurations and dashboards of the organisation (not deleted) whose latest file names the id
     */
    private List<String> references(long id, Set<Long> gone, boolean fresh) throws JEVisException {
        Pattern pattern = Pattern.compile("(?<![0-9])" + id + "(?![0-9])");
        List<String> refs = new ArrayList<>();
        for (JEVisObject o : subtree(fresh)) {
            if (o.getID() == id || gone.contains(o.getID()) || !FILE_CLASSES.contains(o.getJEVisClassName())) continue;
            for (String name : FILE_ATTRIBUTES) {
                JEVisAttribute att = o.getAttribute(name);
                String text = att != null ? latestFileText(att) : null;
                if (text != null && pattern.matcher(text).find()) {
                    refs.add(o.getJEVisClassName() + " '" + o.getName() + "' [" + o.getID() + "]");
                    break;
                }
            }
        }
        return refs;
    }

    private JEVisObject find(JsonNode d) throws JEVisException {
        if (d.has("id")) return object(d.get("id").asLong());
        if (d.has("path")) return byPath(d.get("path").asText());
        return null;
    }

    private static String label(JsonNode d) {
        String what = d.has("id") ? String.valueOf(d.get("id").asLong()) : d.path("path").asText();
        return what + " (" + d.path("class").asText() + " '" + d.path("name").asText() + "')";
    }

    /**
     * @return why the object is not the one the plan describes (class / name), null if it is
     */
    private static String mismatch(JEVisObject obj, JsonNode d) throws JEVisException {
        String cls = d.path("class").asText(null);
        String name = d.path("name").asText(null);
        if (cls != null && !cls.equals(obj.getJEVisClassName())) {
            return "is a '" + obj.getJEVisClassName() + "', the plan expects '" + cls + "'";
        }
        if (name != null && !name.equals(obj.getName())) {
            return "is named '" + obj.getName() + "', the plan expects '" + name + "'";
        }
        return null;
    }

    // ================================================================================================ backup

    private void backupObject(JEVisObject obj, String reason) {
        backupObjects.merge(obj.getID(), reason, (a, b) -> a.contains(b) ? a : a + ", " + b);
    }

    private boolean backupAll() {
        if (backupObjects.isEmpty() && backupTrees.isEmpty()) {
            report.add(SetupReport.Kind.INFO, BACKUP, "nothing to back up (no existing object is changed or deleted)");
            return true;
        }
        try {
            backup = new SetupBackup(backupDirectory);
            report.setBackupDirectory(backupDirectory.toAbsolutePath().toString());
            for (Map.Entry<Long, String> e : backupObjects.entrySet()) {
                JEVisObject o = object(e.getKey());
                if (o == null) throw new IllegalStateException("object " + e.getKey() + " to back up not found");
                backup.backup(o, false, e.getValue());
            }
            for (Map.Entry<Long, String> e : backupTrees.entrySet()) {
                JEVisObject o = object(e.getKey());
                if (o == null) throw new IllegalStateException("object " + e.getKey() + " to back up not found");
                backup.backup(o, true, e.getValue());
            }
            report.add(SetupReport.Kind.BACKUP, BACKUP, backup.objectCount() + " object(s) and " + backup.fileCount()
                    + " file(s) backed up to " + backupDirectory.toAbsolutePath());
            return true;
        } catch (Exception e) {
            logger.error("Backup failed", e);
            report.add(SetupReport.Kind.PROBLEM, BACKUP, "Backup to " + backupDirectory.toAbsolutePath() + " failed: " + e
                    + ". Nothing was written.");
            return false;
        }
    }

    // ================================================================================================ manual steps

    private void manualSteps() {
        String org = organisation != null ? String.valueOf(organisation.getID()) : "<organisation id>";
        List<String> clean = new ArrayList<>();
        for (JsonNode o : plan.path("objects")) {
            if ("Clean Data".equals(o.path("class").asText())) {
                String key = o.path("key").asText();
                clean.add(ids.containsKey(key) ? String.valueOf(ids.get(key)) : "@" + key);
            }
        }
        String jar = "java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid " + org;
        report.addManualStep("JEDataProcessor: let it process the new clean data rows " + clean + " for the whole history "
                + "(the running jedataprocessor.service does it in its next cycles; at once, with the key of the services: "
                + "`source /etc/jevis/jevis.env; java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log -jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar "
                + "-c etc/jevis.xml -sm single -jid " + String.join(",", clean) + "`). Check that they have values from 2025-01 on.");
        if (chargeSteps != null) {
            String processor = "java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedataprocessor-single.log "
                    + "-jar JEDataProcessor/target/JEDataProcessor-1.5-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid ";
            String rows = chargeSteps[0];
            String sumClean = chargeSteps[2];
            boolean withSum = !sumClean.isEmpty();
            StringBuilder text = new StringBuilder("Charge points (case F), after the history was replaced: with the key of the "
                    + "services (`source /etc/jevis/jevis.env`) first the JEDataProcessor on the clean rows `" + processor
                    + rows + "`, then JECalc for the sum ");
            if (chargeSteps[1] != null) {
                text.append("`java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jecalc-single.log "
                        + "-jar JECalc/target/JECalc-1.0.1-jar-with-dependencies.jar -c etc/jevis.xml -sm single -jid ")
                        .append(chargeSteps[1]).append('`');
            } else {
                text.append("(no calculation found: the jecalc service computes it in its next cycle)");
            }
            if (withSum) text.append(", then the JEDataProcessor on the clean row of the sum `").append(processor).append(sumClean).append('`');
            text.append(". Without the single runs the services do it in their next cycles.");
            report.addManualStep(text.toString());
        }
        report.addManualStep("Invoice backfill, first dry: `source /etc/jevis/jevis.env; export JEVIS_API_KEY=\"$JEDEMO_API_KEY\"; "
                + jar + " -o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true`, then the same without `-o dryrun=true`.");
        StringBuilder checks = new StringBuilder("Checks of the plan:");
        for (JsonNode c : plan.path("checks")) checks.append(" (").append(c.asText()).append(')');
        report.addManualStep(checks.toString());
        report.addManualStep("Start the job again: `sudo systemctl start jedemo.service`, then `tail -f var/log/jedemo.log`.");
    }

    // ================================================================================================ helpers

    private interface Action {
        /**
         * @return what was done (report line), null if the action reported for itself
         */
        String run() throws Exception;
    }

    private static final class Step {
        final String section;
        final String text;
        final Action action;

        Step(String section, String text, Action action) {
            this.section = section;
            this.text = text;
            this.action = action;
        }
    }

    private static final class Parent {
        JEVisObject object;
        /**
         * plan key of a parent this run creates
         */
        String key;
        String path;
        /**
         * parentOf: the reference object is gone
         */
        boolean gone;
        long reference;
    }

    private Parent parent(String section, JsonNode node, String key) throws JEVisException {
        Parent p = new Parent();
        if (node.has("path")) {
            String path = node.get("path").asText();
            p.object = byPath(path);
            if (p.object == null) return brokenParent(section, key, "path '" + path + "' not found");
            p.path = path;
        } else if (node.has("key")) {
            String parentKey = node.get("key").asText();
            if (brokenKeys.contains(parentKey) || !paths.containsKey(parentKey)) {
                return brokenParent(section, key, "parent @" + parentKey + " is not available");
            }
            p.path = paths.get(parentKey);
            if (newKeys.contains(parentKey)) {
                p.key = parentKey;
            } else {
                p.object = object(ids.get(parentKey));
                if (p.object == null) return brokenParent(section, key, "parent @" + parentKey + " not found");
            }
        } else if (node.has("id")) {
            long id = node.get("id").asLong();
            p.object = object(id);
            if (p.object == null) return brokenParent(section, key, "parent " + id + " not found");
            if (!inOrganisation(p.object)) return brokenParent(section, key, "parent " + id + " is not below the organisation");
            p.path = path(p.object);
        } else if (node.has("parentOf")) {
            long id = node.get("parentOf").asLong();
            JEVisObject reference = object(id);
            if (reference == null) {
                p.gone = true;
                p.reference = id;
                p.path = "(parent of the deleted " + id + ")";
                return p;
            }
            List<JEVisObject> parents = reference.getParents();
            if (parents == null || parents.isEmpty()) return brokenParent(section, key, "object " + id + " has no parent");
            p.object = parents.get(0);
            if (!inOrganisation(p.object)) return brokenParent(section, key, "the parent of " + id + " is not below the organisation");
            p.path = path(p.object);
        } else {
            return brokenParent(section, key, "no parent in the plan");
        }
        return p;
    }

    private Parent brokenParent(String section, String key, String text) {
        broken(section, key, text);
        return null;
    }

    private void broken(String section, String key, String text) {
        brokenKeys.add(key);
        problem(section, "@" + key + ": " + text);
    }

    private void problem(String section, String text) {
        report.add(SetupReport.Kind.PROBLEM, section, text);
    }

    /**
     * @return the object, null if it does not exist, is not readable or is in the recycle bin
     */
    private JEVisObject object(Long id) throws JEVisException {
        if (id == null) return null;
        JEVisObject o = ds.getObject(id);
        return o == null || isDeleted(o) || deleted.contains(id) ? null : o;
    }

    private static boolean isDeleted(JEVisObject o) {
        return o.getDeleteTS() != null;
    }

    /**
     * The object of a plan key during apply.
     */
    private JEVisObject live(String key) throws JEVisException {
        Long id = ids.get(key);
        if (id == null) throw new IllegalStateException("@" + key + " has no id (not created)");
        JEVisObject o = object(id);
        if (o == null) throw new IllegalStateException("@" + key + " [" + id + "] not found");
        return o;
    }

    private JEVisAttribute writable(String key, String attribute) throws JEVisException {
        JEVisObject o = live(key);
        guard(o);
        JEVisAttribute att = o.getAttribute(attribute);
        if (att == null) throw new IllegalStateException("@" + key + " [" + o.getID() + "] has no attribute '" + attribute + "'");
        return att;
    }

    /**
     * Refuses (exception) to write an object that is not below the organisation.
     */
    private void guard(JEVisObject o) throws JEVisException {
        if (verified.contains(o.getID())) return;
        if (!inOrganisation(o)) {
            throw new IllegalStateException("refused: '" + o.getName() + "' [" + o.getID() + "] is not below the organisation "
                    + organisation.getID());
        }
        verified.add(o.getID());
    }

    /**
     * @return true if the organisation is the object or one of its ancestors (walking all parents)
     */
    boolean inOrganisation(JEVisObject o) throws JEVisException {
        Deque<JEVisObject> todo = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        todo.add(o);
        int count = 0;
        while (!todo.isEmpty() && count++ < MAX_PARENT_STEPS) {
            JEVisObject x = todo.poll();
            if (x == null || x.getID() == null || !seen.add(x.getID())) continue;
            if (x.getID().equals(organisation.getID())) return true;
            List<JEVisObject> parents = x.getParents();
            if (parents != null) todo.addAll(parents);
        }
        return false;
    }

    /**
     * @return the path of names from the organisation (first parent)
     */
    private String path(JEVisObject o) throws JEVisException {
        List<String> names = new ArrayList<>();
        JEVisObject x = o;
        Set<Long> seen = new HashSet<>();
        while (x != null && seen.add(x.getID())) {
            names.add(0, x.getName());
            if (x.getID().equals(organisation.getID())) return String.join(" / ", names);
            List<JEVisObject> parents = x.getParents();
            x = parents == null || parents.isEmpty() ? null : parents.get(0);
        }
        return "(outside) " + String.join(" / ", names);
    }

    private JEVisObject byPath(String path) throws JEVisException {
        String[] parts = path.split(" / ");
        if (parts.length == 0 || !parts[0].equals(organisation.getName())) return null;
        JEVisObject x = organisation;
        for (int i = 1; i < parts.length && x != null; i++) {
            JEVisObject next = null;
            for (JEVisObject c : x.getChildren()) {
                if (parts[i].equals(c.getName()) && !isDeleted(c) && !deleted.contains(c.getID())) {
                    if (next != null) throw new IllegalStateException("path '" + path + "': more than one '" + parts[i] + "'");
                    next = c;
                }
            }
            x = next;
        }
        return x;
    }

    /**
     * @return the child with class and name, null if none
     * @throws IllegalStateException if there is more than one
     */
    private JEVisObject child(JEVisObject parent, String jevisClass, String name) throws JEVisException {
        JEVisObject found = null;
        for (JEVisObject c : parent.getChildren()) {
            if (isDeleted(c) || deleted.contains(c.getID())) continue;
            if (jevisClass.equals(c.getJEVisClassName()) && name.equals(c.getName())) {
                if (found != null) {
                    throw new IllegalStateException("more than one " + jevisClass + " '" + name + "' below " + parent.getID()
                            + ": [" + found.getID() + ", " + c.getID() + "]");
                }
                found = c;
            }
        }
        return found;
    }

    private JEVisObject findInOrganisation(String jevisClass, String name, String key, String section) throws JEVisException {
        List<JEVisObject> found = new ArrayList<>();
        for (JEVisObject o : subtree(false)) {
            if (jevisClass.equals(o.getJEVisClassName()) && name.equals(o.getName())) found.add(o);
        }
        if (found.size() > 1) {
            problem(section, "@" + key + ": " + found.size() + " objects " + jevisClass + " '" + name + "' in the organisation");
            return null;
        }
        return found.isEmpty() ? null : found.get(0);
    }

    /**
     * @return the organisation and all objects below it (no links), cached while planning
     */
    private List<JEVisObject> subtree(boolean fresh) throws JEVisException {
        if (!fresh && subtreeCache != null) return subtreeCache;
        List<JEVisObject> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<JEVisObject> todo = new ArrayDeque<>();
        todo.add(organisation);
        while (!todo.isEmpty()) {
            JEVisObject o = todo.poll();
            if (!seen.add(o.getID()) || isDeleted(o) || deleted.contains(o.getID())) continue;
            if (LINK_CLASS.equals(o.getJEVisClassName())) continue;
            list.add(o);
            todo.addAll(o.getChildren());
        }
        if (!fresh) subtreeCache = list;
        return list;
    }

    private List<JEVisObject> tree(JEVisObject root) throws JEVisException {
        List<JEVisObject> list = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        Deque<JEVisObject> todo = new ArrayDeque<>();
        todo.add(root);
        while (!todo.isEmpty()) {
            JEVisObject o = todo.poll();
            if (!seen.add(o.getID())) continue;
            list.add(o);
            todo.addAll(o.getChildren());
        }
        return list;
    }

    private Long knownId(String key) {
        return newKeys.contains(key) ? null : ids.get(key);
    }

    /**
     * @return the JSON with every plan key replaced by its id
     * @throws IllegalStateException if a plan key has no id
     */
    private JsonNode resolveNow(JsonNode node) {
        JsonNode resolved = SetupPlan.resolveKeys(node, ids::get);
        if (hasPlanKeys(resolved)) throw new IllegalStateException("unresolved keys in " + shorten(resolved.toString()));
        return resolved;
    }

    /**
     * @return true if a text value of the JSON is "@key" of a plan object (unresolved)
     */
    private boolean hasPlanKeys(JsonNode node) {
        if (node == null) return false;
        if (node.isTextual()) {
            String text = node.asText();
            return text.startsWith("@") && (paths.containsKey(text.substring(1)) || brokenKeys.contains(text.substring(1)));
        }
        for (JsonNode child : node) if (hasPlanKeys(child)) return true;
        return false;
    }

    /**
     * @return the tree as it reads back from its text (ids of resolved keys are long nodes, read back int nodes)
     */
    private JsonNode normalize(JsonNode node) throws IOException {
        return mapper.readTree(mapper.writeValueAsBytes(node));
    }

    private JsonNode latestJson(JEVisAttribute att) {
        String text = latestFileText(att);
        if (text == null) return null;
        try {
            return mapper.readTree(text);
        } catch (IOException e) {
            return null;
        }
    }

    private static String latestFileText(JEVisAttribute att) {
        try {
            JEVisSample s = att.getLatestSample();
            JEVisFile file = s != null ? s.getValueAsFile() : null;
            return file != null && file.getBytes() != null ? new String(file.getBytes(), StandardCharsets.UTF_8) : null;
        } catch (Exception e) {
            logger.debug("No file in {}", att.getName(), e);
            return null;
        }
    }

    private static String latestString(JEVisObject o, String attribute) throws JEVisException {
        JEVisAttribute att = o.getAttribute(attribute);
        JEVisSample s = att != null ? att.getLatestSample() : null;
        return s != null ? s.getValueAsString() : null;
    }

    private static JEVisSample sampleAt(JEVisAttribute att, DateTime ts) throws JEVisException {
        for (JEVisSample s : att.getSamples(ts, ts)) {
            if (s.getTimestamp() != null && s.getTimestamp().getMillis() == ts.getMillis()) return s;
        }
        return null;
    }

    static DateTime timestamp(String text) {
        return new DateTime(OffsetDateTime.parse(text).toInstant().toEpochMilli());
    }

    /**
     * @return the value in the primitive type of the attribute
     */
    static Object toWrite(JsonNode value, JEVisAttribute att) {
        int type;
        try {
            type = att.getPrimitiveType();
        } catch (Exception e) {
            type = JEVisConstants.PrimitiveType.STRING;
        }
        String text = value.asText().trim();
        switch (type) {
            case JEVisConstants.PrimitiveType.DOUBLE:
                return value.isNumber() ? value.asDouble() : Double.parseDouble(text.replace(',', '.'));
            case JEVisConstants.PrimitiveType.LONG:
                return value.isNumber() ? value.asLong() : new BigDecimal(text).longValue();
            case JEVisConstants.PrimitiveType.BOOLEAN:
                return value.isBoolean() ? value.asBoolean() : Boolean.parseBoolean(text) || "1".equals(text);
            default:
                return value.isTextual() ? value.asText() : value.asText();
        }
    }

    /**
     * @return true if the stored value equals the wanted one (numbers numerically, booleans ignoring case, JSON as
     * tree)
     */
    /** true/false, also 1/0 as JEVis stores booleans; null for anything else */
    static Boolean asBoolean(String v) {
        if (v == null) return null;
        String t = v.trim();
        if (t.equalsIgnoreCase("true") || t.equals("1")) return Boolean.TRUE;
        if (t.equalsIgnoreCase("false") || t.equals("0")) return Boolean.FALSE;
        return null;
    }

    boolean same(String existing, JsonNode wanted) {
        if (existing == null || wanted == null || wanted.isNull()) return false;
        String a = existing.trim();
        String b = wanted.isValueNode() ? wanted.asText().trim() : wanted.toString();
        if (a.equals(b)) return true;
        Double x = number(a);
        Double y = number(b);
        if (x != null && y != null) return Math.abs(x - y) <= 1e-9 * Math.max(1, Math.abs(y));
        /* JEVis stores boolean attributes as 1/0 (live 2026-11-13): "1" equals true, "0" equals false */
        Boolean ba = asBoolean(a);
        Boolean bb = asBoolean(b);
        if (ba != null && bb != null && (isBoolean(a) || isBoolean(b))) return ba.equals(bb);
        if ((a.startsWith("{") || a.startsWith("[")) && (b.startsWith("{") || b.startsWith("["))) {
            try {
                return mapper.readTree(a).equals(mapper.readTree(b)) || mapper.readTree(a).equals(wanted);
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }

    private static Double number(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isBoolean(String text) {
        return "true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text);
    }

    private static String display(JsonNode value) {
        return shorten(value.isValueNode() ? value.asText() : value.toString());
    }

    private static String shorten(String text) {
        if (text == null) return "null";
        String t = text.replace('\n', ' ');
        return t.length() <= 120 ? t : t.substring(0, 100) + "… (" + t.length() + " characters)";
    }

    /**
     * The unit formula for a label of the plan, as the demo stores its units: physical units with their formula
     * (kWh = "kW·h", m³ = "㎥" (U+33A5)), prices, factors and indices with formula "one" and the label.
     *
     * @return {formula, label}
     */
    static String[] unitFormula(String label) {
        switch (label) {
            case "kWh":
                return new String[]{"kW·h", "kWh"};
            case "MWh":
                return new String[]{"MW·h", "MWh"};
            case "kW":
                return new String[]{"kW", "kW"};
            case "m³":
                return new String[]{"㎥", "m³"};
            case "%":
                return new String[]{"%", "%"};
            default:
                return new String[]{"one", label};
        }
    }

    /**
     * The unit for a label of the plan, built like the web service client reads units ({@link JsonUnit}: formula,
     * label, prefix NONE).
     */
    static JEVisUnit jevisUnit(String label) {
        String[] spec = unitFormula(label);
        JsonUnit json = new JsonUnit();
        json.setFormula(spec[0]);
        json.setLabel(spec[1]);
        json.setPrefix("NONE");
        return new JEVisUnitImp(json);
    }

    private static String label(JEVisUnit unit) {
        return unit != null ? unit.getLabel() : null;
    }
}
