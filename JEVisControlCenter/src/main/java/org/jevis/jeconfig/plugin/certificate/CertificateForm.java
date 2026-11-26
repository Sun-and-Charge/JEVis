/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.certificate;

import javafx.scene.control.*;
import org.jevis.api.JEVisDataSource;
import org.jevis.commons.certificate.CertificateLogic;
import org.jevis.commons.certificate.EnergyCertificate;
import org.jevis.jeconfig.application.ui.FormLayout;
import org.jevis.jeconfig.plugin.appointment.ObjectListEditor;

import java.time.LocalDate;
import java.util.*;

import static org.jevis.jeconfig.application.navigation.PluginSidebar.text;

/**
 * The editor of an energy certificate (BA-07) for the side panel: sections Ausweis, Kennwerte, Gültigkeit und
 * Erinnerung, Datei, Anmerkungen; same checks as the web app and the server ({@link CertificateLogic#validate}).
 */
class CertificateForm {

    static final String[] NUMBER_FIELDS = {"heatConsumption", "electricityConsumption", "finalEnergy", "primaryEnergy", "referenceArea", "co2"};

    private final FormLayout form = new FormLayout();
    private final ComboBox<EnergyCertificate.Type> type = new ComboBox<>();
    private final ComboBox<EnergyCertificate.Use> use = new ComboBox<>();
    private final TextField registration = new TextField();
    private final TextField issuer = new TextField();
    private final ComboBox<String> energyClass = new ComboBox<>();
    private final Map<String, TextField> numbers = new LinkedHashMap<>();
    private final DatePicker issued = new DatePicker();
    private final DatePicker validUntil = new DatePicker();
    private final TextField reminders = new TextField();
    private final ObjectListEditor responsible;
    private final TextArea note = new TextArea();
    private boolean untilTouched;

    CertificateForm(JEVisDataSource ds, EnergyCertificate c, FormLayout.FileRow fileRow, String lastReminder) {
        type.getItems().addAll(EnergyCertificate.Type.values());
        type.setConverter(converter(CertificatePlugin::typeText));
        type.getSelectionModel().select(c.getType() != null ? c.getType() : EnergyCertificate.Type.CONSUMPTION);
        use.getItems().add(null);
        use.getItems().addAll(EnergyCertificate.Use.values());
        use.setConverter(converter(u -> u == EnergyCertificate.Use.RESIDENTIAL ? text("plugin.certificates.use.residential", "Wohngebäude", "Residential building")
                : text("plugin.certificates.use.nonresidential", "Nichtwohngebäude", "Non-residential building")));
        use.getSelectionModel().select(c.getUse());
        registration.setText(nz(c.getRegistrationNumber()));
        issuer.setText(nz(c.getIssuer()));
        energyClass.getItems().add("");
        energyClass.getItems().addAll(CertificateLogic.ENERGY_CLASSES);
        energyClass.getSelectionModel().select(nz(c.getEnergyClass()));
        for (String f : NUMBER_FIELDS) {
            TextField field = new TextField(number(value(c, f)));
            field.setPromptText(f.equals("referenceArea") ? "m²" : f.equals("co2") ? "kg/(m²·a)" : "kWh/(m²·a)");
            numbers.put(f, field);
        }
        issued.setValue(local(c.getIssued()));
        validUntil.setValue(local(c.getValidUntil()));
        untilTouched = c.getValidUntil() != null;
        issued.valueProperty().addListener((o, a, b) -> {
            if (!untilTouched && b != null) validUntil.setValue(b.plusYears(10).minusDays(1));
        });
        validUntil.getEditor().textProperty().addListener((o, a, b) -> {
            if (validUntil.isFocused()) untilTouched = true;
        });
        StringBuilder r = new StringBuilder();
        for (Integer m : c.getReminderMonths()) r.append(r.length() > 0 ? ", " : "").append(m);
        reminders.setText(r.toString());
        responsible = new ObjectListEditor(ds, new ArrayList<>(c.getResponsible()));
        note.setText(nz(c.getNote()));
        note.setPrefRowCount(4);
        note.setWrapText(true);

        form.section(text("plugin.certificates.section.certificate", "Ausweis", "Certificate"))
                .add(text("plugin.certificates.type", "Art des Ausweises", "Type of certificate"), type)
                .add(text("plugin.certificates.use", "Gebäudenutzung", "Use of the building"), use)
                .add(text("plugin.certificates.registration", "Registriernummer", "Registration number"), registration)
                .add(text("plugin.certificates.issuer", "Aussteller", "Issuer"), issuer);
        FormLayout.Section values = form.section(text("plugin.certificates.section.values", "Kennwerte", "Values"))
                .add(text("plugin.certificates.class", "Effizienzklasse", "Efficiency class"), energyClass);
        for (String f : NUMBER_FIELDS) values.add(fieldLabel(f), numbers.get(f));
        /* as in the web app: consumption certificates show the consumption values, demand certificates the demand values */
        type.valueProperty().addListener((o, a, b) -> showTypeFields());
        showTypeFields();
        FormLayout.Section validity = form.section(text("plugin.certificates.section.validity", "Gültigkeit und Erinnerung", "Validity and reminder"))
                .add(text("plugin.certificates.issued", "Ausgestellt am", "Issued on"), issued)
                .add(text("plugin.certificates.validuntil", "Gültig bis (Vorschlag: 10 Jahre)", "Valid until (suggestion: 10 years)"), validUntil)
                .addWide(text("plugin.certificates.reminders", "Erinnerung (Monate vor Ablauf, z. B. 6, 1)", "Reminder (months before expiry, e.g. 6, 1)"), reminders)
                .addWide(text("plugin.certificates.responsible", "Zuständig (Erinnerungen per E-Mail)", "Responsible (reminders by e-mail)"), responsible);
        if (lastReminder != null) validity.addWide(text("plugin.certificates.lastreminder", "Letzte Erinnerung", "Last reminder"), new Label(lastReminder));
        form.section(text("plugin.certificates.section.file", "Datei", "File"))
                .addWide(text("plugin.certificates.pdf", "Energieausweis als PDF", "Energy certificate as PDF"), fileRow != null ? fileRow
                        : new Label(text("plugin.certificates.file.aftersave", "Das PDF kann nach dem Anlegen hochgeladen werden.", "The PDF can be uploaded once the certificate is created.")));
        form.section(text("plugin.certificates.section.note", "Anmerkungen", "Notes"))
                .addWide(text("plugin.certificates.note", "Anmerkungen", "Notes"), note);
    }

    /** hides the value fields that do not fit the type and places the others again without gaps (two columns) */
    private void showTypeFields() {
        boolean demand = type.getValue() == EnergyCertificate.Type.DEMAND;
        List<javafx.scene.Node> cells = new ArrayList<>();
        cells.add(energyClass.getParent());
        for (String f : NUMBER_FIELDS) {
            javafx.scene.Node cell = numbers.get(f).getParent();
            boolean show = !demand || !(f.equals("heatConsumption") || f.equals("electricityConsumption"));
            cell.setVisible(show);
            cell.setManaged(show);
            if (show) cells.add(cell);
        }
        for (int i = 0; i < cells.size(); i++) {
            javafx.scene.layout.GridPane.setColumnIndex(cells.get(i), i % 2);
            javafx.scene.layout.GridPane.setRowIndex(cells.get(i), i / 2);
        }
    }

    FormLayout getForm() {
        return form;
    }

    static String fieldLabel(String f) {
        switch (f) {
            case "heatConsumption":
                return text("plugin.certificates.heat", "Verbrauchskennwert Wärme (kWh/(m²·a))", "Heat consumption (kWh/(m²·a))");
            case "electricityConsumption":
                return text("plugin.certificates.electricity", "Verbrauchskennwert Strom (kWh/(m²·a))", "Electricity consumption (kWh/(m²·a))");
            case "finalEnergy":
                return text("plugin.certificates.final", "Endenergie (kWh/(m²·a))", "Final energy (kWh/(m²·a))");
            case "primaryEnergy":
                return text("plugin.certificates.primary", "Primärenergie (kWh/(m²·a))", "Primary energy (kWh/(m²·a))");
            case "referenceArea":
                return text("plugin.certificates.area", "Bezugsfläche (m²)", "Reference area (m²)");
            default:
                return text("plugin.certificates.co2", "CO₂-Emissionen (kg/(m²·a))", "CO₂ emissions (kg/(m²·a))");
        }
    }

    /**
     * Writes the form into the certificate.
     *
     * @return the first problem as text, null if the certificate can be saved
     */
    String apply(EnergyCertificate c) {
        c.setType(type.getValue());
        c.setUse(use.getValue());
        c.setRegistrationNumber(blankToNull(registration.getText()));
        c.setIssuer(blankToNull(issuer.getText()));
        c.setEnergyClass(blankToNull(energyClass.getValue()));
        c.setIssued(issued.getValue() != null ? issued.getValue().toString() : null);
        c.setValidUntil(validUntil.getValue() != null ? validUntil.getValue().toString() : null);
        c.setNote(blankToNull(note.getText()));
        c.setResponsible(responsible.getIds());
        String problem = null;
        for (String f : NUMBER_FIELDS) {
            String t = numbers.get(f).getText() != null ? numbers.get(f).getText().trim() : "";
            Double v = null;
            if (!t.isEmpty()) {
                try {
                    v = Double.parseDouble(t.replace(".", "").replace(',', '.'));
                    if (t.contains(".") && !t.contains(",")) v = Double.parseDouble(t);
                } catch (NumberFormatException e) {
                    if (problem == null) problem = fieldLabel(f) + ": " + text("plugin.certificates.error.number", "keine gültige Zahl", "not a valid number");
                }
            }
            setValue(c, f, v);
        }
        List<Integer> months = new ArrayList<>();
        for (String part : (reminders.getText() != null ? reminders.getText() : "").split("[,;\\s]+")) {
            if (part.isEmpty()) continue;
            try {
                int m = Integer.parseInt(part);
                if (!months.contains(m)) months.add(m);
            } catch (NumberFormatException e) {
                months.add(-1);
            }
        }
        months.sort(Comparator.reverseOrder());
        c.setReminderMonths(months.isEmpty() ? null : months);
        if (problem != null) return problem;
        List<String[]> problems = CertificateLogic.validate(c);
        return problems.isEmpty() ? null : problemText(problems.get(0));
    }

    static String problemText(String[] p) {
        switch (p[1]) {
            case "required":
                return text("plugin.certificates.error.type", "Bitte die Art des Ausweises wählen.", "Please choose the type of certificate.");
            case "date":
                return text("plugin.certificates.error.date", "Bitte ein gültiges Datum für „Gültig bis“ eingeben.", "Please enter a valid \"valid until\" date.");
            case "order":
                return text("plugin.certificates.error.order", "„Gültig bis“ muss nach dem Ausstellungsdatum liegen.", "\"Valid until\" must be after the issue date.");
            case "class":
                return text("plugin.certificates.error.class", "Bitte eine Klasse von A+ bis H wählen.", "Please choose a class from A+ to H.");
            case "range":
                return text("plugin.certificates.error.range", "Erinnerung: Monate von 0 bis 24, getrennt durch Komma.", "Reminder: months from 0 to 24, separated by commas.");
            default:
                return fieldLabel(p[0]) + ": " + text("plugin.certificates.error.number", "keine gültige Zahl", "not a valid number");
        }
    }

    static Double value(EnergyCertificate c, String f) {
        switch (f) {
            case "heatConsumption":
                return c.getHeatConsumption();
            case "electricityConsumption":
                return c.getElectricityConsumption();
            case "finalEnergy":
                return c.getFinalEnergy();
            case "primaryEnergy":
                return c.getPrimaryEnergy();
            case "referenceArea":
                return c.getReferenceArea();
            default:
                return c.getCo2();
        }
    }

    private static void setValue(EnergyCertificate c, String f, Double v) {
        switch (f) {
            case "heatConsumption":
                c.setHeatConsumption(v);
                break;
            case "electricityConsumption":
                c.setElectricityConsumption(v);
                break;
            case "finalEnergy":
                c.setFinalEnergy(v);
                break;
            case "primaryEnergy":
                c.setPrimaryEnergy(v);
                break;
            case "referenceArea":
                c.setReferenceArea(v);
                break;
            default:
                c.setCo2(v);
        }
    }

    static String number(Double v) {
        if (v == null) return "";
        String s = new java.text.DecimalFormat("0.###", java.text.DecimalFormatSymbols.getInstance(Locale.GERMANY)).format(v);
        return s;
    }

    private static LocalDate local(String iso) {
        org.joda.time.LocalDate d = CertificateLogic.date(iso);
        return d != null ? LocalDate.of(d.getYear(), d.getMonthOfYear(), d.getDayOfMonth()) : null;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    private static <T> javafx.util.StringConverter<T> converter(java.util.function.Function<T, String> f) {
        return new javafx.util.StringConverter<T>() {
            @Override
            public String toString(T object) {
                return object != null ? f.apply(object) : "–";
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }
}
