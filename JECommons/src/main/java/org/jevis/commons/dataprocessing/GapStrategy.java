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
package org.jevis.commons.dataprocessing;

import java.util.HashMap;
import java.util.Map;

public class GapStrategy {
    private final GapMode gapMode;
    private final Map<GapAttribute, String> attributeMap = new HashMap<>();

    public GapStrategy(String gapStrategy) {
        String gapModeString = gapStrategy.split(";")[0];
        gapMode = GapMode.valueOf(gapModeString.toUpperCase());
        String[] split = gapStrategy.split(";");
        for (int i = 1; i < split.length; i++) {
            attributeMap.put(GapAttribute.DEFAULT_VALUE, split[i]);
        }
    }

    public GapMode getGapMode() {
        return gapMode;
    }

    public String getValue(GapAttribute gapAttribute) {
        return attributeMap.get(gapAttribute);
    }
}
