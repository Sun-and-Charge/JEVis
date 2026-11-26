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
import org.jevis.commons.certificate.EnergyCertificate;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.jevis.rest.web.FakeEditStore.ZONE;
import static org.jevis.rest.web.FakeEditStore.obj;
import static org.junit.Assert.*;

public class ResourceWebCertificatesTest {

    private static final DateTime NOW = new DateTime(2026, 11, 26, 10, 0, 0, ZONE);
    private static final String DATA = "{\"type\":\"CONSUMPTION\",\"issued\":\"2017-03-01\",\"validUntil\":\"2027-02-28\","
            + "\"energyClass\":\"C\",\"issuer\":\"Ingenieurbüro Muster\",\"responsible\":[50],\"heatConsumption\":112.5}";

    /** organisation 1 / building 10 (create right) / building 20 (read only) / group 50 */
    private static FakeEditStore store() {
        FakeEditStore store = new FakeEditStore().add(
                obj(1, "Demo", "Organization", 0),
                obj(10, "Musterwerk", "Building", 1),
                obj(20, "Außenlager", "Building", 1),
                obj(50, "Energieteam", "Group", 1));
        store.create.add(10L);
        store.write.add(10L);
        store.delete.add(10L);
        return store;
    }

    @Test
    public void createMakesTheDirectoryOnceAndWritesTheData() throws Exception {
        FakeEditStore store = store();
        WebEdit.Reply reply = ResourceWebCertificates.create(store, "{\"building\":10,\"data\":" + DATA + "}", NOW);
        assertEquals(201, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("Verbrauchsausweis 2017", json.get("name").asText());
        assertEquals("EXPIRING", json.get("status").asText());
        assertEquals(94, json.get("daysLeft").asInt());
        assertEquals("Musterwerk", json.get("building").get("name").asText());
        assertEquals("Energieteam", json.get("responsible").get("names").get(0).asText());
        assertEquals("Demo User", json.get("data").get("createdBy").asText());
        assertTrue(json.get("canWrite").asBoolean());
        long id = json.get("id").asLong();
        long directory = store.object(id).getParent();
        assertEquals(EnergyCertificate.DIRECTORY_CLASS_NAME, store.object(directory).getJevisClass());

        /* the second one goes into the same directory */
        WebEdit.Reply second = ResourceWebCertificates.create(store,
                "{\"building\":10,\"data\":" + DATA.replace("CONSUMPTION", "DEMAND") + "}", NOW.plusSeconds(5));
        assertEquals(201, second.status);
        assertEquals(directory, store.object(FakeEditStore.json(second).get("id").asLong()).getParent());

        Map<String, Object> list = ResourceWebCertificates.list(store, null, new LocalDate(2026, 11, 26));
        JsonNode l = FakeEditStore.MAPPER.valueToTree(list);
        assertEquals(2, l.get("certificates").size());
        assertEquals(2, l.get("buildings").size());
        assertEquals(2, ResourceWebCertificates.moduleCounts(store.lookup().readable()).get("certificates"));
    }

    @Test
    public void createChecksRightsAndInput() throws Exception {
        FakeEditStore store = store();
        assertEquals(403, ResourceWebCertificates.create(store, "{\"building\":20,\"data\":" + DATA + "}", NOW).status);
        assertEquals(404, ResourceWebCertificates.create(store, "{\"building\":1,\"data\":" + DATA + "}", NOW).status);
        WebEdit.Reply bad = WebEdit.call(store, s -> ResourceWebCertificates.create(s,
                "{\"building\":10,\"data\":" + DATA.replace("\"C\"", "\"Z\"") + "}", NOW));
        assertEquals(400, bad.status);
        assertEquals("energyClass:class", bad.body);
        WebEdit.Reply person = WebEdit.call(store, s -> ResourceWebCertificates.create(s,
                "{\"building\":10,\"data\":" + DATA.replace("[50]", "[10]") + "}", NOW));
        assertEquals("responsible:person", person.body);
        assertTrue(store.writes.isEmpty());
    }

    @Test
    public void updateNeedsTheVersionAndKeepsCreated() throws Exception {
        FakeEditStore store = store();
        JsonNode created = FakeEditStore.json(ResourceWebCertificates.create(store, "{\"building\":10,\"data\":" + DATA + "}", NOW));
        long id = created.get("id").asLong();
        String version = created.get("version").asText();

        String changed = DATA.replace("2017-03-01", "2018-03-01").replace("2027-02-28", "2028-02-29");
        WebEdit.Reply conflict = ResourceWebCertificates.update(store, id,
                "{\"version\":\"2020-01-01T00:00:00.000+01:00\",\"data\":" + changed + "}", NOW.plusMinutes(1));
        assertEquals(409, conflict.status);

        WebEdit.Reply ok = ResourceWebCertificates.update(store, id, "{\"version\":\"" + version + "\",\"data\":" + changed + "}",
                NOW.plusMinutes(1));
        assertEquals(200, ok.status);
        JsonNode json = FakeEditStore.json(ok);
        assertEquals("2028-02-29", json.get("validUntil").asText());
        assertEquals("Demo User", json.get("data").get("createdBy").asText());
        assertTrue(store.renames.contains(id + " Verbrauchsausweis 2018"));

        store.write.remove(id);
        assertEquals(403, ResourceWebCertificates.update(store, id, "{\"version\":\"x\",\"data\":" + DATA + "}", NOW).status);
    }

    @Test
    public void pdfUploadAndDelete() throws Exception {
        FakeEditStore store = store();
        long id = FakeEditStore.json(ResourceWebCertificates.create(store, "{\"building\":10,\"data\":" + DATA + "}", NOW)).get("id").asLong();
        byte[] pdf = "%PDF-1.4 demo".getBytes(StandardCharsets.US_ASCII);
        assertEquals(400, ResourceWebCertificates.upload(store, id, "a.pdf",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.US_ASCII)), 1000, NOW).status);
        assertEquals(413, ResourceWebCertificates.upload(store, id, "a.pdf", new ByteArrayInputStream(pdf), 5, NOW).status);
        WebEdit.Reply ok = ResourceWebCertificates.upload(store, id, "Ausweis", new ByteArrayInputStream(pdf), 1000, NOW);
        assertEquals(201, ok.status);
        assertEquals("Ausweis.pdf", FakeEditStore.json(ok).get("file").get("name").asText());
        assertEquals("%PDF-1.4 demo", store.latestFile(id, EnergyCertificate.ATTRIBUTE_FILE));

        assertEquals(204, ResourceWebCertificates.deleteFile(store, id).status);
        assertNull(store.latest(id, EnergyCertificate.ATTRIBUTE_FILE));
    }

    @Test
    public void deleteGoesToTheRecycleBin() throws Exception {
        FakeEditStore store = store();
        JsonNode created = FakeEditStore.json(ResourceWebCertificates.create(store, "{\"building\":10,\"data\":" + DATA + "}", NOW));
        long id = created.get("id").asLong();
        assertEquals(400, ResourceWebCertificates.delete(store, id, null).status);
        assertEquals(204, ResourceWebCertificates.delete(store, id, created.get("version").asText()).status);
        assertTrue(store.recycled.contains(id));
        assertEquals(404, ResourceWebCertificates.delete(store, id, created.get("version").asText()).status);
    }

    @Test
    public void transferRequestNeedsWriteOrExecute() throws Exception {
        FakeEditStore store = store().add(obj(60, "Liegenschaftsamt", ResourceWebCertificates.CLASS_TRANSFER, 1));
        store.sample(60, "Channel", NOW.minusDays(1), "MAIL", null);
        assertEquals(403, ResourceWebCertificates.requestTransfer(store, 60, NOW).status);
        store.execute.add(60L);
        WebEdit.Reply reply = ResourceWebCertificates.requestTransfer(store, 60, NOW);
        assertEquals(202, reply.status);
        JsonNode json = FakeEditStore.json(reply);
        assertEquals("MAIL", json.get("channel").asText());
        assertEquals(FakeEditStore.ACCOUNT, json.get("requested").get("by").asText());
        assertEquals(404, ResourceWebCertificates.requestTransfer(store, 10, NOW).status);
    }
}
