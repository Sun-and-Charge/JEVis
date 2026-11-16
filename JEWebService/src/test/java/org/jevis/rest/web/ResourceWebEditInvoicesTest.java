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
import org.jevis.api.JEVisConstants;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.jevis.rest.invoice.EInvoice;
import org.jevis.rest.invoice.InvoiceAnalysis;
import org.jevis.rest.invoice.InvoiceException;
import org.jevis.rest.invoice.InvoiceImport;
import org.jevis.rest.invoice.InvoiceStore;
import org.jevis.rest.invoice.Medium;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

public class ResourceWebEditInvoicesTest {

    private static final DateTimeZone ZONE = FakeEditStore.ZONE;
    private static final int STRING = JEVisConstants.PrimitiveType.STRING;
    private static final int DOUBLE = JEVisConstants.PrimitiveType.DOUBLE;
    private static final int FILE = JEVisConstants.PrimitiveType.FILE;
    private static final String DIRECTORY = ResourceWebEditInvoices.CLASS_DIRECTORY;
    private static final String DATA = ResourceWebEditInvoices.CLASS_DATA;
    private static final String INVOICE = ResourceWebInvoices.CLASS_INVOICE;
    private static final String VALUE = ResourceWebEditInvoices.ATTRIBUTE_VALUE;
    private static final String NUMBER = ResourceWebInvoices.ATTRIBUTE_NUMBER;

    /* period starts (time stamps of the invoices and their series samples) */
    private static final DateTime OCTOBER = new DateTime(2026, 10, 1, 0, 0, ZONE);
    private static final DateTime SEPTEMBER = new DateTime(2026, 9, 1, 0, 0, ZONE);
    private static final DateTime AUGUST = new DateTime(2026, 8, 1, 0, 0, ZONE);

    /* series of the folder "Strom" */
    private static final long CONSUMPTION = 10;
    private static final long NET = 11;
    private static final long GROSS = 12;

    /* invoices in "Strom": R-1 and R-2 share the October samples, R-3 is a credit note sharing September with R-0
       (no invoice object), R-4 is alone in August, R-5 has no series samples */
    private static final long R1 = 100;
    private static final long R2 = 101;
    private static final long R3 = 102;
    private static final long R4 = 103;
    private static final long R5 = 104;

    private FakeEditStore store;

    @Before
    public void setUp() {
        store = new FakeEditStore();
        store.type(ResourceWebNavigation.CLASS_BUILDING, null)
                .type(DIRECTORY, null, "Medium", STRING)
                .type(DATA, null, VALUE, DOUBLE, "Series", STRING)
                .type(INVOICE, null, "Invoice File", FILE, "Format", STRING, NUMBER, STRING, "Invoice Date", STRING,
                        "Seller", STRING, "Seller VAT ID", STRING, "Buyer", STRING, "Period Start", STRING,
                        "Period End", STRING, "Medium", STRING, "Meter Point", STRING, "Consumption", DOUBLE,
                        "Net Amount", DOUBLE, "Tax Amount", DOUBLE, "Gross Amount", DOUBLE, "Currency", STRING,
                        "Line Items", STRING);
        store.add(FakeEditStore.obj(1, "Organisation", ResourceWebNavigation.CLASS_ORGANIZATION, 0),
                FakeEditStore.obj(2, "Werk A", ResourceWebNavigation.CLASS_BUILDING, 1),
                FakeEditStore.obj(3, "Energieverträge", "Energy Contracting Directory", 2),
                FakeEditStore.obj(4, "Energierechnungen", DIRECTORY, 3),
                FakeEditStore.obj(5, "Strom", DIRECTORY, 4),
                FakeEditStore.obj(6, "Rechnungen ohne Standort", DIRECTORY, 1),
                FakeEditStore.obj(7, "Ohne Anlegerecht", DIRECTORY, 2),
                FakeEditStore.obj(8, "Verborgen", DIRECTORY, 2),
                FakeEditStore.obj(9, "Papierkorb", DIRECTORY, 2),
                FakeEditStore.obj(CONSUMPTION, "Verbrauch laut Rechnung", DATA, 5),
                FakeEditStore.obj(NET, "Kosten netto laut Rechnung", DATA, 5),
                FakeEditStore.obj(GROSS, "Kosten brutto laut Rechnung", DATA, 5),
                FakeEditStore.obj(R1, "R-1 Lieferant A", INVOICE, 5),
                FakeEditStore.obj(R2, "R-2 Lieferant A", INVOICE, 5),
                FakeEditStore.obj(R3, "R-3 Lieferant A", INVOICE, 5),
                FakeEditStore.obj(R4, "R-4 Lieferant A", INVOICE, 5),
                FakeEditStore.obj(R5, "R-5 Lieferant A", INVOICE, 5),
                FakeEditStore.obj(200, "Kein Rechnungsobjekt", DATA, 4));
        store.object(9).setDeleteTS("2026-11-01T00:00:00.000+01:00");
        store.hidden.add(8L);
        store.create.addAll(Arrays.asList(4L, 5L, 6L, 8L, 9L));
        store.delete.addAll(Arrays.asList(R1, R2, R3, R4, R5));
        store.write.addAll(Arrays.asList(CONSUMPTION, NET, GROSS));
        DateTime created = new DateTime(2025, 1, 1, 0, 0, ZONE);
        store.sample(5, "Medium", created, "ELECTRICITY", null);
        store.sample(CONSUMPTION, "Series", created, "CONSUMPTION", null)
                .sample(NET, "Series", created, "NET", null)
                .sample(GROSS, "Series", created, "GROSS", null);

        invoice(R1, "R-1", OCTOBER, "1000", "250.50", "298.10");
        invoice(R2, "R-2", OCTOBER, "500", "100.25", "119.30");
        invoice(R3, "R-3", SEPTEMBER, "-200", "-50", "-59.5");
        invoice(R4, "R-4", AUGUST, "300", "75", "89.25");
        invoice(R5, "R-5", AUGUST, "10", "2", "2.38");

        store.sample(CONSUMPTION, VALUE, OCTOBER, "1500", "R-1, R-2")
                .sample(NET, VALUE, OCTOBER, "350.75", "R-1, R-2")
                .sample(GROSS, VALUE, OCTOBER, "417.4", "R-1, R-2")
                .sample(CONSUMPTION, VALUE, SEPTEMBER, "800", "R-0, R-3")
                .sample(NET, VALUE, SEPTEMBER, "200", "R-0, R-3")
                .sample(GROSS, VALUE, SEPTEMBER, "238", "R-0, R-3")
                .sample(CONSUMPTION, VALUE, AUGUST, "300", "R-4")
                .sample(NET, VALUE, AUGUST, "75", "R-4")
                .sample(GROSS, VALUE, AUGUST, "89.25", "R-4");
    }

    private void invoice(long id, String number, DateTime timestamp, String consumption, String net, String gross) {
        store.sample(id, NUMBER, timestamp, number, number)
                .sample(id, "Seller", timestamp, "Lieferant A", number)
                .sample(id, "Period Start", timestamp, timestamp.toString(), number)
                .sample(id, "Consumption", timestamp, consumption, number)
                .sample(id, "Net Amount", timestamp, net, number)
                .sample(id, "Gross Amount", timestamp, gross, number)
                .sample(id, "Currency", timestamp, "EUR", number);
    }

    private String value(long series, DateTime time) {
        JsonSample sample = store.at(series, VALUE, time);
        return sample != null ? sample.getValue() + " | " + sample.getNote() : null;
    }

    private static String version(DateTime time) {
        return WebEdit.iso(time.getMillis(), DateTimeZone.getDefault());
    }

    private WebEdit.Reply delete(long id, String version) throws Exception {
        return WebEdit.call(store, s -> ResourceWebEditInvoices.delete(s, id, version));
    }

    private List<JsonObject> directories() {
        return ResourceWebEditInvoices.importDirectories(store.lookup(), store.rights(), ResourceWebEditInvoices.texts(store));
    }

    // ---------------------------------------------------------------- directories

    @Test
    @SuppressWarnings("unchecked")
    public void directoriesAsTheControlCenter() throws Exception {
        List<Map<String, Object>> list = ResourceWebEditInvoices.directoriesJson(store.lookup(), store.classes(),
                store.rights(), ResourceWebEditInvoices.texts(store));
        /* not the medium folder 5, not without create right (7), hidden (8) or in the recycle bin (9);
           with site first, then without site */
        List<Object> ids = new ArrayList<>();
        for (Map<String, Object> d : list) ids.add(d.get("id"));
        assertEquals(Arrays.asList(4L, 6L), ids);
        Map<String, Object> first = list.get(0);
        assertEquals("Energierechnungen", first.get("name"));
        assertEquals("Energieverträge", first.get("path"));
        assertEquals(2L, ((Map<String, Object>) first.get("site")).get("id"));
        assertEquals("Werk A", ((Map<String, Object>) first.get("site")).get("name"));
        assertNull(list.get(1).get("path"));
        assertNull(list.get(1).get("site"));
        assertEquals(new HashSet<>(Arrays.asList("id", "name", "path", "site")), first.keySet());

        /* an empty "Medium" is a top folder, only the create right for invoices counts */
        store.sample(5, "Medium", new DateTime(2026, 1, 1, 0, 0, ZONE), " ", null);
        assertTrue(directories().stream().anyMatch(d -> d.getId() == 5));
        store.create.clear();
        store.execute.add(4L);
        assertTrue(directories().isEmpty());
        assertEquals(0, ResourceWebEditInvoices.directoriesJson(store.lookup(), store.classes(),
                WebEdit.Rights.NONE, ResourceWebEditInvoices.texts(store)).size());
        assertTrue(store.writes.isEmpty());
    }

    // ---------------------------------------------------------------- list fields

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> listed(WebEdit.Rights rights, Map<String, Object>[] answer) {
        Map<String, Object> result = ResourceWebInvoices.invoicesJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), new LocalDate(2026, 1, 1), new LocalDate(2026, 12, 31),
                rights);
        answer[0] = result;
        Map<String, Map<String, Object>> byNumber = new HashMap<>();
        for (Map<String, Object> i : (List<Map<String, Object>>) result.get("invoices")) {
            byNumber.put((String) i.get("number"), i);
        }
        return byNumber;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void listAnswersCanImportCanDeleteAndVersion() {
        Map<String, Object>[] answer = new Map[1];
        Map<String, Map<String, Object>> invoices = listed(store.rights(), answer);
        assertEquals(true, answer[0].get("canImport"));
        assertEquals(true, invoices.get("R-1").get("canDelete"));
        assertEquals(version(OCTOBER), invoices.get("R-1").get("version"));
        assertEquals(version(AUGUST), invoices.get("R-4").get("version"));

        /* a later change of an attribute is the new version */
        DateTime edited = new DateTime(2026, 11, 12, 9, 30, 15, ZONE);
        store.sample(R1, "Buyer", edited, "Kunde B", null);
        assertEquals(version(edited), listed(store.rights(), answer).get("R-1").get("version"));

        /* without delete right on the invoice or write right on one series of its folder */
        store.delete.remove(R2);
        store.write.remove(GROSS);
        invoices = listed(store.rights(), answer);
        assertEquals(false, invoices.get("R-1").get("canDelete"));
        assertEquals(false, invoices.get("R-2").get("canDelete"));
        store.write.add(GROSS);
        invoices = listed(store.rights(), answer);
        assertEquals(true, invoices.get("R-1").get("canDelete"));
        assertEquals(false, invoices.get("R-2").get("canDelete"));
        /* a hidden series without write right also counts */
        store.add(FakeEditStore.obj(13, "Verborgene Reihe", DATA, 5));
        store.hidden.add(13L);
        assertEquals(false, listed(store.rights(), answer).get("R-1").get("canDelete"));
        store.write.add(13L);

        /* without rights nothing */
        invoices = listed(WebEdit.Rights.NONE, answer);
        assertEquals(false, answer[0].get("canImport"));
        assertEquals(false, invoices.get("R-1").get("canDelete"));
        assertNotNull(invoices.get("R-1").get("version"));

        /* only the medium folder may be created in: nothing to import into */
        store.create.clear();
        store.create.add(5L);
        listed(store.rights(), answer);
        assertEquals(false, answer[0].get("canImport"));

        /* the detail answers the same fields */
        Map<String, Object> detail = ResourceWebInvoices.invoiceDetailJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), R4, store.rights());
        assertEquals(true, detail.get("canDelete"));
        assertEquals(version(AUGUST), detail.get("version"));
        assertEquals(false, ResourceWebInvoices.invoiceDetailJson(store.lookup(), store.classes(),
                ResourceWebEditInvoices.contractsStore(store), R4).get("canDelete"));
    }

    // ---------------------------------------------------------------- delete

    @Test
    public void deleteAloneDeletesTheSample() throws Exception {
        WebEdit.Reply reply = delete(R4, version(AUGUST));
        assertEquals(204, reply.status);
        assertNull(value(CONSUMPTION, AUGUST));
        assertNull(value(NET, AUGUST));
        assertNull(value(GROSS, AUGUST));
        /* the other samples are untouched */
        assertEquals("1500 | R-1, R-2", value(CONSUMPTION, OCTOBER));
        assertEquals("800 | R-0, R-3", value(CONSUMPTION, SEPTEMBER));
        assertTrue(store.writes.isEmpty());
        assertEquals(Collections.singletonList(R4), store.recycled);
        assertEquals(3, store.deletes.size());
        assertTrue(store.logs.toString(), store.logs.contains("MARK_AS_DELETE_OBJECT 103:R-4 Lieferant A|web:invoice-delete"));
        assertEquals(4, store.logs.size());
        for (String log : store.logs) assertTrue(log, log.endsWith("|web:invoice-delete"));
        /* the invoice is gone from the list */
        assertNull(ResourceWebInvoices.invoiceObject(store.lookup(), store.classes(), R4));
        assertEquals(404, delete(R4, version(AUGUST)).status);
    }

    @Test
    public void deleteSharedSubtractsAndRemovesTheNumber() throws Exception {
        assertEquals(204, delete(R1, version(OCTOBER)).status);
        assertEquals("500 | R-2", value(CONSUMPTION, OCTOBER));
        assertEquals("100.25 | R-2", value(NET, OCTOBER));
        assertEquals("119.3 | R-2", value(GROSS, OCTOBER));
        assertTrue(store.deletes.isEmpty());
        assertEquals(Collections.singletonList(R1), store.recycled);
        assertTrue(store.writes.toString(), store.writes.contains("11/Value 1 100.25 | R-2"));

        /* then the other one alone: the samples go */
        assertEquals(204, delete(R2, version(OCTOBER)).status);
        assertNull(value(CONSUMPTION, OCTOBER));
        assertNull(value(GROSS, OCTOBER));
    }

    @Test
    public void deleteCreditNoteAddsItsAmountBack() throws Exception {
        assertEquals(204, delete(R3, version(SEPTEMBER)).status);
        assertEquals("1000 | R-0", value(CONSUMPTION, SEPTEMBER));
        assertEquals("250 | R-0", value(NET, SEPTEMBER));
        assertEquals("297.5 | R-0", value(GROSS, SEPTEMBER));
    }

    @Test
    public void deleteWithoutSeriesSample() throws Exception {
        assertEquals(204, delete(R5, version(AUGUST)).status);
        assertTrue(store.writes.isEmpty());
        assertTrue(store.deletes.isEmpty());
        assertEquals("300 | R-4", value(CONSUMPTION, AUGUST));
        assertEquals(Collections.singletonList(R5), store.recycled);
    }

    @Test
    public void deleteFindsAMovedSampleOnlyIfItIsTheOnlyOne() throws Exception {
        DateTime moved = AUGUST.plusDays(1);
        /* the series sample is not at the time of the import (e.g. moved by hand) and the only one naming it */
        store.samples.get(GROSS + "/" + VALUE).remove(AUGUST.getMillis());
        store.sample(GROSS, VALUE, moved, "100", "R-4, R-9");
        /* another sample names the number too: the one at the import time is used */
        store.sample(NET, VALUE, moved, "1", "R-4");
        /* two samples name it, none at the import time: ambiguous, left as they are */
        store.samples.get(CONSUMPTION + "/" + VALUE).remove(AUGUST.getMillis());
        store.sample(CONSUMPTION, VALUE, moved, "5", "R-4")
                .sample(CONSUMPTION, VALUE, moved.plusDays(1), "6", "R-4");
        assertEquals(204, delete(R4, version(AUGUST)).status);
        assertEquals("10.75 | R-9", value(GROSS, moved));
        assertNull(value(NET, AUGUST));
        assertEquals("1 | R-4", value(NET, moved));
        assertEquals("5 | R-4", value(CONSUMPTION, moved));
        assertEquals("6 | R-4", value(CONSUMPTION, moved.plusDays(1)));
    }

    @Test
    public void deleteKeepsAConsumptionInAnotherUnit() throws Exception {
        /* the import did not add a consumption in m³ to a kWh series */
        store.samples.get(CONSUMPTION + "/" + VALUE).remove(AUGUST.getMillis());
        DateTime other = AUGUST.plusMonths(1).plusDays(5);
        store.sample(CONSUMPTION, VALUE, other, "50", "R-4");
        store.inputUnits.put(R4 + "/Consumption", "m³");
        store.inputUnits.put(CONSUMPTION + "/" + VALUE, "kWh");
        assertEquals(204, delete(R4, version(AUGUST)).status);
        assertEquals("50 | R-4", value(CONSUMPTION, other));
        assertNull(value(NET, AUGUST));
    }

    @Test
    public void deleteErrors() throws Exception {
        /* 404: unknown, hidden, no invoice, in the recycle bin */
        store.hidden.add(R2);
        assertEquals(404, delete(999, version(OCTOBER)).status);
        assertEquals(404, delete(R2, version(OCTOBER)).status);
        assertEquals(404, delete(CONSUMPTION, version(OCTOBER)).status);
        store.hidden.remove(R2);
        store.object(R5).setDeleteTS("2026-11-01T00:00:00.000+01:00");
        assertEquals(404, delete(R5, version(AUGUST)).status);

        /* 400: version missing or invalid */
        assertEquals(400, delete(R1, null).status);
        assertEquals(400, delete(R1, " ").status);
        assertEquals(400, delete(R1, "gestern").status);

        /* 409 with the current invoice */
        WebEdit.Reply conflict = delete(R1, version(SEPTEMBER));
        assertEquals(409, conflict.status);
        JsonNode body = FakeEditStore.json(conflict);
        assertEquals(R1, body.get("id").asLong());
        assertEquals("R-1", body.get("number").asText());
        assertEquals(version(OCTOBER), body.get("version").asText());
        assertTrue(body.get("canDelete").asBoolean());
        assertTrue(body.has("items"));

        /* 403: no delete right on the invoice, no write right on a series */
        store.delete.remove(R1);
        assertEquals(403, delete(R1, version(OCTOBER)).status);
        store.delete.add(R1);
        store.write.remove(NET);
        assertEquals(403, delete(R1, version(OCTOBER)).status);

        /* nothing was changed */
        assertTrue(store.writes.isEmpty());
        assertTrue(store.deletes.isEmpty());
        assertTrue(store.recycled.isEmpty());
        assertTrue(store.logs.isEmpty());
        assertEquals("1500 | R-1, R-2", value(CONSUMPTION, OCTOBER));
    }

    @Test
    public void deleteFailsWithoutChangeIfASharedAmountIsMissing() throws Exception {
        store.samples.remove(R1 + "/Net Amount");
        try {
            delete(R1, version(OCTOBER));
            fail();
        } catch (IllegalStateException expected) {
            // 500
        }
        assertTrue(store.writes.isEmpty());
        assertTrue(store.recycled.isEmpty());
    }

    @Test
    public void rollbackIfASeriesWriteFails() throws Exception {
        store.failWritesOf.add(GROSS + "/" + VALUE);
        try {
            delete(R1, version(OCTOBER));
            fail();
        } catch (IllegalStateException expected) {
            // 500
        }
        assertEquals("1500 | R-1, R-2", value(CONSUMPTION, OCTOBER));
        assertEquals("350.75 | R-1, R-2", value(NET, OCTOBER));
        assertEquals("417.4 | R-1, R-2", value(GROSS, OCTOBER));
        assertTrue(store.recycled.isEmpty());
        assertNotNull(ResourceWebInvoices.invoiceObject(store.lookup(), store.classes(), R1));
        assertTrue(store.logs.toString(), store.logs.stream().anyMatch(l -> l.endsWith("|web:invoice-delete-rollback")));
        assertFalse(store.logs.toString(), store.logs.stream().anyMatch(l -> l.startsWith("MARK_AS_DELETE_OBJECT")));
    }

    @Test
    public void rollbackIfTheRecycleBinFails() throws Exception {
        FakeEditStore failing = new FakeEditStore() {
            @Override
            public void recycle(JsonObject object) {
                throw new IllegalStateException("database gone");
            }
        };
        failing.classMap.putAll(store.classMap);
        failing.objects.addAll(store.objects);
        failing.samples.putAll(store.samples);
        failing.delete.addAll(store.delete);
        failing.write.addAll(store.write);
        store = failing;
        try {
            delete(R4, version(AUGUST));
            fail();
        } catch (IllegalStateException expected) {
            // 500
        }
        assertEquals("300 | R-4", value(CONSUMPTION, AUGUST));
        assertEquals("75 | R-4", value(NET, AUGUST));
        assertEquals("89.25 | R-4", value(GROSS, AUGUST));
    }

    // ---------------------------------------------------------------- import again

    /**
     * {@link InvoiceStore} on the fake store, so the import and the delete work on the same data.
     */
    private static final class ImportStore implements InvoiceStore {
        private final FakeEditStore store;

        ImportStore(FakeEditStore store) {
            this.store = store;
        }

        @Override
        public JsonObject object(long id) {
            JsonObject object = store.object(id);
            return object != null && !store.hidden.contains(id) && !ResourceWebIso.isDeleted(object) ? object : null;
        }

        @Override
        public List<JsonObject> children(long id) {
            List<JsonObject> children = new ArrayList<>();
            for (JsonObject o : store.objects) {
                if (o.getParent() == id && object(o.getId()) != null) children.add(o);
            }
            return children;
        }

        @Override
        public String latest(long id, String attribute) {
            JsonSample sample = store.latest(id, attribute);
            return sample != null ? sample.getValue() : null;
        }

        @Override
        public JsonSample sampleAt(long id, String attribute, DateTime timestamp) {
            return store.at(id, attribute, timestamp);
        }

        @Override
        public boolean canCreate(JsonObject parent, String jevisClass) {
            return true;
        }

        @Override
        public boolean canWrite(JsonObject object) {
            return true;
        }

        @Override
        public JsonObject create(JsonObject parent, String jevisClass, String name, Map<String, String> localNames) {
            return store.createObject(name, jevisClass, parent.getId());
        }

        @Override
        public void setSample(long id, String attribute, int primitiveType, DateTime timestamp, String value, String note) {
            store.write(id, attribute, primitiveType, new ArrayList<>(Collections.singletonList(WebEdit.sample(timestamp, value, note))));
        }

        @Override
        public void setUnit(long id, String attribute, String unit) {
        }

        @Override
        public String unit(long id, String attribute) {
            return null;
        }

        @Override
        public void storeFile(long id, String attribute, DateTime timestamp, String fileName, byte[] content, String note) {
            store.files.put(id + "/" + attribute + "/" + timestamp.getMillis(), content);
            setSample(id, attribute, FILE, timestamp, fileName, note);
        }

        @Override
        public void deleteObject(long id) {
            store.deleteObject(store.object(id));
        }

        @Override
        public void deleteSample(long id, String attribute, DateTime timestamp) {
            store.delete(id, attribute, timestamp, timestamp);
        }
    }

    private static EInvoice november() {
        EInvoice invoice = new EInvoice();
        invoice.format = "XRechnung 3.0 UBL";
        invoice.number = "R-9";
        invoice.date = java.time.LocalDate.of(2026, 12, 3);
        invoice.seller = "Lieferant A";
        invoice.sellerVatId = "DE000000001";
        invoice.buyer = "Kunde B";
        invoice.periodStart = java.time.LocalDate.of(2026, 11, 1);
        invoice.periodEnd = java.time.LocalDate.of(2026, 11, 30);
        invoice.currency = "EUR";
        invoice.net = new BigDecimal("100.10");
        invoice.tax = new BigDecimal("19.02");
        invoice.gross = new BigDecimal("119.12");
        EInvoice.Line line = new EInvoice.Line();
        line.name = "Arbeitspreis Strom";
        line.quantity = new BigDecimal("400");
        line.unitCode = "KWH";
        line.unitPrice = new BigDecimal("0.25025");
        line.net = new BigDecimal("100.10");
        line.taxRate = new BigDecimal("19");
        invoice.lines.add(line);
        return invoice;
    }

    private InvoiceImport.Result importNovember(ImportStore importStore) throws Exception {
        EInvoice invoice = november();
        return InvoiceImport.importInvoice(importStore, 4L, Medium.ELECTRICITY, invoice, InvoiceAnalysis.analyse(invoice),
                "%PDF-1.7 R-9".getBytes(StandardCharsets.UTF_8), "R-9.pdf", ZONE);
    }

    @Test
    public void theFileCanBeImportedAgainAfterTheDelete() throws Exception {
        DateTime november = new DateTime(2026, 11, 1, 0, 0, ZONE);
        /* another invoice of the same period is in the series already */
        store.sample(CONSUMPTION, VALUE, november, "100", "R-8")
                .sample(NET, VALUE, november, "20", "R-8")
                .sample(GROSS, VALUE, november, "23.8", "R-8");
        ImportStore importStore = new ImportStore(store);
        InvoiceImport.Result first = importNovember(importStore);
        assertEquals(5L, first.folderId);
        assertEquals("500 | R-8, R-9", value(CONSUMPTION, november));
        assertEquals("120.1 | R-8, R-9", value(NET, november));
        assertEquals("142.92 | R-8, R-9", value(GROSS, november));
        try {
            importNovember(importStore);
            fail();
        } catch (InvoiceException duplicate) {
            assertEquals(409, duplicate.getStatus());
        }

        store.delete.add(first.invoiceId);
        String version = ResourceWebEditInvoices.version(
                ResourceWebContracts.attributes(first.invoiceId, ResourceWebEditInvoices.contractsStore(store)));
        assertEquals(version(november), version);
        assertEquals(204, delete(first.invoiceId, version).status);
        assertEquals("100 | R-8", value(CONSUMPTION, november));
        assertEquals("20 | R-8", value(NET, november));
        assertEquals("23.8 | R-8", value(GROSS, november));
        assertNull(InvoiceImport.findDuplicate(importStore, 4L, november()));

        InvoiceImport.Result again = importNovember(importStore);
        assertNotEquals(first.invoiceId, again.invoiceId);
        assertEquals("500 | R-8, R-9", value(CONSUMPTION, november));
        assertEquals("120.1 | R-8, R-9", value(NET, november));
        assertEquals("142.92 | R-8, R-9", value(GROSS, november));
    }
}
