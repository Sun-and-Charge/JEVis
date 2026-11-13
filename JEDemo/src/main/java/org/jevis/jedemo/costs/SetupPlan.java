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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jevis.jedemo.InvoiceCase;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * The setup plan of the demo cost cases (setup-plan.json): what step 3 creates, changes and deletes on the live
 * server, with symbolic keys ({@code "@key"}) for the objects that do not exist yet. Read top to bottom: objects
 * (parents first), templates, configurations, the "Invoice Cases" of the JEDemo service object, the invoice backfill,
 * the objects to delete (each with the checks that must pass first), the fixes of existing objects and the checks to do
 * by hand. {@link CostSetup} carries it out.
 */
public final class SetupPlan {

    /**
     * delete check: the objects of these plan keys exist (the replacements were created)
     */
    public static final String CHECK_AFTER_CREATED = "AFTER_CREATED";
    /**
     * delete check: the object has no children (left after the deletions before it)
     */
    public static final String CHECK_NO_CHILDREN = "NO_CHILDREN";
    /**
     * delete check: no template, configuration or dashboard of the organisation (except those deleted before) names
     * the id in its file
     */
    public static final String CHECK_NOT_REFERENCED = "NOT_REFERENCED";
    /**
     * delete check of an invoice folder: only invoices whose number matches the pattern are deleted; the folder (with
     * its series) only if nothing else is in it
     */
    public static final String CHECK_OLD_INVOICES = "OLD_INVOICES";
    public static final String FIX_REMOVE_DASHBOARD_WIDGET = "REMOVE_DASHBOARD_WIDGET";
    /**
     * fix: delete the values of clean data rows of the plan whose settings did not hold from the start of their history
     * (the JEDataProcessor computes them again); with "force" always
     */
    public static final String FIX_CLEAN_REPROCESS = "CLEAN_REPROCESS";
    /**
     * fix: replace the whole "Value" history of existing raw rows by a profile of {@link History} (seeded by the row
     * id), make the settings of their clean data rows fit and delete the values of the rows computed from them
     */
    public static final String FIX_REPLACE_HISTORY = "REPLACE_HISTORY";
    /**
     * fix: invoices of the plan's invoice folders whose number matches the pattern and that lack a line (e.g. a levy
     * added later) go to the recycle bin and their amounts are taken out of the folder's series; the invoice backfill
     * of the JEDemo job then issues them again with the line
     */
    public static final String FIX_REISSUE_INVOICES = "REISSUE_INVOICES";

    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    private SetupPlan() {
    }

    public static ObjectNode build() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode plan = F.objectNode();
        plan.put("version", 1);
        plan.put("title", "Demo cost cases (Kostenrechnung) of the demo organisation");
        plan.put("organisation", CostCases.ORGANISATION);
        plan.put("timeZone", History.ZONE.getId());
        plan.put("generatedBy", CostDemoGenerator.class.getName() + " (do not edit by hand, see docs/DEMO-COSTS.md)");

        ObjectNode refs = plan.putObject("conventions");
        refs.put("keys", "\"@key\" is an object of this plan that step 3 creates; replace it by the new id everywhere "
                + "(attribute values, configurations' objectKey/templateKey, the invoice cases). Numbers are existing ids.");
        refs.put("parent", "path = object names from the organisation separated by \" / \"; key = object of this plan; "
                + "id = existing object; parentOf = the parent of that existing object");
        refs.put("attributes", "unit = input/display unit of the attribute; value = one sample written at setup time; "
                + "samples = samples with these time stamps (Europe/Berlin)");
        refs.put("history", "profile of org.jevis.jedemo.costs.History, written as samples of \"Value\" from 'from' to "
                + "'until' (\"setup\" = the time of step 3); afterwards the JEDemo job continues the rows");
        refs.put("delete", "objects go to the recycle bin, in this order and only after everything above was written; "
                + "with checkFirst every check must pass: AFTER_CREATED (the objects of the keys exist), NO_CHILDREN, "
                + "NOT_REFERENCED (no template, configuration or dashboard file of the organisation names the id), "
                + "OLD_INVOICES (only invoices whose number matches numberPattern; the folder only if nothing else is in it)");
        refs.put("fixes", "changes of existing objects, skipped if the object no longer looks as described; REPLACE_HISTORY: "
                + "all values of each raw row are deleted and the profile (seeded by the row id) is written from 'from' to 'until', "
                + "unless the row already holds the profile at every quarter hour of the 'verify' windows; then the settings "
                + "of the clean rows are checked and the values of the clean rows and of 'derived' are deleted (computed again)");
        refs.put("settings", "attribute 'value' entries of data rows (Data, Clean Data) are written at the configuration time "
                + "stamp 1989-12-31T23:00:00Z like the Control Center does, so they hold for the whole history; other objects at setup time");
        refs.put("setup", "carried out by JEDemo: -sm single -jid <organisation id> -o costSetup=dryrun|apply (docs/DEMO-COSTS.md)");

        ArrayNode objects = plan.putArray("objects");
        for (PlanObject o : CostCases.objects()) objects.add(object(o));

        ArrayNode templates = plan.putArray("templates");
        ArrayNode configurations = plan.putArray("configurations");
        for (CostCase c : CostCases.cases()) {
            ObjectNode t = templates.addObject();
            t.put("key", TemplateFactory.templateKey(c));
            t.put("class", "Result Calculation Template");
            t.put("name", c.templateName);
            t.putObject("parent").put("parentOf", CostCases.OLD_TEMPLATE);
            t.put("templateFile", "templates/" + c.file + ".json");
            ObjectNode k = configurations.addObject();
            k.put("key", TemplateFactory.configurationKey(c));
            k.put("class", "Accounting Configuration");
            k.put("name", c.configurationName);
            if (CostCases.SITE_S.equals(c.site)) {
                k.putObject("parent").put("key", "S.configs");
            } else {
                k.putObject("parent").put("path", CostCases.MW_CONTRACTS + " / Verträge");
            }
            k.put("templateFile", "configurations/" + c.file + ".json");
            k.put("template", "@" + TemplateFactory.templateKey(c));
        }

        ObjectNode service = plan.putObject("service");
        service.put("class", "JEDemo");
        service.put("note", "Write \"Invoice Cases\" (keys resolved) to the JEDemo service object. Once it is set, the old "
                + "attributes Invoice Directory / Electricity Row / Gas Row / prices are ignored; they may be cleared.");
        ArrayNode cases = service.putObject("attributes").putArray("Invoice Cases");
        for (InvoiceCase ic : CostCases.invoiceCases()) cases.add(mapper.<JsonNode>valueToTree(ic));

        ObjectNode backfill = plan.putObject("invoiceBackfill");
        backfill.put("from", "2025-01");
        backfill.put("when", "after the JEDataProcessor has processed the new clean data rows (B.clean, D.clean, E.clean, C.hourly) "
                + "for the whole history");
        backfill.put("command", "java -Dlog4j.configurationFile=etc/log4j2_service.xml -DserviceLogFile=var/log/jedemo-single.log -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single "
                + "-jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true   (first with -o dryrun=true)");

        ArrayNode delete = plan.putArray("delete");
        ArrayNode allTemplates = F.arrayNode();
        for (CostCase c : CostCases.cases()) allTemplates.add("@" + TemplateFactory.templateKey(c));
        delete(delete, 23951, "Result Calculation Template", "Kostenrechnung - E-Mobilität", "replaced by template F",
                check(CHECK_AFTER_CREATED).set("keys", F.arrayNode().add("@" + TemplateFactory.templateKey(CostCases.caseF()))));
        delete(delete, 23952, "Result Calculation Template", "stromrechnung", "replaced by template A (delete after the new templates exist: they are created next to it)",
                check(CHECK_AFTER_CREATED).set("keys", allTemplates));
        delete(delete, 23518, "Accounting Configuration", "Strom - Gesamt", "replaced by configuration A; also removes its linked outputs",
                check(CHECK_AFTER_CREATED).set("keys", F.arrayNode().add("@" + TemplateFactory.configurationKey(CostCases.caseA()))));
        delete(delete, 23517, "Electricity Grid Operator", "Netzbetreiber Nord", "empty duplicate of 23515",
                check(CHECK_NO_CHILDREN), check(CHECK_NOT_REFERENCED));
        delete(delete, 23950, "Base Data", "Strom - Hauptzähler", "manual invoice values (folder Rechnungswerte), replaced by the invoice series",
                check(CHECK_NOT_REFERENCED));
        delete(delete, 23948, "Base Data", "Strom - Ladestrom", "manual invoice values (folder Rechnungswerte); only used as an option of the old templates",
                check(CHECK_NOT_REFERENCED));
        delete(delete, 23943, "Base Data Directory", "Rechnungswerte", "only if empty after 23950 and 23948",
                check(CHECK_NO_CHILDREN));
        /* the linked outputs of 23518 point to 23705 (Data Directory "Sales & Marketing") and 24239 (an Alarm Directory):
         * wrongly mapped by the 2026-11-06 import, the objects themselves are in use — they go away with 23518, the
         * objects stay */
        String oldInvoices = "invoices EN-yyyyMM-S/-G of the old fixed JEDemo invoices; the folder with its series only if "
                + "nothing else is in it; the cases use their own folders";
        /* both old folders by id (the path lookup did not find "Strom" on live, 2026-11-13) */
        /* object name "Electricity" (the invoice import names the folder by medium; "Strom" is its German local name) */
        delete(delete, 24598, "Energy Invoices Directory", "Electricity", oldInvoices, oldInvoicesCheck("S"));
        delete(delete, 24593, "Energy Invoices Directory", "Gas", oldInvoices, oldInvoicesCheck("G"));

        /* declarative fixes of existing objects, each skipped (with a report line) if the object no longer matches */
        ArrayNode fixes = plan.putArray("fixes");
        ObjectNode widget = fixes.addObject();
        widget.put("type", FIX_REMOVE_DASHBOARD_WIDGET);
        widget.put("dashboard", 24054);
        widget.put("class", "Dashboard Analysis");
        widget.put("name", "01 - Übersicht");
        widget.put("attribute", "Data Model File");
        widget.put("widgetUuid", 21);
        widget.put("widgetType", "Image");
        widget.put("widgetTitle", "Neues Bild");
        widget.put("targetField", "image.objectID");
        widget.put("target", 23723);
        widget.put("targetClass", "Clean Data");
        widget.put("reason", "image widget whose image points to a Clean Data row (23723) instead of an image; it shows nothing");
        ObjectNode reprocess = fixes.addObject();
        reprocess.put("type", FIX_CLEAN_REPROCESS);
        ArrayNode rows = reprocess.putArray("rows");
        for (PlanObject o : CostCases.objects()) {
            if (o.id == null && "Clean Data".equals(o.jevisClass)) rows.add("@" + o.key);
        }
        reprocess.put("force", false);
        reprocess.put("reason", "the settings of the new rows were first written at the time of the setup (2026-11-13), so the "
                + "JEDataProcessor computed the history without them (counter readings instead of consumption); the settings are "
                + "now written at the configuration time stamp and the values computed before are deleted to be computed again");

        fixes.add(chargePoints());

        ObjectNode reissue = fixes.addObject();
        reissue.put("type", FIX_REISSUE_INVOICES);
        reissue.putArray("folders").add("@C.invoices").add("@D.invoices");
        reissue.put("numberPattern", "EN-2025(0[1-9]|1[0-2])-(GR|GS)");
        reissue.put("missingLine", "Gasspeicherumlage");
        reissue.put("reason", "the gas invoices of 2025 were issued without the gas storage levy (2.99 EUR/MWh from 1/2025, "
                + "2.89 EUR/MWh from 7/2025, abolished from 2026); they are issued again with it");

        ArrayNode checks = plan.putArray("checks");
        checks.add("23946 (Gas - Brennwert) has a sample at or before 2025-01-01, otherwise add one (11.4 kWh/m³ at 2025-01-01).");
        checks.add("Units of the charge point rows 23925, 23927, 23923 (kWh per interval) and that each is the clean data of Ladepunkt 1, 2, 3.");
        checks.add("C.hourly: after copying the settings of 23686 the period is PT1H and the JEDataProcessor fills hourly sums of 23572.");
        checks.add("Monthly counters B and D: the clean value of a month is stored at the 1st of the month or of the next month, "
                + "depending on the processor; templates and invoices read the same row, so the costs agree either way. If invoices "
                + "wait one month too long, set the invoice case's consumption.completeRow to the raw row (B.row / D.row).");
        checks.add("Action \"MW 018 - Neuer Fernwärmevertrag\" (story of case E): refer to the new contract E.supplier.");
        checks.add("After the backfill: open each configuration for a month of 2026 and check Differenz Netto ≈ 0 (cents) except the "
                + "deviation months listed in the invoice cases.");
        return plan;
    }

    /**
     * Fix of case F: the live charge point rows hold unusable values (tiny or negative monthly sums, zeros); they get
     * a synthetic company car charging profile, their clean rows and the sum of the calculation are computed again.
     */
    static ObjectNode chargePoints() {
        ObjectNode fix = F.objectNode();
        fix.put("type", FIX_REPLACE_HISTORY);
        fix.put("profile", History.CHARGING_POINT);
        fix.put("seed", "row id");
        fix.put("from", ts(CostCases.CHARGE_FROM));
        fix.put("until", "setup");
        ArrayNode verify = fix.putArray("verify");
        window(verify, ZonedDateTime.of(2025, 11, 3, 0, 0, 0, 0, History.ZONE));
        window(verify, ZonedDateTime.of(2026, 10, 5, 0, 0, 0, 0, History.ZONE));
        ObjectNode rowSettings = fix.putObject("rowSettings");
        rowSettings.put("Period", "PT15M");
        ObjectNode cleanSettings = fix.putObject("cleanSettings");
        cleanSettings.put("Enabled", true);
        cleanSettings.put("Conversion to Differential", false);
        cleanSettings.put("Value is a Quantity", true);
        cleanSettings.put("Period", "PT15M");
        ArrayNode rows = fix.putArray("rows");
        long[][] points = {{CostCases.CHARGE_POINT_1, CostCases.CLEAN_CHARGE_POINT_1}, {CostCases.CHARGE_POINT_2, CostCases.CLEAN_CHARGE_POINT_2},
                {CostCases.CHARGE_POINT_3, CostCases.CLEAN_CHARGE_POINT_3}};
        for (int i = 0; i < points.length; i++) {
            ObjectNode row = rows.addObject();
            row.put("id", points[i][0]);
            row.put("class", "Data");
            row.put("name", CostCases.CHARGE_POINT_NAME + (i + 1));
            row.put("parent", CostCases.CHARGE_POINT_PARENT);
            row.put("clean", points[i][1]);
        }
        ArrayNode derived = fix.putArray("derived");
        ObjectNode sum = derived.addObject();
        sum.put("id", CostCases.CHARGE_SUM);
        sum.put("class", "Data");
        sum.put("name", CostCases.CHARGE_SUM_NAME);
        sum.put("parent", CostCases.CHARGE_POINT_PARENT);
        sum.put("reason", "output of the calculation that sums the charge points; JECalc computes it again");
        ObjectNode sumClean = derived.addObject();
        sumClean.put("id", CostCases.CHARGE_SUM_CLEAN);
        sumClean.put("parent", CostCases.CHARGE_SUM);
        sumClean.put("reason", "below the sum (its clean data): the JEDataProcessor computes it again; as a Clean Data it "
                + "gets the clean settings");
        fix.put("reason", "the charge point rows of case F hold unusable values (monthly sums of a few kWh, a negative month, "
                + "zeros since 2026-05); JEDemo replays each raw row from its own previous year (P52W), so the replaced history "
                + "is continued afterwards");
        return fix;
    }

    private static void window(ArrayNode list, ZonedDateTime from) {
        ObjectNode w = list.addObject();
        w.put("from", ts(from));
        w.put("until", ts(from.plusWeeks(2)));
    }

    private static void delete(ArrayNode list, long id, String jevisClass, String name, String reason, ObjectNode... checks) {
        ObjectNode d = list.addObject();
        d.put("id", id);
        d.put("class", jevisClass);
        d.put("name", name);
        d.put("reason", reason);
        if (checks.length > 0) {
            d.put("checkFirst", true);
            ArrayNode array = d.putArray("checks");
            for (ObjectNode check : checks) array.add(check);
        }
    }

    private static ObjectNode check(String type) {
        ObjectNode check = F.objectNode();
        check.put("type", type);
        return check;
    }

    private static ObjectNode oldInvoicesCheck(String suffix) {
        ObjectNode check = check(CHECK_OLD_INVOICES);
        /* the invoices and the credit notes of the old fixed JEDemo invoices (e.g. EN-202602-G-GS1) */
        check.put("numberPattern", "EN-[0-9]{6}-" + suffix + "(-GS[0-9]+)?");
        return check;
    }

    static ObjectNode object(PlanObject o) {
        ObjectNode node = F.objectNode();
        node.put("key", o.key);
        node.put("action", o.id != null ? "update" : "create");
        if (o.id != null) node.put("id", o.id);
        node.put("class", o.jevisClass);
        node.put("name", o.name);
        if (o.parent != null) {
            ObjectNode parent = node.putObject("parent");
            if (o.parent.value instanceof Long) parent.put(o.parent.kind, (Long) o.parent.value);
            else parent.put(o.parent.kind, String.valueOf(o.parent.value));
        }
        if (o.copyAttributesFrom != null) node.put("copyAttributesFrom", o.copyAttributesFrom);
        if (!o.attributes.isEmpty()) {
            ObjectNode attributes = node.putObject("attributes");
            for (Map.Entry<String, PlanObject.Attr> e : o.attributes.entrySet()) {
                ObjectNode a = attributes.putObject(e.getKey());
                PlanObject.Attr attr = e.getValue();
                if (attr.unit != null) a.put("unit", attr.unit);
                if (attr.value != null) a.set("value", value(attr.value));
                if (!attr.samples.isEmpty()) {
                    ArrayNode samples = a.putArray("samples");
                    for (PlanObject.Sample s : attr.samples) {
                        ObjectNode sample = samples.addObject();
                        sample.put("ts", ts(s.ts));
                        sample.set("value", value(s.value));
                    }
                }
            }
        }
        if (o.history != null) {
            ObjectNode history = node.putObject("history");
            history.put("profile", o.history);
            boolean index = History.GAS_PRICE_INDEX.equals(o.history) || History.HEAT_PRICE_INDEX.equals(o.history);
            history.put("from", ts(index ? CostCases.year(2024) : History.FROM.atStartOfDay(History.ZONE)));
            history.put("until", index ? ts(CostCases.year(CostCases.LAST_YEAR).withMonth(12)) : "setup");
        }
        if (o.note != null) node.put("note", o.note);
        return node;
    }

    /**
     * Replaces the symbolic keys of a JSON tree (text values {@code "@key"}) by the ids of the created objects, as step
     * 3 does for the invoice cases and object targets. In configurations an {@code objectKey} / {@code templateKey}
     * sets {@code objectID} / {@code templateSelection} and is removed.
     *
     * @param ids key (without "@") → id; a key without id is left as it is
     * @return a resolved copy
     */
    public static JsonNode resolveKeys(JsonNode node, java.util.function.Function<String, Long> ids) {
        if (node == null) return null;
        if (node.isTextual()) {
            String text = node.asText();
            if (text.startsWith("@") && text.length() > 1) {
                Long id = ids.apply(text.substring(1));
                if (id != null) return F.numberNode(id);
            }
            return node;
        }
        if (node.isArray()) {
            ArrayNode copy = F.arrayNode();
            for (JsonNode child : node) copy.add(resolveKeys(child, ids));
            return copy;
        }
        if (node.isObject()) {
            ObjectNode copy = F.objectNode();
            node.fields().forEachRemaining(e -> copy.set(e.getKey(), resolveKeys(e.getValue(), ids)));
            JsonNode objectKey = copy.get("objectKey");
            if (objectKey != null && objectKey.isNumber()) {
                copy.set("objectID", objectKey);
                copy.remove("objectKey");
            }
            JsonNode templateKey = copy.get("templateKey");
            if (templateKey != null && templateKey.isNumber()) {
                copy.set("templateSelection", templateKey);
                copy.remove("templateKey");
            }
            return copy;
        }
        return node;
    }

    static String ts(ZonedDateTime ts) {
        return TS.format(ts);
    }

    private static JsonNode value(Object value) {
        if (value instanceof Boolean) return F.booleanNode((Boolean) value);
        if (value instanceof Double) return F.numberNode((Double) value);
        if (value instanceof Integer) return F.numberNode((Integer) value);
        if (value instanceof Long) return F.numberNode((Long) value);
        return F.textNode(String.valueOf(value));
    }
}
