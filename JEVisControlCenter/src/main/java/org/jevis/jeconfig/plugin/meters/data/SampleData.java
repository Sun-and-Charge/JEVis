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
package org.jevis.jeconfig.plugin.meters.data;

import org.jevis.api.JEVisAttribute;
import org.jevis.api.JEVisSample;

import java.util.Optional;

public class SampleData {
    private final JEVisAttribute jeVisAttribute;
    private final Optional<JEVisSample> optionalJEVisSample;

    public SampleData(JEVisAttribute jeVisAttribute) {
        this.jeVisAttribute = jeVisAttribute;
        this.optionalJEVisSample = Optional.ofNullable(jeVisAttribute.getLatestSample());
    }

    public JEVisAttribute getJeVisAttribute() {
        return jeVisAttribute;
    }

    public Optional<JEVisSample> getOptionalJEVisSample() {
        return optionalJEVisSample;
    }
}
