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
package org.jevis.commons.driver;

import org.apache.logging.log4j.LogManager;

import java.util.Map;

public class VarFiller {

    private static final org.apache.logging.log4j.Logger logger = LogManager.getLogger(VarFiller.class);
    private String uriString = "";
    private final Map<Variable, VarFunction> values;

    public VarFiller(String uriString, Map<Variable, VarFunction> values) {
        this.uriString = uriString;
        this.values = values;
    }

    public String getFilledURIString() {
        String filledStrg = uriString;
        for (Map.Entry<Variable, VarFunction> variableVarFunctionEntry : values.entrySet()) {
            try {
                filledStrg = filledStrg.replaceAll("\\{" + variableVarFunctionEntry.getKey().toString() + "}"
                        , variableVarFunctionEntry.getValue().getVarValue());
            } catch (Exception ex) {
                logger.error("Error in Variable: {}", variableVarFunctionEntry.getKey(), ex, ex);
            }
        }


        return filledStrg;
    }

    public interface VarFunction {

        String getVarValue();
    }

    public enum Variable {
        LAST_TS,// Last timestamp of the existing data in JEVis
        CURRENT_TS,
        CURRENT_TS2
    }
}
