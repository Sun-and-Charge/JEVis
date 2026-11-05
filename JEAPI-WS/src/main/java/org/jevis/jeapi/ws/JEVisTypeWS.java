/**
 * Copyright (C) 2016 Envidatec GmbH <info@envidatec.com>
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEAPI-WS.
 * <p>
 * JEAPI-WS is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEAPI-WS is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEAPI-WS. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEAPI-WS is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeapi.ws;

import org.jevis.api.*;
import org.jevis.commons.unit.JEVisUnitImp;
import org.jevis.commons.ws.json.JsonType;
import tech.units.indriya.AbstractUnit;


/**
 * Attribute type of a {@link JEVisClassWS}. Types are stored as part of their class: {@link #commit()} and
 * {@link #delete()} save the whole class (see {@link JEVisClassWS#commit()}).
 *
 * @author fs
 */
public class JEVisTypeWS implements JEVisType {

    private String jclass = null;
    private final JEVisDataSourceWS ds;
    private final JsonType json;
    /**
     * the class instance this type belongs to, may be {@code null} (then the class is looked up by name)
     */
    private JEVisClassWS owner;

    public JEVisTypeWS(JEVisDataSourceWS ds, JsonType json, String jclass) {
        this(ds, json, jclass, null);
    }

    public JEVisTypeWS(JEVisDataSourceWS ds, JsonType json, String jclass, JEVisClassWS owner) {
        this.jclass = jclass;
        this.ds = ds;
        this.json = json;
        this.owner = owner;
    }

    public JEVisTypeWS(JEVisDataSourceWS ds, String name, String jclass) {
        this(ds, name, jclass, null);
    }

    public JEVisTypeWS(JEVisDataSourceWS ds, String name, String jclass, JEVisClassWS owner) {
        this.json = new JsonType();
        this.json.setName(name);
        this.jclass = jclass;
        this.ds = ds;
        this.owner = owner;
    }

    /**
     * @return the JSON of this type (changes are visible to the owning class)
     */
    JsonType getJson() {
        return json;
    }

    private JEVisClassWS getOwner() throws JEVisException {
        if (owner == null) {
            JEVisClass jevisClass = ds.getJEVisClass(jclass);
            if (!(jevisClass instanceof JEVisClassWS)) {
                throw new JEVisException("The class '" + jclass + "' of the type '" + getName() + "' does not exist.", 404);
            }
            owner = (JEVisClassWS) jevisClass;
        }
        return owner;
    }

    @Override
    public String getName() {
        return json.getName();
    }

    @Override
    public void setName(String name) {
        json.setName(name);
    }

    @Override
    public int getPrimitiveType() {
        return json.getPrimitiveType();
    }

    @Override
    public void setPrimitiveType(int type) {
        json.setPrimitiveType(type);
    }

    @Override
    public String getGUIDisplayType() {
        return json.getGuiType();
    }

    @Override
    public void setGUIDisplayType(String type) {
        json.setGuiType(type);
    }

    @Override
    public int getGUIPosition() {
        return json.getGUIPosition();
    }

    @Override
    public void setGUIPosition(int pos) {
        json.setGUIPosition(pos);
    }

    @Override
    public JEVisClass getJEVisClass() {
        return ds.getJEVisClass(jclass);
    }

    @Override
    public int getValidity() {
        return json.getValidity();
    }

    @Override
    public void setValidity(int validity) {
        json.setValidity(validity);
    }

    @Override
    public String getConfigurationValue() {
        return "";
    }

    @Override
    public void setConfigurationValue(String value) {
    }

    @Override
    public JEVisUnit getUnit() {
        return new JEVisUnitImp(AbstractUnit.ONE);
    }

    @Override
    public void setUnit(JEVisUnit unit) {
    }

    @Override
    public String getAlternativSymbol() {
        return "";
    }

    @Override
    public void setAlternativSymbol(String symbol) {
    }

    @Override
    public String getDescription() {
        return json.getDescription();
    }

    @Override
    public void setDescription(String discription) {
        json.setDescription(discription);
    }

    /**
     * Removes this type from its class and saves the class. The web service refuses this while objects have
     * values in this attribute.
     *
     * @throws JEVisException with the message of the web service if the class could not be saved
     */
    @Override
    public boolean delete() throws JEVisException {
        JEVisClassWS jevisClass = getOwner();
        jevisClass.deleteType(getName());
        jevisClass.commit();
        return true;
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    /**
     * Saves the class of this type, including this type.
     *
     * @throws JEVisException with the message of the web service if the class could not be saved
     */
    @Override
    public void commit() throws JEVisException {
        JEVisClassWS jevisClass = getOwner();
        boolean contained = false;
        for (JEVisType type : jevisClass.getTypes()) {
            if (type == this) {
                contained = true;
                break;
            }
        }
        if (!contained) {
            JEVisType existing = jevisClass.getType(getName());
            if (existing != null) {
                jevisClass.getTypes().remove(existing);
            }
            if (json.getJevisClass() == null) {
                json.setJevisclass(jclass);
            }
            jevisClass.getTypes().add(this);
        }
        jevisClass.commit();
    }

    @Override
    public void rollBack() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean hasChanged() {
        //TODO: has Changed
        return false;
    }

    @Override
    public int compareTo(JEVisType o
    ) {
        try {
            return getName().compareTo(o.getName());
        } catch (Exception ex) {
            return 0;
        }
    }

    @Override
    public String getJEVisClassName() {
        return jclass;
    }

    /**
     * @return {@code true} if the type is marked as inherited or the super class of its class has a type with the
     * same name
     */
    @Override
    public boolean isInherited() {
        if (json.getInherited()) {
            return true;
        }
        try {
            JEVisClass superClass = getOwner().getInheritance();
            return superClass != null && superClass.getType(getName()) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void setInherited(boolean inherited) {
        json.setInherited(inherited);
    }

    @Override
    public String toString() {
        return "JEVisTypeWS{" +
                "jclass='" + jclass + '\'' +
                ", json=" + json +
                '}';
    }

    @Override
    public boolean equals(Object obj
    ) {
        try {
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            /**
             * cast needs to be removed
             */
            final JEVisType other = (JEVisType) obj;
            if ((this.getName() == null) ? (other.getName() != null) : !this.getName().equals(other.getName())) {
                return false;
            }
            return this.getJEVisClass() == other.getJEVisClass() || (this.getJEVisClass() != null && this.getJEVisClass().equals(other.getJEVisClass()));
        } catch (Exception ex) {
            return false;
        }
    }

}
