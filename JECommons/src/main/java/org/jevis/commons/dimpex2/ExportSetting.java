/**
 * Copyright (C) JEVis contributors
 * <p>
 * This file is part of JECommons.
 * <p>
 * JECommons is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation in version 3.
 * <p>
 * JECommons is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 * <p>
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.dimpex2;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisObject;

import java.util.UUID;

public class ExportSetting {

    private static final String fileending = ".json";
    private final boolean exportLastSamples = true;
    private final boolean exportAllSamples = false;
    private final UUID uuid = UUID.randomUUID();
    private JEVisObject object;
    private JEVisAttribute attribute;

    public ExportSetting() {
    }

    public ExportSetting(JEVisObject object) {
        this.object = object;
    }


    public String getFilename() {
        return uuid.toString() + fileending;
    }

    public JEVisObject getObject() {
        return object;
    }

    public JEVisAttribute getAttribute() {
        return attribute;
    }

    public boolean isExportLastSamples() {
        return exportLastSamples;
    }

    public boolean isExportAllSamples() {
        return exportAllSamples;
    }
}
