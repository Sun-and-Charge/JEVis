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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Builds the monthly demo energy invoices (ZUGFeRD / Factur-X PDF, profile EN16931) with neutral names. An invoice is
 * a list of {@link Line lines} (quantity × unit price, unit code of UN/ECE Rec. 20), one VAT rate, billing period = one
 * month. The lines of the demo cost cases are made by {@link InvoiceCalculator}; the old fixed pair (work price in kWh
 * and a monthly base price from the service object) is still available through the legacy {@link Spec} constructor.
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
        GAS("G", "Arbeitspreis Erdgas", "Erdgas"),
        DISTRICT_HEATING("W", "Arbeitspreis Fernwärme", "Fernwärme");

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
     * One invoice line: quantity × unit price, rounded to cents like the CII line total.
     */
    public static final class Line {
        final String text;
        final String unitCode;
        final BigDecimal quantity;
        final BigDecimal price;

        /**
         * @param text     line text
         * @param unitCode UN/ECE Rec. 20 unit code: KWH, KWT (kW), DAY, MON, ...
         * @param quantity quantity in that unit
         * @param price    net unit price in EUR
         */
        public Line(String text, String unitCode, BigDecimal quantity, BigDecimal price) {
            this.text = Objects.requireNonNull(text);
            this.unitCode = Objects.requireNonNull(unitCode);
            this.quantity = Objects.requireNonNull(quantity);
            this.price = Objects.requireNonNull(price);
        }

        public String getText() {
            return text;
        }

        public String getUnitCode() {
            return unitCode;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        /**
         * @return quantity × price rounded to cents (half up), as Mustang calculates the line total
         */
        public BigDecimal getNet() {
            return cents(quantity.multiply(price));
        }

        @Override
        public String toString() {
            return text + ": " + quantity.toPlainString() + " " + unitCode + " x " + price.stripTrailingZeros().toPlainString()
                    + " EUR = " + getNet().toPlainString() + " EUR";
        }
    }

    /**
     * Everything one invoice needs.
     */
    public static final class Spec {
        final Medium medium;
        final String number;
        final YearMonth period;
        final String seller;
        final String sellerVatId;
        final List<String> notes;
        final String buyerName;
        final LocalDate issueDate;
        final BigDecimal vatPercent;
        final List<Line> lines;
        final BigDecimal kWh;

        /**
         * The old invoice of the service attributes: work price line in kWh and a monthly base price, supplier
         * {@value #SUPPLIER}, 19 % VAT, number "EN-yyyyMM-S" / "-G".
         *
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
            this.number = number(medium, period);
            this.seller = SUPPLIER;
            this.sellerVatId = SUPPLIER_VAT_ID;
            this.kWh = Objects.requireNonNull(kWh).setScale(0, RoundingMode.HALF_UP);
            this.buyerName = buyer(buyerName);
            this.issueDate = Objects.requireNonNull(issueDate);
            this.vatPercent = VAT_PERCENT;
            List<Line> list = new ArrayList<>();
            list.add(new Line(medium.workPriceLine, "KWH", this.kWh, Objects.requireNonNull(pricePerKWh)));
            if (monthlyFee != null && monthlyFee.signum() > 0) list.add(new Line("Grundpreis", "MON", BigDecimal.ONE, monthlyFee));
            this.lines = Collections.unmodifiableList(list);
            List<String> n = new ArrayList<>();
            n.add(medium == Medium.ELECTRICITY ? "Zählpunkt " + ELECTRICITY_METER_POINT : "Marktlokation " + GAS_MARKET_LOCATION);
            n.add(demoNote(medium.label, period));
            this.notes = Collections.unmodifiableList(n);
        }

        /**
         * An invoice of a demo cost case.
         *
         * @param number     invoice number
         * @param notes      notes (meter point first), a "Demo-Rechnung" note is added
         * @param vatPercent VAT rate of all lines
         * @param lines      the lines, at least one
         * @param kWh        billed consumption in kWh (for the log), may be null
         */
        public Spec(Medium medium, String number, YearMonth period, String seller, String sellerVatId, List<String> notes,
                    String buyerName, LocalDate issueDate, BigDecimal vatPercent, List<Line> lines, BigDecimal kWh) {
            this.medium = Objects.requireNonNull(medium);
            this.number = Objects.requireNonNull(number);
            this.period = Objects.requireNonNull(period);
            this.seller = seller == null || seller.trim().isEmpty() ? SUPPLIER : seller.trim();
            this.sellerVatId = sellerVatId == null || sellerVatId.trim().isEmpty() ? SUPPLIER_VAT_ID : sellerVatId.trim();
            this.buyerName = buyer(buyerName);
            this.issueDate = Objects.requireNonNull(issueDate);
            this.vatPercent = Objects.requireNonNull(vatPercent);
            if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("An invoice needs at least one line");
            this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
            List<String> n = new ArrayList<>();
            if (notes != null) n.addAll(notes);
            n.add(demoNote(medium.label, period));
            this.notes = Collections.unmodifiableList(n);
            this.kWh = kWh;
        }

        private static String buyer(String name) {
            return name == null || name.trim().isEmpty() ? "Demo" : name.trim();
        }

        private static String demoNote(String label, YearMonth period) {
            return "Demo-Rechnung " + label + " " + String.format(Locale.ROOT, "%02d/%04d", period.getMonthValue(), period.getYear());
        }

        public Medium getMedium() {
            return medium;
        }

        public YearMonth getPeriod() {
            return period;
        }

        public String getNumber() {
            return number;
        }

        /**
         * @return billed consumption in kWh, null if unknown
         */
        public BigDecimal getKWh() {
            return kWh;
        }

        public List<Line> getLines() {
            return lines;
        }

        public BigDecimal getVatPercent() {
            return vatPercent;
        }

        public List<String> getNotes() {
            return notes;
        }

        public LocalDate getIssueDate() {
            return issueDate;
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
        return number(period, medium.suffix);
    }

    /**
     * @return e.g. "EN-202610-SR"
     */
    public static String number(YearMonth period, String suffix) {
        return String.format(Locale.ROOT, "EN-%04d%02d-%s", period.getYear(), period.getMonthValue(), suffix);
    }

    public static String fileName(Spec spec) {
        return spec.number + ".pdf";
    }

    public static Amounts amounts(Spec spec) {
        BigDecimal net = BigDecimal.ZERO.setScale(2);
        for (Line line : spec.lines) net = net.add(line.getNet());
        BigDecimal vat = cents(net.multiply(spec.vatPercent).divide(HUNDRED));
        return new Amounts(net, vat);
    }

    static BigDecimal cents(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @return the Mustang invoice of the spec
     */
    public static Invoice invoice(Spec spec) {
        LocalDate first = spec.period.atDay(1);
        LocalDate last = spec.period.atEndOfMonth();

        TradeParty seller = new TradeParty(spec.seller, "Hafenstraße 1", "12345", "Nordstadt", "DE");
        seller.addVATID(spec.sellerVatId);
        seller.setEmail("rechnung@example.org");
        TradeParty buyer = new TradeParty(spec.buyerName, "Musterstraße 2", "54321", "Musterstadt", "DE");

        Invoice invoice = new Invoice()
                .setNumber(spec.number)
                .setIssueDate(date(spec.issueDate))
                .setDueDate(date(spec.issueDate.plusDays(PAYMENT_DAYS)))
                .setSender(seller)
                .setRecipient(buyer)
                .setDetailedDeliveryPeriod(date(first), date(last));
        for (String note : spec.notes) invoice.addNote(note);
        for (Line line : spec.lines) {
            invoice.addItem(new Item(new Product(line.text, "", line.unitCode, spec.vatPercent), line.price, line.quantity));
        }
        return invoice;
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
        lines.add(ascii(spec.seller));
        lines.add("Rechnung " + spec.number + " vom " + spec.issueDate);
        lines.add("Kunde: " + ascii(spec.buyerName));
        lines.add("Abrechnungszeitraum: " + spec.period.atDay(1) + " bis " + spec.period.atEndOfMonth());
        for (String note : spec.notes) lines.add(ascii(note));
        lines.add("");
        for (Line line : spec.lines) {
            lines.add(ascii(line.text) + ": " + line.quantity.toPlainString() + " " + unitName(line.unitCode) + " x "
                    + line.price.stripTrailingZeros().toPlainString() + " EUR = " + line.getNet().toPlainString() + " EUR");
        }
        lines.add("");
        lines.add("Netto " + amounts.getNet().toPlainString() + " EUR, USt " + spec.vatPercent.stripTrailingZeros().toPlainString()
                + " % " + amounts.getVat().toPlainString() + " EUR, Brutto " + amounts.getGross().toPlainString() + " EUR");
        lines.add("Demo-Rechnung, automatisch erzeugt.");

        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(14);
                content.newLineAtOffset(50, 740);
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

    private static String unitName(String code) {
        switch (code) {
            case "KWH":
                return "kWh";
            case "KWT":
                return "kW";
            case "DAY":
                return "Tage";
            case "MON":
                return "Monat";
            case "MTQ":
                return "m3";
            default:
                return code;
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
                case '³': sb.append('3'); break;
                case '×': sb.append('x'); break;
                case '–': sb.append('-'); break;
                default: sb.append(c >= 32 && c < 127 ? c : '?');
            }
        }
        return sb.toString();
    }
}
