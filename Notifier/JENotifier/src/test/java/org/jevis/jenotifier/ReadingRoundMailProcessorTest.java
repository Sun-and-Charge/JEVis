/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of Notifier.
 * <p>
 * Notifier is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * Notifier is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * Notifier. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * Notifier is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jenotifier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.appointment.MailState;
import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ReadingRoundMailProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDateTime NOW = new LocalDateTime(2026, 11, 13, 6, 0);
    private static final byte[] PDF = "%PDF-1.7 test".getBytes();

    /**
     * Rounds as JSON, mail states, addresses per id, route sheet; records writes and requests.
     */
    private static class FakeSource implements ReadingRoundMailProcessor.RoundSource {
        String list;
        final Map<Long, String> states = new HashMap<>();
        final Map<Long, List<String>> writes = new HashMap<>();
        final Map<Long, String> addresses = new HashMap<>();
        final List<String> sheets = new ArrayList<>();
        boolean sheetFails;
        boolean listFails;

        @Override
        public JsonNode rounds() throws Exception {
            if (listFails) throw new Exception("HTTP 503");
            return MAPPER.readTree(list);
        }

        @Override
        public byte[] routeSheet(long roundId, LocalDate due) throws Exception {
            sheets.add(roundId + "@" + due);
            if (sheetFails) throw new Exception("HTTP 500");
            return PDF;
        }

        @Override
        public String mailState(long roundId) {
            return states.get(roundId);
        }

        @Override
        public void writeMailState(long roundId, String json) {
            states.put(roundId, json);
            writes.computeIfAbsent(roundId, k -> new ArrayList<>()).add(json);
        }

        @Override
        public Set<String> addresses(List<Long> usersOrGroups) {
            Set<String> result = new LinkedHashSet<>();
            for (Long id : usersOrGroups) {
                if (addresses.containsKey(id)) result.add(addresses.get(id));
            }
            return result;
        }

        MailState state(long id) {
            return EntryFormat.fromJson(states.get(id), MailState.class);
        }

        int writeCount(long id) {
            List<String> list = writes.get(id);
            return list != null ? list.size() : 0;
        }
    }

    private FakeSource source;
    private final List<MailMessage> sent = new ArrayList<>();
    private boolean sendFails;

    @Before
    public void setUp() {
        source = new FakeSource();
        source.addresses.put(41L, "reader@example.org");
        source.addresses.put(44L, "team@example.org");
        source.addresses.put(42L, "boss@example.org");
    }

    private ReadingRoundMailProcessor.Result run(String today, String... rounds) {
        source.list = "{\"today\":\"" + today + "\",\"rounds\":[" + String.join(",", rounds) + "]}";
        return new ReadingRoundMailProcessor(source, mail -> {
            if (sendFails) throw new Exception("SMTP down");
            sent.add(mail);
        }, "https://example.org/app").run(NOW);
    }

    private static String round(long id, String state, String readers, String supervisor) {
        return "{\"id\":" + id + ",\"name\":\"Runde " + id + "\",\"site\":{\"id\":2,\"name\":\"Musterwerk\"},"
                + "\"enabled\":" + !"INACTIVE".equals(state) + ",\"period\":\"P1M\",\"startDate\":\"2026-01-01\","
                + "\"leadDays\":3,\"reminderDays\":3,\"escalationDays\":7,\"remarks\":null,"
                + "\"readers\":" + readers + ",\"supervisor\":" + supervisor + ","
                + "\"due\":" + ("NOT_STARTED".equals(state) ? "null" : "\"2026-11-01\"") + ",\"nextDue\":\"2026-12-01\","
                + "\"state\":\"" + state + "\",\"done\":1,\"total\":3,\"meters\":["
                + "{\"id\":101,\"name\":\"Zähler A\",\"location\":\"Keller\",\"done\":true,\"automatic\":false,\"readable\":true},"
                + "{\"id\":102,\"name\":\"Zähler B\",\"location\":null,\"done\":false,\"automatic\":false,\"readable\":true},"
                + "{\"id\":103,\"name\":\"Zähler C\",\"location\":\"Hof\",\"done\":false,\"automatic\":false,\"readable\":true}],"
                + "\"mail\":{\"lastMail\":null,\"lastMailKind\":null,\"lastError\":null}}";
    }

    private static final String READERS = "[{\"id\":41,\"name\":\"Max Muster\",\"kind\":\"USER\"},{\"id\":44,\"name\":\"Team\",\"kind\":\"GROUP\"}]";
    private static final String SUPERVISOR = "[{\"id\":42,\"name\":\"Erika Muster\",\"kind\":\"USER\"}]";

    @Test
    public void orderWithRouteSheetToTheReaders() {
        ReadingRoundMailProcessor.Result result = run("2026-11-01", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, result.getSent());
        assertEquals(0, result.getFailed());
        assertEquals(1, sent.size());
        MailMessage mail = sent.get(0);
        assertEquals("Ableseauftrag / Reading order: Runde 7001 - fällig am / due on 01.11.2026", mail.getSubject());
        assertEquals(new LinkedHashSet<>(Arrays.asList("reader@example.org", "team@example.org")), mail.getTo());
        assertTrue(mail.getCc().isEmpty());
        assertEquals(1, mail.getAttachments().size());
        assertEquals("Laufzettel_Runde 7001_2026-11-01.pdf", mail.getAttachments().get(0).getFileName());
        assertEquals("application/pdf", mail.getAttachments().get(0).getContentType());
        assertArrayEquals(PDF, mail.getAttachments().get(0).getContent());
        assertEquals(Collections.singletonList("7001@2026-11-01"), source.sheets);
        assertTrue(mail.getText().contains("https://example.org/app/readings?round=7001"));
        assertTrue(mail.getText().contains("1. Zähler A (Keller)"));

        assertEquals(1, source.writeCount(7001));
        MailState state = source.state(7001);
        assertEquals("2026-11-01", state.getDueMailSentFor());
        assertEquals("ORDER", state.getLastMailKind());
        assertEquals("2026-11-13T06:00:00", state.getLastMail());

        /* not again in the next cycle */
        run("2026-11-02", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        assertEquals(1, source.writeCount(7001));
    }

    @Test
    public void reminderWithTheOpenMeters() {
        MailState state = new MailState();
        state.setDueMailSentFor("2026-11-01");
        source.states.put(7001L, EntryFormat.toJson(state));
        run("2026-11-04", round(7001, "LATE", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        MailMessage mail = sent.get(0);
        assertEquals("Erinnerung / Reminder: Runde 7001 - 2 von / of 3 Zählern offen / meters open", mail.getSubject());
        assertTrue(mail.getText().contains("Offene Zähler:\n1. Zähler B\n2. Zähler C (Hof)\n"));
        assertFalse(mail.getText().contains("Zähler A"));
        assertEquals(1, mail.getAttachments().size());
        assertEquals("2026-11-01", source.state(7001).getReminderSentFor());
        assertEquals("REMINDER", source.state(7001).getLastMailKind());
        assertEquals(1, source.writeCount(7001));
    }

    @Test
    public void escalationToTheSupervisorWithReadersInCc() {
        MailState state = new MailState();
        state.setDueMailSentFor("2026-11-01");
        state.setReminderSentFor("2026-11-01");
        source.states.put(7001L, EntryFormat.toJson(state));
        source.addresses.put(44L, "boss@example.org");
        run("2026-11-08", round(7001, "ESCALATED", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        MailMessage mail = sent.get(0);
        assertEquals("Eskalation / Escalation: Runde 7001 - seit 01.11.2026 nicht abgeschlossen / not completed", mail.getSubject());
        assertEquals(Collections.singleton("boss@example.org"), mail.getTo());
        assertEquals("no address twice", Collections.singleton("reader@example.org"), mail.getCc());
        assertTrue(mail.getText().contains("Ableser: Max Muster, Team"));
        assertEquals("2026-11-01", source.state(7001).getEscalationSentFor());
        assertEquals(1, source.writeCount(7001));
    }

    @Test
    public void escalationWithoutSupervisorIsMarked() {
        MailState state = new MailState();
        state.setDueMailSentFor("2026-11-01");
        state.setReminderSentFor("2026-11-01");
        state.setLastMailKind("REMINDER");
        source.states.put(7001L, EntryFormat.toJson(state));
        ReadingRoundMailProcessor.Result result = run("2026-11-08", round(7001, "ESCALATED", READERS, "[]"));
        assertTrue(sent.isEmpty());
        assertEquals(0, result.getFailed());
        assertEquals("2026-11-01", source.state(7001).getEscalationSentFor());
        assertEquals("REMINDER", source.state(7001).getLastMailKind());
        assertEquals(1, source.writeCount(7001));
        run("2026-11-09", round(7001, "ESCALATED", READERS, "[]"));
        assertEquals(1, source.writeCount(7001));
    }

    @Test
    public void lateStartSendsOnlyTheLatestKind() {
        run("2026-11-10", round(7001, "ESCALATED", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getSubject().startsWith("Eskalation"));
        MailState state = source.state(7001);
        assertEquals("2026-11-01", state.getDueMailSentFor());
        assertEquals("2026-11-01", state.getReminderSentFor());
        assertEquals("2026-11-01", state.getEscalationSentFor());
        run("2026-11-11", round(7001, "ESCALATED", READERS, SUPERVISOR));
        assertEquals(1, sent.size());

        run("2026-11-05", round(7002, "LATE", READERS, SUPERVISOR));
        assertEquals(2, sent.size());
        assertTrue(sent.get(1).getSubject().startsWith("Erinnerung"));
        assertEquals("2026-11-01", source.state(7002).getDueMailSentFor());
    }

    @Test
    public void noMailForDoneInactiveOrNotStartedRounds() {
        ReadingRoundMailProcessor.Result result = run("2026-11-10", round(7001, "DONE", READERS, SUPERVISOR),
                round(7002, "INACTIVE", READERS, SUPERVISOR), round(7003, "NOT_STARTED", READERS, SUPERVISOR));
        assertTrue(sent.isEmpty());
        assertEquals(0, result.getSent());
        assertEquals(0, result.getFailed());
        assertTrue(source.writes.isEmpty());
        assertTrue(source.sheets.isEmpty());
    }

    @Test
    public void routeSheetFailureSendsWithoutAttachment() {
        source.sheetFails = true;
        run("2026-11-01", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getAttachments().isEmpty());
        assertFalse(sent.get(0).getText().contains("Laufzettel ist als PDF"));
        assertEquals("ORDER", source.state(7001).getLastMailKind());
    }

    @Test
    public void noRecipientsRecordsTheErrorAndRetries() {
        source.addresses.clear();
        ReadingRoundMailProcessor.Result result = run("2026-11-01", round(7001, "OPEN", READERS, SUPERVISOR));
        assertTrue(sent.isEmpty());
        assertEquals(1, result.getFailed());
        assertEquals(ReadingRoundMailProcessor.NO_RECIPIENTS, source.state(7001).getLastError());
        assertNull(source.state(7001).getDueMailSentFor());
        assertEquals(1, source.writeCount(7001));

        /* the same error is not written again */
        run("2026-11-01", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, source.writeCount(7001));

        /* tried again with an address: sent, the error cleared */
        source.addresses.put(41L, "reader@example.org");
        run("2026-11-02", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, sent.size());
        assertNull(source.state(7001).getLastError());
        assertEquals("2026-11-01", source.state(7001).getDueMailSentFor());
        assertEquals(2, source.writeCount(7001));
    }

    @Test
    public void sendFailureRecordsTheError() {
        sendFails = true;
        ReadingRoundMailProcessor.Result result = run("2026-11-01", round(7001, "OPEN", READERS, SUPERVISOR));
        assertEquals(1, result.getFailed());
        assertEquals("SMTP down", source.state(7001).getLastError());
        assertNull(source.state(7001).getDueMailSentFor());
    }

    @Test
    public void aFailingRoundDoesNotStopTheOthers() {
        ReadingRoundMailProcessor.RoundSource failing = new FakeSource() {
            @Override
            public String mailState(long roundId) {
                if (roundId == 7001) throw new IllegalStateException("no attribute");
                return super.mailState(roundId);
            }
        };
        ((FakeSource) failing).addresses.put(41L, "reader@example.org");
        ((FakeSource) failing).list = "{\"today\":\"2026-11-01\",\"rounds\":[" + round(7001, "OPEN", READERS, SUPERVISOR)
                + "," + round(7002, "OPEN", READERS, SUPERVISOR) + "]}";
        ReadingRoundMailProcessor.Result result = new ReadingRoundMailProcessor(failing, sent::add, null).run(NOW);
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getSent());
        assertEquals(1, sent.size());
        assertTrue(sent.get(0).getSubject().contains("Runde 7002"));
        assertFalse("no link without Web App URL", sent.get(0).getText().contains("readings?round="));
    }

    @Test
    public void listFailureIsReported() {
        source.listFails = true;
        source.list = "{}";
        ReadingRoundMailProcessor.Result result = new ReadingRoundMailProcessor(source, sent::add, null).run(NOW);
        assertEquals(1, result.getFailed());
        assertTrue(sent.isEmpty());
    }
}
