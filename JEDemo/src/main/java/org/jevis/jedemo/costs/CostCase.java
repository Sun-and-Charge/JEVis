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
package org.jevis.jedemo.costs;

import org.jevis.commons.trc.ContractType;
import org.jevis.jedemo.InvoiceCase;
import org.jevis.jedemo.Ref;

import java.util.ArrayList;
import java.util.List;

/**
 * Definition of one demo cost case: the contract positions, the rows and the invoice settings. From it
 * {@link TemplateFactory} builds the result calculation template and the accounting configuration and
 * {@link CostCases#invoiceCase(CostCase)} the invoice case of the JEDemo job, so both price the same way.
 */
public final class CostCase {

    /**
     * "A" … "F"
     */
    String key;
    /**
     * file name stem of the template and configuration, e.g. "A-strom-rlm"
     */
    String file;
    String templateName;
    String configurationName;
    /**
     * key of the site in the setup plan ("Musterwerk" or "Außenlager Süd"), for the paths
     */
    String site;
    ContractType contractType;
    String contractNumber;
    String marketLocation;
    String contractStart;
    String contractEnd;

    /* invoices */
    String medium;
    String suffix;
    String seller;
    String sellerVatId;
    String meterPointLabel;
    String meterPoint;
    /**
     * the Energy Invoices Directory of the case and its three series (CONSUMPTION, NET, GROSS)
     */
    Ref invoiceDirectory;
    Ref invoiceConsumption;
    Ref invoiceNet;
    Ref invoiceGross;
    final List<InvoiceCase.Deviation> deviations = new ArrayList<>();

    /* rows */
    Row consumption;
    /**
     * volume conversion: consumption row in m³, energy = volume × state number × heating value
     */
    Value heatingValue;
    Value stateNumber;
    /**
     * peak for the demand charge, null without
     */
    Row peak;
    double peakFactor = 1;
    String peakUnit = "kW";

    final List<Value> infos = new ArrayList<>();
    /**
     * values shown in the "Satz" column above the positions (heating value, state number, price indices, capacity)
     */
    final List<Value> facts = new ArrayList<>();
    final List<Position> positions = new ArrayList<>();
    Value vat;

    /* charging bill (case F) instead of positions */
    final List<ChargePoint> chargePoints = new ArrayList<>();
    Value chargePrice;

    public String getKey() {
        return key;
    }

    public String getFile() {
        return file;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getConfigurationName() {
        return configurationName;
    }

    public boolean hasInvoices() {
        return invoiceDirectory != null;
    }

    /**
     * An object attribute used by the template: object, its class (for the input selector), attribute, label.
     */
    static final class Value {
        final Ref object;
        final String objectClass;
        final String attribute;
        final String label;
        String unit;
        String filter;

        Value(Ref object, String objectClass, String attribute, String label) {
            this.object = object;
            this.objectClass = objectClass;
            this.attribute = attribute;
            this.label = label;
        }

        Value unit(String unit) {
            this.unit = unit;
            return this;
        }

        Value filter(String filter) {
            this.filter = filter;
            return this;
        }

        InvoiceCase.Value invoiceValue() {
            return new InvoiceCase.Value(object, attribute);
        }
    }

    /**
     * A data row ("Value" of a clean data or base data row).
     */
    static final class Row {
        final Ref object;
        final String objectClass;
        final String filter;
        final String unit;

        Row(Ref object, String objectClass, String filter, String unit) {
            this.object = object;
            this.objectClass = objectClass;
            this.filter = filter;
            this.unit = unit;
        }
    }

    /**
     * A cost position, priced as the invoice line of the same key (see {@link InvoiceCase.Line}).
     */
    static final class Position {
        final String key;
        final String label;
        /**
         * {@link InvoiceCase#ENERGY}, {@link InvoiceCase#INDEXED}, {@link InvoiceCase#YEARLY},
         * {@link InvoiceCase#DEMAND} or {@link InvoiceCase#CAPACITY}
         */
        final String type;
        final Value price;
        /**
         * ct/kWh, EUR/MWh (energy), EUR/a (yearly), EUR/kW/a (demand, capacity)
         */
        final String priceUnit;
        Value capacity;
        double indexConstant;
        final List<IndexTerm> terms = new ArrayList<>();

        Position(String key, String label, String type, Value price, String priceUnit) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.price = price;
            this.priceUnit = priceUnit;
        }
    }

    /**
     * A charge point of the charging bill: its energy row and the String Data rows with licence plate, cost centre
     * and personnel number of the company car.
     */
    static final class ChargePoint {
        final String label;
        final Row row;
        final Ref plate;
        final Ref costCenter;
        final Ref person;

        ChargePoint(String label, Row row, Ref plate, Ref costCenter, Ref person) {
            this.label = label;
            this.row = row;
            this.plate = plate;
            this.costCenter = costCenter;
            this.person = person;
        }
    }

    static final class IndexTerm {
        final double weight;
        final Value value;
        final double base;
        final String label;

        IndexTerm(double weight, Value value, double base, String label) {
            this.weight = weight;
            this.value = value;
            this.base = base;
            this.label = label;
        }
    }
}
