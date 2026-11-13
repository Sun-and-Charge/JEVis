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
import org.jevis.jedemo.costs.CostCase.ChargePoint;
import org.jevis.jedemo.costs.CostCase.IndexTerm;
import org.jevis.jedemo.costs.CostCase.Position;
import org.jevis.jedemo.costs.CostCase.Row;
import org.jevis.jedemo.costs.CostCase.Value;
import org.jevis.jedemo.costs.PlanObject.Parent;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The six demo cost cases of the demo organisation and the objects they need (neutral names and synthetic values
 * only):
 * <ul>
 * <li>A: electricity, interval metered (RLM), main connection Musterwerk: grid purchase, demand charge on the peak of
 * the year;</li>
 * <li>B: electricity, standard load profile (SLP), new site "Außenlager Süd": monthly meter reading;</li>
 * <li>C: gas, interval metered, Musterwerk: energy row, demand charge on the hourly peak;</li>
 * <li>D: gas, standard load profile, Außenlager Süd: monthly volume reading, energy = m³ × state number × heating
 * value;</li>
 * <li>E: district heating, Außenlager Süd: work price with a price escalation clause (gas and heat price index),
 * capacity price on the connected load, metering price;</li>
 * <li>F: charging of company cars at three charge points of Musterwerk (rebuild of the old "E-Mobilität" template),
 * no supplier invoices.</li>
 * </ul>
 * Prices (net, plausible 2025/2026 values): samples on 1 January 2025 and 2026 ({@code NON_PERIODIC}); yearly amounts
 * ({@code YEARLY_VALUE}) one sample per year 2025–2030, because the calculation only uses a sample of the year of the
 * time frame. Demand charges are ranging tables of the grid operators, which the calculation reads from the latest
 * sample only, so they have one value for both years.
 */
public final class CostCases {

    public static final String ORGANISATION = "Sun and Charge Demo";
    static final String SITES = ORGANISATION + " / Standorte";
    static final String MUSTERWERK = SITES + " / Musterwerk";
    static final String MW_CONTRACTS = MUSTERWERK + " / Energieverträge";
    public static final String SITE_MW = "Musterwerk";
    public static final String SITE_S = "Außenlager Süd";

    /* existing objects of the demo organisation */
    static final long SUPPLIER_ELECTRICITY = 23510;
    static final long MSB_ELECTRICITY = 23511;
    static final long CONTRACTOR_MSB = 23512;
    static final long CONTRACTOR_SUPPLY = 23513;
    static final long CONTRACTOR_GRID = 23514;
    static final long GRID_ELECTRICITY = 23515;
    static final long GRID_GAS = 23516;
    static final long DUES = 23519;
    /**
     * "Strom - Bezug \ Bereinigte Daten": grid purchase, 15-minute kWh (raw row 23682)
     */
    public static final long CLEAN_GRID_PURCHASE = 23734;
    /**
     * "Gas - Hauptzähler (Energie) \ Bereinigte Daten": 15-minute kWh
     */
    public static final long CLEAN_GAS_ENERGY = 23572;
    /**
     * "Gas - Brennwert" (Base Data, kWh/m³)
     */
    public static final long HEATING_VALUE = 23946;
    /**
     * "Strom - Gesamt \ Stundenwerte": clean data with hourly aggregation, model for the hourly gas view
     */
    static final long HOURLY_MODEL = 23686;
    public static final long CLEAN_CHARGE_POINT_1 = 23925;
    public static final long CLEAN_CHARGE_POINT_2 = 23927;
    public static final long CLEAN_CHARGE_POINT_3 = 23923;
    /**
     * raw rows (Data, 15-minute kWh) of the charge points 1–3, parent {@link #CHARGE_POINT_PARENT}
     */
    public static final long CHARGE_POINT_1 = 23922;
    public static final long CHARGE_POINT_2 = 23920;
    public static final long CHARGE_POINT_3 = 23919;
    public static final String CHARGE_POINT_NAME = "Strom - Ladestrom - Ladepunkt ";
    static final long CHARGE_POINT_PARENT = 23677;
    /**
     * "Strom - Ladestrom Summe" (Data, output of a calculation summing the charge points) and its child (clean data)
     */
    public static final long CHARGE_SUM = 23921;
    public static final String CHARGE_SUM_NAME = "Strom - Ladestrom Summe";
    public static final long CHARGE_SUM_CLEAN = 23924;
    /**
     * start of the replaced charge point histories
     */
    static final ZonedDateTime CHARGE_FROM = ZonedDateTime.of(2025, 11, 1, 0, 0, 0, 0, History.ZONE);
    /**
     * the current template "stromrechnung", its directory receives the new templates
     */
    static final long OLD_TEMPLATE = 23952;

    static final ZonedDateTime Y2025 = year(2025);
    static final ZonedDateTime Y2026 = year(2026);
    static final int LAST_YEAR = 2030;

    private CostCases() {
    }

    static ZonedDateTime year(int year) {
        return ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, History.ZONE);
    }

    private static Ref id(long id) {
        return Ref.id(id);
    }

    private static Ref key(String key) {
        return Ref.key(key);
    }

    // ============================================================================================ objects

    /**
     * @return the objects of the setup plan, parents before children
     */
    public static List<PlanObject> objects() {
        List<PlanObject> list = new ArrayList<>();

        /* new site */
        list.add(PlanObject.create("S.site", "Building", SITE_S, Parent.path(SITES))
                .set("Address", "Lagerweg 5, 54321 Musterstadt").set("Timezone", "Europe/Berlin")
                .note("Second site of the demo (office and storage), see cases B, D, E"));
        list.add(PlanObject.create("S.data", "Data Directory", "Daten", Parent.key("S.site")));
        list.add(PlanObject.create("S.data.strom", "Data Directory", "Strom", Parent.key("S.data")));
        list.add(PlanObject.create("S.data.gas", "Data Directory", "Gas", Parent.key("S.data")));
        list.add(PlanObject.create("S.data.waerme", "Data Directory", "Wärme", Parent.key("S.data")));
        list.add(PlanObject.create("S.base", "Base Data Directory", "Stammdaten", Parent.key("S.site")));
        list.add(PlanObject.create("S.base.index", "Base Data Directory", "Preisindizes", Parent.key("S.base")));
        list.add(PlanObject.create("S.base.contract", "Base Data Directory", "Vertragsdaten", Parent.key("S.base")));
        list.add(PlanObject.create("S.contracts", "Energy Contracting Directory", "Energieverträge", Parent.key("S.site")));
        list.add(PlanObject.create("S.supply", "Energy Supply Directory", "Energielieferverträge", Parent.key("S.contracts")));
        list.add(PlanObject.create("S.grid", "Energy Grid Operation Directory", "Netzbetrieb", Parent.key("S.contracts")));
        list.add(PlanObject.create("S.msb", "Energy Metering Point Operation Directory", "Messstellenbetrieb", Parent.key("S.contracts")));
        list.add(PlanObject.create("S.partners", "Energy Contractor Directory", "Vertragspartner", Parent.key("S.contracts")));
        list.add(PlanObject.create("S.configs", "Accounting Configuration Directory", "Verträge", Parent.key("S.contracts")));
        list.add(PlanObject.create("S.invoices", "Energy Invoices Directory", "Energierechnungen", Parent.key("S.contracts")));
        list.add(PlanObject.create("M.base.cars", "Base Data Directory", "Dienstwagen", Parent.path(MUSTERWERK + " / Stammdaten")));

        /* governmental dues (shared) */
        PlanObject dues = PlanObject.update("dues", DUES, "Governmental Dues", "Staatliche Abgaben");
        prices(dues, "VAT Electricity", "%", 19, 19);
        prices(dues, "VAT Gas", "%", 19, 19);
        prices(dues, "VAT Community Heating", "%", 19, 19);
        /* electricity levies 2027 as published by the transmission system operators (netztransparenz.de, 10/2026) */
        prices(dues, "Allocation 02", "ct/kWh", 0.277, 0.446, 0.451);
        prices(dues, "Allocation 03", "ct/kWh", 1.558, 1.559, 2.027);
        prices(dues, "Allocation 05", "ct/kWh", 0.816, 0.941, 1.143);
        prices(dues, "Allocation 08", "ct/kWh", 1.001, 1.092);
        /* gas storage levy (THE): 2.99 EUR/MWh from 1/2025, 2.89 EUR/MWh from 7/2025, abolished from 1/2026 */
        dues.unit("Allocation 09", "ct/kWh");
        dues.sample("Allocation 09", Y2025, 0.299).sample("Allocation 09", Y2025.withMonth(7), 0.289).sample("Allocation 09", Y2026, 0.0);
        prices(dues, "Energy Tax Electricity", "ct/kWh", 2.05, 2.05);
        prices(dues, "Energy Tax Gas", "ct/kWh", 0.55, 0.55);
        dues.note("Allocation 02 = KWKG, 03 = Aufschlag für besondere Netznutzung A (up to 1 GWh, formerly § 19 StromNEV), "
                + "05 = Offshore, 08 = CO2 price (BEHG 55 / 60 EUR/t, 2027 as 2026 until the corridor is final), "
                + "09 = gas storage levy (0 from 2026)");
        list.add(dues);

        /* A: electricity RLM, Musterwerk */
        PlanObject supplierA = PlanObject.update("A.supplier", SUPPLIER_ELECTRICITY, "Electricity Supply Contractor", "Energieversorger Nord");
        prices(supplierA, "Energy Price HT", "ct/kWh", 14.8, 13.5);
        list.add(supplierA.note("Flat work price (no HT/NT split)"));
        PlanObject gridA = PlanObject.update("A.grid", GRID_ELECTRICITY, "Electricity Grid Operator", "Netzbetreiber Nord");
        prices(gridA, "Franchise Tax", "ct/kWh", 0.11, 0.11);
        prices(gridA, "Energy Price Grid", "ct/kWh", 1.08, 1.15);
        ranging(gridA, "Demand Charge", "EUR/kW/a", 135);
        list.add(gridA.note("Medium voltage; demand charge 135 EUR/kW/a as a one-range table (the calculation reads the latest table only)"));
        PlanObject msbA = PlanObject.update("A.msb", MSB_ELECTRICITY, "Electricity Metering Point Operator", "Netzbetreiber Nord");
        yearly(msbA, "Net Cost", "EUR/a", 420, 450);
        list.add(msbA.note("Interval metering (RLM)"));
        addInvoiceFolder(list, "A", "Strom RLM Hauptanschluss", "ELECTRICITY", "kWh", Parent.path(MW_CONTRACTS + " / Energierechnungen"));

        /* B: electricity SLP, Außenlager Süd */
        PlanObject supplierB = PlanObject.create("B.supplier", "Electricity Supply Contractor", "Energieversorger Nord - Gewerbestrom", Parent.key("S.supply"))
                .set("Contractor", String.valueOf(CONTRACTOR_SUPPLY));
        prices(supplierB, "Energy Price HT", "ct/kWh", 18.4, 17.5);
        yearly(supplierB, "Additional Fix Cost", "EUR/a", 168, 180);
        list.add(supplierB.note("Additional Fix Cost = Grundpreis (14 / 15 EUR per month, billed per day)"));
        PlanObject gridB = PlanObject.create("B.grid", "Electricity Grid Operator", "Netzbetreiber Nord - Niederspannung", Parent.key("S.grid"))
                .set("Contractor", String.valueOf(CONTRACTOR_GRID)).set("Energy Grid Operator Code Number", "9900000000002");
        prices(gridB, "Franchise Tax", "ct/kWh", 1.59, 1.59);
        prices(gridB, "Energy Price Grid", "ct/kWh", 8.3, 7.9);
        yearly(gridB, "Net Cost", "EUR/a", 55, 60);
        list.add(gridB);
        PlanObject msbB = PlanObject.create("B.msb", "Electricity Metering Point Operator", "Netzbetreiber Nord - Messstelle Außenlager Süd", Parent.key("S.msb"))
                .set("Contractor", String.valueOf(CONTRACTOR_MSB)).set("Energy Metering Point Operator Code Number", "9900000000002");
        yearly(msbB, "Net Cost", "EUR/a", 100, 110);
        list.add(msbB);
        list.add(PlanObject.create("B.row", "Data", "Strom - Außenlager Süd", Parent.key("S.data.strom"))
                .set("Period", "P1M").unit("Value", "kWh").history(History.ELECTRICITY_SLP_COUNTER)
                .note("Monthly meter reading (counter) at the 1st 00:00"));
        list.add(cleanCounter("B.clean", "B.row", "P1M", "kWh"));
        addInvoiceFolder(list, "B", "Strom SLP Außenlager Süd", "ELECTRICITY", "kWh", Parent.key("S.invoices"));

        /* C: gas RLM, Musterwerk */
        list.add(PlanObject.create("gas.contractor", "Energy Grid Operation Contractor", "Gasnetzbetreiber Nord", Parent.path(MW_CONTRACTS + " / Vertragspartner"))
                .set("Company", "Gasnetzbetreiber Nord"));
        PlanObject supplierC = PlanObject.create("C.supplier", "Gas Supply Contractor", "Energieversorger Nord - Erdgas Musterwerk", Parent.path(MW_CONTRACTS + " / Energielieferverträge"))
                .set("Contractor", String.valueOf(CONTRACTOR_SUPPLY));
        prices(supplierC, "Energy Price", "ct/kWh", 4.4, 3.9);
        prices(supplierC, "State Number", "", 1, 1);
        list.add(supplierC.note("State number 1: the meter has a volume corrector"));
        PlanObject gridC = PlanObject.update("C.grid", GRID_GAS, "Gas Grid Operator", "Gasnetzbetreiber Nord")
                .set("Contractor", "@gas.contractor").set("Energy Grid Operator Code Number", "9800000000001");
        prices(gridC, "Franchise Tax", "ct/kWh", 0.03, 0.03);
        prices(gridC, "Energy Price Grid", "ct/kWh", 0.42, 0.45);
        ranging(gridC, "Demand Charge", "EUR/kW/a", 18);
        list.add(gridC.note("Demand charge 18 EUR per kWh/h and year as a one-range table"));
        PlanObject msbC = PlanObject.create("C.msb", "Gas Metering Point Operator", "Gasnetzbetreiber Nord - Messstelle Musterwerk", Parent.path(MW_CONTRACTS + " / Messstellenbetrieb"))
                .set("Energy Metering Point Operator Code Number", "9800000000001");
        yearly(msbC, "Net Cost", "EUR/a", 380, 400);
        list.add(msbC);
        list.add(PlanObject.create("C.hourly", "Clean Data", "Stundenwerte", Parent.id(CLEAN_GAS_ENERGY))
                .copyAttributesFrom(HOURLY_MODEL).set("Period", "PT1H").set("Value is a Quantity", true).unit("Value", "kWh")
                .note("Hourly sums of the 15-minute gas energy (kWh/h) for the demand charge; settings as 23686 (Strom - Gesamt \\ Stundenwerte)"));
        addInvoiceFolder(list, "C", "Gas RLM Musterwerk", "GAS", "kWh", Parent.path(MW_CONTRACTS + " / Energierechnungen"));

        /* D: gas SLP, Außenlager Süd */
        PlanObject supplierD = PlanObject.create("D.supplier", "Gas Supply Contractor", "Energieversorger Nord - Erdgas Gewerbe", Parent.key("S.supply"))
                .set("Contractor", String.valueOf(CONTRACTOR_SUPPLY));
        prices(supplierD, "Energy Price", "ct/kWh", 6.9, 6.4);
        yearly(supplierD, "Additional Fix Cost", "EUR/a", 132, 144);
        prices(supplierD, "State Number", "", 0.9535, 0.9535);
        list.add(supplierD.note("Additional Fix Cost = Grundpreis (11 / 12 EUR per month, billed per day)"));
        PlanObject gridD = PlanObject.create("D.grid", "Gas Grid Operator", "Gasnetzbetreiber Nord - Außenlager Süd", Parent.key("S.grid"))
                .set("Contractor", "@gas.contractor").set("Energy Grid Operator Code Number", "9800000000001");
        prices(gridD, "Franchise Tax", "ct/kWh", 0.03, 0.03);
        prices(gridD, "Energy Price Grid", "ct/kWh", 1.6, 1.7);
        yearly(gridD, "Net Cost", "EUR/a", 170, 180);
        list.add(gridD);
        PlanObject msbD = PlanObject.create("D.msb", "Gas Metering Point Operator", "Gasnetzbetreiber Nord - Messstelle Außenlager Süd", Parent.key("S.msb"))
                .set("Energy Metering Point Operator Code Number", "9800000000001");
        yearly(msbD, "Net Cost", "EUR/a", 23, 25);
        list.add(msbD);
        list.add(PlanObject.create("D.row", "Data", "Gas - Außenlager Süd (Volumen)", Parent.key("S.data.gas"))
                .set("Period", "P1M").unit("Value", "m³").history(History.GAS_SLP_COUNTER)
                .note("Monthly meter reading (m³, counter) at the 1st 00:00"));
        list.add(cleanCounter("D.clean", "D.row", "P1M", "m³"));
        addInvoiceFolder(list, "D", "Gas SLP Außenlager Süd", "GAS", "kWh", Parent.key("S.invoices"));

        /* E: district heating, Außenlager Süd */
        list.add(PlanObject.create("E.contractor", "Energy Supply Contractor", "Wärmeversorgung Nord", Parent.key("S.partners"))
                .set("Company", "Wärmeversorgung Nord"));
        PlanObject supplierE = PlanObject.create("E.supplier", "Community Heating Supply Contractor", "Wärmeversorgung Nord - Fernwärme Außenlager Süd", Parent.key("S.supply"))
                .set("Contractor", "@E.contractor");
        prices(supplierE, "Energy Price", "EUR/MWh", 95, 95);
        prices(supplierE, "Demand Charge", "EUR/kW/a", 46, 48);
        yearly(supplierE, "Additional Fix Cost", "EUR/a", 230, 240);
        list.add(supplierE.note("Energy Price = base work price AP0 of the escalation clause AP = AP0 × (0.35 + 0.40 × G/G0 + 0.25 × W/W0), "
                + "G0 = W0 = 100 (indices 2021 = 100); Additional Fix Cost = Messpreis. Story: action \"MW 018 - Neuer Fernwärmevertrag\""));
        list.add(PlanObject.create("E.capacity", "Base Data", "Fernwärme - Anschlusswert", Parent.key("S.base.contract"))
                .unit("Value", "kW").sample("Value", Y2025, 150.0));
        list.add(PlanObject.create("E.indexGas", "Base Data", "Preisindex Erdgas (2021 = 100)", Parent.key("S.base.index"))
                .history(History.GAS_PRICE_INDEX).note("Synthetic monthly index 2024-01 … 2030-12 at the 1st 00:00"));
        list.add(PlanObject.create("E.indexHeat", "Base Data", "Preisindex Wärme (2021 = 100)", Parent.key("S.base.index"))
                .history(History.HEAT_PRICE_INDEX).note("Synthetic monthly index 2024-01 … 2030-12 at the 1st 00:00"));
        list.add(PlanObject.create("E.row", "Data", "Fernwärme - Außenlager Süd", Parent.key("S.data.waerme"))
                .set("Period", "PT1H").unit("Value", "kWh").history(History.HEAT_COUNTER)
                .note("Hourly heat meter reading (counter); about 180 MWh per year"));
        list.add(cleanCounter("E.clean", "E.row", "PT1H", "kWh"));
        addInvoiceFolder(list, "E", "Fernwärme Außenlager Süd", "DISTRICT_HEATING", "kWh", Parent.key("S.invoices"));

        /* F: charging of company cars, Musterwerk */
        list.add(PlanObject.create("F.price", "Base Data", "Ladestrompreis Dienstwagen", Parent.key("M.base.cars"))
                .unit("Value", "ct/kWh").sample("Value", Y2025, 28.0).sample("Value", Y2026, 30.0));
        String[][] cars = {{"DE-MO 101E", "4100 Vertrieb", "P-1001"}, {"DE-MO 102E", "4200 Service", "P-1002"},
                {"DE-MO 103E", "4300 Technik", "P-1003"}};
        for (int i = 0; i < 3; i++) {
            String lp = "Ladepunkt " + (i + 1);
            String k = "F.lp" + (i + 1);
            list.add(PlanObject.create(k + ".plate", "String Data", lp + " - Kennzeichen", Parent.key("M.base.cars")).sample("Value", Y2025, cars[i][0]));
            list.add(PlanObject.create(k + ".cost", "String Data", lp + " - Kostenstelle", Parent.key("M.base.cars")).sample("Value", Y2025, cars[i][1]));
            list.add(PlanObject.create(k + ".person", "String Data", lp + " - Personalnummer", Parent.key("M.base.cars")).sample("Value", Y2025, cars[i][2]));
        }
        return list;
    }

    private static PlanObject cleanCounter(String key, String parent, String period, String unit) {
        return PlanObject.create(key, "Clean Data", "Bereinigte Daten", Parent.key(parent))
                .set("Enabled", true).set("Conversion to Differential", true).set("Value is a Quantity", true)
                .set("Period", period).unit("Value", unit);
    }

    private static void addInvoiceFolder(List<PlanObject> list, String caseKey, String name, String medium, String unit, Parent parent) {
        String folder = caseKey + ".invoices";
        list.add(PlanObject.create(folder, "Energy Invoices Directory", name, parent).set("Medium", medium)
                .note("Invoice folder of case " + caseKey + "; the import uses it directly because its Medium is " + medium));
        list.add(PlanObject.create(folder + ".CONSUMPTION", "Energy Invoice Data", "Verbrauch laut Rechnung", Parent.key(folder))
                .set("Series", "CONSUMPTION").unit("Value", unit));
        list.add(PlanObject.create(folder + ".NET", "Energy Invoice Data", "Kosten netto laut Rechnung", Parent.key(folder))
                .set("Series", "NET").unit("Value", "€"));
        list.add(PlanObject.create(folder + ".GROSS", "Energy Invoice Data", "Kosten brutto laut Rechnung", Parent.key(folder))
                .set("Series", "GROSS").unit("Value", "€"));
    }

    /**
     * a price with one sample on 1 January 2025 and one on 1 January 2026
     */
    private static void prices(PlanObject o, String attribute, String unit, double y2025, double y2026) {
        if (unit != null && !unit.isEmpty()) o.unit(attribute, unit);
        o.sample(attribute, Y2025, y2025).sample(attribute, Y2026, y2026);
    }

    /**
     * a price with one sample on 1 January 2025, 2026 and 2027
     */
    private static void prices(PlanObject o, String attribute, String unit, double y2025, double y2026, double y2027) {
        prices(o, attribute, unit, y2025, y2026);
        o.sample(attribute, year(2027), y2027);
    }

    /**
     * a yearly amount: one sample per year 2025 … {@value #LAST_YEAR} (2027 on as 2026)
     */
    private static void yearly(PlanObject o, String attribute, String unit, double y2025, double fromY2026) {
        o.unit(attribute, unit);
        o.sample(attribute, Y2025, y2025);
        for (int year = 2026; year <= LAST_YEAR; year++) o.sample(attribute, year(year), fromY2026);
    }

    /**
     * a ranging table with one range (0 … 1e9) and the yearly price
     */
    private static void ranging(PlanObject o, String attribute, String unit, double value) {
        o.unit(attribute, unit);
        o.sample(attribute, Y2026, rangingJson(value));
    }

    static String rangingJson(double value) {
        return "{\"rangingValues\":[{\"from\":0.0,\"to\":1.0E9,\"value\":" + value + "}]}";
    }

    // ============================================================================================ cases

    public static List<CostCase> cases() {
        List<CostCase> list = new ArrayList<>();
        list.add(caseA());
        list.add(caseB());
        list.add(caseC());
        list.add(caseD());
        list.add(caseE());
        list.add(caseF());
        return Collections.unmodifiableList(list);
    }

    public static CostCase byKey(String key) {
        for (CostCase c : cases()) {
            if (c.key.equals(key)) return c;
        }
        throw new IllegalArgumentException("No cost case " + key);
    }

    private static CostCase base(String key, String file, String template, String configuration, String site,
                                 ContractType type, String number, String marketLocation) {
        CostCase c = new CostCase();
        c.key = key;
        c.file = file;
        c.templateName = template;
        c.configurationName = configuration;
        c.site = site;
        c.contractType = type;
        c.contractNumber = number;
        c.marketLocation = marketLocation;
        c.contractStart = "2025-01-01T00:00:00.000+01:00";
        c.contractEnd = "2027-12-31T00:00:00.000+01:00";
        return c;
    }

    private static void invoices(CostCase c, String medium, String suffix, String seller, String sellerVatId,
                                 String meterPointLabel, String meterPoint) {
        c.medium = medium;
        c.suffix = suffix;
        c.seller = seller;
        c.sellerVatId = sellerVatId;
        c.meterPointLabel = meterPointLabel;
        c.meterPoint = meterPoint;
        String folder = c.key + ".invoices";
        c.invoiceDirectory = key(folder);
        c.invoiceConsumption = key(folder + ".CONSUMPTION");
        c.invoiceNet = key(folder + ".NET");
        c.invoiceGross = key(folder + ".GROSS");
    }

    private static InvoiceCase.Deviation deviation(String month, String type, String line, Double factor, String note) {
        InvoiceCase.Deviation d = new InvoiceCase.Deviation();
        d.month = month;
        d.type = type;
        d.line = line;
        d.factor = factor;
        d.note = note;
        return d;
    }

    private static Value v(Ref object, String objectClass, String attribute, String label) {
        return new Value(object, objectClass, attribute, label);
    }

    private static Value dues(String attribute, String label) {
        return v(id(DUES), "Governmental Dues", attribute, label);
    }

    private static Position energy(String key, String label, Value price) {
        return new Position(key, label, InvoiceCase.ENERGY, price, "ct/kWh");
    }

    private static Position yearly(String key, String label, Value price) {
        return new Position(key, label, InvoiceCase.YEARLY, price, "EUR/a");
    }

    private static Position demand(String key, String label, Value price) {
        return new Position(key, label, InvoiceCase.DEMAND, price, "EUR/kW/a");
    }

    private static void electricityLevies(CostCase c) {
        c.positions.add(energy("KWKG", "KWKG-Umlage", dues("Allocation 02", "KWKG-Umlage")));
        c.positions.add(energy("STROMNEV", "Aufschlag für besondere Netznutzung", dues("Allocation 03", "Aufschlag besondere Netznutzung")));
        c.positions.add(energy("OFFSHORE", "Offshore-Netzumlage", dues("Allocation 05", "Offshore-Netzumlage")));
        c.positions.add(energy("TAX", "Stromsteuer", dues("Energy Tax Electricity", "Stromsteuer")));
        c.vat = dues("VAT Electricity", "Umsatzsteuer");
    }

    private static void gasLevies(CostCase c) {
        c.positions.add(energy("CO2", "CO2-Preis (BEHG)", dues("Allocation 08", "CO2-Preis")));
        c.positions.add(energy("STORAGE", "Gasspeicherumlage", dues("Allocation 09", "Gasspeicherumlage")));
        c.positions.add(energy("TAX", "Energiesteuer", dues("Energy Tax Gas", "Energiesteuer")));
        c.vat = dues("VAT Gas", "Umsatzsteuer");
    }

    static CostCase caseA() {
        CostCase c = base("A", "A-strom-rlm", "Kostenrechnung Strom RLM", "Strom RLM - Musterwerk Hauptanschluss", SITE_MW,
                ContractType.ELECTRICITY, "V-2025-0101", "10000000001");
        invoices(c, "ELECTRICITY", "SR", "Energieversorger Nord", "DE000000000", "Zählpunkt", "DE0000000000000000000000000000001");
        c.consumption = new Row(id(CLEAN_GRID_PURCHASE), "Clean Data", "Bezug", "kWh");
        c.peak = new Row(id(CLEAN_GRID_PURCHASE), "Clean Data", "Bezug", "kWh");
        c.peakFactor = 4;
        c.peakUnit = "kW";
        String supplier = "Electricity Supply Contractor";
        String grid = "Electricity Grid Operator";
        String msb = "Electricity Metering Point Operator";
        c.infos.add(v(id(SUPPLIER_ELECTRICITY), supplier, "name", "Lieferant"));
        c.infos.add(v(id(GRID_ELECTRICITY), grid, "name", "Netzbetreiber"));
        c.infos.add(v(id(MSB_ELECTRICITY), msb, "name", "Messstellenbetreiber"));
        c.positions.add(energy("ENERGY", "Arbeitspreis", v(id(SUPPLIER_ELECTRICITY), supplier, "Energy Price HT", "Arbeitspreis")));
        c.positions.add(demand("GRID_DEMAND", "Netzentgelt Leistungspreis", v(id(GRID_ELECTRICITY), grid, "Demand Charge", "Leistungspreis Netz")));
        c.positions.add(energy("GRID_ENERGY", "Netzentgelt Arbeitspreis", v(id(GRID_ELECTRICITY), grid, "Energy Price Grid", "Arbeitspreis Netz")));
        c.positions.add(yearly("MSB", "Messstellenbetrieb", v(id(MSB_ELECTRICITY), msb, "Net Cost", "Messstellenbetrieb")));
        c.positions.add(energy("FRANCHISE", "Konzessionsabgabe", v(id(GRID_ELECTRICITY), grid, "Franchise Tax", "Konzessionsabgabe")));
        electricityLevies(c);
        c.deviations.add(deviation("2026-03", InvoiceCase.DUPLICATE_LINE, "MSB", null, "Messstellenbetrieb doppelt berechnet"));
        c.deviations.add(deviation("2026-07", InvoiceCase.PREVIOUS_YEAR_PRICE, "ENERGY", null, "Arbeitspreis des Vorjahres berechnet"));
        return c;
    }

    static CostCase caseB() {
        CostCase c = base("B", "B-strom-slp", "Kostenrechnung Strom SLP", "Strom SLP - Außenlager Süd", SITE_S,
                ContractType.ELECTRICITY, "V-2025-0102", "10000000002");
        invoices(c, "ELECTRICITY", "SS", "Energieversorger Nord", "DE000000000", "Zählpunkt", "DE0000000000000000000000000000002");
        c.consumption = new Row(key("B.clean"), "Clean Data", "Strom Außenlager", "kWh");
        String supplier = "Electricity Supply Contractor";
        String grid = "Electricity Grid Operator";
        String msb = "Electricity Metering Point Operator";
        c.infos.add(v(key("B.supplier"), supplier, "name", "Lieferant"));
        c.infos.add(v(key("B.grid"), grid, "name", "Netzbetreiber"));
        c.infos.add(v(key("B.msb"), msb, "name", "Messstellenbetreiber"));
        c.positions.add(energy("ENERGY", "Arbeitspreis", v(key("B.supplier"), supplier, "Energy Price HT", "Arbeitspreis")));
        c.positions.add(yearly("BASE", "Grundpreis", v(key("B.supplier"), supplier, "Additional Fix Cost", "Grundpreis")));
        c.positions.add(yearly("GRID_BASE", "Netzentgelt Grundpreis", v(key("B.grid"), grid, "Net Cost", "Grundpreis Netz")));
        c.positions.add(energy("GRID_ENERGY", "Netzentgelt Arbeitspreis", v(key("B.grid"), grid, "Energy Price Grid", "Arbeitspreis Netz")));
        c.positions.add(yearly("MSB", "Messstellenbetrieb", v(key("B.msb"), msb, "Net Cost", "Messstellenbetrieb")));
        c.positions.add(energy("FRANCHISE", "Konzessionsabgabe", v(key("B.grid"), grid, "Franchise Tax", "Konzessionsabgabe")));
        electricityLevies(c);
        return c;
    }

    static CostCase caseC() {
        CostCase c = base("C", "C-gas-rlm", "Kostenrechnung Gas RLM", "Gas RLM - Musterwerk", SITE_MW,
                ContractType.GAS, "V-2025-0201", "50000000001");
        invoices(c, "GAS", "GR", "Energieversorger Nord", "DE000000000", "Marktlokation", "50000000001");
        c.consumption = new Row(id(CLEAN_GAS_ENERGY), "Clean Data", "Hauptzähler Energie Bereinigte", "kWh");
        c.peak = new Row(key("C.hourly"), "Clean Data", "Hauptzähler Energie Stundenwerte", "kWh");
        c.peakFactor = 1;
        c.peakUnit = "kWh/h";
        String supplier = "Gas Supply Contractor";
        String grid = "Gas Grid Operator";
        String msb = "Gas Metering Point Operator";
        c.infos.add(v(key("C.supplier"), supplier, "name", "Lieferant"));
        c.infos.add(v(id(GRID_GAS), grid, "name", "Netzbetreiber"));
        c.infos.add(v(key("C.msb"), msb, "name", "Messstellenbetreiber"));
        c.facts.add(v(id(HEATING_VALUE), "Base Data", "Value", "Brennwert").unit("kWh/m³").filter("Brennwert"));
        c.facts.add(v(key("C.supplier"), supplier, "State Number", "Zustandszahl").unit(""));
        c.positions.add(energy("ENERGY", "Arbeitspreis", v(key("C.supplier"), supplier, "Energy Price", "Arbeitspreis")));
        c.positions.add(demand("GRID_DEMAND", "Netzentgelt Leistungspreis", v(id(GRID_GAS), grid, "Demand Charge", "Leistungspreis Netz")));
        c.positions.add(energy("GRID_ENERGY", "Netzentgelt Arbeitspreis", v(id(GRID_GAS), grid, "Energy Price Grid", "Arbeitspreis Netz")));
        c.positions.add(yearly("MSB", "Messstellenbetrieb", v(key("C.msb"), msb, "Net Cost", "Messstellenbetrieb")));
        c.positions.add(energy("FRANCHISE", "Konzessionsabgabe", v(id(GRID_GAS), grid, "Franchise Tax", "Konzessionsabgabe")));
        gasLevies(c);
        c.deviations.add(deviation("2026-02", InvoiceCase.QUANTITY_FACTOR, null, 1.04, "Verbrauch geschätzt (+4 %)"));
        return c;
    }

    static CostCase caseD() {
        CostCase c = base("D", "D-gas-slp", "Kostenrechnung Gas SLP", "Gas SLP - Außenlager Süd", SITE_S,
                ContractType.GAS, "V-2025-0202", "50000000002");
        invoices(c, "GAS", "GS", "Energieversorger Nord", "DE000000000", "Marktlokation", "50000000002");
        c.consumption = new Row(key("D.clean"), "Clean Data", "Gas Außenlager Volumen", "m³");
        String supplier = "Gas Supply Contractor";
        String grid = "Gas Grid Operator";
        String msb = "Gas Metering Point Operator";
        c.heatingValue = v(id(HEATING_VALUE), "Base Data", "Value", "Brennwert").unit("kWh/m³").filter("Brennwert");
        c.stateNumber = v(key("D.supplier"), supplier, "State Number", "Zustandszahl").unit("");
        c.infos.add(v(key("D.supplier"), supplier, "name", "Lieferant"));
        c.infos.add(v(key("D.grid"), grid, "name", "Netzbetreiber"));
        c.infos.add(v(key("D.msb"), msb, "name", "Messstellenbetreiber"));
        c.positions.add(energy("ENERGY", "Arbeitspreis", v(key("D.supplier"), supplier, "Energy Price", "Arbeitspreis")));
        c.positions.add(yearly("BASE", "Grundpreis", v(key("D.supplier"), supplier, "Additional Fix Cost", "Grundpreis")));
        c.positions.add(yearly("GRID_BASE", "Netzentgelt Grundpreis", v(key("D.grid"), grid, "Net Cost", "Grundpreis Netz")));
        c.positions.add(energy("GRID_ENERGY", "Netzentgelt Arbeitspreis", v(key("D.grid"), grid, "Energy Price Grid", "Arbeitspreis Netz")));
        c.positions.add(yearly("MSB", "Messung", v(key("D.msb"), msb, "Net Cost", "Messung")));
        c.positions.add(energy("FRANCHISE", "Konzessionsabgabe", v(key("D.grid"), grid, "Franchise Tax", "Konzessionsabgabe")));
        gasLevies(c);
        c.deviations.add(deviation("2026-01", InvoiceCase.PREVIOUS_YEAR_PRICE, "ENERGY", null, "Arbeitspreis des Vorjahres berechnet"));
        return c;
    }

    static CostCase caseE() {
        CostCase c = base("E", "E-fernwaerme", "Kostenrechnung Fernwärme", "Fernwärme - Außenlager Süd", SITE_S,
                ContractType.COMMUNITY_HEATING, "V-2025-0301", "");
        invoices(c, "DISTRICT_HEATING", "FW", "Wärmeversorgung Nord", "DE000000002", "Verbrauchsstelle", "WMZ-0001 Außenlager Süd");
        c.consumption = new Row(key("E.clean"), "Clean Data", "Fernwärme Außenlager", "kWh");
        String supplier = "Community Heating Supply Contractor";
        Value capacity = v(key("E.capacity"), "Base Data", "Value", "Anschlusswert").unit("kW").filter("Anschlusswert");
        Value gasIndex = v(key("E.indexGas"), "Base Data", "Value", "Gaspreisindex G (2021 = 100)").unit("").filter("Preisindex Erdgas");
        Value heatIndex = v(key("E.indexHeat"), "Base Data", "Value", "Wärmepreisindex W (2021 = 100)").unit("").filter("Preisindex Wärme");
        c.infos.add(v(key("E.supplier"), supplier, "name", "Lieferant"));
        c.facts.add(capacity);
        c.facts.add(gasIndex);
        c.facts.add(heatIndex);
        Position work = new Position("ENERGY", "Arbeitspreis (Preisgleitklausel)", InvoiceCase.INDEXED,
                v(key("E.supplier"), supplier, "Energy Price", "Arbeitspreis AP0"), "EUR/MWh");
        work.indexConstant = 0.35;
        work.terms.add(new IndexTerm(0.40, gasIndex, 100, "G"));
        work.terms.add(new IndexTerm(0.25, heatIndex, 100, "W"));
        c.positions.add(work);
        Position demand = new Position("CAPACITY", "Leistungspreis", InvoiceCase.CAPACITY,
                v(key("E.supplier"), supplier, "Demand Charge", "Leistungspreis"), "EUR/kW/a");
        demand.capacity = capacity;
        c.positions.add(demand);
        c.positions.add(yearly("MEASURE", "Messpreis", v(key("E.supplier"), supplier, "Additional Fix Cost", "Messpreis")));
        c.vat = dues("VAT Community Heating", "Umsatzsteuer");
        c.deviations.add(deviation("2026-04", InvoiceCase.DUPLICATE_LINE, "MEASURE", null, "Messpreis doppelt berechnet"));
        return c;
    }

    static CostCase caseF() {
        CostCase c = base("F", "F-ladestrom", "Ladestrom-Abrechnung Dienstwagen", "Ladestrom - Dienstwagen Musterwerk", SITE_MW,
                ContractType.ELECTRICITY, "V-2025-0401", "");
        c.chargePoints.add(new ChargePoint("Ladepunkt 1", new Row(id(CLEAN_CHARGE_POINT_1), "Clean Data", "Ladepunkt 1", "kWh"),
                key("F.lp1.plate"), key("F.lp1.cost"), key("F.lp1.person")));
        c.chargePoints.add(new ChargePoint("Ladepunkt 2", new Row(id(CLEAN_CHARGE_POINT_2), "Clean Data", "Ladepunkt 2", "kWh"),
                key("F.lp2.plate"), key("F.lp2.cost"), key("F.lp2.person")));
        c.chargePoints.add(new ChargePoint("Ladepunkt 3", new Row(id(CLEAN_CHARGE_POINT_3), "Clean Data", "Ladepunkt 3", "kWh"),
                key("F.lp3.plate"), key("F.lp3.cost"), key("F.lp3.person")));
        c.chargePrice = v(key("F.price"), "Base Data", "Value", "Ladestrompreis").unit("ct/kWh").filter("Ladestrompreis");
        c.vat = dues("VAT Electricity", "Umsatzsteuer");
        return c;
    }

    // ============================================================================================ invoice cases

    /**
     * @return the invoice case of the JEDemo job for the cost case, null for a case without invoices (F)
     */
    public static InvoiceCase invoiceCase(CostCase c) {
        if (!c.hasInvoices()) return null;
        InvoiceCase ic = new InvoiceCase();
        ic.key = c.key;
        ic.name = c.configurationName;
        ic.medium = c.medium;
        ic.suffix = c.suffix;
        ic.directory = c.invoiceDirectory;
        ic.seller = c.seller;
        ic.sellerVatId = c.sellerVatId;
        ic.meterPointLabel = c.meterPointLabel;
        ic.meterPoint = c.meterPoint;
        ic.consumption = new InvoiceCase.Consumption();
        ic.consumption.row = c.consumption.object;
        if (c.heatingValue != null) {
            ic.consumption.heatingValue = c.heatingValue.invoiceValue();
            ic.consumption.stateNumber = c.stateNumber != null ? c.stateNumber.invoiceValue() : null;
        }
        if (c.peak != null) {
            ic.peak = new InvoiceCase.Peak();
            ic.peak.row = c.peak.object;
            ic.peak.factor = c.peakFactor;
            ic.peak.yearToDate = true;
        }
        ic.vat = c.vat.invoiceValue();
        for (Position p : c.positions) {
            InvoiceCase.Line line = new InvoiceCase.Line();
            line.key = p.key;
            line.text = p.label;
            line.type = p.type;
            line.price = p.price.invoiceValue();
            if (InvoiceCase.ENERGY.equals(p.type) || InvoiceCase.INDEXED.equals(p.type)) line.priceUnit = p.priceUnit;
            if (p.capacity != null) line.capacity = p.capacity.invoiceValue();
            if (InvoiceCase.INDEXED.equals(p.type)) {
                line.index = new InvoiceCase.Index();
                line.index.constant = p.indexConstant;
                for (IndexTerm t : p.terms) {
                    InvoiceCase.Term term = new InvoiceCase.Term();
                    term.weight = t.weight;
                    term.value = t.value.invoiceValue();
                    term.base = t.base;
                    line.index.terms.add(term);
                }
            }
            ic.lines.add(line);
        }
        ic.deviations.addAll(c.deviations);
        return ic;
    }

    /**
     * @return the invoice cases of all cost cases with invoices
     */
    public static List<InvoiceCase> invoiceCases() {
        List<InvoiceCase> list = new ArrayList<>();
        for (CostCase c : cases()) {
            InvoiceCase ic = invoiceCase(c);
            if (ic != null) list.add(ic);
        }
        return list;
    }
}
