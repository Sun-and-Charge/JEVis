/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.reading;

import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;
import org.junit.Test;

import static org.jevis.commons.reading.RoundMailLogic.MailKind.*;
import static org.junit.Assert.*;

public class RoundMailsTest {

    private static RoundMailInfo.Meter meter(long id, String name, String location, boolean done, boolean automatic) {
        RoundMailInfo.Meter meter = new RoundMailInfo.Meter();
        meter.id = id;
        meter.name = name;
        meter.location = location;
        meter.readable = true;
        meter.done = done;
        meter.automatic = automatic;
        return meter;
    }

    private static RoundMailInfo round() {
        RoundMailInfo round = new RoundMailInfo();
        round.id = 7001;
        round.name = "Monatsablesung Musterwerk";
        round.siteName = "Musterwerk";
        round.enabled = true;
        round.period = "P1M";
        round.due = new LocalDate(2026, 11, 1);
        round.state = "LATE";
        round.remarks = "Schlüssel beim Pförtner";
        round.meters.add(meter(5101, "Hauptzähler Strom", "Keller", true, false));
        round.meters.add(meter(5102, "Wasser Halle 1", null, false, false));
        round.meters.add(meter(5103, "Gas", "Hof", true, true));
        RoundMailInfo.Meter hidden = new RoundMailInfo.Meter();
        hidden.id = 5104;
        round.meters.add(hidden);
        round.total = 4;
        round.done = 2;
        RoundMailInfo.Person reader = new RoundMailInfo.Person();
        reader.id = 41;
        reader.name = "Max Mustermann";
        reader.kind = "USER";
        round.readers.add(reader);
        RoundMailInfo.Person unknown = new RoundMailInfo.Person();
        unknown.id = 43;
        unknown.kind = "GROUP";
        round.readers.add(unknown);
        return round;
    }

    @Test
    public void subjects() {
        RoundMailInfo round = round();
        assertEquals("Ableseauftrag / Reading order: Monatsablesung Musterwerk - fällig am / due on 01.11.2026",
                RoundMails.subject(ORDER, round));
        assertEquals("Erinnerung / Reminder: Monatsablesung Musterwerk - 2 von / of 4 Zählern offen / meters open",
                RoundMails.subject(REMINDER, round));
        assertEquals("Eskalation / Escalation: Monatsablesung Musterwerk - seit 01.11.2026 nicht abgeschlossen / not completed",
                RoundMails.subject(ESCALATION, round));
    }

    @Test
    public void orderListsReadableManualMetersWithLink() {
        MailMessage mail = RoundMails.build(ORDER, round(), "https://example.org/app/", true);
        String text = mail.getText();
        assertFalse(mail.isHtml());
        assertTrue(text.startsWith("Hallo,"));
        assertTrue(text.indexOf("Hello,") > text.indexOf("Hallo,"));
        assertTrue(text.contains("1. Hauptzähler Strom (Keller)\n2. Wasser Halle 1\n"));
        assertFalse(text.contains("Gas"));
        assertTrue(text.contains("1 Zähler ohne Leserecht nicht aufgeführt"));
        assertTrue(text.contains("Turnus: monatlich"));
        assertTrue(text.contains("Period: monthly"));
        assertTrue(text.contains("Standort: Musterwerk"));
        assertTrue(text.contains("Schlüssel beim Pförtner"));
        assertTrue(text.contains("https://example.org/app/readings?round=7001"));
        assertTrue(text.contains("Der Laufzettel ist als PDF angehängt."));
        assertTrue(text.contains("Diese Nachricht wurde automatisch von JEVis versendet."));
        assertFalse(text.contains("Ableser:"));
    }

    @Test
    public void reminderListsOpenMetersWithoutLinkAndSheet() {
        String text = RoundMails.build(REMINDER, round(), null, false).getText();
        assertTrue(text.contains("Offene Zähler:\n1. Wasser Halle 1\n"));
        assertFalse(text.contains("Hauptzähler"));
        assertTrue(text.contains("noch offen 2 von 4 Zählern"));
        assertFalse(text.contains("readings?round="));
        assertFalse(text.contains("Laufzettel"));
    }

    @Test
    public void escalationNamesTheReaders() {
        String text = RoundMails.build(ESCALATION, round(), "https://example.org/app", true).getText();
        assertTrue(text.contains("Ableser: Max Mustermann, #43"));
        assertTrue(text.contains("Readers: Max Mustermann, #43"));
        assertTrue(text.contains("seit 01.11.2026"));
    }

    @Test
    public void attachmentNameAndLink() {
        RoundMailInfo round = round();
        round.name = "Runde A/B: Halle";
        assertEquals("Laufzettel_Runde A_B_ Halle_2026-11-01.pdf", RoundMails.attachmentName(round));
        assertNull(RoundMails.link(" ", 1));
        assertEquals("https://example.org/app/readings?round=1", RoundMails.link("https://example.org/app", 1));
    }
}
