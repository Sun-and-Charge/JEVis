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
package org.jevis.jeconfig.plugin.dashboard.config;

import javafx.beans.value.WritableValue;

/**
 * @deprecated Legacy V1 dashboard widget config property format. This class exists solely for
 * backward-compatibility when reading old dashboard JSON files.
 * Use {@link org.jevis.jeconfig.plugin.dashboard.config2.DashboardPojo} and the
 * {@code config2} package instead.
 */
@Deprecated
public class WidgetConfigProperty<T> {

    private final String name;
    private final String id;
    private final String category;
    private final String description;
    private final WritableValue writableValue;
    private T t;

    public WidgetConfigProperty(String id, String category, String name, String description, WritableValue writableValue) {
        this.id = id;
        this.category = category;
        this.name = name;
        this.description = description;
        this.writableValue = writableValue;
    }

    public T get() {
        return this.t;
    }

    public void set(T t1) {
        this.t = t1;
    }

    public String getCategory() {
        return this.category;
    }

    public String getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public WritableValue getWritableValue() {
        return this.writableValue;
    }

    public String getDescription() {
        return this.description;
    }
}
