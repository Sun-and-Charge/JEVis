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

import jakarta.ws.rs.DELETE;
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
import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.sql.SQLDataSource;
import org.jevis.rest.invoice.InvoiceImport;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.math.BigDecimal;
import java.util.*;

/**
 * Stage E6.1 (see docs/WEB-API.md): the Energy Invoices Directories the user may import into (the import itself uses
 * {@code POST /invoices/parse} and {@code /invoices/import} of docs/ENERGY-INVOICES.md unchanged) and deleting an
 * invoice: it goes to the recycle bin and its amounts are taken out of the series of its folder.
 * <p>
 * The series samples are written as {@link InvoiceImport} writes them: one sample per time stamp, value = sum of the
 * invoices, note = their numbers ({@code "<number>"} or {@code "<number>, <number>"}).
 */
@Path("/JEWebService/v1/web")
public class ResourceWebEditInvoices {

    static final String CLASS_DIRECTORY = InvoiceImport.CLASS_DIRECTORY;
    static final String CLASS_DATA = InvoiceImport.CLASS_DATA;
    static final String ATTRIBUTE_DIRECTORY_MEDIUM = "Medium";
    static final String ATTRIBUTE_SERIES = "Series";
    static final String ATTRIBUTE_VALUE = "Value";
    static final String ACTION_DELETE = "invoice-delete";
    static final String VERSION_NONE = ResourceWebEditRegisters.VERSION_NONE;
    /**
     * the invoice attribute whose amount a series holds
     */
    static final Map<String, String> SERIES_AMOUNTS;

    static {
        Map<String, String> amounts = new LinkedHashMap<>();
        amounts.put(InvoiceImport.SERIES_CONSUMPTION, ResourceWebInvoices.ATTRIBUTE_CONSUMPTION);
        amounts.put(InvoiceImport.SERIES_NET, ResourceWebInvoices.ATTRIBUTE_NET);
        amounts.put(InvoiceImport.SERIES_GROSS, ResourceWebInvoices.ATTRIBUTE_GROSS);
        SERIES_AMOUNTS = Collections.unmodifiableMap(amounts);
    }

    private static final Logger logger = LogManager.getLogger(ResourceWebEditInvoices.class);
    private static final DateTime ALL_FROM = new DateTime(0L, DateTimeZone.UTC);
    /**
     * 2100-01-01
     */
    private static final DateTime ALL_TO = new DateTime(4102444800000L, DateTimeZone.UTC);
    private static final long MAX_SERIES_SAMPLES = 1_000_000L;

    // ---------------------------------------------------------------- endpoints

    @GET
    @Path("invoices/directories")
    @Produces(MediaType.APPLICATION_JSON)
    public Response directories(@Context HttpHeaders httpHeaders, @QueryParam("lang") String lang) {
        return WebEdit.run(httpHeaders, "load the invoice directories",
                store -> WebEdit.Reply.ok(directoriesJson(store.lookup(), store.classes(), store.rights(), texts(store))));
    }

    @DELETE
    @Path("invoices/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@Context HttpHeaders httpHeaders, @PathParam("id") long id,
                           @QueryParam("version") String version) {
        return WebEdit.run(httpHeaders, "delete the invoice " + id, store -> delete(store, id, version));
    }

    // ---------------------------------------------------------------- reading

    /**
     * The latest value of an attribute (as text), null if none.
     */
    interface Texts {
        String latest(long id, String attribute) throws Exception;
    }

    static Texts texts(WebEdit.Store store) {
        return (id, attribute) -> {
            JsonSample sample = store.latest(id, attribute);
            return sample != null ? sample.getValue() : null;
        };
    }

    static Texts texts(ResourceWebContracts.Store store) {
        return (id, attribute) -> ResourceWebContracts.latestValue(ResourceWebContracts.attributes(id, store).get(attribute));
    }

    /**
     * The read access of {@link ResourceWebInvoices} on the data of a write request (for the {@code 409} answer).
     */
    static ResourceWebContracts.Store contractsStore(WebEdit.Store store) {
        ResourceWebRegisters.Data data = store.registerData();
        return new ResourceWebContracts.Store() {
            @Override
            public Map<String, JsonAttribute> attributes(long id) {
                return data.attributes(id);
            }

            @Override
            public List<JsonSample> latest(long id, String attribute, int limit) throws Exception {
                return store.latestSamples(id, attribute, limit);
            }

            @Override
            public byte[] file(long id, String attribute, JsonSample sample) throws Exception {
                return store.file(id, attribute, sample);
            }
        };
    }

    // ---------------------------------------------------------------- directories

    /**
     * @return whether the directory is a folder of one medium (non-empty "Medium"), created by the import below a top
     * folder
     */
    static boolean isMediumFolder(JsonObject directory, Texts texts) {
        try {
            String medium = texts.latest(directory.getId(), ATTRIBUTE_DIRECTORY_MEDIUM);
            return medium != null && !medium.trim().isEmpty();
        } catch (Exception e) {
            logger.warn("Could not read the medium of {}", directory.getId(), e);
            return false;
        }
    }

    /**
     * As the Control Center's {@code InvoiceImportDialog.getImportDirectories}: the readable Energy Invoices
     * Directories outside the recycle bin without "Medium" with create right for "Energy Invoice".
     */
    static List<JsonObject> importDirectories(ResourceWebModules.Lookup lookup, WebEdit.Rights rights, Texts texts) {
        List<JsonObject> result = new ArrayList<>();
        for (JsonObject directory : lookup.readableOfClass(CLASS_DIRECTORY)) {
            if (ResourceWebIso.isDeleted(directory)) continue;
            if (!rights.canCreate(directory, InvoiceImport.CLASS_INVOICE)) continue;
            if (isMediumFolder(directory, texts)) continue;
            result.add(directory);
        }
        return result;
    }

    /**
     * {@code GET /invoices/directories}: {@code [{id, name, path, site}]} by site (without site last), path, name
     */
    static List<Map<String, Object>> directoriesJson(ResourceWebModules.Lookup lookup,
                                                     ResourceWebRegisters.Classes classes, WebEdit.Rights rights,
                                                     Texts texts) {
        Set<String> buildingClasses = classes.withHeirs(ResourceWebNavigation.CLASS_BUILDING);
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonObject directory : importDirectories(lookup, rights, texts)) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("id", directory.getId());
            json.put("name", directory.getName());
            json.put("path", ResourceWebNotes.path(directory, lookup, buildingClasses));
            json.put("site", ResourceWebNotes.siteJson(directory, lookup, buildingClasses));
            list.add(json);
        }
        list.sort(ResourceWebReports.order());
        return list;
    }

    // ---------------------------------------------------------------- rights and versions

    /**
     * @return the series objects ("Energy Invoice Data", readable or not, outside the recycle bin) by their folder
     */
    static Map<Long, List<JsonObject>> seriesByFolder(ResourceWebModules.Lookup lookup) {
        Map<Long, List<JsonObject>> result = new HashMap<>();
        for (JsonObject series : lookup.allOfClass(CLASS_DATA)) {
            if (ResourceWebIso.isDeleted(series)) continue;
            Long parent = lookup.parent(series.getId());
            if (parent != null) result.computeIfAbsent(parent, k -> new ArrayList<>()).add(series);
        }
        for (List<JsonObject> list : result.values()) list.sort(Comparator.comparingLong(JsonObject::getId));
        return result;
    }

    /**
     * @return the series next to the invoice (in its folder), by id
     */
    static List<JsonObject> seriesOf(JsonObject invoice, ResourceWebModules.Lookup lookup,
                                     Map<Long, List<JsonObject>> seriesByFolder) {
        Long folder = lookup.parent(invoice.getId());
        return folder != null ? seriesByFolder.getOrDefault(folder, Collections.emptyList()) : Collections.emptyList();
    }

    /**
     * @return delete right on the invoice and write right on all series of its folder
     */
    static boolean canDelete(JsonObject invoice, WebEdit.Rights rights, ResourceWebModules.Lookup lookup,
                             Map<Long, List<JsonObject>> seriesByFolder) {
        if (!rights.canDelete(invoice)) return false;
        for (JsonObject series : seriesOf(invoice, lookup, seriesByFolder)) {
            if (!rights.canWrite(series)) return false;
        }
        return true;
    }

    /**
     * @return the version of an invoice: the time stamp of the latest sample of all its attributes (ISO, server time
     * zone), {@value ResourceWebEditRegisters#VERSION_NONE} without samples
     */
    static String version(Map<String, JsonAttribute> attributes) {
        long max = Long.MIN_VALUE;
        for (Map.Entry<String, JsonAttribute> entry : attributes.entrySet()) {
            /* the check result (R03) is written by the service, a new check must not make the delete a conflict */
            if (InvoiceCheck.ATTRIBUTE_RESULT.equals(entry.getKey())) continue;
            JsonAttribute attribute = entry.getValue();
            JsonSample latest = attribute != null ? attribute.getLatestValue() : null;
            Long millis = latest != null ? ResourceWebNotes.millis(latest.getTs()) : null;
            if (millis != null) max = Math.max(max, millis);
        }
        return max != Long.MIN_VALUE ? WebEdit.iso(max, DateTimeZone.getDefault()) : VERSION_NONE;
    }

    /**
     * @return whether the given version is the current one
     * @throws WebEdit.Stop {@code 400} for an invalid version
     */
    static boolean sameVersion(String given, String current) throws WebEdit.Stop {
        if (VERSION_NONE.equals(given.trim()) || VERSION_NONE.equals(current)) return given.trim().equals(current);
        JsonSample latest = new JsonSample();
        latest.setTs(WebEdit.sampleTs(new DateTime(current)));
        return WebEdit.sameVersion(given, latest);
    }

    // ---------------------------------------------------------------- delete

    /**
     * The share of the invoice in one series sample.
     */
    static final class Share {
        final JsonObject series;
        final int primitiveType;
        final DateTime timestamp;
        final String oldValue;
        final String oldNote;
        /**
         * null: the sample is deleted (it only holds this invoice)
         */
        final String newValue;
        final String newNote;

        Share(JsonObject series, int primitiveType, DateTime timestamp, String oldValue, String oldNote,
              String newValue, String newNote) {
            this.series = series;
            this.primitiveType = primitiveType;
            this.timestamp = timestamp;
            this.oldValue = oldValue;
            this.oldNote = oldNote;
            this.newValue = newValue;
            this.newNote = newNote;
        }

        boolean deletes() {
            return newValue == null;
        }
    }

    /**
     * @return the invoice numbers of a series note ({@code "<number>"} or {@code "<number>, <number>"})
     */
    static List<String> numbers(String note) {
        List<String> numbers = new ArrayList<>();
        if (note == null) return numbers;
        for (String n : note.split(",")) {
            if (!n.trim().isEmpty()) numbers.add(n.trim());
        }
        return numbers;
    }

    /**
     * @return the number as plain text without trailing zeros (as {@link InvoiceImport} writes it)
     */
    static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static BigDecimal decimal(String text, String what) {
        if (text == null || text.trim().isEmpty()) throw new IllegalStateException(what + " is empty");
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(what + " is no number: " + text);
        }
    }

    /**
     * The changes of the series for taking the invoice out (read only): per series the sample whose note names the
     * invoice number, preferably at the time stamp of the import (the first sample of "Invoice Number"); another one
     * only if it is the only one naming the number. The sample is deleted if it only holds this invoice, else the
     * invoice's amount is subtracted and the number removed from the note.
     *
     * @throws IllegalStateException if a shared sample cannot be reduced (amount or value missing or no number)
     */
    static List<Share> shares(WebEdit.Store store, JsonObject invoice, List<JsonObject> seriesList,
                              Map<String, JsonAttribute> attributes) throws Exception {
        List<Share> shares = new ArrayList<>();
        String number = ResourceWebInvoices.text(attributes, ResourceWebInvoices.ATTRIBUTE_NUMBER);
        if (number == null) return shares;
        number = number.trim();
        List<JsonSample> first = store.samples(invoice.getId(), ResourceWebInvoices.ATTRIBUTE_NUMBER, ALL_FROM, ALL_TO, 1);
        Long importMillis = first.isEmpty() ? null : ResourceWebNotes.millis(first.get(0).getTs());

        for (JsonObject series : seriesList) {
            JsonSample kind = store.latest(series.getId(), ATTRIBUTE_SERIES);
            String amountAttribute = kind != null && kind.getValue() != null
                    ? SERIES_AMOUNTS.get(kind.getValue().trim()) : null;
            if (amountAttribute == null) continue;

            List<JsonSample> candidates = new ArrayList<>();
            JsonSample atImport = null;
            for (JsonSample sample : store.samples(series.getId(), ATTRIBUTE_VALUE, ALL_FROM, ALL_TO, MAX_SERIES_SAMPLES)) {
                if (!numbers(sample.getNote()).contains(number)) continue;
                candidates.add(sample);
                Long millis = ResourceWebNotes.millis(sample.getTs());
                if (importMillis != null && importMillis.equals(millis)) atImport = sample;
            }
            JsonSample sample = atImport;
            if (sample == null && candidates.size() == 1
                    && !(InvoiceImport.SERIES_CONSUMPTION.equals(kind.getValue().trim())
                    && unitsDiffer(store, invoice, series, attributes))) {
                sample = candidates.get(0);
            }
            if (sample == null) {
                if (!candidates.isEmpty()) {
                    logger.warn("Invoice {}: {} samples of series {} name {}, none at the import time; not changed",
                            invoice.getId(), candidates.size(), series.getId(), number);
                }
                continue;
            }

            List<String> numbers = numbers(sample.getNote());
            DateTime timestamp = new DateTime(ResourceWebNotes.millis(sample.getTs()));
            int type = WebEdit.primitiveType(store, series, ATTRIBUTE_VALUE, JEVisConstants.PrimitiveType.DOUBLE);
            if (numbers.size() == 1) {
                shares.add(new Share(series, type, timestamp, sample.getValue(), sample.getNote(), null, null));
                continue;
            }
            BigDecimal amount = decimal(ResourceWebInvoices.text(attributes, amountAttribute),
                    "The " + amountAttribute + " of invoice " + invoice.getId());
            BigDecimal value = decimal(sample.getValue(), "The sample of series " + series.getId() + " at " + sample.getTs());
            List<String> rest = new ArrayList<>(numbers);
            rest.remove(number);
            shares.add(new Share(series, type, timestamp, sample.getValue(), sample.getNote(),
                    plain(value.subtract(amount)), String.join(", ", rest)));
        }
        return shares;
    }

    /**
     * @return whether the input units of the invoice's consumption and of the consumption series are both known and
     * different (the import then did not add the consumption to the series)
     */
    private static boolean unitsDiffer(WebEdit.Store store, JsonObject invoice, JsonObject series,
                                       Map<String, JsonAttribute> attributes) {
        String invoiceUnit = ResourceWebRegisters.inputUnitLabel(attributes.get(ResourceWebInvoices.ATTRIBUTE_CONSUMPTION));
        String seriesUnit = ResourceWebRegisters.inputUnitLabel(store.registerData().attributes(series.getId()).get(ATTRIBUTE_VALUE));
        return invoiceUnit != null && seriesUnit != null && !invoiceUnit.equals(seriesUnit);
    }

    /**
     * {@code DELETE /invoices/{id}?version=…}: takes the invoice out of the series of its folder and moves it to the
     * recycle bin, all or nothing (series changes are undone if a later step fails; the invoice is deleted last).
     */
    static WebEdit.Reply delete(WebEdit.Store store, long id, String version) throws Exception {
        ResourceWebModules.Lookup lookup = store.lookup();
        ResourceWebRegisters.Classes classes = store.classes();
        JsonObject invoice = ResourceWebInvoices.invoiceObject(lookup, classes, id);
        if (invoice == null) return WebEdit.Reply.notFound("Invoice not found");
        WebEdit.Rights rights = store.rights();
        Map<Long, List<JsonObject>> seriesByFolder = seriesByFolder(lookup);
        if (!canDelete(invoice, rights, lookup, seriesByFolder)) {
            return WebEdit.Reply.forbidden("Not allowed to delete this invoice or to change the series of its folder");
        }
        if (version == null || version.trim().isEmpty()) return WebEdit.Reply.badRequest("version missing");
        ResourceWebContracts.Store read = contractsStore(store);
        Map<String, JsonAttribute> attributes = ResourceWebContracts.attributes(id, read);
        if (!sameVersion(version, version(attributes))) {
            return WebEdit.Reply.conflict(ResourceWebInvoices.invoiceDetailJson(lookup, classes, read, id, rights));
        }

        List<Share> shares = shares(store, invoice, seriesOf(invoice, lookup, seriesByFolder), attributes);
        Deque<Share> done = new ArrayDeque<>();
        try {
            for (Share share : shares) {
                done.push(share);
                if (share.deletes()) {
                    WebEdit.deleteSamples(store, share.series.getId(), ATTRIBUTE_VALUE, share.timestamp, share.timestamp,
                            ACTION_DELETE);
                } else {
                    WebEdit.writeSample(store, share.series.getId(), ATTRIBUTE_VALUE, share.primitiveType,
                            WebEdit.sample(share.timestamp, share.newValue, share.newNote), ACTION_DELETE);
                }
            }
            store.recycle(invoice);
        } catch (Exception e) {
            undo(store, done);
            throw e;
        }
        store.log(SQLDataSource.LOG_EVENT.MARK_AS_DELETE_OBJECT,
                WebEdit.objectLog(invoice.getId(), invoice.getName(), ACTION_DELETE));
        return WebEdit.Reply.noContent();
    }

    /**
     * Writes the old samples back, the last change first.
     */
    private static void undo(WebEdit.Store store, Deque<Share> done) {
        while (!done.isEmpty()) {
            Share share = done.pop();
            try {
                WebEdit.writeSample(store, share.series.getId(), ATTRIBUTE_VALUE, share.primitiveType,
                        WebEdit.sample(share.timestamp, share.oldValue, share.oldNote), ACTION_DELETE + "-rollback");
            } catch (Exception e) {
                logger.error("Could not restore the sample of series {} at {}", share.series.getId(), share.timestamp, e);
            }
        }
    }
}
