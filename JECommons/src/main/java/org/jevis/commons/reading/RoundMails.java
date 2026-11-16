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

import org.jevis.commons.appointment.EntryFormat;
import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-text order, reminder and escalation mails of reading rounds (stage E8.3, docs/WEB-API.md), German first,
 * then English, as the appointment mails.
 */
public final class RoundMails {

    static final String SEPARATOR = "----------------------------------------";
    public static final String ATTACHMENT_TYPE = "application/pdf";

    private RoundMails() {
    }

    public static String periodDe(String period) {
        if (period == null) return "";
        switch (period) {
            case "P1W":
                return "wöchentlich";
            case "P2W":
                return "alle 2 Wochen";
            case "P1M":
                return "monatlich";
            case "P3M":
                return "vierteljährlich";
            case "P6M":
                return "halbjährlich";
            case "P1Y":
                return "jährlich";
            default:
                return period;
        }
    }

    public static String periodEn(String period) {
        if (period == null) return "";
        switch (period) {
            case "P1W":
                return "weekly";
            case "P2W":
                return "every 2 weeks";
            case "P1M":
                return "monthly";
            case "P3M":
                return "quarterly";
            case "P6M":
                return "half-yearly";
            case "P1Y":
                return "yearly";
            default:
                return period;
        }
    }

    public static String subject(RoundMailLogic.MailKind kind, RoundMailInfo round) {
        String due = EntryFormat.displayDate(round.due);
        switch (kind) {
            case ORDER:
                return "Ableseauftrag / Reading order: " + round.name + " - fällig am / due on " + due;
            case REMINDER:
                return "Erinnerung / Reminder: " + round.name + " - " + round.open() + " von / of " + round.total
                        + " Zählern offen / meters open";
            default:
                return "Eskalation / Escalation: " + round.name + " - seit " + due
                        + " nicht abgeschlossen / not completed";
        }
    }

    /**
     * @return the file name of the attached route sheet, {@code Laufzettel_<round>_<due>.pdf} (characters not
     * allowed in file names replaced)
     */
    public static String attachmentName(RoundMailInfo round) {
        String name = round.name != null ? round.name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_") : String.valueOf(round.id);
        return "Laufzettel_" + name + "_" + RoundMailLogic.key(round.due) + ".pdf";
    }

    /**
     * @return the link to the round in the web app, null without web app address
     */
    public static String link(String webAppUrl, long roundId) {
        if (webAppUrl == null || webAppUrl.trim().isEmpty()) return null;
        String base = webAppUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/readings?round=" + roundId;
    }

    /**
     * @return the meters listed in the mail: the order lists all readable meters not filled automatically, reminder
     * and escalation the readable ones still open
     */
    static List<RoundMailInfo.Meter> listedMeters(RoundMailLogic.MailKind kind, RoundMailInfo round) {
        List<RoundMailInfo.Meter> meters = new ArrayList<>();
        for (RoundMailInfo.Meter meter : round.meters) {
            if (!meter.readable || meter.automatic) continue;
            if (kind != RoundMailLogic.MailKind.ORDER && meter.done) continue;
            meters.add(meter);
        }
        return meters;
    }

    private static int unreadable(RoundMailInfo round) {
        int count = 0;
        for (RoundMailInfo.Meter meter : round.meters) {
            if (!meter.readable) count++;
        }
        return count;
    }

    private static String people(List<RoundMailInfo.Person> people) {
        StringBuilder text = new StringBuilder();
        for (RoundMailInfo.Person person : people) {
            if (text.length() > 0) text.append(", ");
            text.append(person.name != null && !person.name.trim().isEmpty() ? person.name.trim() : "#" + person.id);
        }
        return text.toString();
    }

    /**
     * @param webAppUrl     the "Web App URL" of the JENotifier service, or null (no link)
     * @param withRouteSheet whether the route sheet is attached
     * @return the mail without recipients and attachment
     */
    public static MailMessage build(RoundMailLogic.MailKind kind, RoundMailInfo round, String webAppUrl, boolean withRouteSheet) {
        String due = EntryFormat.displayDate(round.due);
        String link = link(webAppUrl, round.id);
        List<RoundMailInfo.Meter> meters = listedMeters(kind, round);
        int unreadable = unreadable(round);
        boolean escalation = kind == RoundMailLogic.MailKind.ESCALATION;

        String introDe;
        String introEn;
        switch (kind) {
            case ORDER:
                introDe = "die folgende Ableserunde ist am " + due + " fällig. Bitte lesen Sie die aufgeführten Zähler ab und erfassen Sie die Zählerstände.";
                introEn = "the following reading round is due on " + due + ". Please read the meters listed and enter the readings.";
                break;
            case REMINDER:
                introDe = "die folgende Ableserunde war am " + due + " fällig und ist noch nicht abgeschlossen: noch offen "
                        + round.open() + " von " + round.total + " Zählern.";
                introEn = "the following reading round was due on " + due + " and is not completed yet: "
                        + round.open() + " of " + round.total + " meters still open.";
                break;
            default:
                introDe = "die folgende Ableserunde ist seit " + due + " fällig und noch nicht abgeschlossen: noch offen "
                        + round.open() + " von " + round.total + " Zählern. Sie erhalten diese Nachricht als Verantwortliche/r der Runde.";
                introEn = "the following reading round has been due since " + due + " and is not completed yet: "
                        + round.open() + " of " + round.total + " meters still open. You receive this message as supervisor of the round.";
                break;
        }

        StringBuilder text = new StringBuilder();
        text.append("Hallo,\n\n").append(introDe).append("\n\n");
        text.append("Runde: ").append(round.name).append('\n');
        if (!isBlank(round.siteName)) text.append("Standort: ").append(round.siteName).append('\n');
        text.append("Fällig: ").append(due).append('\n');
        text.append("Turnus: ").append(periodDe(round.period)).append('\n');
        if (escalation) text.append("Ableser: ").append(people(round.readers)).append('\n');
        if (!isBlank(round.remarks)) text.append("Bemerkung:\n").append(round.remarks.trim()).append('\n');
        text.append('\n').append(kind == RoundMailLogic.MailKind.ORDER ? "Zähler:" : "Offene Zähler:").append('\n');
        appendMeters(text, meters);
        if (unreadable > 0) text.append(unreadable).append(" Zähler ohne Leserecht nicht aufgeführt\n");
        text.append('\n');
        if (link != null) text.append("Ablesen in der Web-App: ").append(link).append('\n');
        if (withRouteSheet) text.append("Der Laufzettel ist als PDF angehängt.\n");
        text.append("Diese Nachricht wurde automatisch von JEVis versendet.\n\n");
        text.append(SEPARATOR).append("\n\n");

        text.append("Hello,\n\n").append(introEn).append("\n\n");
        text.append("Round: ").append(round.name).append('\n');
        if (!isBlank(round.siteName)) text.append("Site: ").append(round.siteName).append('\n');
        text.append("Due: ").append(due).append('\n');
        text.append("Period: ").append(periodEn(round.period)).append('\n');
        if (escalation) text.append("Readers: ").append(people(round.readers)).append('\n');
        if (!isBlank(round.remarks)) text.append("Remarks:\n").append(round.remarks.trim()).append('\n');
        text.append('\n').append(kind == RoundMailLogic.MailKind.ORDER ? "Meters:" : "Open meters:").append('\n');
        appendMeters(text, meters);
        if (unreadable > 0) text.append(unreadable).append(" meters without read permission not listed\n");
        text.append('\n');
        if (link != null) text.append("Read in the web app: ").append(link).append('\n');
        if (withRouteSheet) text.append("The route sheet is attached as PDF.\n");
        text.append("This message was sent automatically by JEVis.\n");

        return new MailMessage().subject(subject(kind, round)).text(text.toString(), false);
    }

    private static void appendMeters(StringBuilder text, List<RoundMailInfo.Meter> meters) {
        if (meters.isEmpty()) {
            text.append("-\n");
            return;
        }
        int position = 1;
        for (RoundMailInfo.Meter meter : meters) {
            text.append(position++).append(". ").append(meter.name != null ? meter.name : "#" + meter.id);
            if (!isBlank(meter.location)) text.append(" (").append(meter.location.trim()).append(')');
            text.append('\n');
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
