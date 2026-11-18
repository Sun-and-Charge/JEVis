/**
 * Copyright (C) 2026 Sun and Charge Germany GmbH
 * <p>
 * This file is part of JEConfig.
 * <p>
 * JEConfig is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 * <p>
 * JEConfig is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JEConfig. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JEConfig is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.jeconfig;

/**
 * Main class of the Control Center jar. With OpenJFX on the class path, a main class that extends
 * {@link javafx.application.Application} is refused by the java launcher ("JavaFX runtime components are missing"),
 * so the jar starts here and hands over to {@link JEConfig}.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        JEConfig.main(args);
    }
}
