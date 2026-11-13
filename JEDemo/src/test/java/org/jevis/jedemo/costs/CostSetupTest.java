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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonUnit;
import org.jevis.jedemo.costs.FakeTree.Node;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * The cost demo setup against an in-memory demo organisation that looks like the live one (objects referenced by the
 * plan, old templates, invoice folders, dashboard, JEDemo service object outside the organisation).
 */
public class CostSetupTest {

    static final long ORG = 23500;
    static final long OTHER_ORG = 30000;
    static final long SERVICE = 5000;
    static final Instant NOW = Instant.parse("2026-11-13T10:05:00Z");
    static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    static final DateTime T0 = new DateTime(2026, 11, 6, 0, 0, BERLIN);
    static final String DASHBOARD = "{\"version\":\"1.1\",\"width\":1863.0,\"Dash Board Color\":\"#f4f4f4\",\"Zoom Factor\":1.0,"
            + "\"widget\":[{\"uuid\":20,\"WidgetType\":\"Value Widget\",\"title\":\"Bezug\",\"dataHandler\":{\"objectID\":23734}},"
            + "{\"uuid\":21,\"WidgetType\":\"Image\",\"title\":\"Neues Bild\",\"image\":{\"objectID\":23723,\"dashboardLinker\":{\"dashboardObject\":0}}},"
            + "{\"uuid\":22,\"WidgetType\":\"Image\",\"title\":\"Logo\",\"image\":{\"objectID\":23999}}],\"Update Rate\":900}";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> files = CostDemoGenerator.files();
    private JsonNode plan;
    private FakeTree tree;
    private Node org;
    private int backups;

    @Before
    public void setUp() throws Exception {
        plan = mapper.readTree(files.get("setup-plan.json"));
        tree = world("Sun and Charge Demo");
        org = tree.node(ORG);
    }

    /**
     * The demo organisation before the setup.
     */
    private FakeTree world(String organisationName) {
        FakeTree t = new FakeTree();
        Node organisation = t.add(ORG, organisationName, "Organization", null);
        String prefix = plan.path("organisation").asText();
        for (String section : new String[]{"objects", "configurations"}) {
            for (JsonNode o : plan.path(section)) {
                String path = o.path("parent").path("path").asText(null);
                if (path != null) ensure(t, organisation, path.replace(prefix, organisationName));
            }
        }
        Node contracts = t.path(organisation, CostCases.MW_CONTRACTS);
        Node musterwerk = t.path(organisation, CostCases.MUSTERWERK);
        Node partners = ensure(t, organisation, CostCases.MW_CONTRACTS + " / Vertragspartner");
        for (JsonNode o : plan.path("objects")) {
            if ("update".equals(o.path("action").asText())) {
                t.add(o.path("id").asLong(), o.path("name").asText(), o.path("class").asText(), partners);
            }
        }
        /* the dues have last year's VAT already, the work price of the supplier a different value */
        t.node(CostCases.DUES).set("VAT Electricity", new DateTime(2025, 1, 1, 0, 0, BERLIN), "19.0");
        t.node(CostCases.SUPPLIER_ELECTRICITY).set("Energy Price HT", new DateTime(2025, 1, 1, 0, 0, BERLIN), "15.5");
        Node data = t.add("Daten", "Data Directory", musterwerk);
        t.add(CostCases.CLEAN_GAS_ENERGY, "Bereinigte Daten", "Clean Data", t.add("Gas - Hauptzähler (Energie)", "Data", data));
        t.add(CostCases.HOURLY_MODEL, "Stundenwerte", "Clean Data", data)
                .set("Enabled", T0, true).set("Period", T0, "PT1H").set("Period Alignment", T0, "{\"aggregation\":\"HOURLY\"}")
                .set("Value is a Quantity", T0, true).set("Value", T0, 12.5);
        t.add(23723, "Bereinigte Daten", "Clean Data", data);

        Node templates = t.add("Vorlagen", "Result Calculation Template Directory", contracts);
        t.add(CostCases.OLD_TEMPLATE, "stromrechnung", "Result Calculation Template", templates)
                .file("Template File", T0, "stromrechnung.json", "{\"inputs\":[{\"objectID\":23950},{\"objectID\":23517}]}");
        t.add(23951, "Kostenrechnung - E-Mobilität", "Result Calculation Template", templates)
                .file("Template File", T0, "emob.json", "{\"inputs\":[{\"objectID\":23948}]}");
        t.add(23518, "Strom - Gesamt", "Accounting Configuration", t.path(organisation, CostCases.MW_CONTRACTS + " / Verträge"))
                .file("Template File", T0, "gesamt.json", "{\"templateSelection\":23952,\"selectedInputs\":[{\"objectID\":23517}]}");
        t.add(23517, "Netzbetreiber Nord", "Electricity Grid Operator", contracts);
        Node values = t.add(23943, "Rechnungswerte", "Base Data Directory", t.path(organisation, CostCases.MUSTERWERK + " / Stammdaten"));
        t.add(23950, "Strom - Hauptzähler", "Base Data", values).set("Value", T0, 1234.5);
        t.add(23948, "Strom - Ladestrom", "Base Data", values).set("Value", T0, 99.0);

        Node invoices = ensure(t, organisation, CostCases.MW_CONTRACTS + " / Energierechnungen");
        Node electricity = t.add(24598, "Electricity", "Energy Invoices Directory", invoices);
        Node gas = t.add(24593, "Gas", "Energy Invoices Directory", invoices);
        for (int month = 1; month <= 3; month++) {
            String m = String.format("2026%02d", month);
            invoice(t, electricity, "EN-" + m + "-S");
            invoice(t, gas, "EN-" + m + "-G");
        }
        t.add("Kosten netto laut Rechnung", "Energy Invoice Data", electricity).set("Value", T0, 100.0);
        t.add("Kosten netto laut Rechnung", "Energy Invoice Data", gas).set("Value", T0, 50.0);

        Node dashboards = t.add("Dashboards", "Dashboard Directory", organisation);
        t.add(24054, "01 - Übersicht", "Dashboard Analysis", dashboards).file("Data Model File", T0, "Uebersicht.json", DASHBOARD);

        /* outside: another organisation and the service object */
        Node other = t.add(OTHER_ORG, "Kunde Nord", "Organization", null);
        t.add("Zähler", "Data", other);
        Node system = t.add(1, "System", "Organization", null);
        Node services = t.add(2, "Service Directory", "Service Directory", system);
        t.add(SERVICE, "JEDemo", "JEDemo", services).set("Organization", T0, String.valueOf(ORG));
        return t;
    }

    private static void invoice(FakeTree t, Node folder, String number) {
        t.add(number + " Energieversorger Nord", "Energy Invoice", folder)
                .set("Invoice Number", T0, number).file("Invoice File", T0, number + ".pdf", "%PDF " + number);
    }

    private static Node ensure(FakeTree t, Node organisation, String path) {
        String[] parts = path.split(" / ");
        Node x = organisation;
        for (int i = 1; i < parts.length; i++) {
            Node next = t.child(x, parts[i]);
            x = next != null ? next : t.add(parts[i], "Directory", x);
        }
        return x;
    }

    private SetupReport run(boolean apply) throws Exception {
        return run(apply, NOW, temp.getRoot().toPath().resolve("backup" + (++backups)));
    }

    private SetupReport run(boolean apply, Instant now, Path backup) {
        CostSetup setup = new CostSetup(tree.ds, apply, Clock.fixed(now, ZoneOffset.UTC), plan,
                name -> files.containsKey(name) ? files.get(name).getBytes(StandardCharsets.UTF_8) : null, backup);
        return setup.run(ORG);
    }

    private static String problems(SetupReport report) {
        List<String> list = new ArrayList<>();
        for (SetupReport.Entry e : report.entries(SetupReport.Kind.PROBLEM)) list.add(e.getText());
        return list.toString();
    }

    private static String actions(SetupReport report) {
        List<String> list = new ArrayList<>();
        for (SetupReport.Entry e : report.getEntries()) if (e.getKind().isAction()) list.add(e.toString());
        return list.toString();
    }

    private static boolean has(SetupReport report, SetupReport.Kind kind, String text) {
        for (SetupReport.Entry e : report.entries(kind)) if (e.getText().contains(text)) return true;
        return false;
    }

    private int creates() {
        int n = 0;
        for (JsonNode o : plan.path("objects")) if (!"update".equals(o.path("action").asText())) n++;
        return n + plan.path("templates").size() + plan.path("configurations").size();
    }

    // ================================================================================================ dry run

    @Test
    public void dryRunWritesNothingAndReportsEveryAction() throws Exception {
        SetupReport report = run(false);
        assertFalse(problems(report), report.hasProblems());
        assertTrue(tree.writes.toString(), tree.writes.isEmpty());
        assertTrue(report.getResult(), report.getResult().startsWith("dry run"));
        assertNull("no backup in a dry run", report.getBackupDirectory());
        assertFalse(Files.exists(temp.getRoot().toPath().resolve("backup1")));

        assertEquals(creates(), report.count(SetupReport.Kind.CREATE));
        assertTrue(has(report, SetupReport.Kind.CREATE, "Building 'Außenlager Süd' below 'Sun and Charge Demo / Standorte'"));
        assertTrue(has(report, SetupReport.Kind.WRITE, "Fernwärme - Außenlager Süd · Value: history HEAT_COUNTER, "));
        assertTrue(has(report, SetupReport.Kind.WRITE, "· Value: history GAS_PRICE_INDEX, 84 samples"));
        assertTrue("old → new", has(report, SetupReport.Kind.UPDATE, "Energy Price HT @ 2025-01-01T00:00:00.000+01:00: 15.5 → 14.8"));
        assertFalse("equal value is not written again", has(report, SetupReport.Kind.UPDATE, "VAT Electricity @ 2025"));
        assertTrue(has(report, SetupReport.Kind.WRITE, "Stundenwerte · Period Alignment = {\"aggregation\":\"HOURLY\"} (copied from 23686)"));
        assertTrue(has(report, SetupReport.Kind.WRITE, "'Invoice Cases': (empty) → 5 cases [A, B, C, D, E]"));
        assertTrue(has(report, SetupReport.Kind.UPDATE, "remove widget 21 (Image, 'Neues Bild', image.objectID = 23723, a Clean Data)"));

        Set<Long> deletes = new HashSet<>();
        for (SetupReport.Entry e : report.entries(SetupReport.Kind.DELETE)) deletes.add(e.getData().get("id").asLong());
        long electricityFolder = tree.path(org, CostCases.MW_CONTRACTS + " / Energierechnungen / Electricity").id;
        assertEquals(new HashSet<>(java.util.Arrays.asList(23951L, 23952L, 23518L, 23517L, 23950L, 23948L, 23943L,
                electricityFolder, 24593L)), deletes);
        assertTrue(has(report, SetupReport.Kind.CHECK, "23943 (Base Data Directory 'Rechnungswerte'): NO_CHILDREN passed"));
        assertTrue(has(report, SetupReport.Kind.CHECK, "NOT_REFERENCED passed"));
        assertTrue(has(report, SetupReport.Kind.CHECK, "AFTER_CREATED passed: 6 replacement(s) exist or are created before"));

        report.write(java.nio.file.Paths.get("target"), "costdemo-setup-dryrun-example");
        Path file = report.write(temp.getRoot().toPath().resolve("log"), "costdemo-setup-dryrun");
        String md = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(md.contains("# Cost demo setup - dry run (nothing written)"));
        assertTrue(md.contains("## Manual steps afterwards"));
        assertTrue(md.contains("-o invoicesFrom=2025-01 -o invoicesOnly=true -o dryrun=true"));
        JsonNode json = mapper.readTree(temp.getRoot().toPath().resolve("log").resolve("costdemo-setup-dryrun.json").toFile());
        assertEquals("dryrun", json.get("mode").asText());
        assertEquals(report.getEntries().size(), json.get("entries").size());
    }

    // ================================================================================================ apply

    @Test
    public void applyCreatesEverythingWithResolvedIdsAndASecondRunIsANoOp() throws Exception {
        SetupReport report = run(true);
        assertFalse(problems(report), report.hasProblems());
        assertTrue(report.getResult(), report.getResult().startsWith("applied"));

        /* every object of the plan was created once */
        assertEquals(creates(), tree.writes.stream().filter(w -> w.startsWith("create ")).count());
        Node site = tree.path(org, "Sun and Charge Demo / Standorte / Außenlager Süd");
        assertEquals("Building", site.jevisClass);
        assertEquals("Lagerweg 5, 54321 Musterstadt", site.latest("Address"));
        Node heat = tree.path(org, "Sun and Charge Demo / Standorte / Außenlager Süd / Daten / Wärme / Fernwärme - Außenlager Süd");
        assertEquals("kWh", heat.att("Value").inputUnit.getLabel());
        assertTrue(heat.att("Value").samples.size() > 16000);
        assertTrue(heat.att("Value").samples.lastKey() <= NOW.toEpochMilli());
        Node hourly = tree.child(tree.node(CostCases.CLEAN_GAS_ENERGY), "Stundenwerte");
        assertEquals("{\"aggregation\":\"HOURLY\"}", hourly.latest("Period Alignment"));
        assertEquals("PT1H", hourly.latest("Period"));
        assertNull("the values of the model are not copied", hourly.latest("Value"));
        long y2025 = new DateTime(2025, 1, 1, 0, 0, BERLIN).getMillis();
        assertEquals("old → new", "14.8", tree.node(CostCases.SUPPLIER_ELECTRICITY).att("Energy Price HT").samples.get(y2025)[0].toString());

        /* configuration A: keys resolved, template selection = the new template A */
        Node templateA = find("Result Calculation Template", "Kostenrechnung Strom RLM");
        assertEquals(tree.node(CostCases.OLD_TEMPLATE).parent.name, templateA.parent.name);
        Node configurationB = find("Accounting Configuration", "Strom SLP - Außenlager Süd");
        JsonNode config = mapper.readTree(configurationB.latestFile("Template File"));
        assertFalse(config.toString(), config.toString().contains("\"@"));
        assertEquals(find("Result Calculation Template", "Kostenrechnung Strom SLP").id.longValue(), config.get("templateSelection").asLong());
        Node cleanB = tree.path(org, "Sun and Charge Demo / Standorte / Außenlager Süd / Daten / Strom / Strom - Außenlager Süd / Bereinigte Daten");
        boolean found = false;
        for (JsonNode input : config.get("selectedInputs")) found |= input.path("objectID").asLong() == cleanB.id;
        assertTrue("clean row of B is an input", found);

        /* service: invoice cases with ids */
        JsonNode cases = mapper.readTree((String) tree.node(SERVICE).latest("Invoice Cases"));
        assertEquals(5, cases.size());
        assertEquals(find("Energy Invoices Directory", "Strom RLM Hauptanschluss").id.longValue(), cases.get(0).get("directory").asLong());
        assertFalse(cases.toString().contains("\"@"));

        /* fix: widget 21 removed, everything else kept */
        JsonNode dashboard = mapper.readTree(tree.node(24054).latestFile("Data Model File"));
        ObjectNode expected = (ObjectNode) mapper.readTree(DASHBOARD);
        ((com.fasterxml.jackson.databind.node.ArrayNode) expected.get("widget")).remove(1);
        assertEquals(expected, dashboard);

        /* deletes: recycle bin only, after all writes */
        int lastWrite = -1, firstDelete = Integer.MAX_VALUE;
        for (int i = 0; i < tree.writes.size(); i++) {
            String w = tree.writes.get(i);
            assertFalse(w, w.startsWith("deleteForever"));
            if (w.startsWith("delete ")) firstDelete = Math.min(firstDelete, i);
            else lastWrite = i;
        }
        assertTrue("deletions come last", lastWrite < firstDelete);
        for (long id : new long[]{23951, 23952, 23518, 23517, 23950, 23948, 23943, 24593}) {
            assertTrue(id + " deleted", tree.node(id).deleted);
        }
        assertNull(tree.child(tree.path(org, CostCases.MW_CONTRACTS + " / Energierechnungen"), "Electricity"));
        assertFalse("the other organisation is untouched", tree.writes.stream().anyMatch(w -> w.contains(" " + OTHER_ORG)));
        for (String w : tree.writes) {
            if (w.startsWith("sample " + SERVICE)) assertTrue(w, w.contains("Invoice Cases"));
        }

        /* backup before the changes */
        Path backup = java.nio.file.Paths.get(report.getBackupDirectory());
        assertTrue(Files.exists(backup.resolve("manifest.json")));
        JsonNode supplier = mapper.readTree(backup.resolve("objects").resolve(CostCases.SUPPLIER_ELECTRICITY + ".json").toFile());
        assertEquals("15.5", supplier.get("attributes").get("Energy Price HT").get("samples").get(0).get("value").asText());
        JsonNode dash = mapper.readTree(backup.resolve("objects").resolve("24054.json").toFile());
        String file = dash.get("attributes").get("Data Model File").get("samples").get(0).get("file").asText();
        assertEquals(DASHBOARD, new String(Files.readAllBytes(backup.resolve(file)), StandardCharsets.UTF_8));
        assertTrue(Files.exists(backup.resolve("objects").resolve(SERVICE + ".json")));
        for (long id : new long[]{23951, 23952, 23518, 23517, 23950, 23948, 23943, 24593}) {
            assertTrue(id + " backed up", Files.exists(backup.resolve("objects").resolve(id + ".json")));
        }
        assertTrue("invoice PDFs backed up", Files.list(backup.resolve("files")).count() >= 6);

        /* second run: nothing to do, nothing written */
        int writes = tree.writeCount();
        SetupReport again = run(true, NOW.plusSeconds(600), temp.getRoot().toPath().resolve("again"));
        assertFalse(problems(again), again.hasProblems());
        assertEquals(actions(again), 0, again.actions());
        assertTrue(again.getResult(), again.getResult().startsWith("nothing to do"));
        assertEquals(tree.writes.subList(writes, tree.writeCount()).toString(), writes, tree.writeCount());
        assertFalse("no backup when nothing changes", Files.exists(temp.getRoot().toPath().resolve("again")));
        assertTrue(has(again, SetupReport.Kind.SKIP, "widget 21 is no longer in the dashboard"));
        assertTrue(has(again, SetupReport.Kind.SKIP, "23943 (Base Data Directory 'Rechnungswerte'): not found (already deleted)"));
    }

    private Node find(String jevisClass, String name) {
        Node found = null;
        for (Node n : tree.nodes.values()) {
            if (!n.deleted && n.jevisClass.equals(jevisClass) && n.name.equals(name)) {
                assertNull("unique " + jevisClass + " " + name, found);
                found = n;
            }
        }
        return found;
    }

    @Test
    public void aFailureStopsBeforeTheDeletionsAndTheNextRunContinues() throws Exception {
        tree.failCreate.add("Community Heating Supply Contractor");
        SetupReport report = run(true);
        assertTrue(report.getResult(), report.getResult().startsWith("stopped at step"));
        assertTrue(problems(report), has(report, SetupReport.Kind.PROBLEM, "were not carried out"));
        assertFalse("no deletion", tree.writes.stream().anyMatch(w -> w.startsWith("delete")));
        assertNotNull("objects before the failure exist", find("Building", "Außenlager Süd"));

        tree.failCreate.clear();
        SetupReport second = run(true);
        assertFalse(problems(second), second.hasProblems());
        assertTrue(second.getResult(), second.getResult().startsWith("applied"));
        assertTrue(has(second, SetupReport.Kind.REUSE, "@S.site = Building 'Sun and Charge Demo / Standorte / Außenlager Süd'"));
        assertNotNull(find("Community Heating Supply Contractor", "Wärmeversorgung Nord - Fernwärme Außenlager Süd"));
        assertTrue(tree.node(23518).deleted);
        SetupReport third = run(false);
        assertEquals(actions(third), 0, third.actions());
    }

    // ================================================================================================ safety

    @Test
    public void refusesAnOrganisationWithoutDemoInItsName() throws Exception {
        tree = world("Sun and Charge");
        SetupReport report = run(true);
        assertTrue(report.hasProblems());
        assertTrue(problems(report), has(report, SetupReport.Kind.PROBLEM, "the name does not contain \"Demo\""));
        assertTrue(report.getResult().startsWith("refused"));
        assertTrue(tree.writes.isEmpty());
    }

    @Test
    public void refusesAnObjectOutsideTheOrganisation() throws Exception {
        Node supplier = tree.node(CostCases.SUPPLIER_ELECTRICITY);
        supplier.parent.children.remove(supplier);
        supplier.parent = tree.node(OTHER_ORG);
        tree.node(OTHER_ORG).children.add(supplier);
        SetupReport report = run(true);
        assertTrue(problems(report), has(report, SetupReport.Kind.PROBLEM, "@A.supplier: object 23510 is not below the organisation"));
        assertTrue(report.getResult().startsWith("refused"));
        assertTrue(tree.writes.toString(), tree.writes.isEmpty());
    }

    @Test
    public void refusesToDeleteOutsideTheOrganisation() throws Exception {
        Node grid = tree.node(23517);
        grid.parent.children.remove(grid);
        grid.parent = tree.node(OTHER_ORG);
        tree.node(OTHER_ORG).children.add(grid);
        SetupReport report = run(true);
        assertTrue(problems(report), has(report, SetupReport.Kind.PROBLEM, "23517 (Electricity Grid Operator 'Netzbetreiber Nord'): the object is not below the organisation"));
        assertTrue(tree.writes.isEmpty());
    }

    @Test
    public void aFailedBackupAbortsBeforeAnyWrite() throws Exception {
        Path notADirectory = temp.newFile("backup-file").toPath();
        SetupReport report = run(true, NOW, notADirectory);
        assertTrue(report.getResult(), report.getResult().startsWith("aborted: the backup failed"));
        assertTrue(has(report, SetupReport.Kind.PROBLEM, "Nothing was written"));
        assertTrue(tree.writes.toString(), tree.writes.isEmpty());
    }

    // ================================================================================================ checks

    @Test
    public void deleteChecksKeepWhatIsStillUsed() throws Exception {
        /* 23948 still named by a dashboard, so it stays, and so does its folder 23943 */
        tree.add("Ladestrom", "Dashboard Analysis", tree.node(24054).parent)
                .file("Data Model File", T0, "lade.json", "{\"widget\":[{\"uuid\":1,\"dataHandler\":{\"objectID\":23948}}]}");
        /* a manual invoice in the gas folder: only the old invoices go, the folder stays */
        invoice(tree, tree.node(24593), "RE-4711");
        /* an old credit note of the fixed JEDemo invoices goes with them */
        invoice(tree, tree.node(24593), "EN-202602-G-GS1");
        SetupReport report = run(true);
        assertFalse(problems(report), report.hasProblems());
        assertTrue(has(report, SetupReport.Kind.CHECK, "23948 (Base Data 'Strom - Ladestrom'): NOT_REFERENCED failed: named in [Dashboard Analysis 'Ladestrom'"));
        assertTrue(has(report, SetupReport.Kind.CHECK, "23943 (Base Data Directory 'Rechnungswerte'): NO_CHILDREN failed: has 1 child(ren): ['Strom - Ladestrom' [23948]]"));
        assertFalse(tree.node(23948).deleted);
        assertFalse(tree.node(23943).deleted);
        assertTrue(tree.node(23950).deleted);

        Node gas = tree.node(24593);
        assertFalse(gas.deleted);
        List<String> left = new ArrayList<>();
        for (Node c : gas.children) if (!c.deleted) left.add(c.name);
        assertEquals("[Kosten netto laut Rechnung, RE-4711 Energieversorger Nord]", left.toString());
        assertTrue(has(report, SetupReport.Kind.CHECK, "4 invoice(s) matching EN-[0-9]{6}-G(-GS[0-9]+)? are deleted; the folder and its 1 series are kept"));
        assertNull("the electricity folder held only old invoices", tree.child(tree.path(org, CostCases.MW_CONTRACTS + " / Energierechnungen"), "Electricity"));

        /* again: nothing to do, the kept objects are reported */
        SetupReport again = run(true);
        assertEquals(actions(again), 0, again.actions());
        assertTrue(has(again, SetupReport.Kind.SKIP, "24593 (Energy Invoices Directory 'Gas'): not deleted, OLD_INVOICES failed"));
    }

    @Test
    public void aDeletionWithAnotherObjectThanPlannedIsSkipped() throws Exception {
        Node renamed = tree.node(23950);
        tree.nodes.remove(23950L);
        renamed.parent.children.remove(renamed);
        tree.add(23950, "Strom - Hauptzähler 2027", "Base Data", renamed.parent);
        SetupReport report = run(false);
        assertTrue(has(report, SetupReport.Kind.WARNING, "23950 (Base Data 'Strom - Hauptzähler'): is named 'Strom - Hauptzähler 2027', the plan expects 'Strom - Hauptzähler'; not deleted"));
        assertTrue(has(report, SetupReport.Kind.CHECK, "23943 (Base Data Directory 'Rechnungswerte'): NO_CHILDREN failed"));
    }

    // ================================================================================================ units

    @Test
    public void unitsOfThePlanAreStoredLikeTheDemoStoresThem() throws Exception {
        Map<String, String[]> expected = new java.util.LinkedHashMap<>();
        expected.put("kWh", new String[]{"kW·h", "kWh"});
        expected.put("MWh", new String[]{"MW·h", "MWh"});
        expected.put("kW", new String[]{"kW", "kW"});
        expected.put("m³", new String[]{"㎥", "m³"});
        expected.put("%", new String[]{"%", "%"});
        Set<String> labels = new java.util.TreeSet<>();
        for (JsonNode o : plan.path("objects")) {
            o.path("attributes").forEach(a -> {
                if (a.has("unit") && !a.get("unit").asText().isEmpty()) labels.add(a.get("unit").asText());
            });
        }
        assertTrue(labels.toString(), labels.containsAll(java.util.Arrays.asList("kWh", "m³", "%", "ct/kWh", "EUR/kW/a", "EUR/a", "kW")));
        labels.addAll(expected.keySet());
        labels.add("kWh/m³");
        for (String label : labels) {
            String[] spec = expected.getOrDefault(label, new String[]{"one", label});
            assertArrayEquals(label, spec, CostSetup.unitFormula(label));
            /* what the web service client sends (JEVisAttributeWS.setInputUnit → JsonFactory.buildUnit) */
            JsonUnit json = JsonFactory.buildUnit(CostSetup.jevisUnit(label));
            assertEquals(label + " formula", spec[0], json.getFormula());
            assertEquals(label + " label", spec[1], json.getLabel());
            assertEquals(label + " prefix", "NONE", json.getPrefix());
        }

        /* the setup writes input and display unit alike */
        run(true);
        FakeTree.Att price = tree.node(CostCases.SUPPLIER_ELECTRICITY).att("Energy Price HT");
        assertEquals("ct/kWh", price.inputUnit.getLabel());
        assertEquals("one", price.inputUnit.getFormula());
        assertEquals("ct/kWh", price.displayUnit.getLabel());
        Node volume = tree.path(org, "Sun and Charge Demo / Standorte / Außenlager Süd / Daten / Gas / Gas - Außenlager Süd (Volumen)");
        assertEquals("m³", volume.att("Value").inputUnit.getLabel());
        assertEquals("㎥", volume.att("Value").displayUnit.getFormula());
    }

    // ================================================================================================ dashboard fix

    @Test
    public void dashboardFixKeepsEveryOtherKey() throws Exception {
        JsonNode fix = plan.path("fixes").get(0);
        JsonNode root = mapper.readTree(DASHBOARD);
        DashboardWidgetFix.Result result = DashboardWidgetFix.apply(root, fix, id -> id == 23723 ? "Clean Data" : "Image");
        assertNotNull(result.reason, result.updated);
        assertEquals(2, result.updated.get("widget").size());
        assertEquals(20, result.updated.get("widget").get(0).get("uuid").asInt());
        assertEquals(22, result.updated.get("widget").get(1).get("uuid").asInt());
        List<String> keys = new ArrayList<>();
        result.updated.fieldNames().forEachRemaining(keys::add);
        List<String> before = new ArrayList<>();
        root.fieldNames().forEachRemaining(before::add);
        assertEquals(before, keys);
        assertEquals(3, root.get("widget").size());
    }

    @Test
    public void dashboardFixSkipsAWidgetThatNoLongerMatches() throws Exception {
        JsonNode fix = plan.path("fixes").get(0);
        String retargeted = DASHBOARD.replace("\"objectID\":23723", "\"objectID\":23999");
        assertNull(DashboardWidgetFix.apply(mapper.readTree(retargeted), fix, id -> "Clean Data").updated);
        assertNull(DashboardWidgetFix.apply(mapper.readTree(DASHBOARD), fix, id -> "Image File").updated);
        String other = DASHBOARD.replace("{\"uuid\":21,\"WidgetType\":\"Image\"", "{\"uuid\":21,\"WidgetType\":\"Value Widget\"");
        DashboardWidgetFix.Result result = DashboardWidgetFix.apply(mapper.readTree(other), fix, id -> "Clean Data");
        assertNull(result.updated);
        assertTrue(result.reason, result.reason.contains("now of type 'Value Widget'"));

        /* in the setup: a skip line, the dashboard is not written */
        tree.node(24054).file("Data Model File", T0.plusDays(1), "Uebersicht2.json", retargeted);
        SetupReport report = run(true);
        assertFalse(problems(report), report.hasProblems());
        assertTrue(has(report, SetupReport.Kind.SKIP, "dashboard 24054 '01 - Übersicht', widget 21: widget 21 image.objectID is now '23999', not 23723"));
        assertFalse(tree.writes.stream().anyMatch(w -> w.startsWith("sample 24054 ")));
    }
}
