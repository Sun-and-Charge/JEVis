/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see
 * <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql.local;

import org.jevis.api.*;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonType;
import tech.units.indriya.AbstractUnit;

/**
 * Read-only {@link JEVisType} of a {@link LocalClass}; behaves like {@code JEVisTypeWS}.
 */
public class LocalType implements JEVisType {

    private final LocalDataSource ds;
    private final JsonType json;
    private final String jclass;

    LocalType(LocalDataSource ds, JsonType json, String jclass) {
        this.ds = ds;
        this.json = json;
        this.jclass = jclass;
    }

    @Override
    public String getName() {
        return json.getName();
    }

    @Override
    public int getPrimitiveType() {
        return json.getPrimitiveType();
    }

    @Override
    public String getGUIDisplayType() {
        return json.getGuiType();
    }

    @Override
    public int getGUIPosition() {
        return json.getGUIPosition();
    }

    @Override
    public JEVisClass getJEVisClass() {
        return ds.getJEVisClass(jclass);
    }

    @Override
    public String getJEVisClassName() {
        return jclass;
    }

    @Override
    public int getValidity() {
        return json.getValidity();
    }

    /**
     * Like the client: always empty.
     */
    @Override
    public String getConfigurationValue() {
        return "";
    }

    /**
     * Like the client: always {@code ONE}.
     */
    @Override
    public JEVisUnit getUnit() {
        return new JEVisUnitImp(AbstractUnit.ONE);
    }

    /**
     * Like the client: always empty.
     */
    @Override
    public String getAlternativSymbol() {
        return "";
    }

    @Override
    public String getDescription() {
        return json.getDescription();
    }

    /**
     * Inherited if marked so or if the super class has a type of the same name.
     */
    @Override
    public boolean isInherited() {
        if (json.getInherited()) {
            return true;
        }
        try {
            JEVisClass owner = getJEVisClass();
            JEVisClass superClass = owner != null ? owner.getInheritance() : null;
            return superClass != null && superClass.getType(getName()) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    // ---------------------------------------------------------------- write operations

    @Override
    public void setName(String name) {
        throw LocalDataSource.readOnly("setName");
    }

    @Override
    public void setPrimitiveType(int type) {
        throw LocalDataSource.readOnly("setPrimitiveType");
    }

    @Override
    public void setGUIDisplayType(String type) {
        throw LocalDataSource.readOnly("setGUIDisplayType");
    }

    @Override
    public void setGUIPosition(int pos) {
        throw LocalDataSource.readOnly("setGUIPosition");
    }

    @Override
    public void setValidity(int validity) {
        throw LocalDataSource.readOnly("setValidity");
    }

    @Override
    public void setConfigurationValue(String value) {
        throw LocalDataSource.readOnly("setConfigurationValue");
    }

    @Override
    public void setUnit(JEVisUnit unit) {
        throw LocalDataSource.readOnly("setUnit");
    }

    @Override
    public void setAlternativSymbol(String symbol) {
        throw LocalDataSource.readOnly("setAlternativSymbol");
    }

    @Override
    public void setDescription(String description) {
        throw LocalDataSource.readOnly("setDescription");
    }

    @Override
    public void setInherited(boolean inherited) {
        throw LocalDataSource.readOnly("setInherited");
    }

    @Override
    public boolean delete() {
        throw LocalDataSource.readOnly("delete");
    }

    @Override
    public void commit() {
        throw LocalDataSource.readOnly("commit");
    }

    @Override
    public void rollBack() {
        throw LocalDataSource.readOnly("rollBack");
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public int compareTo(JEVisType o) {
        try {
            return getName().compareTo(o.getName());
        } catch (Exception ex) {
            return 0;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LocalType)) {
            return false;
        }
        LocalType other = (LocalType) obj;
        return java.util.Objects.equals(getName(), other.getName()) && java.util.Objects.equals(jclass, other.jclass);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(getName(), jclass);
    }

    @Override
    public String toString() {
        return "LocalType{jclass='" + jclass + "', name='" + getName() + "'}";
    }
}
