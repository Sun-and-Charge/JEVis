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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.mustangproject.FileAttachment;
import org.mustangproject.IncludedNote;
import org.mustangproject.Invoice;
import org.mustangproject.Product;
import org.mustangproject.TradeParty;
import org.mustangproject.ZUGFeRD.IZUGFeRDExportableItem;
import org.mustangproject.ZUGFeRD.LineCalculator;
import org.mustangproject.ZUGFeRD.ZUGFeRDInvoiceImporter;
import org.mustangproject.Item;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads ZUGFeRD / Factur-X PDFs (CII XML embedded) and XRechnung XML (CII or UBL) with the importer of Mustangproject
 * ({@link ZUGFeRDInvoiceImporter}). Header fields and lines come from Mustang's invoice model; totals, line totals,
 * line periods and line texts, which the model leaves out for UBL, are read from the XML with XPath.
 */
public final class InvoiceReader {

    private static final Logger logger = LogManager.getLogger(InvoiceReader.class);
    private static final byte[] PDF_SIGNATURE = {'%', 'P', 'D', 'F'};
    private static final Pattern XRECHNUNG_VERSION = Pattern.compile("xrechnung_(\\d+(\\.\\d+)?)");
    private static final String LINES = "//*[local-name()='IncludedSupplyChainTradeLineItem']|//*[local-name()='InvoiceLine']|//*[local-name()='CreditNoteLine']";

    private InvoiceReader() {
    }

    enum Syntax {CII, UBL}

    /**
     * @param bytes the file (PDF or XML)
     * @return the invoice
     * @throws InvoiceException NOT_AN_E_INVOICE or UNREADABLE (status 400)
     */
    public static EInvoice read(byte[] bytes) throws InvoiceException {
        if (bytes == null || bytes.length == 0) throw InvoiceException.unreadable("The file is empty.");
        boolean pdf = bytes.length >= 4 && Arrays.equals(Arrays.copyOf(bytes, 4), PDF_SIGNATURE);

        ZUGFeRDInvoiceImporter importer = new ZUGFeRDInvoiceImporter();
        importer.doIgnoreCalculationErrors();
        String attachmentName = null;
        try {
            if (pdf) {
                importer.setInputStream(new ByteArrayInputStream(bytes));
                if (importer.getMeta() == null) {
                    try (PDDocument ignored = Loader.loadPDF(bytes)) {
                        logger.debug("PDF without invoice XML");
                    } catch (Exception e) {
                        throw InvoiceException.unreadable("The PDF file cannot be read.");
                    }
                    throw InvoiceException.notAnEInvoice("The PDF contains no ZUGFeRD / Factur-X / XRechnung invoice.");
                }
                attachmentName = invoiceAttachmentName(importer.getFileAttachmentsPDF());
            } else {
                importer.setRawXML(bytes, false);
            }
        } catch (InvoiceException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            logger.warn("Could not read the invoice file", e);
            throw InvoiceException.unreadable("The invoice cannot be read: " + e.getMessage());
        }

        String xml = importer.getUTF8();
        Document document = parseXml(xml);
        if (document == null) {
            throw InvoiceException.notAnEInvoice(pdf ? "The XML embedded in the PDF cannot be read."
                    : "The file is neither a PDF nor an XML e-invoice.");
        }
        String root = document.getDocumentElement().getLocalName();
        Syntax syntax;
        if ("CrossIndustryInvoice".equals(root) || "CrossIndustryDocument".equals(root)) {
            syntax = Syntax.CII;
        } else if ("Invoice".equals(root) || "CreditNote".equals(root)) {
            syntax = Syntax.UBL;
        } else {
            throw InvoiceException.notAnEInvoice("The XML is no ZUGFeRD / Factur-X / XRechnung invoice (root element '"
                    + root + "').");
        }

        Invoice invoice;
        try {
            invoice = importer.extractInvoice();
        } catch (Exception | LinkageError e) {
            logger.warn("Could not extract the invoice", e);
            throw InvoiceException.unreadable("The invoice cannot be read: " + e.getMessage());
        }

        try {
            return toEInvoice(invoice, document, syntax, xml, pdf, attachmentName, "CrossIndustryDocument".equals(root));
        } catch (XPathExpressionException | RuntimeException e) {
            logger.warn("Could not read the invoice", e);
            throw InvoiceException.unreadable("The invoice cannot be read: " + e.getMessage());
        }
    }

    private static EInvoice toEInvoice(Invoice invoice, Document document, Syntax syntax, String xml, boolean pdf,
                                       String attachmentName, boolean zugferd1) throws XPathExpressionException {
        XPath xpath = XPathFactory.newInstance().newXPath();
        EInvoice result = new EInvoice();
        result.xml = xml;
        String guideline = syntax == Syntax.UBL
                ? text(xpath, document, "/*/*[local-name()='CustomizationID']")
                : text(xpath, document, "//*[local-name()='GuidelineSpecifiedDocumentContextParameter']/*[local-name()='ID']");
        result.format = format(syntax, guideline, pdf, attachmentName, zugferd1);

        result.number = trim(invoice.getNumber());
        result.date = toLocalDate(invoice.getIssueDate());
        if (invoice.getSender() != null) {
            result.seller = trim(invoice.getSender().getName());
            result.sellerVatId = trim(invoice.getSender().getVATID());
        }
        if (invoice.getRecipient() != null) result.buyer = trim(invoice.getRecipient().getName());
        result.currency = trim(invoice.getCurrency());
        result.periodStart = toLocalDate(invoice.getDetailedDeliveryPeriodFrom());
        result.periodEnd = toLocalDate(invoice.getDetailedDeliveryPeriodTo());
        if (invoice.getNotesWithSubjectCode() != null) {
            for (IncludedNote note : invoice.getNotesWithSubjectCode()) addText(result.notes, note.getContent());
        }
        if (invoice.getNotes() != null) {
            for (String note : invoice.getNotes()) addText(result.notes, note);
        }

        String typeCode = syntax == Syntax.UBL
                ? text(xpath, document, "/*/*[local-name()='InvoiceTypeCode' or local-name()='CreditNoteTypeCode']")
                : text(xpath, document, "//*[local-name()='ExchangedDocument' or local-name()='HeaderExchangedDocument']/*[local-name()='TypeCode']");
        result.creditNote = "CreditNote".equals(document.getDocumentElement().getLocalName()) || "381".equals(typeCode);

        /* delivery location: ids (e.g. the market location) and name */
        TradeParty delivery = invoice.getDeliveryAddress();
        if (delivery != null) {
            addText(result.deliveryTexts, delivery.getID());
            addText(result.deliveryTexts, delivery.getGlobalID());
            addText(result.deliveryTexts, delivery.getName());
        }
        for (String id : texts(xpath, document, "//*[local-name()='ShipToTradeParty']/*[local-name()='ID' or local-name()='GlobalID']"
                + "|//*[local-name()='Delivery']/*[local-name()='DeliveryLocation']/*[local-name()='ID']")) {
            addText(result.deliveryTexts, id);
        }

        readTotals(xpath, document, syntax, result);
        readLines(xpath, document, syntax, invoice, result);
        return result;
    }

    private static void readTotals(XPath xpath, Document document, Syntax syntax, EInvoice result) throws XPathExpressionException {
        if (syntax == Syntax.UBL) {
            result.net = amount(xpath, document, "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxExclusiveAmount']", null);
            result.gross = amount(xpath, document, "/*/*[local-name()='LegalMonetaryTotal']/*[local-name()='TaxInclusiveAmount']", null);
            result.tax = amount(xpath, document, "/*/*[local-name()='TaxTotal']/*[local-name()='TaxAmount']", result.currency);
        } else {
            result.net = amount(xpath, document, "//*[local-name()='TaxBasisTotalAmount']", null);
            result.gross = amount(xpath, document, "//*[local-name()='GrandTotalAmount']", null);
            result.tax = amount(xpath, document, "//*[local-name()='TaxTotalAmount']", result.currency);
        }
        if (result.tax == null && result.net != null && result.gross != null) {
            result.tax = result.gross.subtract(result.net);
        }
    }

    private static void readLines(XPath xpath, Document document, Syntax syntax, Invoice invoice, EInvoice result)
            throws XPathExpressionException {
        NodeList lineNodes = (NodeList) xpath.evaluate(LINES, document, XPathConstants.NODESET);
        IZUGFeRDExportableItem[] items = invoice.getZFItems() != null ? invoice.getZFItems() : new IZUGFeRDExportableItem[0];
        boolean aligned = lineNodes.getLength() == items.length;
        if (!aligned) {
            result.warnings.add("The line details (periods, line totals) could not be read for all lines.");
        }
        for (int i = 0; i < items.length; i++) {
            IZUGFeRDExportableItem item = items[i];
            EInvoice.Line line = new EInvoice.Line();
            Product product = item.getProduct() instanceof Product ? (Product) item.getProduct() : null;
            if (item.getProduct() != null) {
                line.name = trim(item.getProduct().getName());
                line.description = trim(item.getProduct().getDescription());
                line.unitCode = trim(item.getProduct().getUnit());
                line.taxRate = item.getProduct().getVATPercent();
            }
            if (product != null && product.getAttributes() != null) {
                for (Map.Entry<String, String> attribute : product.getAttributes().entrySet()) {
                    addText(line.texts, attribute.getKey() + ": " + attribute.getValue());
                }
            }
            line.quantity = item.getQuantity();
            line.unitPrice = item.getPrice();
            if (item instanceof Item && ((Item) item).getNotes() != null) {
                for (String note : ((Item) item).getNotes()) addText(line.texts, note);
            }

            Node node = aligned ? lineNodes.item(i) : null;
            if (node != null) {
                if (syntax == Syntax.UBL) {
                    line.net = amount(xpath, node, "./*[local-name()='LineExtensionAmount']", null);
                    line.periodStart = parseDate(text(xpath, node, "./*[local-name()='InvoicePeriod']/*[local-name()='StartDate']"));
                    line.periodEnd = parseDate(text(xpath, node, "./*[local-name()='InvoicePeriod']/*[local-name()='EndDate']"));
                    for (String note : texts(xpath, node, "./*[local-name()='Note']")) addText(line.texts, note);
                    NodeList properties = (NodeList) xpath.evaluate("./*[local-name()='Item']/*[local-name()='AdditionalItemProperty']",
                            node, XPathConstants.NODESET);
                    for (int p = 0; p < properties.getLength(); p++) {
                        String name = text(xpath, properties.item(p), "./*[local-name()='Name']");
                        String value = text(xpath, properties.item(p), "./*[local-name()='Value']");
                        if (value != null) addText(line.texts, (name != null ? name + ": " : "") + value);
                    }
                } else {
                    line.net = amount(xpath, node, ".//*[local-name()='LineTotalAmount']", null);
                    line.periodStart = parseDate(text(xpath, node, ".//*[local-name()='BillingSpecifiedPeriod']/*[local-name()='StartDateTime']/*"));
                    line.periodEnd = parseDate(text(xpath, node, ".//*[local-name()='BillingSpecifiedPeriod']/*[local-name()='EndDateTime']/*"));
                    for (String note : texts(xpath, node, "./*[local-name()='AssociatedDocumentLineDocument']/*[local-name()='IncludedNote']/*[local-name()='Content']")) {
                        addText(line.texts, note);
                    }
                    NodeList characteristics = (NodeList) xpath.evaluate(".//*[local-name()='ApplicableProductCharacteristic']",
                            node, XPathConstants.NODESET);
                    for (int p = 0; p < characteristics.getLength(); p++) {
                        String name = text(xpath, characteristics.item(p), "./*[local-name()='Description']");
                        String value = text(xpath, characteristics.item(p), "./*[local-name()='Value']");
                        if (value != null) addText(line.texts, (name != null ? name + ": " : "") + value);
                    }
                }
            }
            if (line.net == null) {
                try {
                    line.net = new LineCalculator(item).getItemTotalNetAmount();
                } catch (RuntimeException e) {
                    logger.debug("Could not calculate the line total", e);
                }
            }
            result.lines.add(line);
        }
    }

    /**
     * @return a readable name of the format, e.g. "ZUGFeRD/Factur-X EN16931", "XRechnung 3.0 UBL"
     */
    static String format(Syntax syntax, String guideline, boolean pdf, String attachmentName, boolean zugferd1) {
        String g = guideline != null ? guideline.toLowerCase(Locale.ROOT) : "";
        String profile = profile(g);
        if (g.contains("xrechnung")) {
            Matcher matcher = XRECHNUNG_VERSION.matcher(g);
            String version = matcher.find() ? " " + matcher.group(1) : "";
            return "XRechnung" + version + (syntax == Syntax.UBL ? " UBL" : " CII") + (pdf ? " (PDF)" : "");
        }
        if (syntax == Syntax.UBL) {
            return "UBL" + (profile != null ? " " + profile : "");
        }
        String name;
        if (zugferd1) {
            name = "ZUGFeRD 1.0";
        } else if (attachmentName != null && attachmentName.equalsIgnoreCase("zugferd-invoice.xml")) {
            name = "ZUGFeRD 2.0";
        } else {
            name = "ZUGFeRD/Factur-X";
        }
        return name + (profile != null ? " " + profile : "") + (pdf ? "" : " (XML)");
    }

    private static String profile(String guideline) {
        if (guideline.isEmpty()) return null;
        if (guideline.contains("peppol")) return "Peppol BIS 3.0";
        if (guideline.contains("minimum")) return "MINIMUM";
        if (guideline.contains("basicwl")) return "BASIC WL";
        if (guideline.contains("extended")) return "EXTENDED";
        if (guideline.contains("comfort")) return "COMFORT";
        if (guideline.contains("basic")) return "BASIC";
        if (guideline.contains("en16931")) return "EN16931";
        return null;
    }

    private static String invoiceAttachmentName(List<FileAttachment> attachments) {
        if (attachments == null) return null;
        for (FileAttachment attachment : attachments) {
            String name = attachment.getFilename();
            if (name != null && name.toLowerCase(Locale.ROOT).endsWith(".xml")) return name;
        }
        return null;
    }

    /**
     * Parses the XML without external entities (null if it is no XML).
     */
    static Document parseXml(String xml) {
        if (xml == null || xml.trim().isEmpty()) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            factory.setXIncludeAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            logger.debug("No XML", e);
            return null;
        }
    }

    private static String text(XPath xpath, Object context, String expression) throws XPathExpressionException {
        Node node = (Node) xpath.evaluate(expression, context, XPathConstants.NODE);
        return node != null ? trim(node.getTextContent()) : null;
    }

    private static List<String> texts(XPath xpath, Object context, String expression) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, context, XPathConstants.NODESET);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = trim(nodes.item(i).getTextContent());
            if (value != null) result.add(value);
        }
        return result;
    }

    /**
     * @param currency if set, the node whose attribute currencyID is the currency is preferred (CII and UBL may
     *                 contain the tax total twice, in invoice and in tax currency)
     */
    private static BigDecimal amount(XPath xpath, Object context, String expression, String currency) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(expression, context, XPathConstants.NODESET);
        Node chosen = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (chosen == null) chosen = node;
            if (currency != null && node instanceof Element && currency.equals(((Element) node).getAttribute("currencyID"))) {
                chosen = node;
                break;
            }
        }
        if (chosen == null) return null;
        String value = trim(chosen.getTextContent());
        try {
            return value != null ? new BigDecimal(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the date of "yyyyMMdd" (CII format 102) or "yyyy-MM-dd" (UBL), else null
     */
    static LocalDate parseDate(String value) {
        if (value == null) return null;
        String v = value.trim();
        try {
            if (v.matches("\\d{8}")) return LocalDate.parse(v, DateTimeFormatter.BASIC_ISO_DATE);
            if (v.matches("\\d{4}-\\d{2}-\\d{2}.*")) return LocalDate.parse(v.substring(0, 10));
        } catch (RuntimeException e) {
            logger.debug("No date: {}", value);
        }
        return null;
    }

    private static LocalDate toLocalDate(Date date) {
        return date != null ? date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    private static void addText(List<String> list, String text) {
        String t = trim(text);
        if (t != null && !list.contains(t)) list.add(t);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
