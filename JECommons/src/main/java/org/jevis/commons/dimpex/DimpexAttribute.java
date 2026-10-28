/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dimpex;

import org.jevis.commons.ws.json.JsonSample;
import org.jevis.commons.ws.json.JsonUnit;
import org.joda.time.Period;

import java.util.ArrayList;
import java.util.List;

public class DimpexAttribute {


    private String name = "";
    private JsonUnit inputUnit = new JsonUnit();
    private JsonUnit displayUnit = new JsonUnit();
    private String inputRate = Period.ZERO.toString();
    private String displayRate = Period.ZERO.toString();
    private String sampleID = "";
    private List<JsonSample> samples = new ArrayList<>();


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }

    public JsonUnit getInputUnit() {
        return inputUnit;
    }

    public void setInputUnit(JsonUnit inputUnit) {
        this.inputUnit = inputUnit;
    }

    public JsonUnit getDisplayUnit() {
        return displayUnit;
    }

    public void setDisplayUnit(JsonUnit displayUnit) {
        this.displayUnit = displayUnit;
    }

    public String getInputRate() {
        return inputRate;
    }

    public void setInputRate(String inputRate) {
        this.inputRate = inputRate;
    }

    public String getDisplayRate() {
        return displayRate;
    }

    public void setDisplayRate(String displayRate) {
        this.displayRate = displayRate;
    }

    public String getSampleID() {
        return sampleID;
    }

    public void setSampleID(String sampleID) {
        this.sampleID = sampleID;
    }

    public List<JsonSample> getSamples() {
        return samples;
    }

    public void setSamples(List<JsonSample> samples) {
        this.samples = samples;
    }
}
