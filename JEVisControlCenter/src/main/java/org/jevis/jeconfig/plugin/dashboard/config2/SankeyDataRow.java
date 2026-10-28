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
package org.jevis.jeconfig.plugin.dashboard.config2;

import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.commons.classes.JC;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class SankeyDataRow {

    JEVisObject jeVisObject;
    List<JEVisObject> children = new ArrayList<>();



    public SankeyDataRow(JEVisObject jeVisObject) {
        this.jeVisObject = jeVisObject;
    }

    public JEVisObject getJeVisObject() {
        return jeVisObject;
    }

    public void setJeVisObject(JEVisObject jeVisObject) {
        this.jeVisObject = jeVisObject;
    }

    public List<JEVisObject> getChildren() {
        return children;
    }

    public void setChildren(List<JEVisObject> children) {
        this.children = children;
    }

    public void addChildren(JEVisObject child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }
    public void setAllChildren(List<SankeyDataRow> children) {
        this.children.clear();
        this.children.addAll(children.stream().map(sankeyDataRow -> sankeyDataRow.getJeVisObject()).collect(Collectors.toList()));
    }


    @Override
    public String toString() {
        try {
            if (jeVisObject.getJEVisClassName().equals(JC.Data.CleanData.name)) {
                return jeVisObject.getID() + ": " + jeVisObject.getParent().getName() + " / " + jeVisObject.getName();

            }else {
                return jeVisObject.getID() + ": " + jeVisObject.getName();
            }
        } catch (JEVisException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SankeyDataRow that = (SankeyDataRow) o;
        return Objects.equals(jeVisObject.getID(), that.jeVisObject.getID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(jeVisObject);
    }
}
