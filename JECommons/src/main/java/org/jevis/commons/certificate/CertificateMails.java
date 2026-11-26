/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.certificate;

import org.jevis.commons.mail.MailMessage;
import org.joda.time.LocalDate;

/**
 * Plain-text mails of the energy certificates (BA-07): the reminder before expiry and the transfer mail to a third
 * party; German first, then English (as the appointment mails).
 */
public final class CertificateMails {

    static final String SEPARATOR = "----------------------------------------";

    private CertificateMails() {
    }

    /**
     * @param months    the reminder (months before expiry) that is due
     * @param webAppUrl link to the overview, null if not configured
     * @return the mail without recipients
     */
    public static MailMessage reminder(EnergyCertificate c, String building, int months, LocalDate today, String webAppUrl) {
        LocalDate until = CertificateLogic.date(c.getValidUntil());
        String date = until != null ? until.toString("dd.MM.yyyy") : "?";
        boolean expired = until != null && until.isBefore(today);
        String typeDe = c.getType() == EnergyCertificate.Type.DEMAND ? "Bedarfsausweis" : "Verbrauchsausweis";
        String typeEn = c.getType() == EnergyCertificate.Type.DEMAND ? "demand certificate" : "consumption certificate";
        String subject = "Energieausweis / Energy certificate " + building + ": gültig bis / valid until " + date;

        StringBuilder text = new StringBuilder();
        text.append("Hallo,\n\n");
        text.append(expired ? "der folgende Energieausweis ist am " + date + " abgelaufen."
                : "der folgende Energieausweis läuft am " + date + " ab" + (months > 0 ? " (Erinnerung " + months + (months == 1 ? " Monat" : " Monate") + " vorher)" : "") + ".");
        text.append(" Bitte veranlassen Sie rechtzeitig einen neuen Ausweis und hinterlegen Sie ihn in JEVis.\n\n");
        text.append("Gebäude: ").append(building).append('\n');
        text.append("Art: ").append(typeDe).append('\n');
        if (!blank(c.getRegistrationNumber())) text.append("Registriernummer: ").append(c.getRegistrationNumber()).append('\n');
        if (!blank(c.getIssuer())) text.append("Aussteller: ").append(c.getIssuer()).append('\n');
        text.append("Gültig bis: ").append(date).append('\n');
        if (!blank(webAppUrl)) text.append("\nÜbersicht: ").append(link(webAppUrl)).append('\n');
        text.append("\nDiese Nachricht wurde automatisch von JEVis versendet.\n\n");
        text.append(SEPARATOR).append("\n\n");
        text.append("Hello,\n\n");
        text.append(expired ? "the following energy certificate expired on " + date + "."
                : "the following energy certificate expires on " + date + ".");
        text.append(" Please arrange a new certificate in time and store it in JEVis.\n\n");
        text.append("Building: ").append(building).append('\n');
        text.append("Type: ").append(typeEn).append('\n');
        if (!blank(c.getRegistrationNumber())) text.append("Registration number: ").append(c.getRegistrationNumber()).append('\n');
        text.append("Valid until: ").append(date).append('\n');
        if (!blank(webAppUrl)) text.append("\nOverview: ").append(link(webAppUrl)).append('\n');
        text.append("\nThis message was sent automatically by JEVis.\n");
        return new MailMessage().subject(subject).text(text.toString(), false);
    }

    /**
     * The mail of a transfer with the export attached.
     */
    public static MailMessage transfer(String targetName, int count, String fileName, byte[] content, String contentType) {
        String subject = "Energieausweise / Energy certificates (" + count + ")";
        String text = "Hallo,\n\nanbei die Energieausweise (" + count + ") aus JEVis, Übermittlung \"" + targetName + "\".\n"
                + "Diese Nachricht wurde automatisch von JEVis versendet.\n\n" + SEPARATOR + "\n\n"
                + "Hello,\n\nattached are the energy certificates (" + count + ") from JEVis, transfer \"" + targetName + "\".\n"
                + "This message was sent automatically by JEVis.\n";
        return new MailMessage().subject(subject).text(text, false).attach(fileName, content, contentType);
    }

    private static String link(String webAppUrl) {
        String base = webAppUrl.trim();
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/certificates";
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
