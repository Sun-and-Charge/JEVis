/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.trc;

import java.util.UUID;

/**
 * An input of a result calculation template (JSON model of {@code templateInputs}, also used for the
 * {@code selectedInputs} of an accounting configuration). Moved from the Control Center's {@code plugin.dtrc}; the
 * evaluation ({@code CreateValues}, {@code getValue}) is now part of {@link ResultCalculator}.
 */
public class TemplateInput extends TemplateSelected {
    private String id;
    private Boolean quantity = false;
    private String objectClass;
    private String attributeName;
    private String variableName;
    private String variableType;
    private String templateFormula;
    private String filter;
    private Boolean group;
    private String dependency;
    private Boolean timeRestrictionEnabled = false;
    private String fixedTimeFrame;
    private String reducingTimeFrame;

    public TemplateInput() {
        this.id = UUID.randomUUID().toString();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean isQuantity() {
        return quantity;
    }

    public void setQuantity(Boolean isQuantity) {
        quantity = isQuantity;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public void setObjectClass(String objectClass) {
        this.objectClass = objectClass;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public String getVariableType() {
        return variableType;
    }

    public void setVariableType(String variableType) {
        this.variableType = variableType;
    }

    public String getTemplateFormula() {
        return templateFormula;
    }

    public void setTemplateFormula(String templateFormula) {
        this.templateFormula = templateFormula;
    }

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Boolean getGroup() {
        return group;
    }

    public void setGroup(Boolean group) {
        this.group = group;
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

        if (obj instanceof TemplateInput) {
            TemplateInput otherObj = (TemplateInput) obj;
            return this.getId().equals(otherObj.getId());
        }

        return false;
    }

    public void clone(TemplateInput input) {
        setObjectID(input.getObjectID());
        setObjectClass(input.getObjectClass());
        setAttributeName(input.getAttributeName());
        setVariableType(input.getVariableType());
        setFilter(input.getFilter());
        setGroup(input.getGroup());
    }


}
