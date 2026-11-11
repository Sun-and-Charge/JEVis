/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.web;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.constants.GUIConstants;
import org.jevis.commons.object.plugin.RangingValue;
import org.jevis.commons.object.plugin.RangingValues;
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonType;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.ResourceI18n;
import org.joda.time.LocalDate;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Contracts for the web app, view only (see docs/WEB-API.md, W4.7), like the tabs "Enter data" and "Configuration" of
 * the Control Center's "Accounting Plugin": suppliers, metering point operators, grid operators, contractors and
 * governmental dues with the latest values of their attributes, and the contract fields of the accounting
 * configurations.
 * <p>
 * Only objects the user may read and that are not in the recycle bin are used. File attributes are never given, nor
 * the calculation inputs/outputs of the accounting configurations.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebContracts {

    static final String CLASS_SUPPLIER = "Energy Supplier";
    static final String CLASS_METERING_OPERATOR = "Energy Metering Point Operator";
    static final String CLASS_GRID_OPERATOR = "Energy Grid Operator";
    static final String CLASS_CONTRACTOR = "Energy Contractor";
    static final String CLASS_DUES = "Governmental Dues";
    static final String CLASS_CONFIGURATION = "Accounting Configuration";
    static final String ATTRIBUTE_CONTRACTOR = "Contractor";
    static final String ATTRIBUTE_TEMPLATE_FILE = "Template File";
    static final String TYPE_NUMBER = "number";
    static final String TYPE_RANGING = "ranging";
    static final String TYPE_TEXT = "text";
    static final int DEFAULT_HISTORY = 100;
    static final int MAX_HISTORY = 500;
    /**
     * Group kinds and their base classes, in the order of the answer
     */
    static final Map<String, String> GROUPS;
    /**
     * Energy types of the Control Center's {@code ContractType}
     */
    static final Set<String> ENERGY_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "ELECTRICITY", "GAS", "COMMUNITY_HEATING")));

    private static final Logger logger = LogManager.getLogger(ResourceWebContracts.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ObjectMapper LENIENT = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);

    static {
        Map<String, String> groups = new LinkedHashMap<>();
        groups.put("suppliers", CLASS_SUPPLIER);
        groups.put("meteringOperators", CLASS_METERING_OPERATOR);
        groups.put("gridOperators", CLASS_GRID_OPERATOR);
        groups.put("contractors", CLASS_CONTRACTOR);
        groups.put("dues", CLASS_DUES);
        GROUPS = Collections.unmodifiableMap(groups);
    }

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("contracts")
    @Produces(MediaType.APPLICATION_JSON)
    public Response contracts(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            ResourceWebRegisters.Classes classes = ResourceWebRegisters.Classes.fromCache();
            ResourceWebRegisters.Translations translations = new ResourceWebRegisters.Translations(ResourceI18n.cached(),
                    ResourceWebRegisters.language(lang), classes);
            return json(contractsJson(lookup, classes, translations, sqlStore(ds)));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the contracts", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("contracts/objects/{id}/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response history(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                            @QueryParam("attribute") String attribute,
                            @QueryParam("limit") @DefaultValue("100") int limit) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Map<String, Object> result = historyJson(lookup, ResourceWebRegisters.Classes.fromCache(), sqlStore(ds), id,
                    attribute, limit);
            return result != null ? json(result) : notFound("Not found");
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the history of {} of {}", attribute, id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- data access

    /**
     * Attributes, samples and files of one request (also used for the energy invoices).
     */
    interface Store {
        /**
         * @return the attributes of the object by name, with latest value, display unit and sample count
         */
        Map<String, JsonAttribute> attributes(long id) throws Exception;

        /**
         * @return the latest samples of the attribute, newest first
         */
        List<JsonSample> latest(long id, String attribute, int limit) throws Exception;

        /**
         * @return the content of a file sample, null if the file does not exist or its stored name is not safe
         */
        byte[] file(long id, String attribute, JsonSample sample) throws Exception;
    }

    static Store sqlStore(SQLDataSource ds) {
        return new Store() {
            @Override
            public Map<String, JsonAttribute> attributes(long id) {
                Map<String, JsonAttribute> attributes = new HashMap<>();
                for (JsonAttribute attribute : ds.getAttributes(id)) {
                    if (attribute.getType() != null) attributes.put(attribute.getType(), attribute);
                }
                return attributes;
            }

            @Override
            public List<JsonSample> latest(long id, String attribute, int limit) throws Exception {
                return ds.getLatestSamples(id, attribute, limit);
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return ResourceWebAlarms.sampleFile(Config.getFileDir(), id, attribute, sample);
            }
        };
    }

    // ---------------------------------------------------------------- objects

    /**
     * @return the classes of all groups (with their subclasses)
     */
    static Set<String> groupClasses(ResourceWebRegisters.Classes classes) {
        Set<String> result = new HashSet<>();
        for (String base : GROUPS.values()) result.addAll(classes.withHeirs(base));
        return result;
    }

    /**
     * @return the object if the user may read it, it is not in the recycle bin and it belongs to one of the groups;
     * else null
     */
    static JsonObject contractObject(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        return groupClasses(classes).contains(object.getJevisClass()) ? object : null;
    }

    /**
     * @return {@code number} (Double/Long), {@code ranging} (GUI type "Ranging Value"), {@code text}; null for
     * attributes that are not given (files, passwords, the contractor)
     */
    static String attributeType(JsonType type) {
        if (type == null || type.getName() == null || ATTRIBUTE_CONTRACTOR.equals(type.getName())) return null;
        if (type.getPrimitiveType() == JEVisConstants.PrimitiveType.FILE) return null;
        if (ResourceWebRegisters.columnType(type) == null) return null;
        if (type.getPrimitiveType() == JEVisConstants.PrimitiveType.DOUBLE
                || type.getPrimitiveType() == JEVisConstants.PrimitiveType.LONG) {
            return TYPE_NUMBER;
        }
        if (GUIConstants.RANGING_VALUE.getId().equals(type.getGuiType())) return TYPE_RANGING;
        return TYPE_TEXT;
    }

    /**
     * @return the given types of the class by GUI position (as the Control Center's "Enter data"), each name once
     */
    static List<JsonType> attributeTypes(String className, ResourceWebRegisters.Classes classes) {
        Map<String, JsonType> byName = new LinkedHashMap<>();
        for (JsonType type : classes.types(className)) {
            if (attributeType(type) != null) byName.putIfAbsent(type.getName(), type);
        }
        List<JsonType> sorted = new ArrayList<>(byName.values());
        sorted.sort(Comparator.comparingInt(JsonType::getGUIPosition));
        return sorted;
    }

    /**
     * @return the value of a sample: number (or the text if it is no number), list of {@code {from, to, value}} or
     * text; null without sample
     */
    static Object value(String type, JsonSample sample) {
        if (sample == null || sample.getValue() == null) return null;
        switch (type) {
            case TYPE_NUMBER:
                return sample.getValue().trim().isEmpty() ? null : ResourceWebRegisters.number(sample.getValue());
            case TYPE_RANGING:
                return ranging(sample.getValue());
            default:
                return sample.getValue();
        }
    }

    /**
     * Parses a "Ranging Value" ({@code {"rangingValues": [{"from", "to", "value"}]}}, as {@code RangingValueEditor}).
     *
     * @return {@code [{from, to, value}]}; empty if empty or not parseable
     */
    static List<Map<String, Object>> ranging(String json) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return list;
        RangingValues values = new RangingValues();
        try {
            LENIENT.readerForUpdating(values).readValue(json);
        } catch (Exception e) {
            logger.debug("Could not parse the ranging value {}", json, e);
            return list;
        }
        for (RangingValue value : values.getRangingValues()) {
            if (value == null) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("from", finite(value.getFrom()));
            entry.put("to", finite(value.getTo()));
            entry.put("value", finite(value.getValue()));
            list.add(entry);
        }
        return list;
    }

    private static Double finite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? null : value;
    }

    /**
     * {@code GET /contracts}: the groups with their objects and the accounting configurations.
     */
    static Map<String, Object> contractsJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                             ResourceWebRegisters.Translations translations, Store store) {
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Map<String, Object>> groups = new ArrayList<>();
        Set<Long> done = new HashSet<>();
        for (Map.Entry<String, String> group : GROUPS.entrySet()) {
            List<Map<String, Object>> objects = new ArrayList<>();
            for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(group.getValue()))) {
                if (!done.add(object.getId())) continue;
                objects.add(objectJson(object, lookup, classes, translations, store, buildingClasses));
            }
            if (objects.isEmpty()) continue;
            objects.sort(ResourceWebReports.order());
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("kind", group.getKey());
            json.put("objects", objects);
            groups.add(json);
        }
        List<Map<String, Object>> configurations = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_CONFIGURATION))) {
            configurations.add(configurationJson(object, lookup, store, buildingClasses));
        }
        configurations.sort(ResourceWebReports.order());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groups", groups);
        result.put("configurations", configurations);
        return result;
    }

    static Map<String, Object> objectJson(JsonObject object, ResourceWebModules.Lookup lookup,
                                          ResourceWebRegisters.Classes classes,
                                          ResourceWebRegisters.Translations translations, Store store,
                                          Set<String> buildingClasses) {
        Map<String, JsonAttribute> attributes = attributes(object.getId(), store);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("className", object.getJevisClass());
        json.put("classLabel", translations.className(object.getJevisClass()));
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
        json.put("contractor", contractorJson(latestValue(attributes.get(ATTRIBUTE_CONTRACTOR)), lookup));
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonType type : attributeTypes(object.getJevisClass(), classes)) {
            String kind = attributeType(type);
            JsonAttribute attribute = attributes.get(type.getName());
            JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("name", type.getName());
            entry.put("label", translations.typeName(object.getJevisClass(), type.getName()));
            entry.put("type", kind);
            entry.put("value", value(kind, sample));
            entry.put("unit", TYPE_TEXT.equals(kind) ? null : ResourceWebRegisters.unitLabel(attribute));
            entry.put("timestamp", sample != null ? isoTimestamp(sample.getTs()) : null);
            entry.put("history", attribute != null && attribute.getSampleCount() > 1);
            list.add(entry);
        }
        json.put("attributes", list);
        return json;
    }

    /**
     * @param target latest value of "Contractor" ({@code id} or {@code id:attribute}, as {@code TargetHelper})
     * @return {@code id, name, readable} (name only if readable), null if empty or no target
     */
    static Map<String, Object> contractorJson(String target, ResourceWebModules.Lookup lookup) {
        Long[] id = new Long[1];
        String[] attribute = new String[1];
        if (!ResourceWebRegisters.parseTarget(target, id, attribute)) return null;
        JsonObject contractor = lookup.readable(id[0]);
        if (contractor != null && ResourceWebIso.isDeleted(contractor)) contractor = null;
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id[0]);
        json.put("name", contractor != null ? contractor.getName() : null);
        json.put("readable", contractor != null);
        return json;
    }

    // ---------------------------------------------------------------- configurations

    /**
     * Contract fields of an accounting configuration from the JSON of its "Template File" ({@code SelectionTemplate},
     * written by {@code AccountingTemplateHandler}); never the calculation inputs/outputs.
     */
    static Map<String, Object> configurationJson(JsonObject object, ResourceWebModules.Lookup lookup, Store store,
                                                 Set<String> buildingClasses) {
        JsonNode template = template(object.getId(), store);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("name", object.getName());
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
        String contractType = text(template, "contractType");
        json.put("contractNumber", text(template, "contractNumber"));
        json.put("contractType", contractType);
        json.put("marketLocation", text(template, "marketLocationNumber"));
        json.put("contractDate", day(text(template, "contractDate")));
        json.put("firstRate", day(text(template, "firstRate")));
        json.put("periodOfNotice", day(text(template, "periodOfNotice")));
        json.put("start", day(text(template, "contractStart")));
        json.put("end", day(text(template, "contractEnd")));
        json.put("energyType", contractType != null && ENERGY_TYPES.contains(contractType) ? contractType : null);
        return json;
    }

    /**
     * @return the JSON of the latest "Template File" sample, null if none or not parseable
     */
    static JsonNode template(long id, Store store) {
        try {
            JsonAttribute attribute = attributes(id, store).get(ATTRIBUTE_TEMPLATE_FILE);
            JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
            byte[] bytes = sample != null ? store.file(id, ATTRIBUTE_TEMPLATE_FILE, sample) : null;
            if (bytes == null || bytes.length == 0) return null;
            JsonNode node = MAPPER.readTree(bytes);
            return node != null && node.isObject() ? node : null;
        } catch (Exception e) {
            logger.debug("Could not read the template file of {}", id, e);
            return null;
        }
    }

    /**
     * @return the text of the field, null if missing, null or empty
     */
    static String text(JsonNode node, String field) {
        JsonNode value = node != null ? node.get(field) : null;
        if (value == null || value.isNull() || value.isContainerNode()) return null;
        String text = value.asText();
        return text == null || text.trim().isEmpty() ? null : text;
    }

    /**
     * @return the date of a stored date time ({@code yyyy-MM-dd}, in the stored offset); other texts unchanged
     */
    static String day(String value) {
        if (value == null) return null;
        LocalDate date = ResourceWebRegisters.parseDate(value);
        return date != null ? date.toString() : value;
    }

    // ---------------------------------------------------------------- history

    static int historyLimit(int limit) {
        if (limit <= 0) return DEFAULT_HISTORY;
        return Math.min(limit, MAX_HISTORY);
    }

    /**
     * {@code GET /contracts/objects/{id}/history}: the latest samples of a number or ranging attribute, newest first.
     *
     * @return null if the object is not readable / of no group class or the attribute is unknown or not number/ranging
     */
    static Map<String, Object> historyJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                           Store store, long id, String attributeName, int limit) throws Exception {
        JsonObject object = contractObject(lookup, classes, id);
        if (object == null || attributeName == null) return null;
        JsonType type = null;
        for (JsonType candidate : attributeTypes(object.getJevisClass(), classes)) {
            if (attributeName.equals(candidate.getName())) type = candidate;
        }
        String kind = attributeType(type);
        if (!TYPE_NUMBER.equals(kind) && !TYPE_RANGING.equals(kind)) return null;
        List<Map<String, Object>> samples = new ArrayList<>();
        for (JsonSample sample : store.latest(id, type.getName(), historyLimit(limit))) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("timestamp", isoTimestamp(sample.getTs()));
            json.put("value", value(kind, sample));
            samples.add(json);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", ResourceWebRegisters.unitLabel(attributes(id, store).get(type.getName())));
        result.put("type", kind);
        result.put("samples", samples);
        return result;
    }

    // ---------------------------------------------------------------- navigation

    /**
     * @param readable the objects the user may read
     * @return {@code contracts}: number of readable objects of the groups plus accounting configurations;
     * {@code invoices}: number of readable energy invoices (not in the recycle bin)
     */
    static Map<String, Object> moduleCounts(Collection<JsonObject> readable, ResourceWebRegisters.Classes classes) {
        Set<String> contractClasses = groupClasses(classes);
        contractClasses.addAll(classes.withHeirs(CLASS_CONFIGURATION));
        Set<String> invoiceClasses = classes.withHeirs(ResourceWebInvoices.CLASS_INVOICE);
        int contracts = 0;
        int invoices = 0;
        for (JsonObject object : readable) {
            if (ResourceWebIso.isDeleted(object)) continue;
            if (contractClasses.contains(object.getJevisClass())) {
                contracts++;
            } else if (invoiceClasses.contains(object.getJevisClass())) {
                invoices++;
            }
        }
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("contracts", contracts);
        modules.put("invoices", invoices);
        return modules;
    }

    // ---------------------------------------------------------------- helpers

    /**
     * @return the attributes of the object, empty if they cannot be read
     */
    static Map<String, JsonAttribute> attributes(long id, Store store) {
        try {
            Map<String, JsonAttribute> attributes = store.attributes(id);
            return attributes != null ? attributes : Collections.emptyMap();
        } catch (Exception e) {
            logger.warn("Could not read the attributes of {}", id, e);
            return Collections.emptyMap();
        }
    }

    static String latestValue(JsonAttribute attribute) {
        JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
        return sample != null ? sample.getValue() : null;
    }

    static String isoTimestamp(String ts) {
        if (ts == null) return null;
        try {
            return ISODateTimeFormat.dateTime().print(JsonFactory.sampleDTF.withOffsetParsed().parseDateTime(ts));
        } catch (Exception e) {
            return ts;
        }
    }

    private static Response notFound(String message) {
        return Response.status(Response.Status.NOT_FOUND).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
    }

    private static Response json(Object value) throws Exception {
        return Response.ok(MAPPER.writeValueAsString(value)).type(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store").build();
    }
}
