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
package org.jevis.jeconfig.plugin.equipment;

import javafx.collections.transformation.FilteredList;
import javafx.scene.Node;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import org.jevis.api.JEVisClass;
import org.jevis.jeconfig.plugin.RegisterTableRow;

public class JEVisClassTab extends Tab {

    private JEVisClass jeVisClass;
    private FilteredList<RegisterTableRow> filteredList;

    public JEVisClassTab() {
        super();
    }

    public JEVisClassTab(String s, Node node) {
        super(s, node);
    }

    public JEVisClassTab(JEVisClass jeVisClass) {
        super();
        this.jeVisClass = jeVisClass;
    }

    public JEVisClassTab(String className, TableView<RegisterTableRow> tableView, JEVisClass jeVisClass) {
        super(className, tableView);
        this.jeVisClass = jeVisClass;
    }

    public void setClassName(String className) {
        setText(className);
    }

    public void setTableView(TableView<RegisterTableRow> tableView) {
        setContent(tableView);
    }

    public void setJEVisClass(JEVisClass jeVisClass) {
        this.jeVisClass = jeVisClass;
    }

    public JEVisClass getJeVisClass() {
        return jeVisClass;
    }

    public FilteredList<RegisterTableRow> getFilteredList() {
        return filteredList;
    }

    public void setFilteredList(FilteredList<RegisterTableRow> filteredList) {
        this.filteredList = filteredList;
    }
}
