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
package org.jevis.commons.patch;

/**
 *
 * @author broder
 */
public class Patcher {

    //applies all patches of a given type
    public <T extends Enum<T> & Patch> void applyPatches(Class<T> patchSet) {
        for (Patch p : patchSet.getEnumConstants()) {
            p.apply();
        }
    }

    //undo all patches of a given type
    public <T extends Enum<T> & Patch> void undoPatches(Class<T> patchSet) {
        for (Patch p : patchSet.getEnumConstants()) {
            p.undo();
        }
    }

    //applys a specific version of a patch
    public <T extends Enum<T> & Patch> void applyPatch(Class<T> patchSet, String version) {
        for (Patch p : patchSet.getEnumConstants()) {
            if(p.getVersion().equals(version)){
                p.apply();
            }
        }
    }

}
