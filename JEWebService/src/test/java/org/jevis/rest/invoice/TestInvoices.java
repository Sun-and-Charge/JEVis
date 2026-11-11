/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.rest.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.mustangproject.Invoice;
import org.mustangproject.Item;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.Profiles;
import org.mustangproject.ZUGFeRD.ZUGFeRD2PullProvider;
import org.mustangproject.ZUGFeRD.ZUGFeRDExporterFromA3;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * Test invoices with neutral names, generated with Mustang itself (UBL written by hand, Mustang cannot write UBL
 * invoices without the CII to UBL converter that is left out of the build).
 */
final class TestInvoices {

    /** a Zählpunkt: "DE" + 31 characters */
    static final String METER_POINT = "DE0000000000000000000000000000001";
    static final String MARKET_LOCATION = "51234567890";

    private TestInvoices() {
    }

    static Date date(int year, int month, int day) {
        return Date.from(LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    static TradeParty seller(String name, String vatId) {
        TradeParty party = new TradeParty(name, "Street 1", "12345", "Town", "DE");
        if (vatId != null) party.addVATID(vatId);
        party.setEmail("invoice@example.org");
        return party;
    }

    static TradeParty buyer() {
        return new TradeParty("Customer B", "Road 2", "54321", "City", "DE");
    }

    static Item item(String name, String unit, String price, String quantity) {
        return new Item(new Product(name, "", unit, new BigDecimal(19)), new BigDecimal(price), new BigDecimal(quantity));
    }

    /**
     * Electricity: work price HT/NT in kWh, grid fee and electricity tax for the same consumption, base fee per
     * month; meter point in a note; billing period September 2026.
     */
    static Invoice electricity(String number) {
        return new Invoice()
                .setNumber(number)
                .setIssueDate(date(2026, 10, 5))
                .setDueDate(date(2026, 10, 19))
                .setSender(seller("Supplier A", "DE000000000"))
                .setRecipient(buyer())
                .setReferenceNumber("04011000-12345-03")
                .setDetailedDeliveryPeriod(date(2026, 9, 1), date(2026, 9, 30))
                .addNote("Zählpunkt " + METER_POINT)
                .addItem(item("Arbeitspreis Strom HT", "KWH", "0.25", "3000"))
                .addItem(item("Arbeitspreis Strom NT", "KWH", "0.20", "1321"))
                .addItem(item("Netzentgelt Arbeitspreis", "KWH", "0.08", "4321"))
                .addItem(item("Stromsteuer", "KWH", "0.0205", "4321"))
                .addItem(item("Grundpreis", "MON", "12.50", "1"));
    }

    /**
     * Gas: work price in kWh, the consumption in m³ as information (price 0), base fee per day; market location in a
     * line note; billing period only in the lines.
     */
    static Invoice gas(String number) {
        Item consumption = item("Erdgas Arbeitspreis", "KWH", "0.09", "10000");
        consumption.setDetailedDeliveryPeriod(date(2026, 1, 1), date(2026, 3, 31));
        consumption.addNote("Marktlokation: " + MARKET_LOCATION);
        Item volume = item("Verbrauch Gaszähler", "MTQ", "0", "950");
        volume.setDetailedDeliveryPeriod(date(2026, 1, 1), date(2026, 3, 31));
        return new Invoice()
                .setNumber(number)
                .setIssueDate(date(2026, 4, 10))
                .setDueDate(date(2026, 4, 24))
                .setDeliveryDate(date(2026, 3, 31))
                .setSender(seller("Supplier B", "DE000000002"))
                .setRecipient(buyer())
                .setReferenceNumber("04011000-12345-03")
                .addItem(consumption)
                .addItem(volume)
                .addItem(item("Grundpreis", "DAY", "0.50", "90"));
    }

    /** Gas in m³ only, seller without VAT ID. */
    static Invoice gasCubicMetres(String number) {
        return new Invoice()
                .setNumber(number)
                .setIssueDate(date(2026, 5, 3))
                .setDueDate(date(2026, 5, 17))
                .setSender(seller("Supplier D", null).addTaxID("000/000/00000"))
                .setRecipient(buyer())
                .setDetailedDeliveryPeriod(date(2026, 4, 1), date(2026, 4, 30))
                .addItem(item("Lieferung", "MTQ", "1.10", "300"))
                .addItem(item("Messstellenbetrieb", "C62", "5", "1"));
    }

    /** Neither unit nor text names a medium. */
    static Invoice unknownMedium(String number) {
        return new Invoice()
                .setNumber(number)
                .setIssueDate(date(2026, 6, 1))
                .setDueDate(date(2026, 6, 15))
                .setSender(seller("Supplier E", "DE000000005"))
                .setRecipient(buyer())
                .setDetailedDeliveryPeriod(date(2026, 5, 1), date(2026, 5, 31))
                .addItem(item("Service", "C62", "100", "1"));
    }

    /** CII XML of the invoice with a profile ("EN16931", "XRECHNUNG", ...). */
    static byte[] cii(Invoice invoice, String profile) {
        ZUGFeRD2PullProvider provider = new ZUGFeRD2PullProvider();
        provider.setProfile(Profiles.getByName(profile));
        provider.generateXML(invoice);
        return provider.getXML();
    }

    /** A simple one-page PDF without invoice. */
    static byte[] plainPdf() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            page.setResources(new PDResources());
            document.addPage(page);
            document.save(out);
            return out.toByteArray();
        }
    }

    /** ZUGFeRD / Factur-X PDF (PDF/A-3 with embedded factur-x.xml, EN16931). */
    static byte[] zugferdPdf(Invoice invoice) throws Exception {
        try (ZUGFeRDExporterFromA3 exporter = new ZUGFeRDExporterFromA3()) {
            exporter.ignorePDFAErrors()
                    .load(plainPdf())
                    .setProducer("JEVis test")
                    .setCreator("JEVis test")
                    .setProfile(Profiles.getByName("EN16931"));
            exporter.setTransaction(invoice);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            exporter.export(out);
            return out.toByteArray();
        }
    }

    /**
     * XRechnung 3.0 UBL, district heating: heat in MWh, base fee per month, market location as delivery location,
     * billing period January 2026; net 1330.00, tax 252.70, gross 1582.70.
     */
    static byte[] ublDistrictHeating(String number) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<ubl:Invoice xmlns:ubl=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">\n"
                + "<cbc:CustomizationID>urn:cen.eu:en16931:2017#compliant#urn:xeinkauf.de:kosit:xrechnung_3.0</cbc:CustomizationID>\n"
                + "<cbc:ProfileID>urn:fdc:peppol.eu:2017:poacc:billing:01:1.0</cbc:ProfileID>\n"
                + "<cbc:ID>" + number + "</cbc:ID>\n"
                + "<cbc:IssueDate>2026-02-10</cbc:IssueDate>\n"
                + "<cbc:DueDate>2026-02-24</cbc:DueDate>\n"
                + "<cbc:InvoiceTypeCode>380</cbc:InvoiceTypeCode>\n"
                + "<cbc:Note>Abrechnung Januar 2026</cbc:Note>\n"
                + "<cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>\n"
                + "<cbc:BuyerReference>04011000-12345-03</cbc:BuyerReference>\n"
                + "<cac:InvoicePeriod><cbc:StartDate>2026-01-01</cbc:StartDate><cbc:EndDate>2026-01-31</cbc:EndDate></cac:InvoicePeriod>\n"
                + "<cac:AccountingSupplierParty><cac:Party>"
                + "<cac:PartyName><cbc:Name>Supplier C</cbc:Name></cac:PartyName>"
                + "<cac:PostalAddress><cbc:StreetName>Street 1</cbc:StreetName><cbc:CityName>Town</cbc:CityName>"
                + "<cbc:PostalZone>12345</cbc:PostalZone><cac:Country><cbc:IdentificationCode>DE</cbc:IdentificationCode></cac:Country></cac:PostalAddress>"
                + "<cac:PartyTaxScheme><cbc:CompanyID>DE000000003</cbc:CompanyID><cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:PartyTaxScheme>"
                + "<cac:PartyLegalEntity><cbc:RegistrationName>Supplier C</cbc:RegistrationName></cac:PartyLegalEntity>"
                + "</cac:Party></cac:AccountingSupplierParty>\n"
                + "<cac:AccountingCustomerParty><cac:Party>"
                + "<cac:PartyName><cbc:Name>Customer B</cbc:Name></cac:PartyName>"
                + "<cac:PostalAddress><cbc:StreetName>Road 2</cbc:StreetName><cbc:CityName>City</cbc:CityName>"
                + "<cbc:PostalZone>54321</cbc:PostalZone><cac:Country><cbc:IdentificationCode>DE</cbc:IdentificationCode></cac:Country></cac:PostalAddress>"
                + "<cac:PartyLegalEntity><cbc:RegistrationName>Customer B</cbc:RegistrationName></cac:PartyLegalEntity>"
                + "</cac:Party></cac:AccountingCustomerParty>\n"
                + "<cac:Delivery><cac:DeliveryLocation><cbc:ID>" + MARKET_LOCATION + "</cbc:ID></cac:DeliveryLocation></cac:Delivery>\n"
                + "<cac:PaymentMeans><cbc:PaymentMeansCode>58</cbc:PaymentMeansCode>"
                + "<cac:PayeeFinancialAccount><cbc:ID>DE02120300000000202051</cbc:ID></cac:PayeeFinancialAccount></cac:PaymentMeans>\n"
                + "<cac:TaxTotal><cbc:TaxAmount currencyID=\"EUR\">252.70</cbc:TaxAmount>"
                + "<cac:TaxSubtotal><cbc:TaxableAmount currencyID=\"EUR\">1330.00</cbc:TaxableAmount>"
                + "<cbc:TaxAmount currencyID=\"EUR\">252.70</cbc:TaxAmount>"
                + "<cac:TaxCategory><cbc:ID>S</cbc:ID><cbc:Percent>19</cbc:Percent><cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:TaxCategory>"
                + "</cac:TaxSubtotal></cac:TaxTotal>\n"
                + "<cac:LegalMonetaryTotal><cbc:LineExtensionAmount currencyID=\"EUR\">1330.00</cbc:LineExtensionAmount>"
                + "<cbc:TaxExclusiveAmount currencyID=\"EUR\">1330.00</cbc:TaxExclusiveAmount>"
                + "<cbc:TaxInclusiveAmount currencyID=\"EUR\">1582.70</cbc:TaxInclusiveAmount>"
                + "<cbc:PayableAmount currencyID=\"EUR\">1582.70</cbc:PayableAmount></cac:LegalMonetaryTotal>\n"
                + line("1", "MWH", "12.5", "1250.00", "Wärmearbeitspreis", "100.00", true)
                + line("2", "MON", "1", "80.00", "Grundpreis", "80.00", false)
                + "</ubl:Invoice>\n";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private static String line(String id, String unit, String quantity, String net, String name, String price, boolean period) {
        return "<cac:InvoiceLine><cbc:ID>" + id + "</cbc:ID>"
                + "<cbc:InvoicedQuantity unitCode=\"" + unit + "\">" + quantity + "</cbc:InvoicedQuantity>"
                + "<cbc:LineExtensionAmount currencyID=\"EUR\">" + net + "</cbc:LineExtensionAmount>"
                + (period ? "<cac:InvoicePeriod><cbc:StartDate>2026-01-01</cbc:StartDate><cbc:EndDate>2026-01-31</cbc:EndDate></cac:InvoicePeriod>" : "")
                + "<cac:Item><cbc:Name>" + name + "</cbc:Name>"
                + "<cac:ClassifiedTaxCategory><cbc:ID>S</cbc:ID><cbc:Percent>19</cbc:Percent><cac:TaxScheme><cbc:ID>VAT</cbc:ID></cac:TaxScheme></cac:ClassifiedTaxCategory>"
                + "</cac:Item><cac:Price><cbc:PriceAmount currencyID=\"EUR\">" + price + "</cbc:PriceAmount></cac:Price>"
                + "</cac:InvoiceLine>\n";
    }
}
