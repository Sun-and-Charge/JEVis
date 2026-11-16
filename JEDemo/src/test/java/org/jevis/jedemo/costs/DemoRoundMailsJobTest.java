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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jevis.jedemo.DemoJob;
import org.jevis.jedemo.costs.FakeTree.Node;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.junit.Assert.*;

/**
 * The JEDemo job marks the mails of the reading rounds of the demo organisations as sent (demo rounds send no mails),
 * only there and only when the state differs.
 */
public class DemoRoundMailsJobTest {

    static final DateTimeZone BERLIN = DateTimeZone.forID("Europe/Berlin");
    static final DateTime T0 = new DateTime(2026, 1, 1, 0, 0, BERLIN);
    /**
     * 2026-11-16 08:00 Berlin
     */
    static final Instant NOW = Instant.parse("2026-11-16T07:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private FakeTree tree;
    private Node demoRound;
    private Node otherRound;
    private Node service;

    @Before
    public void setUp() {
        tree = new FakeTree();
        Node demo = tree.add(23048, "Sun and Charge Demo", "Organization", null);
        Node site = tree.add("Musterwerk", "Building", demo);
        demoRound = round(tree.add("Ableserunden", "Reading Round Directory", site), "Monatsablesung Musterwerk");
        Node other = tree.add(30000, "Kunde Nord", "Organization", null);
        otherRound = round(tree.add("Ableserunden", "Reading Round Directory", tree.add("Werk", "Building", other)), "Runde Kunde");
        Node services = tree.add(2, "Service Directory", "Service Directory", tree.add(1, "System", "Organization", null));
        service = tree.add(5000, "JEDemo", "JEDemo", services).set("Organization", T0, "23048;30000");
    }

    private Node round(Node directory, String name) {
        return tree.add(name, "Reading Round", directory).set("Enabled", T0, "true").set("Meters", T0, "[1]")
                .set("Start Date", T0, "2026-01-01").set("Period", T0, "P1M").set("Reminder Days", T0, "3").set("Escalation Days", T0, "7");
    }

    private void cycle(Instant now, boolean dryRun) throws Exception {
        new DemoJob(tree.ds, dryRun, Clock.fixed(now, ZoneOffset.UTC)).runCycle(service.proxy, null);
    }

    @Test
    public void marksTheRoundsOfTheDemoOrganisationOnlyAndOnlyWhenTheStateDiffers() throws Exception {
        cycle(NOW, false);
        assertEquals(mapper.readTree("{\"dueMailSentFor\":\"2026-12-01\",\"reminderSentFor\":\"2026-12-01\",\"escalationSentFor\":\"2026-11-01\"}"),
                mapper.readTree((String) demoRound.latest("Mail State")));
        assertNull("the organisation without \"Demo\" in its name is refused", otherRound.latest("Mail State"));
        assertEquals(Collections.singletonList("sample " + demoRound.id + " Mail State " + new DateTime(NOW.toEpochMilli())), tree.writes);

        /* an hour later: unchanged, nothing written */
        cycle(NOW.plusSeconds(3600), false);
        assertEquals(1, tree.writes.size());

        /* the next period: the escalation mark moves on, the last mail of the JENotifier is cleared */
        demoRound.set("Mail State", new DateTime(2026, 11, 20, 6, 0, BERLIN),
                "{\"dueMailSentFor\":\"2026-12-01\",\"reminderSentFor\":\"2026-12-01\",\"escalationSentFor\":\"2026-11-01\","
                        + "\"lastMail\":\"2026-11-20T06:00:00\",\"lastMailKind\":\"ESCALATION\"}");
        cycle(Instant.parse("2026-12-09T07:00:00Z"), false);
        assertEquals(2, tree.writes.size());
        assertEquals(mapper.readTree("{\"dueMailSentFor\":\"2027-01-01\",\"reminderSentFor\":\"2027-01-01\",\"escalationSentFor\":\"2026-12-01\"}"),
                mapper.readTree((String) demoRound.latest("Mail State")));
    }

    @Test
    public void aDryRunWritesNothing() throws Exception {
        cycle(NOW, true);
        assertTrue(tree.writes.toString(), tree.writes.isEmpty());
        assertNull(demoRound.latest("Mail State"));
    }

    @Test
    public void excludedRoundsAndRoundsWithoutScheduleAreLeftAlone() throws Exception {
        service.set("Excluded Objects", T0, String.valueOf(demoRound.id));
        Node broken = tree.add("Ohne Turnus", "Reading Round", demoRound.parent).set("Start Date", T0, "2026-01-01");
        cycle(NOW, false);
        assertTrue(tree.writes.toString(), tree.writes.isEmpty());
        assertNull(broken.latest("Mail State"));
    }
}
