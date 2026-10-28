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
package org.jevis.jeconfig.application.jevistree.plugin;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;
import org.jevis.jeconfig.application.jevistree.JEVisTreeRow;
import org.joda.time.DateTime;

/**
 * @author fs
 */
public class SimpleTargetPluginData {

    private JEVisTreeRow row;
    private boolean selected = false;
    private String uuid = "";

    public SimpleTargetPluginData() {
    }

    public SimpleTargetPluginData(JEVisTreeRow row) {
        this.row = row;

        if (row.getJEVisAttribute() != null) {
            uuid = getObj().getID() + ":" + getAtt().getName() + "--" + DateTime.now().toString();
        } else {
            uuid = getObj().getID() + "--" + DateTime.now().toString();
        }
    }

    public JEVisTreeRow getRow() {
        return row;
    }

    public void setRow(JEVisTreeRow row) {
        this.row = row;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public JEVisObject getObj() {
        return row.getJEVisObject();
    }

    public JEVisAttribute getAtt() {
        return row.getJEVisAttribute();
//        return _att;
    }

    @Override
    public String toString() {
        return "SimpleTargetPluginData{" + "uuid=" + uuid + '}';
    }

}
