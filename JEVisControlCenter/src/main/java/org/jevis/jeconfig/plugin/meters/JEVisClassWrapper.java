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
package org.jevis.jeconfig.plugin.meters;

import org.jevis.api.JEVisClass;
import org.jevis.jeconfig.application.application.I18nWS;

public class JEVisClassWrapper {
    JEVisClass jeVisClass;

    public JEVisClassWrapper(JEVisClass jeVisClass) {
        this.jeVisClass = jeVisClass;
    }

    @Override
    public String toString() {
        try {
            return I18nWS.getInstance().getClassName(jeVisClass);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "N/A";
    }

    public JEVisClass getJeVisClass() {
        return jeVisClass;
    }

    public void setJeVisClass(JEVisClass jeVisClass) {
        this.jeVisClass = jeVisClass;
    }
}
