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
package org.jevis.jeconfig.tool.template;

import javafx.scene.Node;
import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.joda.time.DateTime;

import java.util.HashMap;
import java.util.Map;

public abstract class Template {

    public String getName() {
        return "";
    }

    public boolean create(JEVisClass jclass, JEVisObject parent, String name) throws JEVisException, InterruptedException {
        return false;
    }

    public boolean supportsClass(JEVisClass jclass) throws JEVisException {
        return false;
    }

    public void setAttribute(JEVisObject object, String attribute, DateTime ts, Object value) throws JEVisException {
        try {
            JEVisAttribute periodAttribute = object.getAttribute(attribute);
            periodAttribute.buildSample(ts, value).commit();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public Map<String, Node> getOptions() {
        return new HashMap<>();
    }

    public boolean isNotAnTemplate() {
        return false;
    }
}
