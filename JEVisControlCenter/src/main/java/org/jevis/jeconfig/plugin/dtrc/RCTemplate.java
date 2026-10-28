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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.MapSerializer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RCTemplate {

    private List<TemplateInput> templateInputs = new ArrayList<>();
    private List<TemplateInput> templateFormulaInputs = new ArrayList<>();
    private List<TemplateOutput> templateOutputs = new ArrayList<>();
    private List<TemplateFormula> templateFormulas = new ArrayList<>();
    @JsonSerialize(keyUsing = MapSerializer.class)
    @JsonProperty("intervalSelectorConfiguration")
    private Map<String, Boolean> intervalSelectorConfiguration = new HashMap<>();


    public List<TemplateInput> getTemplateInputs() {
        return templateInputs;
    }

    public void setTemplateInputs(List<TemplateInput> templateInputs) {
        this.templateInputs = templateInputs;
    }

    public List<TemplateOutput> getTemplateOutputs() {
        return templateOutputs;
    }

    public void setTemplateOutputs(List<TemplateOutput> templateOutputs) {
        this.templateOutputs = templateOutputs;
    }

    public List<TemplateFormula> getTemplateFormulas() {
        return templateFormulas;
    }

    public void setTemplateFormulas(List<TemplateFormula> templateFormulas) {
        this.templateFormulas = templateFormulas;
    }

    public List<TemplateInput> getTemplateFormulaInputs() {
        return templateFormulaInputs;
    }

    public void setTemplateFormulaInputs(List<TemplateInput> templateFormulaInputs) {
        this.templateFormulaInputs = templateFormulaInputs;
    }

    public Map<String, Boolean> getIntervalSelectorConfiguration() {
        return intervalSelectorConfiguration;
    }

    public void setIntervalSelectorConfiguration(Map<String, Boolean> intervalSelectorConfiguration) {
        this.intervalSelectorConfiguration = intervalSelectorConfiguration;
    }
}
