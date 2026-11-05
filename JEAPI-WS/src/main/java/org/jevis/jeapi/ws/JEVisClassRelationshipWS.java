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

import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonClassRelationship;

/**
 * @author fs
 */
public class JEVisClassRelationshipWS implements org.jevis.api.JEVisClassRelationship {

    private JEVisDataSourceWS ds;
    private JsonClassRelationship json;

    public JEVisClassRelationshipWS(JEVisDataSourceWS ds, JsonClassRelationship json) {
        this.ds = ds;
//        this.jclass = jclass;
        this.json = json;
    }

    @Override
    public String getStartName() {
        return json.getStart();
    }

    @Override
    public String getEndName() {
        return json.getEnd();
    }

    @Override
    public String getOtherClassName(String name) {
        if (json.getStart().equals(name)) {
            return json.getEnd();
        } else {
            return json.getStart();
        }
    }

    @Override
    public JEVisClass getStart() throws JEVisException {
        return ds.getJEVisClass(json.getStart());
    }

    @Override
    public JEVisClass getEnd() throws JEVisException {
        return ds.getJEVisClass(json.getEnd());
    }

    @Override
    public int getType() {
        return json.getType();
    }

    @Override
    public JEVisClass[] getJEVisClasses() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JEVisClass getOtherClass(JEVisClass jclass) throws JEVisException {
        if (getStart().equals(jclass)) {
            return getEnd();
        } else {
            return getStart();
        }
    }

    @Override
    public boolean isType(int type) {
        return type == getType();
    }

    @Override
    public boolean isInherited() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public String toString() {
        return "JEVisClassRelationshipWS{" +
                "from=" + json.getStart() +
                "  to=" + json.getEnd() +
                "  type=" + json.getType() +
                '}';
    }

    /**
     * Two class relationships are equal if start, end and type are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JEVisClassRelationshipWS)) return false;
        JEVisClassRelationshipWS other = (JEVisClassRelationshipWS) o;
        return json.getType() == other.json.getType()
                && java.util.Objects.equals(json.getStart(), other.json.getStart())
                && java.util.Objects.equals(json.getEnd(), other.json.getEnd());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(json.getStart(), json.getEnd(), json.getType());
    }
}
