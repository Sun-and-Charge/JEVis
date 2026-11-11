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
package org.jevis.jedemo;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the monthly demo energy invoices (ZUGFeRD / Factur-X PDF, profile EN16931) with neutral names: supplier
 * "Energieversorger Nord", a work price line in kWh and a monthly base price, 19 % VAT, billing period = one month.
 */
public final class InvoiceBuilder {

    public static final String SUPPLIER = "Energieversorger Nord";
    public static final String SUPPLIER_VAT_ID = "DE000000000";
    /**
     * Zählpunkt of the demo electricity meter: "DE" + 31 characters.
     */
    public static final String ELECTRICITY_METER_POINT = "DE0000000000000000000000000000001";
    /**
     * Marktlokation of the demo gas meter (11 digits).
     */
    public static final String GAS_MARKET_LOCATION = "50000000001";
    public static final BigDecimal VAT_PERCENT = new BigDecimal("19");
    public static final int PAYMENT_DAYS = 14;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private InvoiceBuilder() {
    }

    /**
     * Medium of an invoice, with the medium name of the invoice import ({@code ?medium=}).
     */
    public enum Medium {
        ELECTRICITY("S", "Arbeitspreis Strom", "Strom"),
        GAS("G", "Arbeitspreis Erdgas", "Erdgas");

        private final String suffix;
        private final String workPriceLine;
        private final String label;

        Medium(String suffix, String workPriceLine, String label) {
            this.suffix = suffix;
            this.workPriceLine = workPriceLine;
            this.label = label;
        }

        public String getWorkPriceLine() {
            return workPriceLine;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * Everything one invoice needs.
     */
    public static final class Spec {
        final Medium medium;
        final YearMonth period;
        final BigDecimal kWh;
        final BigDecimal pricePerKWh;
        final BigDecimal monthlyFee;
        final String buyerName;
        final LocalDate issueDate;

        /**
         * @param medium      electricity or gas
         * @param period      billing month
         * @param kWh         consumption of the month (rounded to whole kWh)
         * @param pricePerKWh net work price in EUR/kWh
         * @param monthlyFee  net base price in EUR per month (no line if null or 0)
         * @param buyerName   name of the demo organisation
         * @param issueDate   invoice date, due date is {@value #PAYMENT_DAYS} days later
         */
        public Spec(Medium medium, YearMonth period, BigDecimal kWh, BigDecimal pricePerKWh, BigDecimal monthlyFee,
                    String buyerName, LocalDate issueDate) {
            this.medium = Objects.requireNonNull(medium);
            this.period = Objects.requireNonNull(period);
            this.kWh = Objects.requireNonNull(kWh).setScale(0, RoundingMode.HALF_UP);
            this.pricePerKWh = Objects.requireNonNull(pricePerKWh);
            this.monthlyFee = monthlyFee != null && monthlyFee.signum() > 0 ? monthlyFee : null;
            this.buyerName = buyerName == null || buyerName.trim().isEmpty() ? "Demo" : buyerName.trim();
            this.issueDate = Objects.requireNonNull(issueDate);
        }

        public Medium getMedium() {
            return medium;
        }

        public YearMonth getPeriod() {
            return period;
        }

        public BigDecimal getKWh() {
            return kWh;
        }
    }

    /**
     * Net, VAT and gross of an invoice, rounded to cents like the CII calculation (line totals rounded, VAT on the
     * sum of the lines).
     */
    public static final class Amounts {
        private final BigDecimal net;
        private final BigDecimal vat;
        private final BigDecimal gross;

        Amounts(BigDecimal net, BigDecimal vat) {
            this.net = net;
            this.vat = vat;
            this.gross = net.add(vat);
        }

        public BigDecimal getNet() {
            return net;
        }

        public BigDecimal getVat() {
            return vat;
        }

        public BigDecimal getGross() {
            return gross;
        }

        @Override
        public String toString() {
            return "net " + net + " EUR, VAT " + vat + " EUR, gross " + gross + " EUR";
        }
    }

    /**
     * @return e.g. "EN-202610-S" (electricity) or "EN-202610-G" (gas)
     */
    public static String number(Medium medium, YearMonth period) {
        return String.format(Locale.ROOT, "EN-%04d%02d-%s", period.getYear(), period.getMonthValue(), medium.suffix);
    }

    public static String fileName(Spec spec) {
        return number(spec.medium, spec.period) + ".pdf";
    }

    public static Amounts amounts(Spec spec) {
        BigDecimal net = cents(spec.kWh.multiply(spec.pricePerKWh));
        if (spec.monthlyFee != null) net = net.add(cents(spec.monthlyFee));
        BigDecimal vat = cents(net.multiply(VAT_PERCENT).divide(HUNDRED));
        return new Amounts(net, vat);
    }

    private static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @return the Mustang invoice of the spec
     */
    public static Invoice invoice(Spec spec) {
        LocalDate first = spec.period.atDay(1);
        LocalDate last = spec.period.atEndOfMonth();

        TradeParty seller = new TradeParty(SUPPLIER, "Hafenstraße 1", "12345", "Nordstadt", "DE");
        seller.addVATID(SUPPLIER_VAT_ID);
        seller.setEmail("rechnung@example.org");
        TradeParty buyer = new TradeParty(spec.buyerName, "Musterstraße 2", "54321", "Musterstadt", "DE");

        Invoice invoice = new Invoice()
                .setNumber(number(spec.medium, spec.period))
                .setIssueDate(date(spec.issueDate))
                .setDueDate(date(spec.issueDate.plusDays(PAYMENT_DAYS)))
                .setSender(seller)
                .setRecipient(buyer)
                .setDetailedDeliveryPeriod(date(first), date(last))
                .addNote(spec.medium == Medium.ELECTRICITY
                        ? "Zählpunkt " + ELECTRICITY_METER_POINT
                        : "Marktlokation " + GAS_MARKET_LOCATION)
                .addNote("Demo-Rechnung " + spec.medium.label + " " + String.format(Locale.ROOT, "%02d/%04d",
                        spec.period.getMonthValue(), spec.period.getYear()));
        invoice.addItem(item(spec.medium.workPriceLine, "KWH", spec.pricePerKWh, spec.kWh));
        if (spec.monthlyFee != null) {
            invoice.addItem(item("Grundpreis", "MON", spec.monthlyFee, BigDecimal.ONE));
        }
        return invoice;
    }

    private static Item item(String name, String unit, BigDecimal price, BigDecimal quantity) {
        return new Item(new Product(name, "", unit, VAT_PERCENT), price, quantity);
    }

    /**
     * Mustang writes dates in the default time zone of the JVM, so the day is converted in that zone.
     */
    private static Date date(LocalDate day) {
        return Date.from(day.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /**
     * @return the ZUGFeRD / Factur-X PDF (PDF/A-3 with embedded factur-x.xml, EN16931)
     */
    public static byte[] pdf(Spec spec) throws Exception {
        Invoice invoice = invoice(spec);
        try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
            exporter.ignorePDFAErrors()
                    .load(visiblePdf(spec))
                    .setProducer("JEVis JEDemo")
                    .setCreator("JEVis JEDemo")
                    .setProfile(Profiles.getByName("EN16931"));
            exporter.setTransaction(invoice);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(out);
            return out.toByteArray();
        }
    }

    /**
     * A one-page PDF with the main invoice data as text, so the file shows something when opened.
     */
    static byte[] visiblePdf(Spec spec) throws IOException {
        Amounts amounts = amounts(spec);
        List<String> lines = new ArrayList<>();
        lines.add(SUPPLIER);
        lines.add("Rechnung " + number(spec.medium, spec.period) + " vom " + spec.issueDate);
        lines.add("Kunde: " + ascii(spec.buyerName));
        lines.add("Abrechnungszeitraum: " + spec.period.atDay(1) + " bis " + spec.period.atEndOfMonth());
        lines.add(spec.medium == Medium.ELECTRICITY ? "Zaehlpunkt " + ELECTRICITY_METER_POINT
                : "Marktlokation " + GAS_MARKET_LOCATION);
        lines.add(ascii(spec.medium.workPriceLine) + ": " + spec.kWh.toPlainString() + " kWh x "
                + spec.pricePerKWh.toPlainString() + " EUR/kWh");
        if (spec.monthlyFee != null) lines.add("Grundpreis: " + cents(spec.monthlyFee).toPlainString() + " EUR");
        lines.add("Netto " + amounts.getNet().toPlainString() + " EUR, USt 19 % " + amounts.getVat().toPlainString()
                + " EUR, Brutto " + amounts.getGross().toPlainString() + " EUR");
        lines.add("Demo-Rechnung, automatisch erzeugt.");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.setLeading(16);
                content.newLineAtOffset(60, 720);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /**
     * The standard 14 fonts only cover WinAnsi; replace what they cannot show.
     */
    private static String ascii(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            switch (c) {
                case 'ä': sb.append("ae"); break;
                case 'ö': sb.append("oe"); break;
                case 'ü': sb.append("ue"); break;
                case 'Ä': sb.append("Ae"); break;
                case 'Ö': sb.append("Oe"); break;
                case 'Ü': sb.append("Ue"); break;
                case 'ß': sb.append("ss"); break;
                default: sb.append(c >= 32 && c < 127 ? c : '?');
            }
        }
        return sb.toString();
    }
}
