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
package org.jevis.jeconfig.plugin.dashboard.widget;

import javafx.scene.image.Image;

public class WidgetSelection {


    Image icon;
    String classname;
    String displayname;
    String type;

    public WidgetSelection(String classname, String type, String displayname, Image icon) {
        this.classname = classname;
        this.displayname = displayname;
        this.icon = icon;
        this.type = type;
    }

    public Image getIcon() {
        return icon;
    }

    public String getClassname() {
        return classname;
    }

    public String getDisplayname() {
        return displayname;
    }

    public String getType() {
        return type;
    }
}
