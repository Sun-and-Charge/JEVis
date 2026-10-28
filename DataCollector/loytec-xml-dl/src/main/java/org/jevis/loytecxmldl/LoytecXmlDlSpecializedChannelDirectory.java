/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of loytec-xml-dl.
 * <p>
 * loytec-xml-dl is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * loytec-xml-dl is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * loytec-xml-dl. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * loytec-xml-dl is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.loytecxmldl;

/**
 * Container for specialized JEVis Loytec XML-DL Channel Directory information
 */
class LoytecXmlDlSpecializedChannelDirectory {
    private final String name;
    private final String technology;

    LoytecXmlDlSpecializedChannelDirectory(String name, String technology) {
        this.name = name;
        this.technology = technology;
    }

    public String getName() {
        return name;
    }

    public String getTechnology() {
        return technology;
    }
}