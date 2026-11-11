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
package org.jevis.rest.invoice;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.api.JEVisConstants;
import org.jevis.commons.classes.JC;
import org.jevis.commons.utils.JEVisDates;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Stores a parsed invoice below an Energy Invoices Directory (see docs/ENERGY-INVOICES.md): medium folder, the three
 * series, the invoice object with its attributes and file, and the series samples. All rights are checked before
 * anything is written.
 */
public final class InvoiceImport {

    private static final Logger logger = LogManager.getLogger(InvoiceImport.class);

    public static final String CLASS_DIRECTORY = JC.Directory.EnergyInvoicesDirectory.name;
    public static final String CLASS_INVOICE = JC.EnergyInvoice.name;
    public static final String CLASS_DATA = JC.Data.EnergyInvoiceData.name;
    public static final String SERIES_CONSUMPTION = "CONSUMPTION";
    public static final String SERIES_NET = "NET";
    public static final String SERIES_GROSS = "GROSS";
    /** series in creation order with their names de / en */
    static final String[][] SERIES = {
            {SERIES_CONSUMPTION, "Verbrauch laut Rechnung", "Consumption according to invoices"},
            {SERIES_NET, "Kosten netto laut Rechnung", "Net costs according to invoices"},
            {SERIES_GROSS, "Kosten brutto laut Rechnung", "Gross costs according to invoices"}};

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STRING = JEVisConstants.PrimitiveType.STRING;
    private static final int DOUBLE = JEVisConstants.PrimitiveType.DOUBLE;
    private static final int FILE = JEVisConstants.PrimitiveType.FILE;

    private InvoiceImport() {
    }

    /**
     * Result of an import: {@code {invoiceId, folderId, series: {CONSUMPTION, NET, GROSS}, warnings}}.
     */
    public static final class Result {
        public long invoiceId;
        public long folderId;
        public final Map<String, Long> series = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();

        public Map<String, Object> toJson() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("invoiceId", invoiceId);
            json.put("folderId", folderId);
            json.put("series", new LinkedHashMap<>(series));
            json.put("warnings", new ArrayList<>(warnings));
            return json;
        }
    }

    /**
     * @return the directory if it is a readable Energy Invoices Directory, else null
     */
    public static JsonObject directory(InvoiceStore store, Long id) throws Exception {
        if (id == null) return null;
        JsonObject directory = store.object(id);
        return directory != null && CLASS_DIRECTORY.equals(directory.getJevisClass()) ? directory : null;
    }

    /**
     * @return id of an Energy Invoice below the directory (recursively) with the same seller and number, else null
     */
    public static Long findDuplicate(InvoiceStore store, long directoryId, EInvoice invoice) throws Exception {
        if (invoice.number == null) return null;
        Deque<Long> todo = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        todo.add(directoryId);
        while (!todo.isEmpty()) {
            long id = todo.poll();
            if (!seen.add(id)) continue;
            for (JsonObject child : store.children(id)) {
                if (CLASS_INVOICE.equals(child.getJevisClass())) {
                    if (InvoiceAnalysis.isSameInvoice(invoice,
                            store.latest(child.getId(), JC.EnergyInvoice.a_SellerVATID),
                            store.latest(child.getId(), JC.EnergyInvoice.a_Seller),
                            store.latest(child.getId(), JC.EnergyInvoice.a_InvoiceNumber))) {
                        return child.getId();
                    }
                } else {
                    todo.add(child.getId());
                }
            }
        }
        return null;
    }

    /**
     * @return the folder for the medium: the directory itself if its "Medium" is the medium, else its child
     * Energy Invoices Directory with this "Medium", else null
     */
    static JsonObject mediumFolder(InvoiceStore store, JsonObject directory, Medium medium) throws Exception {
        if (medium.name().equals(store.latest(directory.getId(), JC.Directory.EnergyInvoicesDirectory.a_Medium))) {
            return directory;
        }
        for (JsonObject child : store.children(directory.getId())) {
            if (CLASS_DIRECTORY.equals(child.getJevisClass())
                    && medium.name().equals(store.latest(child.getId(), JC.Directory.EnergyInvoicesDirectory.a_Medium))) {
                return child;
            }
        }
        return null;
    }

    /**
     * @return the series of the folder by "Series" value (first one of each)
     */
    static Map<String, JsonObject> series(InvoiceStore store, JsonObject folder) throws Exception {
        Map<String, JsonObject> series = new LinkedHashMap<>();
        for (JsonObject child : store.children(folder.getId())) {
            if (CLASS_DATA.equals(child.getJevisClass())) {
                String key = store.latest(child.getId(), JC.Data.EnergyInvoiceData.a_Series);
                if (key != null && !series.containsKey(key)) series.put(key, child);
            }
        }
        return series;
    }

    /**
     * Imports the invoice.
     *
     * @param zone time zone of the sample time stamps (start of the day)
     * @throws InvoiceException 400 (directory), 403 (rights), 409 (duplicate)
     */
    public static Result importInvoice(InvoiceStore store, Long directoryId, Medium medium, EInvoice invoice,
                                       InvoiceAnalysis.Analysis analysis, byte[] file, String fileName,
                                       DateTimeZone zone) throws Exception {
        JsonObject directory = directory(store, directoryId);
        if (directory == null) {
            throw new InvoiceException(400, InvoiceException.INVALID_DIRECTORY,
                    "The directory is no readable Energy Invoices Directory.");
        }
        if (medium == null) {
            throw new InvoiceException(400, InvoiceException.INVALID_MEDIUM,
                    "The medium must be one of ELECTRICITY, GAS, DISTRICT_HEATING, OTHER.");
        }
        Long duplicate = findDuplicate(store, directory.getId(), invoice);
        if (duplicate != null) {
            throw new InvoiceException(409, InvoiceException.DUPLICATE,
                    "The invoice " + invoice.number + " of " + invoice.seller + " was already imported.", duplicate);
        }

        /* rights first, nothing is written if one is missing */
        JsonObject folder = mediumFolder(store, directory, medium);
        JsonObject parent = folder != null ? folder : directory;
        if (folder == null && !store.canCreate(directory, CLASS_DIRECTORY)) throw forbidden();
        if (!store.canCreate(parent, CLASS_INVOICE)) throw forbidden();
        Map<String, JsonObject> series = folder != null ? series(store, folder) : new HashMap<>();
        for (String[] s : SERIES) {
            JsonObject existing = series.get(s[0]);
            if (existing == null && !store.canCreate(parent, CLASS_DATA)) throw forbidden();
            if (existing != null && !store.canWrite(existing)) throw forbidden();
        }

        /* undo steps of this import, run in reverse order if a later step fails */
        Deque<Undo> undo = new ArrayDeque<>();
        try {
            return write(store, directory, medium, invoice, analysis, file, fileName, zone, folder, series, undo);
        } catch (Exception e) {
            while (!undo.isEmpty()) {
                try {
                    undo.pop().run();
                } catch (Exception rollback) {
                    logger.error("Rollback of the invoice import failed", rollback);
                }
            }
            throw e;
        }
    }

    private interface Undo {
        void run() throws Exception;
    }

    private static Result write(InvoiceStore store, JsonObject directory, Medium medium, EInvoice invoice,
                                InvoiceAnalysis.Analysis analysis, byte[] file, String fileName, DateTimeZone zone,
                                JsonObject folder, Map<String, JsonObject> series, Deque<Undo> undo) throws Exception {
        Result result = new Result();
        DateTime now = DateTime.now(zone);
        if (folder == null) {
            folder = store.create(directory, CLASS_DIRECTORY, medium.nameDe(), names(medium.nameDe(), medium.nameEn()));
            long folderId = folder.getId();
            undo.push(() -> store.deleteObject(folderId));
            store.setSample(folder.getId(), JC.Directory.EnergyInvoicesDirectory.a_Medium, STRING, now, medium.name(), null);
        }
        result.folderId = folder.getId();

        String consumptionUnit = analysis.consumption != null ? analysis.consumption.unit : null;
        for (String[] s : SERIES) {
            JsonObject object = series.get(s[0]);
            if (object == null) {
                object = store.create(folder, CLASS_DATA, s[1], names(s[1], s[2]));
                long seriesId = object.getId();
                undo.push(() -> store.deleteObject(seriesId));
                store.setSample(object.getId(), JC.Data.EnergyInvoiceData.a_Series, STRING, now, s[0], null);
                String unit = SERIES_CONSUMPTION.equals(s[0]) ? consumptionUnit : currencySymbol(invoice.currency);
                if (unit != null) store.setUnit(object.getId(), JC.Data.EnergyInvoiceData.a_Value, unit);
                series.put(s[0], object);
            }
            result.series.put(s[0], object.getId());
        }

        /* invoice object */
        String number = invoice.number != null ? invoice.number : "";
        DateTime timestamp = toDateTime(InvoiceAnalysis.timestampDate(invoice, analysis), zone);
        String name = invoiceName(invoice, fileName);
        JsonObject object = store.create(folder, CLASS_INVOICE, name, Collections.emptyMap());
        result.invoiceId = object.getId();
        undo.push(() -> store.deleteObject(result.invoiceId));
        long id = object.getId();
        String currency = currencySymbol(invoice.currency);

        Map<String, Object[]> values = new LinkedHashMap<>();
        values.put(JC.EnergyInvoice.a_Format, new Object[]{STRING, invoice.format});
        values.put(JC.EnergyInvoice.a_InvoiceNumber, new Object[]{STRING, invoice.number});
        values.put(JC.EnergyInvoice.a_InvoiceDate, new Object[]{STRING, dateValue(invoice.date, zone)});
        values.put(JC.EnergyInvoice.a_Seller, new Object[]{STRING, invoice.seller});
        values.put(JC.EnergyInvoice.a_SellerVATID, new Object[]{STRING, invoice.sellerVatId});
        values.put(JC.EnergyInvoice.a_Buyer, new Object[]{STRING, invoice.buyer});
        values.put(JC.EnergyInvoice.a_PeriodStart, new Object[]{STRING, dateValue(analysis.periodStart, zone)});
        values.put(JC.EnergyInvoice.a_PeriodEnd, new Object[]{STRING, dateValue(analysis.periodEnd, zone)});
        values.put(JC.EnergyInvoice.a_Medium, new Object[]{STRING, medium.name()});
        values.put(JC.EnergyInvoice.a_MeterPoint, new Object[]{STRING, analysis.meterPoint});
        values.put(JC.EnergyInvoice.a_Consumption, new Object[]{DOUBLE, analysis.consumption != null ? analysis.consumption.value : null});
        values.put(JC.EnergyInvoice.a_NetAmount, new Object[]{DOUBLE, invoice.net});
        values.put(JC.EnergyInvoice.a_TaxAmount, new Object[]{DOUBLE, invoice.tax});
        values.put(JC.EnergyInvoice.a_GrossAmount, new Object[]{DOUBLE, invoice.gross});
        values.put(JC.EnergyInvoice.a_Currency, new Object[]{STRING, invoice.currency});
        values.put(JC.EnergyInvoice.a_LineItems, new Object[]{STRING, MAPPER.writeValueAsString(InvoiceAnalysis.itemsJson(invoice))});
        for (Map.Entry<String, Object[]> entry : values.entrySet()) {
            Object value = entry.getValue()[1];
            if (value == null) continue;
            String text = value instanceof BigDecimal ? plain((BigDecimal) value) : value.toString();
            store.setSample(id, entry.getKey(), (Integer) entry.getValue()[0], timestamp, text, number);
        }
        if (consumptionUnit != null) store.setUnit(id, JC.EnergyInvoice.a_Consumption, consumptionUnit);
        if (currency != null) {
            store.setUnit(id, JC.EnergyInvoice.a_NetAmount, currency);
            store.setUnit(id, JC.EnergyInvoice.a_TaxAmount, currency);
            store.setUnit(id, JC.EnergyInvoice.a_GrossAmount, currency);
        }
        store.storeFile(id, JC.EnergyInvoice.a_InvoiceFile, timestamp, safeFileName(fileName), file, number);

        /* series samples */
        if (analysis.consumption != null) {
            JsonObject consumption = series.get(SERIES_CONSUMPTION);
            String unit = store.unit(consumption.getId(), JC.Data.EnergyInvoiceData.a_Value);
            if (unit == null || unit.isEmpty() || unit.equals(analysis.consumption.unit)) {
                addSample(store, consumption.getId(), timestamp, analysis.consumption.value, number, undo);
                if (unit == null || unit.isEmpty()) {
                    store.setUnit(consumption.getId(), JC.Data.EnergyInvoiceData.a_Value, analysis.consumption.unit);
                }
            } else {
                result.warnings.add("The consumption is in " + analysis.consumption.unit + " but the consumption series in "
                        + unit + ", it was not added to the series.");
            }
        }
        if (invoice.net != null) addSample(store, series.get(SERIES_NET).getId(), timestamp, invoice.net, number, undo);
        if (invoice.gross != null) addSample(store, series.get(SERIES_GROSS).getId(), timestamp, invoice.gross, number, undo);
        return result;
    }

    /**
     * Adds the value to the sample at the time stamp (or creates it); the invoice number is appended to the note.
     */
    static void addSample(InvoiceStore store, long id, DateTime timestamp, BigDecimal value, String number,
                          Deque<Undo> undo) throws Exception {
        String attribute = JC.Data.EnergyInvoiceData.a_Value;
        JsonSample existing = store.sampleAt(id, attribute, timestamp);
        if (existing == null) {
            undo.push(() -> store.deleteSample(id, attribute, timestamp));
        } else {
            String oldValue = existing.getValue();
            String oldNote = existing.getNote();
            undo.push(() -> store.setSample(id, attribute, DOUBLE, timestamp, oldValue, oldNote));
        }
        BigDecimal sum = value;
        String note = number;
        if (existing != null) {
            try {
                sum = new BigDecimal(existing.getValue().trim()).add(value);
            } catch (RuntimeException e) {
                sum = value;
            }
            String oldNote = existing.getNote();
            if (oldNote != null && !oldNote.trim().isEmpty()) note = oldNote.trim() + ", " + number;
        }
        store.setSample(id, attribute, DOUBLE, timestamp, plain(sum), note);
    }

    static DateTime toDateTime(LocalDate date, DateTimeZone zone) {
        return new org.joda.time.LocalDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth())
                .toDateTimeAtStartOfDay(zone);
    }

    /**
     * @return value of a "Date" attribute (ISO date time at the start of the day, as written by the Control Center)
     */
    static String dateValue(LocalDate date, DateTimeZone zone) {
        return date != null ? JEVisDates.DEFAULT_DATE_FORMAT.print(toDateTime(date, zone)) : null;
    }

    static String invoiceName(EInvoice invoice, String fileName) {
        StringBuilder name = new StringBuilder();
        if (invoice.number != null) name.append(invoice.number);
        if (invoice.seller != null) name.append(name.length() > 0 ? " " : "").append(invoice.seller);
        if (name.length() == 0) name.append(fileName != null ? fileName : "Invoice");
        return name.length() > 128 ? name.substring(0, 128) : name.toString();
    }

    /**
     * @return the file name without path and characters that are not allowed in file names, "invoice" if empty
     */
    static String safeFileName(String fileName) {
        if (fileName == null) return "invoice";
        String name = fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\x00-\\x1f:*?\"<>|]", "_").replace("..", "_").trim();
        if (name.length() > 200) name = name.substring(name.length() - 200);
        return name.isEmpty() ? "invoice" : name;
    }

    /**
     * @return the symbol of the currency for the unit ("€" for EUR), else the code; null if there is no currency
     */
    static String currencySymbol(String code) {
        if (code == null || code.trim().isEmpty()) return null;
        try {
            return Currency.getInstance(code.trim().toUpperCase(Locale.ROOT)).getSymbol(Locale.GERMANY);
        } catch (IllegalArgumentException e) {
            return code.trim();
        }
    }

    private static Map<String, String> names(String de, String en) {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("de", de);
        names.put("en", en);
        return names;
    }

    private static String plain(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static InvoiceException forbidden() {
        return new InvoiceException(403, InvoiceException.FORBIDDEN,
                "You may not create invoices in this folder or write to its series.");
    }
}
