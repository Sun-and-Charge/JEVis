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
import org.jevis.commons.ws.json.JsonFactory;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.json.JsonSample;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalTime;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/** R04 report on demand, web service part, with a fake environment; neutral test data. */
public class ResourceWebReportOnDemandTest {

    private static final DateTimeZone Z = DateTimeZone.forID("Europe/Berlin");
    private static final DateTime NOW = new DateTime(2026, 11, 27, 10, 0, Z);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final class Fake implements ResourceWebReportOnDemand.Env {
        JsonObject report = new JsonObject();
        boolean canWrite = true;
        Map<String, String> latest = new HashMap<>();
        Map<String, List<JsonSample>> samples = new HashMap<>();
        Map<String, byte[]> files = new HashMap<>();

        Fake() {
            report.setId(42);
            report.setName("Energiebericht Monat");
            report.setJevisClass("Periodic Report");
            latest.put("Schedule", "MONTHLY");
            latest.put("Time Zone", "Europe/Berlin");
            latest.put("Start Record", "2026-11-01T00:00:00.000+01:00");
        }

        void sample(String attribute, DateTime ts, String value, String note) {
            JsonSample s = new JsonSample();
            s.setTs(JsonFactory.sampleDTF.print(ts));
            s.setValue(value);
            s.setNote(note);
            samples.computeIfAbsent(attribute, k -> new ArrayList<>()).add(s);
        }

        @Override
        public JsonObject report(long id) {
            return id == 42 ? report : null;
        }

        @Override
        public boolean canWrite(JsonObject report) {
            return canWrite;
        }

        @Override
        public String latest(long id, String attribute) {
            return latest.get(attribute);
        }

        @Override
        public List<JsonSample> samples(long id, String attribute, DateTime from) {
            return samples.getOrDefault(attribute, Collections.emptyList());
        }

        @Override
        public void add(long id, String attribute, DateTime ts, String value) {
            sample(attribute, ts, value, null);
        }

        @Override
        public byte[] file(long id, String attribute, JsonSample sample) {
            return files.get(sample.getValue());
        }

        @Override
        public long userId() {
            return 7;
        }

        @Override
        public String userName() {
            return "Erika Muster";
        }

        List<String> addresses = new ArrayList<>(Collections.singletonList("erika@example.com"));

        @Override
        public List<String> mailAddresses() {
            return addresses;
        }

        @Override
        public DateTimeZone siteZone(JsonObject report) {
            return Z;
        }
    }

    private static int status(Exception e) {
        return ((ResourceWebReportOnDemand.Problem) e).status;
    }

    @Test
    @SuppressWarnings("unchecked")
    public void periodsFollowTheScheduleOfTheReport() throws Exception {
        Map<String, Object> state = ResourceWebReportOnDemand.state(new Fake(), 42, NOW);
        assertEquals(Boolean.TRUE, state.get("canRequest"));
        List<Map<String, Object>> periods = (List<Map<String, Object>>) state.get("periods");
        assertEquals(13, periods.size());
        assertEquals("2026-11-01T00:00:00.000+01:00", periods.get(0).get("start"));
        assertEquals(Boolean.TRUE, periods.get(0).get("current"));
        assertEquals("2026-10-01T00:00:00.000+02:00", periods.get(1).get("start"));
        assertEquals("2026-11-01T00:00:00.000+01:00", periods.get(1).get("end"));
        assertEquals("2025-11-01T00:00:00.000+01:00", periods.get(12).get("start"));
    }

    @Test
    public void periodStartWithAWorkingDayAndOtherSchedules() {
        LocalTime six = new LocalTime(6, 0);
        /* before 06:00 on Monday the day still belongs to Sunday, so the week started a week earlier */
        assertEquals(new DateTime(2026, 11, 16, 6, 0, Z), ResourceWebReportOnDemand.periodStart(new DateTime(2026, 11, 23, 5, 0, Z), "WEEKLY", six));
        assertEquals(new DateTime(2026, 11, 23, 6, 0, Z), ResourceWebReportOnDemand.periodStart(new DateTime(2026, 11, 23, 7, 0, Z), "WEEKLY", six));
        assertEquals(new DateTime(2026, 10, 1, 0, 0, Z), ResourceWebReportOnDemand.periodStart(NOW, "QUARTERLY", LocalTime.MIDNIGHT));
        assertEquals(new DateTime(2026, 1, 1, 0, 0, Z), ResourceWebReportOnDemand.periodStart(NOW, "YEARLY", LocalTime.MIDNIGHT));
        assertEquals(new DateTime(2026, 11, 27, 0, 0, Z), ResourceWebReportOnDemand.periodStart(NOW, "DAILY", LocalTime.MIDNIGHT));
    }

    @Test
    public void requestIsStoredForJEReport() throws Exception {
        Fake fake = new Fake();
        Map<String, Object> answer = ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\",\"mail\":true}", NOW);
        assertEquals("PENDING", answer.get("status"));
        assertEquals("2026-11-01T00:00:00.000+01:00", answer.get("end"));
        JsonNode stored = MAPPER.readTree(fake.samples.get("On Demand Request").get(0).getValue());
        assertEquals(answer.get("id"), stored.get("id").asText());
        /* the format JEReport's OnDemandRunner reads */
        assertEquals(new DateTime(2026, 10, 1, 0, 0, Z).getMillis(), new DateTime(stored.get("start").asText()).getMillis());
        assertEquals(7, stored.get("requestedBy").asLong());
        assertEquals("Erika Muster", stored.get("requestedByName").asText());
        assertTrue(stored.get("mail").asBoolean());
        assertEquals("erika@example.com", stored.get("mailTo").get(0).asText());
        /* without mail no address is stored */
        ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-09-01T00:00:00+02:00\"}", NOW);
        assertFalse(MAPPER.readTree(fake.samples.get("On Demand Request").get(1).getValue()).has("mailTo"));
        /* mail wanted, but the account has no address */
        fake.addresses.clear();
        try {
            ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-09-01T00:00:00+02:00\",\"mail\":true}", NOW);
            fail();
        } catch (Exception e) {
            assertEquals(400, status(e));
        }
    }

    @Test
    public void invalidRequestsAreRefused() throws Exception {
        Fake fake = new Fake();
        String[][] cases = {
                {"{\"start\":\"2026-10-02T00:00:00+02:00\"}", "400"},
                {"{\"start\":\"2026-12-01T00:00:00+01:00\"}", "400"},
                {"{}", "400"},
                {"not json", "400"},
        };
        for (String[] c : cases) {
            try {
                ResourceWebReportOnDemand.request(fake, 42, c[0], NOW);
                fail(c[0]);
            } catch (ResourceWebReportOnDemand.Problem p) {
                assertEquals(c[0], Integer.parseInt(c[1]), p.status);
            }
        }
        fake.canWrite = false;
        try {
            ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW);
            fail();
        } catch (Exception e) {
            assertEquals(403, status(e));
        }
        fake.canWrite = true;
        fake.latest.put("Schedule", "CUSTOM");
        try {
            ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW);
            fail();
        } catch (Exception e) {
            assertEquals(400, status(e));
        }
        try {
            ResourceWebReportOnDemand.request(fake, 99, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW);
            fail();
        } catch (Exception e) {
            assertEquals(404, status(e));
        }
    }

    @Test
    public void atMostThreeOpenRequests() throws Exception {
        Fake fake = new Fake();
        for (int i = 0; i < 3; i++) ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW.plusSeconds(i));
        try {
            ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW.plusSeconds(5));
            fail();
        } catch (Exception e) {
            assertEquals(429, status(e));
        }
        /* one of them done: a new one is possible again */
        String first = MAPPER.readTree(fake.samples.get("On Demand Request").get(0).getValue()).get("id").asText();
        fake.sample("On Demand Status", NOW.plusMinutes(1), "{\"id\":\"" + first + "\",\"status\":\"DONE\",\"xlsx\":\"a.xlsx\",\"pdf\":\"a.pdf\"}", null);
        ResourceWebReportOnDemand.request(fake, 42, "{\"start\":\"2026-10-01T00:00:00+02:00\"}", NOW.plusMinutes(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void requestsWithTheirStatusNewestFirst() throws Exception {
        Fake fake = new Fake();
        fake.sample("On Demand Request", NOW.minusDays(10), "{\"id\":\"old\",\"start\":\"2026-09-01T00:00:00+02:00\",\"requestedBy\":8,\"requestedByName\":\"Max Beispiel\"}", null);
        fake.sample("On Demand Request", NOW.minusHours(2), "{\"id\":\"done\",\"start\":\"2026-10-01T00:00:00+02:00\",\"requestedBy\":7,\"mail\":true}", null);
        fake.sample("On Demand Request", NOW.minusHours(1), "{\"id\":\"failed\",\"start\":\"2026-10-01T00:00:00+02:00\",\"requestedBy\":7}", null);
        fake.sample("On Demand Status", NOW.minusHours(2).plusMinutes(1), "{\"id\":\"done\",\"status\":\"RUNNING\"}", null);
        fake.sample("On Demand Status", NOW.minusHours(2).plusMinutes(2), "{\"id\":\"done\",\"status\":\"DONE\",\"xlsx\":\"b.xlsx\",\"pdf\":\"b.pdf\"}", null);
        fake.sample("On Demand Status", NOW.minusMinutes(50), "{\"id\":\"failed\",\"status\":\"FAILED\",\"message\":\"The report template produced no file\"}", null);
        fake.sample("On Demand Status", NOW, "kein JSON", null);

        List<Map<String, Object>> requests = (List<Map<String, Object>>) ResourceWebReportOnDemand.state(fake, 42, NOW).get("requests");
        assertEquals(Arrays.asList("failed", "done", "old"), Arrays.asList(requests.get(0).get("id"), requests.get(1).get("id"), requests.get(2).get("id")));
        assertEquals("FAILED", requests.get(0).get("status"));
        assertEquals("The report template produced no file", requests.get(0).get("message"));
        assertEquals("DONE", requests.get(1).get("status"));
        assertEquals(Boolean.TRUE, requests.get(1).get("pdf"));
        assertEquals(Boolean.TRUE, requests.get(1).get("mine"));
        /* not run after seven days */
        assertEquals("EXPIRED", requests.get(2).get("status"));
        assertEquals(Boolean.FALSE, requests.get(2).get("mine"));
        assertEquals("Max Beispiel", requests.get(2).get("requestedByName"));
    }

    @Test
    public void fileOfARequestIsFoundByItsNote() throws Exception {
        Fake fake = new Fake();
        fake.sample("On Demand Report PDF", NOW.minusHours(3), "x.pdf", "other");
        fake.sample("On Demand Report PDF", NOW.minusHours(2), "y.pdf", "mine");
        assertEquals("y.pdf", ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "mine", NOW).getValue());
        assertNull(ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "missing", NOW));
        assertNull(ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "", NOW));
    }

    @Test
    public void fileOfARequestIsFoundByTheNameInItsStatus() throws Exception {
        /* live 2026-11-27: the server replaced the note of the file sample with "Uploaded by servicesuser" */
        Fake fake = new Fake();
        String name = "202610_EnPI_Bericht_20261001.pdf";
        /* the same period requested before: same file name, older sample */
        fake.sample("On Demand Report PDF", NOW.minusHours(5), name, "Uploaded by servicesuser");
        fake.sample("On Demand Status", NOW.minusHours(5), "{\"id\":\"first\",\"status\":\"DONE\",\"pdf\":\"" + name + "\"}", null);
        fake.sample("On Demand Status", NOW.minusHours(1).minusSeconds(11), "{\"id\":\"r1\",\"status\":\"RUNNING\"}", null);
        fake.sample("On Demand Report PDF", NOW.minusHours(1), name, "Uploaded by servicesuser");
        fake.sample("On Demand Status", NOW.minusHours(1), "{\"id\":\"r1\",\"status\":\"DONE\",\"xlsx\":\"a.xlsx\",\"pdf\":\"" + name + "\"}", null);
        /* a later request of the same period */
        fake.sample("On Demand Report PDF", NOW.minusMinutes(5), name, "Uploaded by servicesuser");

        JsonSample r1 = ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "r1", NOW);
        assertEquals(JsonFactory.sampleDTF.print(NOW.minusHours(1)), r1.getTs());
        JsonSample first = ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "first", NOW);
        assertEquals(JsonFactory.sampleDTF.print(NOW.minusHours(5)), first.getTs());
        /* no xlsx sample stored, not finished, unknown */
        assertNull(ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report", "xlsx", "r1", NOW));
        assertNull(ResourceWebReportOnDemand.fileSample(fake, 42, "On Demand Report PDF", "pdf", "missing", NOW));
    }

    @Test
    public void reportWithoutRightsOrScheduleCannotBeRequested() throws Exception {
        Fake fake = new Fake();
        fake.canWrite = false;
        Map<String, Object> state = ResourceWebReportOnDemand.state(fake, 42, NOW);
        assertEquals(Boolean.FALSE, state.get("canRequest"));
        assertEquals("rights", state.get("reason"));
        fake.canWrite = true;
        fake.latest.remove("Schedule");
        state = ResourceWebReportOnDemand.state(fake, 42, NOW);
        assertEquals("schedule", state.get("reason"));
        assertEquals(Collections.emptyList(), state.get("periods"));
    }
}
