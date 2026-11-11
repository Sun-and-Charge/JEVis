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
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.Config;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.Period;
import org.joda.time.format.ISODateTimeFormat;

import javax.security.sasl.AuthenticationException;
import java.util.*;

/**
 * Energy invoices for the web app, view only (see docs/WEB-API.md, W4.7, and docs/ENERGY-INVOICES.md): the "Energy
 * Invoice" objects written by the invoice import with the latest values of their attributes, and the original file.
 * <p>
 * Only invoices the user may read and that are not in the recycle bin are used. Of the files only "Invoice File" is
 * given.
 */
@Path("/JEWebService/v1/web")
public class ResourceWebInvoices {

    static final String CLASS_INVOICE = "Energy Invoice";
    static final String ATTRIBUTE_FILE = "Invoice File";
    static final String ATTRIBUTE_FORMAT = "Format";
    static final String ATTRIBUTE_NUMBER = "Invoice Number";
    static final String ATTRIBUTE_DATE = "Invoice Date";
    static final String ATTRIBUTE_SELLER = "Seller";
    static final String ATTRIBUTE_SELLER_VAT_ID = "Seller VAT ID";
    static final String ATTRIBUTE_BUYER = "Buyer";
    static final String ATTRIBUTE_PERIOD_START = "Period Start";
    static final String ATTRIBUTE_PERIOD_END = "Period End";
    static final String ATTRIBUTE_MEDIUM = "Medium";
    static final String ATTRIBUTE_METER_POINT = "Meter Point";
    static final String ATTRIBUTE_CONSUMPTION = "Consumption";
    static final String ATTRIBUTE_NET = "Net Amount";
    static final String ATTRIBUTE_TAX = "Tax Amount";
    static final String ATTRIBUTE_GROSS = "Gross Amount";
    static final String ATTRIBUTE_CURRENCY = "Currency";
    static final String ATTRIBUTE_LINE_ITEMS = "Line Items";
    static final String TYPE_PDF = "application/pdf";
    static final String TYPE_XML = "application/xml";
    static final int MAX_INVOICES = 2000;
    static final int DEFAULT_MONTHS = 24;
    static final int MAX_YEARS = 10;

    private static final Logger logger = LogManager.getLogger(ResourceWebInvoices.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("invoices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response invoices(@Context HttpHeaders httpHeaders, @QueryParam("from") String from,
                             @QueryParam("to") String to, @QueryParam("timeZone") String timeZone) {
        DateTimeZone zone = ResourceWebNotes.zone(timeZone);
        LocalDate[] range;
        try {
            range = dayRange(from, to, zone, LocalDate.now(zone));
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return json(invoicesJson(lookup, ResourceWebRegisters.Classes.fromCache(),
                    ResourceWebContracts.sqlStore(ds), range[0], range[1]));
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load the invoices", ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("invoices/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response invoice(@Context HttpHeaders httpHeaders, @PathParam("id") long id) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            Map<String, Object> result = invoiceDetailJson(lookup, ResourceWebRegisters.Classes.fromCache(),
                    ResourceWebContracts.sqlStore(ds), id);
            return result != null ? json(result) : notFound("Invoice not found");
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not load invoice {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    @GET
    @Path("invoices/{id}/file")
    public Response file(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                         @QueryParam("inline") @DefaultValue("false") boolean inline) {
        SQLDataSource ds = null;
        try {
            ds = new SQLDataSource(httpHeaders::getRequestHeader);
            ResourceWebModules.Lookup lookup = ResourceWebModules.lookup(ds);
            return fileResponse(lookup, ResourceWebRegisters.Classes.fromCache(), ResourceWebContracts.sqlStore(ds),
                    id, inline);
        } catch (AuthenticationException ex) {
            return Response.status(Response.Status.UNAUTHORIZED).entity(ex.getMessage()).build();
        } catch (Exception ex) {
            logger.error("Could not read the file of invoice {}", id, ex);
            return Response.serverError().build();
        } finally {
            Config.CloseDS(ds);
        }
    }

    // ---------------------------------------------------------------- parameters

    /**
     * @param from  first day ({@code yyyy-MM-dd}), default: the first day of the month 23 months before {@code to}'s
     *              month (24 months up to {@code to})
     * @param to    last day (inclusive), default: the last day of the current month
     * @param today today in the time zone
     * @return first and last day
     * @throws IllegalArgumentException for invalid days, {@code to} before {@code from} or more than ten years
     */
    static LocalDate[] dayRange(String from, String to, DateTimeZone zone, LocalDate today) {
        LocalDate toDay;
        if (to != null && !to.trim().isEmpty()) {
            try {
                toDay = ISODateTimeFormat.date().parseLocalDate(to.trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("Invalid to");
            }
        } else {
            toDay = today.dayOfMonth().withMaximumValue();
        }
        String fromText = from != null && !from.trim().isEmpty() ? from
                : toDay.plusDays(1).minusMonths(DEFAULT_MONTHS).toString();
        DateTime[] range = ResourceWebNotes.dayRange(fromText, toDay.toString(), zone, today,
                Period.months(DEFAULT_MONTHS), Period.years(MAX_YEARS), MAX_YEARS + " years");
        return new LocalDate[]{range[0].withZone(zone).toLocalDate(), toDay};
    }

    // ---------------------------------------------------------------- invoices

    /**
     * @return the invoice if the user may read it, it is not in the recycle bin and it is an energy invoice; else null
     */
    static JsonObject invoiceObject(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes, long id) {
        JsonObject object = lookup.readable(id);
        if (object == null || ResourceWebIso.isDeleted(object)) return null;
        return classes.withHeirs(CLASS_INVOICE).contains(object.getJevisClass()) ? object : null;
    }

    /**
     * @return the day the invoice is sorted and filtered by: "Period Start", else "Invoice Date"; null if none
     */
    static LocalDate keyDay(Map<String, JsonAttribute> attributes) {
        LocalDate start = ResourceWebRegisters.parseDate(text(attributes, ATTRIBUTE_PERIOD_START));
        return start != null ? start : ResourceWebRegisters.parseDate(text(attributes, ATTRIBUTE_DATE));
    }

    /**
     * {@code GET /invoices}: the readable invoices whose period start (else invoice date) lies within the days, newest
     * first.
     */
    static Map<String, Object> invoicesJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                            ResourceWebContracts.Store store, LocalDate from, LocalDate to) {
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Object[]> found = new ArrayList<>();
        for (JsonObject object : ResourceWebRegisters.readableOfClasses(lookup, classes.withHeirs(CLASS_INVOICE))) {
            Map<String, JsonAttribute> attributes = ResourceWebContracts.attributes(object.getId(), store);
            LocalDate day = keyDay(attributes);
            if (day == null || day.isBefore(from) || day.isAfter(to)) continue;
            found.add(new Object[]{object, attributes, day});
        }
        found.sort(Comparator.comparing((Object[] f) -> (LocalDate) f[2]).reversed()
                .thenComparing(f -> ((JsonObject) f[0]).getId(), Comparator.reverseOrder()));
        boolean truncated = found.size() > MAX_INVOICES;
        if (truncated) found = found.subList(0, MAX_INVOICES);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] f : found) {
            @SuppressWarnings("unchecked")
            Map<String, JsonAttribute> attributes = (Map<String, JsonAttribute>) f[1];
            list.add(invoiceJson((JsonObject) f[0], attributes, lookup, buildingClasses));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("invoices", list);
        result.put("truncated", truncated);
        return result;
    }

    static Map<String, Object> invoiceJson(JsonObject object, Map<String, JsonAttribute> attributes,
                                           ResourceWebModules.Lookup lookup, Set<String> buildingClasses) {
        Double gross = amount(attributes, ATTRIBUTE_GROSS);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", object.getId());
        json.put("number", text(attributes, ATTRIBUTE_NUMBER));
        json.put("date", day(attributes, ATTRIBUTE_DATE));
        json.put("format", text(attributes, ATTRIBUTE_FORMAT));
        json.put("seller", text(attributes, ATTRIBUTE_SELLER));
        json.put("buyer", text(attributes, ATTRIBUTE_BUYER));
        json.put("periodStart", day(attributes, ATTRIBUTE_PERIOD_START));
        json.put("periodEnd", day(attributes, ATTRIBUTE_PERIOD_END));
        json.put("medium", text(attributes, ATTRIBUTE_MEDIUM));
        json.put("meterPoint", text(attributes, ATTRIBUTE_METER_POINT));
        json.put("consumption", amount(attributes, ATTRIBUTE_CONSUMPTION));
        json.put("consumptionUnit", ResourceWebRegisters.unitLabel(attributes.get(ATTRIBUTE_CONSUMPTION)));
        json.put("net", amount(attributes, ATTRIBUTE_NET));
        json.put("tax", amount(attributes, ATTRIBUTE_TAX));
        json.put("gross", gross);
        json.put("currency", text(attributes, ATTRIBUTE_CURRENCY));
        json.put("credit", gross != null && gross < 0);
        json.put("file", text(attributes, ATTRIBUTE_FILE));
        json.put("path", ResourceWebNotes.path(object, lookup, buildingClasses));
        json.put("site", ResourceWebNotes.siteJson(object, lookup, buildingClasses));
        return json;
    }

    /**
     * {@code GET /invoices/{id}}: one invoice plus seller VAT ID and line items; null if not readable.
     */
    static Map<String, Object> invoiceDetailJson(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                                 ResourceWebContracts.Store store, long id) {
        JsonObject object = invoiceObject(lookup, classes, id);
        if (object == null) return null;
        Map<String, JsonAttribute> attributes = ResourceWebContracts.attributes(id, store);
        Map<String, Object> json = invoiceJson(object, attributes, lookup,
                classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING));
        json.put("sellerVatId", text(attributes, ATTRIBUTE_SELLER_VAT_ID));
        json.put("items", items(text(attributes, ATTRIBUTE_LINE_ITEMS)));
        return json;
    }

    /**
     * @return the stored line items (JSON array) as is, empty if empty or not parseable
     */
    static List<Object> items(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isArray()) return new ArrayList<>();
            List<Object> items = new ArrayList<>();
            for (JsonNode item : node) items.add(MAPPER.convertValue(item, Object.class));
            return items;
        } catch (Exception e) {
            logger.debug("Could not parse the line items {}", json, e);
            return new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- file

    /**
     * @return {@code application/pdf} for PDF bytes, {@code application/xml} for XML, else
     * {@code application/octet-stream}
     */
    static String contentType(byte[] bytes) {
        if (bytes == null) return MediaType.APPLICATION_OCTET_STREAM;
        if (bytes.length >= 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-') {
            return TYPE_PDF;
        }
        int i = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            i = 3;
        }
        while (i < bytes.length && (bytes[i] == ' ' || bytes[i] == '\t' || bytes[i] == '\r' || bytes[i] == '\n')) i++;
        return i < bytes.length && bytes[i] == '<' ? TYPE_XML : MediaType.APPLICATION_OCTET_STREAM;
    }

    /**
     * {@code GET /invoices/{id}/file}: the bytes of the latest "Invoice File" sample (no other attribute).
     */
    static Response fileResponse(ResourceWebModules.Lookup lookup, ResourceWebRegisters.Classes classes,
                                 ResourceWebContracts.Store store, long id, boolean inline) throws Exception {
        JsonObject invoice = invoiceObject(lookup, classes, id);
        if (invoice == null) return notFound("Invoice not found");
        JsonAttribute attribute = ResourceWebContracts.attributes(id, store).get(ATTRIBUTE_FILE);
        JsonSample sample = attribute != null ? attribute.getLatestValue() : null;
        byte[] bytes = sample != null ? store.file(id, ATTRIBUTE_FILE, sample) : null;
        if (bytes == null) return notFound("File not found");
        String type = contentType(bytes);
        boolean pdf = TYPE_PDF.equals(type);
        String name = sample.getValue();
        if (name == null || name.trim().isEmpty()) {
            name = invoice.getName() + (pdf ? ".pdf" : TYPE_XML.equals(type) ? ".xml" : "");
        }
        boolean showInline = pdf && inline;
        Response.ResponseBuilder response = Response.ok(bytes).type(type)
                .header("Content-Disposition", ResourceWebIso.contentDisposition(name, showInline))
                .header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "private, no-store");
        /* a sandbox would keep the browsers' PDF viewers from showing an inline PDF */
        if (!showInline) response.header("Content-Security-Policy", "default-src 'none'; sandbox");
        return response.build();
    }

    // ---------------------------------------------------------------- helpers

    /**
     * @return the latest value, null if none or empty
     */
    static String text(Map<String, JsonAttribute> attributes, String name) {
        String value = ResourceWebContracts.latestValue(attributes.get(name));
        return value != null && !value.trim().isEmpty() ? value : null;
    }

    /**
     * @return the latest value as {@code yyyy-MM-dd} (stored as ISO date time at the start of the day), the text if it
     * is no date, null if none
     */
    static String day(Map<String, JsonAttribute> attributes, String name) {
        return ResourceWebContracts.day(text(attributes, name));
    }

    /**
     * @return the latest value as finite number, null if none or no number
     */
    static Double amount(Map<String, JsonAttribute> attributes, String name) {
        Object number = ResourceWebRegisters.number(text(attributes, name));
        return number instanceof Double ? (Double) number : null;
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST).entity(message).type(MediaType.TEXT_PLAIN)
                .header("Cache-Control", "no-store").build();
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
