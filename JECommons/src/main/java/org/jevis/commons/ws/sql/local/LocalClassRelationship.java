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

import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisClassRelationship;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.commons.ws.json.JsonClassRelationship;

import java.util.Objects;

/**
 * {@link JEVisClassRelationship} of a {@link LocalClass}; behaves like {@code JEVisClassRelationshipWS}.
 */
public class LocalClassRelationship implements JEVisClassRelationship {

    private final LocalDataSource ds;
    private final JsonClassRelationship json;

    LocalClassRelationship(LocalDataSource ds, JsonClassRelationship json) {
        this.ds = ds;
        this.json = json;
    }

    @Override
    public JEVisClass getStart() {
        return ds.getJEVisClass(json.getStart());
    }

    @Override
    public String getStartName() {
        return json.getStart();
    }

    @Override
    public JEVisClass getEnd() {
        return ds.getJEVisClass(json.getEnd());
    }

    @Override
    public String getEndName() {
        return json.getEnd();
    }

    @Override
    public int getType() {
        return json.getType();
    }

    @Override
    public JEVisClass[] getJEVisClasses() {
        return new JEVisClass[]{getStart(), getEnd()};
    }

    @Override
    public JEVisClass getOtherClass(JEVisClass jclass) throws JEVisException {
        if (jclass != null && jclass.getName().equals(json.getStart())) {
            return getEnd();
        }
        return getStart();
    }

    @Override
    public String getOtherClassName(String name) {
        if (json.getStart().equals(name)) {
            return json.getEnd();
        }
        return json.getStart();
    }

    @Override
    public boolean isType(int type) {
        return type == getType();
    }

    /**
     * Not part of the class definition files; always {@code false}.
     */
    @Override
    public boolean isInherited() {
        return false;
    }

    @Override
    public JEVisDataSource getDataSource() {
        return ds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LocalClassRelationship)) return false;
        LocalClassRelationship other = (LocalClassRelationship) o;
        return json.getType() == other.json.getType()
                && Objects.equals(json.getStart(), other.json.getStart())
                && Objects.equals(json.getEnd(), other.json.getEnd());
    }

    @Override
    public int hashCode() {
        return Objects.hash(json.getStart(), json.getEnd(), json.getType());
    }

    @Override
    public String toString() {
        return "LocalClassRelationship{from=" + json.getStart() + "  to=" + json.getEnd() + "  type=" + json.getType() + '}';
    }
}
