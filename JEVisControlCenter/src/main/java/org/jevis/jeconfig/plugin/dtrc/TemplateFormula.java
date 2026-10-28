/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JEVisControlCenter.
 * <p>
 * JEVisControlCenter is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JEVisControlCenter is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEVisControlCenter. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEVisControlCenter is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig.plugin.dtrc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TemplateFormula {

    private String id;
    private String name = "";
    private String formula = "";
    private List<String> inputIds = new ArrayList<>();
    private String output = "";
    private Boolean timeRestrictionEnabled = false;
    private String fixedTimeFrame;
    private String reducingTimeFrame;

    public TemplateFormula() {
        id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFormula() {
        return formula;
    }

    public void setFormula(String formula) {
        this.formula = formula;
    }

    public List<String> getInputIds() {
        return inputIds;
    }

    public void setInputIds(List<String> inputIds) {
        this.inputIds = inputIds;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Boolean getTimeRestrictionEnabled() {
        return timeRestrictionEnabled;
    }

    public void setTimeRestrictionEnabled(Boolean timeRestrictionEnabled) {
        this.timeRestrictionEnabled = timeRestrictionEnabled;
    }

    public String getFixedTimeFrame() {
        return fixedTimeFrame;
    }

    public void setFixedTimeFrame(String fixedTimeFrame) {
        this.fixedTimeFrame = fixedTimeFrame;
    }

    public String getReducingTimeFrame() {
        return reducingTimeFrame;
    }

    public void setReducingTimeFrame(String reducingTimeFrame) {
        this.reducingTimeFrame = reducingTimeFrame;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TemplateFormula) {
            TemplateFormula otherObj = (TemplateFormula) obj;
            return this.getId().equals(otherObj.getId());
        }

        return false;
    }
}
