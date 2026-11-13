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
 * the objects to delete and the checks.
 */
public final class SetupPlan {

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
        backfill.put("command", "java -jar JEDemo/target/JEDemo-1.0-jar-with-dependencies.jar -c etc/jevis.xml -sm single "
                + "-jid <organisation id> -o invoicesFrom=2025-01 -o invoicesOnly=true   (first with -o dryrun=true)");

        ArrayNode delete = plan.putArray("delete");
        delete(delete, 23951, "Result Calculation Template", "Kostenrechnung - E-Mobilität", "replaced by template F", false);
        delete(delete, 23952, "Result Calculation Template", "stromrechnung", "replaced by template A (delete after the new templates exist: they are created next to it)", false);
        delete(delete, 23518, "Accounting Configuration", "Strom - Gesamt", "replaced by configuration A; also removes its linked outputs", false);
        delete(delete, 23517, "Electricity Grid Operator", "Netzbetreiber Nord", "empty duplicate of 23515", false);
        delete(delete, 23950, "Base Data", "Strom - Hauptzähler", "manual invoice values (folder Rechnungswerte), replaced by the invoice series", false);
        delete(delete, 23948, "Base Data", "Strom - Ladestrom", "manual invoice values (folder Rechnungswerte); only used as an option of the old templates", true);
        delete(delete, 23943, "Base Data Directory", "Rechnungswerte", "only if empty after 23950 and 23948", true);
        /* the linked outputs of 23518 point to 23705 (Data Directory "Sales & Marketing") and 24239 (an Alarm Directory):
         * wrongly mapped by the 2026-11-06 import, the objects themselves are in use — they go away with 23518, the
         * objects stay */
        ObjectNode oldInvoices = delete.addObject();
        oldInvoices.put("path", CostCases.MW_CONTRACTS + " / Energierechnungen / Strom");
        oldInvoices.put("also", "the folder \"Gas\" (24593) next to it");
        oldInvoices.put("reason", "invoices EN-yyyyMM-S/-G of the old fixed JEDemo invoices and their series; the cases use their own folders");
        oldInvoices.put("checkFirst", true);

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

    private static void delete(ArrayNode list, long id, String jevisClass, String name, String reason, boolean checkFirst) {
        ObjectNode d = list.addObject();
        d.put("id", id);
        d.put("class", jevisClass);
        d.put("name", name);
        d.put("reason", reason);
        if (checkFirst) d.put("checkFirst", true);
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
