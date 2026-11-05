/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.ws.sql;

import java.util.List;

/**
 * The headers of an incoming web service request, as far as {@link SQLDataSource} needs them for the login.
 * <p>
 * Keeps JECommons free of the JAX-RS API, which the web service uses in the jakarta version (Java 11+) while
 * JECommons is still shared with the Control Center on Java 8. The web service passes
 * {@code httpHeaders::getRequestHeader}.
 */
@FunctionalInterface
public interface RequestHeaders {

    /**
     * @param name header name, case-insensitive
     * @return all values of the header, or {@code null}/an empty list if it is not set
     */
    List<String> get(String name);

    /**
     * @return the first value of the header, or {@code null} if it is not set
     */
    default String first(String name) {
        List<String> values = get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }
}
