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
package org.jevis.jeconfig.application.login;

import javafx.beans.property.SimpleBooleanProperty;

/**
 *
 * @author fs
 */
public class PreloadTask {

    private String name = "";
    private SimpleBooleanProperty runProperty = new SimpleBooleanProperty(false);

    public PreloadTask(String name, boolean start) {
        this.name = name;
        this.runProperty.set(start);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public SimpleBooleanProperty getRunProperty() {
        return runProperty;
    }

    public void setRunProperty(SimpleBooleanProperty runProperty) {
        this.runProperty = runProperty;
    }

}
