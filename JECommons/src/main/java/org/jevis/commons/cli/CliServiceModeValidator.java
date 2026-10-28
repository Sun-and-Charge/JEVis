/**
 * Copyright (C) 2017 Envidatec GmbH <info@envidatec.com>
 *
 * This file is part of JECommons.
 *
 * JECommons is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * JECommons is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * JECommons. If not, see <http://www.gnu.org/licenses/>.
 *
 * JECommons is part of the JEVis project, see <https://github.com/Sun-and-Charge/JEVis>.
 */
package org.jevis.commons.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.ParameterException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates service mode values
 *
 * @author Artur Iablokov
 */
public class CliServiceModeValidator implements IParameterValidator {

    private final List<String> list = Arrays.asList(BasicSettings.SERVICE, BasicSettings.SINGLE, BasicSettings.COMPLETE);
    
    @Override
    public void validate(String name, String value) throws ParameterException {
        if (!list.contains(value)) {
            String listString = list.stream().map(Object::toString)
                        .collect(Collectors.joining(", "));
            throw new ParameterException("Value -'"+value+"' for Parameter " + name + " is not correct. Valid values:"+ listString);
        }
    }
}
