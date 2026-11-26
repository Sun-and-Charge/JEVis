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
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export of energy certificates for third parties (BA-07 transfer): CSV (";", UTF-8 with BOM, German headers and
 * decimal comma, for spreadsheets), JSON (machine-readable, numbers as numbers) or ZIP (the CSV, the JSON and all
 * PDFs under {@code pdf/}). Pure; the caller collects the entries and the PDFs.
 */
public final class CertificateExport {

    public enum Format {
        CSV("csv", "text/csv; charset=UTF-8"),
        JSON("json", "application/json; charset=UTF-8"),
        ZIP("zip", "application/zip");

        public final String extension;
        public final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        /** @return the format of the text (case-insensitive), CSV if unknown */
        public static Format of(String text) {
            if (text != null) {
                for (Format f : values()) if (f.name().equalsIgnoreCase(text.trim())) return f;
            }
            return CSV;
        }
    }

    /** One certificate of an export. */
    public static class Entry {
        final long id;
        final String building;
        final EnergyCertificate certificate;
        final String fileName;
        final byte[] pdf;

        /**
         * @param fileName name of the PDF, null if none
         * @param pdf      the PDF, null if none (or not needed: CSV and JSON only list the name)
         */
        public Entry(long id, String building, EnergyCertificate certificate, String fileName, byte[] pdf) {
            this.id = id;
            this.building = building;
            this.certificate = certificate;
            this.fileName = fileName;
            this.pdf = pdf;
        }
    }

    static final String[] CSV_HEADER = {"JEVis-ID", "Gebäude", "Art", "Nutzung", "Registriernummer", "Ausgestellt",
            "Gültig bis", "Status", "Effizienzklasse", "Verbrauch Wärme kWh/(m²·a)", "Verbrauch Strom kWh/(m²·a)",
            "Endenergie kWh/(m²·a)", "Primärenergie kWh/(m²·a)", "Bezugsfläche m²", "CO₂ kg/(m²·a)", "Aussteller",
            "Anmerkungen", "PDF"};

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private CertificateExport() {
    }

    /** @return e.g. {@code Energieausweise_20261126-1500.zip} */
    public static String fileName(Format format, DateTime now) {
        return "Energieausweise_" + now.toString("yyyyMMdd-HHmm") + "." + format.extension;
    }

    public static byte[] build(Format format, List<Entry> entries, LocalDate today, DateTime now) throws IOException {
        switch (format) {
            case JSON:
                return json(entries, today, now).getBytes(StandardCharsets.UTF_8);
            case ZIP:
                return zip(entries, today, now);
            case CSV:
            default:
                return csv(entries, today).getBytes(StandardCharsets.UTF_8);
        }
    }

    public static String csv(List<Entry> entries, LocalDate today) {
        StringBuilder sb = new StringBuilder("﻿");
        line(sb, Arrays.asList(CSV_HEADER));
        DecimalFormat number = new DecimalFormat("0.###", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        for (Entry e : entries) {
            EnergyCertificate c = e.certificate;
            List<String> cells = new ArrayList<>();
            cells.add(String.valueOf(e.id));
            cells.add(e.building);
            cells.add(c.getType() == EnergyCertificate.Type.DEMAND ? "Bedarfsausweis" : c.getType() == EnergyCertificate.Type.CONSUMPTION ? "Verbrauchsausweis" : "");
            cells.add(c.getUse() == EnergyCertificate.Use.RESIDENTIAL ? "Wohngebäude" : c.getUse() == EnergyCertificate.Use.NON_RESIDENTIAL ? "Nichtwohngebäude" : "");
            cells.add(c.getRegistrationNumber());
            cells.add(c.getIssued());
            cells.add(c.getValidUntil());
            cells.add(statusText(CertificateLogic.status(c, today)));
            cells.add(c.getEnergyClass());
            for (Double v : Arrays.asList(c.getHeatConsumption(), c.getElectricityConsumption(), c.getFinalEnergy(),
                    c.getPrimaryEnergy(), c.getReferenceArea(), c.getCo2())) {
                cells.add(v != null ? number.format(v) : "");
            }
            cells.add(c.getIssuer());
            cells.add(c.getNote());
            cells.add(e.fileName != null ? pdfPath(e) : "");
            line(sb, cells);
        }
        return sb.toString();
    }

    public static String json(List<Entry> entries, LocalDate today, DateTime now) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("exported", now.toString());
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            EnergyCertificate c = e.certificate;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("building", e.building);
            m.put("status", CertificateLogic.status(c, today).name());
            m.put("type", c.getType() != null ? c.getType().name() : null);
            m.put("use", c.getUse() != null ? c.getUse().name() : null);
            m.put("registrationNumber", c.getRegistrationNumber());
            m.put("issued", c.getIssued());
            m.put("validUntil", c.getValidUntil());
            m.put("issuer", c.getIssuer());
            m.put("energyClass", c.getEnergyClass());
            m.put("heatConsumption", c.getHeatConsumption());
            m.put("electricityConsumption", c.getElectricityConsumption());
            m.put("finalEnergy", c.getFinalEnergy());
            m.put("primaryEnergy", c.getPrimaryEnergy());
            m.put("referenceArea", c.getReferenceArea());
            m.put("co2", c.getCo2());
            m.put("note", c.getNote());
            m.put("file", e.fileName != null ? pdfPath(e) : null);
            list.add(m);
        }
        root.put("certificates", list);
        return GSON.toJson(root);
    }

    public static byte[] zip(List<Entry> entries, LocalDate today, DateTime now) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "Energieausweise.csv", csv(entries, today).getBytes(StandardCharsets.UTF_8));
            put(zip, "Energieausweise.json", json(entries, today, now).getBytes(StandardCharsets.UTF_8));
            for (Entry e : entries) {
                if (e.fileName != null && e.pdf != null) put(zip, pdfPath(e), e.pdf);
            }
        }
        return out.toByteArray();
    }

    /** @return {@code pdf/<id>_<file name>}, the path of the PDF in the ZIP (also listed in CSV and JSON) */
    static String pdfPath(Entry e) {
        String name = e.fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
        return "pdf/" + e.id + "_" + name;
    }

    static String statusText(CertificateLogic.Status status) {
        switch (status) {
            case EXPIRED:
                return "abgelaufen";
            case EXPIRING:
                return "läuft ab";
            case VALID:
                return "gültig";
            default:
                return "ohne Datum";
        }
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static void line(StringBuilder sb, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(';');
            String s = cells.get(i) != null ? cells.get(i) : "";
            if (s.indexOf(';') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                s = '"' + s.replace("\"", "\"\"") + '"';
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }
}
