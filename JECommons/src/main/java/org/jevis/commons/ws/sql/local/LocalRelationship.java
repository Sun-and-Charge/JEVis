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

import org.jevis.api.JEVisObject;
import org.jevis.api.JEVisRelationship;
import org.jevis.commons.ws.json.JsonRelationship;

/**
 * Read-only {@link JEVisRelationship} of a {@link LocalDataSource}; behaves like {@code JEVisRelationshipWS}. The
 * objects are resolved through the data source, so an end the user may not read is {@code null}.
 */
public class LocalRelationship implements JEVisRelationship {

    private final LocalDataSource ds;
    private final JsonRelationship json;

    LocalRelationship(LocalDataSource ds, JsonRelationship json) {
        this.ds = ds;
        this.json = json;
    }

    @Override
    public JEVisObject getStartObject() {
        return ds.getObject(json.getFrom());
    }

    @Override
    public long getStartID() {
        return json.getFrom();
    }

    @Override
    public long getEndID() {
        return json.getTo();
    }

    @Override
    public JEVisObject getEndObject() {
        return ds.getObject(json.getTo());
    }

    @Override
    public JEVisObject[] getObjects() {
        return new JEVisObject[]{getStartObject(), getEndObject()};
    }

    @Override
    public JEVisObject getOtherObject(JEVisObject object) {
        if (object.getID() == getStartID()) {
            return getEndObject();
        }
        return getStartObject();
    }

    @Override
    public int getType() {
        return json.getType();
    }

    @Override
    public boolean isType(int type) {
        return json.getType() == type;
    }

    @Override
    public void delete() {
        throw LocalDataSource.readOnly("delete");
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof LocalRelationship)) {
            return false;
        }
        LocalRelationship other = (LocalRelationship) o;
        return json.getFrom() == other.json.getFrom() && json.getTo() == other.json.getTo()
                && json.getType() == other.json.getType();
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(json.getFrom(), json.getTo(), json.getType());
    }

    @Override
    public String toString() {
        return "LocalRelationship{startobj=" + json.getFrom() + ", endobj=" + json.getTo() + ", type=" + json.getType() + '}';
    }
}
