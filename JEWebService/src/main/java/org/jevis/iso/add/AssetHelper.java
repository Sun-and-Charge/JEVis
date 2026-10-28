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
package org.jevis.iso.add;

/**
 * @author <gerrit.schutz@sunandcharge.com>Gerrit Schutz</gerrit.schutz@sunandcharge.com>
 */
public class AssetHelper {

    private String name = new String();
    private Double yearlyconsumption = 0.0;
    private String energysource = new String();
    private Double shareoftotalesconsumption = 0.0;
    private Double shareoftotalconsumption = 0.0;
    private Double co2emissions = 0.0;
    private Double co2shareoftotal = 0.0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEnergysource() {
        return energysource;
    }

    public void setEnergysource(String energysource) {
        this.energysource = energysource;
    }

    public Double getYearlyconsumption() {
        return yearlyconsumption;
    }

    public void setYearlyconsumption(Double yearlyconsumption) {
        this.yearlyconsumption = yearlyconsumption;
    }

    public Double getShareoftotalconsumption() {
        return shareoftotalconsumption;
    }

    public void setShareoftotalconsumption(Double shareoftotalconsumption) {
        this.shareoftotalconsumption = shareoftotalconsumption;
    }

    public Double getCo2emissions() {
        return co2emissions;
    }

    public void setCo2emissions(Double co2emissions) {
        this.co2emissions = co2emissions;
    }

    public Double getCo2shareoftotal() {
        return co2shareoftotal;
    }

    public void setCo2shareoftotal(Double co2shareoftotal) {
        this.co2shareoftotal = co2shareoftotal;
    }

    public Double getShareoftotalesconsumption() {
        return shareoftotalesconsumption;
    }

    public void setShareoftotalesconsumption(Double shareoftotalesconsumption) {
        this.shareoftotalesconsumption = shareoftotalesconsumption;
    }


}
