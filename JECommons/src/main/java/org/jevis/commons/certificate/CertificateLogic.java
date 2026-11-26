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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joda.time.LocalDate;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation, status and reminders of energy certificates (tender point BA-07); pure functions, shared by the web
 * service, the Control Center and the JENotifier reminders.
 */
public final class CertificateLogic {

    /** state of a certificate on a day */
    public enum Status {
        /** no valid-until date */
        UNKNOWN,
        VALID,
        /** expires within the first reminder period (default 6 months) */
        EXPIRING,
        EXPIRED
    }

    /** energy efficiency classes of the GEG (residential buildings) */
    public static final List<String> ENERGY_CLASSES = list("A+", "A", "B", "C", "D", "E", "F", "G", "H");

    private static final Logger logger = LogManager.getLogger(CertificateLogic.class);
    private static final Gson GSON = new GsonBuilder().create();
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private CertificateLogic() {
    }

    public static String toJson(EnergyCertificate certificate) {
        return GSON.toJson(certificate);
    }

    /** @return the certificate, or null if the text is empty or not valid JSON */
    public static EnergyCertificate fromJson(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return GSON.fromJson(json, EnergyCertificate.class);
        } catch (Exception e) {
            logger.warn("Could not parse an energy certificate from '{}'", json, e);
            return null;
        }
    }

    public static LocalDate date(String iso) {
        if (iso == null || !DATE.matcher(iso.trim()).matches()) return null;
        try {
            return LocalDate.parse(iso.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** Status on {@code today}; "expiring" starts with the largest reminder period. */
    public static Status status(EnergyCertificate certificate, LocalDate today) {
        LocalDate until = date(certificate.getValidUntil());
        if (until == null) return Status.UNKNOWN;
        if (until.isBefore(today)) return Status.EXPIRED;
        int months = 0;
        for (Integer m : certificate.getReminderMonths()) if (m != null && m > months) months = m;
        return !until.minusMonths(months).isAfter(today) ? Status.EXPIRING : Status.VALID;
    }

    /**
     * The reminder due on {@code today} (months before expiry), or null. A reminder is due from its day on until the
     * certificate expires, unless it was already sent ({@code sentMonths}); after the last reminder nothing is due.
     */
    public static Integer dueReminder(EnergyCertificate certificate, LocalDate today, List<Integer> sentMonths) {
        LocalDate until = date(certificate.getValidUntil());
        if (until == null || until.isBefore(today)) return null;
        Integer due = null;
        for (Integer m : certificate.getReminderMonths()) {
            if (m == null || m < 0) continue;
            boolean reached = !until.minusMonths(m).isAfter(today);
            boolean sent = sentMonths != null && sentMonths.contains(m);
            /* the closest reached reminder wins (e.g. 1 month before also covers a missed 6-month reminder) */
            if (reached && !sent && (due == null || m < due)) due = m;
        }
        return due;
    }

    /** Problems of the entered data (field name and message key); empty if the certificate can be saved. */
    public static List<String[]> validate(EnergyCertificate c) {
        List<String[]> problems = new ArrayList<>();
        if (c.getType() == null) problems.add(new String[]{"type", "required"});
        if (c.getValidUntil() == null || date(c.getValidUntil()) == null) problems.add(new String[]{"validUntil", "date"});
        if (c.getIssued() != null && !c.getIssued().isEmpty() && date(c.getIssued()) == null) problems.add(new String[]{"issued", "date"});
        LocalDate issued = date(c.getIssued());
        LocalDate until = date(c.getValidUntil());
        if (issued != null && until != null && !until.isAfter(issued)) problems.add(new String[]{"validUntil", "order"});
        if (c.getEnergyClass() != null && !c.getEnergyClass().isEmpty() && !ENERGY_CLASSES.contains(c.getEnergyClass())) {
            problems.add(new String[]{"energyClass", "class"});
        }
        checkNotNegative(problems, "finalEnergy", c.getFinalEnergy());
        checkNotNegative(problems, "primaryEnergy", c.getPrimaryEnergy());
        checkNotNegative(problems, "heatConsumption", c.getHeatConsumption());
        checkNotNegative(problems, "electricityConsumption", c.getElectricityConsumption());
        checkNotNegative(problems, "referenceArea", c.getReferenceArea());
        checkNotNegative(problems, "co2", c.getCo2());
        for (Integer m : c.getReminderMonths()) {
            if (m == null || m < 0 || m > 24) {
                problems.add(new String[]{"reminderMonths", "range"});
                break;
            }
        }
        return problems;
    }

    /** Validity of a certificate after the GEG: ten years from the issue date (default for the form). */
    public static String defaultValidUntil(String issued) {
        LocalDate d = date(issued);
        return d == null ? null : d.plusYears(10).minusDays(1).toString();
    }

    private static void checkNotNegative(List<String[]> problems, String field, Double value) {
        if (value != null && (value < 0 || value.isNaN() || value.isInfinite())) problems.add(new String[]{field, "number"});
    }

    private static List<String> list(String... values) {
        List<String> l = new ArrayList<>();
        for (String v : values) l.add(v);
        return l;
    }
}
