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
package org.jevis.jeconfig.plugin.meters.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jevis.api.JEVisType;

public class JEVisTypeWrapper {

    private static final Logger logger = LogManager.getLogger(JEVisTypeWrapper.class);
    private final JEVisType jeVisType;
    private String name;

    public JEVisTypeWrapper(JEVisType jeVisType) {
        this.jeVisType = jeVisType;
        if (jeVisType == null) return;
        try {
            this.name = jeVisType.getName();

        } catch (Exception e) {
            logger.error(e);
        }
    }

    public JEVisType getJeVisType() {
        return jeVisType;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o.getClass() != this.getClass()) return false;
        return ((JEVisTypeWrapper) o).getName().equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }


    @Override
    public String toString() {
        return "JEVisTypeWrapper{" +
                "jeVisType=" + jeVisType +
                ", name='" + name + '\'' +
                '}';
    }
}
