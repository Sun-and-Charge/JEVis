/**
 * Copyright (C) JEVis contributors
 * Copyright (C) 2026 Sun and Charge Germany GmbH
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
package org.jevis.jeconfig.application.jevistree.filter;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisObject;
import org.jevis.commons.CommonClasses;
import org.jevis.jeconfig.application.jevistree.JEVisTreeRow;

public class ObjectAttributeFilter {

    public static final String NONE = "NONE";
    public static final String ALL = "*";

    private String attributeName = "";
    private String objectName = "";

    public ObjectAttributeFilter(String objectName, String attributeName) {
        this.attributeName = attributeName;
        this.objectName = objectName;
    }

    public boolean showClass(String jevisclass) {
        if (this.objectName.equals(ALL)) {
            return true;
        }

        if (this.objectName.equals(NONE)) {
            return false;
        }

        return objectName.equals(jevisclass);

    }

    public boolean showObject(JEVisObject obj) throws JEVisException {
        String className = obj.getJEVisClassName();
        if (showClass(className)) {
            return true;
        }

        /* a link is treated like its target, e.g. a link to a data object passes a data filter (OpenJEVis #1674) */
        if (CommonClasses.LINK.NAME.equals(className)) {
            JEVisObject linkedObject = obj.getLinkedObject();
            return linkedObject != null
                    && !CommonClasses.LINK.NAME.equals(linkedObject.getJEVisClassName())
                    && showClass(linkedObject.getJEVisClassName());
        }

        return false;
    }

    /**
     * TODO: need additional parmether object to see if its the right object-attribute combo?
     *
     * @param attributeName
     * @return
     */
    public boolean showAttribute(String attributeName) {
        if (this.attributeName.equals(ALL)) {
            return true;
        }

        if (this.attributeName.equals(NONE)) {
            return false;
        }


        //            if(objectName.equals(ALL)){
        //                return true;
        //            }
        return attributeName.equals(this.attributeName);

    }

    public boolean showAttribute(JEVisAttribute attribute) {
        return showAttribute(attribute.getName());
    }

    public boolean showCell(JEVisTreeRow row) throws JEVisException {
        if (row == null || row.getType() == null) {
            return false;
        }

        if (row.getType() == JEVisTreeRow.TYPE.ATTRIBUTE) {
            return showAttribute(row.getJEVisAttribute());
        } else if (row.getType() == JEVisTreeRow.TYPE.OBJECT) {
            return showObject(row.getJEVisObject());
        }

        return false;
    }

    @Override
    public String toString() {
        return "ObjectAttributeFilter{" +
                "attributeName='" + attributeName + '\'' +
                ", objectName='" + objectName + '\'' +
                '}';
    }

    public enum TYPE {
        OBJECT, ATTRIBUTE, TYPE
    }
}
