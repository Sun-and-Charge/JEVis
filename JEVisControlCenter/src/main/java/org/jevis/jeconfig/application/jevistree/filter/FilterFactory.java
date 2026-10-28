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
package org.jevis.jeconfig.application.jevistree.filter;

import javafx.scene.control.TreeTableColumn;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisException;
import org.jevis.commons.classes.ClassHelper;

import java.util.ArrayList;
import java.util.List;

public class FilterFactory {

    public static List<ObjectAttributeFilter> buildFilterForHeirs(JEVisClass jclass, String attributeFilter) throws JEVisException {
        List<ObjectAttributeFilter> filter = new ArrayList<>();
        List<JEVisClass> heirs = new ArrayList<>();
        ClassHelper.addAllHeir(heirs, jclass);

        filter.add(new ObjectAttributeFilter(jclass.getName(), attributeFilter));
        heirs.forEach(jeVisClass -> {
            try {
                filter.add(new ObjectAttributeFilter(jeVisClass.getName(), attributeFilter));
            } catch (Exception ex) {
            }
        });

        return filter;
    }


    public static List<ObjectAttributeFilter> defaultObjectTreeFilter() {
        List<ObjectAttributeFilter> filter = new ArrayList<>();
        filter.add(new ObjectAttributeFilter(ObjectAttributeFilter.ALL, ObjectAttributeFilter.NONE));

        return filter;
    }

    public static List<ObjectAttributeFilter> defaultAttributeTreeFilter() {
        List<ObjectAttributeFilter> filter = new ArrayList<>();
        filter.add(new ObjectAttributeFilter(ObjectAttributeFilter.ALL, ObjectAttributeFilter.ALL));

        return filter;
    }

    public static void addDefaultObjectTreeFilter(BasicCellFilter filter, TreeTableColumn column) {
        defaultObjectTreeFilter().forEach(objectAttributeFilter -> {
            filter.addFilter(column, objectAttributeFilter);
        });
    }

    public static void addDefaultAttributeTreeFilter(BasicCellFilter filter, TreeTableColumn column) {
        defaultAttributeTreeFilter().forEach(objectAttributeFilter -> {
            filter.addFilter(column, objectAttributeFilter);
        });
    }

    public static BasicCellFilter buildDefaultItemFilter() {
        BasicCellFilter basicFilter = new BasicCellFilter("Default");
//        basicFilter.addItemFilter(new ObjectAttributeFilter(BasicCellFilter.TREE_ITEM_COLUMN, ObjectAttributeFilter.ALL, ObjectAttributeFilter.NONE));

        basicFilter.addItemFilter(new ObjectAttributeFilter("Data", ObjectAttributeFilter.NONE));

        return basicFilter;
    }

}
