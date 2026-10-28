/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEWebService.
 * <p>
 * JEWebService is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEWebService is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEWebService. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEWebService is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.iso.classes;

import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.SQLDataSource;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class EnergyConsumption extends EvaluatedOutput {

    private List<Double> listConsumption;
    private long billsID;
    private String nameBills;
    private double shareoftotalconsumption;
    private double costs;
    private double costrelated;
    private double co2emissions;
    private double co2shareoftotal;

    public EnergyConsumption(SQLDataSource ds, JsonObject input) throws Exception {
        super(ds, input);
        billsID = 0L;
        nameBills = "";
        shareoftotalconsumption = 0.0;
        costs = 0.0;
        costrelated = 0.0;
        co2emissions = 0.0;
        co2shareoftotal = 0.0;
        listConsumption = new ArrayList<>();
        this.listConsumption = getList();

    }

    public EnergyConsumption() {
        listConsumption = new ArrayList<>();
        billsID = 0L;
        nameBills = "";
        shareoftotalconsumption = 0.0;
        costs = 0.0;
        costrelated = 0.0;
        co2emissions = 0.0;
        co2shareoftotal = 0.0;
    }

    public List<Double> getListConsumption() {
        return listConsumption;
    }

    public void setListConsumption(List<Double> listConsumption) {
        this.listConsumption = listConsumption;
    }

    public long getBillsID() {
        return billsID;
    }

    public void setBillsID(long billsID) {
        this.billsID = billsID;
    }

    @Override
    public String toString() {
        return "EnergyConsumption{" + "ID=" + ID + ", EnergySupplier=" + EnergySupplier + ", Year=" + year + ", January=" + January + ", February=" + February + ", March=" + March + ", April=" + April + ", May=" + May + ", June=" + June + ", July=" + July + ", August=" + August + ", September=" + September + ", October=" + October + ", November=" + November + ", December=" + December + '}';
    }

    public String getNameBills() {
        return nameBills;
    }

    public void setNameBills(String nameBills) {
        this.nameBills = nameBills;
    }

    public double getShareoftotalconsumption() {
        return shareoftotalconsumption;
    }

    public void setShareoftotalconsumption(double shareoftotalconsumption) {
        this.shareoftotalconsumption = shareoftotalconsumption;
    }

    public double getCosts() {
        return costs;
    }

    public void setCosts(double costs) {
        this.costs = costs;
    }

    public double getCostrelated() {
        return costrelated;
    }

    public void setCostrelated(double costrelated) {
        this.costrelated = costrelated;
    }

    public double getCo2emissions() {
        return co2emissions;
    }

    public void setCo2emissions(double co2emissions) {
        this.co2emissions = co2emissions;
    }

    public double getCo2shareoftotal() {
        return co2shareoftotal;
    }

    public void setCo2shareoftotal(double co2shareoftotal) {
        this.co2shareoftotal = co2shareoftotal;
    }

}
