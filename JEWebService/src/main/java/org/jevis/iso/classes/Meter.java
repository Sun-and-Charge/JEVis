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

import org.jevis.commons.ws.json.JsonAttribute;
import org.jevis.commons.ws.json.JsonObject;
import org.jevis.commons.ws.sql.SQLDataSource;

import java.util.List;

import static org.jevis.iso.add.Snippets.getValueString;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class Meter {
    private long ID;
    private String name;

    private Double ConversionFactor;
    private String CurrentTransformer;
    private String InstallationDate;
    private String Interface;
    private String Type;
    private String VoltageTransformer;

    Meter(SQLDataSource ds, JsonObject input) throws Exception {
        ID = 0L;
        name = "";
        ConversionFactor = 0.0;
        CurrentTransformer = "";
        InstallationDate = "";
        Type = "";
        Interface = "";
        VoltageTransformer = "";
        this.ID = input.getId();
        this.name = input.getName();

        List<JsonAttribute> listEnergyConsumptionAttributes = ds.getAttributes(input.getId());

        for (JsonAttribute att : listEnergyConsumptionAttributes) {
            String name = att.getType();

            final String attVoltageTransformer = "Voltage Transformer";
            final String attType = "Type";
            final String attInterface = "Interface";
            final String attInstallationDate = "Installation Date";
            final String attCurrentTransformer = "Current Transformer";
            final String attConversionFactor = "Conversion Factor";
            switch (name) {
                case attConversionFactor:
                    if (getValueString(att, "") != "") {
                        this.setConversionFactor(Double.parseDouble(getValueString(att, "")));
                    } else this.setConversionFactor(0.0);
                    break;
                case attCurrentTransformer:
                    this.setCurrentTransformer(getValueString(att, ""));
                    break;
                case attInstallationDate:
                    this.setInstallationDate(getValueString(att, ""));
                    break;
                case attInterface:
                    this.setInterface(getValueString(att, ""));
                    break;
                case attType:
                    this.setType(getValueString(att, ""));
                    break;
                case attVoltageTransformer:
                    this.setVoltageTransformer(getValueString(att, ""));
                    break;
                default:
                    break;
            }
        }

    }

    public long getID() {
        return ID;
    }

    public void setID(long ID) {
        this.ID = ID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getConversionFactor() {
        return ConversionFactor;
    }

    public void setConversionFactor(Double ConversionFactor) {
        this.ConversionFactor = ConversionFactor;
    }

    public String getCurrentTransformer() {
        return CurrentTransformer;
    }

    public void setCurrentTransformer(String CurrentTransformer) {
        this.CurrentTransformer = CurrentTransformer;
    }

    public String getInstallationDate() {
        return InstallationDate;
    }

    public void setInstallationDate(String InstallationDate) {
        this.InstallationDate = InstallationDate;
    }

    public String getInterface() {
        return Interface;
    }

    public void setInterface(String Interface) {
        this.Interface = Interface;
    }

    public String getType() {
        return Type;
    }

    public void setType(String Type) {
        this.Type = Type;
    }

    public String getVoltageTransformer() {
        return VoltageTransformer;
    }

    public void setVoltageTransformer(String VoltageTransformer) {
        this.VoltageTransformer = VoltageTransformer;
    }

}
